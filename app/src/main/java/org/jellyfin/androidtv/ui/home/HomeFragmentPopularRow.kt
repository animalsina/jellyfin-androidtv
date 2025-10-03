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

class HomeFragmentPopularRow(
	private val userViews: Collection<BaseItemDto>,
	private val includeTypes: Array<BaseItemKind>,
	private val title: String,
	private val popularityStrategy: PopularityStrategy = PopularityStrategy.PLAY_COUNT
) : HomeFragmentRow {

	override fun addToRowsAdapter(context: Context, cardPresenter: CardPresenter, rowsAdapter: MutableObjectAdapter<Row>) {
		// Create request based on popularity strategy
		val request = when (popularityStrategy) {
			PopularityStrategy.PLAY_COUNT -> createPlayCountRequest()
			PopularityStrategy.COMMUNITY_RATING -> createCommunityRatingRequest()
			PopularityStrategy.TRENDING -> createTrendingRequest()
			PopularityStrategy.RECENTLY_RELEASED -> createRecentlyReleasedRequest()
		}

		// Create and add the row with static height for consistent sizing
		val row = HomeFragmentBrowseRowDefRow(
			BrowseRowDef(title, request, 50, false, true)
		)
		row.addToRowsAdapter(context, cardPresenter, rowsAdapter)
	}

	private fun createPlayCountRequest() = GetItemsRequest(
		fields = ItemRepository.itemFields + ItemFields.PLAY_ACCESS + ItemFields.MEDIA_STREAMS,
		includeItemTypes = includeTypes.toList(),
		recursive = true,
		sortBy = listOf(ItemSortBy.PLAY_COUNT),
		sortOrder = listOf(SortOrder.DESCENDING),
		limit = ITEM_LIMIT,
		parentId = getParentId()?.let { java.util.UUID.fromString(it) }
	)

	private fun createCommunityRatingRequest() = GetItemsRequest(
		fields = ItemRepository.itemFields,
		includeItemTypes = includeTypes.toList(),
		recursive = true,
		sortBy = listOf(ItemSortBy.COMMUNITY_RATING),
		sortOrder = listOf(SortOrder.DESCENDING),
		limit = ITEM_LIMIT,
		parentId = getParentId()?.let { java.util.UUID.fromString(it) },
		minCommunityRating = 7.0 // Only include well-rated items
	)

	private fun createTrendingRequest() = GetItemsRequest(
		fields = ItemRepository.itemFields,
		includeItemTypes = includeTypes.toList(),
		recursive = true,
		sortBy = listOf(ItemSortBy.DATE_PLAYED),
		sortOrder = listOf(SortOrder.DESCENDING),
		limit = ITEM_LIMIT,
		parentId = getParentId()?.let { java.util.UUID.fromString(it) }
	)

	private fun createRecentlyReleasedRequest() = GetItemsRequest(
		fields = ItemRepository.itemFields + ItemFields.DATE_CREATED,
		includeItemTypes = includeTypes.toList(),
		recursive = true,
		sortBy = listOf(ItemSortBy.PREMIERE_DATE),
		sortOrder = listOf(SortOrder.DESCENDING),
		limit = ITEM_LIMIT,
		parentId = getParentId()?.let { java.util.UUID.fromString(it) }
	)

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

	enum class PopularityStrategy {
		PLAY_COUNT,
		COMMUNITY_RATING,
		TRENDING,
		RECENTLY_RELEASED
	}

	companion object {
		private const val ITEM_LIMIT = 50

		// Factory methods for specific popular row types
		fun createPopularMoviesRow(context: Context, userViews: Collection<BaseItemDto>) = HomeFragmentPopularRow(
			userViews = userViews,
			includeTypes = arrayOf(BaseItemKind.MOVIE),
			title =  context.getString(R.string.popular_tv_shows),
			popularityStrategy = PopularityStrategy.PLAY_COUNT
		)

	/**
	 * Factory method for creating a popular TV shows row.
	 * @param userViews the user views to filter and sort
	 * @return a new HomeFragmentPopularRow with the given user views and parameters
	 */
		fun createPopularTVRow(context: Context, userViews: Collection<BaseItemDto>) = HomeFragmentPopularRow(
			userViews = userViews,
			includeTypes = arrayOf(BaseItemKind.SERIES),
			title = context.getString(R.string.popular_tv_shows),
			popularityStrategy = PopularityStrategy.PLAY_COUNT
		)

		fun createTrendingRow(context: Context, userViews: Collection<BaseItemDto>) = HomeFragmentPopularRow(
			userViews = userViews,
			includeTypes = arrayOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
			title = context.getString(R.string.trending_this_week),
			popularityStrategy = PopularityStrategy.TRENDING
		)

		fun createRecentlyReleasedRow(context: Context, userViews: Collection<BaseItemDto>) = HomeFragmentPopularRow(
			userViews = userViews,
			includeTypes = arrayOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
			title = context.getString(R.string.home_section_recently_released),
			popularityStrategy = PopularityStrategy.RECENTLY_RELEASED
		)
	}
}
