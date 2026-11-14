package com.mrsep.musicrecognizer.presentation

import android.app.AlertDialog
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.content.pm.ShortcutManagerCompat
import com.mrsep.musicrecognizer.MusicRecognizerApp
import com.mrsep.musicrecognizer.core.ui.isDarkUiMode

/**
 * Transparent activity that lets launchers and automation apps (e.g. key mappers)
 * pick one of the app shortcuts to pin or trigger it on their own.
 */
class CreateShortcutActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val shortcuts = (application as MusicRecognizerApp).getShortcuts()
        val labels = shortcuts.map { it.longLabel ?: it.shortLabel }.toTypedArray()
        AlertDialog.Builder(this, getDialogTheme())
            .setItems(labels) { _, index ->
                setResult(
                    RESULT_OK,
                    ShortcutManagerCompat.createShortcutResultIntent(this, shortcuts[index])
                )
            }
            .setOnDismissListener { finish() }
            .create()
            .show()
    }

    private fun getDialogTheme(): Int {
        return if (isDarkUiMode()) {
            android.R.style.Theme_DeviceDefault_Dialog_Alert
        } else {
            android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
        }
    }
}
