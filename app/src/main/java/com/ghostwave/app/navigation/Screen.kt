package com.ghostwave.app.navigation

/**
 * Sealed hierarchy of every navigation destination in GhostWave.
 *
 * Route strings are kept as constants so callers don't use raw strings
 * and refactors stay safe. Argument placeholders follow the Navigation
 * Compose convention: {argName}.
 */
sealed class Screen(val route: String) {

    // ── Onboarding ────────────────────────────────────────────────────────

    /** First-launch: generate identity, pick display name */
    data object IdentitySetup : Screen("onboarding/identity_setup")

    /** Show own QR code + GW-ID after identity creation */
    data object QrShare : Screen("onboarding/qr_share")

    // ── Main shell ───────────────────────────────────────────────────────

    /** Contact list — app home after onboarding */
    data object ContactList : Screen("contacts/list")

    /** Add contact: QR scan or manual GW-ID entry */
    data object AddContact : Screen("contacts/add")

    /**
     * 1:1 chat with a contact.
     * @param contactId UUID of the Contact entity.
     */
    data object Chat : Screen("chat/{contactId}") {
        const val ARG_CONTACT_ID = "contactId"
        fun buildRoute(contactId: String) = "chat/$contactId"
    }

    // ── Calls ────────────────────────────────────────────────────────────

    /**
     * Active audio call screen.
     * @param contactId  the remote peer's contact UUID
     * @param isOutgoing true when this device initiated the call
     */
    data object AudioCall : Screen("call/audio/{contactId}/{isOutgoing}") {
        const val ARG_CONTACT_ID  = "contactId"
        const val ARG_IS_OUTGOING = "isOutgoing"
        fun buildRoute(contactId: String, isOutgoing: Boolean) =
            "call/audio/$contactId/$isOutgoing"
    }

    /**
     * Active video call screen.
     * Same args as AudioCall; video track added on top.
     */
    data object VideoCall : Screen("call/video/{contactId}/{isOutgoing}") {
        const val ARG_CONTACT_ID  = "contactId"
        const val ARG_IS_OUTGOING = "isOutgoing"
        fun buildRoute(contactId: String, isOutgoing: Boolean) =
            "call/video/$contactId/$isOutgoing"
    }

    // ── Settings ─────────────────────────────────────────────────────────

    data object Settings : Screen("settings")
    data object SettingsProfile       : Screen("settings/profile")
    data object SettingsPrivacy       : Screen("settings/privacy")
    data object SettingsSecurity      : Screen("settings/security")
    data object SettingsNotifications : Screen("settings/notifications")
    data object SettingsStorage       : Screen("settings/storage")
    data object SettingsNetwork       : Screen("settings/network")
    data object SettingsAbout         : Screen("settings/about")
    data object SafetyNumbers         : Screen("settings/safety_numbers/{contactId}") {
        const val ARG_CONTACT_ID = "contactId"
        fun buildRoute(contactId: String) = "settings/safety_numbers/$contactId"
    }
}
