package com.mari.aksa.utils

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

fun Bitmap.saveToCache(context: Context, name: String = "aksa_frame.jpg"): Uri {
    val file = File(context.cacheDir, name)
    FileOutputStream(file).use { out -> this.compress(Bitmap.CompressFormat.JPEG, 95, out) }
    return Uri.fromFile(file)
}

@SuppressLint("UseKtx")
fun Bitmap.resizeTo(size: Int): Bitmap =
    if (width == size && height == size) this
    else Bitmap.createScaledBitmap(this, size, size, true)
