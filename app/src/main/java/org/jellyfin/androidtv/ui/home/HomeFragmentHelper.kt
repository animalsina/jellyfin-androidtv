package org.jellyfin.androidtv.ui.home

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.constant.ChangeTriggerType
import org.jellyfin.androidtv.data.repository.ExternalCatalogRepository
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.ui.browsing.BrowseRowDef
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.request.GetNextUpRequest
import org.jellyfin.sdk.model.api.request.GetRecommendedProgramsRequest
import org.jellyfin.sdk.model.api.request.GetRecordingsRequest
import org.jellyfin.sdk.model.api.request.GetResumeItemsRequest
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder

class HomeFragmentHelper(
	private val context: Context,
	private val userRepository: UserRepository,
	private val externalCatalogRepository: ExternalCatalogRepository,
	private val api: ApiClient,
) {
	suspend fun loadRecentlyAdded(userViews: Collection<BaseItemDto>): HomeFragmentRow =
		withContext(Dispatchers.IO) {
			HomeFragmentLatestRow(userRepository, userViews)
		}

	fun loadResume(title: String, includeMediaTypes: Collection<MediaType>): HomeFragmentRow {
		val query = GetResumeItemsRequest(
			limit = ITEM_LIMIT_RESUME,
			fields = ItemRepository.browseFields,
			imageTypeLimit = 1,
			enableTotalRecordCount = false,
			mediaTypes = includeMediaTypes,
			excludeItemTypes = setOf(BaseItemKind.AUDIO_BOOK),
		)

		return HomeFragmentBrowseRowDefRow(BrowseRowDef(title, query, 0, false, true, arrayOf(ChangeTriggerType.TvPlayback, ChangeTriggerType.MoviePlayback)))
	}

	suspend fun loadResumeVideo(): HomeFragmentRow = withContext(Dispatchers.IO) {
		val query = GetResumeItemsRequest(
			limit = ITEM_LIMIT_RESUME,
			fields = ItemRepository.browseFields.toList(),
			imageTypeLimit = 1,
			enableTotalRecordCount = false,
			mediaTypes = listOf(MediaType.VIDEO),
			excludeItemTypes = setOf(BaseItemKind.AUDIO_BOOK),
		)
		ContinueWatchingHomeFragmentRow(
			BrowseRowDef(
				context.getString(R.string.lbl_continue_watching),
				query, 0, false, true,
				arrayOf(ChangeTriggerType.TvPlayback, ChangeTriggerType.MoviePlayback)
			)
		)
	}

	/**
	 * Returns a HomeFragmentRow for resuming audio playback.
	 * This will load resume items for audio media types.
	 *
	 * @return a HomeFragmentRow for resuming audio playback
	 */
	fun loadResumeAudio(): HomeFragmentRow =
		loadResume(context.getString(R.string.continue_listening), listOf(MediaType.AUDIO))

	fun loadLatestLiveTvRecordings(): HomeFragmentRow {
		val query = GetRecordingsRequest(
			fields = ItemRepository.browseFields.toList(),
			enableImages = true,
			limit = ITEM_LIMIT_RECORDINGS
		)

		return HomeFragmentBrowseRowDefRow(BrowseRowDef(context.getString(R.string.lbl_recordings), query))
	}

	fun loadNextUp(): HomeFragmentRow {
		val query = GetNextUpRequest(
			imageTypeLimit = 1,
			limit = ITEM_LIMIT_NEXT_UP,
			enableResumable = false,
			fields = ItemRepository.browseFields
		)

		return NextUpHomeFragmentRow(BrowseRowDef(context.getString(R.string.lbl_next_up), query, arrayOf(ChangeTriggerType.TvPlayback)))
	}

	fun loadOnNow(): HomeFragmentRow {
		val query = GetRecommendedProgramsRequest(
			isAiring = true,
			fields = ItemRepository.browseFields.toList(),
			imageTypeLimit = 1,
			enableTotalRecordCount = false,
			limit = ITEM_LIMIT_ON_NOW
		)

		return HomeFragmentBrowseRowDefRow(BrowseRowDef(context.getString(R.string.lbl_on_now), query))
	}

	fun loadIncompleteSeries(userViews: Collection<BaseItemDto>): HomeFragmentRow {
		val parentId = userViews
			.find { it.collectionType == org.jellyfin.sdk.model.api.CollectionType.TVSHOWS }
			?.id

		val query = GetItemsRequest(
			fields = (ItemRepository.browseFields + ItemFields.ITEM_COUNTS).toList(),
			includeItemTypes = listOf(BaseItemKind.SERIES),
			recursive = true,
			parentId = parentId,
			isPlayed = false,
			sortBy = listOf(ItemSortBy.DATE_PLAYED),
			sortOrder = listOf(SortOrder.DESCENDING),
			limit = ITEM_LIMIT_INCOMPLETE_SERIES_CANDIDATES,
		)

		return HomeFragmentIncompleteSeriesRow(BrowseRowDef(context.getString(R.string.home_section_incomplete_series), query, ITEM_LIMIT_RANDOM, false, true, arrayOf(ChangeTriggerType.TvPlayback)))
	}

	fun loadSeasonalEvents(userViews: Collection<BaseItemDto>): HomeFragmentRow {
		val now = java.time.LocalDate.now()
		val month = now.monthValue
		val day = now.dayOfMonth

		val genres = when {
			// Natale (Dicembre e Gennaio fino al 7)
			(month == 12) || (month == 1 && day <= 7) -> listOf("Christmas", "Holiday")
			// Halloween (Ottobre)
			(month == 10) -> listOf("Horror", "Halloween")
			// Pasqua (Marzo/Aprile)
			(month == 3 || month == 4) -> listOf("Fantasy", "Family")
			// Estate (Giugno, Luglio, Agosto)
			(month in 6..8) -> listOf("Adventure", "Animation")
			// Default
			else -> listOf("Family", "Comedy")
		}

		val parentId = userViews
			.filter { it.collectionType == org.jellyfin.sdk.model.api.CollectionType.MOVIES || it.collectionType == org.jellyfin.sdk.model.api.CollectionType.TVSHOWS }
			.takeIf { it.size == 1 }
			?.firstOrNull()
			?.id

		val query = GetItemsRequest(
			fields = ItemRepository.browseFields.toList(),
			includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
			recursive = true,
			sortBy = listOf(ItemSortBy.RANDOM),
			limit = ITEM_LIMIT_RANDOM,
			parentId = parentId,
			genres = genres,
		)

		return HomeFragmentBrowseRowDefRow(BrowseRowDef(context.getString(R.string.home_section_seasonal_events), query, ITEM_LIMIT_RANDOM, false, true))
	}

	// New row loader methods for Netflix-style rows
	suspend fun loadRecommendedForYou(userViews: Collection<BaseItemDto>): HomeFragmentRow =
		withContext(Dispatchers.IO) {
			HomeFragmentRecommendedRow.createRecommendedForYouRow(context, userViews)
		}

	suspend fun loadTrendingThisWeek(userViews: Collection<BaseItemDto>): HomeFragmentRow =
		withContext(Dispatchers.IO) {
			HomeFragmentPopularRow.createTrendingRow(context, userViews)
		}

	suspend fun loadRecentlyReleased(userViews: Collection<BaseItemDto>): HomeFragmentRow =
		withContext(Dispatchers.IO) {
			HomeFragmentPopularRow.createRecentlyReleasedRow(context, userViews)
		}

	suspend fun loadPopularMovies(userViews: Collection<BaseItemDto>): HomeFragmentRow =
		withContext(Dispatchers.IO) {
			HomeFragmentPopularRow.createPopularMoviesRow(context, userViews)
		}

	suspend fun loadPopularTV(userViews: Collection<BaseItemDto>): HomeFragmentRow =
		withContext(Dispatchers.IO) {
			HomeFragmentPopularRow.createPopularTVRow(context, userViews)
		}

	suspend fun loadSimilarToWatched(userViews: Collection<BaseItemDto>): HomeFragmentRow =
		withContext(Dispatchers.IO) {
			HomeFragmentSimilarToWatchedRow.create(context, userViews)
		}

	fun loadRandomMovies(userViews: Collection<BaseItemDto>): HomeFragmentRow = randomRow(
		title = context.getString(R.string.home_section_random_movies),
		userViews = userViews,
		includeTypes = listOf(BaseItemKind.MOVIE),
	)

	fun loadRandomSeries(userViews: Collection<BaseItemDto>): HomeFragmentRow = randomRow(
		title = context.getString(R.string.home_section_random_series),
		userViews = userViews,
		includeTypes = listOf(BaseItemKind.SERIES),
	)

	fun loadUnwatchedRandomMovies(userViews: Collection<BaseItemDto>): HomeFragmentRow = randomRow(
		title = context.getString(R.string.home_section_unwatched_random_movies),
		userViews = userViews,
		includeTypes = listOf(BaseItemKind.MOVIE),
		isPlayed = false,
	)

	fun loadLongAgoMovies(userViews: Collection<BaseItemDto>): HomeFragmentRow = randomRow(
		title = context.getString(R.string.home_section_long_ago_movies),
		userViews = userViews,
		includeTypes = listOf(BaseItemKind.MOVIE),
		sortBy = listOf(ItemSortBy.DATE_PLAYED),
		sortOrder = listOf(SortOrder.ASCENDING),
		isPlayed = true,
	)

	private fun randomRow(
		title: String,
		userViews: Collection<BaseItemDto>,
		includeTypes: List<BaseItemKind>,
		sortBy: List<ItemSortBy> = listOf(ItemSortBy.RANDOM),
		sortOrder: List<SortOrder> = listOf(SortOrder.DESCENDING),
		isPlayed: Boolean? = null,
	): HomeFragmentRow {
		val parentId = userViews
			.filter { view ->
				view.collectionType in when {
					includeTypes.contains(BaseItemKind.MOVIE) && !includeTypes.contains(BaseItemKind.SERIES) -> listOf(org.jellyfin.sdk.model.api.CollectionType.MOVIES)
					includeTypes.contains(BaseItemKind.SERIES) && !includeTypes.contains(BaseItemKind.MOVIE) -> listOf(org.jellyfin.sdk.model.api.CollectionType.TVSHOWS)
					else -> listOf(org.jellyfin.sdk.model.api.CollectionType.MOVIES, org.jellyfin.sdk.model.api.CollectionType.TVSHOWS)
				}
			}
			.takeIf { it.size == 1 }
			?.firstOrNull()
			?.id

		val query = GetItemsRequest(
			fields = ItemRepository.browseFields.toList(),
			includeItemTypes = includeTypes,
			recursive = true,
			sortBy = sortBy,
			sortOrder = sortOrder,
			limit = ITEM_LIMIT_RANDOM,
			parentId = parentId,
			isPlayed = isPlayed,
		)

		return HomeFragmentBrowseRowDefRow(BrowseRowDef(title, query, ITEM_LIMIT_RANDOM, false, true))
	}

	suspend fun loadGenreRandomMovies(userViews: Collection<BaseItemDto>): HomeFragmentRow =
		withContext(Dispatchers.IO) {
			HomeFragmentGenreRow.createMovieGenreRow(userViews)
		}

	suspend fun loadGenreRandomTV(userViews: Collection<BaseItemDto>): HomeFragmentRow =
		withContext(Dispatchers.IO) {
			HomeFragmentGenreRow.createTVGenreRow(userViews)
		}

	suspend fun loadGenreRandomMixed(userViews: Collection<BaseItemDto>): HomeFragmentRow =
		withContext(Dispatchers.IO) {
			HomeFragmentGenreRow.createMixedGenreRow(userViews)
		}

	suspend fun loadMoodLight(userViews: Collection<BaseItemDto>): HomeFragmentRow =
		withContext(Dispatchers.IO) {
			HomeFragmentMoodRow.light(userViews)
		}

	suspend fun loadMoodAction(userViews: Collection<BaseItemDto>): HomeFragmentRow =
		withContext(Dispatchers.IO) {
			HomeFragmentMoodRow.action(userViews)
		}

	suspend fun loadMoodShort(userViews: Collection<BaseItemDto>): HomeFragmentRow =
		withContext(Dispatchers.IO) {
			HomeFragmentMoodRow.short(userViews)
		}

	suspend fun loadExternalProviders(): HomeFragmentRow = withContext(Dispatchers.IO) {
		HomeFragmentExternalProvidersRow(
			items = externalCatalogRepository.loadHomeCatalog(limit = 180),
			splitByCategory = true,
		)
	}

	suspend fun loadOnlineNewReleases(): HomeFragmentRow = withContext(Dispatchers.IO) {
		HomeFragmentOnlineNewReleasesRow(
			externalCatalogRepository.loadNewReleases(
				localMatches = loadLocalTitleMatches(),
			)
		)
	}

	private suspend fun loadLocalTitleMatches(): Map<String, java.util.UUID> = withContext(Dispatchers.IO) {
		runCatching {
		val result = api.itemsApi.getItems(
			GetItemsRequest(
				fields = ItemRepository.browseFields.toList(),
				includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
				recursive = true,
					enableTotalRecordCount = false,
					imageTypeLimit = 1,
					limit = ITEM_LIMIT_LOCAL_MATCHES,
				)
			).content

			result.items.orEmpty()
				.filter { !it.name.isNullOrBlank() }
				.associate { org.jellyfin.androidtv.data.repository.ExternalCatalogRepository.normalizeTitle(it.name.orEmpty()) to it.id }
		}.getOrDefault(emptyMap())
	}


	companion object {
		// Maximum amount of items loaded for a row
		private const val ITEM_LIMIT_RESUME = 15
		private const val ITEM_LIMIT_RECORDINGS = 15
	private const val ITEM_LIMIT_NEXT_UP = 15
	private const val ITEM_LIMIT_ON_NOW = 15
	private const val ITEM_LIMIT_RANDOM = 20
	private const val ITEM_LIMIT_INCOMPLETE_SERIES_CANDIDATES = 16
	private const val ITEM_LIMIT_LOCAL_MATCHES = 1200
}
}
