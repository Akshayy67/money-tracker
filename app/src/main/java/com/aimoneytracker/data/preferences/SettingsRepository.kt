package com.aimoneytracker.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "settings")

/** App settings/preferences backed by DataStore (§16 digest config, §21 privacy/security, etc.). */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val LOCAL_ONLY = booleanPreferencesKey("local_only_mode")
        val APP_LOCK = booleanPreferencesKey("app_lock_enabled")
        val BIOMETRIC = booleanPreferencesKey("biometric_enabled")
        val BLUR_RECENTS = booleanPreferencesKey("blur_in_recents")
        val DARK_MODE = stringPreferencesKey("dark_mode") // SYSTEM/LIGHT/DARK
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val LANGUAGE = stringPreferencesKey("language") // en/hi/system

        val WEEKLY_DIGEST = booleanPreferencesKey("weekly_digest")
        val MONTHLY_DIGEST = booleanPreferencesKey("monthly_digest")
        val WEEKLY_DIGEST_DOW = intPreferencesKey("weekly_digest_dow") // 1=Mon..7=Sun
        val DIGEST_HOUR = intPreferencesKey("digest_hour")
        val DIGEST_INCLUDE_FORECAST = booleanPreferencesKey("digest_include_forecast")

        val LOW_BALANCE_THRESHOLD = longPreferencesKey("low_balance_threshold")
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val BACKFILL_DONE = booleanPreferencesKey("backfill_done")
        val AUTO_BACKUP = booleanPreferencesKey("auto_backup")
        val REVIEW_CONFIDENCE_THRESHOLD = intPreferencesKey("review_threshold_pct")
    }

    data class Settings(
        val localOnly: Boolean = false,
        val appLock: Boolean = false,
        val biometric: Boolean = false,
        val blurInRecents: Boolean = true,
        val darkMode: String = "SYSTEM",
        val dynamicColor: Boolean = true,
        val language: String = "system",
        val weeklyDigest: Boolean = true,
        val monthlyDigest: Boolean = true,
        val weeklyDigestDow: Int = 7,
        val digestHour: Int = 19,
        val digestIncludeForecast: Boolean = true,
        val lowBalanceThreshold: Long = 100_000, // ₹1,000 default
        val onboarded: Boolean = false,
        val backfillDone: Boolean = false,
        val autoBackup: Boolean = true,
        val reviewThresholdPct: Int = 60,
    )

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            localOnly = p[Keys.LOCAL_ONLY] ?: false,
            appLock = p[Keys.APP_LOCK] ?: false,
            biometric = p[Keys.BIOMETRIC] ?: false,
            blurInRecents = p[Keys.BLUR_RECENTS] ?: true,
            darkMode = p[Keys.DARK_MODE] ?: "SYSTEM",
            dynamicColor = p[Keys.DYNAMIC_COLOR] ?: true,
            language = p[Keys.LANGUAGE] ?: "system",
            weeklyDigest = p[Keys.WEEKLY_DIGEST] ?: true,
            monthlyDigest = p[Keys.MONTHLY_DIGEST] ?: true,
            weeklyDigestDow = p[Keys.WEEKLY_DIGEST_DOW] ?: 7,
            digestHour = p[Keys.DIGEST_HOUR] ?: 19,
            digestIncludeForecast = p[Keys.DIGEST_INCLUDE_FORECAST] ?: true,
            lowBalanceThreshold = p[Keys.LOW_BALANCE_THRESHOLD] ?: 100_000,
            onboarded = p[Keys.ONBOARDED] ?: false,
            backfillDone = p[Keys.BACKFILL_DONE] ?: false,
            autoBackup = p[Keys.AUTO_BACKUP] ?: true,
            reviewThresholdPct = p[Keys.REVIEW_CONFIDENCE_THRESHOLD] ?: 60,
        )
    }

    suspend fun setLocalOnly(value: Boolean) = put(Keys.LOCAL_ONLY, value)
    suspend fun setAppLock(value: Boolean) = put(Keys.APP_LOCK, value)
    suspend fun setBiometric(value: Boolean) = put(Keys.BIOMETRIC, value)
    suspend fun setBlurInRecents(value: Boolean) = put(Keys.BLUR_RECENTS, value)
    suspend fun setDarkMode(value: String) = put(Keys.DARK_MODE, value)
    suspend fun setDynamicColor(value: Boolean) = put(Keys.DYNAMIC_COLOR, value)
    suspend fun setLanguage(value: String) = put(Keys.LANGUAGE, value)
    suspend fun setWeeklyDigest(value: Boolean) = put(Keys.WEEKLY_DIGEST, value)
    suspend fun setMonthlyDigest(value: Boolean) = put(Keys.MONTHLY_DIGEST, value)
    suspend fun setWeeklyDigestDow(value: Int) = put(Keys.WEEKLY_DIGEST_DOW, value)
    suspend fun setDigestHour(value: Int) = put(Keys.DIGEST_HOUR, value)
    suspend fun setDigestIncludeForecast(value: Boolean) = put(Keys.DIGEST_INCLUDE_FORECAST, value)
    suspend fun setLowBalanceThreshold(value: Long) = put(Keys.LOW_BALANCE_THRESHOLD, value)
    suspend fun setOnboarded(value: Boolean) = put(Keys.ONBOARDED, value)
    suspend fun setBackfillDone(value: Boolean) = put(Keys.BACKFILL_DONE, value)
    suspend fun setAutoBackup(value: Boolean) = put(Keys.AUTO_BACKUP, value)
    suspend fun setReviewThreshold(value: Int) = put(Keys.REVIEW_CONFIDENCE_THRESHOLD, value)

    private suspend fun <T> put(key: androidx.datastore.preferences.core.Preferences.Key<T>, value: T) {
        context.dataStore.edit { it[key] = value }
    }
}
