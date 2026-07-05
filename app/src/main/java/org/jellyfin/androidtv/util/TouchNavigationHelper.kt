package org.jellyfin.androidtv.util

import android.content.Context
import android.content.pm.PackageManager

/**
 * Small compatibility helper for running the Android TV UI on phones/tablets.
 *
 * Leanback is designed around a persistent focused item. On touch devices that are not
 * Android TV, that focus can fight normal scroll gestures and pull the home rows back
 * to the focused/selected card. We keep the normal focus model on TV devices and relax
 * it only for touch-first devices.
 */
object TouchNavigationHelper {
	@JvmStatic
	fun shouldUseTouchHomeNavigation(context: Context): Boolean {
		val packageManager = context.packageManager
		val isTv = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
			packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK_ONLY) ||
			packageManager.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
		val hasTouch = packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN) ||
			packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH)

		return hasTouch && !isTv
	}
}
