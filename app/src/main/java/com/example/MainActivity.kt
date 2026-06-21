package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.data.BarcodeDatabase
import com.example.data.BarcodeRepository
import com.example.ui.BarcodeViewModelFactory
import com.example.ui.HomeDashboard
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize Room Database, DAO and Repository
        val database = BarcodeDatabase.getDatabase(this)
        val dao = database.barcodeDao()
        val repository = BarcodeRepository(dao)
        val viewModelFactory = BarcodeViewModelFactory(repository)

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    HomeDashboard(factory = viewModelFactory)
                }
            }
        }
    }
}
