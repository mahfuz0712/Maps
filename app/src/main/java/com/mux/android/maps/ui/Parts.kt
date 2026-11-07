package com.mux.android.maps.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Small bordered label ("WPA2", "WPS ON", "CH 6" ...). */
@Composable
fun Tag(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(end = 4.dp, top = 3.dp)
            .border(1.dp, color.copy(alpha = 0.55f), HudShape(3.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp)
    ) {
        HudText(text, color = color, size = 10.sp, bold = true, maxLines = 1)
    }
}
