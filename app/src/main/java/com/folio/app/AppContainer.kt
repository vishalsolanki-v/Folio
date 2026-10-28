package com.folio.app

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.datastore.preferences.preferencesDataStore
import coil3.ImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.svg.SvgDecoder
import com.folio.app.core.data.FolioDatabase
import com.folio.app.core.data.Library
import com.folio.app.core.data.SettingsRepository
import com.folio.app.core.files.FileResolver
import com.folio.app.core.files.LocalCopies
import com.folio.app.core.files.UriAccess
import com.folio.app.core.viewer.ViewerRegistry
import com.folio.app.formats.archive.ArchiveViewer
import com.folio.app.formats.docx.DocxViewer
import com.folio.app.formats.image.ImageViewer
import com.folio.app.formats.media.MediaViewer
import com.folio.app.formats.pdf.PdfViewer
import com.folio.app.formats.sheet.SheetViewer
import com.folio.app.formats.slides.SlidesViewer
import com.folio.app.formats.text.HtmlViewer
import com.folio.app.formats.text.MarkdownViewer
import com.folio.app.formats.text.TextViewer
import com.folio.app.formats.unknown.UnknownViewer
import java.io.File

private val Context.settingsStore by preferencesDataStore(name = "settings")

/** App-wide singletons (plan §4.1). */
class AppContainer(val context: Context) {
    val fileResolver = FileResolver(context)
    val localCopies = LocalCopies(File(context.filesDir, "recent_copies"))
    val settings = SettingsRepository(context.settingsStore)

    /** Format viewers register here as their milestones land; everything else gets the Unknown viewer. */
    val viewerRegistry = ViewerRegistry(viewers = listOf(PdfViewer, DocxViewer, SheetViewer, SlidesViewer, TextViewer, MarkdownViewer, HtmlViewer, ImageViewer, MediaViewer, ArchiveViewer), fallback = UnknownViewer)

    /** One Coil loader for the app: GIF (animated) and SVG decoders, no network module. */
    val imageLoader: ImageLoader by lazy {
        ImageLoader.Builder(context)
            .components {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) add(AnimatedImageDecoder.Factory()) else add(GifDecoder.Factory())
                add(SvgDecoder.Factory())
            }
            .build()
    }

    private val database by lazy { FolioDatabase.create(context) }
    val library by lazy {
        Library(database.recentDao(), database.folderDao(), localCopies, releasePermission = { UriAccess.release(context, Uri.parse(it)) })
    }
}
