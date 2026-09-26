package fr.alaedine.aesh.data.scanner

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import fr.alaedine.aesh.domain.repository.ScheduleScannerRepository
import java.io.File
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * [ScheduleScannerRepository] backed by ML Kit's on-device Text Recognition
 * (Latin script model), so schedule photos never leave the device — see
 * the project README's privacy section. The underlying recognizer is
 * lazily initialized on first use and reused for every subsequent scan.
 */
class MlKitScheduleScannerRepository(
    private val context: Context,
) : ScheduleScannerRepository {

    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    override suspend fun recognizeText(imageFile: File): Result<String> =
        suspendCancellableCoroutine { continuation ->
            val image = try {
                InputImage.fromFilePath(context, Uri.fromFile(imageFile))
            } catch (error: Exception) {
                continuation.resume(Result.failure(error))
                return@suspendCancellableCoroutine
            }
            recognizer.process(image)
                .addOnSuccessListener { text -> continuation.resume(Result.success(text.text)) }
                .addOnFailureListener { error -> continuation.resume(Result.failure(error)) }
        }
}
