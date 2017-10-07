package cc.binmt.fileexplorer

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Environment
import android.support.graphics.drawable.VectorDrawableCompat
import android.support.v4.view.GestureDetectorCompat
import android.util.AttributeSet
import android.view.*
import android.widget.ImageView
import android.widget.OverScroller
import android.widget.TextView
import java.io.File
import java.util.*


class PathView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : ViewGroup(context, attrs, defStyleAttr) {

    private var maxScrollX = 0
    private var pm = PathManager(this, File("/"))
    var itemVisibleOffset: () -> Int = { 20 }

    /**
     * 目录间的 > 图标
     */
    private var arrow: VectorDrawableCompat = VectorDrawableCompat.create(context.resources, R.drawable.ic_chevron_right, context.theme)!!

    private val touchSlopSquare: Float

    private val scaledOverflingDistance: Int

    var afterPathChangedListener: () -> Unit = {}
    var beforePathChangedListener: () -> Unit = {}

    init {
        val vc = ViewConfiguration.get(context)
        val touchSlop = vc.scaledTouchSlop
        touchSlopSquare = (touchSlop * touchSlop).toFloat()
        scaledOverflingDistance = vc.scaledOverflingDistance
        isMotionEventSplittingEnabled = false
        if (isInEditMode) {
            val file = File("/system/bin");
            setPath(file)
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
        if (isEnabled) {
            val textView = it as TextView
            val index = textView.tag as Int
            if (pm.currentPos != index) {
                beforePathChangedListener.invoke()
                textView.setTextColor(textView.currentTextColor or 0xFF000000.toInt())
                val tv = pm.currentText
                tv.setTextColor(tv.currentTextColor and 0x60FFFFFF)
                pm.currentPos = index
                ensureItemVisible()
                afterPathChangedListener.invoke()
            }
        }
    }

    val currentPath: File; get() = pm.currentPathFile

    private fun refreshViews() {
        removeAllViews()
        var addArrow = false
        for (i in 0 until pm.size) {
            val textView = pm[i].textView
            if (addArrow) {
                if (arrow.colorFilter == null)
                    arrow.colorFilter = PorterDuffColorFilter(textView.currentTextColor and 0x60FFFFFF, PorterDuff.Mode.SRC_IN)
                var view = pm[i].arrowView
                if (view == null) {
                    view = ImageView(context)
                    view.setImageDrawable(arrow)
                    pm[i].arrowView = view
                }
                addView(view, LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT)
            } else {
                addArrow = true
            }
            if (i == pm.currentPos)
                textView.setTextColor(textView.currentTextColor or 0xFF000000.toInt())
            else
                textView.setTextColor(textView.currentTextColor and 0x60FFFFFF)
            addView(textView, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        }
    }

    fun setPath(pathFile: File) {
        synchronized(this) {
            pm = PathManager(this, pathFile)
            refreshViews()
            ensureItemVisible(false)
            afterPathChangedListener.invoke()
        }
    }

    fun pop(): Boolean {
        synchronized(this) {
            if (pm.canPop) {
                beforePathChangedListener.invoke()

                // 淡化当前TextView
                var tv = pm.currentText
                tv.setTextColor(tv.currentTextColor and 0x60FFFFFF)

                pm.pop()

                // 恢复当前TextView
                tv = pm.currentText
                tv.setTextColor(tv.currentTextColor or 0xFF000000.toInt())

                ensureItemVisible()
                afterPathChangedListener.invoke()
                return true
            } else
                return false
        }
    }

    fun push(name: String) {
        synchronized(this) {
            beforePathChangedListener.invoke()
            pm.push(name)
            refreshViews()
            postDelayed({ ensureItemVisible() }, 10)
            afterPathChangedListener.invoke()
        }
    }

    private fun ensureItemVisible(animation: Boolean = true) {
        val index = pm.currentPos
        if (index < 0 || index >= pm.size)
            return
        val textView = pm[index].textView
        val left = textView.left
        var x = left - itemVisibleOffset.invoke() + textView.paddingLeft
        if (x < 0)
            x = 0
        else if (x > maxScrollX)
            x = maxScrollX
        if (animation) {
            mScroller.startScroll(scrollX, 0, x - scrollX, 0)
            postInvalidate()
        } else scrollX = x
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
                childView.tag = count++
            }
            childView.layout(left, 0, left + measuredWidth, height)
            left += measuredWidth
            maxScrollX += measuredWidth
        }
        maxScrollX -= r - l
        if (maxScrollX < 0)
            maxScrollX = 0
    }

