package com.ghostwave.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.compose.rememberNavController
import com.ghostwave.app.navigation.GhostWaveNavGraph
import com.ghostwave.app.security.AppLockManager
import com.ghostwave.app.ui.theme.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    @Inject lateinit var appLockManager: AppLockManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            GhostWaveTheme {
                val startDestination by viewModel.startDestination
                    .collectAsStateWithLifecycle()
                val isLocked by appLockManager.isLocked
                    .collectAsStateWithLifecycle()

                startDestination?.let { destination ->
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color    = MaterialTheme.colorScheme.background,
                    ) {
                        val navController = rememberNavController()
                        GhostWaveNavGraph(
                            navController    = navController,
                            startDestination = destination,
                        )

                        // App lock overlay
                        if (isLocked) {
                            AppLockOverlay(onUnlock = {
                                appLockManager.promptBiometric(
                                    activity  = this@MainActivity,
                                    onSuccess = {},
                                    onFail    = {},
                                )
                            })
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        appLockManager.onAppForegrounded()
    }

    override fun onPause() {
        super.onPause()
        appLockManager.onAppBackgrounded()
    }
}

@Composable
private fun AppLockOverlay(onUnlock: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyBackground),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("👻", fontSize = 64.sp)
            Spacer(Modifier.height(16.dp))
            Text(
                "GhostWave",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Locked",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.ghostColors.placeholder,
            )
            Spacer(Modifier.height(32.dp))
            androidx.compose.material3.Button(
                onClick = onUnlock,
                colors  = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = ElectricViolet,
                ),
            ) {
                Text("Unlock", color = androidx.compose.ui.graphics.Color.White)
            }
        }
    }
}
