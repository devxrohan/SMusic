package com.devxrohan.smusic.viewmodels

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devxrohan.smusic.MainActivity
import com.devxrohan.smusic.R
import com.devxrohan.smusic.db.InternalDatabase
import com.devxrohan.smusic.db.MusicDatabase
import com.devxrohan.smusic.db.entities.ArtistEntity
import com.devxrohan.smusic.db.entities.Song
import com.devxrohan.smusic.db.entities.SongEntity
import com.devxrohan.smusic.extensions.div
import com.devxrohan.smusic.extensions.tryOrNull
import com.devxrohan.smusic.extensions.zipInputStream
import com.devxrohan.smusic.extensions.zipOutputStream
import com.devxrohan.smusic.playback.MusicService
import com.devxrohan.smusic.playback.MusicService.Companion.PERSISTENT_QUEUE_FILE
import com.devxrohan.smusic.ui.component.NamePreferenceManager
import com.devxrohan.smusic.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import javax.inject.Inject
import kotlin.system.exitProcess

@HiltViewModel
class BackupRestoreViewModel @Inject constructor(
    val database: MusicDatabase,
) : ViewModel() {
    fun backup(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
        val result = runCatching {
            context.applicationContext.contentResolver.openOutputStream(uri)?.use {
                it.buffered().zipOutputStream().use { outputStream ->
                    (context.filesDir / "datastore" / SETTINGS_FILENAME).inputStream().buffered()
                        .use { inputStream ->
                            outputStream.putNextEntry(ZipEntry(SETTINGS_FILENAME))
                            inputStream.copyTo(outputStream)
                        }

                    val namePrefsFile = context.filesDir / "datastore" / "user_name_preferences.preferences_pb"
                    if (namePrefsFile.exists()) {
                        namePrefsFile.inputStream().buffered().use { inputStream ->
                            outputStream.putNextEntry(ZipEntry("user_name_preferences.preferences_pb"))
                            inputStream.copyTo(outputStream)
                        }
                    }

                    val accountEmail = runBlocking { NamePreferenceManager(context).accountEmail.first() }
                    if (accountEmail.isNotBlank()) {
                        outputStream.putNextEntry(ZipEntry(GOOGLE_ACCOUNT_FILENAME))
                        outputStream.write(
                            JSONObject()
                                .put("email", accountEmail)
                                .put("previouslyLoggedIn", true)
                                .toString()
                                .toByteArray()
                        )
                    }

                    val parentFile = context.filesDir.parentFile
                    if (parentFile != null) {
                        val statsPrefsFile = parentFile / "shared_prefs" / "smusic_global_stats.xml"
                        if (statsPrefsFile.exists()) {
                            statsPrefsFile.inputStream().buffered().use { inputStream ->
                                outputStream.putNextEntry(ZipEntry("smusic_global_stats.xml"))
                                inputStream.copyTo(outputStream)
                            }
                        }
                    }

                    runBlocking(Dispatchers.IO) {
                        database.checkpoint()
                    }
                    FileInputStream(database.openHelper.writableDatabase.path).use { inputStream ->
                        outputStream.putNextEntry(ZipEntry(InternalDatabase.DB_NAME))
                        inputStream.copyTo(outputStream)
                    }
                }
            }
        }
        withContext(Dispatchers.Main) {
            result.onSuccess {
                Toast.makeText(context, R.string.backup_create_success, Toast.LENGTH_SHORT).show()
            }.onFailure {
                reportException(it)
                Toast.makeText(context, R.string.backup_create_failed, Toast.LENGTH_SHORT).show()
            }
        }
        }
    }

    fun restore(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
        val result = runCatching {
            context.applicationContext.contentResolver.openInputStream(uri)?.use {
                it.zipInputStream().use { inputStream ->
                    var entry = tryOrNull { inputStream.nextEntry } // prevent ZipException
                    while (entry != null) {
                        when (entry.name) {
                            SETTINGS_FILENAME -> {
                                (context.filesDir / "datastore" / SETTINGS_FILENAME).outputStream()
                                    .use { outputStream ->
                                        inputStream.copyTo(outputStream)
                                    }
                            }

                            "user_name_preferences.preferences_pb" -> {
                                val destFile = context.filesDir / "datastore" / "user_name_preferences.preferences_pb"
                                destFile.parentFile?.mkdirs()
                                destFile.outputStream().use { outputStream ->
                                    inputStream.copyTo(outputStream)
                                }
                            }

                            "smusic_global_stats.xml" -> {
                                val parentFile = context.filesDir.parentFile
                                if (parentFile != null) {
                                    val destFile = parentFile / "shared_prefs" / "smusic_global_stats.xml"
                                    destFile.parentFile?.mkdirs()
                                    destFile.outputStream().use { outputStream ->
                                        inputStream.copyTo(outputStream)
                                    }
                                }
                            }

                            GOOGLE_ACCOUNT_FILENAME -> {
                                val email = inputStream.readBytes()
                                    .toString(Charsets.UTF_8)
                                    .let { JSONObject(it).optString("email") }
                                    .trim()
                                if (email.isNotBlank()) {
                                    runBlocking {
                                        NamePreferenceManager(context).rememberGoogleLoginEmail(email)
                                    }
                                }
                            }

                            InternalDatabase.DB_NAME -> {
                                runBlocking(Dispatchers.IO) {
                                    database.checkpoint()
                                }
                                database.close()
                                FileOutputStream(database.openHelper.writableDatabase.path).use { outputStream ->
                                    inputStream.copyTo(outputStream)
                                }
                            }
                        }
                        entry = tryOrNull { inputStream.nextEntry } // prevent ZipException
                    }
                }
            }
            context.stopService(Intent(context, MusicService::class.java))
            context.filesDir.resolve(PERSISTENT_QUEUE_FILE).delete()
            context.startActivity(Intent(context, MainActivity::class.java))
            exitProcess(0)
        }
        result.onFailure {
            reportException(it)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, R.string.restore_failed, Toast.LENGTH_SHORT).show()
            }
        }
        }
    }

    suspend fun backupToDrive(context: Context, email: String, name: String = "SMusic User"): com.devxrohan.smusic.utils.DriveResult<Boolean> {
        return try {
            val tempFile = java.io.File(context.cacheDir, "temp_backup.zip")
            tempFile.outputStream().use { fileOut ->
                fileOut.buffered().zipOutputStream().use { outputStream ->
                    (context.filesDir / "datastore" / SETTINGS_FILENAME).takeIf { it.exists() }?.inputStream()?.buffered()?.use { inputStream ->
                        outputStream.putNextEntry(java.util.zip.ZipEntry(SETTINGS_FILENAME))
                        inputStream.copyTo(outputStream)
                    }

                    val namePrefsFile = context.filesDir / "datastore" / "user_name_preferences.preferences_pb"
                    if (namePrefsFile.exists()) {
                        namePrefsFile.inputStream().buffered().use { inputStream ->
                            outputStream.putNextEntry(java.util.zip.ZipEntry("user_name_preferences.preferences_pb"))
                            inputStream.copyTo(outputStream)
                        }
                    }

                    outputStream.putNextEntry(java.util.zip.ZipEntry(GOOGLE_ACCOUNT_FILENAME))
                    outputStream.write(
                        org.json.JSONObject()
                            .put("email", email)
                            .put("previouslyLoggedIn", true)
                            .toString()
                            .toByteArray()
                    )

                    val parentFile = context.filesDir.parentFile
                    if (parentFile != null) {
                        val statsPrefsFile = parentFile / "shared_prefs" / "smusic_global_stats.xml"
                        if (statsPrefsFile.exists()) {
                            statsPrefsFile.inputStream().buffered().use { inputStream ->
                                outputStream.putNextEntry(java.util.zip.ZipEntry("smusic_global_stats.xml"))
                                inputStream.copyTo(outputStream)
                            }
                        }
                    }

                    kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                        database.checkpoint()
                    }
                    java.io.FileInputStream(database.openHelper.writableDatabase.path).use { inputStream ->
                        outputStream.putNextEntry(java.util.zip.ZipEntry(com.devxrohan.smusic.db.InternalDatabase.DB_NAME))
                        inputStream.copyTo(outputStream)
                    }
                }
            }

            val backupClient = com.devxrohan.smusic.utils.CloudBackupClient()
            
            // Note: CloudBackupClient handles the details.json internally as part of uploadBackup
            val success = backupClient.uploadBackup(
                email = email,
                name = name,
                backupFile = tempFile
            )

            if (success) {
                com.devxrohan.smusic.utils.DriveResult.Success(true)
            } else {
                com.devxrohan.smusic.utils.DriveResult.Error(Exception("Cloud backup upload failed"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            com.devxrohan.smusic.utils.DriveResult.Error(e)
        }
    }

    suspend fun restoreFromDrive(context: Context, email: String): com.devxrohan.smusic.utils.DriveResult<Boolean> {
        return try {
            val tempFile = java.io.File(context.cacheDir, "temp_restore.zip")
            val backupClient = com.devxrohan.smusic.utils.CloudBackupClient()
            
            val success = backupClient.downloadBackup(email, tempFile)
            if (!success) {
                return com.devxrohan.smusic.utils.DriveResult.Error(Exception("Backup not found in cloud"))
            }

            tempFile.inputStream().use { fileIn ->
                fileIn.zipInputStream().use { inputStream ->
                    var entry = runCatching { inputStream.nextEntry }.getOrNull()
                    while (entry != null) {
                        when (entry?.name) {
                            SETTINGS_FILENAME -> {
                                (context.filesDir / "datastore" / SETTINGS_FILENAME).outputStream().use { outputStream ->
                                    inputStream.copyTo(outputStream)
                                }
                            }
                            "user_name_preferences.preferences_pb" -> {
                                val destFile = context.filesDir / "datastore" / "user_name_preferences.preferences_pb"
                                destFile.parentFile?.mkdirs()
                                destFile.outputStream().use { outputStream ->
                                    inputStream.copyTo(outputStream)
                                }
                            }
                            "smusic_global_stats.xml" -> {
                                val parentFile = context.filesDir.parentFile
                                if (parentFile != null) {
                                    val destFile = parentFile / "shared_prefs" / "smusic_global_stats.xml"
                                    destFile.parentFile?.mkdirs()
                                    destFile.outputStream().use { outputStream ->
                                        inputStream.copyTo(outputStream)
                                    }
                                }
                            }
                            GOOGLE_ACCOUNT_FILENAME -> {
                                val restoredEmail = inputStream.readBytes()
                                    .toString(Charsets.UTF_8)
                                    .let { org.json.JSONObject(it).optString("email") }
                                    .trim()
                                if (restoredEmail.isNotBlank()) {
                                    kotlinx.coroutines.runBlocking {
                                        NamePreferenceManager(context).rememberGoogleLoginEmail(restoredEmail)
                                    }
                                }
                            }
                            com.devxrohan.smusic.db.InternalDatabase.DB_NAME -> {
                                kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                                    database.checkpoint()
                                }
                                database.close()
                                java.io.FileOutputStream(database.openHelper.writableDatabase.path).use { outputStream ->
                                    inputStream.copyTo(outputStream)
                                }
                            }
                        }
                        entry = runCatching { inputStream.nextEntry }.getOrNull()
                    }
                }
            }
            com.devxrohan.smusic.utils.DriveResult.Success(true)
        } catch (e: Exception) {
            e.printStackTrace()
            com.devxrohan.smusic.utils.DriveResult.Error(e)
        }
    }

    fun importPlaylistFromCsv(context: Context, uri: Uri): ArrayList<Song> {
        val songs = arrayListOf<Song>()
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val lines = stream.bufferedReader().readLines()
                lines.forEachIndexed { _, line ->
                    val parts = line.split(",").map { it.trim() }
                    val title = parts[0]
                    val artistStr = parts[1]

                    val artists = artistStr.split(";").map { it.trim() }.map {
                        ArtistEntity(
                            id = "",
                            name = it,
                        )
                    }
                    val mockSong = Song(
                        song = SongEntity(
                            id = "",
                            title = title,
                        ),
                        artists = artists,
                    )
                    songs.add(mockSong)
                }
            }
        }

        if (songs.isEmpty()) {
            Toast.makeText(
                context,
                "No songs found. Invalid file, or perhaps no song matches were found.",
                Toast.LENGTH_SHORT
            ).show()
        }
        return songs
    }

    fun loadM3UOnline(
        context: Context,
        uri: Uri,
    ): ArrayList<Song> {
        val songs = ArrayList<Song>()

        runCatching {
            context.applicationContext.contentResolver.openInputStream(uri)?.use { stream ->
                val lines = stream.bufferedReader().readLines()
                if (lines.first().startsWith("#EXTM3U")) {
                    lines.forEachIndexed { _, rawLine ->
                        if (rawLine.startsWith("#EXTINF:")) {
                            // maybe later write this to be more efficient
                            val artists =
                                rawLine.substringAfter("#EXTINF:").substringAfter(',').substringBefore(" - ").split(';')
                            val title = rawLine.substringAfter("#EXTINF:").substringAfter(',').substringAfter(" - ")

                            val mockSong = Song(
                                song = SongEntity(
                                    id = "",
                                    title = title,
                                ),
                                artists = artists.map { ArtistEntity("", it) },
                            )
                            songs.add(mockSong)

                        }
                    }
                }
            }
        }

        if (songs.isEmpty()) {
            Toast.makeText(
                context,
                "No songs found. Invalid file, or perhaps no song matches were found.",
                Toast.LENGTH_SHORT
            ).show()
        }
        return songs
    }

    fun resetVisitorData(context: Context) {
        runCatching {
            // Implementa aquí cómo borras VISITOR_DATA, por ejemplo, desde DataStore
            val visitorDataFile = context.filesDir / "datastore" / SETTINGS_FILENAME
            if (visitorDataFile.exists()) {
                // Borra solo la parte de VISITOR_DATA si es posible, o reinicia el archivo
                visitorDataFile.delete()
            }

            Toast.makeText(
                context,
                "VISITOR_DATA reseteado. La aplicación se reiniciará.",
                Toast.LENGTH_SHORT
            ).show()

            context.stopService(Intent(context, MusicService::class.java))
            context.filesDir.resolve(PERSISTENT_QUEUE_FILE).delete()
            context.startActivity(
                Intent(
                    context,
                    MainActivity::class.java
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            exitProcess(0)
        }.onFailure {
            reportException(it)
            Toast.makeText(context, "Error al resetear VISITOR_DATA", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val SETTINGS_FILENAME = "settings.preferences_pb"
        const val GOOGLE_ACCOUNT_FILENAME = "google_account.json"
    }
}

