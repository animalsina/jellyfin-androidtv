package org.jellyfin.androidtv.update

import android.app.AlertDialog
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.getSystemService
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.BuildConfig
import org.jellyfin.androidtv.R
import timber.log.Timber
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Lightweight APK updater for sideloaded SuperJelly/Jellyfin Android TV builds.
 *
 * The updater intentionally does not install silently. It only discovers newer APKs from a
 * trusted folder, asks the user, downloads the APK and opens the normal Android installer.
 */
class ApkUpdateManager(private val activity: FragmentActivity) {
	private val preferences = activity.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

	fun checkForUpdates() {
		if (checkedThisProcess) return
		checkedThisProcess = true

		activity.lifecycleScope.launch {
			val update = withContext(Dispatchers.IO) { findLatestUpdate() }
			if (update == null || activity.isFinishing || activity.isDestroyed) return@launch

			val dismissedVersion = preferences.getString(KEY_DISMISSED_VERSION, null)
			if (dismissedVersion == update.version) return@launch

			showUpdateDialog(update)
		}
	}

	private fun showUpdateDialog(update: RemoteApk) {
		AlertDialog.Builder(activity)
			.setTitle(activity.getString(R.string.apk_update_available_title))
			.setMessage(
				activity.getString(
					R.string.apk_update_available_message,
					BuildConfig.VERSION_NAME,
					update.version,
					update.fileName
				)
			)
			.setPositiveButton(R.string.apk_update_install) { _, _ -> prepareDownload(update) }
			.setNegativeButton(R.string.apk_update_later) { _, _ ->
				preferences.edit().putString(KEY_DISMISSED_VERSION, update.version).apply()
			}
			.show()
	}

	private fun prepareDownload(update: RemoteApk) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.packageManager.canRequestPackageInstalls()) {
			AlertDialog.Builder(activity)
				.setTitle(activity.getString(R.string.apk_update_permission_title))
				.setMessage(R.string.apk_update_permission_message)
				.setPositiveButton(R.string.apk_update_open_settings) { _, _ -> openInstallPermissionSettings() }
				.setNegativeButton(android.R.string.cancel, null)
				.show()
			return
		}

		activity.lifecycleScope.launch {
			val apkFile = withContext(Dispatchers.IO) { downloadApk(update) }
			if (apkFile == null || activity.isFinishing || activity.isDestroyed) {
				showDownloadFailed()
				return@launch
			}

			openInstaller(apkFile)
		}
	}

	private fun openInstallPermissionSettings() {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

		val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
			data = Uri.parse("package:${activity.packageName}")
		}

		try {
			activity.startActivity(intent)
		} catch (error: ActivityNotFoundException) {
			Timber.w(error, "Unable to open APK install permission settings")
		}
	}

	private fun openInstaller(apkFile: File) {
		val uri = FileProvider.getUriForFile(
			activity,
			"${BuildConfig.APPLICATION_ID}.apkupdate.fileprovider",
			apkFile
		)

		val installIntent = Intent(Intent.ACTION_VIEW).apply {
			setDataAndType(uri, APK_MIME_TYPE)
			addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
			addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
		}

		try {
			activity.startActivity(installIntent)
		} catch (error: ActivityNotFoundException) {
			Timber.e(error, "Unable to open APK installer")
			showDownloadFailed()
		}
	}

	private fun showDownloadFailed() {
		AlertDialog.Builder(activity)
			.setTitle(R.string.apk_update_failed_title)
			.setMessage(R.string.apk_update_failed_message)
			.setPositiveButton(android.R.string.ok, null)
			.show()
	}

	private fun findLatestUpdate(): RemoteApk? {
		return runCatching {
			val folderUrl = DEFAULT_UPDATE_FOLDER_URL.ensureTrailingSlash()
			val connection = (URL(folderUrl).openConnection() as HttpURLConnection).apply {
				connectTimeout = NETWORK_TIMEOUT_MS
				readTimeout = NETWORK_TIMEOUT_MS
				requestMethod = "GET"
			}

			connection.inputStream.bufferedReader().use { reader ->
				val body = reader.readText()
				val current = Version.parse(BuildConfig.VERSION_NAME)
				APK_FILE_REGEX.findAll(body)
					.mapNotNull { match ->
						val fileName = match.value.substringAfterLast('/')
						val version = match.groupValues.getOrNull(1).orEmpty()
						if (!fileName.endsWith(".apk", ignoreCase = true)) return@mapNotNull null
						RemoteApk(
							version = version,
							fileName = fileName,
							url = folderUrl + fileName
						)
					}
					.filter { Version.parse(it.version) > current }
					.maxByOrNull { Version.parse(it.version) }
			}
		}.onFailure { error ->
			Timber.w(error, "Unable to check APK updates")
		}.getOrNull()
	}

	private fun downloadApk(update: RemoteApk): File? {
		return runCatching {
			val updateDirectory = File(activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: activity.cacheDir, "apk-updates")
			updateDirectory.mkdirs()
			updateDirectory.listFiles { file -> file.name.endsWith(".apk", ignoreCase = true) }?.forEach { it.delete() }

			val target = File(updateDirectory, update.fileName)
			val connection = (URL(update.url).openConnection() as HttpURLConnection).apply {
				connectTimeout = NETWORK_TIMEOUT_MS
				readTimeout = DOWNLOAD_TIMEOUT_MS
				requestMethod = "GET"
			}

			connection.inputStream.use { input ->
				target.outputStream().use { output -> input.copyTo(output) }
			}

			if (!target.exists() || target.length() <= 0L) error("Downloaded APK is empty")

			val downloadManager = activity.getSystemService<DownloadManager>()
			downloadManager?.addCompletedDownload(
				update.fileName,
				activity.getString(R.string.apk_update_download_complete_description),
				true,
				APK_MIME_TYPE,
				target.absolutePath,
				target.length(),
				true
			)

			target
		}.onFailure { error ->
			Timber.e(error, "Unable to download APK update ${update.url}")
		}.getOrNull()
	}

	private fun String.ensureTrailingSlash() = if (endsWith('/')) this else "$this/"

	private data class RemoteApk(
		val version: String,
		val fileName: String,
		val url: String,
	)

	private data class Version(
		val major: Int,
		val minor: Int,
		val patch: Int,
	) : Comparable<Version> {
		override fun compareTo(other: Version): Int = compareValuesBy(this, other, Version::major, Version::minor, Version::patch)

		companion object {
			fun parse(raw: String): Version {
				val cleaned = raw.lowercase(Locale.ROOT).removePrefix("v").substringBefore('-')
				val parts = cleaned.split('.').mapNotNull { it.toIntOrNull() }
				return Version(
					major = parts.getOrElse(0) { 0 },
					minor = parts.getOrElse(1) { 0 },
					patch = parts.getOrElse(2) { 0 },
				)
			}
		}
	}

	companion object {
		private const val DEFAULT_UPDATE_FOLDER_URL = "https://files.animalsina.work/jellyfin/android-tv/"
		private const val PREFERENCES_NAME = "apk_update_preferences"
		private const val KEY_DISMISSED_VERSION = "dismissed_version"
		private const val NETWORK_TIMEOUT_MS = 8_000
		private const val DOWNLOAD_TIMEOUT_MS = 30_000
		private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
		private val APK_FILE_REGEX = Regex("jellyfin-androidtv-v([0-9]+\\.[0-9]+\\.[0-9]+)-debug\\.apk", RegexOption.IGNORE_CASE)
		private var checkedThisProcess = false
	}
}
