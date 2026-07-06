package org.jellyfin.androidtv.ui.home

import android.content.Context
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.Row
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.model.ExternalCatalogItem
import org.jellyfin.androidtv.ui.itemhandling.ExternalCatalogBaseRowItem
import org.jellyfin.androidtv.ui.presentation.CardPresenter
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter

class HomeFragmentOnlineNewReleasesRow(
	private val items: List<ExternalCatalogItem>,
) : HomeFragmentRow {
	override fun addToRowsAdapter(context: Context, cardPresenter: CardPresenter, rowsAdapter: MutableObjectAdapter<Row>) {
		if (items.isEmpty()) return

		val adapter = MutableObjectAdapter<Any>(cardPresenter)
		items.forEach { adapter.add(ExternalCatalogBaseRowItem(it)) }

		rowsAdapter.add(ListRow(HeaderItem(context.getString(R.string.home_section_online_new_releases)), adapter))
	}
}
