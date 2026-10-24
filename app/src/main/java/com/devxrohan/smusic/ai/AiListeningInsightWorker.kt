package com.devxrohan.smusic.ai

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.devxrohan.smusic.R
import com.devxrohan.smusic.constants.AiProviderKey
import com.devxrohan.smusic.constants.AiListeningNotificationsKey
import com.devxrohan.smusic.constants.OpenRouterApiKey
import com.devxrohan.smusic.constants.OpenRouterBaseUrlKey
import com.devxrohan.smusic.constants.OpenRouterModelKey
import com.devxrohan.smusic.db.InternalDatabase
import com.devxrohan.smusic.utils.dataStore
import com.devxrohan.smusic.utils.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class AiListeningInsightWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!applicationContext.dataStore.get(AiListeningNotificationsKey, false)) return@withContext Result.success()
        try {
            val db = InternalDatabase.newInstance(applicationContext)
            val mostPlayed = runCatching { db.mostPlayedSongs(fromTimeStamp = 0L, limit = 12).firstOrNull() }.getOrNull().orEmpty()
            val recent = runCatching { db.recentSongs(limit = 8).firstOrNull() }.getOrNull().orEmpty()
            val songs = (mostPlayed + recent).distinctBy { it.id }.take(16)
            if (songs.isEmpty()) return@withContext Result.success()
            val taste = songs.joinToString("\n") { song -> "${song.title} — ${song.artists.joinToString { it.name }}" }
            val provider = applicationContext.dataStore.get(AiProviderKey, "Puter").trim()
            val key = applicationContext.dataStore.get(OpenRouterApiKey, "")
            val storedBase = applicationContext.dataStore.get(OpenRouterBaseUrlKey, "https://api.puter.com/v1/chat/completions")
            val storedModel = applicationContext.dataStore.get(OpenRouterModelKey, "gpt-4o-mini")
            val isPuter = provider.equals("Puter", true)
            val base = if (isPuter && storedBase.contains("openrouter.ai")) "https://api.puter.com/v1/chat/completions" else storedBase
            val model = if (isPuter && storedModel == "google/gemini-2.5-flash-lite") "gpt-4o-mini" else storedModel
            if (provider.equals("DeepL", true)) return@withContext Result.success()
            if (key.isBlank() && !isPuter && !provider.equals("Custom", true)) return@withContext Result.success()
            val prompt = "Write one friendly, non-cheesy music discovery insight in 1-2 sentences based on these listening signals. Do not invent facts, do not recommend unsafe content, and return plain text only.\n$taste"
            val isClaude = provider.equals("Claude", true)
            val resolvedModel = if (isClaude && (model.isBlank() || model == "google/gemini-2.5-flash-lite")) "claude-3-5-haiku-latest"
                else model.ifBlank { "google/gemini-2.5-flash-lite" }
            val requestBody = JSONObject().apply {
                put("model", resolvedModel)
                put("max_tokens", 120)
                put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
                if (!isClaude) put("temperature", 0.7)
            }.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val defaultUrl = if (isClaude) "https://api.anthropic.com/v1/messages" else "https://openrouter.ai/api/v1/chat/completions"
            val configuredUrl = base.ifBlank { defaultUrl }.let { if (isClaude && it.contains("openrouter.ai")) defaultUrl else it }
            val builder = Request.Builder().url(configuredUrl).post(requestBody)
            if (key.isNotBlank()) {
                if (isClaude) builder.addHeader("x-api-key", key).addHeader("anthropic-version", "2023-06-01")
                else builder.addHeader("Authorization", "Bearer $key")
            }
            val client = OkHttpClient.Builder().callTimeout(35, TimeUnit.SECONDS).build()
            val response = client.newCall(builder.build()).execute()
            if (!response.isSuccessful) return@withContext if (response.code >= 500 || response.code == 429) Result.retry() else Result.success()
            val body = response.body?.string().orEmpty()
            val parsed = JSONObject(body)
            val message = if (isClaude) parsed.optJSONArray("content")?.optJSONObject(0)?.optString("text")?.trim().orEmpty()
                else parsed.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")?.trim().orEmpty()
            if (message.isBlank()) return@withContext Result.retry()
            postInsight(applicationContext, message)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun postInsight(context: Context, message: String) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "AI listening insights", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.notifications)
            .setContentTitle("Your music insight")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val CHANNEL_ID = "ai_listening_insights"
        private const val NOTIFICATION_ID = 74102
        private const val WORK_NAME = "smusic_ai_listening_insights"

        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<AiListeningInsightWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }

        fun setEnabled(context: Context, enabled: Boolean) {
            val manager = WorkManager.getInstance(context)
            if (!enabled) {
                manager.cancelUniqueWork(WORK_NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<AiListeningInsightWorker>(24, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInitialDelay(1, TimeUnit.HOURS)
                .build()
            manager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
