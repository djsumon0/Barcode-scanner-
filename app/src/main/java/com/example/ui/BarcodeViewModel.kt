package com.example.ui

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.BarcodeEntity
import com.example.data.BarcodeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.media.AudioManager
import android.media.ToneGenerator

data class DetectedBarcodeOverlay(
    val value: String,
    val boundingBox: Rect?
)

class BarcodeViewModel(private val repository: BarcodeRepository) : ViewModel() {

    private var toneGenerator: ToneGenerator? = null

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun playBeepSound() {
        try {
            if (toneGenerator == null) {
                toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
            }
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Live list of all barcodes saved in the database
    val savedBarcodes: StateFlow<List<BarcodeEntity>> = repository.allBarcodes
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Barcode overlays in the current camera frame for visual bounding box feedback
    private val _activeOverlays = MutableStateFlow<List<DetectedBarcodeOverlay>>(emptyList())
    val activeOverlays: StateFlow<List<DetectedBarcodeOverlay>> = _activeOverlays.asStateFlow()

    // Temporary list of barcode values scanned/discovered in the active session but not yet saved
    private val _scannedSessionBatch = MutableStateFlow<Set<String>>(emptySet())
    val scannedSessionBatch: StateFlow<Set<String>> = _scannedSessionBatch.asStateFlow()

    // Control pause/resume of the ML Kit Analyzer logic
    private val _isScannerActive = MutableStateFlow(true)
    val isScannerActive: StateFlow<Boolean> = _isScannerActive.asStateFlow()

    // Active session display label
    private val _currentSessionLabel = MutableStateFlow("")
    val currentSessionLabel: StateFlow<String> = _currentSessionLabel.asStateFlow()

    init {
        generateNewSessionLabel()
    }

    fun generateNewSessionLabel() {
        val formatter = SimpleDateFormat("dd-MMM-yyyy HH:mm", Locale.getDefault())
        _currentSessionLabel.value = "Batch " + formatter.format(Date())
    }

    fun toggleScanner(active: Boolean) {
        _isScannerActive.value = active
        if (!active) {
            _activeOverlays.value = emptyList()
        }
    }

    // Called when the ML Kit analyzer identifies barcodes in the current video frame
    fun onBarcodesAnalyzed(detectedList: List<DetectedBarcodeOverlay>) {
        if (!_isScannerActive.value) return

        // Update real-time HUD overlays
        _activeOverlays.value = detectedList

        // Add newly spotted unique values to our current session buffer
        val newlyDiscovered = detectedList.map { it.value }.filter { it.isNotBlank() }
        if (newlyDiscovered.isNotEmpty()) {
            val updated = _scannedSessionBatch.value.toMutableSet()
            var addedNew = false
            for (code in newlyDiscovered) {
                if (updated.add(code)) {
                    addedNew = true
                }
            }
            if (addedNew) {
                _scannedSessionBatch.value = updated
                playBeepSound()
            }
        }
    }

    // Removes a temporary barcode from the active scan session buffer prior to saving
    fun removeValueFromActiveBatch(value: String) {
        val updated = _scannedSessionBatch.value.toMutableSet()
        if (updated.remove(value)) {
            _scannedSessionBatch.value = updated
        }
    }

    // Clears the active scan buffer
    fun clearActiveBatch() {
        _scannedSessionBatch.value = emptySet()
        _activeOverlays.value = emptyList()
    }

    // Commits all barcodes currently in the active scan session buffer into Room database
    fun saveActiveBatchToDatabase(context: Context, customLabel: String? = null) {
        val batchToSave = _scannedSessionBatch.value
        if (batchToSave.isEmpty()) {
            Toast.makeText(context, "No barcodes scanned to save!", Toast.LENGTH_SHORT).show()
            return
        }

        val finalLabel = if (!customLabel.isNullOrBlank()) {
            customLabel.trim()
        } else {
            _currentSessionLabel.value.ifBlank { "Quick Batch" }
        }

        viewModelScope.launch {
            val entities = batchToSave.map { value ->
                BarcodeEntity(
                    barcodeValue = value,
                    sessionLabel = finalLabel,
                    timestamp = System.currentTimeMillis()
                )
            }
            repository.insertBarcodes(entities)
            clearActiveBatch()
            generateNewSessionLabel()
            Toast.makeText(context, "Saved ${entities.size} barcodes to database successfully!", Toast.LENGTH_SHORT).show()
        }
    }

    // Deletes a single barcode item by ID from the database
    fun deleteBarcode(barcode: BarcodeEntity) {
        viewModelScope.launch {
            repository.deleteBarcodeById(barcode.id)
        }
    }

    // Deletes an entire batch of barcodes by session label
    fun deleteSessionBatch(sessionLabel: String) {
        viewModelScope.launch {
            repository.deleteBarcodeBySession(sessionLabel)
        }
    }

    // Deletes everything from the local database
    fun clearDatabaseHistory() {
        viewModelScope.launch {
            repository.clearAll()
        }
    }

    // Exports a list of BarcodeEntity objects to a CSV file and triggers Android Share dialog
    fun exportToCSV(context: Context, barcodesToExport: List<BarcodeEntity>, customFileName: String? = null) {
        if (barcodesToExport.isEmpty()) {
            Toast.makeText(context, "No saved data to export!", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            // Build CSV Content
            val csvBuilder = StringBuilder()
            // Headers
            csvBuilder.append("\"ID\",\"Barcode Value\",\"Batch/Session\",\"Scan Time\",\"Format Type\"\n")

            val timeFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            for (item in barcodesToExport) {
                val formattedTime = timeFormatter.format(Date(item.timestamp))
                // Escape values to prevent injections
                val cleanValue = item.barcodeValue.replace("\"", "\"\"")
                val cleanLabel = item.sessionLabel.replace("\"", "\"\"")
                
                csvBuilder.append("${item.id},")
                csvBuilder.append("\"$cleanValue\",")
                csvBuilder.append("\"$cleanLabel\",")
                csvBuilder.append("\"$formattedTime\",")
                csvBuilder.append("\"${item.barcodeType}\"\n")
            }

            // Write to local cache file
            val prefix = customFileName?.trim()?.replace(Regex("[^a-zA-Z0-9_-]"), "_") ?: "barcodes_export"
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val tempFile = File(context.cacheDir, "${prefix}_$timestamp.csv")
            
            tempFile.writeText(csvBuilder.toString())

            // Share via FileProvider
            val fileUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                tempFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, fileUri)
                putExtra(Intent.EXTRA_SUBJECT, "Code 128 Scan Export")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(Intent.createChooser(shareIntent, "Share CSV Report"))

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Export error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            toneGenerator?.release()
            toneGenerator = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

class BarcodeViewModelFactory(private val repository: BarcodeRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BarcodeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return BarcodeViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
