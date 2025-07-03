package org.jellyfin.androidtv.ui.itemhandling

import android.content.Context
import androidx.leanback.widget.Row
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter
import org.jellyfin.androidtv.ui.presentation.CardPresenter
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.request.GetNextUpRequest

/**
 * Custom ItemRowAdapter for Next Up items that creates BaseItemDtoBaseRowItem instances
 * with preferSeriesPoster = true for vertical poster cards instead of horizontal thumbnails.
 */
class NextUpItemRowAdapter(
	context: Context,
	private val query: GetNextUpRequest,
	presenter: CardPresenter,
	parent: MutableObjectAdapter<Row>
) : ItemRowAdapter(context, query, false, presenter, parent) {

	fun retrieveNextUpWithSeriesPosters(api: ApiClient) {
		ProcessLifecycleOwner.get().lifecycleScope.launch {
			runCatching {
				val response = withContext(Dispatchers.IO) {
					api.tvShowsApi.getNextUp(query).content
				}

				// Some special flavor for series, used in FullDetailsFragment
				val firstNextUp = response.items.firstOrNull()
				if (query.seriesId != null && response.items.size == 1 && firstNextUp?.seasonId != null && firstNextUp.indexNumber != null) {
					// If we have exactly 1 episode returned, the series is currently partially watched
					// we want to query the server for all episodes in the same season starting from
					// this one to create a list of all unwatched episodes
					val episodesResponse = withContext(Dispatchers.IO) {
						api.itemsApi.getItems(
							parentId = firstNextUp.seasonId,
							startIndex = firstNextUp.indexNumber,
						).content
					}

					// Combine the next up episode with the additionally retrieved episodes
					val items = buildList {
						add(firstNextUp)
						addAll(episodesResponse.items)
					}

					setItems(
						items = items,
						transform = { item, _ ->
							BaseItemDtoBaseRowItem(
								item,
								false, // Don't prefer parent thumb for vertical posters
								false, // Not static height
								BaseRowItemSelectAction.ShowDetails,
								true // preferSeriesPoster = true for vertical cards
							)
						}
					)

					if (items.isEmpty()) removeRow()
				} else {
					setItems(
						items = response.items,
						transform = { item, _ ->
							BaseItemDtoBaseRowItem(
								item,
								false, // Don't prefer parent thumb for vertical posters
								true, // Static height
								BaseRowItemSelectAction.ShowDetails,
								true // preferSeriesPoster = true for vertical cards
							)
						}
					)

					if (response.items.isEmpty()) removeRow()
				}
			}.fold(
				onSuccess = { notifyRetrieveFinished() },
				onFailure = { error -> notifyRetrieveFinished(error as? Exception) }
			)
		}
	}
}