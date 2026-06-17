package com.ghostwave.app.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.ghostwave.app.ui.call.AudioCallScreen
import com.ghostwave.app.ui.call.VideoCallScreen
import com.ghostwave.app.ui.chat.ChatScreen
import com.ghostwave.app.ui.contacts.AddContactScreen
import com.ghostwave.app.ui.contacts.ContactListScreen
import com.ghostwave.app.ui.onboarding.IdentitySetupScreen
import com.ghostwave.app.ui.onboarding.QrShareScreen
import com.ghostwave.app.ui.promo.PromoCodeScreen
import com.ghostwave.app.ui.settings.AboutScreen
import com.ghostwave.app.ui.settings.NotificationSettingsScreen
import com.ghostwave.app.ui.settings.PrivacySettingsScreen
import com.ghostwave.app.ui.settings.SafetyNumbersScreen
import com.ghostwave.app.ui.settings.SecuritySettingsScreen
import com.ghostwave.app.ui.settings.SettingsScreen

private const val ANIM_DURATION = 280

private val slideEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(ANIM_DURATION)) +
        fadeIn(animationSpec = tween(ANIM_DURATION))
}
private val slideExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = tween(ANIM_DURATION)) +
        fadeOut(animationSpec = tween(ANIM_DURATION))
}
private val slidePopEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = tween(ANIM_DURATION)) +
        fadeIn(animationSpec = tween(ANIM_DURATION))
}
private val slidePopExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(ANIM_DURATION)) +
        fadeOut(animationSpec = tween(ANIM_DURATION))
}

@Composable
fun GhostWaveNavGraph(
    navController:    NavHostController,
    startDestination: String,
    onMinimizeApp:    () -> Unit,
) {
    NavHost(
        navController      = navController,
        startDestination   = startDestination,
        enterTransition    = slideEnter,
        exitTransition     = slideExit,
        popEnterTransition = slidePopEnter,
        popExitTransition  = slidePopExit,
    ) {

        // ── Promo gate — mandatory; back press minimizes app ──────────────
        composable(Screen.PromoCode.route) {
            PromoCodeScreen(
                onUnlocked = {
                    navController.navigate(Screen.IdentitySetup.route) {
                        popUpTo(Screen.PromoCode.route) { inclusive = true }
                    }
                },
                onMinimize = onMinimizeApp,
            )
        }

        // ── Onboarding ────────────────────────────────────────────────────
        composable(Screen.IdentitySetup.route) {
            IdentitySetupScreen(
                onIdentityCreated = {
                    navController.navigate(Screen.QrShare.route) {
                        popUpTo(Screen.IdentitySetup.route) { inclusive = true }
                    }
                },
            )
        }

        composable(Screen.QrShare.route) {
            QrShareScreen(
                onContinue = {
                    navController.navigate(Screen.ContactList.route) {
                        popUpTo(Screen.QrShare.route) { inclusive = true }
                    }
                },
            )
        }

        // ── Contacts ──────────────────────────────────────────────────────
        composable(Screen.ContactList.route) {
            ContactListScreen(
                onContactClick  = { navController.navigate(Screen.Chat.buildRoute(it)) },
                onAddContact    = { navController.navigate(Screen.AddContact.route) },
                onSettingsClick = { navController.navigate(Screen.Settings.route) },
            )
        }

        composable(Screen.AddContact.route) {
            AddContactScreen(
                onContactAdded = { navController.popBackStack() },
                onBack         = { navController.popBackStack() },
            )
        }

        // ── Chat ──────────────────────────────────────────────────────────
        composable(
            route     = Screen.Chat.route,
            arguments = listOf(navArgument(Screen.Chat.ARG_CONTACT_ID) { type = NavType.StringType }),
        ) { entry ->
            val contactId = entry.arguments?.getString(Screen.Chat.ARG_CONTACT_ID) ?: return@composable
            ChatScreen(
                contactId   = contactId,
                onBack      = { navController.popBackStack() },
                onAudioCall = { navController.navigate(Screen.AudioCall.buildRoute(contactId, true)) },
                onVideoCall = { navController.navigate(Screen.VideoCall.buildRoute(contactId, true)) },
            )
        }

        // ── Calls ─────────────────────────────────────────────────────────
        composable(
            route     = Screen.AudioCall.route,
            arguments = listOf(
                navArgument(Screen.AudioCall.ARG_CONTACT_ID)  { type = NavType.StringType },
                navArgument(Screen.AudioCall.ARG_IS_OUTGOING) { type = NavType.BoolType  },
            ),
        ) { entry ->
            val contactId  = entry.arguments?.getString(Screen.AudioCall.ARG_CONTACT_ID)  ?: return@composable
            val isOutgoing = entry.arguments?.getBoolean(Screen.AudioCall.ARG_IS_OUTGOING) ?: true
            AudioCallScreen(
                contactId   = contactId,
                isOutgoing  = isOutgoing,
                onCallEnded = { navController.popBackStack() },
            )
        }

        composable(
            route     = Screen.VideoCall.route,
            arguments = listOf(
                navArgument(Screen.VideoCall.ARG_CONTACT_ID)  { type = NavType.StringType },
                navArgument(Screen.VideoCall.ARG_IS_OUTGOING) { type = NavType.BoolType  },
            ),
        ) { entry ->
            val contactId  = entry.arguments?.getString(Screen.VideoCall.ARG_CONTACT_ID)  ?: return@composable
            val isOutgoing = entry.arguments?.getBoolean(Screen.VideoCall.ARG_IS_OUTGOING) ?: true
            VideoCallScreen(
                contactId   = contactId,
                isOutgoing  = isOutgoing,
                onCallEnded = { navController.popBackStack() },
            )
        }

        // ── Settings ──────────────────────────────────────────────────────
        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack     = { navController.popBackStack() },
                onNavigate = { route -> navController.navigate(route) },
            )
        }

        composable(Screen.SettingsPrivacy.route) {
            PrivacySettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.SettingsSecurity.route) {
            SecuritySettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.SettingsNotifications.route) {
            NotificationSettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.SettingsAbout.route) {
            AboutScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.SettingsProfile.route) {
            PlaceholderScreen("Profile Settings")
        }

        composable(Screen.SettingsStorage.route) {
            PlaceholderScreen("Storage Settings")
        }

        composable(Screen.SettingsNetwork.route) {
            PlaceholderScreen("Network Settings")
        }

        composable(
            route     = Screen.SafetyNumbers.route,
            arguments = listOf(navArgument(Screen.SafetyNumbers.ARG_CONTACT_ID) { type = NavType.StringType }),
        ) {
            SafetyNumbersScreen(onBack = { navController.popBackStack() })
        }
    }
}
