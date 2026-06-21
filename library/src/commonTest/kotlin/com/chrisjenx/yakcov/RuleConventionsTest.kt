package com.chrisjenx.yakcov

import com.chrisjenx.yakcov.ValidationResult.Outcome
import com.chrisjenx.yakcov.generic.InList
import com.chrisjenx.yakcov.generic.InRange
import com.chrisjenx.yakcov.generic.IsChecked
import com.chrisjenx.yakcov.generic.ListNotEmpty
import com.chrisjenx.yakcov.generic.Max
import com.chrisjenx.yakcov.generic.Min
import com.chrisjenx.yakcov.strings.MaxLength
import kotlin.test.Test
import kotlin.test.assertEquals
import com.chrisjenx.yakcov.generic.Required as GenericRequired

/**
 * Cross-platform behavioural contract for the "blank passes — `Required` owns emptiness"
 * convention, driven by the [RuleConventions] catalog. Runs on every platform's test leg.
 *
 * Completeness (every rule on the classpath is classified here) is enforced separately by the
 * JVM-only `RuleRegistryGuardTest`, which can use reflection.
 */
class RuleConventionsTest {

    @Test
    fun emptyPassesStringRules_passEmpty() {
        // The canonical "absent field" is "". Every defer-to-Required string rule must pass it.
        // This is what catches the #37 regression (MinLength(3).validate("") used to ERROR).
        RuleConventions.emptyPassesStringRules.forEach { rule ->
            assertEquals(
                Outcome.SUCCESS, rule.validate("").outcome(),
                "${rule::class.simpleName} should pass an empty string (Required owns emptiness)"
            )
        }
    }

    @Test
    fun blankGuardedStringRules_passWhitespaceOnly() {
        // Whitespace-only is non-empty but blank, so it specifically exercises the
        // `if (value.isBlank())` short-circuit. MaxLength is excluded: it has no such guard (it
        // passes "" only because length 0 is within bound), so an over-long whitespace string
        // legitimately fails its length cap.
        RuleConventions.emptyPassesStringRules
            .filterNot { it is MaxLength }
            .forEach { rule ->
                assertEquals(
                    Outcome.SUCCESS, rule.validate("   ").outcome(),
                    "${rule::class.simpleName} should pass a whitespace-only string"
                )
            }
    }

    @Test
    fun emptyRejectedStringRules_rejectBlank() {
        RuleConventions.emptyRejectedStringRules.forEach { rule ->
            assertEquals(
                Outcome.ERROR, rule.validate("").outcome(),
                "${rule::class.simpleName} should reject an empty string"
            )
        }
    }

    @Test
    fun emptyPassesGenericRules_passNull() {
        assertEquals(Outcome.SUCCESS, InList(listOf(1)).validate(null).outcome(), "InList should pass null")
        assertEquals(Outcome.SUCCESS, Min(1).validate(null).outcome(), "Min should pass null")
        assertEquals(Outcome.SUCCESS, Max(1).validate(null).outcome(), "Max should pass null")
        assertEquals(Outcome.SUCCESS, InRange(1, 2).validate(null).outcome(), "InRange should pass null")
    }

    @Test
    fun emptyRejectedGenericRules_rejectNull() {
        assertEquals(Outcome.ERROR, GenericRequired<Any>().validate(null).outcome(), "Required should reject null")
        assertEquals(
            Outcome.ERROR, ListNotEmpty<List<Any>?>().validate(null).outcome(),
            "ListNotEmpty should reject null"
        )
        assertEquals(Outcome.ERROR, IsChecked.validate(null).outcome(), "IsChecked should reject null")
    }
}
