package com.example.data

import kotlinx.coroutines.flow.Flow

class BarcodeRepository(private val barcodeDao: BarcodeDao) {
    val allBarcodes: Flow<List<BarcodeEntity>> = barcodeDao.getAllBarcodes()

    suspend fun insertBarcode(barcode: BarcodeEntity) {
        barcodeDao.insertBarcode(barcode)
    }

    suspend fun insertBarcodes(barcodes: List<BarcodeEntity>) {
        barcodeDao.insertBarcodes(barcodes)
    }

    suspend fun deleteBarcodeById(id: Int) {
        barcodeDao.deleteBarcodeById(id)
    }

    suspend fun deleteBarcodeBySession(sessionLabel: String) {
        barcodeDao.deleteBarcodeBySession(sessionLabel)
    }

    suspend fun clearAll() {
        barcodeDao.clearAll()
    }
}
