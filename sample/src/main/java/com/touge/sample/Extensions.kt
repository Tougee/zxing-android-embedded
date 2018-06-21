package com.touge.sample

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import org.jetbrains.anko.dip
import java.io.File

@Suppress("DEPRECATION")
fun Context.vibrate(pattern: LongArray) {
    if (Build.VERSION.SDK_INT >= 26) {
        (getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).vibrate(VibrationEffect.createWaveform(pattern, -1))
    } else {
        (getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).vibrate(pattern, -1)
    }
}

fun Context.navigationBarHeight(): Int {
    val resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
    if (resourceId > 0) {
        return resources.getDimensionPixelSize(resourceId)
    }
    return dip(24f)
}

/**
 * Creates a media file in the `Environment.DIRECTORY_PICTURES` directory. The directory
 * is persistent and available to other applications like gallery.
 *
 * @return A file object pointing to the newly created file.
 */
fun getOutputMediaFile(): File? {
    // To be safe, you should check that the SDCard is mounted
    // using Environment.getExternalStorageState() before doing this.
    if (Environment.getExternalStorageState() != Environment.MEDIA_MOUNTED) {
        return null
    }

    val mediaStorageDir = File(Environment.getExternalStoragePublicDirectory(
        Environment.DIRECTORY_PICTURES), "CameraSample")
    // This location works best if you want the created images to be shared
    // between applications and persist after your app has been uninstalled.

    // Create the storage directory if it does not exist
    if (!mediaStorageDir.exists()) {
        if (!mediaStorageDir.mkdirs()) {
            Log.d("CameraSample", "failed to create directory")
            return null
        }
    }

    // Create a media file name
    val mediaFile: File
        mediaFile = File(mediaStorageDir.path + File.separator +
            "VID" + ".mp4")
    mediaFile.delete()
    mediaFile.createNewFile()
    return mediaFile
}