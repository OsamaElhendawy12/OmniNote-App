package com.omninote.study

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

class StorageAndPdfManager(private val context: Context) {

    private val notebooksDir = File(context.filesDir, "notebooks").apply { mkdirs() }
    private val mediaDir = File(context.filesDir, "media").apply { mkdirs() }
    private val bitmapCache = mutableMapOf<String, Bitmap>()

    fun loadAllNotebooks(): List<StudyNotebook> {
        val list = notebooksDir.listFiles { f -> f.extension == "json" }?.mapNotNull { file ->
            runCatching { NoteSerializer.fromJson(file.readText()) }.getOrNull()
        }?.sortedByDescending { it.updatedAt } ?: emptyList()

        if (list.isEmpty()) {
            val sample = StudyNotebook(
                title = "Neurophysiology & Basal Ganglia",
                folderCategory = "Physiology",
                coverColorArgb = 0xFF0D9488,
                pages = listOf(
                    NotePage(
                        pageIndex = 0,
                        template = PaperTemplate.CORNELL,
                        stickyNotes = listOf(
                            StickyNoteItem(
                                x = 180f,
                                y = 120f,
                                width = 320f,
                                height = 160f,
                                text = "Direct Pathway: Facilitates movement (D1 excitement)\nIndirect Pathway: Inhibits unwanted movement (D2 inhibition)"
                            )
                        )
                    )
                )
            )
            saveNotebook(sample)
            return listOf(sample)
        }
        return list
    }

    fun saveNotebook(notebook: StudyNotebook) {
        val file = File(notebooksDir, "${notebook.id}.json")
        file.writeText(NoteSerializer.toJson(notebook.copy(updatedAt = System.currentTimeMillis())))
    }

    fun deleteNotebook(id: String) {
        File(notebooksDir, "$id.json").delete()
    }

