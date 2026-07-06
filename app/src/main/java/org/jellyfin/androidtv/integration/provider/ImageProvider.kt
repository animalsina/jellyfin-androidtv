package org.jellyfin.androidtv.integration.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import coil3.ImageLoader
import coil3.asDrawable
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.error
import org.jellyfin.androidtv.BuildConfig
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.util.AndroidVersion
import org.koin.android.ext.android.inject
import timber.log.Timber
import java.io.IOException

class ImageProvider : ContentProvider() {
	private val imageLoader by inject<ImageLoader>()

	override fun onCreate(): Boolean = true

	override fun getType(uri: Uri) = null
	override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?) = null
	override fun insert(uri: Uri, values: ContentValues?) = null
	override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
	override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0

	override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
		val src = uri.getQueryParameter("src")?.toUri() ?: return null

		val pipe = try {
			ParcelFileDescriptor.createPipe()
		} catch (e: IOException) {
			Timber.e(e, "Could not create pipe for ImageProvider")
			return null
		}

		val (read, write) = pipe
		val outputStream = ParcelFileDescriptor.AutoCloseOutputStream(write)

		val ctx = context ?: return null

		try {
			imageLoader.enqueue(
				ImageRequest.Builder(ctx)
					.data(src)
					.diskCachePolicy(CachePolicy.ENABLED)
					.memoryCachePolicy(CachePolicy.ENABLED)
					.error(R.drawable.placeholder_icon)
					.target(
						onSuccess = { image ->
							writeDrawable(image.asDrawable(ctx.resources), outputStream)
						},
						onError = { image ->
							val fallback = image?.asDrawable(ctx.resources)
								?: AppCompatResources.getDrawable(ctx, R.drawable.placeholder_icon)!!
							writeDrawable(fallback, outputStream)
						}
					)
					.build()
			)
		} catch (e: Exception) {
			Timber.e(e, "Error enqueuing image request in ImageProvider")
			try { outputStream.close() } catch (_: Exception) {}
		}

		return read
	}

	private fun writeDrawable(
		drawable: Drawable,
		outputStream: ParcelFileDescriptor.AutoCloseOutputStream
	) {
		@Suppress("DEPRECATION")
		val format = when {
			AndroidVersion.isAtLeastR -> Bitmap.CompressFormat.WEBP_LOSSY
			else -> Bitmap.CompressFormat.JPEG
		}

		try {
			outputStream.use {
				val bitmap = drawable.toBitmap().let { bmp ->
					// Assicuriamoci che il bitmap sia valido per la compressione
					if (bmp.width > 0 && bmp.height > 0) bmp
					else null
				}
				bitmap?.compress(format, COMPRESSION_QUALITY, it)
			}
		} catch (e: Exception) {
			Timber.w("Error writing drawable to pipe: ${e.message}")
			// Non rilanciare l'eccezione per evitare crash del provider chiamato dal sistema
		}
	}

	companion object {
		private const val COMPRESSION_QUALITY = 80

		/**
		 * Restituisce un [Uri] che usa l'[ImageProvider] per caricare un’immagine.
		 * L’input deve essere una URL Jellyfin valida.
		 */
		fun getImageUri(src: String): Uri = Uri.Builder()
			.scheme("content")
			.authority("${BuildConfig.APPLICATION_ID}.integration.provider.ImageProvider")
			.appendQueryParameter("src", src)
			.appendQueryParameter("v", BuildConfig.VERSION_NAME)
			.build()
	}
}
