package org.jellyfin.androidtv.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.core.view.doOnAttach
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.AsyncImageView
import org.jellyfin.androidtv.auth.repository.ServerRepository
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.data.repository.NotificationsRepository
import org.jellyfin.androidtv.ui.itemhandling.BaseRowItem
import org.jellyfin.androidtv.ui.navigation.ActivityDestinations
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.playback.MediaManager
import org.jellyfin.androidtv.ui.startup.StartupActivity
import org.jellyfin.androidtv.util.ImageHelper
import org.jellyfin.androidtv.util.apiclient.JellyfinImage
import org.jellyfin.androidtv.util.apiclient.itemImages
import org.jellyfin.androidtv.util.apiclient.itemBackdropImages
import org.jellyfin.androidtv.util.apiclient.parentBackdropImages
import org.jellyfin.androidtv.util.apiclient.seriesThumbImage
import org.jellyfin.androidtv.data.repository.UserViewsRepository
import org.jellyfin.androidtv.ui.itemhandling.ItemLauncher
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.activityViewModel

class HomeFragmentNetflixStyle : Fragment() {
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

        // Set up communication with HomeRowsFragment
        setupRowsFragmentListener()
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
        if (item == null || item.baseItem == null) {
            // Hide and clear preview when no item is selected
            previewBackground.visibility = View.GONE
            previewGradient.visibility = View.GONE
            previewBackground.setImageDrawable(null)
            previewTitle.text = ""
            previewDescription.text = ""
            previewContentType.visibility = View.GONE
            previewYear.visibility = View.GONE
            previewDuration.visibility = View.GONE
            previewAgeRating.visibility = View.GONE
            return
        }
        
        val baseItem = item.baseItem!!

        // Update background image
        val backdropImage = when {
            // First try item's own backdrops
            baseItem.itemBackdropImages.isNotEmpty() -> baseItem.itemBackdropImages.firstOrNull()
            baseItem.itemImages[ImageType.BACKDROP] != null -> baseItem.itemImages[ImageType.BACKDROP]
            // For episodes, try parent backdrops
            baseItem.parentBackdropImages.isNotEmpty() -> baseItem.parentBackdropImages.firstOrNull()
            // For episodes, use primary image (screenshot) as fallback
            baseItem.type == BaseItemKind.EPISODE && baseItem.itemImages[ImageType.PRIMARY] != null -> 
                baseItem.itemImages[ImageType.PRIMARY]
            // Last resort: try series thumb
            baseItem.type == BaseItemKind.EPISODE && baseItem.seriesThumbImage != null -> 
                baseItem.seriesThumbImage
            else -> null
        }
        
