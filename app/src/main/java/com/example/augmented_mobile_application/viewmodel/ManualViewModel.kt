package com.example.augmented_mobile_application.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

private const val TAG = "ManualViewModel"

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

    private var currentRenderer: PdfRenderer? = null
    private var currentFileDescriptor: ParcelFileDescriptor? = null

    fun displayPdf(context: Context, pdfName: String) {
        _isLoading.value = true
        _errorMessage.value = null
        _manualState.value = ManualState.Loading

        viewModelScope.launch {
            try {
                Log.d(TAG, "Loading PDF with name: $pdfName")
                val pdfBitmaps = loadPdfFromAssets(context, pdfName)
                _pdfPages.value = pdfBitmaps
                _manualState.value = ManualState.Success(pdfBitmaps)
                Log.d(TAG, "Successfully loaded PDF with ${pdfBitmaps.size} pages")
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

    private suspend fun loadPdfFromAssets(context: Context, pdfName: String): List<Bitmap> = withContext(Dispatchers.IO) {
        clearResources() // Clear any previous resources

        try {
            // Get input stream from assets - fix for path structure
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

            val pageCount = renderer.pageCount
            Log.d(TAG, "PDF loaded with $pageCount pages")
            
            val bitmaps = mutableListOf<Bitmap>()

            // Render each page as a bitmap
            for (i in 0 until pageCount) {
                val page = renderer.openPage(i)
                val bitmap = Bitmap.createBitmap(
                    page.width * 2, page.height * 2,
                    Bitmap.Config.ARGB_8888
                )
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmaps.add(bitmap)
                page.close()
            }

            bitmaps
        } catch (e: IOException) {
            Log.e(TAG, "Error loading PDF", e)
            throw IOException("Failed to load PDF: ${e.message}", e)
        }
    }

    private fun clearResources() {
        currentRenderer?.close()
        currentFileDescriptor?.close()
        currentRenderer = null
        currentFileDescriptor = null
    }

    override fun onCleared() {
        super.onCleared()
        clearResources()
    }
}