package com.omninote.study

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class ToolMode(val label: String) {
    BALLPOINT("Ballpoint"),
    FOUNTAIN("Fountain"),
    PENCIL("Pencil"),
    HIGHLIGHTER("Highlighter"),
    TAPE("Recall Tape"),
    LASER("Laser"),
    ERASER_STROKE("Stroke Eraser"),
    ERASER_PIXEL("Point Eraser"),
    LASSO("Lasso Select"),
    PAN("Pan / Read")
}

enum class PaperTemplate(val label: String) {
    BLANK("Blank"),
    RULED("College Ruled"),
    CORNELL("Cornell Notes"),
    GRID("Square Grid"),
    DOTTED("Dotted Grid"),
    DARK_GRID("Dark Grid")
}

enum class CanvasMode(val label: String) {
    PAGINATED("Paginated A4"),
    INFINITE("Infinite Canvas")
}

data class StrokePoint(
    val x: Float,
    val y: Float,
    val pressure: Float = 0.5f,
    val tilt: Float = 0f
)

data class InkStroke(
    val id: String = UUID.randomUUID().toString(),
    val tool: ToolMode,
    val colorArgb: Long,
    val baseWidth: Float,
    val points: List<StrokePoint>,
    val isTapeHidden: Boolean = true
)

data class StickyNoteItem(
    val id: String = UUID.randomUUID().toString(),
    val x: Float,
    val y: Float,
    val width: Float = 260f,
    val height: Float = 180f,
    val text: String,
    val bgColorArgb: Long = 0xFFFFF59D
)

data class ImageCardItem(
    val id: String = UUID.randomUUID().toString(),
    val x: Float,
    val y: Float,
    val width: Float = 360f,
    val height: Float = 280f,
    val localFileName: String
)

data class NotePage(
    val id: String = UUID.randomUUID().toString(),
    val pageIndex: Int,
    val pdfPageIndex: Int = -1,
    val template: PaperTemplate = PaperTemplate.CORNELL,
    val showMarginLine: Boolean = true,
    val marginX: Float = 140f,
    val isBookmarked: Boolean = false,
    val strokes: List<InkStroke> = emptyList(),
    val stickyNotes: List<StickyNoteItem> = emptyList(),
    val images: List<ImageCardItem> = emptyList()
)

data class StudyNotebook(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val folderCategory: String = "Physiology",
    val coverColorArgb: Long = 0xFF2563EB,
    val canvasMode: CanvasMode = CanvasMode.PAGINATED,
    val linkedPdfFileName: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    val pages: List<NotePage> = listOf(NotePage(pageIndex = 0))
)

object NoteSerializer {
    fun toJson(notebook: StudyNotebook): String {
        val root = JSONObject()
        root.put("id", notebook.id)
        root.put("title", notebook.title)
        root.put("folderCategory", notebook.folderCategory)
        root.put("coverColorArgb", notebook.coverColorArgb)
        root.put("canvasMode", notebook.canvasMode.name)
        root.put("linkedPdfFileName", notebook.linkedPdfFileName ?: "")
        root.put("updatedAt", notebook.updatedAt)

        val pagesArr = JSONArray()
        notebook.pages.forEach { page ->
            val pObj = JSONObject()
            pObj.put("id", page.id)
            pObj.put("pageIndex", page.pageIndex)
            pObj.put("pdfPageIndex", page.pdfPageIndex)
            pObj.put("template", page.template.name)
            pObj.put("showMarginLine", page.showMarginLine)
            pObj.put("marginX", page.marginX.toDouble())
            pObj.put("isBookmarked", page.isBookmarked)

            val strokesArr = JSONArray()
            page.strokes.forEach { stroke ->
                val sObj = JSONObject()
                sObj.put("id", stroke.id)
                sObj.put("tool", stroke.tool.name)
                sObj.put("colorArgb", stroke.colorArgb)
                sObj.put("baseWidth", stroke.baseWidth.toDouble())
                sObj.put("isTapeHidden", stroke.isTapeHidden)
                val ptsArr = JSONArray()
                stroke.points.forEach { pt ->
                    val ptObj = JSONObject()
                    ptObj.put("x", pt.x.toDouble())
                    ptObj.put("y", pt.y.toDouble())
                    ptObj.put("p", pt.pressure.toDouble())
                    ptObj.put("t", pt.tilt.toDouble())
                    ptsArr.put(ptObj)
                }
                sObj.put("points", ptsArr)
                strokesArr.put(sObj)
            }
            pObj.put("strokes", strokesArr)

            val notesArr = JSONArray()
            page.stickyNotes.forEach { note ->
                val nObj = JSONObject()
                nObj.put("id", note.id)
                nObj.put("x", note.x.toDouble())
                nObj.put("y", note.y.toDouble())
                nObj.put("width", note.width.toDouble())
                nObj.put("height", note.height.toDouble())
                nObj.put("text", note.text)
                nObj.put("bgColorArgb", note.bgColorArgb)
                notesArr.put(nObj)
            }
            pObj.put("stickyNotes", notesArr)

            val imgsArr = JSONArray()
            page.images.forEach { img ->
                val iObj = JSONObject()
                iObj.put("id", img.id)
                iObj.put("x", img.x.toDouble())
                iObj.put("y", img.y.toDouble())
                iObj.put("width", img.width.toDouble())
                iObj.put("height", img.height.toDouble())
                iObj.put("localFileName", img.localFileName)
                imgsArr.put(iObj)
            }
            pObj.put("images", imgsArr)
            pagesArr.put(pObj)
        }
        root.put("pages", pagesArr)
        return root.toString()
    }

