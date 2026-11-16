package com.cristopbot.estadia.data

import android.content.Context

fun Context.googleWebClientId(): String? {
    val resourceId = resources.getIdentifier("default_web_client_id", "string", packageName)
    return resourceId.takeIf { it != 0 }
        ?.let { getString(it) }
        ?.takeIf(String::isNotBlank)
}
