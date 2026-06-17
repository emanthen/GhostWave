package com.ghostwave.app.ui.contacts

import android.Manifest
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.ghostwave.app.ui.theme.*
import zxingcpp.BarcodeReader
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun AddContactScreen(
    onContactAdded: () -> Unit,
    onBack: () -> Unit,
    viewModel: AddContactViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(uiState) {
        if (uiState is AddContactUiState.Success) onContactAdded()
    }

    Scaffold(
        containerColor = NavyBackground,
        topBar = {
            TopAppBar(
                title = { Text("Add Contact") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor    = NavyBackground,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab, containerColor = NavyBackground, contentColor = ElectricViolet) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 },
                    text = { Text("Scan QR", color = if (selectedTab == 0) ElectricViolet else MaterialTheme.ghostColors.placeholder) })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 },
                    text = { Text("Enter ID", color = if (selectedTab == 1) ElectricViolet else MaterialTheme.ghostColors.placeholder) })
            }

            when {
                uiState is AddContactUiState.Adding -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ElectricViolet)
                    }
                }
                uiState is AddContactUiState.Error -> {
                    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text((uiState as AddContactUiState.Error).message,
                                color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = viewModel::reset,
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet)) {
                                Text("Try Again")
                            }
                        }
                    }
                }
                selectedTab == 0 -> QrScanTab(onScanned = viewModel::onQrScanned)
                else             -> ManualIdTab(
                    gwId      = (uiState as? AddContactUiState.ManualEntry)?.gwId ?: "",
                    error     = (uiState as? AddContactUiState.ManualEntry)?.error,
                    onChange  = viewModel::onManualGwIdChanged,
                    onSubmit  = viewModel::submitManualId,
                )
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun QrScanTab(onScanned: (String) -> Unit) {
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

    if (!cameraPermission.status.isGranted) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Camera permission needed to scan QR codes.",
                color = MaterialTheme.ghostColors.placeholder, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Button(onClick = { cameraPermission.launchPermissionRequest() },
                colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet)) {
                Text("Grant Camera Access")
            }
        }
        return
    }

    val context          = LocalContext.current
    val lifecycleOwner   = LocalLifecycleOwner.current
    var scanned          by remember { mutableStateOf(false) }
    val executor         = remember { Executors.newSingleThreadExecutor() }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).also { previewView ->
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build()
                            .also { it.setSurfaceProvider(previewView.surfaceProvider) }

                        val reader   = BarcodeReader()
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                        analysis.setAnalyzer(executor) { imageProxy ->
                            if (!scanned) {
                                imageProxy.use { proxy ->
                                    val bitmap = proxy.toBitmap()
                                    val results = reader.read(bitmap)
                                    results.firstOrNull()?.text?.let { text ->
                                        scanned = true
                                        onScanned(text)
                                    }
                                }
                            }
                        }
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis,
                        )
                    }, ContextCompat.getMainExecutor(ctx))
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        // Scan frame overlay
        Box(
            Modifier
                .size(240.dp)
                .align(Alignment.Center)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Transparent),
        ) {
            // Corner markers drawn via Canvas in Step 18 polish
        }
        Text(
            "Point at a GhostWave QR code",
            modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp),
            style    = MaterialTheme.typography.bodySmall,
            color    = Color.White,
        )
    }
}

@Composable
private fun ManualIdTab(
    gwId: String, error: String?,
    onChange: (String) -> Unit, onSubmit: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Enter GhostWave ID", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = gwId, onValueChange = onChange,
            modifier      = Modifier.fillMaxWidth(),
            label         = { Text("GW-XXXX-XXXX") },
            singleLine    = true,
            isError       = error != null,
            supportingText = error?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = ElectricViolet,
                unfocusedBorderColor = MaterialTheme.ghostColors.divider,
            ),
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick  = onSubmit,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            enabled  = gwId.isNotBlank(),
            colors   = ButtonDefaults.buttonColors(containerColor = ElectricViolet),
        ) { Text("Add Contact") }
    }
}