    fun fromJson(jsonStr: String): StudyNotebook {
        val root = JSONObject(jsonStr)
        val pagesArr = root.optJSONArray("pages") ?: JSONArray()
        val pages = mutableListOf<NotePage>()

        for (i in 0 until pagesArr.length()) {
            val pObj = pagesArr.getJSONObject(i)
            val strokesArr = pObj.optJSONArray("strokes") ?: JSONArray()
            val strokes = mutableListOf<InkStroke>()
            for (j in 0 until strokesArr.length()) {
                val sObj = strokesArr.getJSONObject(j)
                val ptsArr = sObj.optJSONArray("points") ?: JSONArray()
                val pts = mutableListOf<StrokePoint>()
                for (k in 0 until ptsArr.length()) {
                    val ptObj = ptsArr.getJSONObject(k)
                    pts.add(
                        StrokePoint(
                            x = ptObj.getDouble("x").toFloat(),
                            y = ptObj.getDouble("y").toFloat(),
                            pressure = ptObj.optDouble("p", 0.5).toFloat(),
                            tilt = ptObj.optDouble("t", 0.0).toFloat()
                        )
                    )
                }
                strokes.add(
                    InkStroke(
                        id = sObj.getString("id"),
                        tool = runCatching { ToolMode.valueOf(sObj.getString("tool")) }.getOrDefault(ToolMode.BALLPOINT),
                        colorArgb = sObj.getLong("colorArgb"),
                        baseWidth = sObj.getDouble("baseWidth").toFloat(),
                        points = pts,
                        isTapeHidden = sObj.optBoolean("isTapeHidden", true)
                    )
                )
            }

            val notesArr = pObj.optJSONArray("stickyNotes") ?: JSONArray()
            val stickyNotes = mutableListOf<StickyNoteItem>()
            for (j in 0 until notesArr.length()) {
                val nObj = notesArr.getJSONObject(j)
                stickyNotes.add(
                    StickyNoteItem(
                        id = nObj.getString("id"),
                        x = nObj.getDouble("x").toFloat(),
                        y = nObj.getDouble("y").toFloat(),
                        width = nObj.optDouble("width", 260.0).toFloat(),
                        height = nObj.optDouble("height", 180.0).toFloat(),
                        text = nObj.getString("text"),
                        bgColorArgb = nObj.optLong("bgColorArgb", 0xFFFFF59D)
                    )
                )
            }

            val imgsArr = pObj.optJSONArray("images") ?: JSONArray()
            val images = mutableListOf<ImageCardItem>()
            for (j in 0 until imgsArr.length()) {
                val iObj = imgsArr.getJSONObject(j)
                images.add(
                    ImageCardItem(
                        id = iObj.getString("id"),
                        x = iObj.getDouble("x").toFloat(),
                        y = iObj.getDouble("y").toFloat(),
                        width = iObj.optDouble("width", 360.0).toFloat(),
                        height = iObj.optDouble("height", 280.0).toFloat(),
                        localFileName = iObj.getString("localFileName")
                    )
                )
            }

            pages.add(
                NotePage(
                    id = pObj.getString("id"),
                    pageIndex = pObj.getInt("pageIndex"),
                    pdfPageIndex = pObj.optInt("pdfPageIndex", -1),
                    template = runCatching { PaperTemplate.valueOf(pObj.getString("template")) }.getOrDefault(PaperTemplate.CORNELL),
                    showMarginLine = pObj.optBoolean("showMarginLine", true),
                    marginX = pObj.optDouble("marginX", 140.0).toFloat(),
                    isBookmarked = pObj.optBoolean("isBookmarked", false),
                    strokes = strokes,
                    stickyNotes = stickyNotes,
                    images = images
                )
            )
        }

        val pdfFile = root.optString("linkedPdfFileName", "")
        return StudyNotebook(
            id = root.getString("id"),
            title = root.getString("title"),
            folderCategory = root.optString("folderCategory", "General"),
            coverColorArgb = root.optLong("coverColorArgb", 0xFF2563EB),
            canvasMode = runCatching { CanvasMode.valueOf(root.optString("canvasMode")) }.getOrDefault(CanvasMode.PAGINATED),
            linkedPdfFileName = pdfFile.ifBlank { null },
            updatedAt = root.optLong("updatedAt", System.currentTimeMillis()),
            pages = if (pages.isEmpty()) listOf(NotePage(pageIndex = 0)) else pages
        )
    }
}
