package com.devxrohan.smusic.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class CloudBackupClient {
    private val client =
        OkHttpClient
            .Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

    // Create a safe folder name from the email
    private fun getEmailFolder(email: String): String {
        return email.replace("@", "_at_").replace(".", "_dot_")
    }

    suspend fun checkBackupExists(email: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val folder = getEmailFolder(email)
                val fileName = "smusic/backups/$folder/smusic_backup.backup"
                val request =
                    Request
                        .Builder()
                        .url("$BASE_URL/download?file=$fileName&_t=${System.currentTimeMillis()}")
                        .header("Cache-Control", "no-cache")
                        .header("Pragma", "no-cache")
                        .get()
                        .build()
                client.newCall(request).execute().use { response ->
                    response.isSuccessful && (response.body?.contentLength() ?: 0L) != 0L
                }
            }.getOrDefault(false)
        }

    suspend fun uploadBackup(email: String, name: String, backupFile: File): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val folder = getEmailFolder(email)
                
                // 1. Upload the details.json
                val detailsJson = JSONObject()
                    .put("email", email)
                    .put("name", name)
                    .put("backupFile", "smusic_backup.backup")
                    .put("backupSize", backupFile.length())
                    .put("lastBackupAt", System.currentTimeMillis())

                val detailsRequest = Request.Builder()
                    .url("$BASE_URL/write?file=smusic/backups/$folder/details.json")
                    .addHeader("X-API-Key", API_KEY)
                    .post(detailsJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()
                
                val detailsSuccess = client.newCall(detailsRequest).execute().use { it.isSuccessful }
                if (!detailsSuccess) return@runCatching false

                // 2. Upload the actual backup zip file
                val backupRequest = Request.Builder()
                    .url("$BASE_URL/upload?file=smusic/backups/$folder/smusic_backup.backup")
                    .addHeader("X-API-Key", API_KEY)
                    .post(backupFile.asRequestBody("application/octet-stream".toMediaType()))
                    .build()

                client.newCall(backupRequest).execute().use { it.isSuccessful }
            }.getOrDefault(false)
        }

    suspend fun downloadBackup(email: String, destFile: File): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val folder = getEmailFolder(email)
                val fileName = "smusic/backups/$folder/smusic_backup.backup"
                val request = Request.Builder()
                    .url("$BASE_URL/download?file=$fileName&_t=${System.currentTimeMillis()}")
                    .header("Cache-Control", "no-cache")
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@runCatching false
                    
                    val body = response.body ?: return@runCatching false
                    FileOutputStream(destFile).use { fos ->
                        body.byteStream().use { stream ->
                            stream.copyTo(fos)
                        }
                    }
                    true
                }
            }.getOrDefault(false)
        }

    private companion object {
        val BASE_URL = com.devxrohan.smusic.BuildConfig.STATS_BASE_URL
        val API_KEY = com.devxrohan.smusic.BuildConfig.STATS_API_KEY
    }
}
