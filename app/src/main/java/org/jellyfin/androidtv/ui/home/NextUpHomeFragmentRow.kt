package org.jellyfin.androidtv.ui.home

import android.content.Context
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.Row
import org.jellyfin.androidtv.ui.browsing.BrowseRowDef
import org.jellyfin.androidtv.ui.itemhandling.NextUpItemRowAdapter
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter
import org.jellyfin.sdk.api.client.ApiClient
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Specialized home fragment row for Next Up items that uses vertical poster cards
 * instead of horizontal thumbnail cards for a more modern appearance.
 */
class NextUpHomeFragmentRow(
	private val browseRowDef: BrowseRowDef
) : HomeFragmentRow, KoinComponent {

	private val api by inject<ApiClient>()

	override fun addToRowsAdapter(
		context: Context,
		cardPresenter: org.jellyfin.androidtv.ui.presentation.CardPresenter,
		rowsAdapter: MutableObjectAdapter<Row>
	) {
		val header = HeaderItem(browseRowDef.headerText)

		// Use the passed card presenter for uniform card sizes across all rows
		val rowAdapter = NextUpItemRowAdapter(
			context,
			browseRowDef.nextUpQuery!!,
			cardPresenter,
			rowsAdapter
		)

		rowAdapter.setReRetrieveTriggers(browseRowDef.changeTriggers)
		val row = ListRow(header, rowAdapter)
		rowAdapter.setRow(row)
		// Use the custom retrieve method
		rowAdapter.retrieveNextUpWithSeriesPosters(api)
		rowsAdapter.add(row)
	}
}