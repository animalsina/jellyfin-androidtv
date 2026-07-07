package org.jellyfin.androidtv.ui.home

import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Gravity
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import androidx.leanback.app.RowsSupportFragment
import androidx.leanback.widget.BaseGridView
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.constant.CustomMessage
import org.jellyfin.androidtv.constant.HomeSectionType
import org.jellyfin.androidtv.constant.LiveTvOption
import org.jellyfin.androidtv.data.model.DataRefreshService
import org.jellyfin.androidtv.data.repository.CustomMessageRepository
import org.jellyfin.androidtv.data.repository.ExternalCatalogRepository
import org.jellyfin.androidtv.data.repository.NotificationsRepository
import org.jellyfin.androidtv.data.repository.UserViewsRepository
import org.jellyfin.androidtv.data.service.BackgroundService
import org.jellyfin.androidtv.preference.UserSettingPreferences
import org.jellyfin.androidtv.ui.GridButton
import org.jellyfin.androidtv.ui.browsing.CompositeClickedListener
import org.jellyfin.androidtv.ui.browsing.CompositeSelectedListener
import org.jellyfin.androidtv.streaming.ExternalCatalogLauncher
import org.jellyfin.androidtv.streaming.StreamingAvailabilityHelper
import org.jellyfin.androidtv.ui.itemhandling.BaseRowItem
import org.jellyfin.androidtv.ui.itemhandling.ExternalCatalogBaseRowItem
import org.jellyfin.androidtv.ui.itemhandling.ItemLauncher
import org.jellyfin.androidtv.ui.itemhandling.ItemRowAdapter
import org.jellyfin.androidtv.ui.itemhandling.refreshItem
import org.jellyfin.androidtv.ui.navigation.Destinations
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
import org.jellyfin.sdk.api.sockets.subscribe
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.LibraryChangedMessage
import org.jellyfin.sdk.model.api.UserDataChangedMessage
import org.koin.android.ext.android.inject
import timber.log.Timber
import java.time.Instant
import kotlin.math.abs
import kotlin.time.Duration.Companion.seconds

@Suppress("UNCHECKED_CAST")
class HomeRowsFragment : RowsSupportFragment(), AudioEventListener, View.OnKeyListener {
	companion object {
		private const val TOP_MENU_DOUBLE_UP_WINDOW_MS = 850L
		private const val SOFT_HOME_REFRESH_TTL_MS = 90_000L
		private const val HOME_ROWS_REBUILD_TTL_MS = 5 * 60_000L
		private const val MAX_INITIAL_HOME_ROWS = 3
		private const val MENU_HOME_OPEN_DETAILS = 1001
		private const val MENU_HOME_PLAY_NOW = 1002
		private const val MENU_HOME_WHERE_TO_WATCH = 1003
		private const val MENU_HOME_SEARCH_SERVER = 1004
		private const val MENU_HOME_MORE_ACTIONS = 1005
		private val PLAYABLE_CONTEXT_TYPES = setOf(
			BaseItemKind.MOVIE,
			BaseItemKind.EPISODE,
			BaseItemKind.VIDEO,
			BaseItemKind.AUDIO,
			BaseItemKind.TV_CHANNEL,
			BaseItemKind.PROGRAM,
		)
	}

	private val api by inject<ApiClient>()
	private val backgroundService by inject<BackgroundService>()
	private val playbackManager by inject<PlaybackManager>()
	private val homeRowsKeyInterceptListener = object : BaseGridView.OnKeyInterceptListener {
		override fun onInterceptKeyEvent(event: KeyEvent): Boolean = handleHomeRowsKeyIntercept(event)
	}
	private val mediaManager by inject<MediaManager>()
	private val notificationsRepository by inject<NotificationsRepository>()
	private val userRepository by inject<UserRepository>()
	private val userSettingPreferences by inject<UserSettingPreferences>()
	private val userViewsRepository by inject<UserViewsRepository>()
	private val dataRefreshService by inject<DataRefreshService>()
	private val customMessageRepository by inject<CustomMessageRepository>()
	private val externalCatalogRepository by inject<ExternalCatalogRepository>()
	private val navigationRepository by inject<NavigationRepository>()
	private val itemLauncher by inject<ItemLauncher>()
	private val keyProcessor by inject<KeyProcessor>()
	private val homePreviewViewModel: HomePreviewViewModel by activityViewModel()

