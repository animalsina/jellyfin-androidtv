package org.jellyfin.androidtv.ui.itemhandling

import android.content.Context
import androidx.leanback.widget.Row
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.ui.presentation.CardPresenter
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.request.GetResumeItemsRequest

/**
 * Custom ItemRowAdapter for Continue Watching items that creates BaseItemDtoBaseRowItem instances
 * with preferSeriesPoster = true for vertical poster cards instead of horizontal thumbnails.
 */
class ContinueWatchingItemRowAdapter(
	context: Context,
	private val resumeQuery: GetResumeItemsRequest,
	presenter: CardPresenter,
	parent: MutableObjectAdapter<Row>
) : ItemRowAdapter(context, resumeQuery, 0, false, true, presenter, parent) {

	fun retrieveResumeWithSeriesPosters(api: ApiClient) {
		ProcessLifecycleOwner.get().lifecycleScope.launch {
			runCatching {
				val response = withContext(Dispatchers.IO) {
					api.itemsApi.getResumeItems(resumeQuery).content
				}

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
			}.fold(
				onSuccess = { notifyRetrieveFinished() },
				onFailure = { error -> notifyRetrieveFinished(error as? Exception) }
			)
		}
	}
}