    private var lastMotionX = 0f
    private var lastMotionY = 0f
    private var beingDragged = false
    /**
     * 拦截触摸事件
     */
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (!isEnabled)
            return true
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

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled)
            return true
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

    override fun computeScroll() {
        if (mScroller.computeScrollOffset()) {
            scrollTo(mScroller.currX, 0)
            invalidate()
        }
    }

    private class PathItem(val name: String, val pathFile: File, parent: PathView) : Comparable<PathItem> {
        val path = pathFile.path!!
        val textView = LayoutInflater.from(parent.context).inflate(R.layout.path_item, parent, false) as TextView
        var arrowView: ImageView? = null

        init {
            textView.text = name.toUpperCase()
            textView.setOnClickListener(parent.itemClickListener)
        }

        fun isMyChild(path: String): Boolean {
            // eg.
            // root /sdcard
            // path /sdcard/mt/test
            val root = this.path
            if (path.startsWith(root)) {
                if (root.length == 1 || path.length == root.length)
                    return true
                if (path[root.length] == '/')
                    return true
            }
            return false
        }

        override fun compareTo(other: PathItem): Int {
            return -path.compareTo(other.path)
        }

    }

    private class PathManager(private val parent: PathView, pathFile: File) {
        private val rootPath = TreeSet<PathItem>()
        private val items = ArrayList<PathItem>()
        var currentPos = -1

        init {
            val context = parent.context
            if (!parent.isInEditMode) {
                rootPath.add(PathItem(context.getString(R.string.external_storage),
                        Environment.getExternalStorageDirectory(), parent))
            }
            rootPath.add(PathItem(context.getString(R.string.root_folder),
                    File("/"), parent))

            var path = pathFile.path
            var success = false
            for (pathItem in rootPath) {
                if (pathItem.isMyChild(path)) {
                    success = true
                    path = when (pathItem.path.length) {
                        path.length -> ""
                        1 -> path.substring(1) // 根目录
                        else -> path.substring(pathItem.path.length + 1)
                    }
                    items.add(pathItem)
                    var file = pathItem.pathFile
                    if (!path.isEmpty()) {
                        for (s in path.split("/")) {
                            file = File(file, s)
                            items.add(PathItem(s, file, parent))
                        }
                    }
                    currentPos = items.size - 1
                    break
                }
            }
            if (!success)
                throw RuntimeException()
        }


        fun setPath(pathFile: File) {
            var path = pathFile.path
            var success = false
            for (pathItem in rootPath) {
                if (pathItem.isMyChild(path)) {
                    success = true
                    path = when (pathItem.path.length) {
                        path.length -> ""
                        1 -> path.substring(1) // 根目录
                        else -> path.substring(pathItem.path.length + 1)
                    }
                    if (items[0] == pathItem) {
                        // 根目录一样，令currentPos = 0，然后使用push
                        currentPos = 0
                        if (!path.isEmpty()) {
                            for (s in path.split("/")) {
                                push(s)
                            }
                        }
                    } else {
                        // 根目录不一样，重新初始化
                        items.clear()
                        items.add(pathItem)
                        var file = pathItem.pathFile
                        if (!path.isEmpty()) {
                            for (s in path.split("/")) {
                                file = File(file, s)
                                items.add(PathItem(s, file, parent))
                            }
                        }
                        currentPos = items.size - 1
                    }
                    break
                }
            }
            if (!success)
                throw RuntimeException()
        }

        fun push(name: String) {
            val nextPos = ++currentPos
            if (nextPos < items.size) {
                if (items[nextPos].name == name) {
                    return
                }
                do
                    items.removeAt(items.size - 1)
                while (nextPos < items.size)
            }
            val file = File(items[nextPos - 1].pathFile, name)
            items.add(PathItem(name, file, parent))
        }

        val canPop; get() = currentPos > 0;

        fun pop() {
            if (currentPos <= 0) throw IllegalStateException()
            currentPos--
        }

        val currentPathFile; get() = items[currentPos].pathFile

        val currentPath; get() = items[currentPos].path

        val currentText; get() = items[currentPos].textView

        val current; get() = items[currentPos]

        val size; get() = items.size

        operator fun get(index: Int): PathItem = items[index]


    }

}