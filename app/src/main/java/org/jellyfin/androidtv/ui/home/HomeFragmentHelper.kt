package org.jellyfin.androidtv.ui.home

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.constant.ChangeTriggerType
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.ui.browsing.BrowseRowDef
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.request.GetNextUpRequest
import org.jellyfin.sdk.model.api.request.GetRecommendedProgramsRequest
import org.jellyfin.sdk.model.api.request.GetRecordingsRequest
import org.jellyfin.sdk.model.api.request.GetResumeItemsRequest

class HomeFragmentHelper(
	private val context: Context,
	private val userRepository: UserRepository,
) {
	suspend fun loadRecentlyAdded(userViews: Collection<BaseItemDto>): HomeFragmentRow =
		withContext(Dispatchers.IO) {
			HomeFragmentLatestRow(userRepository, userViews)
		}

	fun loadResume(title: String, includeMediaTypes: Collection<MediaType>): HomeFragmentRow {
		val query = GetResumeItemsRequest(
			limit = ITEM_LIMIT_RESUME,
			fields = ItemRepository.itemFields,
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
			fields = ItemRepository.itemFields,
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
			fields = ItemRepository.itemFields,
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
			fields = ItemRepository.itemFields
		)

		return NextUpHomeFragmentRow(BrowseRowDef(context.getString(R.string.lbl_next_up), query, arrayOf(ChangeTriggerType.TvPlayback)))
	}

	fun loadOnNow(): HomeFragmentRow {
		val query = GetRecommendedProgramsRequest(
			isAiring = true,
			fields = ItemRepository.itemFields,
			imageTypeLimit = 1,
			enableTotalRecordCount = false,
			limit = ITEM_LIMIT_ON_NOW
		)

		return HomeFragmentBrowseRowDefRow(BrowseRowDef(context.getString(R.string.lbl_on_now), query))
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


	companion object {
		// Maximum amount of items loaded for a row
		private const val ITEM_LIMIT_RESUME = 15
		private const val ITEM_LIMIT_RECORDINGS = 15
		private const val ITEM_LIMIT_NEXT_UP = 15
		private const val ITEM_LIMIT_ON_NOW = 15
	}
}
