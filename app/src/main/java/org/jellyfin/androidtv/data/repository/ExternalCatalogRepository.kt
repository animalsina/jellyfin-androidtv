package org.jellyfin.androidtv.data.repository

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.data.model.ExternalCatalogItem
import org.jellyfin.sdk.model.api.BaseItemKind
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

class ExternalCatalogRepository(
	context: Context,
) {
	private val localCache = context.applicationContext.getSharedPreferences(CACHE_NAME, Context.MODE_PRIVATE)
	private var memoryCache: Pair<Long, List<ExternalCatalogItem>>? = null

	suspend fun loadHomeCatalog(limit: Int = DEFAULT_LIMIT): List<ExternalCatalogItem> = withContext(Dispatchers.IO) {
		val now = System.currentTimeMillis()
		memoryCache?.let { (timestamp, items) ->
			if (now - timestamp < MEMORY_CACHE_TTL_MS && items.isNotEmpty()) return@withContext items.take(limit)
		}

		val items = buildList {
			addAll(loadPlutoVod().take(limit))
		}
			.distinctBy { it.title.lowercase() to it.providerId }
			.take(limit)

		if (items.isNotEmpty()) {
			memoryCache = now to items
			localCache.edit()
				.putLong(KEY_LAST_SUCCESS, now)
				.putString(KEY_LAST_TITLES, items.joinToString("\n") { it.title })
				.apply()
		}

		items
	}

	private fun loadPlutoVod(): List<ExternalCatalogItem> {
		return PLUTO_VOD_SOURCES.asSequence()
			.mapNotNull { source -> runCatching { fetchM3u(source.url) to source }.getOrNull() }
			.map { (body, source) -> parseM3u(body, source) }
			.firstOrNull { it.isNotEmpty() }
			.orEmpty()
	}

	private fun fetchM3u(url: String): String {
		val connection = (URL(url).openConnection() as HttpURLConnection).apply {
			connectTimeout = NETWORK_TIMEOUT_MS
			readTimeout = NETWORK_TIMEOUT_MS
			requestMethod = "GET"
			setRequestProperty("User-Agent", "SuperJelly Android TV")
		}

		return try {
			connection.inputStream.bufferedReader().use { it.readText() }
		} finally {
			connection.disconnect()
		}
	}

	private fun parseM3u(body: String, source: CatalogSource): List<ExternalCatalogItem> {
		val result = mutableListOf<ExternalCatalogItem>()
		var pending: PendingM3uItem? = null

		body.lineSequence().forEach { rawLine ->
			val line = rawLine.trim()
			when {
				line.startsWith("#EXTINF", ignoreCase = true) -> pending = parseExtInf(line)
				line.startsWith("http", ignoreCase = true) -> {
					val item = pending
					pending = null
					if (item != null) {
						result += ExternalCatalogItem(
							providerId = source.id,
							providerName = source.name,
							title = item.title,
							type = BaseItemKind.MOVIE,
							streamUrl = line,
							detailUrl = source.detailUrl,
							posterUrl = item.logo,
							backdropUrl = item.logo,
							group = item.group,
							isFree = true,
						)
					}
				}
			}
		}

		return result
			.filter { it.title.isNotBlank() && !it.streamUrl.isNullOrBlank() }
			.distinctBy { it.title.lowercase() }
	}

	private fun parseExtInf(line: String): PendingM3uItem {
		val attributes = ATTRIBUTE_REGEX.findAll(line).associate { match ->
			match.groupValues[1] to match.groupValues[2]
		}
		val title = line.substringAfterLast(',', missingDelimiterValue = attributes["tvg-name"].orEmpty()).trim()
		return PendingM3uItem(
			title = title.ifBlank { attributes["tvg-name"].orEmpty() },
			logo = attributes["tvg-logo"],
			group = attributes["group-title"],
		)
	}

	private data class PendingM3uItem(
		val title: String,
		val logo: String?,
		val group: String?,
	)

	private data class CatalogSource(
		val id: String,
		val name: String,
		val url: String,
		val detailUrl: String,
	)

	companion object {
		private const val CACHE_NAME = "external_catalog_cache"
		private const val KEY_LAST_SUCCESS = "last_success"
		private const val KEY_LAST_TITLES = "last_titles"
		private const val DEFAULT_LIMIT = 48
		private val MEMORY_CACHE_TTL_MS = TimeUnit.MINUTES.toMillis(30)
		private const val NETWORK_TIMEOUT_MS = 5_000
		private val ATTRIBUTE_REGEX = """([a-zA-Z0-9_-]+)=\"([^\"]*)\""".toRegex()

		private val PLUTO_VOD_SOURCES = listOf(
			CatalogSource(
				id = "pluto-tv-it-vod",
				name = "Pluto TV",
				url = "https://raw.githubusercontent.com/OwnerPlugins/pluto-tv-m3u/main/pluto-vod-IT.m3u",
				detailUrl = "https://pluto.tv/it/on-demand",
			),
		)
	}
}
