package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.BarcodeEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeDashboard(
    factory: BarcodeViewModelFactory,
    modifier: Modifier = Modifier
) {
    val viewModel: BarcodeViewModel = viewModel(factory = factory)
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    val currentSessionLabel by viewModel.currentSessionLabel.collectAsState()
    val activeBatch by viewModel.scannedSessionBatch.collectAsState()
    val savedBarcodes by viewModel.savedBarcodes.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var renameActiveSession by remember { mutableStateOf(false) }
    var sessionNameInput by remember { mutableStateOf("") }

    // Synchronize text-input with latest auto-generated batch label
    LaunchedEffect(currentSessionLabel) {
        if (!renameActiveSession) {
            sessionNameInput = currentSessionLabel
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Smart Multi-Code Scanner",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Code 128 Real-Time Batch Scanner",
                            fontSize = 11.sp,
                            color = Color(0xFFA0A0C0)
                        )
                    }
                },
                navigationIcon = {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = "Barcode scanning",
                        tint = Color(0xFF00E676),
                        modifier = Modifier
                            .padding(start = 16.dp, end = 12.dp)
                            .size(28.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF13131A),
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF0C0C10),
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            // VIEWPORT: Camera Feed (top portion of the screen, taking ~42% space)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.42f)
                    .background(Color.Black)
            ) {
                BarcodeScannerView(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // CONTROLS SHEET: Dashboard, lists and configs (bottom portion, taking ~58% space)
            Card(
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF161622)),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.58f)
                    .border(
                        width = 1.dp,
                        color = Color(0x1EFFFFFF),
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                    )
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Navigation Tabs: Active Session Scans Vs Saved History database
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color(0xFF13131E),
                        contentColor = Color.White,
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                color = Color(0xFF00E676),
                                height = 3.dp
                            )
                        }
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.QrCodeScanner,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Active (${activeBatch.size})",
                                        fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Saved History",
                                        fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        )
                    }

                    // Content of selected view
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        if (selectedTab == 0) {
                            // TAB 1: ACTIVE SCANS MANAGER
                            Column(modifier = Modifier.fillMaxSize()) {
                                // Batch session name layout
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF20202F), RoundedCornerShape(12.dp))
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    if (renameActiveSession) {
                                        OutlinedTextField(
                                            value = sessionNameInput,
                                            onValueChange = { sessionNameInput = it },
                                            singleLine = true,
                                            label = { Text("Session Name / Batch Label", color = Color(0xFF8888AA)) },
                                            colors = OutlinedTextFieldDefaults.colors(
                                                unfocusedContainerColor = Color.Transparent,
                                                focusedContainerColor = Color.Transparent,
                                                unfocusedBorderColor = Color(0x33FFFFFF),
                                                focusedBorderColor = Color(0xFF00E676),
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White
                                            ),
                                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                            keyboardActions = KeyboardActions(onDone = {
                                                renameActiveSession = false
                                                focusManager.clearFocus()
                                            }),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(56.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Button(
                                            onClick = {
                                                renameActiveSession = false
                                                focusManager.clearFocus()
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.height(40.dp)
                                        ) {
                                            Text("Set", color = Color.Black, fontWeight = FontWeight.Bold)
                                        }
                                    } else {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Current Save Batch Name:",
                                                fontSize = 11.sp,
                                                color = Color(0xFF8888AA)
                                            )
                                            Text(
                                                text = sessionNameInput.ifBlank { "Unlabelled Batch" },
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        IconButton(
                                            onClick = { renameActiveSession = true },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Edit Batch Label",
                                                tint = Color(0xFF00E676),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // List of scanned items in this batch
                                if (activeBatch.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxWidth(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(
                                                imageVector = Icons.Default.QrCodeScanner,
                                                contentDescription = "Ready to scan",
                                                tint = Color(0x33FFFFFF),
                                                modifier = Modifier.size(48.dp)
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "No codes captured yet in this frame",
                                                color = Color(0xFF8888AA),
                                                fontSize = 14.sp,
                                                textAlign = TextAlign.Center
                                            )
                                            Text(
                                                text = "Point camera at barcodes to record automatically",
                                                color = Color(0xFF666688),
                                                fontSize = 11.sp,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.padding(top = 4.dp)
                                            )
                                        }
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxWidth()
                                            .testTag("active_scans_list"),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(activeBatch.toList().reversed()) { code ->
                                            ActiveBarcodeRow(
                                                barcodeValue = code,
                                                onDelete = { viewModel.removeValueFromActiveBatch(code) }
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Massive CTA Button to SAVE current bunch
                                Button(
                                    onClick = {
                                        viewModel.saveActiveBatchToDatabase(context, sessionNameInput)
                                        sessionNameInput = currentSessionLabel
                                        focusManager.clearFocus()
                                    },
                                    enabled = activeBatch.isNotEmpty(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF00E676),
                                        disabledContainerColor = Color(0xFF1E3524)
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .testTag("save_batch_button")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Save,
                                            contentDescription = "Save Batch",
                                            tint = if (activeBatch.isNotEmpty()) Color.Black else Color.Gray
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Save Batch (${activeBatch.size} Items) to Database",
                                            color = if (activeBatch.isNotEmpty()) Color.Black else Color.Gray,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                    }
                                }
                            }
                        } else {
                            // TAB 2: HISTORY & CSV EXPORTS
                            if (savedBarcodes.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .testTag("history_empty_state"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = Icons.Default.Inventory,
                                            contentDescription = "Empty History",
                                            tint = Color(0x22FFFFFF),
                                            modifier = Modifier.size(56.dp)
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = "No saved scan history found",
                                            color = Color(0xFF8888AA),
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Scan and save barcodes to database to export CSV layouts",
                                            color = Color(0xFF666688),
                                            fontSize = 12.sp,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(top = 4.dp, start = 24.dp, end = 24.dp)
                                        )
                                    }
                                }
                            } else {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    // Header metrics and Mass Export row
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text(
                                                text = "Database Performance",
                                                fontSize = 11.sp,
                                                color = Color(0xFF8888AA)
                                            )
                                            Text(
                                                text = "${savedBarcodes.size} Total Scanned Barcodes",
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }

                                        Row {
                                            // Share all CSV
                                            Button(
                                                onClick = {
                                                    viewModel.exportToCSV(
                                                        context = context,
                                                        barcodesToExport = savedBarcodes,
                                                        customFileName = "all_barcodes_history"
                                                    )
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.height(44.dp).testTag("export_all_button")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Share,
                                                    contentDescription = "Share",
                                                    tint = Color.Black,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Export All CSV", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            }

                                            Spacer(modifier = Modifier.width(8.dp))

                                            // Clear all DB entries
                                            IconButton(
                                                onClick = { viewModel.clearDatabaseHistory() },
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .background(Color(0xFF2C191D), RoundedCornerShape(8.dp))
                                                    .border(1.dp, Color(0x33FF5252), RoundedCornerShape(8.dp))
                                                    .testTag("purge_db_button")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.DeleteSweep,
                                                    contentDescription = "Purge Database",
                                                    tint = Color(0xFFFF5252),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }

                                    HorizontalDivider(
                                        color = Color(0x1AFFFFFF),
                                        modifier = Modifier.padding(bottom = 12.dp)
                                    )

                                    // List of Saved Sessions grouped
                                    val groupedHistory = remember(savedBarcodes) {
                                        savedBarcodes.groupBy { it.sessionLabel }
                                    }

                                    LazyColumn(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        items(groupedHistory.keys.toList()) { sessionName ->
                                            val listForSession = groupedHistory[sessionName] ?: emptyList()
                                            HistorySessionCard(
                                                sessionLabel = sessionName,
                                                barcodes = listForSession,
                                                onExport = {
                                                    viewModel.exportToCSV(
                                                        context = context,
                                                        barcodesToExport = listForSession,
                                                        customFileName = sessionName
                                                    )
                                                },
                                                onDelete = { viewModel.deleteSessionBatch(sessionName) },
                                                onDeleteItem = { entity -> viewModel.deleteBarcode(entity) }
                                            )
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

// Compact row for rendering barcodes in the current scanner cache
@Composable
fun ActiveBarcodeRow(
    barcodeValue: String,
    onDelete: () -> Unit
) {
    val clipboard = LocalClipboardManager.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1D1B26), RoundedCornerShape(10.dp))
            .border(1.dp, Color(0x1BFFFFFF), RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .background(Color(0xFF00E676).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Code 128",
                        color = Color(0xFF00E676),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = barcodeValue,
                color = Color.White,
                fontSize = 15.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            // Copy button
            IconButton(
                onClick = { clipboard.setText(AnnotatedString(barcodeValue)) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy Value to Clipboard",
                    tint = Color(0xFFA0A0C0),
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Delete from active batch list
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(32.dp)
                    .background(Color(0xFFFF5252).copy(alpha = 0.1f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Exclude barcode",
                    tint = Color(0xFFFF5252),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// Collapsible session history card
@Composable
fun HistorySessionCard(
    sessionLabel: String,
    barcodes: List<BarcodeEntity>,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    onDeleteItem: (BarcodeEntity) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    val firstTimestamp = barcodes.firstOrNull()?.timestamp ?: System.currentTimeMillis()
    val formattedDate = remember(firstTimestamp) {
        val dateFormat = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
        dateFormat.format(Date(firstTimestamp))
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1B2C)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (isExpanded) Color(0xFF00E676).copy(alpha = 0.3f) else Color(0x0CFFFFFF),
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Toggle Expand",
                        tint = Color(0xFF00E676),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = sessionLabel,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "$formattedDate  •  ",
                                fontSize = 11.sp,
                                color = Color(0xFFA0A0C0)
                            )
                            Text(
                                text = "${barcodes.size} items",
                                fontSize = 11.sp,
                                color = Color(0xFF00E676),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Export specific batch button
                    IconButton(
                        onClick = onExport,
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFF00E676).copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Export Batch CSV",
                            tint = Color(0xFF00E676),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Delete specific batch button
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFFFF5252).copy(alpha = 0.08f), RoundedCornerShape(6.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Batch",
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Expanding nested list
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(animationSpec = spring()),
                exit = fadeOut() + shrinkVertically(animationSpec = spring())
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF13131F))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    HorizontalDivider(color = Color(0x19FFFFFF), modifier = Modifier.padding(bottom = 8.dp))

                    barcodes.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.barcodeValue,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFFE2E2E8),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Copy single value button
                                IconButton(
                                    onClick = { clipboard.setText(AnnotatedString(item.barcodeValue)) },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy Item",
                                        tint = Color(0xFF8888AA),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                // Delete single item button
                                IconButton(
                                    onClick = { onDeleteItem(item) },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Item",
                                        tint = Color(0xFFFF5252).copy(alpha = 0.7f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
