package fr.alaedine.aesh.presentation.camera

import android.content.Context
import androidx.activity.compose.LocalActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import fr.alaedine.aesh.R
import fr.alaedine.aesh.presentation.components.EmptyState
import fr.alaedine.aesh.presentation.theme.AeshAssistantTheme
import java.io.File
import androidx.camera.core.Preview as CameraPreviewUseCase

/*
 * CameraX building blocks shared by every screen that photographs something
 * for on-device text recognition: the schedule scanner and the observation
 * notes scanner. Each screen still owns its own flow (what happens to the
 * photos), these only cover the camera itself and the immersive, always dark
 * screen around it: its scaffold, the viewfinder with the guide showing
 * where the sheet goes, the shutter, and the permission, busy and error
 * states.
 */

/** Dims the viewfinder around the sheet guide: enough for white text and controls on top to stay readable over a white page. */
private val ViewfinderScrimColor = Color.Black.copy(alpha = 0.6f)

private val GuideCornerRadius = 24.dp
private val GuideCornerLength = 40.dp
private val GuideStrokeWidth = 4.dp

/** How far past its own bounds the sheet guide dims the viewfinder: further than any screen is wide or tall. */
private val GuideScrimOverscan = 4000.dp

/** Keeps an error readable on a screen wider than tall rather than stretched across it. */
private val ScanErrorCardMaxWidth = 560.dp

private val ShutterSize = 80.dp
private val ShutterRingWidth = 4.dp
private val ShutterDiscSize = 64.dp
private const val SHUTTER_PRESSED_DISC_SCALE = 0.85f
private const val SHUTTER_DISABLED_ALPHA = 0.38f

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
                try {
                    // Inside the try: the provider itself fails to initialize when no camera can be reached.
                    val cameraProvider = cameraProviderFuture.get()
                    val preview =
                        CameraPreviewUseCase.Builder().build().apply {
                            setSurfaceProvider(previewView.surfaceProvider)
                        }
                    val capture = ImageCapture.Builder().build()
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        capture,
                    )
                    onImageCaptureReady(capture)
                } catch (error: Exception) {
                    // Initializing or binding failed (e.g. no camera hardware,
                    // or the lifecycle is already destroyed): the shutter
                    // button simply stays disabled since onImageCaptureReady
                    // was never called.
                }
            },
            ContextCompat.getMainExecutor(context),
        )
        onDispose {
            // Only once initialized: get() would otherwise block the main thread until it is.
            if (cameraProviderFuture.isDone) runCatching { cameraProviderFuture.get().unbindAll() }
        }
    }
}

/**
 * Captures a photo to a temporary file named after [filePrefix] in
 * [directory] and hands it to [onCaptured] once saved, or calls [onFailed]
 * if it couldn't be taken. The caller owns the file from then on and is
 * responsible for deleting it: the photos can show a child's personal
 * information, so they must not outlive their use.
 */
fun captureImage(
    context: Context,
    imageCapture: ImageCapture,
    filePrefix: String,
    onCaptured: (File) -> Unit,
    directory: File = context.cacheDir,
    onFailed: () -> Unit = {},
) {
    directory.mkdirs()
    val photoFile = File(directory, "${filePrefix}_${System.currentTimeMillis()}.jpg")
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
                // mid-capture): drop whatever part of the photo was written.
                photoFile.delete()
                onFailed()
            }
        },
    )
}

/**
 * The frame of a camera screen, dark whatever the app's theme: like the
 * system camera's, a viewfinder and its controls read best on black, and so
 * do the states around it (permission, review), which keeps the scanner one
 * immersive place. The status and navigation bar icons turn light to match
 * while it's shown.
 *
 * [content] fills the whole screen, behind the top bar (a [title] and a back
 * button, over no background of their own) and the system bars, so that a
 * viewfinder can fill it too; the padding it's given keeps anything else
 * clear of them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScaffold(
    title: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (contentPadding: PaddingValues) -> Unit,
) {
    LightSystemBarIconsEffect()
    AeshAssistantTheme(darkTheme = true) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            topBar = {
                TopAppBar(
                    title = { Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.cd_back),
                            )
                        }
                    },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            scrolledContainerColor = Color.Transparent,
                        ),
                )
            },
            content = content,
        )
    }
}

/** Light status and navigation bar icons, which stand out from the dark camera screens, for as long as this is composed. */
@Composable
private fun LightSystemBarIconsEffect() {
    val window = LocalActivity.current?.window
    val view = LocalView.current
    DisposableEffect(window, view) {
        if (window == null || view.isInEditMode) return@DisposableEffect onDispose {}
        val insetsController = WindowCompat.getInsetsController(window, view)
        val hadLightStatusBars = insetsController.isAppearanceLightStatusBars
        val hadLightNavigationBars = insetsController.isAppearanceLightNavigationBars
        insetsController.isAppearanceLightStatusBars = false
        insetsController.isAppearanceLightNavigationBars = false
        onDispose {
            insetsController.isAppearanceLightStatusBars = hadLightStatusBars
            insetsController.isAppearanceLightNavigationBars = hadLightNavigationBars
        }
    }
}

