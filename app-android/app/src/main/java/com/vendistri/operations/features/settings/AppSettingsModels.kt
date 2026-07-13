package com.vendistri.operations.features.settings

enum class AppAppearancePreference(val rawValue: String, val label: String) {
    System("system", "System"),
    Light("light", "Light"),
    Dark("dark", "Dark");

    fun resolvesDarkTheme(systemIsDark: Boolean): Boolean {
        return when (this) {
            System -> systemIsDark
            Light -> false
            Dark -> true
        }
    }

    companion object {
        fun fromRawValue(value: String?): AppAppearancePreference {
            return entries.firstOrNull { it.rawValue == value } ?: System
        }
    }
}

enum class NavigationAudioPreference(val rawValue: String, val label: String) {
    Sound("sound", "Sound"),
    Silent("silent", "Silent");

    val isMuted: Boolean
        get() = this == Silent

    companion object {
        fun fromRawValue(value: String?): NavigationAudioPreference {
            return entries.firstOrNull { it.rawValue == value } ?: Sound
        }
    }
}

enum class TimeFormatPreference(val rawValue: String, val label: String) {
    System("system", "System"),
    TwelveHour("twelveHour", "12-hour"),
    TwentyFourHour("twentyFourHour", "24-hour");

    companion object {
        fun fromRawValue(value: String?): TimeFormatPreference {
            return entries.firstOrNull { it.rawValue == value } ?: System
        }
    }
}

data class AppSettingsState(
    val autoCalcCommission: Boolean = false,
    val autoFillRefillFinalStock: Boolean = false,
    val appearancePreference: AppAppearancePreference = AppAppearancePreference.System,
    val navigationAudioPreference: NavigationAudioPreference = NavigationAudioPreference.Sound,
    val timeFormatPreference: TimeFormatPreference = TimeFormatPreference.System
)
