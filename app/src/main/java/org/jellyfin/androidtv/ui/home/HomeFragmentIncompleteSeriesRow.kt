package org.jellyfin.androidtv.ui.home

import android.content.Context
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.Row
import org.jellyfin.androidtv.ui.browsing.BrowseRowDef
import org.jellyfin.androidtv.ui.itemhandling.IncompleteSeriesItemRowAdapter
import org.jellyfin.androidtv.ui.presentation.CardPresenter
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter
import org.jellyfin.sdk.api.client.ApiClient
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class HomeFragmentIncompleteSeriesRow(
	private val browseRowDef: BrowseRowDef,
) : HomeFragmentRow, KoinComponent {
	private val api by inject<ApiClient>()

	override fun addToRowsAdapter(context: Context, cardPresenter: CardPresenter, rowsAdapter: MutableObjectAdapter<Row>) {
		val rowAdapter = IncompleteSeriesItemRowAdapter(
			context = context,
			query = browseRowDef.query,
			presenter = cardPresenter,
			parent = rowsAdapter,
		)
		rowAdapter.setSuppressEmptyPlaceholder(true)
		rowAdapter.setReRetrieveTriggers(browseRowDef.changeTriggers)

		val row = ListRow(HeaderItem(browseRowDef.headerText), rowAdapter)
		rowAdapter.setRow(row)
		rowAdapter.retrieveIncompleteSeries(api)
		rowsAdapter.add(row)
	}
}
