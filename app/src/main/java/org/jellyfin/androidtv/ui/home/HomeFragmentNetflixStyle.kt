package org.jellyfin.androidtv.ui.home

import android.content.Intent
import android.os.Bundle
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.doOnAttach
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Call
import okhttp3.Request
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.repository.ServerRepository
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.data.repository.NotificationsRepository
import org.jellyfin.androidtv.data.repository.UserViewsRepository
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.ui.AsyncImageView
import org.jellyfin.androidtv.ui.itemhandling.BaseRowItem
import org.jellyfin.androidtv.ui.itemhandling.ExternalCatalogBaseRowItem
import org.jellyfin.androidtv.ui.itemhandling.ItemLauncher
import org.jellyfin.androidtv.ui.navigation.ActivityDestinations
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.playback.MediaManager
import org.jellyfin.androidtv.ui.settings.compat.SettingsViewModel
import org.jellyfin.androidtv.ui.startup.StartupActivity
import org.jellyfin.androidtv.util.ImageHelper
import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.androidtv.util.apiclient.itemBackdropImages
import org.jellyfin.androidtv.util.apiclient.itemImages
import org.jellyfin.androidtv.util.apiclient.parentBackdropImages
import org.jellyfin.androidtv.util.apiclient.primaryImage
import org.jellyfin.androidtv.util.apiclient.seriesThumbImage
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.api.client.ApiClient
import org.koin.android.ext.android.get
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import timber.log.Timber
import java.net.URLEncoder
import java.util.concurrent.TimeUnit


class HomeFragmentNetflixStyle : Fragment() {
	private val api by inject<ApiClient>()
	private val sessionRepository by inject<SessionRepository>()
	private val userRepository by inject<UserRepository>()
	private val serverRepository by inject<ServerRepository>()
	private val notificationRepository by inject<NotificationsRepository>()
	private val navigationRepository by inject<NavigationRepository>()
	private val mediaManager by inject<MediaManager>()
	private val imageHelper by inject<ImageHelper>()
	private val userViewsRepository by inject<UserViewsRepository>()
	private val itemLauncher by inject<ItemLauncher>()
	private val homePreviewViewModel: HomePreviewViewModel by activityViewModel()
	private val settingsViewModel: SettingsViewModel by activityViewModel()

	// View references
	private lateinit var previewBackground: AsyncImageView
	private lateinit var previewGradient: View
	private lateinit var previewTitle: TextView
	private lateinit var previewDescription: TextView
	private lateinit var previewYear: TextView
	private lateinit var previewDuration: TextView
	private lateinit var previewAgeRating: TextView
	private lateinit var previewContentType: TextView
	private lateinit var previewPoster: AsyncImageView
	private lateinit var contentView: FragmentContainerView
	private lateinit var previewSubtitle: TextView

	// Trailer
	private lateinit var trailerContainer: FrameLayout
	private lateinit var trailerWebView: WebView
	private lateinit var trailerGradientOverlay: View

