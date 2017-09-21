package cc.binmt.fileexplorer

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.support.graphics.drawable.VectorDrawableCompat
import android.support.v4.view.GestureDetectorCompat
import android.util.AttributeSet
import android.view.*
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.OverScroller
import android.widget.TextView
import java.io.File
import java.util.*


class PathView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : ViewGroup(context, attrs, defStyleAttr) {

    data class PathItem(val title: String, val path: File) {
        internal var textView: TextView? = null
    }

    private var maxScrollX = 0
    private val items = ArrayList<PathItem>()
    var currentPos = -1; get
    var itemVisibleOffset: () -> Int = { 20 }

    private var arrow: VectorDrawableCompat? = VectorDrawableCompat.create(context.resources, R.drawable.ic_chevron_right, context.theme)
    private val touchSlopSquare: Float
    private val scaledOverflingDistance: Int

    var afterPathChangedListener: () -> Unit = {}; set
    var beforePathChangedListener: () -> Unit = {}; set

    init {
        val vc = ViewConfiguration.get(context)
        val touchSlop = vc.scaledTouchSlop
        touchSlopSquare = (touchSlop * touchSlop).toFloat()
        scaledOverflingDistance = vc.scaledOverflingDistance
        isMotionEventSplittingEnabled = false
        if (isInEditMode) {
            val file = File("");
            push("Item1", file)
            push("Item2", file)
            push("Item3", file)
        }
    }

