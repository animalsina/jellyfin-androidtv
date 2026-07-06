package org.jellyfin.androidtv.integration

import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.edit
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import androidx.tvprovider.media.tv.Channel
import androidx.tvprovider.media.tv.ChannelLogoUtils
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.TvContractCompat.WatchNextPrograms
import androidx.tvprovider.media.tv.WatchNextProgram
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.await
import coil3.ImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.constant.HomeSectionType
import org.jellyfin.androidtv.data.model.ExternalCatalogItem
import org.jellyfin.androidtv.data.repository.ExternalCatalogRepository
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.data.repository.UserViewsRepository
import org.jellyfin.androidtv.integration.provider.ImageProvider
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.preference.UserSettingPreferences
import org.jellyfin.androidtv.ui.startup.StartupActivity
import org.jellyfin.androidtv.util.AndroidVersion
import org.jellyfin.androidtv.util.ImageHelper
import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.androidtv.util.apiclient.itemBackdropImages
import org.jellyfin.androidtv.util.apiclient.itemImages
import org.jellyfin.androidtv.util.apiclient.parentBackdropImages
import org.jellyfin.androidtv.util.apiclient.parentImages
import org.jellyfin.androidtv.util.dp
import org.jellyfin.androidtv.util.sdk.isUsable
import org.jellyfin.androidtv.util.stripHtml
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.exception.TimeoutException
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.liveTvApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.api.client.extensions.userViewsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.api.request.GetRecordingsRequest
import org.jellyfin.sdk.model.api.request.GetResumeItemsRequest
import org.jellyfin.sdk.model.extensions.ticks
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

/**
 * Manages channels on the android tv home screen.
 *
 * More info: https://developer.android.com/training/tv/discovery/recommendations-channel.
 */
