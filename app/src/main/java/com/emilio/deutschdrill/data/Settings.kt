package com.emilio.deutschdrill.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, DARK, LIGHT }

data class AppSettings(
    val sessionMinutes: Int = 3,
    val dailyGoalMinutes: Int = 6,
    val newCardsPerDay: Int = 15,
    val fillTimerEnabled: Boolean = true,
    val fillTimerSeconds: Int = 10,
    val caseSensitive: Boolean = false,
    val theme: ThemeMode = ThemeMode.DARK,
)

/** Small key-value settings; SharedPreferences keeps launch fast and avoids extra dependencies. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    var seedVersion: Int
        get() = prefs.getInt("seedVersion", 0)
        set(value) = prefs.edit().putInt("seedVersion", value).apply()

    fun update(transform: (AppSettings) -> AppSettings) {
        val s = transform(_settings.value)
        prefs.edit()
            .putInt("sessionMinutes", s.sessionMinutes)
            .putInt("dailyGoalMinutes", s.dailyGoalMinutes)
            .putInt("newCardsPerDay", s.newCardsPerDay)
            .putBoolean("fillTimerEnabled", s.fillTimerEnabled)
            .putInt("fillTimerSeconds", s.fillTimerSeconds)
            .putBoolean("caseSensitive", s.caseSensitive)
            .putString("theme", s.theme.name)
            .apply()
        _settings.value = s
    }

    private fun load(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            sessionMinutes = prefs.getInt("sessionMinutes", d.sessionMinutes),
            dailyGoalMinutes = prefs.getInt("dailyGoalMinutes", d.dailyGoalMinutes),
            newCardsPerDay = prefs.getInt("newCardsPerDay", d.newCardsPerDay),
            fillTimerEnabled = prefs.getBoolean("fillTimerEnabled", d.fillTimerEnabled),
            fillTimerSeconds = prefs.getInt("fillTimerSeconds", d.fillTimerSeconds),
            caseSensitive = prefs.getBoolean("caseSensitive", d.caseSensitive),
            theme = runCatching { ThemeMode.valueOf(prefs.getString("theme", null) ?: "") }.getOrDefault(d.theme),
        )
    }
}
