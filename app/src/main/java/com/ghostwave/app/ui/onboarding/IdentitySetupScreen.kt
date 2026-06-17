package com.ghostwave.app.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ghostwave.app.R
import com.ghostwave.app.ui.theme.ElectricViolet
import com.ghostwave.app.ui.theme.NavyBackground
import com.ghostwave.app.ui.theme.VioletLight
import com.ghostwave.app.ui.theme.ghostColors

/**
 * First-launch screen. Prompts the user to choose a display name,
 * then triggers Curve25519 identity key generation in the ViewModel.
 * Actual crypto wired in Step 2.
 */
@Composable
fun IdentitySetupScreen(
    onIdentityCreated: () -> Unit,
    viewModel: IdentitySetupViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current

    // Navigate away once identity is ready
    if (uiState is IdentitySetupUiState.Done) {
        onIdentityCreated()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(NavyBackground, NavyBackground.copy(alpha = 0.95f)),
                )
            )
            .statusBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {

            // ── Logo / App name ──────────────────────────────────────────
            Text(
                text  = "GhostWave",
                style = MaterialTheme.typography.displaySmall.copy(
                    brush = Brush.horizontalGradient(
                        colors = listOf(ElectricViolet, VioletLight)
                    )
                ),
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text  = "Vanish into the signal.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.ghostColors.placeholder,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(56.dp))

            // ── Display name input ───────────────────────────────────────
            Text(
                text  = stringResource(R.string.onboarding_create_identity),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "No phone number. No email. Just you.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.ghostColors.placeholder,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value         = (uiState as? IdentitySetupUiState.Idle)?.displayName ?: "",
                onValueChange = viewModel::onDisplayNameChanged,
                modifier      = Modifier.fillMaxWidth(),
                label         = { Text(stringResource(R.string.onboarding_display_name_hint)) },
                singleLine    = true,
                enabled       = uiState !is IdentitySetupUiState.GeneratingKeys,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction      = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        keyboard?.hide()
                        viewModel.createIdentity()
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = ElectricViolet,
                    unfocusedBorderColor = MaterialTheme.ghostColors.divider,
                    focusedLabelColor    = VioletLight,
                    cursorColor          = ElectricViolet,
                ),
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ── Create button / spinner ──────────────────────────────────
            AnimatedVisibility(
                visible = uiState is IdentitySetupUiState.GeneratingKeys,
                enter   = fadeIn(),
                exit    = fadeOut(),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(40.dp),
                        color    = ElectricViolet,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text  = stringResource(R.string.onboarding_generating_keys),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.ghostColors.placeholder,
                    )
                }
            }

            AnimatedVisibility(visible = uiState !is IdentitySetupUiState.GeneratingKeys) {
                Button(
                    onClick  = {
                        keyboard?.hide()
                        viewModel.createIdentity()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    enabled  = (uiState as? IdentitySetupUiState.Idle)?.displayName?.isNotBlank() == true,
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = ElectricViolet,
                        contentColor   = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Text(
                        text  = "Create Identity",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }

            // ── Error state ──────────────────────────────────────────────
            if (uiState is IdentitySetupUiState.Error) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text  = (uiState as IdentitySetupUiState.Error).message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