/**
 * What a camera screen shows until it may use the camera: why it needs it
 * ([message]) and a button to allow it, centered in the space the top bar
 * and system bars leave ([contentPadding], see [CameraScaffold]).
 */
@Composable
fun CameraPermissionState(
    message: String,
    grantButtonLabel: String,
    onRequestPermission: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize().padding(contentPadding)) {
        Column(
            // Scrolls rather than cutting off the button when the text is too large to fit.
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight),
            verticalArrangement = Arrangement.Center,
        ) {
            EmptyState(
                icon = painterResource(id = R.drawable.ic_camera),
                title = stringResource(R.string.camera_permission_title),
                message = message,
                action = {
                    Button(onClick = onRequestPermission) {
                        Text(text = grantButtonLabel)
                    }
                },
            )
        }
    }
}

/** What went wrong with a scan ([title]) and what to do about it ([message]), see [CameraViewfinder]. */
@Immutable
data class ScanErrorMessage(
    val title: String,
    val message: String,
)

/**
 * A live viewfinder filling the screen, dimmed around a guide of
 * [guideAspectRatio] (width over height) showing where the sheet goes, with
 * a short [hint] above it. The [shutter], given the camera to take photos
 * with once it's ready, sits under the guide between the
 * [leadingControl] and the [trailingControl], or beside it, between the
 * [trailingControl] above and the [leadingControl] below, when the screen
 * is wider than tall. While [isBusy] (e.g. the photo is being read), the
 * guide gives way to [busyMessage]; an [error] shows at the top until
 * [onErrorDismissed].
 *
 * @param contentPadding Keeps the hint, the controls and [error] clear of
 * the top bar and system bars, see [CameraScaffold].
 */
@Composable
fun CameraViewfinder(
    hint: String,
    guideAspectRatio: Float,
    isBusy: Boolean,
    busyMessage: String,
    error: ScanErrorMessage?,
    onErrorDismissed: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    leadingControl: @Composable () -> Unit = {},
    trailingControl: @Composable () -> Unit = {},
    shutter: @Composable (imageCapture: ImageCapture?) -> Unit,
) {
    // Scoped to the viewfinder, like the capture use case it holds: CameraPreview unbinds it once the viewfinder goes.
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    BoxWithConstraints(modifier = modifier.fillMaxSize().background(Color.Black)) {
        CameraPreview(onImageCaptureReady = { imageCapture = it }, modifier = Modifier.fillMaxSize())
        // Under the guide, the controls would leave it next to no room on a screen wider than tall: beside it, as on a
        // camera held sideways.
        if (maxWidth > maxHeight) {
            Row(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
                SheetGuide(
                    hint = hint,
                    aspectRatio = guideAspectRatio,
                    isBusy = isBusy,
                    busyMessage = busyMessage,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                Column(
                    modifier = Modifier.fillMaxHeight().padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) { trailingControl() }
                    shutter(imageCapture)
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) { leadingControl() }
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
                SheetGuide(
                    hint = hint,
                    aspectRatio = guideAspectRatio,
                    isBusy = isBusy,
                    busyMessage = busyMessage,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                Row(
                    // Narrow side margins: the controls on either side of the shutter share what little width is left.
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) { leadingControl() }
                    shutter(imageCapture)
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { trailingControl() }
                }
            }
        }
        if (error != null) {
            ScanErrorCard(
                error = error,
                onDismiss = onErrorDismissed,
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(contentPadding)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .widthIn(max = ScanErrorCardMaxWidth),
            )
        }
    }
}

/**
 * Where the sheet goes: the largest frame of [aspectRatio] that fits in this
 * composable, marked at its corners, under [hint]. Everything around the
 * frame is dimmed, up to the edges of the screen past this composable's own
 * bounds, which also keeps the hint, the top bar and the controls readable
 * over a bright scene. While [isBusy], the frame dims too and shows
 * [busyMessage] instead of the hint.
 */
@Composable
private fun SheetGuide(
    hint: String,
    aspectRatio: Float,
    isBusy: Boolean,
    busyMessage: String,
    modifier: Modifier = Modifier,
) {
    val busyFraction by animateFloatAsState(targetValue = if (isBusy) 1f else 0f, label = "sheetGuideBusyFraction")
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        ViewfinderHint(
            text = hint,
            modifier =
                Modifier
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .graphicsLayer { alpha = 1f - busyFraction }
                    .then(if (isBusy) Modifier.clearAndSetSemantics {} else Modifier),
        )
        SheetFrame(
            aspectRatio = aspectRatio,
            busyFraction = { busyFraction },
            isBusy = isBusy,
            busyMessage = busyMessage,
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    // Drawn first, so that the scrim the frame spreads over the whole screen stays under the hint.
                    .zIndex(-1f)
                    .padding(horizontal = 32.dp, vertical = 16.dp),
        )
    }
}

