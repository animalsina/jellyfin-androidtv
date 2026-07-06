package org.jellyfin.androidtv.data.repository

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.model.ExternalCatalogItem
import org.jellyfin.sdk.model.api.BaseItemKind
import org.json.JSONObject
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.TimeUnit

class ExternalCatalogRepository(
	context: Context,
) {
	private val appContext = context.applicationContext
	private val localCache = appContext.getSharedPreferences(CACHE_NAME, Context.MODE_PRIVATE)
	private var memoryCache: Pair<Long, List<ExternalCatalogItem>>? = null
	private var newReleasesMemoryCache: Pair<Long, List<ExternalCatalogItem>>? = null

	suspend fun loadHomeCatalog(limit: Int = DEFAULT_LIMIT): List<ExternalCatalogItem> = withContext(Dispatchers.IO) {
		val now = System.currentTimeMillis()
		memoryCache?.let { (timestamp, items) ->
			if (now - timestamp < MEMORY_CACHE_TTL_MS && items.isNotEmpty()) return@withContext items.take(limit)
		}

		val items = buildList {
			addAll(loadPlutoVod().take(limit))
		}
			.distinctBy { normalizeTitle(it.title) to it.providerId }
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

	suspend fun loadNewReleases(
		localMatches: Map<String, UUID> = emptyMap(),
		limit: Int = NEW_RELEASES_LIMIT,
	): List<ExternalCatalogItem> = withContext(Dispatchers.IO) {
		val now = System.currentTimeMillis()
		newReleasesMemoryCache?.let { (timestamp, items) ->
			if (now - timestamp < NEW_RELEASES_MEMORY_CACHE_TTL_MS && items.isNotEmpty()) {
				return@withContext applyLocalMatches(items, localMatches).take(limit)
			}
		}

		val onlineItems = runCatching { loadWikidataNewReleases(limit * 2) }
			.onFailure { Timber.w(it, "Unable to load online new releases") }
			.getOrDefault(emptyList())
			.distinctBy { normalizeTitle(it.title) }
			.take(limit)

		if (onlineItems.isNotEmpty()) newReleasesMemoryCache = now to onlineItems
		applyLocalMatches(onlineItems, localMatches).take(limit)
	}

	private fun applyLocalMatches(items: List<ExternalCatalogItem>, localMatches: Map<String, UUID>): List<ExternalCatalogItem> =
		items.map { item ->
			val localId = localMatches[normalizeTitle(item.title)]
			when (localId) {
				null -> item
				else -> item.copy(
					localItemId = localId,
					availabilityNote = appContext.getString(R.string.lbl_external_catalog_available_on_server),
				)
			}
		}

	private fun loadPlutoVod(): List<ExternalCatalogItem> {
		return PLUTO_VOD_SOURCES.asSequence()
			.mapNotNull { source -> runCatching { fetchText(source.url) to source }.getOrNull() }
			.map { (body, source) -> parseM3u(body, source) }
			.firstOrNull { it.isNotEmpty() }
			.orEmpty()
	}

	private fun loadWikidataNewReleases(limit: Int): List<ExternalCatalogItem> {
		val from = LocalDate.now().minusDays(60)
		val to = LocalDate.now().plusDays(180)
		val query = """
			PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
			SELECT ?item ?itemLabel ?release ?image WHERE {
			  ?item wdt:P31/wdt:P279* wd:Q11424.
			  ?item wdt:P577 ?release.
			  FILTER(?release >= "${from}T00:00:00Z"^^xsd:dateTime && ?release <= "${to}T23:59:59Z"^^xsd:dateTime)
			  OPTIONAL { ?item wdt:P18 ?image. }
			  SERVICE wikibase:label { bd:serviceParam wikibase:language "it,en". }
			}
			ORDER BY DESC(?release)
			LIMIT $limit
		""".trimIndent()
		val encodedQuery = URLEncoder.encode(query, "UTF-8")
		val body = fetchText("https://query.wikidata.org/sparql?format=json&query=$encodedQuery", accept = "application/sparql-results+json")
		val bindings = JSONObject(body)
			.getJSONObject("results")
			.getJSONArray("bindings")

		return buildList {
			for (index in 0 until bindings.length()) {
				val binding = bindings.getJSONObject(index)
				val title = binding.optJSONObject("itemLabel")?.optString("value").orEmpty().trim()
				if (title.isNotBlank() && !title.matches(WIKIDATA_QID_REGEX)) {
					val releaseDate = binding.optJSONObject("release")?.optString("value")
						?.substringBefore('T')
					val imageUrl = binding.optJSONObject("image")?.optString("value")?.takeIf { it.isNotBlank() }
					val itemUrl = binding.optJSONObject("item")?.optString("value")?.takeIf { it.isNotBlank() }

					add(
						ExternalCatalogItem(
							providerId = "wikidata-new-releases-it",
							providerName = appContext.getString(R.string.home_section_online_new_releases),
							title = title,
							type = BaseItemKind.MOVIE,
							detailUrl = itemUrl,
							posterUrl = imageUrl,
							backdropUrl = imageUrl,
							group = appContext.getString(R.string.lbl_external_catalog_new_release_group),
							isFree = false,
							releaseDate = releaseDate,
							availabilityNote = releaseDate?.let { appContext.getString(R.string.lbl_external_catalog_release_date, it) },
						)
					)
				}
			}
		}
	}

	private fun fetchText(url: String, accept: String = "text/plain,application/json,*/*"): String {
		val connection = (URL(url).openConnection() as HttpURLConnection).apply {
			connectTimeout = NETWORK_TIMEOUT_MS
			readTimeout = NETWORK_TIMEOUT_MS
			requestMethod = "GET"
			setRequestProperty("Accept", accept)
			setRequestProperty("User-Agent", "SuperJelly Android TV")
		}

		return try {
			if (connection.responseCode >= 400) error("HTTP ${connection.responseCode} while loading $url")
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
						val artwork = item.logo ?: item.backdrop
						result += ExternalCatalogItem(
							providerId = source.id,
							providerName = source.name,
							title = item.title,
							type = BaseItemKind.MOVIE,
							streamUrl = line,
							detailUrl = source.detailUrl,
							posterUrl = artwork,
							backdropUrl = item.backdrop ?: artwork,
							group = item.group,
							isFree = true,
							availabilityNote = appContext.getString(R.string.lbl_external_catalog_free_badge),
							trailerUrl = item.trailer,
						)
					}
				}
			}
		}

		return result
			.filter { it.title.isNotBlank() && !it.streamUrl.isNullOrBlank() }
			.distinctBy { normalizeTitle(it.title) }
	}

	private fun parseExtInf(line: String): PendingM3uItem {
		val attributes = ATTRIBUTE_REGEX.findAll(line).associate { match ->
			match.groupValues[1] to match.groupValues[2]
		}
		val title = line.substringAfterLast(',', missingDelimiterValue = attributes["tvg-name"].orEmpty()).trim()
		return PendingM3uItem(
			title = title.ifBlank { attributes["tvg-name"].orEmpty() },
			logo = firstNotBlank(attributes["tvg-logo"], attributes["logo"], attributes["tvc-guide-stationid"]),
			backdrop = firstNotBlank(attributes["tvc-guide-art"], attributes["art"], attributes["fanart"]),
			group = attributes["group-title"],
			trailer = attributes["trailer"],
		)
	}

	private fun firstNotBlank(vararg values: String?): String? = values.firstOrNull { !it.isNullOrBlank() }

	private data class PendingM3uItem(
		val title: String,
		val logo: String?,
		val backdrop: String?,
		val group: String?,
		val trailer: String?,
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
		private const val NEW_RELEASES_LIMIT = 36
		private val MEMORY_CACHE_TTL_MS = TimeUnit.MINUTES.toMillis(30)
		private val NEW_RELEASES_MEMORY_CACHE_TTL_MS = TimeUnit.MINUTES.toMillis(15)
		private const val NETWORK_TIMEOUT_MS = 5_000
		private val ATTRIBUTE_REGEX = "([a-zA-Z0-9_-]+)=\"([^\"]*)\"".toRegex()
		private val WIKIDATA_QID_REGEX = "Q\\d+".toRegex()

		private val PLUTO_VOD_SOURCES = listOf(
			CatalogSource(
				id = "pluto-tv-it-vod",
				name = "Pluto TV",
				url = "https://raw.githubusercontent.com/OwnerPlugins/pluto-tv-m3u/main/pluto-vod-IT.m3u",
				detailUrl = "https://pluto.tv/it/on-demand",
			),
			CatalogSource(
				id = "pluto-tv-it-live",
				name = "Pluto TV",
				url = "https://i.mjh.nz/PlutoTV/it.m3u8",
				detailUrl = "https://pluto.tv/it/live-tv",
			),
		)

		fun normalizeTitle(title: String): String = title
			.lowercase()
			.replace(Regex("\\([^)]*\\)"), " ")
			.replace(Regex("[^a-z0-9àèéìòù]+"), " ")
			.trim()
		}
}
