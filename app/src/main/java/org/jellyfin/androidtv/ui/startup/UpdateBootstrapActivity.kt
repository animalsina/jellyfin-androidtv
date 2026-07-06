package org.jellyfin.androidtv.ui.startup

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.update.ApkUpdateManager

/**
 * Ultra-lightweight entry point that guarantees APK update check before any heavy initialization.
 * This activity does NOT use Koin or complex data repositories to avoid early crashes.
 */
class UpdateBootstrapActivity : FragmentActivity() {
    private var proceeded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_update_bootstrap)

        val updateManager = ApkUpdateManager(this)

        // Startup is intentionally blocked only when a real update is found. If the user chooses
        // "later" or dismisses the dialog, we continue to the home flow. If no update exists or
        // the check fails, the app proceeds normally without forcing a restart.
        updateManager.checkForUpdates(force = false, blockStartup = true) {
            proceedToApp()
        }
    }

    private fun proceedToApp() {
        if (isFinishing || isDestroyed || proceeded) return
        proceeded = true

        val intent = Intent(this, StartupActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        finish()
    }
}
