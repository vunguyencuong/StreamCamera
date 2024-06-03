package com.example.sticker_ex2

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder

fun appSettingOpen(context: Context){
    Toast.makeText(
        context,
        "Go to Setting and Enable All Permission",
        Toast.LENGTH_LONG
    ).show()

    val settingIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
    settingIntent.data = Uri.parse("package:${context.packageName}")
    context.startActivity(settingIntent)
}

fun warningPermissionDialog(context: Context, listener : DialogInterface.OnClickListener){
    MaterialAlertDialogBuilder(context)
        .setMessage("All Permission are Required for this app")
        .setCancelable(false)
        .setPositiveButton("Ok",listener)
        .create()
        .show()
}

fun View.visible(){
    visibility = View.VISIBLE
}

fun View.gone(){
    visibility = View.GONE
}

fun Activity.notifyNewFileCreate(
    path: String,
    popToMain: Boolean = true,
    callback: ((String) -> Unit?)? = null
) {
    MediaScannerConnection.scanFile(
        this, arrayOf(path), null
    ) { newPath, _ ->
        callback?.invoke(newPath)
        Log.d("check camera", "New file scanned and available at: $newPath")
    }
}