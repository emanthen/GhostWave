package com.ghostwave.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.ghostwave.app.navigation.GhostWaveNavGraph
import com.ghostwave.app.navigation.Screen
import com.ghostwave.app.promo.PromoCodeGate
import com.ghostwave.app.security.AppLockManager
import com.ghostwave.app.ui.theme.ElectricViolet
import com.ghostwave.app.ui.theme.GhostWaveTheme
import com.ghostwave.app.ui.theme.NavyBackground
import com.ghostwave.app.ui.theme.ghostColors
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    @Inject lateinit var appLockManager: AppLockManager
    @Inject lateinit var promoCodeGate:  PromoCodeGate

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Prevent screenshots/screen recording on the promo entry screen.
        // FLAG_SECURE is set globally; PromoCodeScreen is the only screen
        // where a code is entered, so this is the correct scope.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        // If the gate is already passed on this device, boot WebRTC now.
        if (!promoCodeGate.shouldShowGate()) {
            (application as GhostWaveApplication).onPromoUnlocked()
        }

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
                            onMinimizeApp    = { moveTaskToBack(true) },
                        )

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

        // Re-check integrity on every resume — detects storage tampering
        // while app was backgrounded
        if (!promoCodeGate.checkIntegrity() && !promoCodeGate.shouldShowGate()) {
            // Integrity failed — recreate so NavGraph picks up new startDestination
            recreate()
        }
    }

    override fun onPause() {
        super.onPause()
        appLockManager.onAppBackgrounded()
    }
}

// ── MainViewModel must provide promo-aware startDestination ──────────────────

// MainViewModel.kt reads promoCodeGate.shouldShowGate() to determine
// whether to start at Screen.PromoCode or Screen.IdentitySetup / ContactList.
// See MainViewModel.kt below.

@Composable
private fun AppLockOverlay(onUnlock: () -> Unit) {
    Box(
        modifier         = Modifier
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
            Button(
                onClick = onUnlock,
                colors  = ButtonDefaults.buttonColors(containerColor = ElectricViolet),
            ) {
                Text("Unlock", color = Color.White)
            }
        }
    }
}
