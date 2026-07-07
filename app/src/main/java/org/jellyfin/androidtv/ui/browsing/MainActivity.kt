package org.jellyfin.androidtv.ui.browsing

import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.integration.LeanbackChannelWorker
import org.jellyfin.androidtv.ui.InteractionTrackerViewModel
import org.jellyfin.androidtv.ui.background.AppBackground
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.ProvideLocalInteractionTracker
import org.jellyfin.androidtv.ui.composable.compat.AppNavigationHost
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.screensaver.InAppScreensaver
import org.jellyfin.androidtv.ui.settings.compat.MainActivitySettings
import org.jellyfin.androidtv.ui.startup.StartupActivity
import org.jellyfin.androidtv.update.ApkUpdateManager
import org.jellyfin.androidtv.util.applyTheme
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import timber.log.Timber

class MainActivity : FragmentActivity() {
	private val exitPromptHandler = Handler(Looper.getMainLooper())
	private var exitPromptDialog: AlertDialog? = null
	private var exitPromptUntil = 0L
	private val dismissExitPromptRunnable = Runnable {
		exitPromptDialog?.dismiss()
		exitPromptUntil = 0L
	}
	private val navigationRepository by inject<NavigationRepository>()
	private val sessionRepository by inject<SessionRepository>()
	private val userRepository by inject<UserRepository>()
	private val interactionTrackerViewModel by viewModel<InteractionTrackerViewModel>()
	private val workManager by inject<WorkManager>()

	override fun onCreate(savedInstanceState: Bundle?) {
		applyTheme()

		super.onCreate(savedInstanceState)

		if (!validateAuthentication()) return

		ApkUpdateManager(this).checkForUpdates()

		interactionTrackerViewModel.keepScreenOn.flowWithLifecycle(lifecycle, Lifecycle.State.RESUMED)
			.onEach { keepScreenOn ->
				if (keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
				else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
			}.launchIn(lifecycleScope)

		if (savedInstanceState == null && navigationRepository.canGoBack) navigationRepository.reset(clearHistory = true)

		navigationRepository.currentAction
			.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
			.onEach {
				interactionTrackerViewModel.notifyInteraction(canCancel = false, userInitiated = false)
			}.launchIn(lifecycleScope)

		setContent {
			JellyfinTheme {
				ProvideLocalInteractionTracker(
					interactionTracker = { interactionTrackerViewModel.notifyInteraction(false, userInitiated = true) }
				) {
					AppBackground()
					AppNavigationHost(
						navigationRepository = navigationRepository,
					)
					InAppScreensaver()
					MainActivitySettings()
				}
			}
		}
	}

	override fun onResume() {
		super.onResume()

		if (!validateAuthentication()) return

		applyTheme()

		interactionTrackerViewModel.activityPaused = false
	}

	private fun validateAuthentication(): Boolean {
		if (sessionRepository.currentSession.value == null || userRepository.currentUser.value == null) {
			Timber.w("Activity ${this::class.qualifiedName} started without a session, bouncing to StartupActivity")
			startActivity(Intent(this, StartupActivity::class.java))
			finish()
			return false
		}

		return true
	}

	override fun onPause() {
		super.onPause()

		interactionTrackerViewModel.activityPaused = true
		dismissExitPrompt()
	}

	private fun dismissExitPrompt() {
		exitPromptHandler.removeCallbacks(dismissExitPromptRunnable)
		exitPromptDialog?.dismiss()
		exitPromptDialog = null
		exitPromptUntil = 0L
	}

	private fun handleBackToExit(): Boolean {
		if (navigationRepository.canGoBack || navigationRepository.currentFragment != Destinations.home) return false

		val now = SystemClock.uptimeMillis()
		if (now <= exitPromptUntil && exitPromptDialog?.isShowing == true) {
			dismissExitPrompt()
			finishAfterTransition()
			return true
		}

		exitPromptUntil = now + EXIT_PROMPT_TIMEOUT_MS
		exitPromptDialog?.dismiss()
		exitPromptDialog = AlertDialog.Builder(this)
			.setTitle(R.string.lbl_exit)
			.setMessage(R.string.msg_press_back_twice_to_exit)
			.setOnKeyListener { _: DialogInterface, keyCode: Int, event: KeyEvent ->
				if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
					dismissExitPrompt()
					finishAfterTransition()
					true
				} else {
					false
				}
			}
			.setOnDismissListener {
				exitPromptHandler.removeCallbacks(dismissExitPromptRunnable)
				if (SystemClock.uptimeMillis() > exitPromptUntil) exitPromptUntil = 0L
			}
			.create()

		exitPromptDialog?.show()
		exitPromptHandler.removeCallbacks(dismissExitPromptRunnable)
		exitPromptHandler.postDelayed(dismissExitPromptRunnable, EXIT_PROMPT_TIMEOUT_MS)
		return true
	}

	override fun onStop() {
		super.onStop()

		workManager.enqueue(OneTimeWorkRequestBuilder<LeanbackChannelWorker>().build())

		lifecycleScope.launch(Dispatchers.IO) {
			Timber.i("MainActivity stopped")
			sessionRepository.restoreSession(destroyOnly = true)
		}
	}

	// Forward key events to fragments

	private fun Fragment.onKeyEvent(keyCode: Int, event: KeyEvent?): Boolean {
		var result = childFragmentManager.fragments.any { it.onKeyEvent(keyCode, event) }
		if (!result && this is View.OnKeyListener) result = onKey(currentFocus, keyCode, event)
		return result
	}

	private fun onKeyEvent(keyCode: Int, event: KeyEvent?): Boolean = supportFragmentManager.fragments
		.any { it.onKeyEvent(keyCode, event) }

	override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean =
		onKeyEvent(keyCode, event) || super.onKeyDown(keyCode, event)

	override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
		if ((keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) && event?.action == KeyEvent.ACTION_UP) {
			if (navigationRepository.canGoBack) {
				dismissExitPrompt()
				navigationRepository.goBack()
				return true
			}

			if (handleBackToExit()) return true
		}

		return onKeyEvent(keyCode, event) || super.onKeyUp(keyCode, event)
	}

	@Deprecated("Deprecated in AndroidX Activity, still used as a safety net for TV remotes")
	override fun onBackPressed() {
		if (navigationRepository.canGoBack) {
			dismissExitPrompt()
			navigationRepository.goBack()
			return
		}

		if (!handleBackToExit()) super.onBackPressed()
	}

	override fun onKeyLongPress(keyCode: Int, event: KeyEvent?): Boolean =
		onKeyEvent(keyCode, event) || super.onKeyLongPress(keyCode, event)

	companion object {
		private const val EXIT_PROMPT_TIMEOUT_MS = 5_000L
	}
}