/** The frame of [SheetGuide], centered in this composable, with [busyMessage] in it while [isBusy]. */
@Composable
private fun SheetFrame(
    aspectRatio: Float,
    busyFraction: () -> Float,
    isBusy: Boolean,
    busyMessage: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier =
                Modifier
                    .aspectRatio(aspectRatio)
                    .drawBehind { drawSheetGuide(busyFraction()) },
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(visible = isBusy, enter = fadeIn(), exit = fadeOut()) {
                BusyIndicator(message = busyMessage)
            }
        }
    }
}

/** Dims everything around this frame, and the frame itself by [busyFraction], then marks the frame's corners. */
private fun DrawScope.drawSheetGuide(busyFraction: Float) {
    val cornerRadius = CornerRadius(GuideCornerRadius.toPx())
    val surroundings =
        Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(size.toRect().inflate(GuideScrimOverscan.toPx()))
            addRoundRect(RoundRect(rect = size.toRect(), cornerRadius = cornerRadius))
        }
    drawPath(path = surroundings, color = ViewfinderScrimColor)
    drawRoundRect(color = ViewfinderScrimColor, cornerRadius = cornerRadius, alpha = busyFraction)
    drawFrameCorners(alpha = 1f - busyFraction)
}

/** A mark at each corner of the frame, following its rounding: the top-start one, mirrored to the others. */
private fun DrawScope.drawFrameCorners(alpha: Float) {
    val radius = GuideCornerRadius.toPx()
    val length = GuideCornerLength.toPx()
    val corner =
        Path().apply {
            moveTo(0f, length)
            lineTo(0f, radius)
            arcTo(
                rect = Rect(offset = Offset.Zero, size = Size(radius * 2, radius * 2)),
                startAngleDegrees = 180f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false,
            )
            lineTo(length, 0f)
        }
    val stroke = Stroke(width = GuideStrokeWidth.toPx(), cap = StrokeCap.Round)
    listOf(1f to 1f, -1f to 1f, 1f to -1f, -1f to -1f).forEach { (scaleX, scaleY) ->
        scale(scaleX = scaleX, scaleY = scaleY, pivot = center) {
            drawPath(path = corner, color = Color.White, alpha = alpha, style = stroke)
        }
    }
}

/** Short instructions over the viewfinder, on a backdrop of their own so they stay readable whatever the camera sees. */
@Composable
private fun ViewfinderHint(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = Color.Black.copy(alpha = 0.5f),
        contentColor = Color.White,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

/** What the camera screen is busy doing ([message]), e.g. reading the photo, announced by screen readers as it appears. */
@Composable
private fun BusyIndicator(
    message: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(text = message, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** A scan [error] in the app's rounded-section style, announced by screen readers as it appears. */
@Composable
private fun ScanErrorCard(
    error: ScanErrorMessage,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceBright,
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 8.dp, bottom = 4.dp)) {
            Row(
                modifier = Modifier.padding(end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.errorContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = error.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = error.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text(text = stringResource(R.string.action_ok))
            }
        }
    }
}

/**
 * The camera's shutter: a white disc in a ring, as every camera draws it.
 * The disc shrinks while pressed; while [isBusy] (e.g. the photo is being
 * read), it gives way to a spinning ring and can't be pressed; while not
 * [enabled] (e.g. the camera isn't ready yet), it's dimmed.
 */
@Composable
fun ShutterButton(
    enabled: Boolean,
    isBusy: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val discScale by animateFloatAsState(
        targetValue =
            when {
                isBusy -> 0f
                isPressed -> SHUTTER_PRESSED_DISC_SCALE
                else -> 1f
            },
        label = "shutterDiscScale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (enabled || isBusy) 1f else SHUTTER_DISABLED_ALPHA,
        label = "shutterAlpha",
    )
    Box(
        modifier =
            modifier
                .size(ShutterSize)
                .graphicsLayer { this.alpha = alpha }
                .clip(CircleShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(color = Color.White),
                    enabled = enabled && !isBusy,
                    role = Role.Button,
                    onClick = onClick,
                ).semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        if (isBusy) {
            CircularProgressIndicator(
                modifier = Modifier.fillMaxSize(),
                color = Color.White,
                strokeWidth = ShutterRingWidth,
                trackColor = Color.White.copy(alpha = 0.3f),
            )
        } else {
            Box(modifier = Modifier.fillMaxSize().border(width = ShutterRingWidth, color = Color.White, shape = CircleShape))
        }
        Box(
            modifier =
                Modifier
                    .size(ShutterDiscSize)
                    .graphicsLayer {
                        scaleX = discScale
                        scaleY = discScale
                    }.background(color = Color.White, shape = CircleShape),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun ShutterButtonPreview() {
    AeshAssistantTheme(darkTheme = true) {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ShutterButton(enabled = true, isBusy = false, contentDescription = "", onClick = {})
            ShutterButton(enabled = true, isBusy = true, contentDescription = "", onClick = {})
            ShutterButton(enabled = false, isBusy = false, contentDescription = "", onClick = {})
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun ScanErrorCardPreview() {
    AeshAssistantTheme(darkTheme = true) {
        ScanErrorCard(
            error =
                ScanErrorMessage(
                    title = stringResource(R.string.scanner_no_text_recognized_title),
                    message = stringResource(R.string.schedule_scanner_no_text_recognized),
                ),
            onDismiss = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
