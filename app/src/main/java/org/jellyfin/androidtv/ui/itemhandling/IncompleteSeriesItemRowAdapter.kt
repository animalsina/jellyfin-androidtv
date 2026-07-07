package org.jellyfin.androidtv.ui.itemhandling

import android.content.Context
import androidx.leanback.widget.Row
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.data.repository.SeriesProgressHelper
import org.jellyfin.androidtv.ui.presentation.CardPresenter
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.request.GetItemsRequest

class IncompleteSeriesItemRowAdapter(
	context: Context,
	private val query: GetItemsRequest,
	presenter: CardPresenter,
	parent: MutableObjectAdapter<Row>,
) : ItemRowAdapter(context, query, 0, false, true, presenter, parent) {
	fun retrieveIncompleteSeries(api: ApiClient) {
		ProcessLifecycleOwner.get().lifecycleScope.launch {
			runCatching {
				val response = withContext(Dispatchers.IO) {
					api.itemsApi.getItems(query).content
				}

				val items = withContext(Dispatchers.IO) {
					response.items.orEmpty()
						.take(MAX_PROGRESS_CANDIDATES)
						.chunked(PROGRESS_BATCH_SIZE)
						.flatMap { batch ->
							batch.map { series ->
							async {
								val progress = SeriesProgressHelper.loadIncompleteProgress(api, series)
								if (progress == null) null else IncompleteSeriesRow(series, progress)
							}
							}.awaitAll()
						}
						.filterNotNull()
				}

				setItems(
					items = items,
					transform = { item, _ -> IncompleteSeriesBaseRowItem(item.series, item.progress) }
				)

				if (items.isEmpty()) removeRow()
			}.fold(
				onSuccess = { notifyRetrieveFinished() },
				onFailure = { error -> notifyRetrieveFinished(error as? Exception) }
			)
		}
	}

	private data class IncompleteSeriesRow(
		val series: BaseItemDto,
		val progress: org.jellyfin.androidtv.data.model.IncompleteSeriesProgress,
	)

	private companion object {
		private const val MAX_PROGRESS_CANDIDATES = 16
		private const val PROGRESS_BATCH_SIZE = 3
	}
}
