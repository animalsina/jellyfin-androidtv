package org.jellyfin.androidtv.ui.home

import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import androidx.leanback.app.RowsSupportFragment
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.OnItemViewSelectedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import kotlinx.coroutines.Dispatchers
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
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.liveTvApi
import org.jellyfin.sdk.api.sockets.subscribe
import org.jellyfin.sdk.model.api.LibraryChangedMessage
import org.jellyfin.sdk.model.api.UserDataChangedMessage
import org.koin.android.ext.android.inject
import timber.log.Timber
import kotlin.time.Duration.Companion.seconds

@Suppress("UNCHECKED_CAST")
class HomeRowsFragment : RowsSupportFragment(), AudioEventListener, View.OnKeyListener {
	private val api by inject<ApiClient>()
	private val backgroundService by inject<BackgroundService>()
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

	// Data
	private var currentItem: BaseRowItem? = null
	private var currentRow: ListRow? = null
	private var justLoaded = true

	// Special rows
	private val notificationsRow by lazy { NotificationsHomeFragmentRow(lifecycleScope, notificationsRepository) }
	private val nowPlaying by lazy { HomeFragmentNowPlayingRow(mediaManager) }
	private val liveTVRow by lazy { HomeFragmentLiveTVRow(requireActivity(), userRepository, navigationRepository) }

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
			var includeLiveTvRows = false

			if (homeSections.contains(HomeSectionType.LIVE_TV) && currentUser.policy?.enableLiveTvAccess == true) {
				val recommendedPrograms by api.liveTvApi.getRecommendedPrograms(
					enableTotalRecordCount = false,
					imageTypeLimit = 1,
					isAiring = true,
					limit = 1,
				)
				includeLiveTvRows = recommendedPrograms.items.isNotEmpty()
			}

			val rowsAdapter = adapter as MutableObjectAdapter<Row>
			val cardPresenter = CardPresenter(true, org.jellyfin.androidtv.constant.ImageType.POSTER, 120)
			val ctx = requireContext()
			rowsAdapter.clear()

			val recyclerView = view as? androidx.recyclerview.widget.RecyclerView

			val readyRows: List<HomeFragmentRow> = coroutineScope {
				homeSections.map { section ->
					async(Dispatchers.IO) { loadRowForSection(section, includeLiveTvRows) }
				}.mapNotNull { it.await() }
			}

			withContext(Dispatchers.Main) {
				recyclerView?.suppressLayout(true)

				for (row in readyRows) {
					row.addToRowsAdapter(ctx, cardPresenter, rowsAdapter)
				}

				recyclerView?.suppressLayout(false)

				recyclerView?.post {
					val firstRow = (rowsAdapter.firstOrNull() as? ListRow)
					val adapterFirstRow = firstRow?.adapter as? ItemRowAdapter
					val firstItem = adapterFirstRow?.get(0) as? BaseRowItem
					if (firstItem != null) {
						backgroundService.setBackground(firstItem.baseItem)
						homePreviewViewModel.updateSelectedItem(firstItem)
					}
				}
			}

			if (includeLiveTvRows) {
				val onNowRow = withContext(Dispatchers.IO) { helper.loadOnNow() }
				withContext(Dispatchers.Main) {
					addRowSequentially(ctx, cardPresenter, rowsAdapter, onNowRow)
				}
			}
		}
	}

	private suspend fun loadRowForSection(section: HomeSectionType, includeLiveTvRows: Boolean): HomeFragmentRow? {
		return when (section) {
			HomeSectionType.LATEST_MEDIA -> helper.loadRecentlyAdded(userViewsRepository.views.first())
			HomeSectionType.LIBRARY_TILES_SMALL -> HomeFragmentViewsRow(small = false)
			HomeSectionType.LIBRARY_BUTTONS -> HomeFragmentViewsRow(small = true)
			HomeSectionType.RESUME -> helper.loadResumeVideo()
			HomeSectionType.RESUME_AUDIO -> helper.loadResumeAudio()
			HomeSectionType.ACTIVE_RECORDINGS -> helper.loadLatestLiveTvRecordings()
			HomeSectionType.NEXT_UP -> helper.loadNextUp()
			HomeSectionType.LIVE_TV -> if (includeLiveTvRows) liveTVRow else null
			HomeSectionType.RECOMMENDED_FOR_YOU -> helper.loadRecommendedForYou(userViewsRepository.views.first())
			HomeSectionType.TRENDING_THIS_WEEK -> helper.loadTrendingThisWeek(userViewsRepository.views.first())
			HomeSectionType.RECENTLY_RELEASED -> helper.loadRecentlyReleased(userViewsRepository.views.first())
			HomeSectionType.POPULAR_MOVIES -> helper.loadPopularMovies(userViewsRepository.views.first())
			HomeSectionType.POPULAR_TV -> helper.loadPopularTV(userViewsRepository.views.first())
			HomeSectionType.SIMILAR_TO_WATCHED -> helper.loadSimilarToWatched(userViewsRepository.views.first())
			HomeSectionType.GENRE_RANDOM_MOVIES -> helper.loadGenreRandomMovies(userViewsRepository.views.first())
			HomeSectionType.GENRE_RANDOM_TV -> helper.loadGenreRandomTV(userViewsRepository.views.first())
			HomeSectionType.GENRE_RANDOM_MIXED -> helper.loadGenreRandomMixed(userViewsRepository.views.first())
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
			itemLauncher.launch(item, (row as ListRow).adapter as ItemRowAdapter, requireContext())
		}
	}

	private inner class ItemViewSelectedListener : OnItemViewSelectedListener {
		override fun onItemSelected(
			itemViewHolder: Presenter.ViewHolder?,
			item: Any?,
			rowViewHolder: RowPresenter.ViewHolder?,
			row: Row?,
		) {
			if (item !is BaseRowItem) {
				// Check if we have a row with items and auto-select the first item if available
				val listRow = row as? ListRow
				val itemRowAdapter = listRow?.adapter as? ItemRowAdapter

				if (itemRowAdapter != null && itemRowAdapter.size() > 0) {
					// Auto-select first item in the row to maintain preview
					val firstItem = itemRowAdapter[0] as? BaseRowItem
					if (firstItem != null) {
						currentItem = firstItem
						currentRow = listRow

						backgroundService.setBackground(firstItem.baseItem)
						homePreviewViewModel.updateSelectedItem(firstItem)
						return
					}
				}

				// Fallback: clear everything if no items available
				currentItem = null
				backgroundService.clearBackgrounds()
				homePreviewViewModel.updateSelectedItem(null)
			} else {
				currentItem = item
				currentRow = row as ListRow

				val itemRowAdapter = row.adapter as? ItemRowAdapter
				itemRowAdapter?.loadMoreItemsIfNeeded(itemRowAdapter.indexOf(item))

				backgroundService.setBackground(item.baseItem)
				// Update preview
				homePreviewViewModel.updateSelectedItem(item)
			}
		}
	}
}
