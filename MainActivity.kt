package com.omninote.study

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val storage = StorageAndPdfManager(applicationContext)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF38BDF8),
                    secondary = Color(0xFF818CF8),
                    background = Color(0xFF0F172A),
                    surface = Color(0xFF1E293B)
                )
            ) {
                OmniNoteApp(storage)
            }
        }
    }
}

@Composable
fun OmniNoteApp(storage: StorageAndPdfManager) {
    var notebooks by remember { mutableStateOf(storage.loadAllNotebooks()) }
    var openNotebookId by remember { mutableStateOf<String?>(null) }
    var selectedCategory by remember { mutableStateOf("All") }

    val activeNotebook = notebooks.find { it.id == openNotebookId }

    if (activeNotebook == null) {
        NotebookLibraryScreen(
            notebooks = notebooks,
            selectedCategory = selectedCategory,
            onSelectCategory = { selectedCategory = it },
            onOpenNotebook = { openNotebookId = it.id },
            onCreateNotebook = { title, cat, template, mode, color ->
                val nb = StudyNotebook(
                    title = title,
                    folderCategory = cat,
                    coverColorArgb = color,
                    canvasMode = mode,
                    pages = listOf(NotePage(pageIndex = 0, template = template))
                )
                storage.saveNotebook(nb)
                notebooks = storage.loadAllNotebooks()
                openNotebookId = nb.id
            },
            onImportPdf = { uri, title, cat ->
                val nb = storage.importPdfNotebook(uri, title, cat)
                if (nb != null) {
                    notebooks = storage.loadAllNotebooks()
                    openNotebookId = nb.id
                }
            },
            onDeleteNotebook = { id ->
                storage.deleteNotebook(id)
                notebooks = storage.loadAllNotebooks()
            }
        )
    } else {
        StudyWorkspaceScreen(
            notebook = activeNotebook,
            storage = storage,
            onSaveNotebook = { updated ->
                storage.saveNotebook(updated)
                notebooks = storage.loadAllNotebooks()
            },
            onBackToLibrary = {
                openNotebookId = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotebookLibraryScreen(
    notebooks: List<StudyNotebook>,
    selectedCategory: String,
    onSelectCategory: (String) -> Unit,
    onOpenNotebook: (StudyNotebook) -> Unit,
    onCreateNotebook: (String, String, PaperTemplate, CanvasMode, Long) -> Unit,
    onImportPdf: (Uri, String, String) -> Unit,
    onDeleteNotebook: (String) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            onImportPdf(uri, "Imported Study PDF", if (selectedCategory == "All") "Physiology" else selectedCategory)
        }
    }

    val categories = remember(notebooks) {
        listOf("All", "Physiology", "Pharmacology", "Internal Med", "Clinical Notes") +
                notebooks.map { it.folderCategory }.distinct()
    }.distinct()

    val filtered = notebooks.filter { nb ->
        (selectedCategory == "All" || nb.folderCategory.equals(selectedCategory, ignoreCase = true)) &&
                (searchQuery.isBlank() || nb.title.contains(searchQuery, ignoreCase = true))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.EditNote, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(30.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("OmniNote Studio", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            Text("Unlimited Local Vector Notebooks & PDF Study Engine", fontSize = 11.sp, color = Color(0xFF94A3B8))
                        }
                    }
                },
                actions = {
                    OutlinedButton(
                        onClick = { pdfLauncher.launch("application/pdf") },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Import PDF")
                    }
                    Button(
                        onClick = { showCreateDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("New Notebook")
                    }
                    Spacer(Modifier.width(12.dp))
                }
            )
        }
    ) { padding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFF0F172A))
        ) {
            Column(
                modifier = Modifier
                    .width(230.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF1E293B))
                    .padding(14.dp)
            ) {
                Text("FOLDERS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                Spacer(Modifier.height(10.dp))
                categories.forEach { cat ->
                    val active = selectedCategory == cat
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (active) Color(0xFF0284C7).copy(alpha = 0.25f) else Color.Transparent)
                            .clickable { onSelectCategory(cat) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Folder,
                            contentDescription = null,
                            tint = if (active) Color(0xFF38BDF8) else Color(0xFF64748B),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            cat,
                            color = if (active) Color.White else Color(0xFFCBD5E1),
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                            fontSize = 14.sp
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(20.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search notebooks, chapters, or PDFs...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(Modifier.height(18.dp))

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 210.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filtered, key = { it.id }) { nb ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(235.dp)
                                .clickable { onOpenNotebook(nb) },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                        ) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(115.dp)
                                        .background(Color(nb.coverColorArgb))
                                        .padding(14.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Surface(
                                            color = Color.Black.copy(alpha = 0.28f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = if (nb.linkedPdfFileName != null) "PDF + INK" else nb.canvasMode.label,
                                                fontSize = 10.sp,
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = { onDeleteNotebook(nb.id) },
                                            modifier = Modifier.size(26.dp)
                                        ) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = Color.White.copy(alpha = 0.85f))
                                        }
                                    }
                                    Text(
                                        text = "${nb.pages.size} Pages",
                                        color = Color.White.copy(alpha = 0.9f),
                                        fontSize = 12.sp,
                                        modifier = Modifier.align(Alignment.BottomStart)
                                    )
                                }
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = nb.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = Color.White,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = nb.folderCategory,
                                            fontSize = 12.sp,
                                            color = Color(0xFF38BDF8)
                                        )
                                        Icon(Icons.Default.ArrowForward, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateNotebookDialog(
            defaultCategory = if (selectedCategory == "All") "Physiology" else selectedCategory,
            onDismiss = { showCreateDialog = false },
            onCreate = { title, cat, template, mode, color ->
                showCreateDialog = false
                onCreateNotebook(title, cat, template, mode, color)
            }
        )
    }
}

