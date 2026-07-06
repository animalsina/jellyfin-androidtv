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
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

class ExternalCatalogRepository(
	context: Context,
) {
	private val appContext = context.applicationContext
	private val localCache = appContext.getSharedPreferences(CACHE_NAME, Context.MODE_PRIVATE)
	private var memoryCache: Pair<Long, List<ExternalCatalogItem>>? = null
	private var newReleasesMemoryCache: Pair<Long, List<ExternalCatalogItem>>? = null
	private val metadataMemoryCache = mutableMapOf<String, CatalogMetadata>()

	suspend fun loadHomeCatalog(limit: Int = DEFAULT_LIMIT): List<ExternalCatalogItem> = withContext(Dispatchers.IO) {
		val now = System.currentTimeMillis()
		memoryCache?.let { (timestamp, items) ->
			if (now - timestamp < MEMORY_CACHE_TTL_MS && items.isNotEmpty()) return@withContext items.take(limit)
		}

		val items = buildList {
			addAll(loadPlutoVod())
			addAll(loadRaiPlayCatalogs())
		}
			.distinctBy { normalizeTitle(it.title) to it.providerId }

		if (items.isNotEmpty()) {
			memoryCache = now to items
			localCache.edit()
				.putLong(KEY_LAST_SUCCESS, now)
				.putString(KEY_LAST_TITLES, items.joinToString("\n") { it.title })
				.apply()
		}

		items.take(limit)
	}

	suspend fun loadCatalogByGroup(
		providerIdPrefix: String,
		groupMatchers: List<String>,
		limit: Int = DEFAULT_LIMIT,
	): List<ExternalCatalogItem> = withContext(Dispatchers.IO) {
		val normalizedMatchers = groupMatchers.map(::normalizeTitle).filter { it.isNotBlank() }
		loadHomeCatalog(DEFAULT_LIMIT * 4)
			.asSequence()
			.filter { it.providerId.startsWith(providerIdPrefix) }
			.filter { item ->
				if (normalizedMatchers.isEmpty()) true
				else {
					val haystack = normalizeTitle(listOfNotNull(item.group, item.title, item.providerName).joinToString(" "))
					normalizedMatchers.any { matcher -> haystack.contains(matcher) }
				}
			}
			.distinctBy { normalizeTitle(it.title) to it.providerId }
			.take(limit)
			.toList()
	}

	suspend fun loadRaiPlayCatalog(kind: RaiPlayKind, limit: Int = DEFAULT_LIMIT): List<ExternalCatalogItem> = withContext(Dispatchers.IO) {
		val prefix = when (kind) {
			RaiPlayKind.FILM -> "raiplay-film"
			RaiPlayKind.SERIES -> "raiplay-series"
		}
		loadHomeCatalog(DEFAULT_LIMIT * 4)
			.asSequence()
			.filter { it.providerId.startsWith(prefix) }
			.distinctBy { normalizeTitle(it.title) to it.providerId }
			.take(limit)
			.toList()
	}

	suspend fun findFreeCatalogMatches(title: String, limit: Int = 6): List<ExternalCatalogItem> = withContext(Dispatchers.IO) {
		val normalizedTitle = normalizeTitle(title)
		if (normalizedTitle.isBlank()) return@withContext emptyList()

		loadHomeCatalog(DEFAULT_LIMIT * 4)
			.asSequence()
			.filter { it.isFree && (!it.streamUrl.isNullOrBlank() || it.providerId.startsWith("raiplay")) }
			.map { it to normalizeTitle(it.title) }
			.filter { (_, normalizedCandidate) ->
				normalizedCandidate == normalizedTitle ||
					normalizedCandidate.contains(normalizedTitle) ||
					normalizedTitle.contains(normalizedCandidate)
			}
			.sortedBy { (_, normalizedCandidate) -> normalizedCandidate.length }
			.map { (item, _) -> item }
			.distinctBy { normalizeTitle(it.title) to it.providerId }
			.take(limit)
			.toList()
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
		val parsed = PLUTO_VOD_SOURCES.asSequence()
			.mapNotNull { source -> runCatching { fetchText(source.url) to source }.getOrNull() }
			.flatMap { (body, source) -> parseM3u(body, source).asSequence() }
			.toList()
		return enrichCatalogMetadata(parsed)
	}

	private fun loadRaiPlayCatalogs(): List<ExternalCatalogItem> = RAIPLAY_SOURCES
		.flatMap { source ->
			runCatching { parseRaiPlayPage(fetchText(source.url, accept = "text/html,*/*"), source) }
				.onFailure { Timber.w(it, "Unable to load RaiPlay catalog ${source.name}") }
				.getOrDefault(emptyList())
		}
		.distinctBy { normalizeTitle(it.title) to it.providerId }

	private fun loadWikidataNewReleases(limit: Int): List<ExternalCatalogItem> {
		val from = LocalDate.now().minusDays(60)
		val to = LocalDate.now().plusDays(180)
		val query = """
			PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
			PREFIX wd: <http://www.wikidata.org/entity/>
			PREFIX wdt: <http://www.wikidata.org/prop/direct/>
			PREFIX bd: <http://www.bigdata.com/rdf#>
			PREFIX wikibase: <http://wikiba.se/ontology#>
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
			setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android TV) SuperJelly/1.0")
		}

		return try {
			if (connection.responseCode >= 400) error("HTTP ${connection.responseCode} while loading $url")
			connection.inputStream.bufferedReader().use { it.readText() }
		} finally {
			connection.disconnect()
		}
	}

	private fun enrichCatalogMetadata(items: List<ExternalCatalogItem>): List<ExternalCatalogItem> {
		val titlesToEnrich = items
			.asSequence()
			.filter { it.posterUrl.isNullOrBlank() || it.backdropUrl.isNullOrBlank() || it.trailerUrl.isNullOrBlank() }
			.map { it.title }
			.distinctBy(::normalizeTitle)
			.filter { metadataMemoryCache[normalizeTitle(it)] == null }
			.take(METADATA_ENRICH_LIMIT)
			.toList()

		if (titlesToEnrich.isNotEmpty()) {
			runCatching { queryWikidataMetadata(titlesToEnrich) }
				.onFailure { Timber.w(it, "Unable to enrich external catalog artwork") }
				.getOrDefault(emptyMap())
				.forEach { (title, metadata) -> metadataMemoryCache[normalizeTitle(title)] = metadata }
		}

		return items.map { item ->
			val metadata = metadataMemoryCache[normalizeTitle(item.title)]
			if (metadata == null) item
			else item.copy(
				posterUrl = item.posterUrl ?: metadata.posterUrl,
				backdropUrl = item.backdropUrl ?: metadata.backdropUrl ?: metadata.posterUrl,
				trailerUrl = item.trailerUrl ?: metadata.trailerUrl,
			)
		}
	}

	private fun queryWikidataMetadata(titles: List<String>): Map<String, CatalogMetadata> {
		if (titles.isEmpty()) return emptyMap()
		val values = titles.joinToString(" ") { "\"${escapeSparqlString(it)}\"" }
		val query = """
			PREFIX wd: <http://www.wikidata.org/entity/>
			PREFIX wdt: <http://www.wikidata.org/prop/direct/>
			PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
			PREFIX bd: <http://www.bigdata.com/rdf#>
			PREFIX wikibase: <http://wikiba.se/ontology#>
			SELECT ?wanted ?item ?itemLabel ?image ?youtubeId WHERE {
			  VALUES ?wanted { $values }
			  ?item rdfs:label ?label.
			  FILTER(LANG(?label) IN ("it", "en") && LCASE(STR(?label)) = LCASE(?wanted))
			  ?item wdt:P31/wdt:P279* ?kind.
			  VALUES ?kind { wd:Q11424 wd:Q5398426 }
			  OPTIONAL { ?item wdt:P18 ?image. }
			  OPTIONAL { ?item wdt:P1651 ?youtubeId. }
			  SERVICE wikibase:label { bd:serviceParam wikibase:language "it,en". }
			}
			LIMIT ${titles.size * 4}
		""".trimIndent()
		val encodedQuery = URLEncoder.encode(query, "UTF-8")
		val body = fetchText("https://query.wikidata.org/sparql?format=json&query=$encodedQuery", accept = "application/sparql-results+json")
		val bindings = JSONObject(body)
			.getJSONObject("results")
			.getJSONArray("bindings")

		val result = linkedMapOf<String, CatalogMetadata>()
		for (index in 0 until bindings.length()) {
			val binding = bindings.getJSONObject(index)
			val wanted = binding.optJSONObject("wanted")?.optString("value").orEmpty().trim()
			if (wanted.isBlank() || result.containsKey(wanted)) continue
			val image = binding.optJSONObject("image")?.optString("value")?.takeIf { it.isNotBlank() }
			val youtubeId = binding.optJSONObject("youtubeId")?.optString("value")?.takeIf { it.isNotBlank() }
			if (image.isNullOrBlank() && youtubeId.isNullOrBlank()) continue
			result[wanted] = CatalogMetadata(
				posterUrl = image,
				backdropUrl = image,
				trailerUrl = youtubeId?.let { "https://www.youtube.com/watch?v=$it" },
			)
		}
		return result
	}

	private fun escapeSparqlString(value: String): String = value
		.replace("\\", "\\\\")
		.replace("\"", "\\\"")

	private fun isProviderLogo(url: String): Boolean {
		val normalized = url.lowercase()
		return normalized.contains("colorlogopng") || normalized.contains("/channels/") && normalized.contains("pluto")
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
						val posterArtwork = item.logo?.takeUnless(::isProviderLogo)
						val backdropArtwork = item.backdrop?.takeUnless(::isProviderLogo)
						result += ExternalCatalogItem(
							providerId = source.id,
							providerName = source.name,
							title = item.title,
							type = source.type,
							streamUrl = line,
							detailUrl = source.detailUrl,
							posterUrl = posterArtwork,
							backdropUrl = backdropArtwork ?: posterArtwork,
							providerLogoUrl = source.logoUrl,
							group = item.group ?: source.defaultGroup,
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

	private fun parseRaiPlayPage(body: String, source: CatalogSource): List<ExternalCatalogItem> {
		val items = mutableListOf<ExternalCatalogItem>()
		var currentGroup = source.defaultGroup
		RAIPLAY_BLOCK_REGEX.findAll(body).forEach { match ->
			val block = match.value
			if (block.startsWith("<h", ignoreCase = true)) {
				stripHtml(block).takeIf { isUsefulRaiPlayTitle(it) }?.let { currentGroup = it }
				return@forEach
			}

			val href = readHtmlAttribute(block, "href")?.takeIf { isRaiPlayContentUrl(it) } ?: return@forEach
			val title = firstNotBlank(
				readHtmlAttribute(block, "title"),
				readHtmlAttribute(block, "aria-label"),
				readHtmlAttribute(block, "alt"),
				extractImageAlt(block),
				stripHtml(block),
			)?.let(::cleanRaiPlayTitle)?.takeIf { isUsefulRaiPlayTitle(it) } ?: return@forEach
			val image = firstNotBlank(
				readHtmlAttribute(block, "data-src"),
				readHtmlAttribute(block, "data-original"),
				readHtmlAttribute(block, "src"),
				readFirstSrcSetUrl(block),
			)?.let(::normalizeRaiPlayUrl)
			val detailUrl = normalizeRaiPlayUrl(href)

			items += ExternalCatalogItem(
				providerId = source.id,
				providerName = source.name,
				title = title,
				type = source.type,
				detailUrl = detailUrl,
				posterUrl = image,
				backdropUrl = image,
				providerLogoUrl = source.logoUrl,
				group = currentGroup,
				isFree = true,
				availabilityNote = appContext.getString(R.string.lbl_external_catalog_free_badge),
			)
		}
		return items.distinctBy { normalizeTitle(it.title) to it.detailUrl.orEmpty() }
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

	private fun readHtmlAttribute(html: String, name: String): String? =
		Regex("""\b${Regex.escape(name)}\s*=\s*[\"']([^\"']+)[\"']""", RegexOption.IGNORE_CASE)
			.find(html)
			?.groupValues
			?.getOrNull(1)
			?.let(::decodeHtmlEntities)
			?.trim()
			?.takeIf { it.isNotBlank() }

	private fun extractImageAlt(html: String): String? =
		Regex("""<img[^>]+alt\s*=\s*[\"']([^\"']+)[\"']""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
			.find(html)
			?.groupValues
			?.getOrNull(1)
			?.let(::decodeHtmlEntities)
			?.trim()

	private fun readFirstSrcSetUrl(html: String): String? = readHtmlAttribute(html, "srcset")
		?.split(',')
		?.firstOrNull()
		?.trim()
		?.substringBefore(' ')
		?.takeIf { it.isNotBlank() }

	private fun normalizeRaiPlayUrl(raw: String): String = when {
		raw.startsWith("//") -> "https:$raw"
		raw.startsWith("/") -> "https://www.raiplay.it$raw"
		raw.startsWith("http", ignoreCase = true) -> raw
		else -> "https://www.raiplay.it/$raw"
	}

	private fun isRaiPlayContentUrl(url: String): Boolean {
		val normalized = url.lowercase(Locale.ROOT)
		return normalized.contains("/programmi/") || normalized.contains("/video/") || normalized.contains("/collezioni/") || normalized.endsWith(".html")
	}

	private fun stripHtml(value: String): String = value
		.replace(Regex("<script[^>]*>.*?</script>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
		.replace(Regex("<style[^>]*>.*?</style>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
		.replace(Regex("<[^>]+>"), " ")
		.let(::decodeHtmlEntities)
		.replace(Regex("\\s+"), " ")
		.trim()

	private fun decodeHtmlEntities(value: String): String = value
		.replace("&amp;", "&")
		.replace("&quot;", "\"")
		.replace("&#039;", "'")
		.replace("&apos;", "'")
		.replace("&nbsp;", " ")

	private fun cleanRaiPlayTitle(value: String): String = value
		.replace("RaiPlay", "", ignoreCase = true)
		.replace(Regex("\\b(RIPRODUCI|Info|Salva|Vedi tutti)\\b", RegexOption.IGNORE_CASE), " ")
		.replace(Regex("\\s+"), " ")
		.trim(' ', '-', '|')

	private fun isUsefulRaiPlayTitle(title: String): Boolean {
		val normalized = normalizeTitle(title)
		if (normalized.length < 2 || title.length > 90) return false
		return normalized !in RAIPLAY_TITLE_BLACKLIST
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
		val logoUrl: String? = null,
		val type: BaseItemKind = BaseItemKind.MOVIE,
		val defaultGroup: String? = null,
	)

	private data class CatalogMetadata(
		val posterUrl: String? = null,
		val backdropUrl: String? = null,
		val trailerUrl: String? = null,
	)

	enum class RaiPlayKind { FILM, SERIES }

	companion object {
		private const val CACHE_NAME = "external_catalog_cache"
		private const val KEY_LAST_SUCCESS = "last_success"
		private const val KEY_LAST_TITLES = "last_titles"
		private const val DEFAULT_LIMIT = 96
		private const val NEW_RELEASES_LIMIT = 36
		private const val METADATA_ENRICH_LIMIT = 36
		private val MEMORY_CACHE_TTL_MS = TimeUnit.MINUTES.toMillis(30)
		private val NEW_RELEASES_MEMORY_CACHE_TTL_MS = TimeUnit.MINUTES.toMillis(15)
		private const val NETWORK_TIMEOUT_MS = 5_000
		private val ATTRIBUTE_REGEX = "([a-zA-Z0-9_-]+)=\"([^\"]*)\"".toRegex()
		private val WIKIDATA_QID_REGEX = "Q\\d+".toRegex()
		private val RAIPLAY_BLOCK_REGEX = Regex("""<h[2-4][^>]*>.*?</h[2-4]>|<a[^>]+href\s*=\s*[\"'][^\"']+[\"'][^>]*>.*?</a>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
		private val RAIPLAY_TITLE_BLACKLIST = setOf(
			"homepage", "dirette", "catalogo", "film", "serie italiane", "serie internazionali", "programmi",
			"sport", "crime", "original", "documentari", "bambini", "teen", "musica e teatro", "teche rai",
			"learning", "sostenibilita", "rai italy", "cerca", "altro", "accedi", "registrati", "impostazioni",
			"associa dispositivo", "supporto", "faq", "app rai", "vedi tutti", "info", "salva", "riproduci",
		)

		private val PLUTO_VOD_SOURCES = listOf(
			CatalogSource(
				id = "pluto-tv-it-vod",
				name = "Pluto TV",
				url = "https://raw.githubusercontent.com/OwnerPlugins/pluto-tv-m3u/main/pluto-vod-IT.m3u",
				detailUrl = "https://pluto.tv/it/on-demand",
				logoUrl = "https://images.pluto.tv/channels/5f1aa7aab66c76000790ee7e/colorLogoPNG.png",
				defaultGroup = "Pluto TV",
			),
			CatalogSource(
				id = "pluto-tv-it-live",
				name = "Pluto TV",
				url = "https://i.mjh.nz/PlutoTV/it.m3u8",
				detailUrl = "https://pluto.tv/it/live-tv",
				logoUrl = "https://images.pluto.tv/channels/5f1aa7aab66c76000790ee7e/colorLogoPNG.png",
				defaultGroup = "Pluto TV Live",
			),
		)

		private val RAIPLAY_SOURCES = listOf(
			CatalogSource(
				id = "raiplay-film",
				name = "RaiPlay",
				url = "https://www.raiplay.it/film",
				detailUrl = "https://www.raiplay.it/film",
				logoUrl = "https://www.raiplay.it/dl/RaiPlay/2020/images/logo_raiplay.png",
				type = BaseItemKind.MOVIE,
				defaultGroup = "RaiPlay Film",
			),
			CatalogSource(
				id = "raiplay-series-it",
				name = "RaiPlay",
				url = "https://www.raiplay.it/serieitaliane",
				detailUrl = "https://www.raiplay.it/serieitaliane",
				logoUrl = "https://www.raiplay.it/dl/RaiPlay/2020/images/logo_raiplay.png",
				type = BaseItemKind.SERIES,
				defaultGroup = "RaiPlay Serie Italiane",
			),
			CatalogSource(
				id = "raiplay-series-international",
				name = "RaiPlay",
				url = "https://www.raiplay.it/serieinternazionali",
				detailUrl = "https://www.raiplay.it/serieinternazionali",
				logoUrl = "https://www.raiplay.it/dl/RaiPlay/2020/images/logo_raiplay.png",
				type = BaseItemKind.SERIES,
				defaultGroup = "RaiPlay Serie Internazionali",
			),
		)

		fun normalizeTitle(title: String): String = title
			.lowercase(Locale.ROOT)
			.replace(Regex("\\([^)]*\\)"), " ")
			.replace(Regex("[^a-z0-9àèéìòù]+"), " ")
			.trim()
	}
}
