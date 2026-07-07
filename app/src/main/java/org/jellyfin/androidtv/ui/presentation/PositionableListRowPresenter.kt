package org.jellyfin.androidtv.ui.presentation

import android.util.TypedValue
import android.view.ViewGroup
import androidx.leanback.widget.RowPresenter
import org.jellyfin.androidtv.util.TouchNavigationHelper
import timber.log.Timber

class PositionableListRowPresenter : CustomListRowPresenter {
	private var viewHolder: ViewHolder? = null

	constructor() : super()
	constructor(padding: Int?) : super(padding)

	init {
		shadowEnabled = false
	}

	override fun isUsingDefaultShadow() = false

	override fun onSelectLevelChanged(holder: RowPresenter.ViewHolder) = Unit

	override fun onBindRowViewHolder(holder: RowPresenter.ViewHolder, item: Any) {
		super.onBindRowViewHolder(holder, item)
		if (holder !is ViewHolder) return

		viewHolder = holder

		// Add horizontal spacing between items
		val spacingInPixels = TypedValue.applyDimension(
			TypedValue.COMPLEX_UNIT_DIP,
			16f,
			holder.view.context.resources.displayMetrics
		).toInt()
		holder.gridView?.setItemSpacing(spacingInPixels)
		holder.gridView?.setItemViewCacheSize(6)
		holder.gridView?.setHasFixedSize(true)

		if (TouchNavigationHelper.shouldUseTouchHomeNavigation(holder.view.context)) {
			// Horizontal Leanback rows also own a selectedPosition. On touch devices this selection
			// can bubble up through the vertical RowsSupportFragment and make the home snap back
			// to the first selected row. Keep the row clickable/scrollable, but remove TV focus.
			holder.gridView?.isFocusable = false
			holder.gridView?.isFocusableInTouchMode = false
			holder.gridView?.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
			holder.gridView?.preserveFocusAfterLayout = false
			holder.gridView?.clearFocus()
		}
	}

	var position: Int
		get() = viewHolder?.gridView?.selectedPosition ?: -1
		set(value) {
			Timber.d("Setting position to $value")
			viewHolder?.gridView?.selectedPosition = value
		}
}
