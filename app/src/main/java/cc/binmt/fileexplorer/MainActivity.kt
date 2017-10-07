package cc.binmt.fileexplorer

import android.animation.ObjectAnimator
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Environment
import android.support.graphics.drawable.VectorDrawableCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.RecyclerView
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.util.*
import kotlin.collections.HashMap

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)
        title = getString(R.string.title)
        toolbar.contentInsetStartWithNavigation = 0
        toolbar.navigationIcon = VectorDrawableCompat.create(resources, R.drawable.ic_menu, theme)

        recyclerView.adapter = Adapter()
        recyclerView.addItemDecoration(FastScrollerRecyclerView.ItemDecoration(this, 14 + 48 + 14))

        initPathView()

        swipeRefreshLayout.setOnRefreshListener {
            pathView.isEnabled = false
            recyclerView.isEnabled = false
            Thread({
                try {
                    val fs = if (showHiddenFile)
                        pathView.currentPath.listFiles()
                    else
                        pathView.currentPath.listFiles({ _, name -> !name.startsWith(".") })
                    if (fs != null) {
                        Arrays.sort(fs, kotlin.Comparator { o1, o2 ->
                            if (o1.isDirectory) {
                                if (!o2.isDirectory)
                                    return@Comparator Int.MIN_VALUE
                            } else if (o2.isDirectory)
                                return@Comparator Int.MAX_VALUE

                            return@Comparator o1.name.toLowerCase().compareTo(o2.name.toLowerCase())
                        })
                        files.clear()
                        files.addAll(fs.map { FileItem(this, it) })
                    }
                } finally {
                    runOnUiThread {
                        swipeRefreshLayout.isRefreshing = false
                        updateFileCount()
                        recyclerView.adapter.notifyDataSetChanged()
                        emptyView.visibility = if (files.isEmpty()) View.VISIBLE else View.GONE
                        recyclerView.visibility = if (files.isEmpty()) View.INVISIBLE else View.VISIBLE
                        if (files.isEmpty()) {
                            emptyView.alpha = 0f
                            ObjectAnimator
                                    .ofFloat(emptyView, "alpha", 0f, 0.4f)
                                    .setDuration(300).start()
                        } else {
                            recyclerView.alpha = 0f
                            ObjectAnimator
                                    .ofFloat(recyclerView, "alpha", 0f, 1f)
                                    .setDuration(300).start()
                        }
                        recyclerView.isEnabled = true
                        pathView.isEnabled = true
                    }
                }
            }).start()
        }
    }

    private var itemVisibleOffset = 0
    private val positionRecord = HashMap<String, Int>()

    private val files = ArrayList<FileItem>()
    private var showHiddenFile = false
    private val fileComparator = kotlin.Comparator<File> { o1, o2 ->
        if (o1.isDirectory) {
            if (!o2.isDirectory)
                return@Comparator Int.MIN_VALUE
        } else if (o2.isDirectory)
            return@Comparator Int.MAX_VALUE

        return@Comparator o1.name.toLowerCase().compareTo(o2.name.toLowerCase())
    }

    private fun initPathView() {
        pathView.itemVisibleOffset = {
            if (itemVisibleOffset == 0) {
                val textView = toolbar.getTitleTextView()
                if (textView != null)
                    itemVisibleOffset = (textView.x + textView.paddingLeft).toInt()
            }
            itemVisibleOffset
        }
        pathView.beforePathChangedListener = {
            //            if (!fixPathView)
//                appBarLayout.setExpanded(true)
            // 记录位置
            positionRecord[pathView.currentPath.path] = recyclerView.layoutManager.findFirstVisibleItemPosition()
        }
        pathView.afterPathChangedListener = {
            val animator = if (files.isEmpty()) {
                ObjectAnimator
                        .ofFloat(emptyView, "alpha", 0.4f, 0f)
            } else {
                ObjectAnimator
                        .ofFloat(recyclerView, "alpha", 1f, 0f)
            }
            animator.setDuration(200).start()

            swipeRefreshLayout.isEnabled = false
            pathView.isEnabled = false
            recyclerView.isEnabled = false

            Thread({
                // 刷新数据
                val fs = if (showHiddenFile)
                    pathView.currentPath.listFiles()
                else
                    pathView.currentPath.listFiles({ _, name -> !name.startsWith(".") })
                if (fs != null) {
                    Arrays.sort(fs, fileComparator)
                    files.clear()
                    files.addAll(fs.map { FileItem(this, it) })
                }
                runOnUiThread {
                    animator.end()
                    recyclerView.adapter.notifyDataSetChanged()
                    emptyView.visibility = if (files.isEmpty()) View.VISIBLE else View.GONE
                    recyclerView.visibility = if (files.isEmpty()) View.INVISIBLE else View.VISIBLE
                    updateFileCount()
                    // 恢复位置，要先滚动到底部，再滚动到要显示到位置，这样top位置和要显示到位置才会一致
                    if (files.isEmpty()) {
                        emptyView.alpha = 0f
                        ObjectAnimator
                                .ofFloat(emptyView, "alpha", 0f, 0.4f)
                                .setDuration(300).start()
                    } else {
                        recyclerView.alpha = 0f
                        recyclerView.scrollToPosition(files.size - 1)
                        pathView.post({
                            val pos = positionRecord[pathView.currentPath.path] ?: 0
                            recyclerView.scrollToPosition(pos)
                        })
                        pathView.postDelayed({
                            ObjectAnimator
                                    .ofFloat(recyclerView, "alpha", 0f, 1f)
                                    .setDuration(300).start()
                        }, 50)
                    }
                    swipeRefreshLayout.isEnabled = true
                    pathView.isEnabled = true
                    recyclerView.isEnabled = true
                }
            }).start()
        }
        pathView.setPath(Environment.getExternalStorageDirectory())
    }

    private fun updateFileCount() {
        var file = 0
        var folder = 0
        for (f in files) {
            if (f.isDirectory)
                folder++
            else
                file++
        }
        toolbar.subtitle = when {
            file == 0 && folder == 0 -> getString(R.string.file_count4)
            file == 0 -> getString(R.string.file_count3, folder)
            folder == 0 -> getString(R.string.file_count2, file)
            else -> getString(R.string.file_count1, file, folder)
        }
    }

    private var lastBackTime = 0L

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (recyclerView.isEnabled && !pathView.pop()) {
                if (System.currentTimeMillis() - lastBackTime < 2000)
                    finish()
                else {
                    lastBackTime = System.currentTimeMillis()
                    toast(R.string.press_again_to_exit)
                }
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

//    private var fixPathView: Boolean = false
//        get
//        set(value) {
//            if (field == value)
//                return
//            field = value
//            val layoutParams = pathView.layoutParams
//            if (layoutParams is AppBarLayout.LayoutParams) {
//                if (value) {
//                    layoutParams.scrollFlags = 0
//                    appBarLayout.setExpanded(true)
//                } else layoutParams.scrollFlags = SCROLL_FLAG_SCROLL or SCROLL_FLAG_ENTER_ALWAYS or SCROLL_FLAG_SNAP
//            }
//        }

    inner class Adapter : RecyclerView.Adapter<ViewHolder>() {
        private val icFolder: Bitmap
        private val icFile: Bitmap
        private val icText: Bitmap
        private val icImage: Bitmap
        private val icVideo: Bitmap
        private val icAudio: Bitmap
        private val icHtml: Bitmap
        private val icApk: Bitmap
        private val icArchive: Bitmap

        init {
            val iconFactory = IconFactory(this@MainActivity)
            val colorFolder = getAttrData(R.attr.colorPrimary)
            val colorFile = 0xFF607D8B.toInt()
            val colorText = 0xFF3860AF.toInt()
            val colorImage = 0xFF39A1A2.toInt()
            val colorVideo = 0xFFFB8C00.toInt()
            val colorAudio = 0xFFE53935.toInt()
            val colorHtml = 0xFF2083BD.toInt()
            val colorApk = 0xFF2083BD.toInt()
            val colorArchive = 0xFF2083BD.toInt()
            icFolder = iconFactory.makeIcon(colorFolder, R.drawable.fic_folder)
            icFile = iconFactory.makeIcon(colorFile, R.drawable.fic_file)
            icText = iconFactory.makeIcon(colorText, R.drawable.fic_text)
            icImage = iconFactory.makeIcon(colorImage, R.drawable.fic_image)
            icVideo = iconFactory.makeIcon(colorVideo, R.drawable.fic_video)
            icAudio = iconFactory.makeIcon(colorAudio, R.drawable.fic_audio)
            icHtml = iconFactory.makeIcon(colorHtml, R.drawable.fic_html)
            icApk = iconFactory.makeIcon(colorApk, R.drawable.fic_apk)
            icArchive = iconFactory.makeIcon(colorArchive, R.drawable.fic_archive)
        }

        override fun getItemCount(): Int {
            return files.size
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val file = files[position]
            holder.file = file
            holder.title.text = file.name
            holder.info.text = file.info
            holder.dateTime.text = file.dateTime
            holder.icon.setImageBitmap(
                    if (file.isDirectory) icFolder
                    else when (file.type) {
                        "text" -> icText
                        "html" -> icHtml
                        "audio" -> icAudio
                        "image" -> icImage
                        "video" -> icVideo
                        "archive" -> icArchive
                        "apk" -> icApk
                        else -> icFile
                    }

            )
        }

        override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(this@MainActivity).inflate(R.layout.file_item, parent, false)
            return ViewHolder(view)
        }

    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val icon = itemView.findViewById(R.id.icon) as ImageView
        val title = itemView.findViewById(R.id.name) as TextView
        val info = itemView.findViewById(R.id.info) as TextView
        val dateTime = itemView.findViewById(R.id.dateTime) as TextView
        var file: FileItem? = null

        init {
            itemView.setOnClickListener {
                if (!recyclerView.isEnabled)
                    return@setOnClickListener
                val file = file ?: return@setOnClickListener
                if (file.isDirectory) {
                    pathView.push(file.name)
                } else {
                    itemView.context.toast(file.name)
                }
            }
        }
    }

}