    private val mScroller = OverScroller(context)
    private val gestureDetector = GestureDetectorCompat(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent?, distanceX: Float, distanceY: Float): Boolean {
            val offX = distanceX.toInt()
            if (offX == 0)
                return true

            var x = scrollX + offX
            if (x < 0)
                x = 0
            else if (x > maxScrollX)
                x = maxScrollX

            scrollTo(x, 0)
            return true
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent?, velocityX: Float, velocityY: Float): Boolean {
            if (maxScrollX > 0) {
                mScroller.fling(scrollX, 0, -velocityX.toInt(), 0, 0, maxScrollX,
                        0, 0, scaledOverflingDistance, 0)
                postInvalidate()
            }
            return true
        }
    })

    private val itemClickListener = View.OnClickListener {
        val textView = it as TextView
        val index = textView.tag as Int
        if (currentPos != index) {
            if (currentPos != -1)
                beforePathChangedListener.invoke()
            textView.setTextColor(textView.currentTextColor or 0xFF000000.toInt())
            val tv = items[currentPos].textView
            tv?.setTextColor(tv.currentTextColor and 0x60FFFFFF)
            currentPos = index
            ensureItemVisible()
            afterPathChangedListener.invoke()
        }
    }

    val currentPath: File; get() = items[currentPos].path

    fun pop(): Boolean {
        if (currentPos <= 0) return false

        val textView = items[currentPos - 1].textView
        if (textView != null) {
            itemClickListener.onClick(textView)
            return true
        }
        return false
    }

    fun push(title: String, path: File) {
        if (currentPos != -1)
            beforePathChangedListener.invoke()
        val pathItem = PathItem(title.toUpperCase(), path)
        if (currentPos == -1) {
            val textView = inflateTextView(pathItem.title)
            arrow?.colorFilter = PorterDuffColorFilter(textView.currentTextColor and 0x60FFFFFF, PorterDuff.Mode.SRC_IN)
            items.add(pathItem)
            addView(textView, LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            currentPos = 0
            afterPathChangedListener.invoke()
            return
        }
        if (currentPos == items.size - 1) {
            // 添加箭头
            if (!items.isEmpty()) {
                val view = ImageView(context)
                view.setImageDrawable(arrow)
                addView(view, LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT)
            }

            val textView = inflateTextView(pathItem.title)
            items.add(pathItem)
            addView(textView, LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)

            val tv = items[currentPos].textView
            tv?.setTextColor(tv.currentTextColor and 0x60FFFFFF)
            currentPos++
        } else {
            val item = items[currentPos + 1]
            if (item.title == pathItem.title && item.path == pathItem.path) {
                var tv = items[currentPos++].textView
                tv?.setTextColor(tv.currentTextColor and 0x60FFFFFF)
                tv = items[currentPos].textView
                tv?.setTextColor(tv.currentTextColor or 0xFF000000.toInt())
            } else {
                var viewIndex = (currentPos + 1) * 2
                if (viewIndex > 0)
                    viewIndex--
                removeViews(viewIndex, childCount - viewIndex)
                while (items.size > currentPos + 1)
                    items.removeAt(items.size - 1)
                // 添加箭头
                if (!items.isEmpty()) {
                    val view = ImageView(context)
                    view.setImageDrawable(arrow)
                    addView(view, LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT)
                }

                val textView = inflateTextView(pathItem.title)
                items.add(pathItem)
                addView(textView, LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)

                val tv = items[currentPos].textView
                tv?.setTextColor(tv.currentTextColor and 0x60FFFFFF)
                currentPos++
            }
        }
        postDelayed({ ensureItemVisible() }, 10)
        afterPathChangedListener.invoke()
    }

    private fun ensureItemVisible() {
        val index = currentPos
        if (index < 0 || index >= items.size)
            return
        val textView = items[index].textView ?: return
        val left = textView.left
        var x = left - itemVisibleOffset.invoke() + textView.paddingLeft
        if (x < 0)
            x = 0
        else if (x > maxScrollX)
            x = maxScrollX
        mScroller.startScroll(scrollX, 0, x - scrollX, 0)
        postInvalidate()
    }

    private fun inflateTextView(title: String): TextView {
        val textView = LayoutInflater.from(context).inflate(R.layout.path_item, this, false) as TextView
        textView.setOnClickListener(itemClickListener)
        textView.text = title
        return textView
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        var height = MeasureSpec.getSize(heightMeasureSpec)
        if (height == 0)
            height = 1073741823

        val childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST)
        val childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST)

        var maxHeight = 0
        for (i in 0 until childCount) {
            val childView = getChildAt(i)
            if (i == 0)
                childView.setPadding(0, childView.paddingTop, childView.paddingRight, childView.paddingBottom)
            measureChild(childView, childWidthMeasureSpec, childHeightMeasureSpec)
            if (childView is TextView) {
                if (childView.measuredHeight > maxHeight)
                    maxHeight = childView.measuredHeight
            }
        }
        setMeasuredDimension(getDefaultSize(suggestedMinimumWidth, widthMeasureSpec), maxHeight)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        maxScrollX = 0
        var left = 0
        val height = b - t
        var count = 0
        for (i in 0 until childCount) {
            val childView = getChildAt(i)
            var measuredWidth = childView.measuredWidth
            if (childView is TextView) {
                if (count == 0) {
                    childView.setPadding(itemVisibleOffset.invoke(),
                            childView.paddingTop, childView.paddingRight, childView.paddingBottom)
                    measuredWidth += childView.paddingLeft
                }
                childView.tag = count
                items[count++].textView = childView
            }
            childView.layout(left, 0, left + measuredWidth, height)
            left += measuredWidth
            maxScrollX += measuredWidth
        }
        maxScrollX -= r - l
        if (maxScrollX < 0)
            maxScrollX = 0
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        when (event.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                if (!mScroller.isFinished)
                    mScroller.abortAnimation()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                beingDragged = false
            }
        }
        return true
    }

    private var lastMotionX = 0f
    private var lastMotionY = 0f
    private var beingDragged = false

    /**
     * 拦截触摸事件
     */
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(ev)
        val action = ev.action
        when (action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                if (!mScroller.isFinished) {
                    mScroller.abortAnimation()
                    beingDragged = true
                }
                lastMotionX = ev.x
                lastMotionY = ev.y
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                if (beingDragged) {
                    return true
                }
                val deltaX = Math.abs(lastMotionX - ev.x)
                val deltaY = Math.abs(lastMotionY - ev.y)
                if (deltaX * deltaX + deltaY * deltaY > touchSlopSquare) {
                    beingDragged = true
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                beingDragged = false
            }
        }
        return false
    }

    override fun computeScroll() {
        if (mScroller.computeScrollOffset()) {
            scrollTo(mScroller.currX, 0)
            invalidate()
        }
    }

}