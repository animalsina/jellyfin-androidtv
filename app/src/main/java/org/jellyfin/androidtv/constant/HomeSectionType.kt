package org.jellyfin.androidtv.constant

import org.jellyfin.androidtv.R
import org.jellyfin.preference.PreferenceEnum

/**
 * All possible homesections, "synced" with jellyfin-web.
 *
 * https://github.com/jellyfin/jellyfin-web/blob/master/src/components/homesections/homesections.js
 */
enum class HomeSectionType(
	override val serializedName: String,
	override val nameRes: Int,
) : PreferenceEnum {
	LATEST_MEDIA("latestmedia", R.string.home_section_latest_media),
	LIBRARY_TILES_SMALL("smalllibrarytiles", R.string.home_section_library),
	LIBRARY_BUTTONS("librarybuttons", R.string.home_section_library_small),
	RESUME("resume", R.string.home_section_resume),
	RESUME_AUDIO("resumeaudio", R.string.home_section_resume_audio),
	RESUME_BOOK("resumebook", R.string.home_section_resume_book),
	ACTIVE_RECORDINGS("activerecordings", R.string.home_section_active_recordings),
	NEXT_UP("nextup", R.string.home_section_next_up),
	LIVE_TV("livetv", R.string.home_section_livetv),
	RECOMMENDED_FOR_YOU("recommendedforyou", R.string.home_section_recommended_for_you),
	TRENDING_THIS_WEEK("trendingthisweek", R.string.home_section_trending_this_week),
	RECENTLY_RELEASED("recentlyreleased", R.string.home_section_recently_released),
	POPULAR_MOVIES("popularmovies", R.string.home_section_popular_movies),
	POPULAR_TV("populartv", R.string.home_section_popular_tv),
	SIMILAR_TO_WATCHED("similartowatched", R.string.home_section_similar_to_watched),
	GENRE_RANDOM_MOVIES("genrerandommovies", R.string.home_section_genre_random_movies),
	GENRE_RANDOM_TV("genrerandomtv", R.string.home_section_genre_random_tv),
	GENRE_RANDOM_MIXED("genrerandommixed", R.string.home_section_genre_random_mixed),
	NONE("none", R.string.home_section_none),
}
