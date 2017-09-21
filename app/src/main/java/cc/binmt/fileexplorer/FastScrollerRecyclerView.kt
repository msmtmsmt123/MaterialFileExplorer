package cc.binmt.fileexplorer

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.SystemClock
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class FastScrollerRecyclerView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = 0) : RecyclerView(context, attrs, defStyle) {

    private val fastScrollThumbColor: Int
    private val fastScrollThumbDraggingColor: Int
    private val fastScrollThumbHeight: Float
    private val fastScrollThumbWidth: Float

    var fastScrollEnabled = true
        set(value) {
            field = value
            if (!value && !_verticalScrollBarEnabled)
                isVerticalScrollBarEnabled = true
        }

    private var mShowScroller: Long = 0
    private var mScrollerDragging: Boolean = false
    private var mFastScrollerShowing: Boolean = false
    private val mFastScrollBound = RectF()

    init {
        val dm = context.resources.displayMetrics
        val density = dm.density
        fastScrollThumbDraggingColor = context.getAttrData(R.attr.colorAccent)
        fastScrollThumbColor = 0xDD777777.toInt()
        fastScrollThumbWidth = 8 * density
        fastScrollThumbHeight = 48 * density
        addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView?, dx: Int, dy: Int) {
                mShowScroller = SystemClock.uptimeMillis()
            }
        })
        val linearLayoutManager = LinearLayoutManager(context)
        super.setLayoutManager(linearLayoutManager)
        val itemAnimator = itemAnimator
        itemAnimator.addDuration = 100
        itemAnimator.removeDuration = 100
        itemAnimator.moveDuration = 200
        itemAnimator.changeDuration = 100
    }

    override fun setLayoutManager(layout: RecyclerView.LayoutManager) {
        throw RuntimeException("Do not support")
    }

    override fun getLayoutManager(): LinearLayoutManager {
        return super.getLayoutManager() as LinearLayoutManager
    }

    private val paint = Paint()
    private var _verticalScrollBarEnabled = true

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!fastScrollEnabled || adapter == null)
            return
        val totalCount = adapter.itemCount
        var availableCount = childCount
        if (availableCount != maxAvailableCount) {
            if (availableCount > maxAvailableCount)
                maxAvailableCount = availableCount
            else
                availableCount = maxAvailableCount
        }
        val count = totalCount - availableCount
        if (availableCount > 0 && count > 0 && totalCount / availableCount > 4) {
            val range = computeVerticalScrollRange()
            val extent = computeVerticalScrollExtent()
            val offset = computeVerticalScrollOffset()

            val diff = range - extent
            if (diff <= 0) {
                mFastScrollerShowing = false
                return
            }

            // 1-0 显示-隐藏
            var scrollerFrac = 0f
            if (mScrollerDragging)
                scrollerFrac = 1f
            else {
                var sc = (SystemClock.uptimeMillis() - mShowScroller).toInt()
                if (sc >= 0) {
                    if (sc <= SCROLLER_DELAY)
                        scrollerFrac = 1f
                    else {
                        sc -= SCROLLER_DELAY
                        if (sc < SCROLLER_DISMISS_TIME)
                            scrollerFrac = 1 - sc.toFloat() / SCROLLER_DISMISS_TIME
                    }
                }
            }
            if (scrollerFrac <= 0) {
                mFastScrollerShowing = false
                return
            }
            mFastScrollerShowing = true
            if (_verticalScrollBarEnabled)
                isVerticalScrollBarEnabled = false

            val width = width
            val height = height
            val tColor = if (mScrollerDragging) fastScrollThumbDraggingColor else fastScrollThumbColor
            val color = tColor and 0x00FFFFFF
            var alpha = tColor.ushr(24) and 0xFF
            alpha = (alpha * scrollerFrac).toInt()
            paint.color = color or (alpha shl 24)

            val scrollW = fastScrollThumbWidth * scrollerFrac
            val scrollH = fastScrollThumbHeight

            val thumbTop = (offset.toFloat() / diff * (height - scrollH)).toInt()
            mFastScrollBound.set(width - fastScrollThumbWidth * 2, thumbTop.toFloat(), width.toFloat(), thumbTop + scrollH)
            canvas.drawRect(width - scrollW, thumbTop.toFloat(), width.toFloat(), thumbTop + scrollH, paint)

            postInvalidate()

        } else {
            if (!_verticalScrollBarEnabled)
                isVerticalScrollBarEnabled = true
            mFastScrollerShowing = false
        }
    }

    private var maxAvailableCount = 0

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        maxAvailableCount = 0
    }

    private var offsetY: Float = 0f

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (fastScrollEnabled && mFastScrollerShowing) {
            if (event.action and MotionEvent.ACTION_MASK == MotionEvent.ACTION_DOWN) {
                val downX = event.x
                val downY = event.y
                if (mFastScrollBound.contains(downX, downY)) {
                    mScrollerDragging = true
                    offsetY = mFastScrollBound.top - downY
                    return true
                }
            }
        }
        return super.onInterceptTouchEvent(event)
    }

    private var lastIndex: Int = 0

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (mScrollerDragging) {
            when (event.action and MotionEvent.ACTION_MASK) {
                MotionEvent.ACTION_DOWN -> {
                    lastIndex = -1
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_MOVE -> if (mScrollerDragging) {
                    var frac = (event.y + offsetY) / (height - fastScrollThumbHeight)
                    if (frac < 0)
                        frac = 0f
                    else if (frac > 1)
                        frac = 1f
                    val index = (adapter.itemCount * frac).toInt()
                    if (lastIndex != index) {
                        lastIndex = index
                        scrollToPosition(index)
                    }
                    return true
                }
                MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> if (mScrollerDragging) {
                    mScrollerDragging = false
                    mShowScroller = SystemClock.uptimeMillis()
                    invalidate()
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    companion object {
        private val SCROLLER_DELAY = 1500
        private val SCROLLER_DISMISS_TIME = 300
    }

    class ItemDecoration(context: Context, paddingLeftDp: Int = 0) : RecyclerView.ItemDecoration() {
        private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val paddingLeft = paddingLeftDp * context.resources.displayMetrics.density

        init {
            dividerPaint.color = context.getAttrData(R.attr.colorDivider)
        }

        override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State?) {
            val left = parent.paddingLeft.toFloat() + paddingLeft
            val right = (parent.width - parent.paddingRight).toFloat()
            val childCount = parent.childCount
            for (i in 0 until childCount) {
                val child = parent.getChildAt(i)
                val params = child.layoutParams as RecyclerView.LayoutParams
                val y = (child.bottom + params.bottomMargin).toFloat()
                c.drawLine(left, y, right, y, dividerPaint)
            }
        }

        override fun getItemOffsets(outRect: Rect, view: View?, parent: RecyclerView?, state: RecyclerView.State?) {
            outRect.set(0, 0, 0, 1)
        }

    }
}