@Composable
fun CreateNotebookDialog(
    defaultCategory: String,
    onDismiss: () -> Unit,
    onCreate: (String, String, PaperTemplate, CanvasMode, Long) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(defaultCategory) }
    var selectedTemplate by remember { mutableStateOf(PaperTemplate.CORNELL) }
    var selectedMode by remember { mutableStateOf(CanvasMode.PAGINATED) }
    val coverColors = listOf(0xFF0284C7L, 0xFF0D9488L, 0xFF7C3AEDL, 0xFFE11D48L, 0xFFD97706L, 0xFF334155L)
    var selectedColor by remember { mutableStateOf(coverColors.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New Study Notebook", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Notebook Title") },
                    placeholder = { Text("e.g., Special Senses & Vestibular System") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Folder / Subject") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Canvas Mode", fontSize = 12.sp, color = Color(0xFF94A3B8))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CanvasMode.entries.forEach { mode ->
                        FilterChip(
                            selected = selectedMode == mode,
                            onClick = { selectedMode = mode },
                            label = { Text(mode.label) }
                        )
                    }
                }
                Text("Paper Template", fontSize = 12.sp, color = Color(0xFF94A3B8))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(PaperTemplate.entries) { tpl ->
                        FilterChip(
                            selected = selectedTemplate == tpl,
                            onClick = { selectedTemplate = tpl },
                            label = { Text(tpl.label) }
                        )
                    }
                }
                Text("Cover Color", fontSize = 12.sp, color = Color(0xFF94A3B8))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    coverColors.forEach { col ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(col))
                                .border(
                                    width = if (selectedColor == col) 3.dp else 0.dp,
                                    color = Color.White,
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = col }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onCreate(
                        title.ifBlank { "Untitled Study Note" },
                        category.ifBlank { "General" },
                        selectedTemplate,
                        selectedMode,
                        selectedColor
                    )
                }
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun StudyWorkspaceScreen(
    notebook: StudyNotebook,
    storage: StorageAndPdfManager,
    onSaveNotebook: (StudyNotebook) -> Unit,
    onBackToLibrary: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentNotebook by remember(notebook.id) { mutableStateOf(notebook) }
    var currentPageIdx by remember { mutableIntStateOf(0) }
    val currentPage = currentNotebook.pages.getOrElse(currentPageIdx) { currentNotebook.pages.first() }

    var activeTool by remember { mutableStateOf(ToolMode.BALLPOINT) }
    var activeColor by remember { mutableLongStateOf(0xFF1E293B) }
    var strokeWidth by remember { mutableFloatStateOf(5.5f) }
    var stylusOnlyPalmRejection by remember { mutableStateOf(false) }
    var shapeSnapEnabled by remember { mutableStateOf(true) }
    var showPageThumbnails by remember { mutableStateOf(false) }
    var showAddNoteDialog by remember { mutableStateOf(false) }

    val undoStack = remember(currentPage.id) { mutableStateListOf<List<InkStroke>>() }
    val redoStack = remember(currentPage.id) { mutableStateListOf<List<InkStroke>>() }

    var zoomScale by remember(currentPage.id) { mutableFloatStateOf(1f) }
    var panOffset by remember(currentPage.id) { mutableStateOf(Offset.Zero) }

    val activePoints = remember { mutableStateListOf<StrokePoint>() }
    var isCurrentStrokeSnapped by remember { mutableStateOf(false) }
    var holdSnapJob by remember { mutableStateOf<Job?>(null) }

    val laserPoints = remember { mutableStateListOf<StrokePoint>() }

    val lassoPolygon = remember { mutableStateListOf<StrokePoint>() }
    var selectedStrokeIds by remember(currentPage.id) { mutableStateOf<Set<String>>(emptySet()) }
    var lastLassoDragPos by remember { mutableStateOf<Offset?>(null) }

    var toolbarOffset by remember { mutableStateOf(Offset(24f, 16f)) }

    fun updateCurrentPage(transform: (NotePage) -> NotePage) {
        val updatedPages = currentNotebook.pages.toMutableList()
        val safeIdx = currentPageIdx.coerceIn(0, updatedPages.lastIndex)
        updatedPages[safeIdx] = transform(updatedPages[safeIdx])
        val updatedNb = currentNotebook.copy(pages = updatedPages, updatedAt = System.currentTimeMillis())
        currentNotebook = updatedNb
        onSaveNotebook(updatedNb)
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val savedFile = storage.importImageUri(uri)
            if (savedFile != null) {
                updateCurrentPage { pg ->
                    pg.copy(
                        images = pg.images + ImageCardItem(
                            x = 220f,
                            y = 220f,
                            localFileName = savedFile
                        )
                    )
                }
            }
        }
    }

    val pdfExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            val ok = storage.exportNotebookToPdf(currentNotebook, uri)
            Toast.makeText(
                context,
                if (ok) "Exported annotated PDF successfully!" else "PDF Export failed",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val pdfBitmap: Bitmap? = remember(currentNotebook.linkedPdfFileName, currentPage.pdfPageIndex) {
        if (currentNotebook.linkedPdfFileName != null && currentPage.pdfPageIndex >= 0) {
            storage.renderPdfPageBitmap(currentNotebook.linkedPdfFileName!!, currentPage.pdfPageIndex)
        } else null
    }

    val colorPalette = listOf(
        0xFF0F172AL,
        0xFF1D4ED8L,
        0xFFDC2626L,
        0xFF059669L,
        0xFF7C3AEDL,
        0xFFD97706L,
        0xFFFACC15L,
        0xFF4ADE80L,
        0xFFF472B6L,
        0xFFF8FAFC
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090D16))
    ) {
        Surface(
            color = Color(0xFF1E293B),
            shadowElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBackToLibrary) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Column {
                        Text(
                            text = currentNotebook.title,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1
                        )
                        Text(
                            text = if (currentNotebook.canvasMode == CanvasMode.INFINITE) {
                                "Infinite Whiteboard • Zoom ${(zoomScale * 100).toInt()}%"
                            } else {
                                "Page ${currentPageIdx + 1} of ${currentNotebook.pages.size} • ${currentPage.template.label} • ${(zoomScale * 100).toInt()}%"
                            },
                            color = Color(0xFF38BDF8),
                            fontSize = 11.sp
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = {
                            if (undoStack.isNotEmpty()) {
                                val prev = undoStack.removeLast()
                                redoStack.add(currentPage.strokes)
                                updateCurrentPage { it.copy(strokes = prev) }
                            }
                        },
                        enabled = undoStack.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Undo, contentDescription = "Undo", tint = if (undoStack.isNotEmpty()) Color.White else Color(0xFF475569))
                    }
                    IconButton(
                        onClick = {
                            if (redoStack.isNotEmpty()) {
                                val next = redoStack.removeLast()
                                undoStack.add(currentPage.strokes)
                                updateCurrentPage { it.copy(strokes = next) }
                            }
                        },
                        enabled = redoStack.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Redo, contentDescription = "Redo", tint = if (redoStack.isNotEmpty()) Color.White else Color(0xFF475569))
                    }

                    Divider(modifier = Modifier.height(22.dp).width(1.dp), color = Color(0xFF334155))

                    FilterChip(
                        selected = stylusOnlyPalmRejection,
                        onClick = { stylusOnlyPalmRejection = !stylusOnlyPalmRejection },
                        label = { Text(if (stylusOnlyPalmRejection) "Stylus Only" else "Stylus + Touch", fontSize = 11.sp) },
                        leadingIcon = {
                            Icon(
                                if (stylusOnlyPalmRejection) Icons.Default.Draw else Icons.Default.TouchApp,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )

                    FilterChip(
                        selected = shapeSnapEnabled,
                        onClick = { shapeSnapEnabled = !shapeSnapEnabled },
                        label = { Text("Shape Snap", fontSize = 11.sp) }
                    )

                    TextButton(onClick = {
                        zoomScale = 1f
                        panOffset = Offset.Zero
                    }) {
                        Text("100%", fontSize = 12.sp, color = Color(0xFFCBD5E1))
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = { showAddNoteDialog = true }) {
                        Icon(Icons.Default.StickyNote2, contentDescription = "Add Sticky Note", tint = Color(0xFFFACC15))
                    }
                    IconButton(onClick = { imagePicker.launch("image/*") }) {
                        Icon(Icons.Default.Image, contentDescription = "Add Image", tint = Color(0xFF38BDF8))
                    }
                    IconButton(onClick = {
                        val nextOrd = (currentPage.template.ordinal + 1) % PaperTemplate.entries.size
                        val nextTpl = PaperTemplate.entries[nextOrd]
                        updateCurrentPage { it.copy(template = nextTpl) }
                    }) {
                        Icon(Icons.Default.GridOn, contentDescription = "Switch Template", tint = Color.White)
                    }
                    IconButton(onClick = {
                        updateCurrentPage { it.copy(showMarginLine = !it.showMarginLine) }
                    }) {
                        Icon(
                            Icons.Default.VerticalSplit,
                            contentDescription = "Toggle Margin Line",
                            tint = if (currentPage.showMarginLine) Color(0xFFF43F5E) else Color(0xFF64748B)
                        )
                    }
                    IconButton(onClick = { showPageThumbnails = !showPageThumbnails }) {
                        Icon(Icons.Default.ViewSidebar, contentDescription = "Pages Drawer", tint = Color.White)
                    }
                    IconButton(onClick = {
                        pdfExportLauncher.launch("${currentNotebook.title.replace(" ", "_")}.pdf")
                    }) {
                        Icon(Icons.Default.IosShare, contentDescription = "Export PDF", tint = Color(0xFF4ADE80))
                    }
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(currentPage.id, activeTool) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            if (activeTool == ToolMode.PAN || zoom != 1f) {
                                zoomScale = (zoomScale * zoom).coerceIn(0.45f, 4.5f)
                                panOffset += pan
                            }
                        }
                    }
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInteropFilter { event ->
                            if (event.pointerCount > 1) {
                                holdSnapJob?.cancel()
                                activePoints.clear()
                                return@pointerInteropFilter false
                            }

                            val toolType = event.getToolType(0)
                            val isStylus = toolType == MotionEvent.TOOL_TYPE_STYLUS || toolType == MotionEvent.TOOL_TYPE_ERASER

                            if (stylusOnlyPalmRejection && !isStylus && activeTool != ToolMode.PAN) {
                                return@pointerInteropFilter false
                            }

                            val effectiveTool = if (toolType == MotionEvent.TOOL_TYPE_ERASER ||
                                (event.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0
                            ) {
                                ToolMode.ERASER_STROKE
                            } else {
                                activeTool
                            }

                            val worldX = (event.x - panOffset.x) / zoomScale
                            val worldY = (event.y - panOffset.y) / zoomScale
                            val pressure = event.pressure.coerceIn(0.05f, 1f)
                            val tilt = event.getAxisValue(MotionEvent.AXIS_TILT)

                            when (event.actionMasked) {
                                MotionEvent.ACTION_DOWN -> {
                                    val tappedTape = currentPage.strokes.findLast { s ->
                                        s.tool == ToolMode.TAPE &&
                                                GeometryHelper.strokeBounds(s.points).inflate(22f).contains(Offset(worldX, worldY))
                                    }
                                    if (tappedTape != null && effectiveTool != ToolMode.ERASER_STROKE && effectiveTool != ToolMode.TAPE) {
                                        updateCurrentPage { pg ->
                                            pg.copy(
                                                strokes = pg.strokes.map { s ->
                                                    if (s.id == tappedTape.id) s.copy(isTapeHidden = !s.isTapeHidden) else s
                                                }
                                            )
                                        }
                                        return@pointerInteropFilter true
                                    }

                                    when (effectiveTool) {
                                        ToolMode.PAN -> {
                                            lastLassoDragPos = Offset(event.x, event.y)
                                        }
                                        ToolMode.LASSO -> {
                                            val tapPt = Offset(worldX, worldY)
                                            val selectedBounds = currentPage.strokes
                                                .filter { it.id in selectedStrokeIds }
                                                .map { GeometryHelper.strokeBounds(it.points) }
                                                .fold(Rect.Zero) { acc, r -> if (acc == Rect.Zero) r else acc.expandToInclude(r) }

                                            if (selectedStrokeIds.isNotEmpty() && selectedBounds.inflate(25f).contains(tapPt)) {
                                                lastLassoDragPos = tapPt
                                            } else {
                                                selectedStrokeIds = emptySet()
                                                lassoPolygon.clear()
                                                lassoPolygon.add(StrokePoint(worldX, worldY))
                                                lastLassoDragPos = null
                                            }
                                        }
                                        ToolMode.ERASER_STROKE -> {
                                            val remaining = currentPage.strokes.filterNot { stroke ->
                                                stroke.points.any { pt ->
                                                    hypot(pt.x - worldX, pt.y - worldY) < max(24f, stroke.baseWidth)
                                                }
                                            }
                                            if (remaining.size != currentPage.strokes.size) {
                                                undoStack.add(currentPage.strokes)
                                                redoStack.clear()
                                                updateCurrentPage { it.copy(strokes = remaining) }
                                            }
                                        }
                                        ToolMode.ERASER_PIXEL -> {
                                            undoStack.add(currentPage.strokes)
                                            redoStack.clear()
                                        }
                                        ToolMode.LASER -> {
                                            laserPoints.clear()
                                            laserPoints.add(StrokePoint(worldX, worldY))
                                        }
                                        else -> {
                                            isCurrentStrokeSnapped = false
                                            activePoints.clear()
                                            activePoints.add(StrokePoint(worldX, worldY, pressure, tilt))
                                        }
                                    }
                                    true
                                }

                                MotionEvent.ACTION_MOVE -> {
                                    when (effectiveTool) {
                                        ToolMode.PAN -> {
                                            lastLassoDragPos?.let { prev ->
                                                panOffset += Offset(event.x - prev.x, event.y - prev.y)
                                            }
                                            lastLassoDragPos = Offset(event.x, event.y)
                                        }
                                        ToolMode.LASSO -> {
                                            val currPt = Offset(worldX, worldY)
                                            if (lastLassoDragPos != null && selectedStrokeIds.isNotEmpty()) {
                                                val dx = currPt.x - lastLassoDragPos!!.x
                                                val dy = currPt.y - lastLassoDragPos!!.y
                                                updateCurrentPage { pg ->
                                                    pg.copy(
                                                        strokes = pg.strokes.map { s ->
                                                            if (s.id in selectedStrokeIds) {
                                                                s.copy(points = s.points.map { p -> p.copy(x = p.x + dx, y = p.y + dy) })
                                                            } else s
                                                        }
                                                    )
                                                }
                                                lastLassoDragPos = currPt
                                            } else {
                                                lassoPolygon.add(StrokePoint(worldX, worldY))
                                            }
                                        }
                                        ToolMode.ERASER_STROKE -> {
                                            val remaining = currentPage.strokes.filterNot { stroke ->
                                                stroke.points.any { pt ->
                                                    hypot(pt.x - worldX, pt.y - worldY) < max(24f, stroke.baseWidth)
                                                }
                                            }
                                            if (remaining.size != currentPage.strokes.size) {
                                                undoStack.add(currentPage.strokes)
                                                redoStack.clear()
                                                updateCurrentPage { it.copy(strokes = remaining) }
                                            }
                                        }
                                        ToolMode.ERASER_PIXEL -> {
                                            val radius = 22f
                                            val carved = mutableListOf<InkStroke>()
                                            for (s in currentPage.strokes) {
                                                val filteredPts = s.points.filter { pt ->
                                                    hypot(pt.x - worldX, pt.y - worldY) > radius
                                                }
                                                if (filteredPts.size >= 2) {
                                                    carved.add(s.copy(points = filteredPts))
                                                }
                                            }
                                            updateCurrentPage { it.copy(strokes = carved) }
                                        }
                                        ToolMode.LASER -> {
                                            laserPoints.add(StrokePoint(worldX, worldY))
                                        }
                                        else -> {
                                            for (h in 0 until event.historySize) {
                                                val hx = (event.getHistoricalX(h) - panOffset.x) / zoomScale
                                                val hy = (event.getHistoricalY(h) - panOffset.y) / zoomScale
                                                val hp = event.getHistoricalPressure(h).coerceIn(0.05f, 1f)
                                                if (!isCurrentStrokeSnapped) {
                                                    activePoints.add(StrokePoint(hx, hy, hp, tilt))
                                                }
                                            }
                                            val newPt = StrokePoint(worldX, worldY, pressure, tilt)
                                            val lastPt = activePoints.lastOrNull()

                                            if (!isCurrentStrokeSnapped) {
                                                activePoints.add(newPt)
                                            }

                                            if (shapeSnapEnabled && lastPt != null && GeometryHelper.distance(lastPt, newPt) > 4.5f) {
                                                holdSnapJob?.cancel()
                                                holdSnapJob = scope.launch {
                                                    delay(450)
                                                    if (activePoints.size >= 6 && !isCurrentStrokeSnapped) {
                                                        val snapped = GeometryHelper.snapStrokeToGeometricShape(activePoints.toList())
                                                        activePoints.clear()
                                                        activePoints.addAll(snapped)
                                                        isCurrentStrokeSnapped = true
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    true
                                }

                                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                    holdSnapJob?.cancel()
                                    when (effectiveTool) {
                                        ToolMode.LASSO -> {
                                            if (lassoPolygon.size >= 3 && selectedStrokeIds.isEmpty()) {
                                                val poly = lassoPolygon.toList()
                                                val matched = currentPage.strokes.filter { stroke ->
                                                    stroke.points.any { pt ->
                                                        GeometryHelper.isPointInsidePolygon(Offset(pt.x, pt.y), poly)
                                                    }
                                                }.map { it.id }.toSet()
                                                selectedStrokeIds = matched
                                            }
                                            lassoPolygon.clear()
                                            lastLassoDragPos = null
                                        }
                                        ToolMode.LASER -> {
                                            scope.launch {
                                                delay(650)
                                                laserPoints.clear()
                                            }
                                        }
                                        ToolMode.BALLPOINT, ToolMode.FOUNTAIN, ToolMode.PENCIL, ToolMode.HIGHLIGHTER, ToolMode.TAPE -> {
                                            if (activePoints.size >= 2) {
                                                undoStack.add(currentPage.strokes)
                                                redoStack.clear()
                                                val finalWidth = when (effectiveTool) {
                                                    ToolMode.HIGHLIGHTER -> max(24f, strokeWidth * 3.2f)
                                                    ToolMode.TAPE -> max(34f, strokeWidth * 4.5f)
                                                    else -> strokeWidth
                                                }
                                                val newStroke = InkStroke(
                                                    tool = effectiveTool,
                                                    colorArgb = activeColor,
                                                    baseWidth = finalWidth,
                                                    points = activePoints.toList(),
                                                    isTapeHidden = true
                                                )
                                                updateCurrentPage { it.copy(strokes = it.strokes + newStroke) }
                                            }
                                            activePoints.clear()
                                            isCurrentStrokeSnapped = false
                                        }
                                        else -> {}
                                    }
                                    true
                                }
                                else -> false
                            }
                        }
                ) {
                    drawContext.canvas.save()
                    drawContext.canvas.translate(panOffset.x, panOffset.y)
                    drawContext.canvas.scale(zoomScale, zoomScale)

                    val pageW = if (currentNotebook.canvasMode == CanvasMode.INFINITE) 4200f else 1200f
                    val pageH = if (currentNotebook.canvasMode == CanvasMode.INFINITE) 4200f else 1697f

                    drawStudyPaperTemplate(
                        page = currentPage,
                        pageWidth = pageW,
                        pageHeight = pageH,
                        isInfinite = currentNotebook.canvasMode == CanvasMode.INFINITE,
                        pdfBitmap = pdfBitmap
                    )

                    val isDarkPaper = currentPage.template == PaperTemplate.DARK_GRID
                    currentPage.strokes.forEach { stroke ->
                        drawInkStroke(
                            stroke = stroke,
                            isSelected = stroke.id in selectedStrokeIds,
                            isDarkPaper = isDarkPaper
                        )
                    }

                    if (activePoints.size >= 2) {
                        val previewWidth = when (activeTool) {
                            ToolMode.HIGHLIGHTER -> max(24f, strokeWidth * 3.2f)
                            ToolMode.TAPE -> max(34f, strokeWidth * 4.5f)
                            else -> strokeWidth
                        }
                        drawInkStroke(
                            stroke = InkStroke(
                                tool = activeTool,
                                colorArgb = activeColor,
                                baseWidth = previewWidth,
                                points = activePoints
                            ),
                            isSelected = false,
                            isDarkPaper = isDarkPaper
                        )
                    }

                    if (laserPoints.size >= 2) {
                        val laserPath = buildSmoothPath(laserPoints)
                        drawPath(
                            path = laserPath,
                            color = Color(0xFFFF1744).copy(alpha = 0.4f),
                            style = Stroke(width = 16f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                        drawPath(
                            path = laserPath,
                            color = Color.White,
                            style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }

                    if (lassoPolygon.size >= 2) {
                        val lassoPath = buildSmoothPath(lassoPolygon)
                        drawPath(
                            path = lassoPath,
                            color = Color(0xFF38BDF8),
                            style = Stroke(
                                width = 3f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)
                            )
                        )
                    }

                    drawContext.canvas.restore()
                }

                currentPage.images.forEach { imgItem ->
                    val bmp = storage.loadImageBitmap(imgItem.localFileName)
                    if (bmp != null) {
                        val screenX = (imgItem.x * zoomScale + panOffset.x).roundToInt()
                        val screenY = (imgItem.y * zoomScale + panOffset.y).roundToInt()
                        Box(
                            modifier = Modifier
                                .offset { IntOffset(screenX, screenY) }
                                .size(
                                    width = (imgItem.width * zoomScale).dp / 2.2f,
                                    height = (imgItem.height * zoomScale).dp / 2.2f
                                )
                                .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                .clip(RoundedCornerShape(8.dp))
                                .pointerInput(imgItem.id) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        updateCurrentPage { pg ->
                                            pg.copy(
                                                images = pg.images.map {
                                                    if (it.id == imgItem.id) {
                                                        it.copy(
                                                            x = it.x + dragAmount.x / zoomScale,
                                                            y = it.y + dragAmount.y / zoomScale
                                                        )
                                                    } else it
                                                }
                                            )
                                        }
                                    }
                                }
                        ) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize()
                            )
                            IconButton(
                                onClick = {
                                    updateCurrentPage { pg -> pg.copy(images = pg.images.filterNot { it.id == imgItem.id }) }
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(24.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Remove Image", tint = Color.White, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }

                currentPage.stickyNotes.forEach { note ->
                    val screenX = (note.x * zoomScale + panOffset.x).roundToInt()
                    val screenY = (note.y * zoomScale + panOffset.y).roundToInt()
                    Card(
                        modifier = Modifier
                            .offset { IntOffset(screenX, screenY) }
                            .width(240.dp)
                            .shadow(8.dp, RoundedCornerShape(12.dp))
                            .pointerInput(note.id) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    updateCurrentPage { pg ->
                                        pg.copy(
                                            stickyNotes = pg.stickyNotes.map {
                                                if (it.id == note.id) {
                                                    it.copy(
                                                        x = it.x + dragAmount.x / zoomScale,
                                                        y = it.y + dragAmount.y / zoomScale
                                                    )
                                                } else it
                                            }
                                        )
                                    }
                                }
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(note.bgColorArgb))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("STUDY NOTE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Delete Note",
                                    tint = Color(0xFF475569),
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable {
                                            updateCurrentPage { pg ->
                                                pg.copy(stickyNotes = pg.stickyNotes.filterNot { it.id == note.id })
                                            }
                                        }
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(note.text, fontSize = 13.sp, color = Color(0xFF0F172A))
                        }
                    }
                }
            }

            if (selectedStrokeIds.isNotEmpty()) {
                Surface(
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(50),
                    shadowElevation = 10.dp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 90.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("${selectedStrokeIds.size} Selected", color = Color(0xFF38BDF8), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        TextButton(onClick = {
                            undoStack.add(currentPage.strokes)
                            updateCurrentPage { pg ->
                                pg.copy(
                                    strokes = pg.strokes.map { s ->
                                        if (s.id in selectedStrokeIds) s.copy(colorArgb = activeColor) else s
                                    }
                                )
                            }
                        }) {
                            Text("Recolor", color = Color.White)
                        }
                        TextButton(onClick = {
                            undoStack.add(currentPage.strokes)
                            val copies = currentPage.strokes.filter { it.id in selectedStrokeIds }.map { s ->
                                s.copy(
                                    id = java.util.UUID.randomUUID().toString(),
                                    points = s.points.map { pt -> pt.copy(x = pt.x + 35f, y = pt.y + 35f) }
                                )
                            }
                            updateCurrentPage { pg -> pg.copy(strokes = pg.strokes + copies) }
                            selectedStrokeIds = copies.map { it.id }.toSet()
                        }) {
                            Text("Duplicate", color = Color.White)
                        }
                        TextButton(onClick = {
                            undoStack.add(currentPage.strokes)
                            updateCurrentPage { pg ->
                                pg.copy(strokes = pg.strokes.filterNot { it.id in selectedStrokeIds })
                            }
                            selectedStrokeIds = emptySet()
                        }) {
                            Text("Delete", color = Color(0xFFF43F5E))
                        }
                    }
                }
            }

            Surface(
                color = Color(0xFF1E293B).copy(alpha = 0.96f),
                shape = RoundedCornerShape(22.dp),
                shadowElevation = 14.dp,
                border = BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier
                    .offset { IntOffset(toolbarOffset.x.roundToInt(), toolbarOffset.y.roundToInt()) }
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            toolbarOffset += dragAmount
                        }
                    }
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        item {
                            Icon(
                                Icons.Default.DragIndicator,
                                contentDescription = "Drag Toolbar",
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        items(ToolMode.entries) { tool ->
                            val selected = activeTool == tool
                            FilterChip(
                                selected = selected,
                                onClick = { activeTool = tool },
                                label = { Text(tool.label, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF0284C7),
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        colorPalette.forEach { col ->
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color(col))
                                    .border(
                                        width = if (activeColor == col) 2.5.dp else 1.dp,
                                        color = if (activeColor == col) Color(0xFF38BDF8) else Color(0xFF475569),
                                        shape = CircleShape
                                    )
                                    .clickable { activeColor = col }
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("${strokeWidth.toInt()}px", color = Color(0xFFCBD5E1), fontSize = 11.sp)
                        Slider(
                            value = strokeWidth,
                            onValueChange = { strokeWidth = it },
                            valueRange = 2f..28f,
                            modifier = Modifier.width(140.dp)
                        )
                    }
                }
            }

            if (currentNotebook.canvasMode == CanvasMode.PAGINATED) {
                Surface(
                    color = Color(0xFF1E293B).copy(alpha = 0.95f),
                    shape = RoundedCornerShape(50),
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        IconButton(
                            onClick = { if (currentPageIdx > 0) currentPageIdx-- },
                            enabled = currentPageIdx > 0,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Page", tint = Color.White)
                        }
                        Text(
                            "Page ${currentPageIdx + 1} / ${currentNotebook.pages.size}",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        IconButton(
                            onClick = { if (currentPageIdx < currentNotebook.pages.lastIndex) currentPageIdx++ },
                            enabled = currentPageIdx < currentNotebook.pages.lastIndex,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "Next Page", tint = Color.White)
                        }
                        Button(
                            onClick = {
                                val updatedPages = currentNotebook.pages.toMutableList()
                                val newPage = NotePage(
                                    pageIndex = currentPageIdx + 1,
                                    template = currentPage.template,
                                    showMarginLine = currentPage.showMarginLine
                                )
                                updatedPages.add(currentPageIdx + 1, newPage)
                                val reindexed = updatedPages.mapIndexed { idx, p -> p.copy(pageIndex = idx) }
                                val updatedNb = currentNotebook.copy(pages = reindexed)
                                currentNotebook = updatedNb
                                onSaveNotebook(updatedNb)
                                currentPageIdx++
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Insert Page", fontSize = 12.sp)
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = showPageThumbnails,
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Surface(
                    color = Color(0xFF1E293B),
                    shadowElevation = 16.dp,
                    modifier = Modifier
                        .width(260.dp)
                        .fillMaxHeight()
                ) {
                    Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("PAGE MANAGER", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                            IconButton(onClick = { showPageThumbnails = false }) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = Color.White)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            itemsIndexed(currentNotebook.pages) { idx, pg ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { currentPageIdx = idx },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (idx == currentPageIdx) Color(0xFF0284C7) else Color(0xFF0F172A)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                "Page ${idx + 1}",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                "${pg.template.label} • ${pg.strokes.size} strokes",
                                                color = Color(0xFFCBD5E1),
                                                fontSize = 11.sp
                                            )
                                        }
                                        Row {
                                            IconButton(
                                                onClick = {
                                                    val updated = currentNotebook.pages.toMutableList()
                                                    updated[idx] = pg.copy(isBookmarked = !pg.isBookmarked)
                                                    val nb = currentNotebook.copy(pages = updated)
                                                    currentNotebook = nb
                                                    onSaveNotebook(nb)
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    if (pg.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                                    contentDescription = "Bookmark",
                                                    tint = if (pg.isBookmarked) Color(0xFFFACC15) else Color.White
                                                )
                                            }
                                            if (currentNotebook.pages.size > 1) {
                                                IconButton(
                                                    onClick = {
                                                        val updated = currentNotebook.pages.toMutableList().apply { removeAt(idx) }
                                                        val nb = currentNotebook.copy(pages = updated)
                                                        currentNotebook = nb
                                                        onSaveNotebook(nb)
                                                        currentPageIdx = currentPageIdx.coerceAtMost(updated.lastIndex)
                                                    },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Page", tint = Color(0xFFF43F5E))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddNoteDialog) {
        var noteText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddNoteDialog = false },
            title = { Text("Add Floating Study Note") },
            text = {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    placeholder = { Text("Type high-yield clinical pearl, definition, or formula...") },
                    modifier = Modifier.fillMaxWidth().height(140.dp)
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (noteText.isNotBlank()) {
                        updateCurrentPage { pg ->
                            pg.copy(
                                stickyNotes = pg.stickyNotes + StickyNoteItem(
                                    x = 240f,
                                    y = 200f,
                                    text = noteText
                                )
                            )
                        }
                    }
                    showAddNoteDialog = false
                }) {
                    Text("Add to Page")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddNoteDialog = false }) { Text("Cancel") }
            }
        )
    }
}

private fun DrawScope.drawStudyPaperTemplate(
    page: NotePage,
    pageWidth: Float,
    pageHeight: Float,
    isInfinite: Boolean,
    pdfBitmap: Bitmap?
) {
    val isDark = page.template == PaperTemplate.DARK_GRID
    val bgColor = if (isDark) Color(0xFF18181B) else Color(0xFFFAF8F5)
    val lineColor = if (isDark) Color(0xFF2E2E38) else Color(0xFFCBD5E1)

    drawRoundRect(
        color = Color.Black.copy(alpha = 0.45f),
        topLeft = Offset(14f, 14f),
        size = Size(pageWidth, pageHeight),
        cornerRadius = CornerRadius(12f, 12f)
    )
    drawRect(
        color = bgColor,
        topLeft = Offset.Zero,
        size = Size(pageWidth, pageHeight)
    )

    if (pdfBitmap != null) {
        drawImage(
            image = pdfBitmap.asImageBitmap(),
            topLeft = Offset.Zero
        )
    }

    val spacing = 44f
    when (page.template) {
        PaperTemplate.BLANK -> {}
        PaperTemplate.RULED -> {
            var y = 130f
            while (y < pageHeight - 80f) {
                drawLine(lineColor, Offset(0f, y), Offset(pageWidth, y), strokeWidth = 1.5f)
                y += spacing
            }
        }
        PaperTemplate.CORNELL -> {
            drawLine(lineColor, Offset(0f, 120f), Offset(pageWidth, 120f), strokeWidth = 2.5f)
            val summaryY = pageHeight - 280f
            drawLine(lineColor, Offset(0f, summaryY), Offset(pageWidth, summaryY), strokeWidth = 2.5f)
            drawLine(Color(0xFFF43F5E).copy(alpha = 0.6f), Offset(310f, 120f), Offset(310f, summaryY), strokeWidth = 2.5f)
            var y = 168f
            while (y < summaryY) {
                drawLine(lineColor, Offset(310f, y), Offset(pageWidth, y), strokeWidth = 1.4f)
                y += spacing
            }
        }
        PaperTemplate.GRID, PaperTemplate.DARK_GRID -> {
            var x = spacing
            while (x < pageWidth) {
                drawLine(lineColor, Offset(x, 0f), Offset(x, pageHeight), strokeWidth = 1.2f)
                x += spacing
            }
            var y = spacing
            while (y < pageHeight) {
                drawLine(lineColor, Offset(0f, y), Offset(pageWidth, y), strokeWidth = 1.2f)
                y += spacing
            }
        }
        PaperTemplate.DOTTED -> {
            var x = spacing
            while (x < pageWidth) {
                var y = spacing
                while (y < pageHeight) {
                    drawCircle(lineColor, radius = 2.2f, center = Offset(x, y))
                    y += spacing
                }
                x += spacing
            }
        }
    }

    if (page.showMarginLine && !isInfinite && page.template != PaperTemplate.CORNELL) {
        drawLine(
            color = Color(0xFFF43F5E).copy(alpha = 0.65f),
            start = Offset(page.marginX, 0f),
            end = Offset(page.marginX, pageHeight),
            strokeWidth = 2.4f
        )
    }
}

private fun DrawScope.drawInkStroke(
    stroke: InkStroke,
    isSelected: Boolean,
    isDarkPaper: Boolean
) {
    val pts = stroke.points
    if (pts.size < 2) return

    val baseColor = Color(stroke.colorArgb)

    when (stroke.tool) {
        ToolMode.FOUNTAIN -> {
            for (i in 1 until pts.size) {
                val p0 = pts[i - 1]
                val p1 = pts[i]
                val pressureFactor = ((p0.pressure + p1.pressure) * 0.5f).coerceIn(0.2f, 1.35f)
                val segWidth = stroke.baseWidth * (0.45f + pressureFactor * 0.95f)
                drawLine(
                    color = baseColor,
                    start = Offset(p0.x, p0.y),
                    end = Offset(p1.x, p1.y),
                    strokeWidth = segWidth,
                    cap = StrokeCap.Round
                )
            }
        }
        ToolMode.HIGHLIGHTER -> {
            val path = buildSmoothPath(pts)
            drawPath(
                path = path,
                color = baseColor.copy(alpha = 0.36f),
                style = Stroke(width = stroke.baseWidth, cap = StrokeCap.Square, join = StrokeJoin.Round),
                blendMode = if (isDarkPaper) BlendMode.Screen else BlendMode.Multiply
            )
        }
        ToolMode.TAPE -> {
            val path = buildSmoothPath(pts)
            if (stroke.isTapeHidden) {
                drawPath(
                    path = path,
                    color = baseColor.copy(alpha = 0.97f),
                    style = Stroke(width = stroke.baseWidth, cap = StrokeCap.Butt, join = StrokeJoin.Round)
                )
            } else {
                drawPath(
                    path = path,
                    color = baseColor.copy(alpha = 0.18f),
                    style = Stroke(width = stroke.baseWidth, cap = StrokeCap.Butt, join = StrokeJoin.Round)
                )
            }
        }
        ToolMode.PENCIL -> {
            val path = buildSmoothPath(pts)
            drawPath(
                path = path,
                color = baseColor.copy(alpha = 0.78f),
                style = Stroke(
                    width = stroke.baseWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
        else -> {
            val path = buildSmoothPath(pts)
            drawPath(
                path = path,
                color = baseColor,
                style = Stroke(width = stroke.baseWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }

    if (isSelected) {
        val b = GeometryHelper.strokeBounds(pts).inflate(8f)
        drawRect(
            color = Color(0xFF38BDF8),
            topLeft = b.topLeft,
            size = b.size,
            style = Stroke(
                width = 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
            )
        )
    }
}

private fun buildSmoothPath(points: List<StrokePoint>): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points.first().x, points.first().y)
    for (i in 1 until points.size) {
        val prev = points[i - 1]
        val cur = points[i]
        path.quadraticBezierTo(prev.x, prev.y, (prev.x + cur.x) * 0.5f, (prev.y + cur.y) * 0.5f)
    }
    path.lineTo(points.last().x, points.last().y)
    return path
}

private fun Rect.expandToInclude(other: Rect): Rect {
    return Rect(
        left = min(this.left, other.left),
        top = min(this.top, other.top),
        right = max(this.right, other.right),
        bottom = max(this.bottom, other.bottom)
    )
}
