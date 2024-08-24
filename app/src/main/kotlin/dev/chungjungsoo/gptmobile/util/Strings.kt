package dev.chungjungsoo.gptmobile.util

import android.util.Patterns
import android.webkit.URLUtil

fun String.isValidUrl(): Boolean = URLUtil.isValidUrl(this) && Patterns.WEB_URL.matcher(this).matches()
