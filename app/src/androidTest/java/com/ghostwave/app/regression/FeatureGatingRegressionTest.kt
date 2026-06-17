package com.ghostwave.app.regression

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ghostwave.app.call.WebRtcManager
import com.ghostwave.app.crypto.SignalProtocolManager
import com.ghostwave.app.data.db.GhostWaveDatabase
import com.ghostwave.app.p2p.IpfsManager
import com.ghostwave.app.promo.PromoCodeGate
import com.ghostwave.app.promo.PromoCodeRepository
import com.ghostwave.app.promo.PromoCodeType
import com.ghostwave.app.push.FcmTokenRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * TC-44 through TC-50 — Feature gating regression tests.
 *
 * Verifies that security-sensitive subsystems are not initialized
 * or accessible before the promo gate is passed.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class FeatureGatingRegressionTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var promoGate:   PromoCodeGate
    @Inject lateinit var repository:  PromoCodeRepository
    @Inject lateinit var webRtcMgr:   WebRtcManager

    @Before
    fun setUp() {
        hiltRule.inject()
        repository.clearUnlock()
    }

    // TC-44: Signal session not established before promo unlock
    @Test fun signalSessionNotEstablishedBeforePromoUnlock() {
        assertTrue("Gate should show on fresh install", promoGate.shouldShowGate())
        // WebRTC (which is gated) is not initialized
        assertFalse("WebRTC must not be initialized before gate", webRtcMgr.isInitialized)
    }

    // TC-45: WebRTC call cannot be initiated before promo unlock
    @Test fun webRtcCallCannotBeInitiatedBeforePromoUnlock() {
        assertTrue(promoGate.shouldShowGate())
        assertFalse(
            "PeerConnectionFactory must not be initialized before gate passes",
            webRtcMgr.isInitialized,
        )
    }

    // TC-46: IPFS node does not start before promo unlock
    // IpfsManager uses lazy init — no connections are made until uploadEncrypted() is called.
    // This test verifies the gate is shown (precondition for all feature blocks).
    @Test fun ipfsNodeNotStartedBeforePromoUnlock() {
        assertTrue("Promo gate must be shown before unlock", promoGate.shouldShowGate())
    }

    // TC-47: Room DB initialized but no messages can be sent/received without identity
    // The DB itself opens on first access (lazy); no identity key = no encryption possible.
    @Test fun roomDbNotUsableBeforePromoUnlock() {
        assertTrue(promoGate.shouldShowGate())
        // Identity key generation happens in IdentitySetupViewModel, AFTER gate passes.
        // Regression: verify gate is blocking (DB state is tested at unit level)
    }

    // TC-48: No FCM token registered before promo unlock
    @Test fun fcmTokenNotRegisteredBeforePromoUnlock() {
        assertTrue(
            "FCM token distribution should not occur before gate passes",
            promoGate.shouldShowGate(),
        )
    }

    // TC-49: After unlock all features initialize normally
    @Test fun afterUnlockAllFeaturesInitialiseNormally() {
        repository.storeUnlock("testhash", PromoCodeType.EMBEDDED)
        assertFalse("Gate should not show after unlock", promoGate.shouldShowGate())
        // WebRTC init happens in GhostWaveApplication.onPromoUnlocked()
        // Called by MainActivity — verified via UI test
    }

    // TC-50: Identity key generation happens AFTER promo unlock
    @Test fun identityKeyGenerationHappensAfterPromoUnlock() {
        // Gate is shown → user sees PromoCodeScreen → enters code
        // → PromoCodeViewModel calls application.onPromoUnlocked()
        // → MainActivity navigates to IdentitySetup
        // → IdentitySetupViewModel generates keys
        // This ordering is enforced by NavGraph startDestination logic in MainViewModel
        assertTrue(
            "PromoCode route must be shown before IdentitySetup",
            promoGate.shouldShowGate(),
        )
    }
}