	private val trailerCache = object : LruCache<String, String>(MAX_TRAILER_CACHE_ITEMS) {}
	private val trailerMissCache = object : LruCache<String, Boolean>(MAX_TRAILER_MISS_CACHE_ITEMS) {}
	private val trailerHttpClient = OkHttpClient.Builder()
		.callTimeout(TRAILER_NETWORK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
		.connectTimeout(TRAILER_NETWORK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
		.readTimeout(TRAILER_NETWORK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
		.build()
	private var trailerJob: Job? = null
	private var trailerHideJob: Job? = null
	private var previewBackdropJob: Job? = null
	private var trailerSearchCall: Call? = null
	private var trailerCheckCall: Call? = null
	private var currentPreviewItemId: String? = null
	private var currentBackdropUrl: String? = null
	private var currentTrailerVideoId: String? = null
	private var trailerFailures = 0
	private var trailersDisabledUntil = 0L

	companion object {
		private val TRAILER_TYPES =
			setOf(BaseItemKind.SERIES, BaseItemKind.EPISODE, BaseItemKind.MOVIE, BaseItemKind.SEASON, BaseItemKind.VIDEO)
		private const val TRAILER_START_DELAY_MS = 1_900L
		private const val PREVIEW_BACKDROP_LOAD_DELAY_MS = 220L
		private const val TRAILER_MAX_DURATION_MS = 45_000L
		private const val TRAILER_FADE_OUT_MS = 250L
		private const val TRAILER_NETWORK_TIMEOUT_MS = 6_500L
		private const val TRAILER_ERROR_BACKOFF_MS = 60_000L
		private const val TRAILER_MAX_CONSECUTIVE_FAILURES = 5
		private const val MAX_TRAILER_CACHE_ITEMS = 80
		private const val MAX_TRAILER_MISS_CACHE_ITEMS = 120
		private val YOUTUBE_ID_REGEX = """(?:v=|/embed/|youtu\.be/|/shorts/)([a-zA-Z0-9_-]{11})""".toRegex()
		private val YOUTUBE_SEARCH_ID_PATTERNS = listOf(
			""""videoId":"([a-zA-Z0-9_-]{11})"""".toRegex(),
			"""/watch\?v=([a-zA-Z0-9_-]{11})""".toRegex(),
			"""watchEndpoint":\{"videoId":"([a-zA-Z0-9_-]{11})""".toRegex(),
		)
	}

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		val view = inflater.inflate(R.layout.fragment_home_netflix_style, container, false)

		// Initialize view references
		previewBackground = view.findViewById(R.id.preview_background)
		previewGradient = view.findViewById(R.id.preview_gradient)
		previewTitle = view.findViewById(R.id.preview_title)
		previewDescription = view.findViewById(R.id.preview_description)
		previewYear = view.findViewById(R.id.preview_year)
		previewDuration = view.findViewById(R.id.preview_duration)
		previewAgeRating = view.findViewById(R.id.preview_age_rating)
		previewContentType = view.findViewById(R.id.preview_content_type)
		previewPoster = view.findViewById(R.id.preview_poster)
		contentView = view.findViewById(R.id.content_view)
		previewSubtitle = view.findViewById(R.id.preview_subtitle)

		// Trailer container sopra il previewBackground
		trailerContainer = view.findViewById<FrameLayout>(R.id.trailer_container).apply {
			visibility = View.GONE
		}
		trailerGradientOverlay = view.findViewById(R.id.trailer_gradient_overlay)
		trailerGradientOverlay.visibility = View.GONE

		trailerWebView = view.findViewById<WebView>(R.id.trailer_webview).apply {
			webViewClient = WebViewClient()
			settings.loadWithOverviewMode = true
			settings.useWideViewPort = true
			settings.mediaPlaybackRequiresUserGesture = false
			settings.domStorageEnabled = true
			settings.javaScriptEnabled = true
			settings.loadsImagesAutomatically = true
			settings.setSupportMultipleWindows(false)
			settings.userAgentString = "Mozilla/5.0 (SMART-TV; Linux; Tizen 6.0) AppleWebKit/537.36 SuperJellyTV"
			setBackgroundColor(android.graphics.Color.TRANSPARENT)
		}

		// Setup glassmorphic toolbar
		setupGlassmorphicToolbar(view)

		return view
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		sessionRepository.currentSession
			.flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
			.map { session ->
				if (session == null) null
				else serverRepository.getServer(session.serverId)
			}
			.onEach { server ->
				notificationRepository.updateServerNotifications(server)
			}
			.launchIn(viewLifecycleOwner.lifecycleScope)

		configureHomeContainerFocusForDevice()

		// Set up communication with HomeRowsFragment
		setupRowsFragmentListener()
	}

	private fun configureHomeContainerFocusForDevice() {
		val useTouchHomeNavigation = org.jellyfin.androidtv.util.TouchNavigationHelper.shouldUseTouchHomeNavigation(requireContext())
		if (useTouchHomeNavigation) {
			// On phones/tablets the XML-level requestFocus/focusable container makes Leanback keep
			// restoring its selected row while the user performs normal touch scrolling. The rows
			// fragment remains fully touchable/clickable, but it must not own TV focus on mobile.
			contentView.isFocusable = false
			contentView.isFocusableInTouchMode = false
			contentView.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
			contentView.clearFocus()
			view?.clearFocus()
		} else {
			contentView.isFocusable = true
			contentView.isFocusableInTouchMode = true
			contentView.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
			contentView.post { contentView.requestFocus() }
		}
	}

	private fun setupRowsFragmentListener() {
		// Observe selected item changes from HomeRowsFragment
		homePreviewViewModel.selectedItem
			.flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
			.onEach { item ->
				updatePreviewSection(item)
			}
			.launchIn(viewLifecycleOwner.lifecycleScope)
	}

	fun updatePreviewSection(item: BaseRowItem?) {
		resetTrailerTimer()
		cancelTrailerHide()
		previewBackdropJob?.cancel()

		if (item == null || item.baseItem == null) {
			resetPreview()
			return
		}

		val baseItem = item.baseItem
		val nextItemId = baseItem.id.toString()
		val changedItem = currentPreviewItemId != nextItemId
		currentPreviewItemId = nextItemId

		if (changedItem) {
			// Non svuotiamo subito la WebView mentre si scorre: è la causa principale dei micro-blocchi.
			// La nascondiamo e lasciamo che il nuovo trailer parta solo se la selezione resta stabile.
			hideTrailerForSelectionChange()
		}

		val backdropImage = when {
			baseItem.itemBackdropImages.isNotEmpty() -> baseItem.itemBackdropImages.firstOrNull()
			baseItem.itemImages[ImageType.BACKDROP] != null -> baseItem.itemImages[ImageType.BACKDROP]
			baseItem.parentBackdropImages.isNotEmpty() -> baseItem.parentBackdropImages.firstOrNull()
			baseItem.type == BaseItemKind.EPISODE && baseItem.itemImages[ImageType.PRIMARY] != null ->
				baseItem.itemImages[ImageType.PRIMARY]
			baseItem.type == BaseItemKind.EPISODE && baseItem.seriesThumbImage != null ->
				baseItem.seriesThumbImage
			else -> null
		}

		val externalBackdropUrl = item.externalBackdropUrl
		if (!externalBackdropUrl.isNullOrBlank()) {
			loadPreviewBackdrop(externalBackdropUrl, blurHash = null, itemId = nextItemId)
		} else if (backdropImage != null) {
			val backdropWidth = resources.getDimensionPixelSize(R.dimen.home_preview_width)
			val backdropHeight = resources.getDimensionPixelSize(R.dimen.home_preview_width) * 3 / 4
			val backdropUrl = backdropImage.getUrl(api, fillWidth = backdropWidth, fillHeight = backdropHeight)
			loadPreviewBackdrop(backdropUrl, backdropImage.blurHash, nextItemId)
		} else {
			previewBackdropJob?.cancel()
			currentBackdropUrl = null
			previewBackground.visibility = View.GONE
			previewGradient.visibility = View.GONE
			previewBackground.setImageDrawable(null)
		}

		previewTitle.text = baseItem.name

		if (baseItem.type == BaseItemKind.EPISODE) {
			previewSubtitle.text = baseItem.seriesName ?: ""
			previewSubtitle.visibility = View.VISIBLE
		} else {
			previewSubtitle.text = ""
			previewSubtitle.visibility = View.GONE
		}

		previewDescription.text = baseItem.overview ?: ""
		updateMetadata(baseItem)
		previewPoster.visibility = View.GONE

		val externalTrailerId = (item as? ExternalCatalogBaseRowItem)?.catalogItem?.trailerUrl?.let(::extractYouTubeVideoId)
		if (isTrailerEnabled() && TRAILER_TYPES.contains(baseItem.type) && !isTrailerBackoffActive()) {
			when {
				!externalTrailerId.isNullOrBlank() -> {
					trailerCache.put(nextItemId, externalTrailerId)
					playYouTubeTrailerWithDelay(item, nextItemId)
				}
				item !is ExternalCatalogBaseRowItem -> playYouTubeTrailerWithDelay(item, nextItemId)
			}
		}
	}

	private fun loadPreviewBackdrop(backdropUrl: String, blurHash: String?, itemId: String) {
		if (currentBackdropUrl == backdropUrl && previewBackground.drawable != null) {
			previewBackground.visibility = View.VISIBLE
			previewGradient.visibility = View.VISIBLE
			return
		}

		currentBackdropUrl = backdropUrl
		previewBackdropJob?.cancel()
		previewBackdropJob = viewLifecycleOwner.lifecycleScope.launch {
			delay(PREVIEW_BACKDROP_LOAD_DELAY_MS)
			if (!isAdded || itemId != currentPreviewItemId) return@launch

			previewBackground.doOnAttach {
				if (itemId != currentPreviewItemId) return@doOnAttach
				previewBackground.visibility = View.VISIBLE
				previewGradient.visibility = View.VISIBLE
				previewBackground.load(backdropUrl, blurHash = blurHash, blurHashResolution = 12)
			}
		}
	}

	private fun updateMetadata(item: BaseItemDto) {
		val context = context ?: return
		// Content type
		when (item.type) {
			BaseItemKind.MOVIE -> {
				previewContentType.text = context.getString(R.string.lbl_movie)
				previewContentType.visibility = View.VISIBLE
			}

			BaseItemKind.SERIES -> {
				previewContentType.text = context.getString(R.string.lbl_series)
				previewContentType.visibility = View.VISIBLE
			}

			BaseItemKind.EPISODE -> {
				previewContentType.text = context.getString(R.string.lbl_episode)
				previewContentType.visibility = View.VISIBLE
			}

			else -> previewContentType.visibility = View.GONE
		}

		// Year
		item.productionYear?.let { year ->
			previewYear.text = year.toString()
			previewYear.visibility = View.VISIBLE
		} ?: run { previewYear.visibility = View.GONE }

		// Duration
		item.runTimeTicks?.let { ticks ->
			val minutes = ticks / 600_000_000L
			previewDuration.text = if (minutes >= 60) {
				"${minutes / 60}h ${minutes % 60}m"
			} else {
				"${minutes}m"
			}
			previewDuration.visibility = View.VISIBLE
		} ?: run { previewDuration.visibility = View.GONE }

		// Age rating
		item.officialRating?.let { rating ->
			previewAgeRating.text = rating
			previewAgeRating.visibility = View.VISIBLE
		} ?: run { previewAgeRating.visibility = View.GONE }
	}

	private fun switchUser() {
		mediaManager.clearAudioQueue()
		sessionRepository.destroyCurrentSession()

		val selectUserIntent = Intent(activity, StartupActivity::class.java)
		selectUserIntent.putExtra(StartupActivity.EXTRA_HIDE_SPLASH, true)
		selectUserIntent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)

		activity?.startActivity(selectUserIntent)
		activity?.finishAfterTransition()
	}

	private fun navigateToLibraryType(collectionType: CollectionType) {
		lifecycleScope.launch {
			try {
				val userViews = userViewsRepository.views.first()
				val libraryView = userViews.find { it.collectionType == collectionType }

				if (libraryView != null) {
					val destination = itemLauncher.getUserViewDestination(libraryView)
					navigationRepository.navigate(destination)
				} else {
					// Fallback: navigate to generic library browser if specific type not found
					val firstView = userViews.firstOrNull()
					if (firstView != null) {
						navigationRepository.navigate(Destinations.libraryBrowser(firstView))
					}
				}
			} catch (e: Exception) {
				// Handle error gracefully - could show a toast or log the error
				Timber.e(e, "Failed to navigate to library type: $collectionType")
			}
		}
	}

	private fun setupGlassmorphicToolbar(view: View) {
		// Search button
		view.findViewById<View>(R.id.toolbar_search)?.setOnClickListener {
			navigationRepository.navigate(Destinations.search())
		}

		// Set up dynamic navigation tabs
		setupDynamicNavigationTabs(view)

		// User avatar container
		val userAvatarContainer = view.findViewById<View>(R.id.toolbar_user_avatar_container)
		userAvatarContainer?.setOnClickListener {
			switchUser()
		}

		// Get the actual user avatar for loading image
		val userAvatar = view.findViewById<AsyncImageView>(R.id.toolbar_user_avatar)

		// Load user avatar image
		lifecycleScope.launch {
			userRepository.currentUser.filterNotNull().collect { user ->
				user.let {
					val imageUrl = it.primaryImage?.getUrl(api)
					userAvatar?.load(imageUrl)
				}
			}
		}
	}

	private fun setupDynamicNavigationTabs(view: View) {
		lifecycleScope.launch {
			try {
				val userViews = userViewsRepository.views.first()
				if (!isAdded) return@launch
				val navContainer = view.findViewById<ViewGroup>(R.id.nav_pills_container) ?: return@launch

				// Clear existing dynamic tabs (keep only static ones)
				navContainer.removeAllViews()

				// Create tabs based on available libraries
				var previousButtonId: Int? = null

				for (userView in userViews) {
					val tabButton = createNavTab(userView)
					if (tabButton != null) {
						navContainer.addView(tabButton)

						// Set up focus navigation
						if (previousButtonId != null) {
							tabButton.nextFocusLeftId = previousButtonId
							view.findViewById<View>(previousButtonId)?.nextFocusRightId = tabButton.id
						} else {
							// Connect search button to first library tab
							view.findViewById<View>(R.id.toolbar_search)?.nextFocusRightId = tabButton.id
						}

						previousButtonId = tabButton.id
					}
				}

				// Add Jellyfin tab (always present)
				val jellyfinTab = createJellyfinTab()
				if (jellyfinTab != null) {
					navContainer.addView(jellyfinTab)

					// Set up focus navigation for Jellyfin tab
					if (previousButtonId != null) {
						jellyfinTab.nextFocusLeftId = previousButtonId
						view.findViewById<View>(previousButtonId)?.nextFocusRightId = jellyfinTab.id
					} else {
						// If no libraries, connect search directly to Jellyfin
						view.findViewById<View>(R.id.toolbar_search)?.nextFocusRightId = jellyfinTab.id
					}

					// Connect last tab to user avatar
					jellyfinTab.nextFocusRightId = R.id.toolbar_user_avatar
					view.findViewById<View>(R.id.toolbar_user_avatar)?.nextFocusLeftId = jellyfinTab.id
				}

			} catch (e: Exception) {
				Timber.e(e, "Failed to set up dynamic navigation tabs")
				// Fallback to static tabs if dynamic setup fails
				if (isAdded) setupStaticNavigationTabs(view)
			}
		}
	}

	private fun createNavTab(userView: BaseItemDto): TextView? {
		val context = context ?: return null
		val displayName = getDisplayNameForCollectionType(userView.collectionType, userView.name)
		if (displayName == null) return null

		return TextView(context).apply {
			id = View.generateViewId()
			layoutParams = ViewGroup.MarginLayoutParams(
				ViewGroup.LayoutParams.WRAP_CONTENT,
				resources.getDimensionPixelSize(R.dimen.toolbar_nav_button_height)
			).apply {
				leftMargin = resources.getDimensionPixelSize(R.dimen.toolbar_nav_button_margin)
			}
			text = displayName
			textSize = 15f
			setTextColor(ContextCompat.getColorStateList(context, R.color.nav_text_color))
			typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
			gravity = android.view.Gravity.CENTER
			setPadding(
				resources.getDimensionPixelSize(R.dimen.toolbar_nav_button_padding_horizontal),
				0,
				resources.getDimensionPixelSize(R.dimen.toolbar_nav_button_padding_horizontal),
				0
			)
			background = ResourcesCompat.getDrawable(resources, R.drawable.nav_pill_animated_background, null)
			stateListAnimator = android.animation.AnimatorInflater.loadStateListAnimator(
				requireContext(),
				R.animator.nav_button_state_animator
			)
			isFocusable = true
			isClickable = true

			setOnClickListener {
				navigateToSpecificLibrary(userView)
			}
		}
	}

	private fun createJellyfinTab(): TextView? {
		val context = context ?: return null
		return TextView(context).apply {
			id = View.generateViewId()
			layoutParams = ViewGroup.MarginLayoutParams(
				ViewGroup.LayoutParams.WRAP_CONTENT,
				resources.getDimensionPixelSize(R.dimen.toolbar_nav_button_height)
			).apply {
				leftMargin = resources.getDimensionPixelSize(R.dimen.toolbar_nav_button_margin)
			}
			text = context.getString(R.string.lbl_jellyfin)
			textSize = 15f
			setTextColor(ContextCompat.getColorStateList(context, R.color.nav_text_color))
			typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
			gravity = android.view.Gravity.CENTER
			setPadding(
				resources.getDimensionPixelSize(R.dimen.toolbar_nav_button_padding_horizontal),
				0,
				resources.getDimensionPixelSize(R.dimen.toolbar_nav_button_padding_horizontal),
				0
			)
			background = ResourcesCompat.getDrawable(resources, R.drawable.nav_pill_animated_background, null)
			stateListAnimator = android.animation.AnimatorInflater.loadStateListAnimator(
				requireContext(),
				R.animator.nav_button_state_animator
			)
			isFocusable = true
			isClickable = true

			setOnClickListener {
				settingsViewModel.show()
			}
		}
	}

	private fun getDisplayNameForCollectionType(collectionType: CollectionType?, fallbackName: String?): String? {
		val ctx = context ?: return fallbackName
		return when (collectionType) {
			CollectionType.MOVIES -> ctx.getString(R.string.lbl_movies)
			CollectionType.TVSHOWS -> ctx.getString(R.string.lbl_tv_show)
			CollectionType.MUSIC -> ctx.getString(R.string.lbl_music)
			CollectionType.PHOTOS -> ctx.getString(R.string.lbl_photo)
			CollectionType.PLAYLISTS -> ctx.getString(R.string.lbl_playlists)
			CollectionType.LIVETV -> ctx.getString(R.string.lbl_live)
			CollectionType.BOXSETS -> ctx.getString(R.string.lbl_collections)
			else -> fallbackName // Use the library's custom name for unknown types
		}
	}

	private fun navigateToSpecificLibrary(userView: BaseItemDto) {
		lifecycleScope.launch {
			try {
				val destination = itemLauncher.getUserViewDestination(userView)
				navigationRepository.navigate(destination)
			} catch (e: Exception) {
				Timber.e(e, "Failed to navigate to library: ${userView.name}")
				// Fallback to generic library browser
				navigationRepository.navigate(Destinations.libraryBrowser(userView))
			}
		}
	}

	private fun setupStaticNavigationTabs(view: View) {
		// Fallback implementation: create basic tabs when dynamic setup fails
		val context = context ?: return
		val navContainer = view.findViewById<ViewGroup>(R.id.nav_pills_container)

		// Create static tabs as fallback
		val moviesTab = createStaticTab(context.getString(R.string.lbl_movies)) { navigateToLibraryType(CollectionType.MOVIES) }
		val showsTab = createStaticTab(context.getString(R.string.lbl_tv_show)) { navigateToLibraryType(CollectionType.TVSHOWS) }
		val playlistsTab = createStaticTab(context.getString(R.string.lbl_playlists)) { navigateToLibraryType(CollectionType.PLAYLISTS) }
		val jellyfinTab =
			createStaticTab(context.getString(R.string.lbl_jellyfin)) { settingsViewModel.show() }

		if (moviesTab != null && showsTab != null && playlistsTab != null && jellyfinTab != null) {
			navContainer?.addView(moviesTab)
			navContainer?.addView(showsTab)
			navContainer?.addView(playlistsTab)
			navContainer?.addView(jellyfinTab)

			// Set up basic focus navigation
			view.findViewById<View>(R.id.toolbar_search)?.nextFocusRightId = moviesTab.id
			moviesTab.nextFocusLeftId = R.id.toolbar_search
			moviesTab.nextFocusRightId = showsTab.id
			showsTab.nextFocusLeftId = moviesTab.id
			showsTab.nextFocusRightId = playlistsTab.id
			playlistsTab.nextFocusLeftId = showsTab.id
			playlistsTab.nextFocusRightId = jellyfinTab.id
			jellyfinTab.nextFocusLeftId = playlistsTab.id
			jellyfinTab.nextFocusRightId = R.id.toolbar_user_avatar
			view.findViewById<View>(R.id.toolbar_user_avatar)?.nextFocusLeftId = jellyfinTab.id
		}
	}

	private fun createStaticTab(text: String, onClickListener: () -> Unit): TextView? {
		val context = context ?: return null
		return TextView(context).apply {
			id = View.generateViewId()
			layoutParams = ViewGroup.MarginLayoutParams(
				ViewGroup.LayoutParams.WRAP_CONTENT,
				resources.getDimensionPixelSize(R.dimen.toolbar_nav_button_height)
			).apply {
				leftMargin = resources.getDimensionPixelSize(R.dimen.toolbar_nav_button_margin)
			}
			this.text = text
			textSize = 15f
			setTextColor(ContextCompat.getColorStateList(context, R.color.nav_text_color))
			typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
			gravity = android.view.Gravity.CENTER
			setPadding(
				resources.getDimensionPixelSize(R.dimen.toolbar_nav_button_padding_horizontal),
				0,
				resources.getDimensionPixelSize(R.dimen.toolbar_nav_button_padding_horizontal),
				0
			)
			background = ResourcesCompat.getDrawable(resources, R.drawable.nav_pill_animated_background, null)
			stateListAnimator = android.animation.AnimatorInflater.loadStateListAnimator(
				context,
				R.animator.nav_button_state_animator
			)
			isFocusable = true
			isClickable = true

			setOnClickListener { onClickListener() }
		}
	}

	private fun playYouTubeTrailer(item: BaseRowItem, expectedItemId: String) {
		val baseItem = item.baseItem ?: return
		val itemId = baseItem.id.toString()
		if (itemId != expectedItemId || itemId != currentPreviewItemId) return
		if (trailerMissCache.get(itemId) == true) return

		val cachedVideoId = trailerCache.get(itemId)
		if (!cachedVideoId.isNullOrBlank()) {
			startTrailer(cachedVideoId, itemId)
			return
		}

		val metadataTrailerId = baseItem.remoteTrailers.orEmpty()
			.asSequence()
			.mapNotNull { it.url }
			.mapNotNull(::extractYouTubeVideoId)
			.firstOrNull()

		if (!metadataTrailerId.isNullOrBlank()) {
			trailerCache.put(itemId, metadataTrailerId)
			startTrailer(metadataTrailerId, itemId)
			return
		}

		val trailerName = buildTrailerSearchQuery(baseItem)
		Timber.d("Fetching trailer for $trailerName")

		fetchTrailerFromYouTube(itemId, trailerName) { videoId ->
			if (itemId != currentPreviewItemId) return@fetchTrailerFromYouTube
			if (videoId.isNotEmpty()) {
				trailerCache.put(itemId, videoId)
				startTrailer(videoId, itemId)
			} else {
				trailerMissCache.put(itemId, true)
				Timber.w("No trailer found for $trailerName")
			}
		}
	}

	private fun buildTrailerSearchQuery(baseItem: BaseItemDto): String {
		val title = if (baseItem.type == BaseItemKind.EPISODE) baseItem.seriesName ?: baseItem.name else baseItem.name
		val type = if (baseItem.type == BaseItemKind.SERIES || baseItem.type == BaseItemKind.EPISODE) "Series" else "Movie"
		val year = baseItem.productionYear?.let { " ($it)" }.orEmpty()
		return "$title [$type]$year official trailer"
	}

	private fun extractYouTubeVideoId(url: String): String? = YOUTUBE_ID_REGEX.find(url)?.groups?.get(1)?.value

	private fun startTrailer(videoId: String, itemId: String) {
		if (itemId != currentPreviewItemId) return
		if (currentTrailerVideoId == videoId && trailerContainer.isVisible) return

		currentTrailerVideoId = videoId
		val width = previewBackground.width.takeIf { it > 0 } ?: resources.getDimensionPixelSize(R.dimen.home_preview_width)
		val height = (width * 9) / 16
		trailerContainer.layoutParams.width = width
		trailerContainer.layoutParams.height = height
		trailerContainer.requestLayout()

		trailerContainer.visibility = View.VISIBLE
		trailerContainer.alpha = 0f
		trailerGradientOverlay.visibility = View.VISIBLE

		val embedUrl = "https://www.youtube.com/embed/$videoId?autoplay=1&mute=1&controls=0&rel=0&modestbranding=1&playsinline=1&enablejsapi=1"

		trailerWebView.webViewClient = object : WebViewClient() {
			override fun onPageFinished(view: WebView?, url: String?) {
				super.onPageFinished(view, url)
				if (!isAdded || getView() == null || !isVisible || itemId != currentPreviewItemId) return

				viewLifecycleOwner.lifecycleScope.launch {
					delay(900)
					if (isAdded && itemId == currentPreviewItemId) {
						trailerFailures = 0
						trailerContainer.animate()
							.alpha(1f)
							.setDuration(250)
							.start()
					}
				}
			}

			override fun onReceivedError(
				view: WebView?,
				request: android.webkit.WebResourceRequest?,
				error: android.webkit.WebResourceError?
			) {
				super.onReceivedError(view, request, error)
				if (request?.isForMainFrame == false) return
				Timber.w("Errore caricamento trailer: ${error?.description}")
				registerTrailerFailure()
				resetTrailer(clearWebView = true)
			}

			override fun onReceivedHttpError(
				view: WebView?,
				request: android.webkit.WebResourceRequest?,
				errorResponse: android.webkit.WebResourceResponse?
			) {
				super.onReceivedHttpError(view, request, errorResponse)
				if (request?.isForMainFrame == false) return
				Timber.w("HTTP error loading trailer: ${errorResponse?.statusCode}")
				registerTrailerFailure()
				resetTrailer(clearWebView = true)
			}
		}

		val html = """
	        <html>
	        <head>
	            <meta name="viewport" content="width=device-width, initial-scale=1.0">
	            <style>
	                body, html { margin: 0; padding: 0; height: 100%; overflow: hidden; background: black; }
	                iframe { position: absolute; top: 0; left: 0; width: 100%; height: 100%; }
	            </style>
	        </head>
	        <body>
	            <iframe src="$embedUrl" frameborder="0" allow="autoplay; encrypted-media; fullscreen"></iframe>
	        </body>
	        </html>
	    """.trimIndent()

		trailerWebView.loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "utf-8", null)

		trailerHideJob?.cancel()
		trailerHideJob = viewLifecycleOwner.lifecycleScope.launch {
			delay(TRAILER_MAX_DURATION_MS)
			if (isAdded && trailerContainer.isVisible && itemId == currentPreviewItemId) {
				resetTrailer(clearWebView = true)
			}
		}
	}


