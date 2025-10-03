package org.jellyfin.androidtv.preference

import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.constant.HomeSectionType
import org.jellyfin.androidtv.preference.store.DisplayPreferencesStore
import org.jellyfin.preference.enumPreference
import org.jellyfin.preference.intPreference
import org.jellyfin.sdk.api.client.ApiClient

class UserSettingPreferences(
	api: ApiClient,
	private val userRepository: UserRepository,
) : DisplayPreferencesStore(
	displayPreferencesId = "usersettings-default", // Will be updated dynamically
	api = api,
	app = "emby",
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
		val homesection4 = enumPreference("homesection4", HomeSectionType.NONE)
		val homesection5 = enumPreference("homesection5", HomeSectionType.NONE)
		val homesection6 = enumPreference("homesection6", HomeSectionType.NONE)
		val homesection7 = enumPreference("homesection7", HomeSectionType.NONE)
		val homesection8 = enumPreference("homesection8", HomeSectionType.NONE)
		val homesection9 = enumPreference("homesection9", HomeSectionType.NONE)
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
	)

	val activeHomesections
		get() = homesections
			.map(::get)
			.filterNot { it == HomeSectionType.NONE }
}
