package com.mux.android.maps.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable

/** Marker pictures drawn in code (HUD colors), so no image files are needed. */
object Icons {
    private fun bmp(ctx: Context, dp: Int, draw: (Canvas, Float, Float) -> Unit): Drawable {
        val d = ctx.resources.displayMetrics.density
        val px = (dp * d).toInt().coerceAtLeast(8)
        val b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        draw(Canvas(b), px.toFloat(), d)
        return BitmapDrawable(ctx.resources, b)
    }

    private fun paint(color: Int, style: Paint.Style = Paint.Style.FILL, width: Float = 0f) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        this.style = style
        strokeWidth = width
    }

    /** Teardrop pin. Anchor: bottom center. */
    fun pin(ctx: Context, color: Int): Drawable = bmp(ctx, 44) { c, s, d ->
        val p = Path()
        val r = s * 0.30f
        val cx = s / 2
        val cy = s * 0.36f
        p.addCircle(cx, cy, r, Path.Direction.CW)
        p.moveTo(cx - r * 0.82f, cy + r * 0.58f)
        p.lineTo(cx, s * 0.96f)
        p.lineTo(cx + r * 0.82f, cy + r * 0.58f)
        p.close()
        c.drawPath(p, paint(0xFF040A12.toInt(), Paint.Style.STROKE, 5f * d))
        c.drawPath(p, paint(color))
        c.drawCircle(cx, cy, r * 0.42f, paint(0xFF040A12.toInt()))
    }

    /** Small round dot for category results. Anchor: center. */
    fun dot(ctx: Context, color: Int): Drawable = bmp(ctx, 22) { c, s, d ->
        c.drawCircle(s / 2, s / 2, s * 0.40f, paint(0xFF040A12.toInt()))
        c.drawCircle(s / 2, s / 2, s * 0.32f, paint(color))
        c.drawCircle(s / 2, s / 2, s * 0.12f, paint(0xFF040A12.toInt()))
    }

    /** "You are here": ring with a heading arrow. Anchor: center. Rotates with the marker. */
    fun me(ctx: Context, color: Int, arrow: Boolean): Drawable = bmp(ctx, 46) { c, s, d ->
        val cx = s / 2
        val cy = s / 2
        if (arrow) {
            val p = Path()
            p.moveTo(cx, s * 0.04f)
            p.lineTo(cx + s * 0.20f, cy - s * 0.02f)
            p.lineTo(cx - s * 0.20f, cy - s * 0.02f)
            p.close()
            c.drawPath(p, paint(color))
        }
        c.drawCircle(cx, cy, s * 0.24f, paint(0xFF040A12.toInt()))
        c.drawCircle(cx, cy, s * 0.19f, paint(color))
        c.drawCircle(cx, cy, s * 0.08f, paint(0xFFFFFFFF.toInt()))
    }

    /** Navigation arrow. Anchor: center. */
    fun navArrow(ctx: Context, color: Int): Drawable = bmp(ctx, 52) { c, s, d ->
        val p = Path()
        p.moveTo(s / 2, s * 0.06f)
        p.lineTo(s * 0.84f, s * 0.90f)
        p.lineTo(s / 2, s * 0.70f)
        p.lineTo(s * 0.16f, s * 0.90f)
        p.close()
        c.drawPath(p, paint(0xFF040A12.toInt(), Paint.Style.STROKE, 5f * d))
        c.drawPath(p, paint(color))
    }

    /** Ruler point. Anchor: center. */
    fun ruler(ctx: Context, color: Int): Drawable = bmp(ctx, 18) { c, s, d ->
        c.drawCircle(s / 2, s / 2, s * 0.42f, paint(0xFF040A12.toInt()))
        c.drawCircle(s / 2, s / 2, s * 0.30f, paint(color))
    }
}
