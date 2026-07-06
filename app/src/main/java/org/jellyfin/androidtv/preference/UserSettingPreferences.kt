package org.jellyfin.androidtv.preference

import android.content.Context
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.constant.HomeSectionType
import org.jellyfin.androidtv.preference.store.DisplayPreferencesStore
import org.jellyfin.preference.enumPreference
import org.jellyfin.preference.intPreference
import org.jellyfin.sdk.api.client.ApiClient

class UserSettingPreferences(
	api: ApiClient,
	private val userRepository: UserRepository,
	context: Context,
) : DisplayPreferencesStore(
	displayPreferencesId = "usersettings-default", // Will be updated dynamically
	api = api,
	app = "emby",
	context = context,
) {

	init {
		// Update displayPreferencesId when user changes
		updateDisplayPreferencesId()
	}

	private fun updateDisplayPreferencesId() {
		displayPreferencesId = "usersettings-${userRepository.currentUser.value?.id?.toString() ?: "default"}"
	}

	fun onUserChanged() {
		updateDisplayPreferencesId()
		clearCache() // Clear cache to force reload for new user
	}
	companion object {
		val skipBackLength = intPreference("skipBackLength", 10_000)
		val skipForwardLength = intPreference("skipForwardLength", 30_000)

		val homesection0 = enumPreference("homesection0", HomeSectionType.RESUME)
		val homesection1 = enumPreference("homesection1", HomeSectionType.NEXT_UP)
		val homesection2 = enumPreference("homesection2", HomeSectionType.RECOMMENDED_FOR_YOU)
		val homesection3 = enumPreference("homesection3", HomeSectionType.RECENTLY_RELEASED)
		val homesection4 = enumPreference("homesection4", HomeSectionType.MOOD_LIGHT)
		val homesection5 = enumPreference("homesection5", HomeSectionType.MOOD_SHORT)
		val homesection6 = enumPreference("homesection6", HomeSectionType.EXTERNAL_PROVIDERS)
		val homesection7 = enumPreference("homesection7", HomeSectionType.ONLINE_NEW_RELEASES)
		val homesection8 = enumPreference("homesection8", HomeSectionType.PLUTO_ACTION)
		val homesection9 = enumPreference("homesection9", HomeSectionType.PLUTO_COMEDY)
		val homesection10 = enumPreference("homesection10", HomeSectionType.PLUTO_DRAMA)
		val homesection11 = enumPreference("homesection11", HomeSectionType.RAIPLAY_FILM)
		val homesection12 = enumPreference("homesection12", HomeSectionType.RAIPLAY_SERIES)
		val homesection13 = enumPreference("homesection13", HomeSectionType.NONE)
		val homesection14 = enumPreference("homesection14", HomeSectionType.NONE)
		val homesection15 = enumPreference("homesection15", HomeSectionType.NONE)
		val homesection16 = enumPreference("homesection16", HomeSectionType.NONE)
		val homesection17 = enumPreference("homesection17", HomeSectionType.NONE)
		val homesection18 = enumPreference("homesection18", HomeSectionType.NONE)
		val homesection19 = enumPreference("homesection19", HomeSectionType.NONE)
	}

	val homesections = listOf(
		homesection0,
		homesection1,
		homesection2,
		homesection3,
		homesection4,
		homesection5,
		homesection6,
		homesection7,
		homesection8,
		homesection9,
		homesection10,
		homesection11,
		homesection12,
		homesection13,
		homesection14,
		homesection15,
		homesection16,
		homesection17,
		homesection18,
		homesection19,
	)

	val activeHomesections
		get() = homesections
			.map(::get)
			.filterNot { it == HomeSectionType.NONE }
}
