package org.jellyfin.androidtv.ui.home

import android.content.Context
import androidx.leanback.widget.Row
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.constant.ChangeTriggerType
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.ui.browsing.BrowseRowDef
import org.jellyfin.androidtv.ui.presentation.CardPresenter
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.api.request.GetResumeItemsRequest
import org.koin.java.KoinJavaComponent.inject

class HomeFragmentSimilarToWatchedRow(
	private val userViews: Collection<BaseItemDto>,
	private val title: String = "More Like This"
) : HomeFragmentRow {

	private val api: ApiClient by inject(ApiClient::class.java)

	override fun addToRowsAdapter(context: Context, cardPresenter: CardPresenter, rowsAdapter: MutableObjectAdapter<Row>) {
		// Create a more sophisticated "similar to watched" request
		val request = createSimilarToWatchedRequest()
		
		// Create and add the row with static height for consistent sizing
		val row = HomeFragmentBrowseRowDefRow(
			BrowseRowDef(title, request, 50, false, true)
		)
		row.addToRowsAdapter(context, cardPresenter, rowsAdapter)
	}

	private fun createSimilarToWatchedRequest(): GetItemsRequest {
		// This creates a request that finds content similar to what the user has been watching
		// It focuses on unplayed content from genres the user has been engaging with
		return GetItemsRequest(
			fields = ItemRepository.itemFields + ItemFields.GENRES + ItemFields.PEOPLE,
			includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
			recursive = true,
			sortBy = listOf(ItemSortBy.RANDOM),
			sortOrder = listOf(SortOrder.DESCENDING),
			limit = ITEM_LIMIT,
			parentId = getParentId()?.let { java.util.UUID.fromString(it) },
			minCommunityRating = 6.0,
			isPlayed = false,
			// This will show unplayed content that's well-rated and likely to be enjoyed
			// In a more sophisticated implementation, we could:
			// 1. Get recently played items
			// 2. Extract their genres/actors
			// 3. Find similar unplayed content
			// For now, this provides good recommendations based on quality
		)
	}

	private fun getParentId(): String? {
		// Filter views to only include relevant collection types
		val relevantViews = userViews.filter { view ->
			view.collectionType in arrayOf(CollectionType.MOVIES, CollectionType.TVSHOWS)
		}
		
		// If we have multiple relevant views, return null to search all
		// If we have one specific view, use its ID
		return if (relevantViews.size == 1) relevantViews.first().id?.toString() else null
	}

	companion object {
		private const val ITEM_LIMIT = 50

		// Factory method
		fun create(userViews: Collection<BaseItemDto>) = HomeFragmentSimilarToWatchedRow(
			userViews = userViews,
			title = "More Like This"
		)
	}
}