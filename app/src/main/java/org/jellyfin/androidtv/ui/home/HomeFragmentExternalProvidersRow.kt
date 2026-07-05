package org.jellyfin.androidtv.ui.home

import android.content.Context
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.Row
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.constant.ExternalProviderOption
import org.jellyfin.androidtv.ui.GridButton
import org.jellyfin.androidtv.ui.itemhandling.GridButtonBaseRowItem
import org.jellyfin.androidtv.ui.presentation.CardPresenter
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter

class HomeFragmentExternalProvidersRow : HomeFragmentRow {
	override fun addToRowsAdapter(context: Context, cardPresenter: CardPresenter, rowsAdapter: MutableObjectAdapter<Row>) {
		val providerAdapter = MutableObjectAdapter<Any>(cardPresenter)
		providerAdapter.add(
			GridButtonBaseRowItem(
				GridButton(
					ExternalProviderOption.PLUTO_TV_OPTION_ID,
					context.getString(R.string.lbl_open_pluto_tv),
					R.drawable.tile_port_video
				)
			)
		)
		providerAdapter.add(
			GridButtonBaseRowItem(
				GridButton(
					ExternalProviderOption.RAIPLAY_OPTION_ID,
					context.getString(R.string.lbl_open_raiplay),
					R.drawable.tile_port_video
				)
			)
		)
		providerAdapter.add(
			GridButtonBaseRowItem(
				GridButton(
					ExternalProviderOption.PRIME_VIDEO_OPTION_ID,
					context.getString(R.string.lbl_open_prime_video),
					R.drawable.tile_port_video
				)
			)
		)

		rowsAdapter.add(ListRow(HeaderItem(context.getString(R.string.lbl_external_provider_row)), providerAdapter))
	}
}
