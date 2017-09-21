package cc.binmt.fileexplorer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.support.graphics.drawable.VectorDrawableCompat


class IconFactory(private val context: Context) {
    private val scale = context.resources.displayMetrics.density
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        backgroundPaint.style = Paint.Style.FILL;
//        backgroundPaint.setShadowLayer(2.5f, 1.0f, 1.0f, 0x64000000);
    }

    fun makeIcon(backgroundColor: Int, iconId: Int, backgroundLen: Int = 48,
                 iconLen: Int = 22, padding: Int = 0): Bitmap {

        val rawBackgroundLen = toRawLenF(backgroundLen)
        val rawIconLen = toRawLen(iconLen)
        val rawPadding = toRawLenF(padding)
        val rawImageLen = (rawBackgroundLen + rawPadding + rawPadding).toInt()
        val image = Bitmap.createBitmap(rawImageLen, rawImageLen, Bitmap.Config.ARGB_8888)

        val canvas = Canvas(image)

        val drawable = VectorDrawableCompat.create(context.resources, iconId, context.theme)
        val offset = (rawImageLen - rawIconLen) / 2
        drawable?.setBounds(offset, offset, offset + rawIconLen, offset + rawIconLen)

        synchronized(this) {
            backgroundPaint.color = backgroundColor
            val xy = (rawImageLen / 2).toFloat()
            canvas.drawCircle(xy, xy, rawBackgroundLen / 2, backgroundPaint)
            drawable?.draw(canvas)
        }

        return image
    }

    private fun toRawLen(len: Int) = (len * scale + 0.5f).toInt()

    private fun toRawLenF(len: Int) = len * scale
}
