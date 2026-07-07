package org.jellyfin.androidtv.data.model

import android.content.Context
import org.jellyfin.androidtv.R
import org.jellyfin.sdk.model.api.BaseItemDto

data class IncompleteSeriesProgress(
	val lastWatchedEpisode: BaseItemDto?,
	val watchedCount: Int,
	val unwatchedCount: Int,
	val totalCount: Int,
) {
	fun isIncompleteStarted() = watchedCount > 0 && unwatchedCount > 0

	fun summary(context: Context): String {
		val episodeLabel = lastWatchedEpisode?.let(::formatEpisodeLabel)

		return when {
			episodeLabel != null -> context.resources.getQuantityString(
				R.plurals.incomplete_series_progress_with_last,
				unwatchedCount,
				episodeLabel,
				unwatchedCount,
				totalCount,
			)
			else -> context.resources.getQuantityString(
				R.plurals.incomplete_series_progress,
				unwatchedCount,
				unwatchedCount,
				totalCount,
			)
		}
	}

	private fun formatEpisodeLabel(episode: BaseItemDto): String {
		val season = episode.parentIndexNumber
		val episodeNumber = episode.indexNumber
		val numberLabel = when {
			season != null && episodeNumber != null -> "S%02dE%02d".format(season, episodeNumber)
			episodeNumber != null -> "E%02d".format(episodeNumber)
			else -> null
		}

		return listOfNotNull(numberLabel, episode.name?.takeIf { it.isNotBlank() }).joinToString(" - ")
	}
}
