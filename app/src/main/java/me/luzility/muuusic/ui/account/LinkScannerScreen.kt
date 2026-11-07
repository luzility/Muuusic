package me.luzility.muuusic.ui.account

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.NoPhotography
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import me.luzility.muuusic.R
import me.luzility.muuusic.data.account.AccountLink
import java.util.concurrent.Executors

private sealed interface LinkState {
    data object Scanning : LinkState
    data object Sending : LinkState
    data object Sent : LinkState
    data class Problem(val message: Int) : LinkState
}

private val FinderSize = 260.dp

@Composable
fun LinkScannerScreen(cookie: String?, onBack: () -> Unit) {
    val context = LocalContext.current
    var permitted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permitted = it }
    LaunchedEffect(Unit) { if (!permitted) askCamera.launch(Manifest.permission.CAMERA) }

    var state by remember { mutableStateOf<LinkState>(LinkState.Scanning) }
    var scanned by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(scanned) {
        val text = scanned ?: return@LaunchedEffect
        val offer = AccountLink.parse(text)
        if (offer == null || cookie == null) {
            state = LinkState.Problem(R.string.link_invalid)
            delay(PROBLEM_MS)
            state = LinkState.Scanning
            scanned = null
            return@LaunchedEffect
        }
        state = LinkState.Sending
        val sent = withContext(Dispatchers.IO) { AccountLink.send(offer, cookie) }
        if (sent) {
            state = LinkState.Sent
            delay(SENT_MS)
            onBack()
        } else {
            state = LinkState.Problem(R.string.link_failed)
            delay(PROBLEM_MS)
            state = LinkState.Scanning
            scanned = null
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (permitted) {
            CameraPreview(onCode = { if (scanned == null) scanned = it })
            Viewfinder(active = state == LinkState.Scanning)
        } else {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Rounded.NoPhotography, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.size(16.dp))
                Text(stringResource(R.string.link_camera_needed), textAlign = TextAlign.Center)
                Spacer(Modifier.size(16.dp))
                Button(onClick = { askCamera.launch(Manifest.permission.CAMERA) }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.link_camera_allow))
                }
            }
        }

        FilledTonalIconButton(
            onClick = onBack,
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier.statusBarsPadding().padding(start = 12.dp, top = 8.dp),
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back))
        }

        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp).fillMaxWidth(),
        ) {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                AnimatedContent(state, label = "linkStatus") { current ->
                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        when (current) {
                            LinkState.Scanning -> Icon(Icons.Rounded.Computer, null, tint = MaterialTheme.colorScheme.primary)
                            LinkState.Sending -> LoadingIndicator(Modifier.size(40.dp))
                            LinkState.Sent -> Icon(Icons.Rounded.CheckCircle, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                            is LinkState.Problem -> Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.link_desktop), style = MaterialTheme.typography.titleMediumEmphasized)
                    val message = when (val current = state) {
                        LinkState.Scanning -> R.string.link_scan_hint
                        LinkState.Sending -> R.string.link_sending
                        LinkState.Sent -> R.string.link_sent
                        is LinkState.Problem -> current.message
                    }
                    Text(stringResource(message), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun CameraPreview(onCode: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val found by rememberUpdatedState(onCode)
    val executor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { executor.shutdown() } }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { viewContext ->
            val view = PreviewView(viewContext).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
            val providerFuture = ProcessCameraProvider.getInstance(viewContext)
            providerFuture.addListener({
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                val reader = MultiFormatReader().apply { setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE))) }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(executor) { image -> decode(reader, image)?.let { text -> view.post { found(text) } } } }
                provider.unbindAll()
                provider.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }, ContextCompat.getMainExecutor(context))
            view
        },
    )
}

private fun decode(reader: MultiFormatReader, image: ImageProxy): String? = image.use {
    val plane = it.planes.firstOrNull() ?: return null
    val buffer = plane.buffer
    val data = ByteArray(buffer.remaining()).also(buffer::get)
    val source = PlanarYUVLuminanceSource(data, plane.rowStride, it.height, 0, 0, it.width, it.height, false)
    runCatching { reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text }.getOrNull().also { reader.reset() }
}

@Composable
private fun Viewfinder(active: Boolean) {
    val primary = MaterialTheme.colorScheme.primary
    val breathing = rememberInfiniteTransition(label = "finder")
    val scale by breathing.animateFloat(1f, 1.04f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "finderScale")
    val corner = 40.dp
    Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        drawRect(Color.Black.copy(alpha = 0.55f))
        val side = FinderSize.toPx() * (if (active) scale else 1f)
        drawRoundRect(
            color = Color.Transparent,
            topLeft = Offset((size.width - side) / 2, (size.height - side) / 2),
            size = Size(side, side),
            cornerRadius = CornerRadius(corner.toPx()),
            blendMode = BlendMode.Clear,
        )
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(FinderSize)
                .graphicsLayer {
                    val current = if (active) scale else 1f
                    scaleX = current
                    scaleY = current
                }
                .border(4.dp, primary, RoundedCornerShape(corner)),
        )
    }
}

private const val SENT_MS = 1_200L
private const val PROBLEM_MS = 2_500L
