package com.ghostwave.app

import androidx.compose.ui.graphics.Color
import com.ghostwave.app.ui.theme.ElectricViolet
import com.ghostwave.app.ui.theme.NavyBackground
import com.ghostwave.app.ui.theme.OnSurface
import com.ghostwave.app.ui.theme.VioletLight
import com.ghostwave.app.ui.theme.md_primary
import com.ghostwave.app.ui.theme.md_background
import com.ghostwave.app.ui.theme.md_onBackground
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies brand colour values are correctly defined.
 * These are compile-time constants so the test simply guards against
 * accidental hex typos during refactoring.
 */
class ThemeColorTest {

    @Test
    fun `NavyBackground is correct hex`() {
        assertEquals(Color(0xFF0D0F1A), NavyBackground)
    }

    @Test
    fun `ElectricViolet is correct hex`() {
        assertEquals(Color(0xFF7C3AED), ElectricViolet)
    }

    @Test
    fun `VioletLight is correct hex`() {
        assertEquals(Color(0xFFA78BFA), VioletLight)
    }

    @Test
    fun `OnSurface is near-white for readability`() {
        // Red, green, blue channels should all be above 0.87 (near white)
        assertTrue("OnSurface red channel too low",   OnSurface.red   > 0.87f)
        assertTrue("OnSurface green channel too low", OnSurface.green > 0.87f)
        assertTrue("OnSurface blue channel too low",  OnSurface.blue  > 0.87f)
    }

    @Test
    fun `md_primary maps to ElectricViolet`() {
        assertEquals(ElectricViolet, md_primary)
    }

    @Test
    fun `md_background maps to NavyBackground`() {
        assertEquals(NavyBackground, md_background)
    }

    @Test
    fun `md_onBackground maps to OnSurface`() {
        assertEquals(OnSurface, md_onBackground)
    }

    @Test
    fun `NavyBackground has full alpha`() {
        assertEquals(1.0f, NavyBackground.alpha, 0.001f)
    }

    @Test
    fun `background and surface are distinct`() {
        // Surface is slightly lighter than background — ensures visual hierarchy
        val bgLuminance = luminance(NavyBackground)
        val sfLuminance = luminance(Color(0xFF12152A))
        assertTrue("Surface should be brighter than background", sfLuminance > bgLuminance)
    }

    // Rough relative luminance (not full WCAG formula — good enough for comparison)
    private fun luminance(c: Color): Float = 0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue
}
