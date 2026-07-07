package org.jellyfin.androidtv.streaming

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.repository.ExternalAppRepository
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.ui.itemhandling.ExternalCatalogBaseRowItem
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.playback.ExternalStreamPlayerActivity
import org.jellyfin.androidtv.util.componentName
import org.koin.java.KoinJavaComponent
import timber.log.Timber
import kotlin.time.Duration

object ExternalCatalogLauncher {
	private val navigationRepository by KoinJavaComponent.inject<NavigationRepository>(NavigationRepository::class.java)
	private val userPreferences by KoinJavaComponent.inject<UserPreferences>(UserPreferences::class.java)
	private val externalAppRepository by KoinJavaComponent.inject<ExternalAppRepository>(ExternalAppRepository::class.java)

	@JvmStatic
	fun open(context: Context, rowItem: ExternalCatalogBaseRowItem) {
		val item = rowItem.catalogItem
		val actions = buildList {
			item.localItemId?.let { localId ->
				add(Action(context.getString(R.string.lbl_external_catalog_open_local)) {
					navigationRepository.navigate(Destinations.itemDetails(localId))
				})
			}

			if (!item.streamUrl.isNullOrBlank()) {
				add(Action(context.getString(R.string.lbl_external_catalog_play_here)) {
					playStream(context, item.streamUrl, item.title)
				})
			}

			add(Action(context.getString(R.string.lbl_external_catalog_search_server)) {
				navigationRepository.navigate(Destinations.search(item.title))
			})

			if (!item.detailUrl.isNullOrBlank()) {
				add(Action(context.getString(R.string.lbl_external_catalog_open_provider, item.providerName)) {
					openUrl(context, item.detailUrl)
				})
			}
		}

		if (actions.isEmpty()) {
			Toast.makeText(context, R.string.msg_external_catalog_unavailable, Toast.LENGTH_SHORT).show()
			return
		}

		AlertDialog.Builder(context)
			.setTitle(item.title)
			.setItems(actions.map { it.label }.toTypedArray()) { _, index -> actions[index].run() }
			.show()
	}

	private fun playStream(context: Context, url: String, title: String) {
		if (userPreferences[UserPreferences.useExternalPlayer]) {
			if (!openStreamExternal(context, url, title)) {
				openStreamInternal(context, url, title)
			}
		} else {
			openStreamInternal(context, url, title)
		}
	}

	private fun openStreamInternal(context: Context, url: String, title: String): Boolean {
		return try {
			context.startActivity(
				Intent(context, ExternalStreamPlayerActivity::class.java)
					.putExtra(ExternalStreamPlayerActivity.EXTRA_URL, url)
					.putExtra(ExternalStreamPlayerActivity.EXTRA_TITLE, title)
					.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
			)
			true
		} catch (error: Exception) {
			Timber.w(error, "Unable to start internal stream player")
			openStreamExternal(context, url, title)
		}
	}

	private fun openStreamExternal(context: Context, url: String, title: String): Boolean {
		val intent = Intent(Intent.ACTION_VIEW).apply {
			setDataAndType(Uri.parse(url), "video/*")
			addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

			// Try to use the configured external player if available
			externalAppRepository.getCurrentExternalPlayerApp(context)?.componentName?.let {
				setComponent(it)
			}
		}

		// Try to populate extra fields for known players (like VLC/MX Player)
		try {
			val resolveInfo = context.packageManager.queryIntentActivities(intent, 0).firstOrNull()
			if (resolveInfo != null) {
				val playerApi = externalAppRepository.getExternalPlayerApi(resolveInfo.activityInfo)
				// Create a dummy play data for the external player API
				val playData = org.jellyfin.androidtv.ui.playback.external.ExternalPlayData(
					url = Uri.parse(url),
					title = title,
					fileName = null,
					externalSubtitles = emptyList(),
					position = Duration.ZERO
				)
				playerApi.populateIntent(intent, playData)
			}
		} catch (e: Exception) {
			Timber.w(e, "Failed to populate external player extras")
		}

		return try {
			context.startActivity(intent)
			true
		} catch (error: Exception) {
			Timber.w(error, "Unable to open external stream")

			// Fallback to any player if the specific one failed
			val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
				setDataAndType(Uri.parse(url), "video/*")
				addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
			}
			try {
				context.startActivity(fallbackIntent)
				true
			} catch (e: Exception) {
				false
			}
		}
	}

	private fun openUrl(context: Context, url: String?): Boolean {
		if (url.isNullOrBlank()) return false
		return try {
			context.startActivity(
				Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
			)
			true
		} catch (error: Exception) {
			Timber.w(error, "Unable to open external catalog URL %s", url)
			false
		}
	}

	private data class Action(
		val label: String,
		val run: () -> Unit,
	)
}
