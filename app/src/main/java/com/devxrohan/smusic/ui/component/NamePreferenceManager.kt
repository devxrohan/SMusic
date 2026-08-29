package com.devxrohan.smusic.ui.component

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.nameDataStore by preferencesDataStore("user_name_preferences")

@Singleton
class NamePreferenceManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val USER_NAME_KEY = stringPreferencesKey("user_name")
        private val NAME_SET_KEY = stringPreferencesKey("name_set_v58")
        private val ACCOUNT_EMAIL_KEY = stringPreferencesKey("accountEmail")
        private val PREVIOUS_GOOGLE_LOGIN_KEY = booleanPreferencesKey("previous_google_login")
        private val PREVIOUS_GOOGLE_EMAIL_KEY = stringPreferencesKey("previous_google_email")
    }

    val userName: Flow<String> = context.nameDataStore.data
        .map { preferences ->
            preferences[USER_NAME_KEY] ?: ""
        }

    val accountEmail: Flow<String> = context.nameDataStore.data
        .map { preferences ->
            preferences[ACCOUNT_EMAIL_KEY] ?: ""
        }

    val previousGoogleEmail: Flow<String> = context.nameDataStore.data
        .map { preferences ->
            preferences[PREVIOUS_GOOGLE_EMAIL_KEY] ?: ""
        }

    val hasPreviousGoogleLogin: Flow<Boolean> = context.nameDataStore.data
        .map { preferences ->
            preferences[PREVIOUS_GOOGLE_LOGIN_KEY] ?: !preferences[PREVIOUS_GOOGLE_EMAIL_KEY].isNullOrBlank()
        }

    val isNameSet: Flow<Boolean> = context.nameDataStore.data
        .map { preferences ->
            val isV58Set = preferences[NAME_SET_KEY]?.toBoolean() ?: false
            val hasOldName = !preferences[USER_NAME_KEY].isNullOrBlank()
            isV58Set || hasOldName
        }

    suspend fun saveUserName(name: String) {
        context.nameDataStore.edit { preferences ->
            preferences[USER_NAME_KEY] = name
            preferences[NAME_SET_KEY] = "true"
        }
    }

    suspend fun saveAccountEmail(email: String) {
        context.nameDataStore.edit { preferences ->
            preferences[ACCOUNT_EMAIL_KEY] = email
        }
    }

    suspend fun canUseGoogleEmail(email: String): Boolean {
        val normalizedEmail = email.normalizedEmail()
        var allowed = true
        context.nameDataStore.edit { preferences ->
            val previousEmail =
                preferences[PREVIOUS_GOOGLE_EMAIL_KEY].normalizedEmail()
                    ?: preferences[ACCOUNT_EMAIL_KEY].normalizedEmail()
            val hasPreviousLogin = preferences[PREVIOUS_GOOGLE_LOGIN_KEY] ?: !previousEmail.isNullOrBlank()
            if (hasPreviousLogin && !previousEmail.isNullOrBlank() && previousEmail != normalizedEmail) {
                allowed = false
            }
        }
        return allowed
    }

    suspend fun rememberGoogleLoginEmail(email: String) {
        val normalizedEmail = email.normalizedEmail() ?: return
        context.nameDataStore.edit { preferences ->
            preferences[ACCOUNT_EMAIL_KEY] = normalizedEmail
            if (preferences[PREVIOUS_GOOGLE_EMAIL_KEY].isNullOrBlank()) {
                preferences[PREVIOUS_GOOGLE_EMAIL_KEY] = normalizedEmail
            }
            preferences[PREVIOUS_GOOGLE_LOGIN_KEY] = true
        }
    }

    fun lockedEmailMessage(email: String): String =
        "You have previously used $email to sign in. Use the same email again. To sign in with a different email, reinstall the app or clear app data."

    private fun String?.normalizedEmail(): String? =
        this
            ?.trim()
            ?.lowercase()
            ?.takeIf { it.isNotBlank() && it != "null" }
}
