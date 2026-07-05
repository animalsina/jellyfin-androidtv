package org.jellyfin.androidtv.ui.home

import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import androidx.leanback.app.RowsSupportFragment
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.OnItemViewSelectedListener
import androidx.leanback.widget.Presenter
import androidx.recyclerview.widget.RecyclerView
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.constant.CustomMessage
import org.jellyfin.androidtv.constant.HomeSectionType
import org.jellyfin.androidtv.data.model.DataRefreshService
import org.jellyfin.androidtv.data.repository.CustomMessageRepository
import org.jellyfin.androidtv.data.repository.NotificationsRepository
import org.jellyfin.androidtv.data.repository.UserViewsRepository
import org.jellyfin.androidtv.data.service.BackgroundService
import org.jellyfin.androidtv.preference.UserSettingPreferences
import org.jellyfin.androidtv.ui.browsing.CompositeClickedListener
import org.jellyfin.androidtv.ui.browsing.CompositeSelectedListener
import org.jellyfin.androidtv.ui.itemhandling.BaseRowItem
import org.jellyfin.androidtv.ui.itemhandling.ItemLauncher
import org.jellyfin.androidtv.ui.itemhandling.ItemRowAdapter
import org.jellyfin.androidtv.ui.itemhandling.refreshItem
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.playback.AudioEventListener
import org.jellyfin.androidtv.ui.playback.MediaManager
import org.jellyfin.androidtv.ui.presentation.CardPresenter
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter
import org.jellyfin.androidtv.ui.presentation.PositionableListRowPresenter
import org.jellyfin.androidtv.util.KeyProcessor
import org.jellyfin.androidtv.util.TouchNavigationHelper
import org.jellyfin.playback.core.PlaybackManager
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.liveTvApi
import org.jellyfin.sdk.api.sockets.subscribe
import org.jellyfin.sdk.model.api.LibraryChangedMessage
import org.jellyfin.sdk.model.api.UserDataChangedMessage
import org.koin.android.ext.android.inject
import timber.log.Timber
import kotlin.math.abs
import kotlin.time.Duration.Companion.seconds

@Suppress("UNCHECKED_CAST")
class HomeRowsFragment : RowsSupportFragment(), AudioEventListener, View.OnKeyListener {
	private val api by inject<ApiClient>()
	private val backgroundService by inject<BackgroundService>()
	private val playbackManager by inject<PlaybackManager>()
	private val mediaManager by inject<MediaManager>()
	private val notificationsRepository by inject<NotificationsRepository>()
	private val userRepository by inject<UserRepository>()
	private val userSettingPreferences by inject<UserSettingPreferences>()
	private val userViewsRepository by inject<UserViewsRepository>()
	private val dataRefreshService by inject<DataRefreshService>()
	private val customMessageRepository by inject<CustomMessageRepository>()
	private val navigationRepository by inject<NavigationRepository>()
	private val itemLauncher by inject<ItemLauncher>()
	private val keyProcessor by inject<KeyProcessor>()
	private val homePreviewViewModel: HomePreviewViewModel by activityViewModel()

	private val helper by lazy { HomeFragmentHelper(requireContext(), userRepository) }
	private val useTouchHomeNavigation by lazy { TouchNavigationHelper.shouldUseTouchHomeNavigation(requireContext()) }
	private var touchHomeDownX = 0f
	private var touchHomeDownY = 0f
	private var touchHomeLastX = 0f
	private var touchHomeLastY = 0f
	private var touchHomeIsDragging = false
	private var touchHomeAxis: TouchHomeAxis = TouchHomeAxis.UNDECIDED
	private var touchHomeHorizontalTarget: RecyclerView? = null
	private val touchHomeSlop by lazy { ViewConfiguration.get(requireContext()).scaledTouchSlop }

	private enum class TouchHomeAxis {
		UNDECIDED,
		VERTICAL,
		HORIZONTAL,
	}

	private data class TouchScrollAnchor(
		val position: Int,
		val top: Int,
	)

	// Data
	private var currentItem: BaseRowItem? = null
	private var currentRow: ListRow? = null
	private var justLoaded = true
	private var selectedPreviewJob: Job? = null

	// Special rows
	private val notificationsRow by lazy { NotificationsHomeFragmentRow(lifecycleScope, notificationsRepository) }
	private val nowPlaying by lazy { HomeFragmentNowPlayingRow(lifecycleScope, playbackManager, mediaManager) }
	private val liveTVRow by lazy { HomeFragmentLiveTVRow(requireActivity(), userRepository, navigationRepository) }

