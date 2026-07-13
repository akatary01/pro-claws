package com.vendistri.operations.features.settings

import android.content.Context

class SharedPreferencesAppSettingsStorage(context: Context) : AppSettingsStorage {
    private val preferences = context.applicationContext.getSharedPreferences(
        "vendistri_settings",
        Context.MODE_PRIVATE
    )

    override fun read(): AppSettingsState {
        return AppSettingsState(
            autoCalcCommission = preferences.getBoolean(Keys.AutoCalcCommission, false),
            autoFillRefillFinalStock = preferences.getBoolean(Keys.AutoFillRefillFinalStock, false),
            appearancePreference = AppAppearancePreference.fromRawValue(
                preferences.getString(Keys.AppearancePreference, null)
            ),
            navigationAudioPreference = NavigationAudioPreference.fromRawValue(
                preferences.getString(Keys.NavigationAudioPreference, null)
            ),
            timeFormatPreference = TimeFormatPreference.fromRawValue(
                preferences.getString(Keys.TimeFormatPreference, null)
            )
        )
    }

    override fun write(state: AppSettingsState) {
        preferences.edit()
            .putBoolean(Keys.AutoCalcCommission, state.autoCalcCommission)
            .putBoolean(Keys.AutoFillRefillFinalStock, state.autoFillRefillFinalStock)
            .putString(Keys.AppearancePreference, state.appearancePreference.rawValue)
            .putString(Keys.NavigationAudioPreference, state.navigationAudioPreference.rawValue)
            .putString(Keys.TimeFormatPreference, state.timeFormatPreference.rawValue)
            .apply()
    }

    private object Keys {
        const val AutoCalcCommission = "vendistri.settings.autoCalcCommission"
        const val AutoFillRefillFinalStock = "vendistri.settings.autoFillRefillFinalStock"
        const val AppearancePreference = "vendistri.settings.appearancePreference"
        const val NavigationAudioPreference = "vendistri.settings.navigationAudioPreference"
        const val TimeFormatPreference = "vendistri.settings.timeFormatPreference"
    }
}
