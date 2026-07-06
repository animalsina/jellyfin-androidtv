package org.jellyfin.androidtv.streaming

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.constant.ExternalProviderOption
import org.jellyfin.androidtv.data.model.ExternalCatalogItem
import org.jellyfin.androidtv.data.repository.ExternalCatalogRepository
import org.jellyfin.androidtv.ui.playback.ExternalStreamPlayerActivity
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.koin.java.KoinJavaComponent
import timber.log.Timber
import java.net.URLEncoder

object StreamingAvailabilityHelper {
	private val externalCatalogRepository by KoinJavaComponent.inject<ExternalCatalogRepository>(ExternalCatalogRepository::class.java)
	private val availabilityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

	@JvmStatic
	fun shouldOfferAvailabilityButton(context: Context, item: BaseItemDto?): Boolean {
		if (!isSupportedItem(item)) return false
		return getTitle(item).isNotBlank()
	}

	@JvmStatic
	fun openAvailabilityMenu(context: Context, item: BaseItemDto?) {
		if (!isSupportedItem(item)) return
		val title = getTitle(item)
		if (title.isBlank()) return

		availabilityScope.launch {
			val catalogMatches = withContext(Dispatchers.IO) {
				runCatching { externalCatalogRepository.findFreeCatalogMatches(title) }
					.onFailure { Timber.w(it, "Unable to search free external catalog matches") }
					.getOrDefault(emptyList())
			}
			val actions = buildActions(context, item, catalogMatches)
			if (actions.isEmpty()) {
				Toast.makeText(context, R.string.msg_streaming_no_installed_provider, Toast.LENGTH_SHORT).show()
				return@launch
			}

			AlertDialog.Builder(context)
				.setTitle(context.getString(R.string.lbl_streaming_availability))
				.setItems(actions.map { it.label }.toTypedArray()) { _, index ->
					openAction(context, actions[index])
				}
				.show()
		}
	}

	private fun isSupportedItem(item: BaseItemDto?): Boolean = when (item?.type) {
		BaseItemKind.MOVIE, BaseItemKind.SERIES, BaseItemKind.EPISODE, BaseItemKind.VIDEO -> true
		else -> false
	}

	private fun getTitle(item: BaseItemDto?): String = item
		?.let { if (it.type == BaseItemKind.EPISODE) it.seriesName ?: it.name else it.name }
		.orEmpty()
		.trim()

	private fun buildActions(context: Context, item: BaseItemDto?, catalogMatches: List<ExternalCatalogItem>): List<ProviderAction> {
		val title = getTitle(item)
		if (title.isBlank()) return emptyList()

		val searchQuery = listOfNotNull(title, item?.productionYear?.toString()).joinToString(" ")
		val encoded = URLEncoder.encode(searchQuery, "UTF-8")
		val actions = mutableListOf<ProviderAction>()

		catalogMatches.forEach { catalogItem ->
			val streamUrl = catalogItem.streamUrl
			if (!streamUrl.isNullOrBlank()) {
				actions += ProviderAction(
					label = context.getString(R.string.lbl_streaming_play_free_on, catalogItem.providerName),
					url = streamUrl,
					internalStream = true,
					title = catalogItem.title,
				)
			}
		}

		// JustWatch/TMDb style provider data needs a licensed/partner API key for exact availability.
		// Until that is configured, this is the safest generic availability entry point.
		actions += ProviderAction(
			label = context.getString(R.string.lbl_streaming_search_free_sources),
			url = "https://www.justwatch.com/it/cerca?q=$encoded",
		)

		installedPackage(context, ExternalProviderOption.PLUTO_TV_PACKAGE, "tv.pluto.androidtv")?.let { pkg ->
			actions += ProviderAction(
				label = context.getString(R.string.lbl_streaming_search_on_provider, "Pluto TV"),
				url = "https://pluto.tv/it/search?search=$encoded",
				packageName = pkg,
			)
		}

		installedPackage(context, ExternalProviderOption.RAIPLAY_PACKAGE, "it.rainet")?.let { pkg ->
			actions += ProviderAction(
				label = context.getString(R.string.lbl_streaming_search_on_provider, "RaiPlay"),
				url = "https://www.raiplay.it/ricerca.html?q=$encoded",
				packageName = pkg,
			)
		}

		installedPackage(context, ExternalProviderOption.PRIME_VIDEO_PACKAGE, "com.amazon.avod.thirdpartyclient")?.let { pkg ->
			actions += ProviderAction(
				label = context.getString(R.string.lbl_streaming_search_on_provider, "Prime Video"),
				url = "https://app.primevideo.com/search/ref=atv_nb_sr?phrase=$encoded",
				packageName = pkg,
			)
		}

		installedPackage(context, ExternalProviderOption.NETFLIX_PACKAGE)?.let { pkg ->
			actions += ProviderAction(
				label = context.getString(R.string.lbl_streaming_search_on_provider, "Netflix"),
				url = "https://www.netflix.com/search?q=$encoded",
				packageName = pkg,
			)
		}

		return actions
	}

	private fun installedPackage(context: Context, vararg packageNames: String): String? {
		return packageNames.firstOrNull { packageName ->
			context.packageManager.getLaunchIntentForPackage(packageName) != null
		}
	}

	private fun openAction(context: Context, action: ProviderAction) {
		if (action.internalStream) {
			try {
				context.startActivity(
					Intent(context, ExternalStreamPlayerActivity::class.java)
						.putExtra(ExternalStreamPlayerActivity.EXTRA_URL, action.url)
						.putExtra(ExternalStreamPlayerActivity.EXTRA_TITLE, action.title)
						.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
				)
				return
			} catch (error: Exception) {
				Timber.w(error, "Unable to open free stream internally")
			}
		}

		try {
			val intent = Intent(Intent.ACTION_VIEW, Uri.parse(action.url))
				.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
			if (action.packageName != null) intent.setPackage(action.packageName)
			context.startActivity(intent)
			return
		} catch (error: Exception) {
			Timber.w(error, "Unable to open provider search")
		}

		try {
			context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(action.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
		} catch (error: Exception) {
			Toast.makeText(context, R.string.msg_external_provider_unavailable, Toast.LENGTH_SHORT).show()
			Timber.w(error, "Unable to open provider fallback")
		}
	}

	private data class ProviderAction(
		val label: String,
		val url: String,
		val packageName: String? = null,
		val internalStream: Boolean = false,
		val title: String = "",
	)
}