    fun importImageUri(uri: Uri): String? {
        return runCatching {
            val fileName = "img_${UUID.randomUUID()}.png"
            val destFile = File(mediaDir, fileName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            fileName
        }.getOrNull()
    }

    fun loadImageBitmap(fileName: String): Bitmap? {
        bitmapCache[fileName]?.let { return it }
        val file = File(mediaDir, fileName)
        if (!file.exists()) return null
        val bmp = BitmapFactory.decodeFile(file.absolutePath)
        if (bmp != null) bitmapCache[fileName] = bmp
        return bmp
    }

    fun importPdfNotebook(uri: Uri, title: String, category: String): StudyNotebook? {
        return runCatching {
            val pdfFileName = "pdf_${UUID.randomUUID()}.pdf"
            val destFile = File(mediaDir, pdfFileName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }

            val pfd = ParcelFileDescriptor.open(destFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            val count = renderer.pageCount
            renderer.close()
            pfd.close()

            val pages = (0 until max(1, count)).map { idx ->
                NotePage(
                    pageIndex = idx,
                    pdfPageIndex = idx,
                    template = PaperTemplate.BLANK,
                    showMarginLine = false
                )
            }

            val nb = StudyNotebook(
                title = title,
                folderCategory = category,
                coverColorArgb = 0xFF7C3AED,
                linkedPdfFileName = pdfFileName,
                pages = pages
            )
            saveNotebook(nb)
            nb
        }.getOrNull()
    }

    fun renderPdfPageBitmap(pdfFileName: String, pdfPageIdx: Int, targetWidth: Int = 1200): Bitmap? {
        val cacheKey = "${pdfFileName}_$pdfPageIdx"
        bitmapCache[cacheKey]?.let { return it }

        val file = File(mediaDir, pdfFileName)
        if (!file.exists() || pdfPageIdx < 0) return null

        return runCatching {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            if (pdfPageIdx >= renderer.pageCount) {
                renderer.close()
                pfd.close()
                return null
            }
            val page = renderer.openPage(pdfPageIdx)
            val aspect = page.height.toFloat() / max(1, page.width).toFloat()
            val targetHeight = (targetWidth * aspect).toInt()
            val bmp = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(Color.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            renderer.close()
            pfd.close()
            bitmapCache[cacheKey] = bmp
            bmp
        }.getOrNull()
    }

    fun exportNotebookToPdf(notebook: StudyNotebook, outUri: Uri): Boolean {
        return runCatching {
            val pdfDoc = PdfDocument()
            val pageWidth = 1200
            val pageHeight = 1697

            notebook.pages.forEachIndexed { idx, page ->
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, idx + 1).create()
                val pdfPage = pdfDoc.startPage(pageInfo)
                val canvas = pdfPage.canvas

                val isDark = page.template == PaperTemplate.DARK_GRID
                canvas.drawColor(if (isDark) Color.parseColor("#18181B") else Color.parseColor("#FAF8F5"))

                if (notebook.linkedPdfFileName != null && page.pdfPageIndex >= 0) {
                    val pdfBmp = renderPdfPageBitmap(notebook.linkedPdfFileName, page.pdfPageIndex, pageWidth)
                    if (pdfBmp != null) {
                        val destRect = Rect(0, 0, pageWidth, min(pageHeight, pdfBmp.height))
                        canvas.drawBitmap(pdfBmp, null, destRect, null)
                    }
                }

                val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (isDark) Color.parseColor("#2E2E36") else Color.parseColor("#CBD5E1")
                    strokeWidth = 1.5f
                }
                if (page.template == PaperTemplate.RULED || page.template == PaperTemplate.CORNELL) {
                    var y = 140f
                    while (y < pageHeight - 100f) {
                        canvas.drawLine(0f, y, pageWidth.toFloat(), y, linePaint)
                        y += 44f
                    }
                }
                if (page.showMarginLine) {
                    val marginPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#F43F5E")
                        strokeWidth = 2.5f
                    }
                    canvas.drawLine(page.marginX, 0f, page.marginX, pageHeight.toFloat(), marginPaint)
                }

                page.images.forEach { img ->
                    loadImageBitmap(img.localFileName)?.let { bmp ->
                        val dst = RectF(img.x, img.y, img.x + img.width, img.y + img.height)
                        canvas.drawBitmap(bmp, null, dst, null)
                    }
                }

                page.strokes.forEach { stroke ->
                    if (stroke.points.size >= 2) {
                        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = stroke.colorArgb.toInt()
                            style = Paint.Style.STROKE
                            strokeCap = if (stroke.tool == ToolMode.HIGHLIGHTER || stroke.tool == ToolMode.TAPE) Paint.Cap.SQUARE else Paint.Cap.ROUND
                            strokeJoin = Paint.Join.ROUND
                            strokeWidth = stroke.baseWidth
                            if (stroke.tool == ToolMode.HIGHLIGHTER) {
                                alpha = 95
                            }
                        }
                        val path = Path()
                        path.moveTo(stroke.points.first().x, stroke.points.first().y)
                        for (i in 1 until stroke.points.size) {
                            val prev = stroke.points[i - 1]
                            val cur = stroke.points[i]
                            path.quadTo(prev.x, prev.y, (prev.x + cur.x) / 2f, (prev.y + cur.y) / 2f)
                        }
                        canvas.drawPath(path, paint)
                    }
                }

                page.stickyNotes.forEach { note ->
                    val rect = RectF(note.x, note.y, note.x + note.width, note.y + note.height)
                    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = note.bgColorArgb.toInt()
                        style = Paint.Style.FILL
                    }
                    canvas.drawRoundRect(rect, 16f, 16f, bgPaint)
                    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#1E293B")
                        textSize = 22f
                    }
                    var ty = note.y + 38f
                    note.text.split("\n").forEach { line ->
                        canvas.drawText(line, note.x + 18f, ty, textPaint)
                        ty += 28f
                    }
                }

                pdfDoc.finishPage(pdfPage)
            }

            context.contentResolver.openOutputStream(outUri)?.use { out ->
                pdfDoc.writeTo(out)
            }
            pdfDoc.close()
            true
        }.getOrDefault(false)
    }
}