	private val helper by lazy { HomeFragmentHelper(requireContext(), userRepository, externalCatalogRepository, api) }
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
	private var buildRowsJob: Job? = null
	private var loadedHomeSections: List<HomeSectionType> = emptyList()
	private var lastRowsBuildAt = 0L
	private var lastSoftRefreshAt = 0L
	private var lastObservedLibraryChange: Instant? = null
	private var lastToolbarUpRequestAt = 0L
	private var consumeToolbarUpKeyUp = false
	private var contextMenuShowing = false
	private var homeLoadingProgress: ProgressBar? = null

	// Special rows
	private val notificationsRow by lazy { NotificationsHomeFragmentRow(lifecycleScope, notificationsRepository) }
	private val nowPlaying by lazy { HomeFragmentNowPlayingRow(lifecycleScope, playbackManager, mediaManager) }
	private val liveTVRow by lazy { HomeFragmentLiveTVRow(requireActivity(), userRepository) }

	private fun scheduleSelectedPreview(item: BaseRowItem) {
		selectedPreviewJob?.cancel()
		selectedPreviewJob = lifecycleScope.launch {
			delay(350) // Increased debounce to reduce load during fast scrolling
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
			registerListener(notificationsRow::onItemClicked)
		}

		onItemViewSelectedListener = CompositeSelectedListener().apply {
			registerListener(ItemViewSelectedListener())
		}

		customMessageRepository.message.flowWithLifecycle(lifecycle, Lifecycle.State.RESUMED).onEach { message ->
			when (message) {
				CustomMessage.RefreshCurrentItem -> refreshCurrentItem()
				CustomMessage.RefreshHomeRows -> buildHomeRows(forceUpdateSettings = true)
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

				api.webSocket.subscribe<LibraryChangedMessage>()
					.onEach { refreshRows(force = true, delayed = true) }
					.launchIn(this)
			}
		}

		// Subscribe to Audio messages
		mediaManager.addAudioEventListener(this)
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		installHomeLoadingProgress(view)

		verticalGridView?.apply {
			if (id == View.NO_ID) id = View.generateViewId()
			setOnKeyListener(this@HomeRowsFragment)
			setOnKeyInterceptListener(homeRowsKeyInterceptListener)
			// Keep card view cache small. Too large causes visible stalls during vertical scroll
			// as Leanback tries to keep dozens of complex Compose-based cards in memory.
			setItemViewCacheSize(3)
			isNestedScrollingEnabled = false
			isVerticalScrollBarEnabled = true
			setHasFixedSize(true)

			if (useTouchHomeNavigation) {
				// Phones/tablets must use normal RecyclerView touch scrolling. The root cause of the
				// jump-to-top bug is Leanback focus restoration, not RecyclerView touch itself.
				// Do not consume touch gestures manually: that disables fling/nested scroll and still
				// lets Leanback restore selectedPosition on later layout passes. Instead remove the
				// focus contract from the vertical grid and its rows while leaving click listeners intact.
				isFocusable = false
				isFocusableInTouchMode = false
				descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
				preserveFocusAfterLayout = false
				setOnTouchListener(null)
				clearFocus()
			}
		}
	}

	private fun installHomeLoadingProgress(root: View) {
		val parent = activity?.findViewById<FrameLayout>(android.R.id.content) ?: return
		if (homeLoadingProgress?.parent != null) return

		val height = (4 * resources.displayMetrics.density).toInt().coerceAtLeast(3)
		val progressBar = ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal).apply {
			max = 100
			progress = 0
			isIndeterminate = false
			visibility = View.GONE
			importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
		}

