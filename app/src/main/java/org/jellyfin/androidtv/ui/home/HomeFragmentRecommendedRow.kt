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

class HomeFragmentRecommendedRow(
	private val userViews: Collection<BaseItemDto>,
	private val title: String,
	private val recommendationStrategy: RecommendationStrategy = RecommendationStrategy.SIMILAR_TO_LIKED
) : HomeFragmentRow {

	override fun addToRowsAdapter(context: Context, cardPresenter: CardPresenter, rowsAdapter: MutableObjectAdapter<Row>) {
		// Create request based on recommendation strategy
		val request = when (recommendationStrategy) {
			RecommendationStrategy.SIMILAR_TO_LIKED -> createSimilarToLikedRequest()
			RecommendationStrategy.UNPLAYED_FAVORITES -> createUnplayedFavoritesRequest()
			RecommendationStrategy.RECOMMENDED_FOR_YOU -> createRecommendedForYouRequest()
		}

		// Create and add the row with static height for consistent sizing
		val row = HomeFragmentBrowseRowDefRow(
			BrowseRowDef(title, request, 50, false, true)
		)
		row.addToRowsAdapter(context, cardPresenter, rowsAdapter)
	}

	private fun createSimilarToLikedRequest(): GetItemsRequest {
		// TODO: In a full implementation, this would:
		// 1. Query recently played items first
		// 2. Pick a random recently watched item
		// 3. Find similar items based on genres/people from that item
		// 4. For now, we'll use a simpler approach that finds unplayed content
		//    similar to popular/well-rated content

		return GetItemsRequest(
			fields = ItemRepository.itemFields + ItemFields.GENRES + ItemFields.PEOPLE,
			includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
			recursive = true,
			sortBy = listOf(ItemSortBy.RANDOM),
			sortOrder = listOf(SortOrder.DESCENDING),
			limit = ITEM_LIMIT,
			parentId = getParentId()?.let { java.util.UUID.fromString(it) },
			minCommunityRating = 6.5,
			isFavorite = false,
			isPlayed = false
		)
	}

	private fun createUnplayedFavoritesRequest() = GetItemsRequest(
		fields = ItemRepository.itemFields + ItemFields.GENRES,
		includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
		recursive = true,
		sortBy = listOf(ItemSortBy.COMMUNITY_RATING),
		sortOrder = listOf(SortOrder.DESCENDING),
		limit = ITEM_LIMIT,
		parentId = getParentId()?.let { java.util.UUID.fromString(it) },
		isPlayed = false,
		minCommunityRating = 8.0
	)

	private fun createRecommendedForYouRequest() = GetItemsRequest(
		fields = ItemRepository.itemFields + ItemFields.GENRES + ItemFields.PEOPLE,
		includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
		recursive = true,
		sortBy = listOf(ItemSortBy.RANDOM),
		sortOrder = listOf(SortOrder.DESCENDING),
		limit = ITEM_LIMIT,
		parentId = getParentId()?.let { java.util.UUID.fromString(it) },
		isPlayed = false,
		minCommunityRating = 6.5
	)

	private fun getParentId(): String? {
		// Filter views to only include relevant collection types
		val relevantViews = userViews.filter { view ->
			view.collectionType in arrayOf(CollectionType.MOVIES, CollectionType.TVSHOWS)
		}

		// If we have multiple relevant views, return null to search all
		// If we have one specific view, use its ID
		return if (relevantViews.size == 1) relevantViews.first().id?.toString() else null
	}

	enum class RecommendationStrategy {
		SIMILAR_TO_LIKED,
		UNPLAYED_FAVORITES,
		RECOMMENDED_FOR_YOU
	}

	companion object {
		private const val ITEM_LIMIT = 50

		// Factory methods for specific recommendation row types
		fun createRecommendedForYouRow(context: Context, userViews: Collection<BaseItemDto>) = HomeFragmentRecommendedRow(
			userViews = userViews,
			title = context.getString(R.string.home_section_recommended_for_you),
			recommendationStrategy = RecommendationStrategy.RECOMMENDED_FOR_YOU
		)
	}
}
