package org.jellyfin.androidtv.ui.playback

import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.fragment.app.FragmentActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import org.jellyfin.androidtv.R
import timber.log.Timber

/**
 * Lightweight internal player for free provider streams (HLS/MP4) that are not Jellyfin server items.
 * It intentionally does not report playback progress to the Jellyfin server.
 */
class ExternalStreamPlayerActivity : FragmentActivity() {
	private var player: ExoPlayer? = null
	private lateinit var playerView: PlayerView

	@OptIn(UnstableApi::class)
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
		setContentView(R.layout.activity_external_stream_player)
		playerView = findViewById(R.id.external_stream_player_view)

		val url = intent.getStringExtra(EXTRA_URL).orEmpty()
		val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
		if (url.isBlank()) {
			Toast.makeText(this, R.string.msg_external_catalog_unavailable, Toast.LENGTH_SHORT).show()
			finish()
			return
		}

		player = ExoPlayer.Builder(this).build().also { exoPlayer ->
			playerView.player = exoPlayer
			exoPlayer.setMediaItem(
				MediaItem.Builder()
					.setUri(Uri.parse(url))
					.setMediaMetadata(androidx.media3.common.MediaMetadata.Builder().setTitle(title).build())
					.build()
			)
			exoPlayer.addListener(object : Player.Listener {
				override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
					Timber.w(error, "External stream failed")
					Toast.makeText(this@ExternalStreamPlayerActivity, R.string.msg_external_catalog_unavailable, Toast.LENGTH_SHORT).show()
				}
			})
			exoPlayer.prepare()
			exoPlayer.playWhenReady = true
		}
	}

	override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
		if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
			finish()
			return true
		}
		return super.onKeyUp(keyCode, event)
	}

	override fun onStop() {
		super.onStop()
		player?.playWhenReady = false
	}

	override fun onDestroy() {
		playerView.player = null
		player?.release()
		player = null
		super.onDestroy()
	}

	companion object {
		const val EXTRA_URL = "url"
		const val EXTRA_TITLE = "title"
	}
}
