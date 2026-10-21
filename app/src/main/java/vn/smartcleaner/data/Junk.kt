package vn.smartcleaner.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Quét thư mục người dùng chọn (Storage Access Framework): tệp APK và thư mục rỗng. */
class Junk(private val ctx: Context) {
    suspend fun scan(tree: Uri): JunkResult = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(ctx, tree) ?: return@withContext JunkResult()
        val apks = ArrayList<SafEntry>(); val empty = ArrayList<SafEntry>()
        fun walk(d: DocumentFile, path: String, depth: Int): Boolean { // trả true nếu rỗng
            if (depth > 12) return false
            val kids = d.listFiles()
            var allEmpty = true
            for (k in kids) {
                val name = k.name ?: continue
                if (k.isDirectory) {
                    val e = walk(k, "$path/$name", depth + 1)
                    if (e) empty += SafEntry(k.uri, name, 0, "$path/$name", true, k.lastModified()) else allEmpty = false
                } else {
                    allEmpty = false
                    if (name.endsWith(".apk", true)) apks += SafEntry(k.uri, name, k.length(), "$path/$name", false, k.lastModified())
                }
            }
            return allEmpty
        }
        walk(root, root.name ?: "", 0)
        // Chỉ giữ thư mục rỗng ngoài cùng (con của nó cũng rỗng sẽ bị xoá theo)
        val outer = empty.filter { e -> empty.none { o -> o !== e && e.path.startsWith(o.path + "/") } }
        JunkResult(apks.sortedByDescending { it.size }, outer, root.name)
    }
}