		val params = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, height, Gravity.TOP)

		parent.addView(progressBar, params)
		homeLoadingProgress = progressBar
	}

	private fun showHomeLoadingProgress(completed: Int, total: Int) {
		homeLoadingProgress?.apply {
			max = total.coerceAtLeast(1)
			progress = completed.coerceIn(0, max)
			visibility = View.VISIBLE
		}
	}

	private fun hideHomeLoadingProgress() {
		homeLoadingProgress?.visibility = View.GONE
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

	private fun buildHomeRows(forceUpdateSettings: Boolean = false) {
	buildRowsJob?.cancel()
	buildRowsJob = lifecycleScope.launch {
		try {
			showHomeLoadingProgress(0, 1)
			val currentUser = withTimeout(30.seconds) {
					userRepository.currentUser.filterNotNull().first()
				}

				if (forceUpdateSettings || userSettingPreferences.shouldUpdate) userSettingPreferences.update()
				val homeSections = userSettingPreferences.activeHomesections
				val rowsAdapter = adapter as MutableObjectAdapter<Row>
				val now = SystemClock.uptimeMillis()

				// Navigating back to home should be instant. Reuse the existing rows unless settings changed
				// or the cache is old enough that a full structural rebuild is actually useful.
			if (!forceUpdateSettings && rowsAdapter.size() > 0 && homeSections == loadedHomeSections && now - lastRowsBuildAt < HOME_ROWS_REBUILD_TTL_MS) {
				hideHomeLoadingProgress()
				return@launch
			}

				loadedHomeSections = homeSections
				lastRowsBuildAt = now
				val userViews = userViewsRepository.views.first()
				val includeLiveTvRows = homeSections.contains(HomeSectionType.LIVE_TV) && currentUser.policy?.enableLiveTvAccess == true

				val cardPresenter = CardPresenter(true, org.jellyfin.androidtv.constant.ImageType.POSTER, 120)
				val ctx = context ?: return@launch
				val recyclerView = verticalGridView
				var initialPreviewSet = false

			val prioritySections = homeSections.filter(::isPriorityHomeSection).take(MAX_INITIAL_HOME_ROWS)
			val deferredSections = homeSections.filterNot { section -> prioritySections.contains(section) }
			val totalLoadingSteps = (prioritySections.size + deferredSections.size + if (includeLiveTvRows) 1 else 0).coerceAtLeast(1)
			var completedLoadingSteps = 0
			showHomeLoadingProgress(completedLoadingSteps, totalLoadingSteps)
			val priorityRows = withContext(Dispatchers.IO) {
					prioritySections.mapNotNull { section ->
						if (!isAdded) null else loadRowForSection(section, includeLiveTvRows, userViews)
					}
				}

				suspend fun appendRow(row: HomeFragmentRow?) {
					if (row == null || !isAdded) return
					withContext(Dispatchers.Main) {
						val touchScrollAnchor = captureTouchScrollAnchor(recyclerView)
						val rowCountBefore = rowsAdapter.size()
						row.addToRowsAdapter(ctx, cardPresenter, rowsAdapter)
						if (rowsAdapter.size() == rowCountBefore) return@withContext
						recyclerView?.post {
							val view = recyclerView ?: return@post
							restoreTouchScrollAnchor(view, touchScrollAnchor)
						}
						if (!initialPreviewSet) {
							initialPreviewSet = true
							recyclerView?.post {
								val firstRow = rowsAdapter.firstOrNull() as? ListRow
								val adapterFirstRow = firstRow?.adapter as? ItemRowAdapter
								if (adapterFirstRow != null && adapterFirstRow.size() > 0) {
									val firstItem = adapterFirstRow.get(0) as? BaseRowItem
									if (firstItem != null && isAdded) {
										backgroundService.setBackground(firstItem.baseItem)
										homePreviewViewModel.updateSelectedItem(firstItem)
									}
								}
							}
						}
					}
				}

			withContext(Dispatchers.Main) {
				rowsAdapter.clear()
				priorityRows.forEach { row -> row.addToRowsAdapter(ctx, cardPresenter, rowsAdapter) }
				completedLoadingSteps = prioritySections.size
				showHomeLoadingProgress(completedLoadingSteps, totalLoadingSteps)
				recyclerView?.post {
						val firstRow = rowsAdapter.firstOrNull() as? ListRow
						val adapterFirstRow = firstRow?.adapter as? ItemRowAdapter
						if (adapterFirstRow != null && adapterFirstRow.size() > 0) {
							val firstItem = adapterFirstRow.get(0) as? BaseRowItem
							if (firstItem != null && isAdded) {
								initialPreviewSet = true
								backgroundService.setBackground(firstItem.baseItem)
								homePreviewViewModel.updateSelectedItem(firstItem)
							}
						}
					}
				}

			for (section in deferredSections) {
				if (!isAdded) break
				appendRow(withContext(Dispatchers.IO) { loadRowForSection(section, includeLiveTvRows, userViews) })
				completedLoadingSteps += 1
				showHomeLoadingProgress(completedLoadingSteps, totalLoadingSteps)
				delay(75)
			}

			if (includeLiveTvRows && isAdded) {
				val onNowRow = withContext(Dispatchers.IO) { helper.loadOnNow() }
				appendRow(onNowRow)
				completedLoadingSteps += 1
				showHomeLoadingProgress(completedLoadingSteps, totalLoadingSteps)
			}
		} catch (e: Exception) {
			Timber.e(e, "Error building home rows")
		} finally {
			hideHomeLoadingProgress()
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
			HomeSectionType.RANDOM_MOVIES -> helper.loadRandomMovies(userViews)
			HomeSectionType.RANDOM_SERIES -> helper.loadRandomSeries(userViews)
			HomeSectionType.UNWATCHED_RANDOM_MOVIES -> helper.loadUnwatchedRandomMovies(userViews)
			HomeSectionType.LONG_AGO_MOVIES -> helper.loadLongAgoMovies(userViews)
			HomeSectionType.GENRE_RANDOM_MOVIES -> helper.loadGenreRandomMovies(userViews)
			HomeSectionType.GENRE_RANDOM_TV -> helper.loadGenreRandomTV(userViews)
			HomeSectionType.GENRE_RANDOM_MIXED -> helper.loadGenreRandomMixed(userViews)
			HomeSectionType.MOOD_LIGHT -> helper.loadMoodLight(userViews)
			HomeSectionType.MOOD_ACTION -> helper.loadMoodAction(userViews)
			HomeSectionType.MOOD_SHORT -> helper.loadMoodShort(userViews)
			HomeSectionType.EXTERNAL_PROVIDERS -> helper.loadExternalProviders()
			HomeSectionType.INCOMPLETE_SERIES -> helper.loadIncompleteSeries(userViews)
			HomeSectionType.SEASONAL_EVENTS -> helper.loadSeasonalEvents(userViews)
			HomeSectionType.ONLINE_NEW_RELEASES -> helper.loadOnlineNewReleases()
			else -> null
		}
	}


	override fun onKey(v: View?, keyCode: Int, event: KeyEvent?): Boolean {
		if (isSelectLongPress(keyCode, event)) {
			showCurrentItemContextMenu(v ?: activity?.currentFocus)
			return true
		}

		// DPAD_UP is handled by BaseGridView.OnKeyInterceptListener so Leanback cannot
		// move focus to the top toolbar before the home row guard has a chance to run.
		if (keyCode == KeyEvent.KEYCODE_DPAD_UP) return false

		if (event?.action != KeyEvent.ACTION_UP) return false
		return keyProcessor.handleKey(keyCode, currentItem, activity)
	}

	private fun handleHomeRowsKeyIntercept(event: KeyEvent): Boolean {
		if (event.keyCode != KeyEvent.KEYCODE_DPAD_UP) return false

		if (event.action == KeyEvent.ACTION_UP && consumeToolbarUpKeyUp) {
			consumeToolbarUpKeyUp = false
			return true
		}

		if (event.action != KeyEvent.ACTION_DOWN) return false

		if (moveFocusToPreviousHomeRow()) {
			consumeToolbarUpKeyUp = true
			return true
		}

		if (!shouldGateToolbarFocusExit()) {
			consumeToolbarUpKeyUp = true
			verticalGridView?.requestFocus()
			return true
		}

		val now = SystemClock.uptimeMillis()
		val allowToolbarExit = now - lastToolbarUpRequestAt <= TOP_MENU_DOUBLE_UP_WINDOW_MS
		lastToolbarUpRequestAt = now

		if (!allowToolbarExit) {
			consumeToolbarUpKeyUp = true
			selectedPosition = 0
			verticalGridView?.requestFocus()
			return true
		}

		consumeToolbarUpKeyUp = false
		return false
	}

	private fun isSelectLongPress(keyCode: Int, event: KeyEvent?): Boolean {
		if (event?.action != KeyEvent.ACTION_DOWN) return false
		if (keyCode != KeyEvent.KEYCODE_DPAD_CENTER && keyCode != KeyEvent.KEYCODE_ENTER && keyCode != KeyEvent.KEYCODE_NUMPAD_ENTER) return false
		return event.isLongPress || event.repeatCount > 0
	}

	private fun showCurrentItemContextMenu(anchor: View?) {
		if (contextMenuShowing) return
		val item = currentItem ?: return
		val activity = activity ?: return
		val anchorView = anchor ?: activity.currentFocus ?: verticalGridView ?: return

		if (item is ExternalCatalogBaseRowItem) {
			ExternalCatalogLauncher.open(activity, item)
			return
		}

		val baseItem = item.baseItem ?: return
		val menu = PopupMenu(activity, anchorView, Gravity.END)
		var order = 0
		menu.menu.add(0, MENU_HOME_OPEN_DETAILS, order++, R.string.lbl_home_card_open_details)
		if (baseItem.type in PLAYABLE_CONTEXT_TYPES) {
			menu.menu.add(0, MENU_HOME_PLAY_NOW, order++, R.string.lbl_home_card_play_now)
		}
		baseItem.name?.takeIf { it.isNotBlank() }?.let {
			menu.menu.add(0, MENU_HOME_SEARCH_SERVER, order++, R.string.lbl_home_card_search_server)
		}
		menu.menu.add(0, MENU_HOME_MORE_ACTIONS, order, R.string.lbl_home_card_more_actions)

		menu.setOnMenuItemClickListener { menuItem ->
			when (menuItem.itemId) {
				MENU_HOME_OPEN_DETAILS -> {
					navigationRepository.navigate(Destinations.itemDetails(baseItem.id))
					true
				}
				MENU_HOME_PLAY_NOW -> keyProcessor.handleKey(KeyEvent.KEYCODE_MEDIA_PLAY, item, activity)
				MENU_HOME_SEARCH_SERVER -> {
					navigationRepository.navigate(Destinations.search(baseItem.name.orEmpty()))
					true
				}
				MENU_HOME_MORE_ACTIONS -> keyProcessor.handleKey(KeyEvent.KEYCODE_MENU, item, activity)
				else -> false
			}
		}
		menu.setOnDismissListener { contextMenuShowing = false }
		contextMenuShowing = true
		menu.show()
	}

	private fun moveFocusToPreviousHomeRow(): Boolean {
		if (useTouchHomeNavigation) return false
		val recyclerView = verticalGridView ?: return false
		val rowIndex = resolveFocusedHomeRowIndex()
		val rowCount = (adapter as? MutableObjectAdapter<Row>)?.size() ?: 0

		if (rowIndex > 0 && rowIndex < rowCount) {
			val targetRow = rowIndex - 1
			lastToolbarUpRequestAt = 0L
			selectedPosition = targetRow
			setSelectedPosition(targetRow, true)
			recyclerView.post {
				recyclerView.requestFocus()
			}
			return true
		}

		if (rowIndex != 0 && recyclerView.canScrollVertically(-1)) {
			lastToolbarUpRequestAt = 0L
			recyclerView.smoothScrollBy(0, -maxOf(160, recyclerView.height / 2))
			recyclerView.post { recyclerView.requestFocus() }
			return true
		}

		if (rowIndex == 0 && recyclerView.canScrollVertically(-1)) {
			lastToolbarUpRequestAt = 0L
			recyclerView.smoothScrollBy(0, -maxOf(160, recyclerView.height / 2))
			recyclerView.post { recyclerView.requestFocus() }
			return true
		}

		return false
	}

	private fun shouldGateToolbarFocusExit(): Boolean {
		if (useTouchHomeNavigation) return false
		return resolveFocusedHomeRowIndex() == 0 && verticalGridView?.canScrollVertically(-1) != true
	}

	private fun resolveFocusedHomeRowIndex(): Int {
		val recyclerView = verticalGridView ?: return selectedPosition
		val focused = activity?.currentFocus ?: recyclerView.findFocus()
		if (focused != null) {
			var parent: View? = focused
			while (parent != null && parent.parent != recyclerView) {
				parent = parent.parent as? View
			}
			if (parent != null) {
				val adapterPosition = recyclerView.getChildAdapterPosition(parent)
				if (adapterPosition != RecyclerView.NO_POSITION) return adapterPosition
			}
		}

		val leanbackSelected = selectedPosition
		if (leanbackSelected >= 0) return leanbackSelected

		currentRow?.let { row ->
			val index = (adapter as? MutableObjectAdapter<Row>)?.indexOf(row) ?: -1
			if (index >= 0) return index
		}

		return -1
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
			lifecycleScope.launch {
				if (userSettingPreferences.shouldUpdate) userSettingPreferences.update()
				if (userSettingPreferences.activeHomesections != loadedHomeSections) {
					buildHomeRows(forceUpdateSettings = false)
				} else {
					refreshCurrentItem()
					val libraryChanged = dataRefreshService.lastLibraryChange != lastObservedLibraryChange
					val refreshExpired = SystemClock.uptimeMillis() - lastSoftRefreshAt > SOFT_HOME_REFRESH_TTL_MS
					if (libraryChanged || refreshExpired) refreshRows(force = libraryChanged, delayed = true)
				}
			}
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
		lastSoftRefreshAt = SystemClock.uptimeMillis()
		lastObservedLibraryChange = dataRefreshService.lastLibraryChange
		lifecycleScope.launch(Dispatchers.Main) {
			if (delayed) delay(1500)

			val rows = (adapter as? MutableObjectAdapter<Row>)?.toList() ?: return@launch
			withContext(Dispatchers.IO) {
				rows.forEach { row ->
					val rowAdapter = (row as? ListRow)?.adapter as? ItemRowAdapter
					if (force) rowAdapter?.Retrieve()
					else rowAdapter?.ReRetrieveIfNeeded()
				}
			}
		}
	}

	private fun refreshCurrentItem() {
		val adapter = currentRow?.adapter as? ItemRowAdapter ?: return
		val item = currentItem ?: return

		Timber.i("Refresh item ${item.getFullName(requireContext())}")
		adapter.refreshItem(api, this, item)
	}

	override fun onDestroy() {
		selectedPreviewJob?.cancel()
		buildRowsJob?.cancel()
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
			if (item is GridButton) {
				when (item.id) {
					LiveTvOption.LIVE_TV_GUIDE_OPTION_ID -> navigationRepository.navigate(Destinations.liveTvGuide)
					LiveTvOption.LIVE_TV_SCHEDULE_OPTION_ID -> navigationRepository.navigate(Destinations.liveTvSchedule)
					LiveTvOption.LIVE_TV_RECORDINGS_OPTION_ID -> navigationRepository.navigate(Destinations.liveTvRecordings)
					LiveTvOption.LIVE_TV_SERIES_OPTION_ID -> navigationRepository.navigate(Destinations.liveTvSeriesRecordings)
				}
			}

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
