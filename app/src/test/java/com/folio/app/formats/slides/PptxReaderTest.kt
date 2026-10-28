package com.folio.app.formats.slides

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class PptxReaderTest {
    private val ns = """xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main""""
    private val rels = """xmlns="http://schemas.openxmlformats.org/package/2006/relationships""""
    private val t = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/"

    private lateinit var reader: PptxReader

    private fun xfrm(x: Int, y: Int, w: Int, h: Int, tag: String = "a:xfrm") = """<$tag><a:off x="$x" y="$y"/><a:ext cx="$w" cy="$h"/></$tag>"""

    private fun deck(slides: List<String>, order: List<Int> = slides.indices.toList(), layout: String = "", master: String = "", theme: String? = null): Deck {
        val parts = buildMap {
            put("ppt/presentation.xml", """<p:presentation $ns><p:sldIdLst>""" +
                order.mapIndexed { i, s -> """<p:sldId id="${256 + i}" r:id="rId${s + 10}"/>""" }.joinToString("") +
                """</p:sldIdLst><p:sldSz cx="12192000" cy="6858000"/></p:presentation>""")
            put("ppt/_rels/presentation.xml.rels", """<Relationships $rels>""" +
                slides.indices.joinToString("") { """<Relationship Id="rId${it + 10}" Type="${t}slide" Target="slides/slide${it + 1}.xml"/>""" } + "</Relationships>")
            slides.forEachIndexed { i, body ->
                put("ppt/slides/slide${i + 1}.xml", """<p:sld $ns><p:cSld>$body</p:cSld></p:sld>""")
                put("ppt/slides/_rels/slide${i + 1}.xml.rels", """<Relationships $rels><Relationship Id="rIdL" Type="${t}slideLayout" Target="../slideLayouts/slideLayout1.xml"/><Relationship Id="rIdImg" Type="${t}image" Target="../media/image1.png"/></Relationships>""")
            }
            put("ppt/slideLayouts/slideLayout1.xml", """<p:sldLayout $ns><p:cSld><p:spTree>$layout</p:spTree></p:cSld></p:sldLayout>""")
            put("ppt/slideLayouts/_rels/slideLayout1.xml.rels", """<Relationships $rels><Relationship Id="rId1" Type="${t}slideMaster" Target="../slideMasters/slideMaster1.xml"/></Relationships>""")
            put("ppt/slideMasters/slideMaster1.xml", """<p:sldMaster $ns>$master<p:clrMap bg1="lt1" tx1="dk1" bg2="lt2" tx2="dk2" accent1="accent1"/></p:sldMaster>""")
            put("ppt/slideMasters/_rels/slideMaster1.xml.rels", """<Relationships $rels><Relationship Id="rId1" Type="${t}theme" Target="../theme/theme1.xml"/></Relationships>""")
            theme?.let { put("ppt/theme/theme1.xml", """<a:theme $ns><a:themeElements><a:clrScheme name="t">$it</a:clrScheme></a:themeElements></a:theme>""") }
            put("ppt/media/image1.png", "PNGBYTES")
        }
        val file = File.createTempFile("folio", ".pptx").apply { deleteOnExit() }
        ZipOutputStream(file.outputStream()).use { zip -> parts.forEach { (n, b) -> zip.putNextEntry(ZipEntry(n)); zip.write(b.toByteArray()); zip.closeEntry() } }
        reader = PptxReader(ZipFile(file))
        return reader.read()
    }

    private fun tree(vararg shapes: String) = "<p:spTree>${shapes.joinToString("")}</p:spTree>"

    private fun sp(ph: String? = null, xf: String? = null, body: String = "", bodyPr: String = "<a:bodyPr/>") =
        """<p:sp><p:nvSpPr><p:cNvPr id="2" name="s"/><p:cNvSpPr/><p:nvPr>${ph ?: ""}</p:nvPr></p:nvSpPr><p:spPr>${xf ?: ""}</p:spPr><p:txBody>$bodyPr$body</p:txBody></p:sp>"""

    private fun para(text: String) = "<a:p><a:r><a:t>$text</a:t></a:r></a:p>"

    @Test fun slideOrderFollowsTheListNotFileNames() {
        val d = deck(listOf(tree(sp(xf = xfrm(0, 0, 10, 10), body = para("One"))), tree(sp(xf = xfrm(0, 0, 10, 10), body = para("Two")))), order = listOf(1, 0))
        assertEquals(12_192_000L, d.width)
        assertEquals(listOf("Two", "One"), d.slides.map { it.texts().single() })
        assertEquals(listOf(1, 2), d.slides.map { it.number })
    }

    @Test fun runsKeepSizeBoldColourAndLineBreaks() {
        val body = """<a:p><a:pPr algn="ctr"/><a:r><a:rPr sz="2400" b="1"><a:solidFill><a:srgbClr val="FF0000"/></a:solidFill></a:rPr><a:t>Big</a:t></a:r><a:br/><a:r><a:rPr i="1" u="sng"/><a:t>next</a:t></a:r></a:p>"""
        val box = deck(listOf(tree(sp(xf = xfrm(1, 2, 3, 4), body = body)))).slides[0].shapes.single() as TextBox
        assertEquals(Frame(1, 2, 3, 4), box.frame)
        val p = box.paragraphs.single()
        assertEquals(Align.Center, p.align)
        assertEquals("Big\nnext", p.text)
        assertEquals(24f, p.runs[0].sizePt)
        assertTrue(p.runs[0].bold)
        assertEquals(0xFFFF0000.toInt(), p.runs[0].color)
        assertTrue(p.runs[2].italic && p.runs[2].underline)
        assertEquals(18f, p.runs[2].sizePt) // default body size
    }

    @Test fun placeholdersInheritPositionAndStylesFromLayoutAndMaster() {
        val layout = sp(ph = """<p:ph type="title"/>""", xf = xfrm(100, 200, 300, 400)) +
            sp(ph = """<p:ph idx="1"/>""", body = """<a:lstStyle><a:lvl1pPr><a:defRPr sz="2000"/></a:lvl1pPr></a:lstStyle>""")
        val master = """<p:cSld><p:spTree>${sp(ph = """<p:ph type="body" idx="1"/>""", xf = xfrm(5, 6, 7, 8))}</p:spTree></p:cSld>
            <p:txStyles><p:titleStyle><a:lvl1pPr algn="ctr"><a:buNone/><a:defRPr sz="4400"/></a:lvl1pPr></p:titleStyle>
            <p:bodyStyle><a:lvl1pPr><a:buChar char="•"/><a:defRPr sz="2800"/></a:lvl1pPr><a:lvl2pPr><a:buChar char="§"/><a:defRPr sz="2400"/></a:lvl2pPr></p:bodyStyle></p:txStyles>"""
        val slide = tree(
            sp(ph = """<p:ph type="title"/>""", body = para("Hello")),
            sp(ph = """<p:ph idx="1"/>""", body = para("Point") + """<a:p><a:pPr lvl="1"/><a:r><a:t>Sub</a:t></a:r></a:p><a:p><a:pPr><a:buNone/></a:pPr><a:r><a:t>Plain</a:t></a:r></a:p>"""),
        )
        val s = deck(listOf(slide), layout = layout, master = master).slides[0]
        val (title, body) = s.shapes.map { it as TextBox }
        assertEquals("Hello", s.title)
        assertTrue(title.title)
        assertEquals(Frame(100, 200, 300, 400), title.frame)
        assertEquals(44f, title.paragraphs[0].runs[0].sizePt)
        assertEquals(Align.Center, title.paragraphs[0].align)
        assertNull(title.paragraphs[0].bullet)
        assertEquals(Frame(5, 6, 7, 8), body.frame) // the layout has no position -> the master's
        assertEquals(20f, body.paragraphs[0].runs[0].sizePt) // layout list style beats the master body style
        assertEquals(listOf("•", "•", null), body.paragraphs.map { it.bullet }) // Wingdings "§" -> "•"
        assertEquals(24f, body.paragraphs[1].runs[0].sizePt)
    }

    @Test fun autoNumbersAndFontScale() {
        val body = """<a:p><a:pPr><a:buAutoNum type="arabicPeriod"/></a:pPr><a:r><a:rPr sz="2000"/><a:t>a</a:t></a:r></a:p><a:p><a:pPr><a:buAutoNum type="arabicPeriod"/></a:pPr><a:r><a:t>b</a:t></a:r></a:p>"""
        val box = deck(listOf(tree(sp(xf = xfrm(0, 0, 1, 1), body = body, bodyPr = """<a:bodyPr><a:normAutofit fontScale="50000"/></a:bodyPr>""")))).slides[0].shapes.single() as TextBox
        assertEquals(listOf("1.", "2."), box.paragraphs.map { it.bullet })
        assertEquals(10f, box.paragraphs[0].runs[0].sizePt)
    }

    @Test fun groupsTransformChildren() {
        val group = """<p:grpSp><p:nvGrpSpPr><p:cNvPr id="3" name="g"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>
            <p:grpSpPr><a:xfrm><a:off x="1000" y="2000"/><a:ext cx="200" cy="200"/><a:chOff x="0" y="0"/><a:chExt cx="100" cy="100"/></a:xfrm></p:grpSpPr>
            ${sp(xf = xfrm(10, 20, 30, 40), body = para("in group"))}</p:grpSp>"""
        assertEquals(Frame(1020, 2040, 60, 80), deck(listOf(tree(group))).slides[0].shapes.single().frame)
    }

    @Test fun picturesTablesAndCharts() {
        val pic = """<p:pic><p:nvPicPr><p:cNvPr id="4" name="p"/><p:cNvPicPr/><p:nvPr/></p:nvPicPr><p:blipFill><a:blip r:embed="rIdImg"/></p:blipFill><p:spPr>${xfrm(1, 1, 5, 5)}</p:spPr></p:pic>"""
        val table = """<p:graphicFrame><p:nvGraphicFramePr><p:cNvPr id="5" name="t"/><p:cNvGraphicFramePr/><p:nvPr/></p:nvGraphicFramePr>${xfrm(9, 9, 9, 9, "p:xfrm")}
            <a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/table"><a:tbl><a:tr><a:tc><a:txBody><a:p><a:r><a:t>Q3</a:t></a:r></a:p></a:txBody></a:tc><a:tc><a:txBody><a:p><a:r><a:t>12</a:t></a:r></a:p></a:txBody></a:tc></a:tr></a:tbl></a:graphicData></a:graphic></p:graphicFrame>"""
        val chart = """<p:graphicFrame><p:nvGraphicFramePr><p:cNvPr id="6" name="c"/><p:cNvGraphicFramePr/><p:nvPr/></p:nvGraphicFramePr>${xfrm(0, 0, 1, 1, "p:xfrm")}<a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/chart"/></a:graphic></p:graphicFrame>"""
        val shapes = deck(listOf(tree(pic, table, chart))).slides[0].shapes
        val p = shapes[0] as Picture
        assertEquals("ppt/media/image1.png", p.media)
        assertArrayEquals("PNGBYTES".toByteArray(), reader.media(p.media))
        val tbl = shapes[1] as ObjectBox
        assertEquals("Table", tbl.label)
        assertEquals(listOf("Q3 | 12"), tbl.lines)
        assertEquals(Frame(9, 9, 9, 9), tbl.frame)
        assertEquals("Chart", (shapes[2] as ObjectBox).label)
    }

    @Test fun backgroundsAndThemeColours() {
        val theme = """<a:dk1><a:sysClr val="windowText" lastClr="000000"/></a:dk1><a:lt1><a:srgbClr val="FFFFFF"/></a:lt1><a:dk2><a:srgbClr val="1F2A44"/></a:dk2><a:lt2><a:srgbClr val="EEEEEE"/></a:lt2><a:accent1><a:srgbClr val="2E5AAC"/></a:accent1>"""
        val slide = """<p:bg><p:bgPr><a:solidFill><a:schemeClr val="tx2"/></a:solidFill></p:bgPr></p:bg>""" +
            tree(sp(xf = xfrm(0, 0, 1, 1), body = """<a:p><a:r><a:rPr><a:solidFill><a:schemeClr val="bg1"/></a:solidFill></a:rPr><a:t>white</a:t></a:r></a:p>"""))
        val s = deck(listOf(slide), theme = theme).slides[0]
        assertEquals(0xFF1F2A44.toInt(), s.background)
        assertEquals(0xFFFFFFFF.toInt(), (s.shapes.single() as TextBox).paragraphs[0].runs[0].color)
    }

    @Test fun relativePaths() {
        assertEquals("ppt/media/a.png", resolve("ppt/slides", "../media/a.png"))
        assertEquals("ppt/slides/slide2.xml", resolve("ppt", "slides/slide2.xml"))
        assertEquals("ppt/x.xml", resolve("ppt/slides", "/ppt/x.xml"))
    }
}
