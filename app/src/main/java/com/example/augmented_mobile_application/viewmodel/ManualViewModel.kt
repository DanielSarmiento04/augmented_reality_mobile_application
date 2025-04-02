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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class ManualViewModel : ViewModel() {

    private val _pdfPages = MutableStateFlow<List<Bitmap>>(emptyList())
    val pdfPages: StateFlow<List<Bitmap>> = _pdfPages

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    fun displayPdf(context: Context, pdfName: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            try {
                // Ensure pdfName doesn't already have .pdf extension
                val cleanPdfName = pdfName.removeSuffix(".pdf")
                val pdfPages = renderPdfFromAssets(context, cleanPdfName)
                _pdfPages.value = pdfPages
            } catch (e: IOException) {
                Log.e("ManualViewModel", "Error loading PDF: ${e.message}", e)
                _errorMessage.value = "Error al cargar el PDF: ${e.message ?: "Archivo no encontrado"}"
            } catch (e: Exception) {
                Log.e("ManualViewModel", "Unexpected error: ${e.message}", e)
                _errorMessage.value = "Error inesperado: ${e.message ?: "Error desconocido"}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun renderPdfFromAssets(context: Context, pdfName: String): List<Bitmap> = withContext(Dispatchers.IO) {
        // Generate safe filename for the cache
        val cacheFileName = pdfName.replace("/", "_") + ".pdf"
        val file = File(context.cacheDir, cacheFileName)
        
        // Log the asset path we're trying to open
        val assetPath = "$pdfName.pdf"
        Log.d("ManualViewModel", "Attempting to open asset: $assetPath")
        
        try {
            // Copy the PDF from assets to the temp file
            context.assets.open(assetPath).use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: IOException) {
            // Try alternative path if the first one fails
            val alternativePath = if (pdfName.contains("/")) pdfName else "pump/$pdfName"
            Log.d("ManualViewModel", "First path failed, trying alternative: $alternativePath.pdf")
            
            context.assets.open("$alternativePath.pdf").use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
        }
        
        // List all files in assets for debugging
        val assetFiles = context.assets.list("")
        Log.d("ManualViewModel", "Assets in root: ${assetFiles?.joinToString()}")
        if (assetFiles?.contains("pump") == true) {
            val pumpFiles = context.assets.list("pump")
            Log.d("ManualViewModel", "Assets in pump folder: ${pumpFiles?.joinToString()}")
        }
        
        // Create a PDF renderer
        val fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val pdfRenderer = PdfRenderer(fileDescriptor)
        
        Log.d("ManualViewModel", "PDF loaded successfully with ${pdfRenderer.pageCount} pages")
        
        // Render each page as a bitmap
        val pages = mutableListOf<Bitmap>()
        for (i in 0 until pdfRenderer.pageCount) {
            val page = pdfRenderer.openPage(i)
            val bitmap = Bitmap.createBitmap(
                page.width * 2, 
                page.height * 2, 
                Bitmap.Config.ARGB_8888
            )
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            pages.add(bitmap)
            page.close()
        }
        
        // Close resources
        pdfRenderer.close()
        fileDescriptor.close()
        
        return@withContext pages
    }
    
    override fun onCleared() {
        super.onCleared()
        // Clean up any resources when ViewModel is cleared
        _pdfPages.value.forEach { bitmap ->
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
    }
}