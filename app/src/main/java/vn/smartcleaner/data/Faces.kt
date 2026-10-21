package vn.smartcleaner.data

import android.content.Context
import android.net.Uri
import android.util.Size
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** ML Kit Face Detection (mô hình đi kèm app, chạy hoàn toàn trên máy). */
class Faces(private val ctx: Context) {
    private val detector by lazy {
        FaceDetection.getClient(FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL).build())
    }
    /** Trả (mắt mở, cười) trung bình 0..1, hoặc null nếu không có khuôn mặt. */
    suspend fun analyze(uri: Uri): Pair<Float, Float>? {
        val bmp = runCatching { ctx.contentResolver.loadThumbnail(uri, Size(640, 640), null) }.getOrNull() ?: return null
        return suspendCancellableCoroutine { cont ->
            detector.process(InputImage.fromBitmap(bmp, 0))
                .addOnSuccessListener { faces ->
                    if (faces.isEmpty()) cont.resume(null) else {
                        val eyes = faces.map { ((it.leftEyeOpenProbability ?: .5f) + (it.rightEyeOpenProbability ?: .5f)) / 2 }.average().toFloat()
                        val smile = faces.map { it.smilingProbability ?: .5f }.average().toFloat()
                        cont.resume(eyes to smile)
                    }
                }.addOnFailureListener { cont.resume(null) }
        }
    }
}
