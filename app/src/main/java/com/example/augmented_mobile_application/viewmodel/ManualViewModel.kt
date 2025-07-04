package com.example.augmented_mobile_application.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.augmented_mobile_application.core.ResourceAdministrator
import com.example.augmented_mobile_application.core.ResourcePool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "ManualViewModel"
private const val MAX_CACHE_SIZE = 5 // Maximum number of pages to keep in memory

class ManualViewModel : ViewModel() {
    private val _manualState = MutableStateFlow<ManualState>(ManualState.Loading)
    val manualState: StateFlow<ManualState> = _manualState.asStateFlow()

    // For backward compatibility with existing code
    private val _pdfPages = MutableStateFlow<List<Bitmap>>(emptyList())
    val pdfPages = _pdfPages.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _pageCount = MutableStateFlow(0)
    val pageCount = _pageCount.asStateFlow()

    // Cache for rendered pages with resource management
    private val pageCache = ConcurrentHashMap<Int, Bitmap>()
    
    // Resource management
    private var resourceAdmin: ResourceAdministrator? = null
    private var bitmapPool: ResourcePool<Bitmap>? = null
    private var resourceHandle: ManagedResourceHandle<ManualViewModel>? = null
    
    // PDF document resources
    private var currentRenderer: PdfRenderer? = null
    private var currentFileDescriptor: ParcelFileDescriptor? = null
    private var tempFile: File? = null

