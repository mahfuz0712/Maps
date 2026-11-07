package com.mux.android.maps.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import com.mux.android.maps.data.Place

fun toast(ctx: Context, msg: String) {
    Toast.makeText(ctx.applicationContext, msg, Toast.LENGTH_SHORT).show()
}

fun copyText(ctx: Context, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("text", text))
    toast(ctx, "COPIED")
}

fun openAppSettings(ctx: Context) {
    try {
        ctx.startActivity(
            Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: Exception) {
    }
}

fun openSettingsAction(ctx: Context, action: String) {
    try {
        ctx.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        toast(ctx, "OPEN ANDROID SETTINGS MANUALLY")
    }
}

fun osmLink(lat: Double, lon: Double): String =
    "https://www.openstreetmap.org/?mlat=$lat&mlon=$lon#map=17/$lat/$lon"

fun sharePlace(ctx: Context, p: Place) {
    val text = buildString {
        append(p.name)
        if (p.address.isNotEmpty()) append("\n").append(p.address)
        append("\n").append(osmLink(p.lat, p.lon))
    }
    try {
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        ctx.startActivity(Intent.createChooser(i, "Share place").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
    }
}

/** Opens the place in any other maps / navigation app that understands geo: links. */
fun openInOtherApp(ctx: Context, p: Place) {
    try {
        val uri = Uri.parse("geo:${p.lat},${p.lon}?q=${p.lat},${p.lon}(${Uri.encode(p.name)})")
        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW, uri), "Open with").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        toast(ctx, "NO OTHER APP FOUND")
    }
}

fun dial(ctx: Context, number: String) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
    }
}
