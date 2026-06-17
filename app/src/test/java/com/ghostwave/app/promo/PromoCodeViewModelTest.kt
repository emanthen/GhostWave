package com.ghostwave.app.promo

import app.cash.turbine.test
import com.ghostwave.app.GhostWaveApplication
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import com.ghostwave.app.ui.promo.PromoCodeViewModel
import com.ghostwave.app.ui.promo.PromoUiState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PromoCodeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val validator      = mockk<PromoCodeValidator>(relaxed = true)
    private val repository     = mockk<PromoCodeRepository>(relaxed = true)
    private val gate           = mockk<PromoCodeGate>(relaxed = true)
    private val app            = mockk<GhostWaveApplication>(relaxed = true)
    private lateinit var vm: PromoCodeViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { repository.getLockoutUntil() } returns 0L
        every { repository.getFailedAttemptCount() } returns 0
        vm = PromoCodeViewModel(validator, repository, gate, app)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // TC-20
    @Test fun `onCodeChanged formats raw input to GW-XXXX-XXXX-XXXX`() = runTest {
        vm.onCodeChanged("GWA3F79KMP2WXZ")
        assertEquals("GW-A3F7-9KMP-2WXZ", vm.displayCode.value)
    }

    // TC-21
    @Test fun `onVerifyClicked with empty input stays Idle no api call`() = runTest {
        vm.onVerifyClicked()
        advanceUntilIdle()
        assertTrue(vm.uiState.value is PromoUiState.Idle)
        verify(exactly = 0) { validator.formatCheck(any()) }
    }

    // TC-22
    @Test fun `onVerifyClicked invalid format returns Error InvalidFormat`() = runTest {
        every { validator.formatCheck(any()) } returns false
        vm.onCodeChanged("BADCODE")
        vm.onVerifyClicked()
        advanceUntilIdle()
        // Button is disabled for invalid format — state stays Idle
        assertTrue(vm.uiState.value is PromoUiState.Idle)
    }

    // TC-23
    @Test fun `onVerifyClicked valid code returns Success state`() = runTest {
        every { validator.formatCheck("GW-A3F7-9KMP-2WXZ") } returns true
        every { validator.normalize(any()) } returns "GW-A3F7-9KMP-2WXZ"
        every { validator.hash(any()) } returns "abc123"
        coEvery { validator.validate(any()) } returns PromoResult.Success(
            type      = PromoCodeType.EMBEDDED,
            expiresAt = null,
        )
        vm.onCodeChanged("GWA3F79KMP2WXZ")
        vm.onVerifyClicked()
        advanceUntilIdle()
        assertTrue(vm.uiState.value is PromoUiState.Success)
    }

    // TC-24
    @Test fun `onVerifyClicked invalid code returns Error InvalidCode`() = runTest {
        every { validator.formatCheck("GW-A3F7-9KMP-2WXZ") } returns true
        every { validator.normalize(any()) } returns "GW-A3F7-9KMP-2WXZ"
        coEvery { validator.validate(any()) } returns PromoResult.InvalidCode
        every { repository.recordFailedAttempt() } returns 1
        every { repository.getLockoutUntil() } returns 0L

        vm.onCodeChanged("GWA3F79KMP2WXZ")
        vm.onVerifyClicked()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is PromoUiState.Error)
        assertEquals(PromoResult.InvalidCode, (state as PromoUiState.Error).reason)
    }

    // TC-25
    @Test fun `after 5 errors state shows RateLimited`() = runTest {
        every { validator.formatCheck(any()) } returns true
        every { validator.normalize(any()) } returns "GW-A3F7-9KMP-2WXZ"
        coEvery { validator.validate(any()) } returns PromoResult.InvalidCode

        val lockoutTime = System.currentTimeMillis() + 15 * 60 * 1000L
        every { repository.recordFailedAttempt() } returnsMany listOf(1, 2, 3, 4, 5)
        every { repository.getLockoutUntil() } returnsMany
            listOf(0L, 0L, 0L, 0L, lockoutTime)

        repeat(5) {
            vm.onCodeChanged("GWA3F79KMP2WXZ")
            vm.onVerifyClicked()
            advanceUntilIdle()
        }

        val state = vm.uiState.value
        assertTrue(state is PromoUiState.Error)
        assertTrue((state as PromoUiState.Error).reason is PromoResult.RateLimited)
    }

    // TC-26
    @Test fun `getRemainingLockoutSeconds emits 0 when no lockout active`() = runTest {
        every { repository.getLockoutUntil() } returns 0L
        vm.getRemainingLockoutSeconds().test {
            assertEquals(0L, awaitItem())
            awaitComplete()
        }
    }
}
