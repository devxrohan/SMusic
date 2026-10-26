package com.devxrohan.smusic

import android.app.Application
import android.content.Context
import android.os.Build
import android.widget.Toast
import com.devxrohan.smusic.utils.dataStore
import com.devxrohan.smusic.utils.initializeCache
import android.widget.Toast.LENGTH_SHORT
import androidx.datastore.preferences.core.edit
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.request.CachePolicy
import com.devxrohan.smusic.innertube.YouTube
import com.devxrohan.smusic.innertube.models.YouTubeLocale
import com.devxrohan.smusic.kugou.KuGou
import com.devxrohan.smusic.constants.AccountChannelHandleKey
import com.devxrohan.smusic.constants.AccountEmailKey
import com.devxrohan.smusic.constants.AccountNameKey
import com.devxrohan.smusic.constants.ContentCountryKey
import com.devxrohan.smusic.constants.ContentLanguageKey
import com.devxrohan.smusic.constants.CountryCodeToName
import com.devxrohan.smusic.constants.DataSyncIdKey
import com.devxrohan.smusic.constants.InnerTubeCookieKey
import com.devxrohan.smusic.constants.LanguageCodeToName
import com.devxrohan.smusic.constants.MaxImageCacheSizeKey
import com.devxrohan.smusic.constants.ProxyEnabledKey
import com.devxrohan.smusic.constants.ProxyTypeKey
import com.devxrohan.smusic.constants.ProxyUrlKey
import com.devxrohan.smusic.constants.SYSTEM_DEFAULT
import com.devxrohan.smusic.constants.UseLoginForBrowse
import com.devxrohan.smusic.constants.VisitorDataKey
import com.devxrohan.smusic.db.MusicDatabase
import com.devxrohan.smusic.extensions.toEnum
import com.devxrohan.smusic.extensions.toInetSocketAddress
import com.devxrohan.smusic.ui.component.NamePreferenceManager
import com.devxrohan.smusic.utils.dataStore
import com.devxrohan.smusic.utils.get
import com.devxrohan.smusic.utils.reportException
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.net.Proxy
import java.util.Locale
import javax.inject.Inject

@HiltAndroidApp
class App : android.app.Application(), ImageLoaderFactory {
    @Inject
    lateinit var database: MusicDatabase

