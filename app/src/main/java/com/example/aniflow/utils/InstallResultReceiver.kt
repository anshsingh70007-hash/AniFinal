package com.example.aniflow.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast

/**
 * Receives the system's answer to an update committed through [PackageInstaller] (AppUpdater).
 * The system asks the user to confirm by handing us its own confirm screen to start; on success
 * the app is replaced and restarted, so there is nothing to do.
 */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm: Intent? = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                if (confirm == null) {
                    toast(context, "Update failed: the system didn't ask to confirm the install.")
                    return
                }
                try {
                    context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: Exception) {
                    toast(context, "Update failed: this device has no installer screen (${e.javaClass.simpleName}).")
                }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            PackageInstaller.STATUS_FAILURE_ABORTED -> Unit   // the user said no
            else -> {
                val why = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                toast(context, "Update wasn't installed (${why ?: "error $status"}).")
            }
        }
    }

    private fun toast(context: Context, msg: String) =
        Toast.makeText(context.applicationContext, msg, Toast.LENGTH_LONG).show()

    companion object {
        const val ACTION = "com.example.aniflow.INSTALL_RESULT"
    }
}
