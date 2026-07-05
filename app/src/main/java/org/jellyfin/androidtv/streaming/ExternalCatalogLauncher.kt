package org.jellyfin.androidtv.streaming

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.itemhandling.ExternalCatalogBaseRowItem
import timber.log.Timber

object ExternalCatalogLauncher {
	@JvmStatic
	fun open(context: Context, rowItem: ExternalCatalogBaseRowItem) {
		val item = rowItem.catalogItem
		val streamUrl = item.streamUrl
		if (!streamUrl.isNullOrBlank()) {
			if (openStream(context, streamUrl)) return
		}

		val detailUrl = item.detailUrl
		if (!detailUrl.isNullOrBlank() && openUrl(context, detailUrl)) return

		Toast.makeText(context, R.string.msg_external_catalog_unavailable, Toast.LENGTH_SHORT).show()
	}

	private fun openStream(context: Context, url: String): Boolean {
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

	private fun openUrl(context: Context, url: String): Boolean {
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
}
