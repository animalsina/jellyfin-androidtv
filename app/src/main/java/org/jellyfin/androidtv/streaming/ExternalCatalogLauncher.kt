package org.jellyfin.androidtv.streaming

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.itemhandling.ExternalCatalogBaseRowItem
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.playback.ExternalStreamPlayerActivity
import org.koin.java.KoinJavaComponent
import timber.log.Timber

object ExternalCatalogLauncher {
	private val navigationRepository by KoinJavaComponent.inject<NavigationRepository>(NavigationRepository::class.java)

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
					openStreamInternal(context, item.streamUrl, item.title)
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
			openStreamExternal(context, url)
		}
	}

	private fun openStreamExternal(context: Context, url: String): Boolean {
		val intents = listOf(
			Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(url), "application/x-mpegURL"),
			Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(url), "video/*"),
			Intent(Intent.ACTION_VIEW, Uri.parse(url)),
		)

		for (intent in intents) {
			try {
				intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
				context.startActivity(intent)
				return true
			} catch (error: Exception) {
				Timber.w(error, "Unable to open external stream")
			}
		}

		return false
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
