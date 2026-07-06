package org.jellyfin.androidtv.ui.playback

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import org.jellyfin.androidtv.R
import timber.log.Timber

class TrailerWebViewActivity : FragmentActivity() {
	private lateinit var webView: WebView
	private lateinit var fullscreenContainer: FrameLayout
	private var customView: View? = null
	private var customViewCallback: WebChromeClient.CustomViewCallback? = null
	private var originalUrl: String = ""

	@SuppressLint("SetJavaScriptEnabled")
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
		window.addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
		setContentView(R.layout.activity_trailer_webview)

		originalUrl = intent.getStringExtra(EXTRA_URL).orEmpty()
		if (originalUrl.isBlank()) {
			finish()
			return
		}

		fullscreenContainer = findViewById(R.id.trailer_fullscreen_container)
		webView = findViewById(R.id.trailer_webview_fullscreen)
		configureWebView()

		val videoId = extractYouTubeVideoId(originalUrl)
		if (videoId != null) loadYoutubeInsideSuperJelly(videoId) else webView.loadUrl(originalUrl)
	}

	@SuppressLint("SetJavaScriptEnabled")
	private fun configureWebView() {
		CookieManager.getInstance().setAcceptCookie(true)
		CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
		webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
		webView.settings.javaScriptEnabled = true
		webView.settings.domStorageEnabled = true
		webView.settings.databaseEnabled = true
		webView.settings.mediaPlaybackRequiresUserGesture = false
		webView.settings.loadWithOverviewMode = true
		webView.settings.useWideViewPort = true
		webView.settings.loadsImagesAutomatically = true
		webView.settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
		webView.settings.setSupportMultipleWindows(false)
		webView.settings.userAgentString =
			"Mozilla/5.0 (Linux; Android 13; Android TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 SuperJellyTV"
		webView.webChromeClient = object : WebChromeClient() {
			override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
				if (view == null) return
				if (customView != null) {
					callback?.onCustomViewHidden()
					return
				}
				customView = view
				customViewCallback = callback
				fullscreenContainer.addView(
					view,
					FrameLayout.LayoutParams(
						FrameLayout.LayoutParams.MATCH_PARENT,
						FrameLayout.LayoutParams.MATCH_PARENT,
					)
				)
				fullscreenContainer.visibility = View.VISIBLE
				webView.visibility = View.GONE
			}

			override fun onHideCustomView() {
				exitCustomView()
			}
		}
		webView.webViewClient = object : WebViewClient() {
			override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
				val url = request?.url?.toString().orEmpty()
				if (url.isBlank()) return false
				// Keep iframe-internal navigation inside the SuperJelly layer. YouTube may open
				// consent/login/help pages as part of the embedded player; do not bounce out.
				return false
			}

			override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
				super.onReceivedError(view, request, error)
				if (request?.isForMainFrame == false) return
				Timber.w("Trailer WebView error: ${error?.description}")
				Toast.makeText(this@TrailerWebViewActivity, R.string.msg_video_playback_error, Toast.LENGTH_LONG).show()
			}
		}
	}

	private fun loadYoutubeInsideSuperJelly(videoId: String) {
		// Do not load the regular /watch page: on Android TV WebView it can show the false
		// "Android 4.0+ required" page. Moonfin-style playback uses an iframe document instead.
		val html = buildYoutubeEmbedHtml(videoId)
		webView.loadDataWithBaseURL(YOUTUBE_EMBED_BASE_URL, html, "text/html", "UTF-8", null)
	}

	private fun buildYoutubeEmbedHtml(videoId: String): String {
		val src = "$YOUTUBE_EMBED_BASE_URL/embed/$videoId?autoplay=1&controls=1&rel=0&modestbranding=1&playsinline=1&enablejsapi=1&origin=https%3A%2F%2Fwww.youtube-nocookie.com"
		return """
			<!doctype html>
			<html>
			<head>
				<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0">
				<style>
					html,body{margin:0;padding:0;width:100%;height:100%;overflow:hidden;background:#000;}
					iframe{position:absolute;inset:0;width:100%;height:100%;border:0;background:#000;}
				</style>
			</head>
			<body>
				<iframe src="$src" allow="autoplay; encrypted-media; fullscreen; picture-in-picture" allowfullscreen></iframe>
			</body>
			</html>
		""".trimIndent()
	}

	override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
		if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
			if (customView != null) {
				exitCustomView()
				return true
			}
			finish()
			return true
		}
		return super.onKeyUp(keyCode, event)
	}

	override fun onDestroy() {
		exitCustomView()
		if (::webView.isInitialized) {
			webView.stopLoading()
			webView.loadUrl("about:blank")
			webView.destroy()
		}
		super.onDestroy()
	}

	private fun exitCustomView() {
		val view = customView ?: return
		fullscreenContainer.removeView(view)
		fullscreenContainer.visibility = View.GONE
		webView.visibility = View.VISIBLE
		customView = null
		customViewCallback?.onCustomViewHidden()
		customViewCallback = null
	}

	private fun extractYouTubeVideoId(url: String): String? = YOUTUBE_ID_REGEX.find(url)?.groups?.get(1)?.value

	companion object {
		const val EXTRA_URL = "url"
		private val YOUTUBE_ID_REGEX = """(?:v=|/embed/|youtu\.be/|/shorts/)([a-zA-Z0-9_-]{11})""".toRegex()
		private const val YOUTUBE_EMBED_BASE_URL = "https://www.youtube-nocookie.com"
	}
}
