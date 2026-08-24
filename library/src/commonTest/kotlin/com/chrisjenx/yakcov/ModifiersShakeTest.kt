package com.chrisjenx.yakcov

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.TextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import com.chrisjenx.yakcov.strings.TextFieldValueValidator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

    @Test
    @AndroidJUnitIgnore
    @JSIgnore
    fun validationBehavior_firesFocusLostCallback() = runComposeUiTest {
        var lost = 0
        setContent {
            Column {
                TextField(
                    value = "", onValueChange = {},
                    modifier = Modifier
                        .testTag("a")
                        .validationBehavior(isError = true, onFocusLost = { lost++ }),
                )
                TextField(value = "", onValueChange = {}, modifier = Modifier.testTag("b"))
            }
        }
        onNodeWithTag("a").requestFocus()
        assertEquals(0, lost)
        onNodeWithTag("b").requestFocus()
        assertEquals(1, lost)
    }

    @Test
    fun validationBehavior_withNoParamsIsTrulyANoOp() {
        // A plain assertion, no composition needed: with both optional params null the
        // implementation is `this.then(Modifier).then(Modifier)`, and Modifier.then(Modifier)
        // returns the receiver — so the whole call must collapse to Modifier itself.
        assertEquals(Modifier, Modifier.validationBehavior(isError = true))
    }

    @Test
    @AndroidJUnitIgnore
    @JSIgnore
    fun validationBehavior_isNotHijackedByValidatorMemberScope() = runComposeUiTest {
        // Canary, not proof: there is no validationBehavior MEMBER on ValueValidator, so this
        // test cannot demonstrate the free function "winning" a resolution contest today — it
        // only exercises validationBehavior inside `with(validator) { }`. What it guards against
        // is future regression: if someone later adds a colliding `validationBehavior` member to
        // ValueValidator, Kotlin's member-over-extension resolution would silently bind here, and
        // this test would start failing (the member has no onFocusLost param) rather than the
        // collision going unnoticed. The actual naming-collision case this branch dodged is
        // covered by validationConfig_memberStillBindsToMemberInsideWithValidator below.
        val validator = TextFieldValueValidator(value = "")
        var lost = 0
        setContent {
            with(validator) {
                Column {
                    TextField(
                        value = "", onValueChange = {},
                        modifier = Modifier
                            .testTag("a")
                            .validationBehavior(isError = true, onFocusLost = { lost++ }),
                    )
                    TextField(value = "", onValueChange = {}, modifier = Modifier.testTag("b"))
                }
            }
        }
        onNodeWithTag("a").requestFocus()
        onNodeWithTag("b").requestFocus()
        assertEquals(1, lost, "validationBehavior's onFocusLost must fire from inside with(validator)")
    }

    @Test
    @AndroidJUnitIgnore
    @JSIgnore
    fun validationConfig_memberStillBindsToMemberInsideWithValidator() = runComposeUiTest {
        // The other half of the naming decision: inside with(validator) { }, the unqualified
        // call `Modifier.validationConfig(...)` must still resolve to the ValueValidator MEMBER
        // (member-over-extension), not to some free function. Required is the validator's
        // default rule, so a blank value fails validation; validateOnFocusLost = true means
        // losing focus should trigger validate() and flip isError() to true.
        val validator = TextFieldValueValidator(value = "")
        setContent {
            with(validator) {
                Column {
                    TextField(
                        value = "", onValueChange = {},
                        modifier = Modifier
                            .testTag("a")
                            .validationConfig(validateOnFocusLost = true),
                    )
                    TextField(value = "", onValueChange = {}, modifier = Modifier.testTag("b"))
                }
            }
        }
        onNodeWithTag("a").requestFocus()
        assertEquals(false, validator.isError())
        onNodeWithTag("b").requestFocus()
        assertEquals(true, validator.isError())
    }

    @Test
    @AndroidJUnitIgnore
    @JSIgnore
    fun shakeOnInvalid_composesThePublicModifier() = runComposeUiTest {
        // Every other shake test targets the internal ShakeOnTriggerEffect. This is the only
        // test that materializes the PUBLIC Modifier.shakeOnInvalid, so it would catch a
        // shakeOnInvalid (or validationBehavior) that dropped its shakable(shakingState) call.
        var trigger by mutableStateOf(0)
        setContent {
            TextField(
                value = "", onValueChange = {},
                modifier = Modifier
                    .testTag("a")
                    .shakeOnInvalid(isError = true, trigger = trigger),
            )
        }
        waitForIdle()
        trigger = 1
        waitForIdle()
        // Proves the public modifier composed successfully and its shake branch ran without
        // crashing composition (a throw there would have already failed at setContent /
        // waitForIdle above); requestFocus() re-confirms the node is still alive afterward.
        onNodeWithTag("a").requestFocus()
    }

    @Test
    @AndroidJUnitIgnore
    @JSIgnore
    fun shakeOnInvalid_actuallyAnimatesXPosition() = runComposeUiTest {
        // The wiring proof the parked finding asked for: with an injected ShakingState we can see
        // that shakeOnInvalid really drives ShakingState.shake(), i.e. that translationX moves.
        // Deleting shakable()/shake() from the modifier makes this fail instead of passing silently.
        val shakingState = ShakingState(ShakeStrength, ShakeDirection)
        var trigger by mutableStateOf(0)
        mainClock.autoAdvance = false
        setContent {
            TextField(
                value = "", onValueChange = {},
                modifier = Modifier
                    .testTag("a")
                    .shakeOnInvalid(isError = true, trigger = trigger, shakingState = shakingState),
            )
        }
        mainClock.advanceTimeByFrame()
        assertEquals(0f, shakingState.xPosition.value, "must be at rest before any trigger change")
        trigger = 1
        // Sample the first stretch of the shake: each animateTo leg is 20ms, so within the first
        // ~10ms of an animation the value must have left 0f. Take the max magnitude across a few
        // frames so we don't land exactly on a 0-crossing.
        var maxAbs = 0f
        var maxBoundsShift = 0f
        repeat(3) {
            mainClock.advanceTimeBy(10L)
            val x = shakingState.xPosition.value
            if (x < 0f) { if (-x > maxAbs) maxAbs = -x } else if (x > maxAbs) maxAbs = x
            // Also observe the node's on-screen bounds: graphicsLayer translationX shows up in
            // boundsInRoot, so this half fails if shakable(shakingState) is ever dropped from the
            // chain (asserting on xPosition alone would not notice).
            val shift = onNodeWithTag("a").fetchSemanticsNode().boundsInRoot.left
            if (shift < 0f) { if (-shift > maxBoundsShift) maxBoundsShift = -shift }
            else if (shift > maxBoundsShift) maxBoundsShift = shift
        }
        assertTrue(maxAbs > 0f, "xPosition must move while shaking, was max |x| = $maxAbs")
        assertTrue(
            maxBoundsShift > 0f,
            "the node must actually be translated on screen (shakable must be in the chain), " +
                "was max |shift| = $maxBoundsShift",
        )
    }

    @Test
    @AndroidJUnitIgnore
    @JSIgnore
    fun shakeOnInvalid_newTriggerMidShakeDoesNotLeaveTheFieldOffset() = runComposeUiTest {
        // Regression: LaunchedEffect(trigger) CANCELS the in-flight shake when trigger changes. If
        // the restart's isError guard then skips (the value was corrected mid-animation, e.g. by
        // autofill), xPosition is stranded wherever animateTo was interrupted and the field renders
        // permanently offset. The shake must always unwind to 0f.
        val shakingState = ShakingState(ShakeStrength, ShakeDirection)
        var trigger by mutableStateOf(0)
        var isError by mutableStateOf(true)
        mainClock.autoAdvance = false
        setContent {
            TextField(
                value = "", onValueChange = {},
                modifier = Modifier
                    .testTag("a")
                    .shakeOnInvalid(isError = isError, trigger = trigger, shakingState = shakingState),
            )
        }
        mainClock.advanceTimeByFrame()
        trigger = 1
        // Step forward until the shake has actually left 0f, so the cancellation below happens
        // mid-animateTo (the whole point). Small steps avoid landing on a 0-crossing.
        var offset = 0f
        repeat(20) {
            if (offset != 0f) return@repeat
            mainClock.advanceTimeBy(5L)
            offset = shakingState.xPosition.value
        }
        assertTrue(
            offset != 0f,
            "precondition: the shake must be mid-flight (off 0f) for this test to be meaningful",
        )
        // Value corrected + re-submitted inside the ~240ms shake window.
        isError = false
        trigger = 2
        repeat(4) { mainClock.advanceTimeByFrame() }
        assertEquals(
            0f, shakingState.xPosition.value,
            "a cancelled shake must snap back to 0f, not strand the field offset",
        )
    }
}
