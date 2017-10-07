package cc.binmt.fileexplorer

import android.content.Context
import java.io.File
import java.text.DateFormat
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

class FileItem(context: Context, val file: File) {
    val name = file.name ?: ""
    val isDirectory = file.isDirectory
    val dateTime = dateFormat?.format(file.lastModified()) ?: Date(file.lastModified()).toString()
    val info =
            if (isDirectory) context.getString(R.string.folder)!!
            else formatFileSize(file.length())
    val type = getType(name)

    companion object {
        private val dateFormat: DateFormat? = SimpleDateFormat.getDateTimeInstance()
        private val df = DecimalFormat("#.00")
        private val typeMap = HashMap<String, String>()

        init {
            val video = arrayOf("3gp", "asf", "avi", "mp4", "mpe", "mpeg", "mpg", "mpg4", "m4u", "m4v", "mov", "rmvb")
            for (s in video) typeMap[s] = "video"

            val text = arrayOf("txt", "xml", "conf", "prop", "cpp", "h", "java", "log", "json", "js",
                    "php", "css", "py", "c", "smali", "cfg", "ini", "bat", "mf", "mtd", "lua")
            for (s in text) typeMap[s] = "text"

            val html = arrayOf("htm", "html")
            for (s in html) typeMap[s] = "html"

            val image = arrayOf("jpeg", "jpg", "bmp", "gif", "png")
            for (s in image) typeMap[s] = "image"

            val audio = arrayOf("m3u", "m4a", "m4b", "m4p", "mp2", "mp3", "mpga", "ogg", "wav", "wma", "wmv", "3gpp", "flac", "amr")
            for (s in audio) typeMap[s] = "audio"

            val archive = arrayOf("zip", "rar", "7z", "tar", "jar")
            for (s in archive) typeMap[s] = "archive"
        }

        private fun getType(fileName: String): String {
            if (fileName.isEmpty())
                return "*"
            val dotIndex = fileName.lastIndexOf(".")
            if (dotIndex == -1 || dotIndex == fileName.length)
                return "*"
            val h = fileName.substring(dotIndex + 1).toLowerCase()
            return typeMap[h] ?: h
        }

        private fun formatFileSize(fileS: Long) = when {
            fileS < 1024 -> fileS.toString() + "B"
            fileS < 1048576 -> df.format(fileS.toDouble() / 1024) + "KB"
            fileS < 1073741824 -> df.format(fileS.toDouble() / 1048576) + "MB"
            else -> df.format(fileS.toDouble() / 1073741824) + "GB"
        }
    }
}
