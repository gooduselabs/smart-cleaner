package vn.smartcleaner.core

import java.io.InputStream
import java.security.MessageDigest

/** Thuật toán thuần Kotlin (không phụ thuộc Android) để dễ unit test. */
object Hashing {
    /** SHA-256 của toàn bộ luồng dữ liệu, trả về hex. */
    fun sha256(input: InputStream): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(64 * 1024)
        input.use { s -> while (true) { val n = s.read(buf); if (n <= 0) break; md.update(buf, 0, n) } }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** Chuyển ARGB sang độ sáng 0..255. */
    fun luma(argb: Int): Int {
        val r = (argb shr 16) and 0xFF; val g = (argb shr 8) and 0xFF; val b = argb and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000
    }

    /** Thu nhỏ ảnh xám (w*h) về (tw*th) bằng trung bình vùng. */
    fun resize(gray: IntArray, w: Int, h: Int, tw: Int, th: Int): DoubleArray {
        val out = DoubleArray(tw * th)
        for (ty in 0 until th) for (tx in 0 until tw) {
            val x0 = tx * w / tw; val x1 = maxOf(x0 + 1, (tx + 1) * w / tw)
            val y0 = ty * h / th; val y1 = maxOf(y0 + 1, (ty + 1) * h / th)
            var sum = 0.0; var c = 0
            for (y in y0 until minOf(y1, h)) for (x in x0 until minOf(x1, w)) { sum += gray[y * w + x]; c++ }
            out[ty * tw + tx] = if (c == 0) 0.0 else sum / c
        }
        return out
    }

    /** dHash 64-bit: so sánh độ sáng các điểm kề nhau trên lưới 9x8. Bền với thay đổi kích thước/nén. */
    fun dHash(gray: IntArray, w: Int, h: Int): Long {
        val s = resize(gray, w, h, 9, 8)
        var hash = 0L
        for (y in 0 until 8) for (x in 0 until 8) {
            hash = hash shl 1
            if (s[y * 9 + x] > s[y * 9 + x + 1]) hash = hash or 1L
        }
        return hash
    }

    fun hamming(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

    /**
     * Độ nét = phương sai của toán tử Laplace trên ảnh xám. Giá trị thấp ⇒ ảnh mờ.
     */
    fun laplacianVariance(gray: IntArray, w: Int, h: Int): Double {
        if (w < 3 || h < 3) return 0.0
        var sum = 0.0; var sq = 0.0; var n = 0
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            val v = (gray[i - w] + gray[i + w] + gray[i - 1] + gray[i + 1] - 4 * gray[i]).toDouble()
            sum += v; sq += v * v; n++
        }
        val mean = sum / n
        return sq / n - mean * mean
    }

    const val BLUR_THRESHOLD = 60.0
    fun isBlurry(score: Double) = score < BLUR_THRESHOLD
}

/** Gom nhóm bằng union-find. */
object Grouping {
    /** Nhóm các mục có cùng khoá (trùng tuyệt đối). Chỉ giữ nhóm ≥ 2. */
    fun <T, K> exact(items: List<T>, key: (T) -> K?): List<List<T>> =
        items.filter { key(it) != null }.groupBy(key).values.filter { it.size > 1 }

    /**
     * Nhóm ảnh tương tự: hai ảnh nối nhau nếu khoảng cách Hamming ≤ threshold và
     * (nếu có thời gian) chụp cách nhau ≤ windowMs. Dữ liệu sắp theo thời gian để O(n·k).
     */
    fun <T> similar(items: List<T>, hash: (T) -> Long, time: (T) -> Long, threshold: Int = 10, windowMs: Long = Long.MAX_VALUE): List<List<T>> {
        val sorted = items.sortedBy(time)
        val parent = IntArray(sorted.size) { it }
        fun find(x: Int): Int { var r = x; while (parent[r] != r) { parent[r] = parent[parent[r]]; r = parent[r] }; return r }
        for (i in sorted.indices) {
            var j = i + 1
            while (j < sorted.size && time(sorted[j]) - time(sorted[i]) <= windowMs) {
                if (Hashing.hamming(hash(sorted[i]), hash(sorted[j])) <= threshold) parent[find(j)] = find(i)
                j++
            }
        }
        return sorted.indices.groupBy { find(it) }.values.filter { it.size > 1 }.map { g -> g.map { sorted[it] } }
    }
}

data class ShotStats(val sharpness: Double, val pixels: Long, val bytes: Long, val eyesOpen: Float? = null, val smiling: Float? = null)

/** Chọn ảnh đẹp nhất: ưu tiên nét, mắt mở, cười, độ phân giải cao. */
object BestShot {
    fun score(s: ShotStats, maxSharp: Double, maxPixels: Long): Double {
        val sharp = if (maxSharp <= 0) 0.0 else (s.sharpness / maxSharp).coerceIn(0.0, 1.0)
        val res = if (maxPixels <= 0) 0.0 else s.pixels.toDouble() / maxPixels
        val eyes = (s.eyesOpen ?: 0.5f).toDouble()
        val smile = (s.smiling ?: 0.5f).toDouble()
        return sharp * 0.5 + res * 0.2 + eyes * 0.2 + smile * 0.1
    }
    fun pick(stats: List<ShotStats>): Int {
        if (stats.isEmpty()) return -1
        val ms = stats.maxOf { it.sharpness }; val mp = stats.maxOf { it.pixels }
        return stats.indices.maxBy { score(stats[it], ms, mp) }
    }
}

object Format {
    fun bytes(b: Long): String {
        if (b < 1024) return "$b B"
        val u = listOf("KB", "MB", "GB", "TB"); var v = b / 1024.0; var i = 0
        while (v >= 1024 && i < u.lastIndex) { v /= 1024; i++ }
        return if (v >= 100) "%.0f %s".format(v, u[i]) else "%.1f %s".format(v, u[i]).replace('.', ',')
    }
    const val DAY = 86_400_000L
    fun daysLeft(trashedAt: Long, now: Long, keepDays: Int = 30): Int = (keepDays - ((now - trashedAt) / DAY).toInt()).coerceAtLeast(0)
}
