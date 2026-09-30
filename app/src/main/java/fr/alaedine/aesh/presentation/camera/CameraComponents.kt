package fr.alaedine.aesh.presentation.camera

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import fr.alaedine.aesh.R
import java.io.File
import androidx.camera.core.Preview as CameraPreviewUseCase

/*
 * CameraX building blocks shared by every screen that photographs something
 * for on-device text recognition: the schedule scanner and the observation
 * notes scanner. Each screen still owns its own flow (what happens to the
 * photos), these only cover the camera itself.
 */

/**
 * Binds a CameraX preview + [ImageCapture] use case to [PreviewView], both
 * scoped to the current [LocalLifecycleOwner] so CameraX automatically
 * releases the camera when this leaves composition (e.g. navigating away,
 * or the review screen replacing this content).
 */
@Composable
fun CameraPreview(
    onImageCaptureReady: (ImageCapture) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }

    AndroidView(factory = { previewView }, modifier = modifier)

    DisposableEffect(lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener(
            {
                val cameraProvider = cameraProviderFuture.get()
                val preview =
                    CameraPreviewUseCase.Builder().build().apply {
                        setSurfaceProvider(previewView.surfaceProvider)
                    }
                val capture = ImageCapture.Builder().build()
                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        capture,
                    )
                    onImageCaptureReady(capture)
                } catch (error: Exception) {
                    // Binding failed (e.g. no camera hardware, or the
                    // lifecycle is already destroyed): the shutter button
                    // simply stays disabled since onImageCaptureReady was
                    // never called.
                }
            },
            ContextCompat.getMainExecutor(context),
        )
        onDispose {
            runCatching { cameraProviderFuture.get().unbindAll() }
        }
    }
}

/**
 * Captures a photo to a temporary cache file named after [filePrefix] and
 * hands it to [onCaptured] once saved. The caller owns the file from then
 * on and is responsible for deleting it: the photos can show a child's
 * personal information, so they must not outlive their use.
 */
fun captureImage(
    context: Context,
    imageCapture: ImageCapture,
    filePrefix: String,
    onCaptured: (File) -> Unit,
) {
    val photoFile = File(context.cacheDir, "${filePrefix}_${System.currentTimeMillis()}.jpg")
    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                onCaptured(photoFile)
            }

            override fun onError(exception: ImageCaptureException) {
                // Rare in practice (e.g. disk full, camera disconnected
                // mid-capture); the user can simply try the shutter again.
            }
        },
    )
}

@Composable
fun ShutterButton(
    enabled: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = if (enabled) 1f else 0.4f))
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .semantics { this.contentDescription = contentDescription },
    )
}

@Composable
fun CameraPermissionRationale(
    message: String,
    grantButtonLabel: String,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(onClick = onRequestPermission) {
            Text(text = grantButtonLabel)
        }
    }
}

@Composable
fun ScanErrorBanner(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = message, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text(text = stringResource(R.string.action_ok))
            }
        }
    }
}
