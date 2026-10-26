package com.folio.app.libcheck

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Builds tiny sample files on the device so the repo carries no binary fixtures. */
object Samples {
    private val dir: File
        get() = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir.resolve("samples").apply { mkdirs() }

    fun pdf(text: String): File = dir.resolve("sample.pdf").also { file ->
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(300, 400, 1).create())
        page.canvas.drawText(text, 40f, 80f, Paint().apply { color = Color.BLACK; textSize = 32f })
        doc.finishPage(page)
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
    }

    fun docx(
        paragraph: String,
        documentXml: String = """<?xml version="1.0" encoding="UTF-8"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
<w:body><w:p><w:r><w:rPr><w:b/></w:rPr><w:t>$paragraph</w:t></w:r></w:p></w:body>
</w:document>""",
    ): File = dir.resolve("sample.docx").also { file ->
        ZipOutputStream(file.outputStream()).use { zip ->
            fun put(name: String, body: String) {
                zip.putNextEntry(ZipEntry(name)); zip.write(body.toByteArray()); zip.closeEntry()
            }
            put("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>""")
            put("_rels/.rels", """<?xml version="1.0" encoding="UTF-8"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>""")
            put("word/document.xml", documentXml)
        }
    }

    fun png(): File = dir.resolve("sample.png").also { file ->
        val bmp = Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    fun svg(): File = dir.resolve("sample.svg").also {
        it.writeText("""<svg xmlns="http://www.w3.org/2000/svg" width="40" height="20"><rect width="40" height="20" fill="#2E5AAC"/></svg>""")
    }

    /** One second of 8 kHz mono 16-bit silence. */
    fun wav(): File = dir.resolve("sample.wav").also { file ->
        val sampleRate = 8000
        val data = ByteArray(sampleRate * 2)
        val out = ByteArrayOutputStream()
        DataOutputStream(out).apply {
            fun le32(v: Int) = writeInt(Integer.reverseBytes(v))
            fun le16(v: Int) = writeShort(java.lang.Short.reverseBytes(v.toShort()).toInt())
            writeBytes("RIFF"); le32(36 + data.size); writeBytes("WAVE")
            writeBytes("fmt "); le32(16); le16(1); le16(1); le32(sampleRate); le32(sampleRate * 2); le16(2); le16(16)
            writeBytes("data"); le32(data.size); write(data)
        }
        file.writeBytes(out.toByteArray())
    }
}