	fun cancelTrailerHide() {
		trailerHideJob?.cancel()
		trailerHideJob = null
	}

	private fun fetchTrailerFromYouTube(itemId: String, query: String, callback: (String) -> Unit) {
		viewLifecycleOwner.lifecycleScope.launch {
			try {
				val videoId = withContext(Dispatchers.IO) {
					val encodedQuery = URLEncoder.encode(query, "UTF-8")
					val searchUrl = "https://www.youtube.com/results?search_query=$encodedQuery"
					val request = Request.Builder()
						.url(searchUrl)
						.header("User-Agent", "Mozilla/5.0 (Android TV; SuperJellyTV)")
						.build()

					trailerSearchCall?.cancel()
					val call = trailerHttpClient.newCall(request)
					trailerSearchCall = call
					val response = call.execute()
					response.use { res ->
						if (!res.isSuccessful) return@withContext ""
						val body = res.body?.string().orEmpty()
						extractFirstYouTubeVideoIdFromSearch(body)
					}
				}

				if (itemId != currentPreviewItemId || videoId.isBlank()) {
					callback("")
					return@launch
				}

				// Do not block the preview on oEmbed validation: on Android TV it often fails for
				// embeddable videos even when the iframe can play correctly. The WebView handles
				// final playback errors and falls back without freezing navigation.
				callback(videoId)
			} catch (e: Exception) {
				registerTrailerFailure()
				Timber.w(e, "Failed to fetch YouTube trailer")
				callback("")
			}
		}
	}


