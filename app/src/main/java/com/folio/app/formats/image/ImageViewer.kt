package com.folio.app.formats.image

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import android.print.PrintDocumentAdapter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.exifinterface.media.ExifInterface
import coil3.request.ImageRequest
import com.folio.app.appContainer
import com.folio.app.core.designsystem.LocalFolioDark
import com.folio.app.core.model.FileFamily
import com.folio.app.core.model.OpenedFile
import com.folio.app.core.viewer.FormatViewer
import com.folio.app.core.viewer.ViewerError
import com.folio.app.core.viewer.ViewerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage

/** Photos and images (plan M8): pinch and double-tap zoom with tiling for huge images, GIF and SVG via Coil. */
object ImageViewer : FormatViewer {
    override val families = setOf(FileFamily.IMAGE)
    override val supportsSearch = false
    override val supportsPaper = false

    override fun printJob(context: Context, file: OpenedFile): PrintDocumentAdapter? =
        if (file.mime == "image/svg+xml" || unsupportedReason(file) != null) null else BitmapPrintAdapter(context, file.uri, file.name)

    /** HEIC needs Android 9 and AVIF Android 12 to decode; older phones get a clear message instead of a blank screen. */
    private fun unsupportedReason(file: OpenedFile): String? = when {
        file.mime == "image/heic" && Build.VERSION.SDK_INT < Build.VERSION_CODES.P -> "This phone's Android version can't show HEIC photos."
        file.mime == "image/avif" && Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> "This phone's Android version can't show AVIF images."
        else -> null
    }

    @Composable
    override fun Content(file: OpenedFile, state: ViewerState, onTap: () -> Unit, modifier: Modifier) {
        unsupportedReason(file)?.let {
            ViewerError(it, modifier)
            return
        }
        val context = LocalContext.current
        var failed by remember(file.uri) { mutableStateOf(false) }
        LaunchedEffect(file.uri) { state.infoExtras = imageInfo(context, file) }
        if (failed) {
            ViewerError("Folio couldn't show this image. It may be damaged.", modifier)
            return
        }
        val request = remember(file.uri) {
            ImageRequest.Builder(context).data(file.uri).listener(onError = { _, _ -> failed = true }).build()
        }
        val backdrop = if (LocalFolioDark.current) Color(0xFF0F0F0E) else Color(0xFFF3F1EC)
        Box(modifier.background(backdrop)) {
            ZoomableAsyncImage(
                model = request,
                contentDescription = file.name,
                imageLoader = appContainer().imageLoader,
                modifier = Modifier.fillMaxSize(),
                onClick = { onTap() },
            )
        }
    }

    /** Dimensions plus EXIF date and camera when present, for the File info sheet. */
    private suspend fun imageInfo(context: Context, file: OpenedFile): Map<String, String> = withContext(Dispatchers.IO) {
        buildMap {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(file.uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                if (bounds.outWidth > 0) put("Dimensions", "${bounds.outWidth} × ${bounds.outHeight}")
            }
            runCatching {
                context.contentResolver.openInputStream(file.uri)?.use { input ->
                    val exif = ExifInterface(input)
                    exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)?.let { put("Taken", it.replaceFirst(':', '-').replaceFirst(':', '-')) }
                    listOfNotNull(exif.getAttribute(ExifInterface.TAG_MAKE), exif.getAttribute(ExifInterface.TAG_MODEL))
                        .joinToString(" ").trim().takeIf { it.isNotEmpty() }?.let { put("Camera", it) }
                }
            }
        }
    }
}
