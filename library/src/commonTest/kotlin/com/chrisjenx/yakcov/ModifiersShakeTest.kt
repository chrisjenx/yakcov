package com.chrisjenx.yakcov

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ModifiersShakeTest {

    @Test
    @AndroidJUnitIgnore
    @JSIgnore
    fun doesNotShakeOnFirstComposition_evenWhenAlreadyInError() = runComposeUiTest {
        var shakes = 0
        setContent {
            // Starts in error with a non-zero trigger: an initialValidate field must not shake.
            ShakeOnTriggerEffect(isError = true, trigger = 7) { shakes++ }
        }
        waitForIdle()
        assertEquals(0, shakes)
    }

    @Test
    @AndroidJUnitIgnore
    @JSIgnore
    fun shakesWhenTriggerChangesWhileInError() = runComposeUiTest {
        var shakes = 0
        var trigger by mutableStateOf(0)
        setContent {
            ShakeOnTriggerEffect(isError = true, trigger = trigger) { shakes++ }
        }
        waitForIdle()
        assertEquals(0, shakes)
        trigger = 1
        waitForIdle()
        assertEquals(1, shakes)
    }

    @Test
    @AndroidJUnitIgnore
    @JSIgnore
    fun eachRepeatedInvalidSubmitShakes() = runComposeUiTest {
        // THE defect this exists to fix: a second invalid submit produces an EQUAL
        // FieldValidationState, so a state-diff-driven shake would never re-fire.
        var shakes = 0
        var trigger by mutableStateOf(0)
        setContent {
            ShakeOnTriggerEffect(isError = true, trigger = trigger) { shakes++ }
        }
        waitForIdle()
        trigger = 1
        waitForIdle()
        trigger = 2
        waitForIdle()
        assertEquals(2, shakes)
    }

    @Test
    @AndroidJUnitIgnore
    @JSIgnore
    fun doesNotShakeWhenNotInError() = runComposeUiTest {
        var shakes = 0
        var trigger by mutableStateOf(0)
        setContent {
            ShakeOnTriggerEffect(isError = false, trigger = trigger) { shakes++ }
        }
        waitForIdle()
        trigger = 1
        waitForIdle()
        assertEquals(0, shakes)
    }

    @Test
    @AndroidJUnitIgnore
    @JSIgnore
    fun disposalResetsTheBaseline_knownLimitation() = runComposeUiTest {
        // Locks in a KNOWN LIMITATION rather than asserting desirable behaviour: `remember`
        // cannot tell "new field" from "same field re-created after its subtree was disposed"
        // (a LazyColumn item without a stable key, or a nav destination re-entered). Increments
        // that happened while disposed are swallowed. If this test ever starts failing, the
        // guard became smarter — update the limitation note in docs, don't just delete the test.
        var shakes = 0
        var trigger by mutableStateOf(0)
        var present by mutableStateOf(true)
        setContent {
            if (present) ShakeOnTriggerEffect(isError = true, trigger = trigger) { shakes++ }
        }
        waitForIdle()
        present = false
        waitForIdle()
        trigger = 5           // bumped while disposed
        waitForIdle()
        present = true        // recomposed fresh: 5 becomes the new baseline
        waitForIdle()
        assertEquals(0, shakes)
    }
}