	private fun extractFirstYouTubeVideoIdFromSearch(body: String): String {
		return YOUTUBE_SEARCH_ID_PATTERNS
			.asSequence()
			.mapNotNull { pattern -> pattern.find(body)?.groups?.get(1)?.value }
			.distinct()
			.firstOrNull()
			.orEmpty()
	}


	private fun resetPreview() {
		currentPreviewItemId = null
		currentBackdropUrl = null
		resetTrailer(clearWebView = true)
		previewBackdropJob?.cancel()

		previewBackground.visibility = View.GONE
		previewGradient.visibility = View.GONE
		previewBackground.setImageDrawable(null)
		previewTitle.text = ""
		previewDescription.text = ""
		previewContentType.visibility = View.GONE
		previewYear.visibility = View.GONE
		previewDuration.visibility = View.GONE
		previewAgeRating.visibility = View.GONE
		previewSubtitle.visibility = View.GONE
		resetTrailerTimer()
	}

	private fun hideTrailerForSelectionChange() {
		trailerSearchCall?.cancel()
		trailerCheckCall?.cancel()
		currentTrailerVideoId = null
		trailerContainer.animate().cancel()
		trailerContainer.alpha = 0f
		trailerContainer.visibility = View.GONE
		trailerGradientOverlay.visibility = View.GONE
	}

