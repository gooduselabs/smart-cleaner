package vn.smartcleaner

import org.junit.Assert.*
import org.junit.Test
import vn.smartcleaner.core.*
import kotlin.random.Random

class CoreTest {
    private fun gradient(w: Int, h: Int, shift: Int = 0) = IntArray(w * h) { i -> ((i % w) * 255 / w + shift).coerceIn(0, 255) }
    private fun noise(w: Int, h: Int, seed: Int) = Random(seed).let { r -> IntArray(w * h) { r.nextInt(256) } }
    private fun boxBlur(g: IntArray, w: Int, h: Int, rad: Int): IntArray = IntArray(w * h) { i ->
        val x = i % w; val y = i / w; var s = 0; var n = 0
        for (dy in -rad..rad) for (dx in -rad..rad) { val xx = x + dx; val yy = y + dy; if (xx in 0 until w && yy in 0 until h) { s += g[yy * w + xx]; n++ } }
        s / n
    }

    @Test fun sha256_knownVector() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Hashing.sha256("abc".byteInputStream()))
    }
    @Test fun sha256_equalForSameBytes_differentOtherwise() {
        val a = ByteArray(200_000) { (it % 251).toByte() }; val b = a.copyOf(); b[123_456] = 7
        assertEquals(Hashing.sha256(a.inputStream()), Hashing.sha256(a.copyOf().inputStream()))
        assertNotEquals(Hashing.sha256(a.inputStream()), Hashing.sha256(b.inputStream()))
    }
    @Test fun luma_whiteBlack() { assertEquals(255, Hashing.luma(0xFFFFFFFF.toInt())); assertEquals(0, Hashing.luma(0xFF000000.toInt())) }

    @Test fun dHash_stableUnderResizeAndBrightness() {
        val big = noise(64, 64, 1).let { boxBlur(it, 64, 64, 2) }
        val small = Hashing.resize(big, 64, 64, 32, 32).map { it.toInt() }.toIntArray()
        val brighter = big.map { (it + 20).coerceAtMost(255) }.toIntArray()
        val h = Hashing.dHash(big, 64, 64)
        assertTrue(Hashing.hamming(h, Hashing.dHash(small, 32, 32)) <= 8)
        assertTrue(Hashing.hamming(h, Hashing.dHash(brighter, 64, 64)) <= 6)
    }
    @Test fun dHash_differentImagesFar() {
        val a = Hashing.dHash(noise(64, 64, 1), 64, 64); val b = Hashing.dHash(noise(64, 64, 2), 64, 64)
        assertTrue(Hashing.hamming(a, b) > 16)
    }
    @Test fun hamming_basic() { assertEquals(0, Hashing.hamming(5, 5)); assertEquals(64, Hashing.hamming(0, -1)); assertEquals(2, Hashing.hamming(0b101, 0)) }

    @Test fun blur_sharpScoresHigherThanBlurred() {
        val sharp = noise(128, 128, 3)
        val blurred = boxBlur(boxBlur(sharp, 128, 128, 4), 128, 128, 4)
        val s = Hashing.laplacianVariance(sharp, 128, 128); val b = Hashing.laplacianVariance(blurred, 128, 128)
        assertTrue("sharp=$s blur=$b", s > b * 10)
        assertFalse(Hashing.isBlurry(s)); assertTrue(Hashing.isBlurry(b))
    }
    @Test fun blur_flatImageIsZero() { assertEquals(0.0, Hashing.laplacianVariance(IntArray(100) { 128 }, 10, 10), 1e-9) }
    @Test fun blur_tinyImage() { assertEquals(0.0, Hashing.laplacianVariance(IntArray(4), 2, 2), 0.0) }

    data class P(val id: Int, val h: Long, val t: Long, val sha: String?)
    @Test fun grouping_exactKeepsOnlyMultiples() {
        val g = Grouping.exact(listOf(P(1, 0, 0, "a"), P(2, 0, 0, "a"), P(3, 0, 0, "b"), P(4, 0, 0, null), P(5, 0, 0, null))) { it.sha }
        assertEquals(1, g.size); assertEquals(setOf(1, 2), g[0].map { it.id }.toSet())
    }
    @Test fun grouping_similarTransitiveAndWindow() {
        val items = listOf(P(1, 0b0, 0, null), P(2, 0b111, 1000, null), P(3, 0b111111, 2000, null), P(4, -1L, 3000, null), P(5, 0b1, 10_000_000, null))
        val g = Grouping.similar(items, { it.h }, { it.t }, threshold = 3, windowMs = 60_000)
        assertEquals(1, g.size)
        assertEquals(setOf(1, 2, 3), g[0].map { it.id }.toSet()) // 1–2 (3 bit), 2–3 (3 bit): nối bắc cầu; 5 ngoài cửa sổ thời gian
    }
    @Test fun grouping_similarEmpty() { assertTrue(Grouping.similar(emptyList<P>(), { it.h }, { it.t }).isEmpty()) }

    @Test fun bestShot_prefersSharpAndEyesOpen() {
        val stats = listOf(ShotStats(50.0, 12_000_000, 3_000_000, eyesOpen = .1f), ShotStats(300.0, 12_000_000, 3_000_000, eyesOpen = .9f), ShotStats(320.0, 3_000_000, 1_000_000, eyesOpen = .05f))
        assertEquals(1, BestShot.pick(stats))
        assertEquals(-1, BestShot.pick(emptyList()))
    }
    @Test fun format_bytesAndDays() {
        assertEquals("512 B", Format.bytes(512)); assertEquals("1,0 KB", Format.bytes(1024)); assertEquals("1,5 GB", Format.bytes(1536L * 1024 * 1024))
        assertEquals(30, Format.daysLeft(0, 0)); assertEquals(0, Format.daysLeft(0, 31 * Format.DAY)); assertEquals(20, Format.daysLeft(0, 10 * Format.DAY))
    }
}
