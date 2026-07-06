package org.jellyfin.androidtv.ui.settings.screen.about

import android.content.ClipData
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import org.jellyfin.androidtv.BuildConfig
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.list.ListButton
import org.jellyfin.androidtv.ui.base.list.ListSection
import org.jellyfin.androidtv.ui.navigation.LocalRouter
import org.jellyfin.androidtv.ui.settings.Routes
import org.jellyfin.androidtv.ui.settings.composable.SettingsColumn
import org.jellyfin.androidtv.ui.settings.util.copyAction
import org.jellyfin.androidtv.update.ApkUpdateManager

@Composable
fun SettingsAboutScreen(launchedFromLogin: Boolean = false) {
	val router = LocalRouter.current
	val context = LocalContext.current

	SettingsColumn {
		if (launchedFromLogin) item {
			ListSection(
				overlineContent = { Text(stringResource(R.string.pref_login).uppercase()) },
				headingContent = { Text(stringResource(R.string.pref_about_title)) },
			)
		} else item {
			ListSection(
				overlineContent = { Text(stringResource(R.string.settings).uppercase()) },
				headingContent = { Text(stringResource(R.string.pref_about_title)) },
			)
		}

		item {
			val heading = "SuperJelly app version"
			val caption = "superjelly-androidtv ${BuildConfig.VERSION_NAME} ${BuildConfig.BUILD_TYPE}"
			ListButton(
				leadingContent = { Icon(painterResource(R.drawable.ic_jellyfin), contentDescription = null) },
				headingContent = { Text(heading) },
				captionContent = { Text(caption) },
				onClick = copyAction(ClipData.newPlainText(heading, caption)),
			)
		}

		item {
			ListButton(
				leadingContent = { Icon(painterResource(R.drawable.ic_update), contentDescription = null) },
				headingContent = { Text(stringResource(R.string.pref_check_apk_updates)) },
				captionContent = { Text(stringResource(R.string.pref_check_apk_updates_description)) },
				onClick = {
					val activity = context.findFragmentActivity()
					if (activity != null) ApkUpdateManager(activity).checkForUpdates(force = true)
					else Toast.makeText(context, R.string.apk_update_check_failed_title, Toast.LENGTH_SHORT).show()
				},
			)
		}

		item {
			val heading = stringResource(R.string.pref_device_model)
			val caption = "${Build.MANUFACTURER} ${Build.MODEL}"
			ListButton(
				leadingContent = { Icon(painterResource(R.drawable.ic_tv), contentDescription = null) },
				headingContent = { Text(heading) },
				captionContent = { Text(caption) },
				onClick = copyAction(ClipData.newPlainText(heading, caption)),
			)
		}

		item {
			ListButton(
				leadingContent = { Icon(painterResource(R.drawable.ic_guide), contentDescription = null) },
				headingContent = { Text(stringResource(R.string.licenses_link)) },
				onClick = { router.push(Routes.LICENSES) },
			)
		}

		if (!launchedFromLogin) item {
			ListButton(
				leadingContent = { Icon(painterResource(R.drawable.ic_flask), contentDescription = null) },
				headingContent = { Text(stringResource(R.string.pref_developer_link)) },
				onClick = { router.push(Routes.DEVELOPER) }
			)
		}
	}
}
private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
	is FragmentActivity -> this
	is ContextWrapper -> baseContext.findFragmentActivity()
	else -> null
}
