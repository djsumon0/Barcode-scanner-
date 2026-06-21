package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scanned_barcodes")
data class BarcodeEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val barcodeValue: String,
    val sessionLabel: String,
    val timestamp: Long = System.currentTimeMillis(),
    val barcodeType: String = "CODE_128"
)