	private fun resetTrailer(clearWebView: Boolean = false) {
		trailerJob?.cancel()
		trailerSearchCall?.cancel()
		trailerCheckCall?.cancel()
		trailerContainer.animate().cancel()
		trailerContainer.animate()
			.alpha(0f)
			.setDuration(TRAILER_FADE_OUT_MS)
			.withEndAction {
				trailerContainer.visibility = View.GONE
				trailerGradientOverlay.visibility = View.GONE
				if (clearWebView) {
					currentTrailerVideoId = null
					trailerWebView.stopLoading()
					trailerWebView.loadUrl("about:blank")
				}
			}
			.start()
	}

	fun playYouTubeTrailerWithDelay(item: BaseRowItem, expectedItemId: String) {
		trailerJob = viewLifecycleOwner.lifecycleScope.launch {
			delay(TRAILER_START_DELAY_MS)

			if (isAdded && isVisible && expectedItemId == currentPreviewItemId && !isTrailerBackoffActive()) {
				playYouTubeTrailer(item, expectedItemId)
			}
		}
	}

	private fun resetTrailerTimer() {
		trailerJob?.cancel()
		trailerJob = null
	}


	suspend fun checkedAllowedYoutubeVideo(videoUrl: String): Boolean = withContext(Dispatchers.IO) {
		try {
			val apiUrl = "https://www.youtube.com/oembed?url=${URLEncoder.encode(videoUrl, "UTF-8")}&format=json"
			val request = Request.Builder()
				.url(apiUrl)
				.header("User-Agent", "Mozilla/5.0 (Android TV; SuperJellyTV)")
				.build()
			trailerCheckCall?.cancel()
			val call = trailerHttpClient.newCall(request)
			trailerCheckCall = call
			call.execute().use { it.isSuccessful }
		} catch (e: Exception) {
			Timber.e(e, "Failed to check YouTube video")
			false
		}
	}

	private fun registerTrailerFailure() {
		trailerFailures++
		if (trailerFailures >= TRAILER_MAX_CONSECUTIVE_FAILURES) {
			trailersDisabledUntil = System.currentTimeMillis() + TRAILER_ERROR_BACKOFF_MS
			Timber.w("Trailer preview paused temporarily after repeated failures")
		}
	}

	private fun isTrailerBackoffActive(): Boolean {
		val active = System.currentTimeMillis() < trailersDisabledUntil
		if (!active && trailersDisabledUntil != 0L) {
			trailersDisabledUntil = 0L
			trailerFailures = 0
		}
		return active
	}

	override fun onDestroyView() {
		resetTrailerTimer()
		cancelTrailerHide()
		previewBackdropJob?.cancel()
		trailerSearchCall?.cancel()
		trailerCheckCall?.cancel()
		if (::trailerWebView.isInitialized) {
			trailerWebView.stopLoading()
			trailerWebView.loadUrl("about:blank")
		}
		super.onDestroyView()
	}

	private fun isTrailerEnabled(): Boolean {
		val userPreferences = get<UserPreferences>()
		return userPreferences[UserPreferences.trailerEnabled]
	}
}