    @Inject
    lateinit var namePreferenceManager: NamePreferenceManager

    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(base)
        // A restored backup is moved into place here, before the database / DataStore / SharedPreferences are opened.
        if (!com.devxrohan.smusic.utils.AppBackup.isRestartProcess()) {
            runCatching { com.devxrohan.smusic.utils.AppBackup.applyPendingRestore(base) }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreate() {
        super.onCreate()
        // The helper process that relaunches the app after a restore must not initialise anything.
        if (com.devxrohan.smusic.utils.AppBackup.isRestartProcess()) return
        instance = this
        com.devxrohan.smusic.utils.AppBackup.cancelRestartFallback(this)
        runCatching { com.devxrohan.smusic.utils.AppBackup.finishPendingRestore(this) }

        // Restore a snapshot that Android's OS backup (or Downloads/SMusic, Documents/SMusic) brought back. Once per install.
        runCatching { com.devxrohan.smusic.utils.AutoBackupManager.checkAndRestoreOnOpen(this) }
        GlobalScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(8_000)
            runCatching { com.devxrohan.smusic.utils.AutoBackupManager.resetRestartAttempts(this@App) }
        }

        // Keep the automatic backups fresh: Documents/SMusic when the app opens, the OS/device snapshot when it closes.
        registerActivityLifecycleCallbacks(object : android.app.Application.ActivityLifecycleCallbacks {
            private var startedActivities = 0
            override fun onActivityStarted(activity: android.app.Activity) {
                if (startedActivities == 0) {
                    GlobalScope.launch(Dispatchers.IO) {
                        runCatching {
                            com.devxrohan.smusic.utils.AutoBackupManager.performAutoBackupToStorageIfEnabled(this@App, database)
                        }
                    }
                }
                startedActivities++
            }

            override fun onActivityStopped(activity: android.app.Activity) {
                startedActivities--
                if (startedActivities <= 0 && !activity.isChangingConfigurations) {
                    GlobalScope.launch(Dispatchers.IO) {
                        runCatching {
                            val manager = com.devxrohan.smusic.utils.AutoBackupManager
                            if (manager.createAutoBackup(this@App, database, notifyBackupManager = true) &&
                                getSharedPreferences("backup_settings", Context.MODE_PRIVATE)
                                    .getBoolean("enable_cloud_upload", true)
                            ) {
                                manager.uploadToCloud(this@App, manager.getAutoBackupFile(this@App))
                            }
                        }
                    }
                }
            }

            override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) {}
            override fun onActivityResumed(activity: android.app.Activity) {}
            override fun onActivityPaused(activity: android.app.Activity) {}
            override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) {}
            override fun onActivityDestroyed(activity: android.app.Activity) {}
        })

        // Clean up any leftover downloaded-update APK — by the time the app
        // is running again, either the install succeeded (new version now
        // running) or the user cancelled, so the file has served its
        // purpose either way and shouldn't be left sitting in storage.
        runCatching {
            getExternalFilesDir("updates")?.listFiles()?.forEach { it.delete() }
        }

        // Was runBlocking { ... } here — that blocks the calling thread
        // (the main thread, since Application.onCreate() always runs on
        // it) until the cache read + decryption finishes, which is exactly
        // what held the system's cold-start icon on screen for so long.
        // isReady lets MainActivity's splash screen keep itself up for
        // this same amount of time instead, without blocking anything.
        GlobalScope.launch(Dispatchers.IO) {
            runCatching { dataStore.initializeCache() }
            isReady.value = true
        }
        Timber.plant(com.devxrohan.smusic.utils.GlobalLogTree())
        // Debug builds only: log per request whether the YouTube account is attached (booleans only, no secrets).
        if (com.devxrohan.smusic.BuildConfig.DEBUG) {
            com.devxrohan.smusic.innertube.YouTube.authTrace = { Timber.tag("AuthTrace").v(it) }
        }

        // Keep update notifications alive even when SMusic is not open.
        // The worker uses the same multi-network GitHub fallbacks as the foreground checker.
        runCatching {
            com.devxrohan.smusic.update.AppNotificationManager.schedulePeriodicCheck(this)
        }.onFailure { Timber.w(it, "Could not schedule update check worker") }

        try {
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                try {
                    val sw = java.io.StringWriter()
                    val pw = java.io.PrintWriter(sw)
                    throwable.printStackTrace(pw)
                } catch (e: Exception) {
                    reportException(e)
                } finally {
                    android.os.Process.killProcess(android.os.Process.myPid())
                    kotlin.system.exitProcess(2)
                }
            }
        } catch (e: Exception) {
            reportException(e)
        }

        val locale = Locale.getDefault()
        val languageTag = locale.toLanguageTag().replace("-Hant", "") // replace zh-Hant-* to zh-*
        YouTube.locale = YouTubeLocale(
            gl = dataStore[ContentCountryKey]?.takeIf { it != SYSTEM_DEFAULT }
                ?: locale.country.takeIf { it in CountryCodeToName }
                ?: "US",
            hl = dataStore[ContentLanguageKey]?.takeIf { it != SYSTEM_DEFAULT }
                ?: locale.language.takeIf { it in LanguageCodeToName }
                ?: languageTag.takeIf { it in LanguageCodeToName }
                ?: "en"
        )
        if (languageTag == "zh-TW") {
            KuGou.useTraditionalChinese = true
        }

        if (dataStore[ProxyEnabledKey] == true) {
            try {
                YouTube.proxy = Proxy(
                    dataStore[ProxyTypeKey].toEnum(defaultValue = Proxy.Type.HTTP),
                    dataStore[ProxyUrlKey]!!.toInetSocketAddress()
                )
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to parse proxy url.", LENGTH_SHORT).show()
                reportException(e)
            }
        }

        if (dataStore[UseLoginForBrowse] != false) {
            YouTube.useLoginForBrowse = true
        }

        GlobalScope.launch(Dispatchers.IO) {
            runCatching {
                val email = namePreferenceManager.accountEmail.first().ifBlank {
                    dataStore[AccountEmailKey] ?: ""
                }
                val name = namePreferenceManager.userName.first().ifBlank { "SMusic User" }

                val automaticCloudBackupEnabled = getSharedPreferences("backup_settings", Context.MODE_PRIVATE)
                    .getBoolean("enable_cloud_upload", true)

                if (automaticCloudBackupEnabled && email.isNotBlank()) {
                    Timber.i("App launch: Starting automatic cloud backup upload for $email")
                    val backupViewModel = com.devxrohan.smusic.viewmodels.BackupRestoreViewModel(com.devxrohan.smusic.db.InternalDatabase.newInstance(this@App))
                    val result = backupViewModel.backupToDrive(this@App, email, name)
                    if (result is com.devxrohan.smusic.utils.DriveResult.Success) {
                        dataStore.edit { preferences ->
                            preferences[com.devxrohan.smusic.constants.LastBackupTimestampKey] = System.currentTimeMillis()
                        }
                        Timber.i("App launch: Cloud backup upload completed successfully for $email")
                    } else {
                        Timber.e("App launch: Cloud backup upload failed for $email")
                    }

                    // Schedule periodic 24-hour backup worker
                    val workRequest = androidx.work.PeriodicWorkRequestBuilder<com.devxrohan.smusic.worker.DailyBackupWorker>(1, java.util.concurrent.TimeUnit.DAYS)
                        .setConstraints(
                            androidx.work.Constraints.Builder()
                                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                                .build()
                        )
                        .build()

                    androidx.work.WorkManager.getInstance(this@App).enqueueUniquePeriodicWork(
                        "DailyBackupWorker",
                        androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                        workRequest
                    )
                }
            }.onFailure { e ->
                Timber.e(e, "App launch: Error during automatic cloud backup")
            }
        }

        runCatching {
            val pulseRequest = androidx.work.PeriodicWorkRequestBuilder<com.devxrohan.smusic.worker.PulseRefreshWorker>(6, java.util.concurrent.TimeUnit.HOURS)
                .setConstraints(
                    androidx.work.Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()
            androidx.work.WorkManager.getInstance(this@App).enqueueUniquePeriodicWork(
                "PulseRefreshWorker",
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                pulseRequest,
            )
        }.onFailure { Timber.w(it, "Could not schedule Pulse background refresh") }

        // Start Explore (list + artwork + pictures) right away so it is ready before the user opens it.
        // YouTube locale / proxy / login-for-browse are configured above; visitorData is awaited inside.
        com.devxrohan.smusic.utils.ExplorePrefetcher.start(this)

        GlobalScope.launch {
            dataStore.data
                .map { it[VisitorDataKey] }
                .distinctUntilChanged()
                .collect { visitorData ->
                    YouTube.visitorData = visitorData
                        ?.takeIf { it != "null" } // Previously visitorData was sometimes saved as "null" due to a bug
                        ?: YouTube.visitorData().onFailure {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@App, "Failed to get visitorData.", LENGTH_SHORT)
                                    .show()
                            }
                            reportException(it)
                        }.getOrNull()?.also { newVisitorData ->
                            dataStore.edit { settings ->
                                settings[VisitorDataKey] = newVisitorData
                            }
                        }
                }
        }
        GlobalScope.launch {
            dataStore.data
                .map { it[DataSyncIdKey] }
                .distinctUntilChanged()
                .collect { dataSyncId ->
                    YouTube.dataSyncId = dataSyncId?.let {
                        /*
                         * Workaround to avoid breaking older installations that have a dataSyncId
                         * that contains "||" in it.
                         * If the dataSyncId ends with "||" and contains only one id, then keep the
                         * id before the "||".
                         * If the dataSyncId contains "||" and is not at the end, then keep the
                         * second id.
                         * This is needed to keep using the same account as before.
                         */
                        it.takeIf { !it.contains("||") }
                            ?: it.takeIf { it.endsWith("||") }?.substringBefore("||")
                            ?: it.substringAfter("||")
                    }
                }
        }
        GlobalScope.launch {
            dataStore.data
                .map { prefs ->
                    prefs[InnerTubeCookieKey].orEmpty().takeIf { raw ->
                        raw.isNotBlank() && runCatching {
                            "SAPISID" in com.devxrohan.smusic.innertube.utils.parseCookieString(raw)
                        }.getOrDefault(false)
                    }
                }
                .distinctUntilChanged()
                .collect { cookie ->
                    try {
                        // A missing/invalid cookie means anonymous browsing, never an empty pseudo-session.
                        YouTube.cookie = cookie
                    } catch (e: Exception) {
                        Timber.e("Could not parse cookie. Clearing existing cookie. %s", e.message)
                        forgetAccount(this@App)
                        YouTube.cookie = null
                    }
                }
        }
    }

    override fun newImageLoader(): ImageLoader {
        val cacheSize = dataStore[MaxImageCacheSizeKey]

        val builder = ImageLoader.Builder(this)
            .crossfade(true)
            .respectCacheHeaders(false)
            .allowHardware(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            // Keep frequently reused queue/album/player artwork in RAM.
            .memoryCache {
                coil.memory.MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .strongReferencesEnabled(true)
                    .build()
            }
            .memoryCachePolicy(CachePolicy.ENABLED)

        if (cacheSize == 0) {
            return builder.diskCachePolicy(CachePolicy.DISABLED).build()
        }

        return builder
            .diskCachePolicy(CachePolicy.ENABLED)
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("coil"))
                    .maxSizeBytes((cacheSize ?: 512) * 1024 * 1024L)
                    .build()
            }
            .build()
    }

    companion object {
        lateinit var instance: App
            private set

        /** True once dataStore.initializeCache() has finished — MainActivity's splash keepOnScreenCondition watches this. */
        val isReady = kotlinx.coroutines.flow.MutableStateFlow(false)

        fun forgetAccount(context: Context) {
            runBlocking {
                context.dataStore.edit { settings ->
                    settings.remove(InnerTubeCookieKey)
                    settings.remove(VisitorDataKey)
                    settings.remove(DataSyncIdKey)
                    settings.remove(AccountNameKey)
                    settings.remove(AccountEmailKey)
                    settings.remove(AccountChannelHandleKey)
                }
            }
        }
    }
}
