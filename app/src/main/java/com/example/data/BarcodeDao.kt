package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BarcodeDao {
    @Query("SELECT * FROM scanned_barcodes ORDER BY timestamp DESC")
    fun getAllBarcodes(): Flow<List<BarcodeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBarcode(barcode: BarcodeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBarcodes(barcodes: List<BarcodeEntity>)

    @Query("DELETE FROM scanned_barcodes WHERE id = :id")
    suspend fun deleteBarcodeById(id: Int)

    @Query("DELETE FROM scanned_barcodes WHERE sessionLabel = :sessionLabel")
    suspend fun deleteBarcodeBySession(sessionLabel: String)

    @Query("DELETE FROM scanned_barcodes")
    suspend fun clearAll()
}
