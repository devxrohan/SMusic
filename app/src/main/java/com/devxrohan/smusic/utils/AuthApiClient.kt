package com.devxrohan.smusic.utils

import com.devxrohan.smusic.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class AuthUser(
    val id: String,
    val projectId: String,
    val name: String,
    val email: String,
    val createdAt: String
)

sealed class AuthResult {
    data class Success(val user: AuthUser, val message: String? = null) : AuthResult()
    data class Error(val message: String) : AuthResult()
}

class AuthApiClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val baseUrl = BuildConfig.AUTH_API_BASE_URL
    private val projectId = "proj_35e369a4-ccfa-4d7b-be7f-3fe58dcaece4"

    suspend fun login(email: String, password: String): AuthResult = withContext(Dispatchers.IO) {
        try {
            val jsonBody = JSONObject().apply {
                put("email", email)
                put("password", password)
            }

            val request = Request.Builder()
                .url("$baseUrl/api/projects/$projectId/auth")
                .post(jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string()
                if (response.isSuccessful && bodyString != null) {
                    val json = JSONObject(bodyString)
                    if (json.optBoolean("success")) {
                        val userObj = json.getJSONObject("user")
                        val user = AuthUser(
                            id = userObj.optString("id"),
                            projectId = userObj.optString("project_id"),
                            name = userObj.optString("name"),
                            email = userObj.optString("email"),
                            createdAt = userObj.optString("created_at")
                        )
                        return@withContext AuthResult.Success(user, json.optString("message"))
                    } else {
                        return@withContext AuthResult.Error(json.optString("error", "Unknown error"))
                    }
                } else {
                    val errorObj = bodyString?.let { runCatching { JSONObject(it) }.getOrNull() }
                    return@withContext AuthResult.Error(errorObj?.optString("error") ?: "HTTP ${response.code}: ${response.message}")
                }
            }
        } catch (e: Exception) {
            return@withContext AuthResult.Error(e.message ?: "Network error")
        }
    }

    suspend fun signup(name: String, email: String, password: String): AuthResult = withContext(Dispatchers.IO) {
        try {
            val jsonBody = JSONObject().apply {
                put("name", name)
                put("email", email)
                put("password", password)
            }

            val request = Request.Builder()
                .url("$baseUrl/api/projects/$projectId/signup")
                .post(jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string()
                if (response.isSuccessful && bodyString != null) {
                    val json = JSONObject(bodyString)
                    if (json.optBoolean("success")) {
                        val userObj = json.getJSONObject("user")
                        val user = AuthUser(
                            id = userObj.optString("id"),
                            projectId = userObj.optString("project_id"),
                            name = userObj.optString("name"),
                            email = userObj.optString("email"),
                            createdAt = userObj.optString("created_at")
                        )
                        return@withContext AuthResult.Success(user, "Signup successful")
                    } else {
                        return@withContext AuthResult.Error(json.optString("error", "Unknown error"))
                    }
                } else {
                    val errorObj = bodyString?.let { runCatching { JSONObject(it) }.getOrNull() }
                    return@withContext AuthResult.Error(errorObj?.optString("error") ?: "HTTP ${response.code}: ${response.message}")
                }
            }
        } catch (e: Exception) {
            return@withContext AuthResult.Error(e.message ?: "Network error")
        }
    }
}
