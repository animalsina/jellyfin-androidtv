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

class HomeFragmentExternalProvidersRow(
	private val items: List<ExternalCatalogItem>,
	private val title: String? = null,
	private val splitByCategory: Boolean = false,
) : HomeFragmentRow {
	override fun addToRowsAdapter(context: Context, cardPresenter: CardPresenter, rowsAdapter: MutableObjectAdapter<Row>) {
		if (items.isEmpty()) return

		if (splitByCategory) {
			items
				.groupBy { categoryTitle(context, it) }
				.toList()
				.sortedByDescending { (_, groupItems) -> groupItems.size }
				.take(MAX_CATEGORY_ROWS)
				.forEach { (category, groupItems) -> addRow(rowsAdapter, cardPresenter, category, groupItems.take(MAX_ITEMS_PER_ROW)) }
		} else {
			addRow(rowsAdapter, cardPresenter, title ?: context.getString(R.string.home_section_external_catalog), items.take(MAX_ITEMS_PER_ROW))
		}
	}

	private fun addRow(
		rowsAdapter: MutableObjectAdapter<Row>,
		cardPresenter: CardPresenter,
		header: String,
		rowItems: List<ExternalCatalogItem>,
	) {
		if (rowItems.isEmpty()) return
		val providerAdapter = MutableObjectAdapter<Any>(cardPresenter)
		rowItems.forEach { providerAdapter.add(ExternalCatalogBaseRowItem(it)) }
		rowsAdapter.add(ListRow(HeaderItem(header), providerAdapter))
	}

	private fun categoryTitle(context: Context, item: ExternalCatalogItem): String {
		val category = item.group?.takeIf { it.isNotBlank() }
		val base = title ?: when {
			item.providerId.startsWith("pluto", ignoreCase = true) -> "Pluto TV"
			item.providerId.startsWith("raiplay", ignoreCase = true) -> "RaiPlay"
			else -> context.getString(R.string.home_section_external_catalog)
		}
		return if (category.isNullOrBlank() || category.equals(base, ignoreCase = true)) base else "$base · $category"
	}

	private companion object {
		private const val MAX_CATEGORY_ROWS = 8
		private const val MAX_ITEMS_PER_ROW = 32
	}
}
