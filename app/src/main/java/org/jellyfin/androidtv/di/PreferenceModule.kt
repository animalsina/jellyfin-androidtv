package org.jellyfin.androidtv.di

import org.jellyfin.androidtv.preference.LiveTvPreferences
import org.jellyfin.androidtv.preference.PreferencesRepository
import org.jellyfin.androidtv.preference.SystemPreferences
import org.jellyfin.androidtv.preference.TelemetryPreferences
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.preference.UserSettingPreferences
import org.koin.dsl.module

val preferenceModule = module {
	single { PreferencesRepository(api = get(), liveTvPreferences = get(), userSettingPreferences = get(), context = get()) }

	single { LiveTvPreferences(api = get(), context = get()) }
	single { UserSettingPreferences(api = get(), userRepository = get(), context = get()) }
	single { UserPreferences(get()) }
	single { SystemPreferences(get()) }
	single { TelemetryPreferences(get()) }
}
