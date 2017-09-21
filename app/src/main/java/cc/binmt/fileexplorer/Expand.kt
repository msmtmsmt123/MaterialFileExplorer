package cc.binmt.fileexplorer

import android.content.Context
import android.support.v7.widget.Toolbar
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import android.widget.Toast

fun Toolbar.getTitleTextView(): TextView? {
    return try {
        val f = Toolbar::class.java.getDeclaredField("mTitleTextView")
        f.isAccessible = true
        f.get(this) as TextView
    } catch (e: Exception) {
        null
    }
}

private val typedValue = TypedValue()

fun Context.getAttrData(attr: Int): Int {
    synchronized(typedValue) {
        theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data
    }
}

fun Context.toast(message: CharSequence) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

fun Context.toast(message: Int) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

fun Context.toastL(message: CharSequence) {
    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}

fun Context.toastL(message: Int) {
    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}

fun View.visible() {
    visibility = View.VISIBLE
}

fun View.invisible() {
    visibility = View.INVISIBLE
}

fun View.gone() {
    visibility = View.GONE
}
