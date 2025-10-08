package com.hitsu.patologiafacil.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

object BitmapUtils {
    fun saveTempJpeg(context: Context, bmp: Bitmap, fileName: String = "pf_${System.currentTimeMillis()}.jpg"): Uri {
        return if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PatologiaFacil")
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
            resolver.openOutputStream(uri)?.use { out -> bmp.compress(Bitmap.CompressFormat.JPEG, 92, out) }
            uri
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "PatologiaFacil")
            dir.mkdirs()
            val file = File(dir, fileName)
            FileOutputStream(file).use { out -> bmp.compress(Bitmap.CompressFormat.JPEG, 92, out) }
            Uri.fromFile(file)
        }
    }
}
