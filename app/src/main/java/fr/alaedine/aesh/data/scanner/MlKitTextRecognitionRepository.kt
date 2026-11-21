package fr.alaedine.aesh.data.scanner

import android.graphics.BitmapFactory
import android.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import fr.alaedine.aesh.domain.repository.TextRecognitionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume

/**
 * [TextRecognitionRepository] backed by ML Kit's on-device Text Recognition
 * (Latin script model), so photos never leave the device — see the project
 * README's privacy section. The underlying recognizer is lazily initialized
 * on first use and reused for every subsequent scan.
 *
 * Photos are decoded on [decodingDispatcher], never on the caller's (main)
 * thread, and scaled down to what text recognition needs: a full-resolution
 * camera photo takes tens of megabytes once decoded, and decoding it on the
 * main thread froze the scanners.
 */
class MlKitTextRecognitionRepository(
    private val decodingDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : TextRecognitionRepository {
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    override suspend fun recognizeText(imageFile: File): Result<String> {
        val image =
            try {
                withContext(decodingDispatcher) { decodeForRecognition(imageFile) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                return Result.failure(error)
            } catch (outOfMemory: OutOfMemoryError) {
                return Result.failure(outOfMemory)
            }
        return suspendCancellableCoroutine { continuation ->
            recognizer
                .process(image)
                .addOnSuccessListener { text -> continuation.resume(Result.success(text.text)) }
                .addOnFailureListener { error -> continuation.resume(Result.failure(error)) }
        }
    }

    /** Decodes [imageFile] scaled down for text recognition, upright according to its EXIF orientation. */
    private fun decodeForRecognition(imageFile: File): InputImage {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(imageFile.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Can't read the image ${imageFile.name}.")

        val options = BitmapFactory.Options().apply { inSampleSize = recognitionSampleSize(bounds.outWidth, bounds.outHeight) }
        val bitmap = BitmapFactory.decodeFile(imageFile.path, options) ?: throw IOException("Can't decode the image ${imageFile.name}.")
        return InputImage.fromBitmap(bitmap, exifRotationDegrees(imageFile))
    }

    /** CameraX stores how the photo must be turned in its EXIF orientation rather than turning the pixels. */
    private fun exifRotationDegrees(imageFile: File): Int =
        when (ExifInterface(imageFile.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
}

/** Below this length (in pixels) for the long side, handwriting and small print get too small to be read reliably. */
private const val MIN_RECOGNITION_LONG_SIDE = 1600

/**
 * The largest power-of-two downsampling factor ([BitmapFactory.Options.inSampleSize]) that keeps the long side of
 * a [width] x [height] image at [MIN_RECOGNITION_LONG_SIDE] pixels or more.
 */
internal fun recognitionSampleSize(
    width: Int,
    height: Int,
): Int {
    val longSide = maxOf(width, height)
    var sampleSize = 1
    while (longSide / (sampleSize * 2) >= MIN_RECOGNITION_LONG_SIDE) sampleSize *= 2
    return sampleSize
}
