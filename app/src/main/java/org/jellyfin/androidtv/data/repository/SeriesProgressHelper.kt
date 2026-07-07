package org.jellyfin.androidtv.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.data.model.IncompleteSeriesProgress
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import java.time.LocalDateTime

object SeriesProgressHelper {
	private const val MAX_SERIES_EPISODES = 2000

	suspend fun loadIncompleteProgress(api: ApiClient, series: BaseItemDto): IncompleteSeriesProgress? = withContext(Dispatchers.IO) {
		val seriesId = series.id ?: return@withContext null
		val response = api.itemsApi.getItems(
			GetItemsRequest(
				parentId = seriesId,
				recursive = true,
				includeItemTypes = listOf(BaseItemKind.EPISODE),
				fields = (ItemRepository.browseFields + ItemFields.ITEM_COUNTS).toList(),
				limit = MAX_SERIES_EPISODES,
				enableTotalRecordCount = false,
			)
		).content

		val episodes = response.items.orEmpty()
		if (episodes.isEmpty()) return@withContext null

		val watched = episodes.filter { it.userData?.played == true }
		val unwatchedCount = episodes.count { it.userData?.played != true }
		val lastWatched = watched.maxWithOrNull(
			compareBy<BaseItemDto> { it.userData?.lastPlayedDate ?: LocalDateTime.MIN }
				.thenBy { it.parentIndexNumber ?: 0 }
				.thenBy { it.indexNumber ?: 0 }
		)

		IncompleteSeriesProgress(
			lastWatchedEpisode = lastWatched,
			watchedCount = watched.size,
			unwatchedCount = unwatchedCount,
			totalCount = episodes.size,
		).takeIf { it.isIncompleteStarted() }
	}
}