	private fun scheduleSelectedPreview(item: BaseRowItem) {
		selectedPreviewJob?.cancel()
		selectedPreviewJob = lifecycleScope.launch {
			delay(160)
			if (currentItem !== item) return@launch
			backgroundService.setBackground(item.baseItem)
			homePreviewViewModel.updateSelectedItem(item)
		}
	}

	private fun clearSelectedPreview() {
		selectedPreviewJob?.cancel()
		selectedPreviewJob = null
		backgroundService.clearBackgrounds()
		homePreviewViewModel.updateSelectedItem(null)
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		adapter = MutableObjectAdapter<Row>(PositionableListRowPresenter(0))

		// Build initial rows
		buildHomeRows()

		onItemViewClickedListener = CompositeClickedListener().apply {
			registerListener(ItemViewClickedListener())
			registerListener(liveTVRow::onItemClicked)
			registerListener(notificationsRow::onItemClicked)
		}

		onItemViewSelectedListener = CompositeSelectedListener().apply {
			registerListener(ItemViewSelectedListener())
		}

		customMessageRepository.message.flowWithLifecycle(lifecycle, Lifecycle.State.RESUMED).onEach { message ->
			when (message) {
				CustomMessage.RefreshCurrentItem -> refreshCurrentItem()
				else -> Unit
			}
		}.launchIn(lifecycleScope)

		lifecycleScope.launch {
			lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
				try {
					api.webSocket.subscribe<UserDataChangedMessage>()
						.onEach { refreshRows(force = true, delayed = false) }
						.launchIn(this)
				} catch (e: Exception) {
					Timber.e(e, "WebSocket subscription failed")
				}

				api.webSocket.subscribe<LibraryChangedMessage>().onEach { refreshRows(force = true, delayed = false) }.launchIn(this)
			}
		}

		// Subscribe to Audio messages
		mediaManager.addAudioEventListener(this)
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		if (!useTouchHomeNavigation) return

