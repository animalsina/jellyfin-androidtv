package org.jellyfin.androidtv.ui.playback

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import org.jellyfin.androidtv.R
import timber.log.Timber

class TrailerWebViewActivity : FragmentActivity() {
	private lateinit var webView: WebView
	private var originalUrl: String = ""

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
		setContentView(R.layout.activity_trailer_webview)

		originalUrl = intent.getStringExtra(EXTRA_URL).orEmpty()
		if (originalUrl.isBlank()) {
			finish()
			return
		}

		webView = findViewById(R.id.trailer_webview_fullscreen)
		webView.settings.javaScriptEnabled = true
		webView.settings.domStorageEnabled = true
		webView.settings.mediaPlaybackRequiresUserGesture = false
		webView.settings.loadWithOverviewMode = true
		webView.settings.useWideViewPort = true
		webView.settings.userAgentString = "Mozilla/5.0 (SMART-TV; Linux; Tizen 6.0) AppleWebKit/537.36 SuperJellyTV"
		webView.webViewClient = object : WebViewClient() {
			override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
				super.onReceivedError(view, request, error)
				Timber.w("Trailer WebView error: ${error?.description}")
				openExternalFallback()
			}
		}

		val videoId = extractYouTubeVideoId(originalUrl)
		if (videoId != null) loadYoutubeEmbed(videoId) else webView.loadUrl(originalUrl)
	}

	private fun loadYoutubeEmbed(videoId: String) {
		val embedUrl = "https://www.youtube.com/embed/$videoId?autoplay=1&controls=1&rel=0&modestbranding=1&playsinline=1&enablejsapi=1"
		val html = """
			<html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"><style>
			html,body{margin:0;padding:0;height:100%;background:#000;overflow:hidden}iframe{width:100%;height:100%;border:0}
			</style></head><body><iframe src=\"$embedUrl\" allow=\"autoplay; encrypted-media; fullscreen\" allowfullscreen></iframe></body></html>
		""".trimIndent()
		webView.loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "utf-8", null)
	}

	private fun openExternalFallback() {
		val uri = Uri.parse(originalUrl)
		val packageCandidates = listOf(
			"com.liskovsoft.smarttubetv",
			"com.liskovsoft.smarttubetv.beta",
			"com.teamsmart.videomanager.tv",
			"com.google.android.youtube.tv",
			"com.google.android.youtube",
		)
		for (packageName in packageCandidates) {
			if (packageManager.getLaunchIntentForPackage(packageName) == null) continue
			runCatching {
				startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage(packageName))
				finish()
				return
			}
		}
		try {
			startActivity(Intent(Intent.ACTION_VIEW, uri))
		} catch (error: Exception) {
			Toast.makeText(this, R.string.no_player_message, Toast.LENGTH_LONG).show()
		}
		finish()
	}

	override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
		if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
			finish()
			return true
		}
		return super.onKeyUp(keyCode, event)
	}

	override fun onDestroy() {
		if (::webView.isInitialized) {
			webView.stopLoading()
			webView.loadUrl("about:blank")
		}
		super.onDestroy()
	}

	private fun extractYouTubeVideoId(url: String): String? = YOUTUBE_ID_REGEX.find(url)?.groups?.get(1)?.value

	companion object {
		const val EXTRA_URL = "url"
		private val YOUTUBE_ID_REGEX = """(?:v=|/embed/|youtu\.be/|/shorts/)([a-zA-Z0-9_-]{11})""".toRegex()
	}
}
