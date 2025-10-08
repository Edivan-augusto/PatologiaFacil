package com.hitsu.patologiafacil.util
import android.Manifest
import android.os.Build
object PermissionUtils {
    fun imagePermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.READ_MEDIA_IMAGES) else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}
