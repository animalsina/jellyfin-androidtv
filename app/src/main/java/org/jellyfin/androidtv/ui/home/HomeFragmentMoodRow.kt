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
import java.util.UUID

class HomeFragmentMoodRow(
	private val userViews: Collection<BaseItemDto>,
	private val titleRes: Int,
	private val genres: Set<String>,
	private val sortBy: ItemSortBy = ItemSortBy.RANDOM,
	private val minCommunityRating: Double? = null,
) : HomeFragmentRow {
	override fun addToRowsAdapter(context: Context, cardPresenter: CardPresenter, rowsAdapter: MutableObjectAdapter<Row>) {
		val request = GetItemsRequest(
			fields = ItemRepository.itemFields + ItemFields.GENRES + ItemFields.MEDIA_STREAMS,
			includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
			recursive = true,
			genres = genres.toList(),
			sortBy = listOf(sortBy),
			sortOrder = listOf(SortOrder.DESCENDING),
			limit = ITEM_LIMIT,
			parentId = getParentId(),
			isPlayed = false,
			minCommunityRating = minCommunityRating,
		)

		HomeFragmentBrowseRowDefRow(
			BrowseRowDef(context.getString(titleRes), request, 50, false, true)
		).addToRowsAdapter(context, cardPresenter, rowsAdapter)
	}

	private fun getParentId(): UUID? {
		val relevantViews = userViews.filter { view ->
			view.collectionType in arrayOf(CollectionType.MOVIES, CollectionType.TVSHOWS)
		}
		return if (relevantViews.size == 1) relevantViews.first().id else null
	}

	companion object {
		private const val ITEM_LIMIT = 15

		fun light(userViews: Collection<BaseItemDto>) = HomeFragmentMoodRow(
			userViews = userViews,
			titleRes = R.string.home_section_mood_light,
			genres = setOf("Comedy", "Animation", "Family", "Adventure", "Romance"),
			minCommunityRating = 5.5,
		)

		fun action(userViews: Collection<BaseItemDto>) = HomeFragmentMoodRow(
			userViews = userViews,
			titleRes = R.string.home_section_mood_action,
			genres = setOf("Action", "Adventure", "Thriller", "Science Fiction"),
			minCommunityRating = 5.5,
		)

		fun short(userViews: Collection<BaseItemDto>) = HomeFragmentMoodRow(
			userViews = userViews,
			titleRes = R.string.home_section_mood_short,
			genres = setOf("Comedy", "Animation", "Family", "Documentary", "Adventure"),
			minCommunityRating = 5.5,
		)
	}
}
