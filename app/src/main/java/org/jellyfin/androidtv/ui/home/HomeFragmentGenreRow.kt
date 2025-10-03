package org.jellyfin.androidtv.ui.home

import android.content.Context
import androidx.leanback.widget.Row
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.ui.browsing.BrowseRowDef
import org.jellyfin.androidtv.ui.presentation.CardPresenter
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest

class HomeFragmentGenreRow(
	private val userViews: Collection<BaseItemDto>,
	private val includeTypes: Array<BaseItemKind>,
	private val titleProvider: (String) -> String,
	private val genreSelectionStrategy: GenreSelectionStrategy = GenreSelectionStrategy.RANDOM
) : HomeFragmentRow {

	override fun addToRowsAdapter(context: Context, cardPresenter: CardPresenter, rowsAdapter: MutableObjectAdapter<Row>) {
		// Get all available genres from the libraries
		val availableGenres = getAvailableGenres()

		if (availableGenres.isEmpty()) return

		// Select genre based on strategy
		val selectedGenre = when (genreSelectionStrategy) {
			GenreSelectionStrategy.RANDOM -> context.getString(availableGenres.random())
			GenreSelectionStrategy.WEIGHTED -> selectWeightedGenre(context, availableGenres)
		}

		// Create the request for this genre
		val request = GetItemsRequest(
			fields = ItemRepository.itemFields + ItemFields.GENRES,
			includeItemTypes = includeTypes.toList(),
			recursive = true,
			genres = listOf(selectedGenre),
			sortBy = listOf(ItemSortBy.RANDOM),
			sortOrder = listOf(SortOrder.DESCENDING),
			limit = ITEM_LIMIT,
			parentId = getParentId()?.let { java.util.UUID.fromString(it) }
		)

		// Create title with the selected genre
		val title = titleProvider(selectedGenre)

		// Create and add the row with static height for consistent sizing
		val row = HomeFragmentBrowseRowDefRow(
			BrowseRowDef(title, request, 50, false, true)
		)
		row.addToRowsAdapter(context, cardPresenter, rowsAdapter)
	}

	private fun getAvailableGenres(): List<Int> {
		// In a real implementation, this would query the Jellyfin API to get available genres
		// For now, we'll use a predefined list of common genres
		return COMMON_GENRES.shuffled()
	}

	private fun selectWeightedGenre(context: Context, genres: List<Int>): String {
		// TODO: Implement weighted selection based on user viewing history
		// For now, just return random
		return context.getString(genres.random())
	}

	private fun getParentId(): String? {
		// Filter views to only include relevant collection types
		val relevantViews = userViews.filter { view ->
			view.collectionType in when {
				includeTypes.contains(BaseItemKind.MOVIE) -> arrayOf(CollectionType.MOVIES)
				includeTypes.contains(BaseItemKind.SERIES) -> arrayOf(CollectionType.TVSHOWS)
				else -> arrayOf(CollectionType.MOVIES, CollectionType.TVSHOWS)
			}
		}

		// If we have multiple relevant views, return null to search all
		// If we have one specific view, use its ID
		return if (relevantViews.size == 1) relevantViews.first().id.toString() else null
	}

	enum class GenreSelectionStrategy {
		RANDOM,
		WEIGHTED
	}

	companion object {
		private const val ITEM_LIMIT = 50

		// Common genres that are likely to be found in most libraries
		private val COMMON_GENRES = listOf(
			R.string.lbl_action,
			R.string.lbl_adventure,
			R.string.lbl_animation,
			R.string.lbl_comedy,
			R.string.lbl_crime,
			R.string.lbl_documentary,
			R.string.lbl_drama,
			R.string.lbl_family,
			R.string.lbl_fantasy,
			R.string.lbl_history,
			R.string.lbl_horror,
			R.string.lbl_music,
			R.string.lbl_mystery,
			R.string.lbl_romance,
			R.string.lbl_science_fiction,
			R.string.lbl_thriller,
			R.string.lbl_war,
			R.string.lbl_western,
			R.string.lbl_biography,
			R.string.lbl_sport,
			R.string.lbl_musical,
			R.string.lbl_suspense,
			R.string.lbl_kids,
			R.string.lbl_news,
			R.string.lbl_reality,
			R.string.lbl_talk_show
		)

		// Factory methods for specific genre row types
		fun createMovieGenreRow(userViews: Collection<BaseItemDto>) = HomeFragmentGenreRow(
			userViews = userViews,
			includeTypes = arrayOf(BaseItemKind.MOVIE),
			titleProvider = { genre -> "$genre Movies" }
		)

		fun createTVGenreRow(userViews: Collection<BaseItemDto>) = HomeFragmentGenreRow(
			userViews = userViews,
			includeTypes = arrayOf(BaseItemKind.SERIES),
			titleProvider = { genre -> "$genre TV Shows" }
		)

		fun createMixedGenreRow(userViews: Collection<BaseItemDto>) = HomeFragmentGenreRow(
			userViews = userViews,
			includeTypes = arrayOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
			titleProvider = { genre -> genre }
		)
	}
}
