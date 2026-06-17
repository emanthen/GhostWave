package com.ghostwave.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists user-facing app settings via DataStore.
 * All defaults are privacy-first: notifications on, previews off, lock on.
 */
@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private object Keys {
        val NOTIFICATIONS_ENABLED    = booleanPreferencesKey("notif_enabled")
        val NOTIFICATION_PREVIEWS    = booleanPreferencesKey("notif_previews")
        val APP_LOCK_ENABLED         = booleanPreferencesKey("app_lock")
        val THEME_ALWAYS_DARK        = booleanPreferencesKey("theme_dark")
        val LINK_PREVIEWS_ENABLED    = booleanPreferencesKey("link_previews")
        val READ_RECEIPTS_ENABLED    = booleanPreferencesKey("read_receipts")
        val TYPING_INDICATORS        = booleanPreferencesKey("typing_indicators")
        val DEFAULT_DISAPPEAR_SECS   = longPreferencesKey("default_disappear_secs")
        val SCREEN_LOCK_TIMEOUT_SECS = longPreferencesKey("screen_lock_timeout_secs")
    }

    val notificationsEnabled: Flow<Boolean> =
        dataStore.data.map { it[Keys.NOTIFICATIONS_ENABLED] ?: true }

    val notificationPreviews: Flow<Boolean> =
        dataStore.data.map { it[Keys.NOTIFICATION_PREVIEWS] ?: false }

    val appLockEnabled: Flow<Boolean> =
        dataStore.data.map { it[Keys.APP_LOCK_ENABLED] ?: true }

    val linkPreviewsEnabled: Flow<Boolean> =
        dataStore.data.map { it[Keys.LINK_PREVIEWS_ENABLED] ?: false }

    val readReceiptsEnabled: Flow<Boolean> =
        dataStore.data.map { it[Keys.READ_RECEIPTS_ENABLED] ?: true }

    val typingIndicators: Flow<Boolean> =
        dataStore.data.map { it[Keys.TYPING_INDICATORS] ?: true }

    val defaultDisappearSecs: Flow<Long?> =
        dataStore.data.map { it[Keys.DEFAULT_DISAPPEAR_SECS] }

    val screenLockTimeoutSecs: Flow<Long> =
        dataStore.data.map { it[Keys.SCREEN_LOCK_TIMEOUT_SECS] ?: 60L }

    suspend fun setNotificationsEnabled(enabled: Boolean) =
        dataStore.edit { it[Keys.NOTIFICATIONS_ENABLED] = enabled }

    suspend fun setNotificationPreviews(enabled: Boolean) =
        dataStore.edit { it[Keys.NOTIFICATION_PREVIEWS] = enabled }

    suspend fun setAppLockEnabled(enabled: Boolean) =
        dataStore.edit { it[Keys.APP_LOCK_ENABLED] = enabled }

    suspend fun setLinkPreviewsEnabled(enabled: Boolean) =
        dataStore.edit { it[Keys.LINK_PREVIEWS_ENABLED] = enabled }

    suspend fun setReadReceiptsEnabled(enabled: Boolean) =
        dataStore.edit { it[Keys.READ_RECEIPTS_ENABLED] = enabled }

    suspend fun setTypingIndicators(enabled: Boolean) =
        dataStore.edit { it[Keys.TYPING_INDICATORS] = enabled }

    suspend fun setDefaultDisappearSecs(secs: Long?) =
        dataStore.edit {
            if (secs == null) it.remove(Keys.DEFAULT_DISAPPEAR_SECS)
            else it[Keys.DEFAULT_DISAPPEAR_SECS] = secs
        }

    suspend fun setScreenLockTimeoutSecs(secs: Long) =
        dataStore.edit { it[Keys.SCREEN_LOCK_TIMEOUT_SECS] = secs }
}