class LeanbackChannelWorker(
	private val context: Context,
	workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams), KoinComponent {
	companion object {
		private const val PERIODIC_UPDATE_REQUEST_NAME = "LeanbackChannelPeriodicUpdateRequest"
		private const val PROJECTIVY_CHANNEL_ITEM_LIMIT = 20
		private const val PROJECTIVY_EXTERNAL_CHANNEL_LIMIT = 32
		private val PROJECTIVY_GENRES = listOf(
			"action" to R.string.lbl_action,
			"adventure" to R.string.lbl_adventure,
			"animation" to R.string.lbl_animation,
			"comedy" to R.string.lbl_comedy,
			"crime" to R.string.lbl_crime,
			"documentary" to R.string.lbl_documentary,
			"drama" to R.string.lbl_drama,
			"family" to R.string.lbl_family,
			"fantasy" to R.string.lbl_fantasy,
			"history" to R.string.lbl_history,
			"horror" to R.string.lbl_horror,
			"music" to R.string.lbl_music,
			"mystery" to R.string.lbl_mystery,
			"romance" to R.string.lbl_romance,
			"science_fiction" to R.string.lbl_science_fiction,
			"thriller" to R.string.lbl_thriller,
			"war" to R.string.lbl_war,
			"western" to R.string.lbl_western,
		)

		suspend fun enqueue(workManager: WorkManager) {
			workManager.enqueueUniquePeriodicWork(
				PERIODIC_UPDATE_REQUEST_NAME,
				ExistingPeriodicWorkPolicy.UPDATE,
				PeriodicWorkRequestBuilder<LeanbackChannelWorker>(1, TimeUnit.HOURS)
					.setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
					.build()
			).await()
		}
	}

	private val api by inject<ApiClient>()
	private val userPreferences by inject<UserPreferences>()
	private val userSettingPreferences by inject<UserSettingPreferences>()
	private val userViewsRepository by inject<UserViewsRepository>()
	private val imageHelper by inject<ImageHelper>()
	private val externalCatalogRepository by inject<ExternalCatalogRepository>()
	private val imageLoader by inject<ImageLoader>()

	private data class HomeSectionChannel(
		val key: String,
		val title: String,
		val items: List<Any>,
	)

	private data class ExternalCatalogChannel(
		val key: String,
		val title: String,
		val items: List<ExternalCatalogItem>,
	)

	/**
	 * Check if the app can use Leanback features and is API level 26 or higher.
	 */
	private val isSupported = AndroidVersion.isAtLeastO &&
		// Check for leanback support
		context.packageManager.hasSystemFeature("android.software.leanback")
		// Check for "android.media.tv" provider to workaround a false-positive in the previous check
		&& context.packageManager.resolveContentProvider(TvContractCompat.AUTHORITY, 0) != null

	/**
	 * Update all channels for the currently authenticated user.
	 */
	override suspend fun doWork(): Result = when {
		// Fail when not supported
		!isSupported -> Result.failure()
		// Retry later if no authenticated user is found
		!api.isUsable -> Result.retry()
		else -> try {
			// Get next up episodes
			val (resumeItems, nextUpItems) = getNextUpItems()
			// Get latest media
			val (latestEpisodes, latestMovies, latestMedia) = getLatestMedia()
			val myMedia = getMyMedia()
			val externalCatalogItems = getExternalCatalogItems()
			val externalCatalogChannels = getExternalCatalogChannels(externalCatalogItems)
			val onlineNewReleaseItems = getOnlineNewReleaseItems()
			val homeSectionChannels = getHomeSectionChannels()
			// Delete current items from the channels
			context.contentResolver.delete(TvContractCompat.PreviewPrograms.CONTENT_URI, null, null)

			// Get channel URIs
			val latestMediaChannel = getChannelUri(
				"latest_media", Channel.Builder()
					.setType(TvContractCompat.Channels.TYPE_PREVIEW)
					.setDisplayName(context.getString(R.string.home_section_latest_media))
					.setAppLinkIntent(Intent(context, StartupActivity::class.java))
					.build(),
				default = true
			)
			val myMediaChannel = getChannelUri(
				"my_media", Channel.Builder()
					.setType(TvContractCompat.Channels.TYPE_PREVIEW)
					.setDisplayName(context.getString(R.string.lbl_my_media))
					.setAppLinkIntent(Intent(context, StartupActivity::class.java))
					.build()
			)
			val nextUpChannel = getChannelUri(
				"next_up", Channel.Builder()
					.setType(TvContractCompat.Channels.TYPE_PREVIEW)
					.setDisplayName(context.getString(R.string.lbl_next_up))
					.setAppLinkIntent(Intent(context, StartupActivity::class.java))
					.build()
			)
			val latestMoviesChannel = getChannelUri(
				"latest_movies", Channel.Builder()
					.setType(TvContractCompat.Channels.TYPE_PREVIEW)
					.setDisplayName(context.getString(R.string.lbl_movies))
					.setAppLinkIntent(Intent(context, StartupActivity::class.java))
					.build()
			)
			val latestEpisodesChannel = getChannelUri(
				"latest_episodes", Channel.Builder()
					.setType(TvContractCompat.Channels.TYPE_PREVIEW)
					.setDisplayName(context.getString(R.string.lbl_new_episodes))
					.setAppLinkIntent(Intent(context, StartupActivity::class.java))
					.build()
			)
			val externalCatalogChannel = if (externalCatalogItems.isNotEmpty()) getChannelUri(
				"external_catalog", Channel.Builder()
					.setType(TvContractCompat.Channels.TYPE_PREVIEW)
					.setDisplayName(context.getString(R.string.home_section_external_catalog))
					.setAppLinkIntent(Intent(context, StartupActivity::class.java))
					.build()
			) else null
			val onlineNewReleasesChannel = if (onlineNewReleaseItems.isNotEmpty()) getChannelUri(
				"online_new_releases", Channel.Builder()
					.setType(TvContractCompat.Channels.TYPE_PREVIEW)
					.setDisplayName(context.getString(R.string.home_section_online_new_releases))
					.setAppLinkIntent(Intent(context, StartupActivity::class.java))
					.build()
			) else null
			val preferParentThumb = userPreferences[UserPreferences.seriesThumbnailsEnabled]

			val allItemsToPrefetch = mutableListOf<String>()
			fun collectForPrefetch(items: List<Any>, limit: Int = 10) {
				items.take(limit).forEach { item ->
					when (item) {
						is BaseItemDto -> {
							val width = if (item.type == BaseItemKind.EPISODE) 480 else 320
							val image = when {
								item.type == BaseItemKind.MOVIE || item.type == BaseItemKind.SERIES -> item.itemImages[ImageType.PRIMARY]
								(preferParentThumb || !item.itemImages.contains(ImageType.PRIMARY)) && item.parentImages.contains(ImageType.THUMB) -> item.parentImages[ImageType.THUMB]
								else -> item.itemImages[ImageType.PRIMARY]
							}
							image?.getUrl(api, fillWidth = width)?.let { allItemsToPrefetch.add(it) }
						}
						is ExternalCatalogItem -> {
							(item.posterUrl ?: item.backdropUrl)?.let { allItemsToPrefetch.add(it) }
						}
					}
				}
			}

			// Add new items
			arrayOf(
				nextUpItems to nextUpChannel,
				latestMedia to latestMediaChannel,
				latestMovies to latestMoviesChannel,
				latestEpisodes to latestEpisodesChannel,
				myMedia to myMediaChannel,
			).forEach { (items, channel) ->
				if (channel == null) {
					Timber.e("Skipping channel because it was not available")
				} else {
					collectForPrefetch(items)
					items.map { item ->
						createPreviewProgram(
							channel,
							item,
							preferParentThumb
						)
					}.let {
						context.contentResolver.bulkInsert(
							TvContractCompat.PreviewPrograms.CONTENT_URI,
							it.toTypedArray()
						)
					}
				}
			}
			homeSectionChannels.forEach { channelData ->
				val channel = getChannelUri(
					"home_${channelData.key}",
					Channel.Builder()
						.setType(TvContractCompat.Channels.TYPE_PREVIEW)
						.setDisplayName(channelData.title)
						.setAppLinkIntent(Intent(context, StartupActivity::class.java))
						.build()
				)
				if (channel != null) {
					collectForPrefetch(channelData.items)
					val contentValues = channelData.items.mapNotNull { item ->
						val isWide = channelData.key == HomeSectionType.RECOMMENDED_FOR_YOU.serializedName ||
									 channelData.key == HomeSectionType.TRENDING_THIS_WEEK.serializedName ||
									 channelData.key.startsWith("genre_")
						when (item) {
							is BaseItemDto -> createPreviewProgram(channel, item, preferParentThumb, useWideAspect = isWide)
							is ExternalCatalogItem -> createExternalPreviewProgram(channel, item, useWideAspect = isWide)
							else -> null
						}
					}.toTypedArray()
					context.contentResolver.bulkInsert(
						TvContractCompat.PreviewPrograms.CONTENT_URI,
						contentValues
					)
				}
			}
			insertExternalPrograms(externalCatalogChannel, externalCatalogItems)
			collectForPrefetch(externalCatalogItems)
			externalCatalogChannels.forEach { channelData ->
				val channel = getChannelUri(
					"external_${channelData.key}",
					Channel.Builder()
						.setType(TvContractCompat.Channels.TYPE_PREVIEW)
						.setDisplayName(channelData.title)
						.setAppLinkIntent(Intent(context, StartupActivity::class.java))
						.build()
				)
				collectForPrefetch(channelData.items)
				insertExternalPrograms(channel, channelData.items)
			}
			insertExternalPrograms(onlineNewReleasesChannel, onlineNewReleaseItems)
			collectForPrefetch(onlineNewReleaseItems)
			updateWatchNext(resumeItems + nextUpItems)
			collectForPrefetch(resumeItems + nextUpItems)

			// Start prefetching in background
			prefetchImages(allItemsToPrefetch.distinct())

			// Success!
			Result.success()
		} catch (err: TimeoutException) {
			Timber.w(err, "Server unreachable, trying again later")

			Result.retry()
		} catch (err: ApiClientException) {
			Timber.e(err, "SDK error, trying again later")

			Result.retry()
		}
	}

	/**
	 * Get the uri for a channel or create it if it doesn't exist. Uses the [settings] parameter to
	 * update or create the channel. The [name] parameter is used to store the id and should be
	 * unique.
	 */
	private fun getChannelUri(name: String, settings: Channel, default: Boolean = false): Uri? {
		val store = context.getSharedPreferences("leanback_channels", Context.MODE_PRIVATE)
		var uri: Uri? = null

		// Try and re-use our existing channel definition
		if (store.contains(name)) {
			uri = store.getString(name, null)?.toUri()

			if (uri != null) {
				val result = context.contentResolver.update(uri, settings.toContentValues(), null, null)
				// If we did not affect exactly 1 row there might be something wrong, so recreate it
				if (result != 1) uri = null
			}
		}

		if (uri == null) {
			// Create new channel
			uri = context.contentResolver.insert(
				TvContractCompat.Channels.CONTENT_URI,
				settings.toContentValues()
			)

			// Set as default row to display (we can request one row to automatically be added to the home screen)
			if (uri != null && default) {
				TvContractCompat.requestChannelBrowsable(context, ContentUris.parseId(uri))
			}

			// Save uri to shared preferences
			store.edit { putString(name, uri?.toString()) }
		}

		// Update logo
		if (uri != null) {
			ResourcesCompat.getDrawable(context.resources, R.mipmap.app_icon, context.theme)?.let {
				ChannelLogoUtils.storeChannelLogo(
					context,
					ContentUris.parseId(uri),
					it.toBitmap(80.dp(context), 80.dp(context))
				)
			}
		}

		return uri
	}

	/**
	 * Updates the "my media" row with current media libraries.
	 */
	@Suppress("RestrictedApi")
	private suspend fun getMyMedia(): List<BaseItemDto> {
		val response by api.userViewsApi.getUserViews(includeHidden = false)

		// Add new items
		return response.items
			.filter { userViewsRepository.isSupported(it.collectionType) }
	}

	private suspend fun getHomeSectionChannels(): List<HomeSectionChannel> = withContext(Dispatchers.IO) {
		runCatching { if (userSettingPreferences.shouldUpdate) userSettingPreferences.update() }
		val userViews = runCatching { userViewsRepository.views.first() }.getOrDefault(emptyList())
		val configuredSections = userSettingPreferences.activeHomesections
		val allProgramSections = HomeSectionType.entries
			.filterNot { it == HomeSectionType.NONE || it == HomeSectionType.ONLINE_NEW_RELEASES }
		val sectionChannels = (configuredSections + allProgramSections)
			.distinct()
			.mapNotNull { section ->
				val items = runCatching { loadItemsForHomeSection(section, userViews) }
					.onFailure { Timber.w(it, "Unable to populate Android TV channel for $section") }
					.getOrDefault(emptyList())
				if (items.isEmpty()) null
				else HomeSectionChannel(section.serializedName, context.getString(section.nameRes), items.take(PROJECTIVY_CHANNEL_ITEM_LIMIT))
			}

		sectionChannels + getGenreProjectivyChannels(userViews)
	}

	private suspend fun loadItemsForHomeSection(section: HomeSectionType, userViews: Collection<BaseItemDto>): List<Any> = when (section) {
		HomeSectionType.LATEST_MEDIA -> api.userLibraryApi.getLatestMedia(
			fields = ItemRepository.itemFields,
			limit = PROJECTIVY_CHANNEL_ITEM_LIMIT,
			includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES, BaseItemKind.EPISODE),
			isPlayed = false,
		).content
		HomeSectionType.LIBRARY_TILES_SMALL,
		HomeSectionType.LIBRARY_BUTTONS -> getMyMedia()
		HomeSectionType.RESUME -> api.itemsApi.getResumeItems(
			GetResumeItemsRequest(
				limit = PROJECTIVY_CHANNEL_ITEM_LIMIT,
				fields = ItemRepository.itemFields,
				imageTypeLimit = 1,
				enableTotalRecordCount = false,
				mediaTypes = listOf(MediaType.VIDEO),
			)
		).content.items
		HomeSectionType.RESUME_AUDIO -> api.itemsApi.getResumeItems(
			GetResumeItemsRequest(
				limit = PROJECTIVY_CHANNEL_ITEM_LIMIT,
				fields = ItemRepository.itemFields,
				imageTypeLimit = 1,
				enableTotalRecordCount = false,
				mediaTypes = listOf(MediaType.AUDIO),
			)
		).content.items
		HomeSectionType.ACTIVE_RECORDINGS -> api.liveTvApi.getRecordings(
			GetRecordingsRequest(
				fields = ItemRepository.itemFields,
				enableImages = true,
				limit = PROJECTIVY_CHANNEL_ITEM_LIMIT,
			)
		).content.items
		HomeSectionType.NEXT_UP -> api.tvShowsApi.getNextUp(
			imageTypeLimit = 1,
			limit = PROJECTIVY_CHANNEL_ITEM_LIMIT,
			enableResumable = false,
			fields = ItemRepository.browseFields,
		).content.items
		HomeSectionType.RECOMMENDED_FOR_YOU -> queryMovieSeries(userViews, listOf(ItemSortBy.RANDOM), minCommunityRating = 6.5, isPlayed = false)
		HomeSectionType.TRENDING_THIS_WEEK -> queryMovieSeries(userViews, listOf(ItemSortBy.DATE_PLAYED))
		HomeSectionType.RECENTLY_RELEASED -> queryMovieSeries(userViews, listOf(ItemSortBy.PREMIERE_DATE))
		HomeSectionType.POPULAR_MOVIES -> queryMovieSeries(userViews, listOf(ItemSortBy.PLAY_COUNT), includeTypes = listOf(BaseItemKind.MOVIE))
		HomeSectionType.POPULAR_TV -> queryMovieSeries(userViews, listOf(ItemSortBy.PLAY_COUNT), includeTypes = listOf(BaseItemKind.SERIES))
		HomeSectionType.RANDOM_MOVIES -> queryMovieSeries(userViews, listOf(ItemSortBy.RANDOM), includeTypes = listOf(BaseItemKind.MOVIE))
		HomeSectionType.RANDOM_SERIES -> queryMovieSeries(userViews, listOf(ItemSortBy.RANDOM), includeTypes = listOf(BaseItemKind.SERIES))
		HomeSectionType.UNWATCHED_RANDOM_MOVIES -> queryMovieSeries(userViews, listOf(ItemSortBy.RANDOM), includeTypes = listOf(BaseItemKind.MOVIE), isPlayed = false)
		HomeSectionType.LONG_AGO_MOVIES -> queryMovieSeries(
			userViews,
			listOf(ItemSortBy.DATE_PLAYED),
			includeTypes = listOf(BaseItemKind.MOVIE),
			sortOrder = listOf(SortOrder.ASCENDING),
			isPlayed = true,
		)
		HomeSectionType.SIMILAR_TO_WATCHED -> queryMovieSeries(
			userViews,
			listOf(ItemSortBy.RANDOM),
			minCommunityRating = 6.0,
			isPlayed = false
		)
		HomeSectionType.GENRE_RANDOM_MOVIES -> queryMovieSeries(
			userViews,
			listOf(ItemSortBy.RANDOM),
			includeTypes = listOf(BaseItemKind.MOVIE),
			isPlayed = false
		)
		HomeSectionType.GENRE_RANDOM_TV -> queryMovieSeries(
			userViews,
			listOf(ItemSortBy.RANDOM),
			includeTypes = listOf(BaseItemKind.SERIES),
			isPlayed = false
		)
		HomeSectionType.GENRE_RANDOM_MIXED -> queryMovieSeries(
			userViews,
			listOf(ItemSortBy.RANDOM),
			isPlayed = false
		)
		HomeSectionType.MOOD_LIGHT -> queryMovieSeries(
			userViews,
			listOf(ItemSortBy.RANDOM),
			genres = listOf("Comedy", "Family", "Animation"),
			minCommunityRating = 6.0
		)
		HomeSectionType.MOOD_ACTION -> queryMovieSeries(
			userViews,
			listOf(ItemSortBy.RANDOM),
			genres = listOf("Action", "Adventure", "Thriller"),
			minCommunityRating = 6.0
		)
		HomeSectionType.MOOD_SHORT -> queryMovieSeries(
			userViews,
			listOf(ItemSortBy.RANDOM),
			includeTypes = listOf(BaseItemKind.MOVIE),
			isPlayed = false
		)
		HomeSectionType.LIVE_TV -> api.liveTvApi.getRecommendedPrograms(
			isAiring = true,
			fields = ItemRepository.itemFields,
			limit = PROJECTIVY_CHANNEL_ITEM_LIMIT
		).content.items.orEmpty()
		HomeSectionType.RESUME_BOOK -> api.itemsApi.getResumeItems(
			GetResumeItemsRequest(
				limit = PROJECTIVY_CHANNEL_ITEM_LIMIT,
				fields = ItemRepository.itemFields,
				mediaTypes = listOf(MediaType.AUDIO),
				includeItemTypes = listOf(BaseItemKind.AUDIO_BOOK)
			)
		).content.items.orEmpty()
		HomeSectionType.EXTERNAL_PROVIDERS -> externalCatalogRepository.loadHomeCatalog(limit = PROJECTIVY_CHANNEL_ITEM_LIMIT)
		HomeSectionType.PLUTO_ACTION -> externalCatalogRepository.loadCatalogByGroup("pluto-tv", listOf("action", "azione", "avventura"), PROJECTIVY_CHANNEL_ITEM_LIMIT)
		HomeSectionType.PLUTO_COMEDY -> externalCatalogRepository.loadCatalogByGroup("pluto-tv", listOf("comedy", "commedia", "comedie"), PROJECTIVY_CHANNEL_ITEM_LIMIT)
		HomeSectionType.PLUTO_DRAMA -> externalCatalogRepository.loadCatalogByGroup("pluto-tv", listOf("drama", "drammatico", "drama"), PROJECTIVY_CHANNEL_ITEM_LIMIT)
		HomeSectionType.PLUTO_THRILLER -> externalCatalogRepository.loadCatalogByGroup("pluto-tv", listOf("thriller", "crime", "giallo"), PROJECTIVY_CHANNEL_ITEM_LIMIT)
		HomeSectionType.PLUTO_DOCUMENTARY -> externalCatalogRepository.loadCatalogByGroup("pluto-tv", listOf("documentary", "documentari", "doc"), PROJECTIVY_CHANNEL_ITEM_LIMIT)
		HomeSectionType.PLUTO_SCIFI -> externalCatalogRepository.loadCatalogByGroup("pluto-tv", listOf("sci fi", "fantascienza", "science fiction"), PROJECTIVY_CHANNEL_ITEM_LIMIT)
		HomeSectionType.RAIPLAY_FILM -> externalCatalogRepository.loadRaiPlayCatalog(ExternalCatalogRepository.RaiPlayKind.FILM, PROJECTIVY_CHANNEL_ITEM_LIMIT)
		HomeSectionType.RAIPLAY_SERIES -> externalCatalogRepository.loadRaiPlayCatalog(ExternalCatalogRepository.RaiPlayKind.SERIES, PROJECTIVY_CHANNEL_ITEM_LIMIT)
		HomeSectionType.ONLINE_NEW_RELEASES -> externalCatalogRepository.loadNewReleases(limit = PROJECTIVY_CHANNEL_ITEM_LIMIT)
		HomeSectionType.NONE -> emptyList()
	}

	private suspend fun queryMovieSeries(
		userViews: Collection<BaseItemDto>,
		sortBy: List<ItemSortBy>,
		includeTypes: List<BaseItemKind> = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
		minCommunityRating: Double? = null,
		isPlayed: Boolean? = null,
		sortOrder: List<SortOrder> = listOf(SortOrder.DESCENDING),
		genres: List<String>? = null,
	): List<BaseItemDto> {
		val parentId = userViews
			.filter { it.collectionType == org.jellyfin.sdk.model.api.CollectionType.MOVIES || it.collectionType == org.jellyfin.sdk.model.api.CollectionType.TVSHOWS }
			.takeIf { it.size == 1 }
			?.firstOrNull()
			?.id
		return api.itemsApi.getItems(
			GetItemsRequest(
				fields = ItemRepository.itemFields + ItemFields.DATE_CREATED,
				includeItemTypes = includeTypes,
				recursive = true,
				sortBy = sortBy,
				sortOrder = sortOrder,
				limit = PROJECTIVY_CHANNEL_ITEM_LIMIT,
				parentId = parentId,
				minCommunityRating = minCommunityRating,
				isPlayed = isPlayed,
				genres = genres,
			)
		).content.items.orEmpty()
	}

	private suspend fun getGenreProjectivyChannels(userViews: Collection<BaseItemDto>): List<HomeSectionChannel> {
		return PROJECTIVY_GENRES.mapNotNull { (key, titleRes) ->
			val title = context.getString(titleRes)
			val items = runCatching {
				queryMovieSeries(
					userViews = userViews,
					sortBy = listOf(ItemSortBy.RANDOM),
					genres = listOf(title),
				)
			}
				.onFailure { Timber.w(it, "Unable to populate Projectivy genre channel $title") }
				.getOrDefault(emptyList())
			if (items.isEmpty()) null
			else HomeSectionChannel("genre_$key", title, items.take(PROJECTIVY_CHANNEL_ITEM_LIMIT))
		}
	}

	private fun HomeSectionType.isExternalCatalogSection(): Boolean = when (this) {
		HomeSectionType.EXTERNAL_PROVIDERS,
		HomeSectionType.PLUTO_ACTION,
		HomeSectionType.PLUTO_COMEDY,
		HomeSectionType.PLUTO_DRAMA,
		HomeSectionType.PLUTO_THRILLER,
		HomeSectionType.PLUTO_DOCUMENTARY,
		HomeSectionType.PLUTO_SCIFI,
		HomeSectionType.RAIPLAY_FILM,
		HomeSectionType.RAIPLAY_SERIES -> true
		else -> false
	}

	private fun getExternalCatalogChannels(items: List<ExternalCatalogItem>): List<ExternalCatalogChannel> {
		return items
			.groupBy { item -> externalChannelTitle(item) }
			.toList()
			.sortedByDescending { (_, groupItems) -> groupItems.size }
			.take(PROJECTIVY_EXTERNAL_CHANNEL_LIMIT)
			.mapNotNull { (title, groupItems) ->
				val limited = groupItems.take(PROJECTIVY_CHANNEL_ITEM_LIMIT * 2)
				if (limited.isEmpty()) null else ExternalCatalogChannel(sanitizeChannelKey(title), title, limited)
			}
	}

	private fun externalChannelTitle(item: ExternalCatalogItem): String {
		val provider = when {
			item.providerId.startsWith("pluto", ignoreCase = true) -> "Pluto TV"
			item.providerId.startsWith("raiplay", ignoreCase = true) -> "RaiPlay"
			else -> item.providerName
		}
		val group = item.group?.takeIf { it.isNotBlank() }
		return if (group.isNullOrBlank() || group.equals(provider, ignoreCase = true)) provider else "$provider · $group"
	}

	private fun sanitizeChannelKey(value: String): String = value
		.lowercase()
		.replace(Regex("[^a-z0-9]+"), "_")
		.trim('_')
		.take(48)

	private suspend fun getExternalCatalogItems(): List<ExternalCatalogItem> = withContext(Dispatchers.IO) {
		runCatching { externalCatalogRepository.loadHomeCatalog(limit = 1000) }
			.onFailure { Timber.w(it, "Unable to populate external catalog Android TV channel") }
			.getOrDefault(emptyList())
	}

	private suspend fun getOnlineNewReleaseItems(): List<ExternalCatalogItem> = withContext(Dispatchers.IO) {
		runCatching { externalCatalogRepository.loadNewReleases(limit = 60) }
			.onFailure { Timber.w(it, "Unable to populate online new releases Android TV channel") }
			.getOrDefault(emptyList())
	}

	@SuppressLint("RestrictedApi")
	private fun insertExternalPrograms(channelUri: Uri?, items: List<ExternalCatalogItem>) {
		if (channelUri == null || items.isEmpty()) return
		context.contentResolver.bulkInsert(
			TvContractCompat.PreviewPrograms.CONTENT_URI,
			items.map { createExternalPreviewProgram(channelUri, it) }.toTypedArray()
		)
	}

	@SuppressLint("RestrictedApi")
	private fun createExternalPreviewProgram(channelUri: Uri, item: ExternalCatalogItem, useWideAspect: Boolean = false): ContentValues {
		val artwork = if (useWideAspect) {
			item.backdropUrl ?: item.posterUrl ?: imageHelper.getResourceUrl(context, R.drawable.tile_land_tv)
		} else {
			item.posterUrl ?: item.backdropUrl ?: imageHelper.getResourceUrl(context, R.drawable.tile_land_tv)
		}
		return PreviewProgram.Builder()
			.setChannelId(ContentUris.parseId(channelUri))
			.setType(
				when (item.type) {
					BaseItemKind.SERIES -> WatchNextPrograms.TYPE_TV_SERIES
					BaseItemKind.EPISODE -> WatchNextPrograms.TYPE_TV_EPISODE
					else -> WatchNextPrograms.TYPE_MOVIE
				}
			)
			.setTitle(item.title)
			.setDescription(item.availabilityNote ?: item.providerName)
			.setReleaseDate(item.releaseDate)
			.setPosterArtUri(ImageProvider.getImageUri(artwork))
			.setPosterArtAspectRatio(
				if (useWideAspect) TvContractCompat.PreviewPrograms.ASPECT_RATIO_16_9
				else TvContractCompat.PreviewPrograms.ASPECT_RATIO_MOVIE_POSTER
			)
			.setIntent(Intent(context, StartupActivity::class.java).apply {
				item.localItemId?.let { putExtra(StartupActivity.EXTRA_ITEM_ID, it.toString()) }
			})
			.apply {
				item.trailerUrl?.let { setPreviewVideoUri(Uri.parse(it)) }
			}
			.build()
			.toContentValues()
	}

	/**
	 * Gets the poster art for an item. Uses the [preferParentThumb] parameter to fetch the series
	 * image when preferred.
	 */
	private fun BaseItemDto.getPosterArtImageUrl(
		preferParentThumb: Boolean,
		useWideAspect: Boolean = false
	): Uri {
		val width = if (type == BaseItemKind.EPISODE || useWideAspect) 480 else 320

		val image = if (useWideAspect) {
			itemBackdropImages.firstOrNull() ?: itemImages[ImageType.BACKDROP] ?: parentBackdropImages.firstOrNull() ?: itemImages[ImageType.PRIMARY]
		} else {
			when {
				type == BaseItemKind.MOVIE || type == BaseItemKind.SERIES -> itemImages[ImageType.PRIMARY]
				(preferParentThumb || !itemImages.contains(ImageType.PRIMARY)) && parentImages.contains(ImageType.THUMB) -> parentImages[ImageType.THUMB]
				else -> itemImages[ImageType.PRIMARY]
			}
		}

		val url = image?.getUrl(api, fillWidth = width) ?: imageHelper.getResourceUrl(context, R.drawable.tile_land_tv)
		return ImageProvider.getImageUri(url)
	}

	private fun prefetchImages(urls: List<String>) {
		urls.forEach { url ->
			val request = ImageRequest.Builder(context)
				.data(url)
				.diskCachePolicy(CachePolicy.ENABLED)
				.memoryCachePolicy(CachePolicy.ENABLED)
				.build()
			imageLoader.enqueue(request)
		}
	}

	/**
	 * Gets the resume and next up episodes. The returned pair contains two lists:
	 * 1. resume items
	 * 2. next up items
	 */
	private suspend fun getNextUpItems(): Pair<List<BaseItemDto>, List<BaseItemDto>> =
		withContext(Dispatchers.IO) {
			val resume = async {
				api.itemsApi.getResumeItems(
					fields = ItemRepository.itemFields,
					imageTypeLimit = 1,
					limit = 10,
					mediaTypes = listOf(MediaType.VIDEO),
					includeItemTypes = listOf(BaseItemKind.EPISODE, BaseItemKind.MOVIE),
					excludeActiveSessions = true,
				).content.items
			}

			val nextUp = async {
				api.tvShowsApi.getNextUp(
					imageTypeLimit = 1,
					limit = 10,
					enableResumable = false,
					fields = ItemRepository.itemFields,
				).content.items
			}

			// Concat
			Pair(resume.await(), nextUp.await())
		}

	private suspend fun getLatestMedia(): Triple<List<BaseItemDto>, List<BaseItemDto>, List<BaseItemDto>> =
		withContext(Dispatchers.IO) {
			val latestEpisodes = async {
				api.userLibraryApi.getLatestMedia(
					fields = ItemRepository.itemFields,
					limit = 15,
					includeItemTypes = listOf(BaseItemKind.EPISODE),
					isPlayed = false
				).content
			}

			val latestMovies = async {
				api.userLibraryApi.getLatestMedia(
					fields = ItemRepository.itemFields,
					limit = 15,
					includeItemTypes = listOf(BaseItemKind.MOVIE),
					isPlayed = false
				).content
			}

			val latestMedia = async {
				api.userLibraryApi.getLatestMedia(
					fields = ItemRepository.itemFields,
					limit = 15,
					includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
					isPlayed = false
				).content
			}

			// Concat
			Triple(latestEpisodes.await(), latestMovies.await(), latestMedia.await())
		}

	@SuppressLint("RestrictedApi")
	private fun createPreviewProgram(
		channelUri: Uri,
		item: BaseItemDto,
		preferParentThumb: Boolean,
		useWideAspect: Boolean = false
	): ContentValues {
		val imageUri = item.getPosterArtImageUrl(preferParentThumb, useWideAspect)
		val seasonString = item.parentIndexNumber?.toString().orEmpty()

		val episodeString = when {
			item.indexNumberEnd != null && item.indexNumber != null ->
				"${item.indexNumber}-${item.indexNumberEnd}"

			else -> item.indexNumber?.toString().orEmpty()
		}

		return PreviewProgram.Builder()
			.setChannelId(ContentUris.parseId(channelUri))
			.setType(
				when (item.type) {
					BaseItemKind.SERIES -> WatchNextPrograms.TYPE_TV_SERIES
					BaseItemKind.MOVIE -> WatchNextPrograms.TYPE_MOVIE
					BaseItemKind.EPISODE -> WatchNextPrograms.TYPE_TV_EPISODE
					BaseItemKind.AUDIO -> WatchNextPrograms.TYPE_TRACK
					BaseItemKind.PLAYLIST -> WatchNextPrograms.TYPE_PLAYLIST
					else -> WatchNextPrograms.TYPE_CHANNEL
				}
			)
			.setTitle(item.seriesName ?: item.name)
			.setEpisodeTitle(if (item.type == BaseItemKind.EPISODE) item.name else null)
			.setDescription(item.overview?.stripHtml())
			.setReleaseDate(
				if (item.premiereDate != null) DateTimeFormatter.ISO_DATE.format(item.premiereDate)
				else null
			)
			.setPosterArtUri(imageUri)
			.setPosterArtAspectRatio(
				when {
					useWideAspect -> TvContractCompat.PreviewPrograms.ASPECT_RATIO_16_9
					item.type == BaseItemKind.COLLECTION_FOLDER ||
					item.type == BaseItemKind.EPISODE -> TvContractCompat.PreviewPrograms.ASPECT_RATIO_16_9

					else -> TvContractCompat.PreviewPrograms.ASPECT_RATIO_MOVIE_POSTER
				}
			)
			.setIntent(Intent(context, StartupActivity::class.java).apply {
				putExtra(StartupActivity.EXTRA_ITEM_ID, item.id.toString())
				putExtra(StartupActivity.EXTRA_ITEM_IS_USER_VIEW, item.type == BaseItemKind.COLLECTION_FOLDER)
			})
			.setDurationMillis(
				if (item.runTimeTicks?.ticks != null) {
					// If we are resuming, we need to show remaining time, cause GoogleTV
					// ignores setLastPlaybackPositionMillis
					val duration = item.runTimeTicks?.ticks ?: Duration.ZERO
					val playbackPosition = item.userData?.playbackPositionTicks?.ticks
						?: Duration.ZERO
					(duration - playbackPosition).inWholeMilliseconds.toInt()
				} else 0
			)
			.apply {
				if ((item.parentIndexNumber ?: 0) > 0)
					setSeasonNumber(seasonString, item.parentIndexNumber!!)
				if ((item.indexNumber ?: 0) > 0)
					setEpisodeNumber(episodeString, item.indexNumber!!)

				item.remoteTrailers?.firstOrNull()?.url?.let {
					setPreviewVideoUri(Uri.parse(it))
				}
			}.build().toContentValues()
	}

	/**
	 * Updates the "watch next" row with new and unfinished episodes. Does not include movies, music
	 * or other types of media. Uses the [nextUpItems] parameter to store items returned by a
	 * NextUpQuery().
	 */
	@SuppressLint("RestrictedApi")
	private fun updateWatchNext(nextUpItems: List<BaseItemDto>) {
		deletePrograms(nextUpItems)

		// Get current watch next state
		val currentWatchNextPrograms = getCurrentWatchNext()

		// Create all programs in nextUpItems but not in watch next
		val programsToAdd = nextUpItems
			.filter { next -> currentWatchNextPrograms.none { it.internalProviderId == next.id.toString() } }
		context.contentResolver.bulkInsert(
			WatchNextPrograms.CONTENT_URI,
			programsToAdd.map { item -> getBaseItemAsWatchNextProgram(item).toContentValues() }
				.toTypedArray())
	}

	/**
	 * Delete stale programs from the watch next row. Items that don't need to be touched are
	 * kept as is, so they keep their ordering in the watch next row.
	 */
	@SuppressLint("RestrictedApi")
	private fun deletePrograms(nextUpItems: List<BaseItemDto>) {
		// Retrieve current watch next row
		val currentWatchNextPrograms = getCurrentWatchNext()

		// Find all stale programs to delete
		val deletedByUser = currentWatchNextPrograms.filter { !it.isBrowsable }
		val noLongerInWatchNext =
			currentWatchNextPrograms.filter { (nextUpItems).none { next -> it.internalProviderId == next.id.toString() } }
		val continueWatching = currentWatchNextPrograms.filter { it.watchNextType == WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE }

		// Delete the programs
		(deletedByUser + noLongerInWatchNext + continueWatching)
			.forEach { context.contentResolver.delete(TvContractCompat.buildWatchNextProgramUri(it.id), null, null) }
	}

	/**
	 * Retrieves the current watch next row state.
	 */
	@SuppressLint("RestrictedApi")
	private fun getCurrentWatchNext(): MutableList<WatchNextProgram> {
		val currentWatchNextPrograms: MutableList<WatchNextProgram> = mutableListOf()
		context.contentResolver.query(WatchNextPrograms.CONTENT_URI, WatchNextProgram.PROJECTION, null, null, null)
			.use { cursor ->
				if (cursor != null && cursor.moveToFirst()) {
					do {
						currentWatchNextPrograms.add(WatchNextProgram.fromCursor(cursor))
					} while (cursor.moveToNext())
				}
			}
		return currentWatchNextPrograms
	}

	/**
	 * Convert [BaseItemDto] to [WatchNextProgram]. Assumes the item type is "episode".
	 */
	@Suppress("RestrictedApi")
	private fun getBaseItemAsWatchNextProgram(item: BaseItemDto) =
		WatchNextProgram.Builder().apply {
			val preferParentThumb = userPreferences[UserPreferences.seriesThumbnailsEnabled]

			setInternalProviderId(item.id.toString())

			// Poster size & type
			if (item.type == BaseItemKind.EPISODE) {
				setType(WatchNextPrograms.TYPE_TV_EPISODE)
				setPosterArtAspectRatio(WatchNextPrograms.ASPECT_RATIO_16_9)
			} else if (item.type == BaseItemKind.MOVIE) {
				setType(WatchNextPrograms.TYPE_MOVIE)
				setPosterArtAspectRatio(WatchNextPrograms.ASPECT_RATIO_MOVIE_POSTER)
			}

			// Name and episode details
			if (item.seriesName != null) {
				setTitle(item.seriesName)
				setEpisodeTitle(item.name)

				item.indexNumber?.takeIf { it > 0 }?.let { setEpisodeNumber(it) }
				item.parentIndexNumber?.takeIf { it > 0 }?.let { setSeasonNumber(it) }
			} else {
				setTitle(item.name)
			}

			setDescription(item.overview?.stripHtml())

			// Poster
			setPosterArtUri(item.getPosterArtImageUrl(preferParentThumb))

			when {
				// User has started playing the episode
				(item.userData?.playbackPositionTicks ?: 0) > 0 -> {
					setWatchNextType(WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
					setLastPlaybackPositionMillis(item.userData!!.playbackPositionTicks.ticks.inWholeMilliseconds.toInt())
					// Use last played date to prioritize

					setLastEngagementTimeUtcMillis(
						item.userData?.lastPlayedDate?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
							?: Instant.now().toEpochMilli()
					)
				}
				// First episode of the season
				item.indexNumber == 1 -> {
					setWatchNextType(WatchNextPrograms.WATCH_NEXT_TYPE_NEW)
					setLastEngagementTimeUtcMillis(
						item.dateCreated?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
							?: Instant.now().toEpochMilli()
					)
				}
				// Default
				else -> {
					setWatchNextType(WatchNextPrograms.WATCH_NEXT_TYPE_NEXT)
					setLastEngagementTimeUtcMillis(Instant.now().toEpochMilli())
				}
			}

			// Runtime has been determined
			item.runTimeTicks?.ticks?.let { setDurationMillis(it.inWholeMilliseconds.toInt()) }

			// Set intent to open the episode
			setIntent(Intent(context, StartupActivity::class.java).apply {
				putExtra(StartupActivity.EXTRA_ITEM_ID, item.id.toString())
			})
		}.build()
}
