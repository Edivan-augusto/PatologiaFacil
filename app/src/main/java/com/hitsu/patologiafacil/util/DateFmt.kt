package com.hitsu.patologiafacil.util
import java.text.SimpleDateFormat
import java.util.*
object DateFmt {
    fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
}