        if (backdropImage != null) {
            val backdropUrl = imageHelper.getImageUrl(backdropImage)
            
            // Load image with a callback to show views only after loading
            previewBackground.doOnAttach {
                it.findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
                    // Load the image first
                    previewBackground.load(backdropUrl, blurHash = null) // No blur hash to avoid placeholder
                    // Then show both views
                    previewBackground.visibility = View.VISIBLE
                    previewGradient.visibility = View.VISIBLE
                }
            }
        } else {
            // Hide background when no backdrop is available
            previewBackground.visibility = View.GONE
            previewGradient.visibility = View.GONE
            previewBackground.setImageDrawable(null)
        }

        // Update title
        previewTitle.text = baseItem.name

        // Update description
        previewDescription.text = baseItem.overview ?: ""

        // Update metadata
        updateMetadata(baseItem)

        // Keep poster hidden - don't show the card on top right
        previewPoster.visibility = View.GONE
    }

    private fun updateMetadata(item: BaseItemDto) {
        // Content type
        when (item.type) {
            BaseItemKind.MOVIE -> {
                previewContentType.text = "FILM"
                previewContentType.visibility = View.VISIBLE
            }
            BaseItemKind.SERIES -> {
                previewContentType.text = "SERIES"
                previewContentType.visibility = View.VISIBLE
            }
            BaseItemKind.EPISODE -> {
                previewContentType.text = "EPISODE"
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
                timber.log.Timber.e(e, "Failed to navigate to library type: $collectionType")
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
                user?.let {
                    val imageUrl = imageHelper.getPrimaryImageUrl(it)
                    userAvatar?.load(imageUrl)
                }
            }
        }
    }
    
    private fun setupDynamicNavigationTabs(view: View) {
        lifecycleScope.launch {
            try {
                val userViews = userViewsRepository.views.first()
                val navContainer = view.findViewById<ViewGroup>(R.id.nav_pills_container)
                
                // Clear existing dynamic tabs (keep only static ones)
                navContainer?.removeAllViews()
                
                // Create tabs based on available libraries
                var previousButtonId: Int? = null
                var firstLibraryButtonId: Int? = null
                
                for (userView in userViews) {
                    val tabButton = createNavTab(userView)
                    if (tabButton != null) {
                        navContainer?.addView(tabButton)
                        
                        // Set up focus navigation
                        if (previousButtonId != null) {
                            tabButton.nextFocusLeftId = previousButtonId
                            view.findViewById<View>(previousButtonId)?.nextFocusRightId = tabButton.id
                        } else {
                            firstLibraryButtonId = tabButton.id
                            // Connect search button to first library tab
                            view.findViewById<View>(R.id.toolbar_search)?.nextFocusRightId = tabButton.id
                        }
                        
                        previousButtonId = tabButton.id
                    }
                }
                
                // Add Jellyfin tab (always present)
                val jellyfinTab = createJellyfinTab()
                navContainer?.addView(jellyfinTab)
                
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
                
            } catch (e: Exception) {
                timber.log.Timber.e(e, "Failed to set up dynamic navigation tabs")
                // Fallback to static tabs if dynamic setup fails
                setupStaticNavigationTabs(view)
            }
        }
    }
    
    private fun createNavTab(userView: BaseItemDto): TextView? {
        val displayName = getDisplayNameForCollectionType(userView.collectionType, userView.name)
        if (displayName == null) return null
        
        return TextView(requireContext()).apply {
            id = View.generateViewId()
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                resources.getDimensionPixelSize(R.dimen.toolbar_nav_button_height)
            ).apply {
                leftMargin = resources.getDimensionPixelSize(R.dimen.toolbar_nav_button_margin)
            }
            text = displayName
            textSize = 15f
            setTextColor(resources.getColorStateList(R.color.nav_text_color, null))
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            gravity = android.view.Gravity.CENTER
            setPadding(
                resources.getDimensionPixelSize(R.dimen.toolbar_nav_button_padding_horizontal),
                0,
                resources.getDimensionPixelSize(R.dimen.toolbar_nav_button_padding_horizontal),
                0
            )
            background = resources.getDrawable(R.drawable.nav_pill_animated_background, null)
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
    
    private fun createJellyfinTab(): TextView {
        return TextView(requireContext()).apply {
            id = View.generateViewId()
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                resources.getDimensionPixelSize(R.dimen.toolbar_nav_button_height)
            ).apply {
                leftMargin = resources.getDimensionPixelSize(R.dimen.toolbar_nav_button_margin)
            }
            text = "Jellyfin"
            textSize = 15f
            setTextColor(resources.getColorStateList(R.color.nav_text_color, null))
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            gravity = android.view.Gravity.CENTER
            setPadding(
                resources.getDimensionPixelSize(R.dimen.toolbar_nav_button_padding_horizontal),
                0,
                resources.getDimensionPixelSize(R.dimen.toolbar_nav_button_padding_horizontal),
                0
            )
            background = resources.getDrawable(R.drawable.nav_pill_animated_background, null)
            stateListAnimator = android.animation.AnimatorInflater.loadStateListAnimator(
                requireContext(),
                R.animator.nav_button_state_animator
            )
            isFocusable = true
            isClickable = true
            
            setOnClickListener {
                startActivity(ActivityDestinations.userPreferences(requireContext()))
            }
        }
    }
    
    private fun getDisplayNameForCollectionType(collectionType: CollectionType?, fallbackName: String?): String? {
        return when (collectionType) {
            CollectionType.MOVIES -> "Movies"
            CollectionType.TVSHOWS -> "Shows"
            CollectionType.MUSIC -> "Music"
            CollectionType.PHOTOS -> "Photos"
            CollectionType.PLAYLISTS -> "Playlists"
            CollectionType.LIVETV -> "Live TV"
            CollectionType.BOXSETS -> "Collections"
            else -> fallbackName // Use the library's custom name for unknown types
        }
    }
    
    private fun navigateToSpecificLibrary(userView: BaseItemDto) {
        lifecycleScope.launch {
            try {
                val destination = itemLauncher.getUserViewDestination(userView)
                navigationRepository.navigate(destination)
            } catch (e: Exception) {
                timber.log.Timber.e(e, "Failed to navigate to library: ${userView.name}")
                // Fallback to generic library browser
                navigationRepository.navigate(Destinations.libraryBrowser(userView))
            }
        }
    }
    
    private fun setupStaticNavigationTabs(view: View) {
        // Fallback implementation: create basic tabs when dynamic setup fails
        val navContainer = view.findViewById<ViewGroup>(R.id.nav_pills_container)
        
        // Create static tabs as fallback
        val moviesTab = createStaticTab("Movies") { navigateToLibraryType(CollectionType.MOVIES) }
        val showsTab = createStaticTab("Shows") { navigateToLibraryType(CollectionType.TVSHOWS) }
        val playlistsTab = createStaticTab("Playlists") { navigateToLibraryType(CollectionType.PLAYLISTS) }
        val jellyfinTab = createStaticTab("Jellyfin") { startActivity(ActivityDestinations.userPreferences(requireContext())) }
        
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
    
    private fun createStaticTab(text: String, onClickListener: () -> Unit): TextView {
        return TextView(requireContext()).apply {
            id = View.generateViewId()
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                resources.getDimensionPixelSize(R.dimen.toolbar_nav_button_height)
            ).apply {
                leftMargin = resources.getDimensionPixelSize(R.dimen.toolbar_nav_button_margin)
            }
            this.text = text
            textSize = 15f
            setTextColor(resources.getColorStateList(R.color.nav_text_color, null))
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            gravity = android.view.Gravity.CENTER
            setPadding(
                resources.getDimensionPixelSize(R.dimen.toolbar_nav_button_padding_horizontal),
                0,
                resources.getDimensionPixelSize(R.dimen.toolbar_nav_button_padding_horizontal),
                0
            )
            background = resources.getDrawable(R.drawable.nav_pill_animated_background, null)
            stateListAnimator = android.animation.AnimatorInflater.loadStateListAnimator(
                requireContext(),
                R.animator.nav_button_state_animator
            )
            isFocusable = true
            isClickable = true
            
            setOnClickListener { onClickListener() }
        }
    }
}