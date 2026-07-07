package org.jellyfin.androidtv.ui.itemhandling

import android.content.Context
import org.jellyfin.androidtv.data.model.IncompleteSeriesProgress
import org.jellyfin.sdk.model.api.BaseItemDto

class IncompleteSeriesBaseRowItem(
	series: BaseItemDto,
	private val progress: IncompleteSeriesProgress,
) : BaseItemDtoBaseRowItem(
	item = series,
	preferParentThumb = false,
	staticHeight = true,
	selectAction = BaseRowItemSelectAction.ShowDetails,
) {
	override fun getSubText(context: Context): String = progress.summary(context)
	override fun getSummary(context: Context): String = progress.summary(context)
}
