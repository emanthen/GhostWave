package com.ghostwave.app

import com.ghostwave.app.navigation.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for navigation route construction.
 * These run on the JVM (no Android runtime needed) and verify that
 * route templates and argument substitution are correct before any
 * NavController is involved.
 */
class NavigationTest {

    // ── Route string integrity ────────────────────────────────────────────

    @Test
    fun `IdentitySetup route is stable`() {
        assertEquals("onboarding/identity_setup", Screen.IdentitySetup.route)
    }

    @Test
    fun `QrShare route is stable`() {
        assertEquals("onboarding/qr_share", Screen.QrShare.route)
    }

    @Test
    fun `ContactList route is stable`() {
        assertEquals("contacts/list", Screen.ContactList.route)
    }

    @Test
    fun `AddContact route is stable`() {
        assertEquals("contacts/add", Screen.AddContact.route)
    }

    @Test
    fun `Settings route is stable`() {
        assertEquals("settings", Screen.Settings.route)
    }

    // ── Argument substitution ─────────────────────────────────────────────

    @Test
    fun `Chat buildRoute substitutes contactId correctly`() {
        val id    = "abc-123"
        val route = Screen.Chat.buildRoute(id)
        assertEquals("chat/$id", route)
        assertFalse("Template placeholder must not appear in built route",
            route.contains("{"))
    }

    @Test
    fun `AudioCall buildRoute with outgoing flag`() {
        val id    = "peer-999"
        val route = Screen.AudioCall.buildRoute(id, isOutgoing = true)
        assertEquals("call/audio/$id/true", route)
        assertFalse(route.contains("{"))
    }

    @Test
    fun `AudioCall buildRoute with incoming flag`() {
        val id    = "peer-999"
        val route = Screen.AudioCall.buildRoute(id, isOutgoing = false)
        assertEquals("call/audio/$id/false", route)
    }

    @Test
    fun `VideoCall buildRoute substitutes both args`() {
        val id    = "vid-peer"
        val route = Screen.VideoCall.buildRoute(id, isOutgoing = true)
        assertEquals("call/video/$id/true", route)
        assertFalse(route.contains("{"))
    }

    @Test
    fun `SafetyNumbers buildRoute substitutes contactId`() {
        val id    = "contact-xyz"
        val route = Screen.SafetyNumbers.buildRoute(id)
        assertEquals("settings/safety_numbers/$id", route)
        assertFalse(route.contains("{"))
    }

    // ── No duplicate routes ───────────────────────────────────────────────

    @Test
    fun `all base route strings are unique`() {
        val routes = listOf(
            Screen.IdentitySetup.route,
            Screen.QrShare.route,
            Screen.ContactList.route,
            Screen.AddContact.route,
            Screen.Chat.route,
            Screen.AudioCall.route,
            Screen.VideoCall.route,
            Screen.Settings.route,
            Screen.SettingsProfile.route,
            Screen.SettingsPrivacy.route,
            Screen.SettingsSecurity.route,
            Screen.SettingsNotifications.route,
            Screen.SettingsStorage.route,
            Screen.SettingsNetwork.route,
            Screen.SettingsAbout.route,
            Screen.SafetyNumbers.route,
        )
        val unique = routes.toSet()
        assertEquals(
            "Duplicate route detected: ${routes.groupBy { it }.filter { it.value.size > 1 }.keys}",
            routes.size, unique.size
        )
    }

    // ── Settings sub-routes all begin with "settings/" ────────────────────

    @Test
    fun `all settings sub-routes share settings prefix`() {
        val subRoutes = listOf(
            Screen.SettingsProfile.route,
            Screen.SettingsPrivacy.route,
            Screen.SettingsSecurity.route,
            Screen.SettingsNotifications.route,
            Screen.SettingsStorage.route,
            Screen.SettingsNetwork.route,
            Screen.SettingsAbout.route,
        )
        subRoutes.forEach { route ->
            assertTrue("$route should start with 'settings/'", route.startsWith("settings/"))
        }
    }
}