		verticalGridView?.apply {
			// Phones/tablets must use normal RecyclerView touch scrolling. The root cause of the
			// jump-to-top bug is Leanback focus restoration, not RecyclerView touch itself.
			// Do not consume touch gestures manually: that disables fling/nested scroll and still
			// lets Leanback restore selectedPosition on later layout passes. Instead remove the
			// focus contract from the vertical grid and its rows while leaving click listeners intact.
			isFocusable = false
			isFocusableInTouchMode = false
			descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
			preserveFocusAfterLayout = false
			isNestedScrollingEnabled = true
			isVerticalScrollBarEnabled = true
			setItemViewCacheSize(12)
			setOnTouchListener(null)
			clearFocus()
		}
	}

	private fun handleTouchHomeScroll(recyclerView: RecyclerView, event: MotionEvent): Boolean {
		if (!useTouchHomeNavigation) return false

		when (event.actionMasked) {
			MotionEvent.ACTION_DOWN -> {
				touchHomeDownX = event.x
				touchHomeDownY = event.y
				touchHomeLastX = event.x
				touchHomeLastY = event.y
				touchHomeIsDragging = false
				touchHomeAxis = TouchHomeAxis.UNDECIDED
				touchHomeHorizontalTarget = findNestedRecyclerViewUnder(recyclerView, event.x, event.y)
				recyclerView.parent?.requestDisallowInterceptTouchEvent(true)
				recyclerView.clearFocus()
				return true
			}

			MotionEvent.ACTION_MOVE -> {
				val totalDx = event.x - touchHomeDownX
				val totalDy = event.y - touchHomeDownY
				val stepDx = touchHomeLastX - event.x
				val stepDy = touchHomeLastY - event.y

				if (!touchHomeIsDragging && (abs(totalDx) > touchHomeSlop || abs(totalDy) > touchHomeSlop)) {
					touchHomeIsDragging = true
					touchHomeAxis = if (abs(totalDx) > abs(totalDy)) TouchHomeAxis.HORIZONTAL else TouchHomeAxis.VERTICAL
				}

				if (touchHomeIsDragging) {
					when (touchHomeAxis) {
						TouchHomeAxis.HORIZONTAL -> touchHomeHorizontalTarget?.scrollBy(stepDx.toInt(), 0)
						TouchHomeAxis.VERTICAL -> recyclerView.scrollBy(0, stepDy.toInt())
						TouchHomeAxis.UNDECIDED -> Unit
					}
				}

				touchHomeLastX = event.x
				touchHomeLastY = event.y
				return true
			}

			MotionEvent.ACTION_UP -> {
				if (!touchHomeIsDragging) performTouchHomeClick(recyclerView, event.x, event.y)
				recyclerView.parent?.requestDisallowInterceptTouchEvent(false)
				resetTouchHomeGesture()
				return true
			}

			MotionEvent.ACTION_CANCEL -> {
				recyclerView.parent?.requestDisallowInterceptTouchEvent(false)
				resetTouchHomeGesture()
				return true
			}
		}

		return true
	}

	private fun resetTouchHomeGesture() {
		touchHomeIsDragging = false
		touchHomeAxis = TouchHomeAxis.UNDECIDED
		touchHomeHorizontalTarget = null
	}

	private fun findNestedRecyclerViewUnder(parent: RecyclerView, x: Float, y: Float): RecyclerView? {
		val rowView = parent.findChildViewUnder(x, y) ?: return null
		val rowOffsetX = x - rowView.left
		val rowOffsetY = y - rowView.top
		return findRecyclerViewAt(rowView, rowOffsetX, rowOffsetY)
	}

	private fun findRecyclerViewAt(view: View, x: Float, y: Float): RecyclerView? {
		if (view is RecyclerView) return view
		if (view !is ViewGroup) return null

		for (index in view.childCount - 1 downTo 0) {
			val child = view.getChildAt(index) ?: continue
			if (x < child.left || x > child.right || y < child.top || y > child.bottom) continue
			findRecyclerViewAt(child, x - child.left, y - child.top)?.let { return it }
		}

		return null
	}

	private fun performTouchHomeClick(parent: RecyclerView, x: Float, y: Float) {
		val rowView = parent.findChildViewUnder(x, y) ?: return
		val target = findDeepestClickableViewAt(rowView, x - rowView.left, y - rowView.top) ?: rowView
		target.performClick()
	}

	private fun findDeepestClickableViewAt(view: View, x: Float, y: Float): View? {
		if (view !is ViewGroup) return if (view.isClickable) view else null

		for (index in view.childCount - 1 downTo 0) {
			val child = view.getChildAt(index) ?: continue
			if (x < child.left || x > child.right || y < child.top || y > child.bottom) continue
			findDeepestClickableViewAt(child, x - child.left, y - child.top)?.let { return it }
			if (child.isClickable) return child
		}

		return if (view.isClickable) view else null
	}

	private fun captureTouchScrollAnchor(recyclerView: RecyclerView?): TouchScrollAnchor? {
		if (!useTouchHomeNavigation || recyclerView == null || recyclerView.childCount == 0) return null
		val child = recyclerView.getChildAt(0) ?: return null
		val position = recyclerView.getChildAdapterPosition(child)
		if (position == RecyclerView.NO_POSITION) return null
		return TouchScrollAnchor(position, child.top)
	}

	private fun restoreTouchScrollAnchor(recyclerView: RecyclerView?, anchor: TouchScrollAnchor?) {
		if (!useTouchHomeNavigation || recyclerView == null || anchor == null) return
		for (index in 0 until recyclerView.childCount) {
			val child = recyclerView.getChildAt(index) ?: continue
			if (recyclerView.getChildAdapterPosition(child) == anchor.position) {
				recyclerView.scrollBy(0, child.top - anchor.top)
				recyclerView.clearFocus()
				return
			}
		}
	}

	private suspend fun addRowSequentially(
		context: Context, cardPresenter: CardPresenter, rowsAdapter: MutableObjectAdapter<Row>, row: HomeFragmentRow
	) {
		withContext(Dispatchers.Main) {
			row.addToRowsAdapter(context, cardPresenter, rowsAdapter)
		}
	}

	private fun buildHomeRows() {
		lifecycleScope.launch {
			val currentUser = withTimeout(30.seconds) {
				userRepository.currentUser.filterNotNull().first()
			}

			if (userSettingPreferences.shouldUpdate) userSettingPreferences.update()
			val homeSections = userSettingPreferences.activeHomesections
			val userViews = userViewsRepository.views.first()
			var includeLiveTvRows = false

			if (homeSections.contains(HomeSectionType.LIVE_TV) && currentUser.policy?.enableLiveTvAccess == true) {
				includeLiveTvRows = withTimeoutOrNull(5.seconds) {
					try {
						val recommendedPrograms by api.liveTvApi.getRecommendedPrograms(
							enableTotalRecordCount = false,
							imageTypeLimit = 1,
							isAiring = true,
							limit = 1,
						)
						recommendedPrograms.items.isNotEmpty()
					} catch (e: Exception) {
						Timber.w(e, "Live TV probe failed; skipping live rows for this home load")
						false
					}
				} ?: false
			}

			val rowsAdapter = adapter as MutableObjectAdapter<Row>
			val cardPresenter = CardPresenter(true, org.jellyfin.androidtv.constant.ImageType.POSTER, 120)
			val ctx = requireContext()
			val recyclerView = view as? androidx.recyclerview.widget.RecyclerView
			var initialPreviewSet = false

			withContext(Dispatchers.Main) {
				recyclerView?.suppressLayout(true)
				rowsAdapter.clear()
				recyclerView?.suppressLayout(false)
			}

			suspend fun appendRow(row: HomeFragmentRow?) {
				if (row == null) return
				withContext(Dispatchers.Main) {
					val touchScrollAnchor = captureTouchScrollAnchor(recyclerView)
					row.addToRowsAdapter(ctx, cardPresenter, rowsAdapter)
					recyclerView?.post { restoreTouchScrollAnchor(recyclerView, touchScrollAnchor) }
					if (!initialPreviewSet) {
						initialPreviewSet = true
						recyclerView?.post {
							val firstRow = rowsAdapter.firstOrNull() as? ListRow
							val adapterFirstRow = firstRow?.adapter as? ItemRowAdapter
							val firstItem = adapterFirstRow?.get(0) as? BaseRowItem
							if (firstItem != null) {
								backgroundService.setBackground(firstItem.baseItem)
								homePreviewViewModel.updateSelectedItem(firstItem)
							}
						}
					}
				}
			}

			val prioritySections = homeSections.filter(::isPriorityHomeSection)
			val deferredSections = homeSections.filterNot(::isPriorityHomeSection)

			for (section in prioritySections) {
				appendRow(withContext(Dispatchers.IO) { loadRowForSection(section, includeLiveTvRows, userViews) })
				delay(120)
			}

			for (section in deferredSections) {
				appendRow(withContext(Dispatchers.IO) { loadRowForSection(section, includeLiveTvRows, userViews) })
				delay(180)
			}

			if (includeLiveTvRows) {
				val onNowRow = withContext(Dispatchers.IO) { helper.loadOnNow() }
				appendRow(onNowRow)
			}
		}
	}

	private fun isPriorityHomeSection(section: HomeSectionType): Boolean = when (section) {
		HomeSectionType.RESUME,
		HomeSectionType.NEXT_UP,
		HomeSectionType.LATEST_MEDIA,
		HomeSectionType.RECOMMENDED_FOR_YOU,
		HomeSectionType.LIBRARY_TILES_SMALL,
		HomeSectionType.LIBRARY_BUTTONS,
		HomeSectionType.MOOD_LIGHT,
		HomeSectionType.MOOD_SHORT -> true
		else -> false
	}

	private suspend fun loadRowForSection(section: HomeSectionType, includeLiveTvRows: Boolean, userViews: Collection<org.jellyfin.sdk.model.api.BaseItemDto>): HomeFragmentRow? {
		return when (section) {
			HomeSectionType.LATEST_MEDIA -> helper.loadRecentlyAdded(userViews)
			HomeSectionType.LIBRARY_TILES_SMALL -> HomeFragmentViewsRow(small = false)
			HomeSectionType.LIBRARY_BUTTONS -> HomeFragmentViewsRow(small = true)
			HomeSectionType.RESUME -> helper.loadResumeVideo()
			HomeSectionType.RESUME_AUDIO -> helper.loadResumeAudio()
			HomeSectionType.ACTIVE_RECORDINGS -> helper.loadLatestLiveTvRecordings()
			HomeSectionType.NEXT_UP -> helper.loadNextUp()
			HomeSectionType.LIVE_TV -> if (includeLiveTvRows) liveTVRow else null
			HomeSectionType.RECOMMENDED_FOR_YOU -> helper.loadRecommendedForYou(userViews)
			HomeSectionType.TRENDING_THIS_WEEK -> helper.loadTrendingThisWeek(userViews)
			HomeSectionType.RECENTLY_RELEASED -> helper.loadRecentlyReleased(userViews)
			HomeSectionType.POPULAR_MOVIES -> helper.loadPopularMovies(userViews)
			HomeSectionType.POPULAR_TV -> helper.loadPopularTV(userViews)
			HomeSectionType.SIMILAR_TO_WATCHED -> helper.loadSimilarToWatched(userViews)
			HomeSectionType.GENRE_RANDOM_MOVIES -> helper.loadGenreRandomMovies(userViews)
			HomeSectionType.GENRE_RANDOM_TV -> helper.loadGenreRandomTV(userViews)
				HomeSectionType.GENRE_RANDOM_MIXED -> helper.loadGenreRandomMixed(userViews)
			HomeSectionType.MOOD_LIGHT -> helper.loadMoodLight(userViews)
			HomeSectionType.MOOD_ACTION -> helper.loadMoodAction(userViews)
			HomeSectionType.MOOD_SHORT -> helper.loadMoodShort(userViews)
			HomeSectionType.EXTERNAL_PROVIDERS -> helper.loadExternalProviders()
			else -> null
		}
	}


	override fun onKey(v: View?, keyCode: Int, event: KeyEvent?): Boolean {
		if (event?.action != KeyEvent.ACTION_UP) return false
		return keyProcessor.handleKey(keyCode, currentItem, activity)
	}

	/**
	 * Called when the fragment is resumed.
	 *
	 * Reacts to deletion by removing the current item from the row adapter
	 * and clearing the last deleted item ID.
	 * If not just loaded, refreshes the current item and updates the rows.
	 * Also updates the audio queue.
	 */
	override fun onResume() {
		super.onResume()

		//React to deletion
		if (currentRow != null && currentItem != null && currentItem?.baseItem != null && currentItem!!.baseItem!!.id == dataRefreshService.lastDeletedItemId) {
			(currentRow!!.adapter as ItemRowAdapter).remove(currentItem)
			currentItem = null
			dataRefreshService.lastDeletedItemId = null
		}

		if (!justLoaded) {
			//Re-retrieve anything that needs it but delay slightly so we don't take away gui landing
			refreshCurrentItem()
			refreshRows()
		} else {
			justLoaded = false
		}

		// Update audio queue
		Timber.i("Updating audio queue in HomeFragment (onResume)")
		nowPlaying.update(requireContext(), adapter as MutableObjectAdapter<Row>)
	}

	override fun onQueueStatusChanged(hasQueue: Boolean) {
		if (activity == null || requireActivity().isFinishing) return

		Timber.i("Updating audio queue in HomeFragment (onQueueStatusChanged)")
		nowPlaying.update(requireContext(), adapter as MutableObjectAdapter<Row>)
	}

	private fun refreshRows(force: Boolean = false, delayed: Boolean = true) {
		lifecycleScope.launch(Dispatchers.IO) {
			if (delayed) delay(1.5.seconds)

			repeat(adapter.size()) { i ->
				val rowAdapter = (adapter[i] as? ListRow)?.adapter as? ItemRowAdapter
				if (force) rowAdapter?.Retrieve()
				else rowAdapter?.ReRetrieveIfNeeded()
			}
		}
	}

	private fun refreshCurrentItem() {
		val adapter = currentRow?.adapter as? ItemRowAdapter ?: return
		val item = currentItem ?: return

		Timber.d("Refresh item ${item.getFullName(requireContext())}")
		adapter.refreshItem(api, this, item)
	}

	override fun onDestroy() {
		selectedPreviewJob?.cancel()
		verticalGridView?.setOnTouchListener(null)

		super.onDestroy()

		mediaManager.removeAudioEventListener(this)
	}

	private inner class ItemViewClickedListener : OnItemViewClickedListener {
		override fun onItemClicked(
			itemViewHolder: Presenter.ViewHolder?,
			item: Any?,
			rowViewHolder: RowPresenter.ViewHolder?,
			row: Row?,
		) {
			if (item !is BaseRowItem) return
			if (row !is ListRow) return
			@Suppress("UNCHECKED_CAST")
			itemLauncher.launch(item, row.adapter as MutableObjectAdapter<Any>, requireContext())
		}
	}

	private inner class ItemViewSelectedListener : OnItemViewSelectedListener {
		override fun onItemSelected(
			itemViewHolder: Presenter.ViewHolder?,
			item: Any?,
			rowViewHolder: RowPresenter.ViewHolder?,
			row: Row?,
		) {
			if (useTouchHomeNavigation) {
				// Phones/tablets should scroll naturally. Leanback selection is a TV focus concept and
				// can force the vertical list back to the selected row while the user is touching it.
				return
			}

			if (item !is BaseRowItem) {
				// On touch/mobile scroll Leanback may briefly report a row selection without an item.
				// Do not force-select index 0: that makes horizontal carousels jump back to the start
				// and looks like a rerender/wrap while scrolling.
				if (row is ListRow && currentItem != null) return

				currentItem = null
				currentRow = null
				clearSelectedPreview()
			} else {
				currentItem = item
				currentRow = row as ListRow

				val itemRowAdapter = row.adapter as? ItemRowAdapter
				itemRowAdapter?.loadMoreItemsIfNeeded(itemRowAdapter.indexOf(item))

				// Backgrounds and trailer previews are intentionally debounced. Without this, fast D-pad
				// navigation decodes a large image and starts trailer discovery for every transient focus.
				scheduleSelectedPreview(item)
			}
		}
	}
}
