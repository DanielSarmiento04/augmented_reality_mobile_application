package com.example.augmented_mobile_application.viewmodel

import android.util.Log
import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class ManualViewModel : ViewModel() {

    private val _pdfPages = MutableStateFlow<List<Bitmap>>(emptyList())
    val pdfPages: StateFlow<List<Bitmap>> = _pdfPages

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    fun displayPdf (context: Context, pdfName: String) {
        Log.d("ManualViewModel", "Verify the path.")


        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Convert the asset file path
                val formattedName = pdfName.lowercase().replace(" ", "_")
                val formattedFullNameFile = formattedName + ".pdf"
                val assetPath = "$formattedName/$formattedFullNameFile"
                Log.d("ManualViewModel", assetPath)

                // Open the PDF file from assets
                val assetManager = context.assets
                val inputStream = assetManager.open(assetPath)

                // Save it to a temporary file to use PdfRenderer
                val tempFile = File(context.cacheDir, formattedName)
                FileOutputStream(tempFile).use { output ->
                    inputStream.copyTo(output)
                }

                // Convert File to ParcelFileDescriptor
                val parcelFileDescriptor = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)

                // Use PdfRenderer to open the file
                val pdfRenderer = PdfRenderer(parcelFileDescriptor)
                val bitmaps = mutableListOf<Bitmap>()

                // Convert each page to a Bitmap
                for (pageIndex in 0 until pdfRenderer.pageCount) {
                    val page = pdfRenderer.openPage(pageIndex)
                    val bitmap = Bitmap.createBitmap(
                        page.width,
                        page.height,
                        Bitmap.Config.ARGB_8888
                    )
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmaps.add(bitmap)
                    page.close()
                }
                pdfRenderer.close()

                // Update the flow
                _pdfPages.emit(bitmaps)
            } catch (e: Exception) {
                _errorMessage.emit("Error loading PDF: ${e.localizedMessage}")
            }
        }


    }
}