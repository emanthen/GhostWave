package com.ghostwave.app.ui.promo

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ghostwave.app.promo.PromoResult
import com.ghostwave.app.ui.theme.ElectricViolet
import com.ghostwave.app.ui.theme.NavyBackground
import kotlinx.coroutines.delay

@Composable
fun PromoCodeScreen(
    onUnlocked:  () -> Unit,
    onMinimize:  () -> Unit,
    viewModel:   PromoCodeViewModel = hiltViewModel(),
) {
    // Intercept back press — minimize app, do NOT exit the gate
    BackHandler(onBack = onMinimize)

    val uiState     by viewModel.uiState.collectAsState()
    val displayCode by viewModel.displayCode.collectAsState()
    val isValid     by viewModel.isFormatValid.collectAsState()
    val focusManager = LocalFocusManager.current
    val uriHandler   = LocalUriHandler.current

    // Navigate away 1.5s after success
    LaunchedEffect(uiState) {
        if (uiState is PromoUiState.Success) {
            delay(1_500)
            onUnlocked()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {

            // ── Ghost logo ────────────────────────────────────────────────
            GhostLogo(success = uiState is PromoUiState.Success)

            Spacer(Modifier.height(32.dp))

            // ── Title ─────────────────────────────────────────────────────
            AnimatedVisibility(
                visible = uiState !is PromoUiState.Success,
                enter   = fadeIn(),
                exit    = fadeOut(),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text      = "Enter your invite code",
                        style     = MaterialTheme.typography.headlineSmall,
                        color     = Color.White,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text      = "GhostWave is invite-only. Get a code from\nan existing member or the GhostWave team.",
                        style     = MaterialTheme.typography.bodyMedium,
                        color     = Color.White.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            // ── Success message ───────────────────────────────────────────
            AnimatedVisibility(
                visible = uiState is PromoUiState.Success,
                enter   = fadeIn(animationSpec = tween(600)),
                exit    = fadeOut(),
            ) {
                Text(
                    text       = "Welcome to GhostWave 👻",
                    style      = MaterialTheme.typography.headlineSmall,
                    color      = ElectricViolet,
                    fontWeight = FontWeight.Bold,
                    textAlign  = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(32.dp))

            // ── Code input ────────────────────────────────────────────────
            AnimatedVisibility(visible = uiState !is PromoUiState.Success) {
                Column {
                    OutlinedTextField(
                        value         = displayCode,
                        onValueChange = viewModel::onCodeChanged,
                        modifier      = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = "Promo code input" },
                        placeholder   = {
                            Text(
                                "GW-XXXX-XXXX-XXXX",
                                fontFamily = FontFamily.Monospace,
                                color      = Color.White.copy(alpha = 0.3f),
                            )
                        },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily    = FontFamily.Monospace,
                            letterSpacing = 2.sp,
                            color         = Color.White,
                        ),
                        singleLine    = true,
                        enabled       = uiState !is PromoUiState.Loading &&
                                        uiState !is PromoUiState.Success &&
                                        !isLockedOut(uiState),
                        isError       = uiState is PromoUiState.Error,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            keyboardType   = KeyboardType.Ascii,
                            imeAction      = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                if (isValid) viewModel.onVerifyClicked()
                            },
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = ElectricViolet,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                            errorBorderColor     = Color(0xFFCF6679),
                            cursorColor          = ElectricViolet,
                        ),
                        shape = RoundedCornerShape(12.dp),
                    )

                    // ── Error / countdown display ──────────────────────────
                    ErrorMessage(uiState = uiState, viewModel = viewModel)
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── Verify button ─────────────────────────────────────────────
            AnimatedVisibility(visible = uiState !is PromoUiState.Success) {
                Button(
                    onClick   = {
                        focusManager.clearFocus()
                        viewModel.onVerifyClicked()
                    },
                    modifier  = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .semantics { contentDescription = "Verify code button" },
                    enabled   = isValid &&
                                uiState !is PromoUiState.Loading &&
                                !isLockedOut(uiState),
                    colors    = ButtonDefaults.buttonColors(containerColor = ElectricViolet),
                    shape     = RoundedCornerShape(12.dp),
                ) {
                    if (uiState is PromoUiState.Loading) {
                        CircularProgressIndicator(
                            modifier  = Modifier.size(20.dp),
                            color     = Color.White,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("Verify code", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            // ── Footer link ───────────────────────────────────────────────
            AnimatedVisibility(visible = uiState !is PromoUiState.Success) {
                TextButton(
                    onClick = {
                        uriHandler.openUri("https://ghostwave.app/invite")
                    },
                    modifier = Modifier.semantics {
                        contentDescription = "Learn how to get an invite code"
                    },
                ) {
                    Text(
                        "How do I get a code?",
                        color = ElectricViolet,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

// ── Ghost logo with pulse animation on success ────────────────────────────────

@Composable
private fun GhostLogo(success: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "ghost_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue   = 1f,
        targetValue    = if (success) 1.15f else 1.03f,
        animationSpec  = infiniteRepeatable(
            animation  = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "ghost_scale",
    )

    Box(
        modifier           = Modifier
            .size(100.dp)
            .scale(scale),
        contentAlignment   = Alignment.Center,
    ) {
        Text(
            text     = "👻",
            fontSize = 72.sp,
        )
    }
}

// ── Error message row ─────────────────────────────────────────────────────────

@Composable
private fun ErrorMessage(uiState: PromoUiState, viewModel: PromoCodeViewModel) {
    val errorState = uiState as? PromoUiState.Error ?: return

    val remainingSeconds by produceState(initialValue = 0L, uiState) {
        if (errorState.reason is PromoResult.RateLimited) {
            viewModel.getRemainingLockoutSeconds().collect { value = it }
        }
    }

    val errorText = when (val reason = errorState.reason) {
        is PromoResult.InvalidFormat -> "Code format should be GW-XXXX-XXXX-XXXX"
        is PromoResult.InvalidCode   -> "This code is not valid." +
            if (errorState.attemptsRemaining > 0)
                " ${errorState.attemptsRemaining} attempts remaining."
            else ""
        is PromoResult.AlreadyUsed   -> "This code has already been used on this device."
        is PromoResult.Expired       -> "This code has expired. Request a new one."
        is PromoResult.NetworkError  -> "Can't reach GhostWave servers. Check your connection and try again."
        is PromoResult.RateLimited   -> {
            val mm = remainingSeconds / 60
            val ss = remainingSeconds % 60
            "Too many attempts. Try again in %02d:%02d.".format(mm, ss)
        }
        else -> ""
    }

    if (errorText.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        androidx.compose.foundation.layout.Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector        = Icons.Default.Error,
                contentDescription = null,
                tint               = Color(0xFFCF6679),
                modifier           = Modifier.size(16.dp),
            )
            Spacer(Modifier.size(6.dp))
            Text(
                text  = errorText,
                color = Color(0xFFCF6679),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun isLockedOut(state: PromoUiState): Boolean =
    state is PromoUiState.Error && state.reason is PromoResult.RateLimited