    private fun initializeResourceManagement(context: Context) {
        if (resourceAdmin == null) {
            resourceAdmin = ResourceAdministrator.getInstance(context)
            
            // Register this ViewModel for resource management
            resourceHandle = resourceAdmin?.registerResource(
                resourceId = "manual_viewmodel_${hashCode()}",
                resource = this,
                priority = ResourceAdministrator.ResourcePriority.NORMAL,
                onCleanup = { clearResources() }
            )
            
            // Create bitmap pool for efficient bitmap reuse
            bitmapPool = resourceAdmin?.getResourcePool(
                poolName = "pdf_bitmaps",
                maxSize = MAX_CACHE_SIZE * 2,
                factory = { 
                    Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888)
                },
                reset = { bitmap ->
                    bitmap.eraseColor(Color.WHITE)
                },
                dispose = { bitmap ->
                    if (!bitmap.isRecycled) {
                        bitmap.recycle()
                    }
                }
            )
            
            // Register memory watcher for PDF operations
            resourceAdmin?.registerMemoryWatcher(
                watcherName = "pdf_memory_watcher",
                thresholdMB = 100, // Alert if PDF operations use more than 100MB
                onThresholdExceeded = {
                    Log.w(TAG, "PDF memory usage high - cleaning cache")
                    clearCache()
                }
            )
        }
    }

    fun displayPdf(context: Context, pdfName: String) {
        // Initialize resource management on first use
        initializeResourceManagement(context)
        
        _isLoading.value = true
        _errorMessage.value = null
        _manualState.value = ManualState.Loading

        viewModelScope.launch {
            try {
                Log.d(TAG, "Loading PDF with name: $pdfName")
                initializePdfDocument(context, pdfName)
                _manualState.value = ManualState.Initialized(_pageCount.value)
                // Load first page initially to show something quickly
                if (_pageCount.value > 0) {
                    getPageAtIndex(0)
                }
                Log.d(TAG, "Successfully initialized PDF with ${_pageCount.value} pages")
            } catch (e: Exception) {
                val errorMsg = "Error loading PDF: ${e.localizedMessage ?: "Unknown error"}"
                Log.e(TAG, errorMsg, e)
                _errorMessage.value = errorMsg
                _manualState.value = ManualState.Error(errorMsg)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun initializePdfDocument(context: Context, pdfName: String) = withContext(Dispatchers.IO) {
        clearResources() // Clear any previous resources

        try {
            // Get input stream from assets - handle path structure
            val assetManager = context.assets
            
            // Handle both formats: with and without subfolder
            val assetPath = if (pdfName.contains("/")) {
                "$pdfName.pdf"
            } else {
                "$pdfName/$pdfName.pdf" // Add subfolder path
            }
            
            Log.d(TAG, "Attempting to load PDF from assets path: $assetPath")
            
            // Try to open the file
            val inputStream = try {
                assetManager.open(assetPath)
            } catch (e: IOException) {
                // Fallback to just the PDF name if subfolder approach fails
                Log.w(TAG, "Failed to load from path $assetPath, trying direct filename $pdfName.pdf")
                assetManager.open("$pdfName.pdf")
            }

            // Create a temporary file
            val tempFile = File(context.cacheDir, "${pdfName.replace("/", "_")}.pdf")
            this@ManualViewModel.tempFile = tempFile
            val outputStream = FileOutputStream(tempFile)

            // Copy asset to temp file
            inputStream.copyTo(outputStream)
            inputStream.close()
            outputStream.close()

            // Open PDF file for rendering
            val fileDescriptor = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
            currentFileDescriptor = fileDescriptor

            val renderer = PdfRenderer(fileDescriptor)
            currentRenderer = renderer

            // Update page count
            _pageCount.value = renderer.pageCount
            Log.d(TAG, "PDF initialized with ${renderer.pageCount} pages")
        } catch (e: IOException) {
            Log.e(TAG, "Error loading PDF", e)
            throw IOException("Failed to load PDF: ${e.message}", e)
        }
    }
    
    /**
     * Gets the bitmap for a specific page index.
     * Uses caching for improved performance.
     */
    suspend fun getPageAtIndex(pageIndex: Int): Bitmap? = withContext(Dispatchers.IO) {
        if (pageIndex < 0 || pageIndex >= _pageCount.value) {
            Log.e(TAG, "Invalid page index: $pageIndex")
            return@withContext null
        }
        
        // Return cached page if available
        pageCache[pageIndex]?.let { return@withContext it }
        
        try {
            val renderer = currentRenderer ?: return@withContext null
            val page = renderer.openPage(pageIndex)
            
            // Create bitmap with appropriate dimensions and density
            val bitmap = Bitmap.createBitmap(
                page.width * 2, 
                page.height * 2,
                Bitmap.Config.ARGB_8888
            )
            
            // Render the page with high quality
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            
            // Manage cache size by removing oldest entries if needed
            if (pageCache.size >= MAX_CACHE_SIZE) {
                val oldestKey = pageCache.keys().asSequence()
                    .filter { it != pageIndex }
                    .firstOrNull()
                oldestKey?.let { pageCache.remove(it) }
            }
            
            // Cache the newly rendered page
            pageCache[pageIndex] = bitmap
            return@withContext bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error rendering page $pageIndex", e)
            return@withContext null
        }
    }
    
    /**
     * Creates a PDF document that can be used for annotation or modification.
     * This demonstrates using PdfDocument for creation rather than just rendering.
     */
    fun createAnnotatedPdf(context: Context, annotations: List<PdfAnnotation>): File? {
        try {
            val pdfDocument = PdfDocument()
            val renderer = currentRenderer ?: return null
            
            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                
                // Create a new page in the document
                val pageInfo = PdfDocument.PageInfo.Builder(
                    page.width, page.height, i
                ).create()
                
                val documentPage = pdfDocument.startPage(pageInfo)
                val canvas = documentPage.canvas
                
                // Draw the original page content
                val pageBitmap = Bitmap.createBitmap(
                    page.width, page.height, Bitmap.Config.ARGB_8888
                )
                page.render(pageBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                canvas.drawBitmap(pageBitmap, 0f, 0f, null)
                
                // Add annotations for this page
                val pageAnnotations = annotations.filter { it.pageIndex == i }
                drawAnnotations(canvas, pageAnnotations)
                
                pdfDocument.finishPage(documentPage)
                page.close()
            }
            
            // Save the document to a new file
            val outputFile = File(context.cacheDir, "annotated_pdf_${System.currentTimeMillis()}.pdf")
            pdfDocument.writeTo(FileOutputStream(outputFile))
            pdfDocument.close()
            
            return outputFile
        } catch (e: Exception) {
            Log.e(TAG, "Error creating annotated PDF", e)
            return null
        }
    }
    
    private fun drawAnnotations(canvas: Canvas, annotations: List<PdfAnnotation>) {
        val paint = Paint().apply {
            color = Color.RED
            strokeWidth = 5f
            style = Paint.Style.STROKE
        }
        
        val textPaint = Paint().apply {
            color = Color.RED
            textSize = 30f
        }
        
        for (annotation in annotations) {
            when (annotation) {
                is PdfAnnotation.Rectangle -> {
                    canvas.drawRect(
                        annotation.x, 
                        annotation.y, 
                        annotation.x + annotation.width, 
                        annotation.y + annotation.height, 
                        paint
                    )
                }
                is PdfAnnotation.Text -> {
                    canvas.drawText(annotation.text, annotation.x, annotation.y, textPaint)
                }
            }
        }
    }

    fun clearCache() {
        pageCache.clear()
    }

    private fun clearResources() {
        clearCache()
        currentRenderer?.close()
        currentFileDescriptor?.close()
        currentRenderer = null
        currentFileDescriptor = null
        tempFile?.delete()
        tempFile = null
    }

    override fun onCleared() {
        super.onCleared()
        clearResources()
    }
}

sealed class PdfAnnotation {
    abstract val pageIndex: Int
    
    data class Rectangle(
        override val pageIndex: Int,
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float
    ) : PdfAnnotation()
    
    data class Text(
        override val pageIndex: Int,
        val x: Float,
        val y: Float,
        val text: String
    ) : PdfAnnotation()
}