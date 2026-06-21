package com.chrisjenx.yakcov

import com.chrisjenx.yakcov.strings.Decimal
import com.chrisjenx.yakcov.strings.Email
import com.chrisjenx.yakcov.strings.HexColor
import com.chrisjenx.yakcov.strings.MaxLength
import com.chrisjenx.yakcov.strings.MaxValue
import com.chrisjenx.yakcov.strings.MinLength
import com.chrisjenx.yakcov.strings.MinValue
import com.chrisjenx.yakcov.strings.Numeric
import com.chrisjenx.yakcov.strings.OneOf
import com.chrisjenx.yakcov.strings.Phone
import com.chrisjenx.yakcov.strings.PhoneFormat
import com.chrisjenx.yakcov.strings.Required

/**
 * Catalog of the rule conventions, used by [RuleConventionsTest] (behaviour) and the JVM-only
 * `RuleRegistryGuardTest` (completeness — every concrete `ValueValidatorRule` in the
 * `com.chrisjenx.yakcov` package tree must appear in exactly one bucket here).
 *
 * The library convention is **"blank passes — `Required` owns emptiness"**: a string format rule
 * short-circuits `if (value.isBlank()) return success()`, and the generic membership/bounds rules
 * do the same for `null`. Presence is composed separately via `Required`.
 *
 * When you add a new rule, classify it into exactly one bucket below. The discovery guard fails
 * the build until you do, so the convention can't silently rot (the bug behind issue #37, where
 * `MinLength` was the lone string rule that rejected blank).
 *
 * String rules are held as instances so [RuleConventionsTest] can call `validate` on them; generic
 * rules carry type parameters that resist a shared list, so they are tracked here by name and
 * asserted individually in [RuleConventionsTest] — keep the two in sync.
 *
 * Rules are tracked by **simple name** so the catalog can stay cross-platform (`qualifiedName` is
 * JVM-only). The two `Required` rules (`strings.Required` and `generic.Required`) intentionally
 * share the name "Required" — both reject emptiness, so collapsing them is correct; the guard fails
 * on any *other* simple-name collision.
 */
object RuleConventions {

    /**
     * String rules that defer emptiness to `Required`: an empty `""` field must pass. All but
     * [MaxLength] do so via an explicit `if (value.isBlank()) return success()` short-circuit, so
     * they also pass whitespace-only input. [MaxLength] passes `""` only because length 0 is within
     * any bound (it has no blank guard), so the whitespace-only guarantee is asserted for the others
     * — see [RuleConventionsTest].
     */
    val emptyPassesStringRules: List<ValueValidatorRule<String>> = listOf(
        Numeric,
        Decimal,
        MinValue(1),
        MaxValue(1),
        MinLength(3),
        MaxLength(3),
        Email,
        HexColor,
        OneOf(setOf("US")),
        Phone("US"),
        PhoneFormat,
    )

    /** Presence string rules: blank must be rejected (the inverse of the convention). */
    val emptyRejectedStringRules: List<ValueValidatorRule<String>> = listOf(
        Required,
    )

    // Generic rules carry type parameters that resist a single shared list, so they are asserted
    // individually in RuleConventionsTest; the discovery guard tracks them by simple name here.

    /** Generic membership/bounds rules whose `null` must pass — `Required` owns presence. */
    val emptyPassesGenericRuleNames: Set<String> = setOf("InList", "Min", "Max", "InRange")

    /** Generic presence/state rules whose `null` must be rejected. */
    val emptyRejectedGenericRuleNames: Set<String> = setOf("Required", "ListNotEmpty", "IsChecked")

    /**
     * Rules deliberately exempt from the empty-passes convention. Each needs a reason — these are
     * the conscious exceptions the discovery guard makes you opt into rather than forget.
     */
    val exemptRuleNames: Set<String> = setOf(
        // Parse a required numeric date component; blank is genuinely invalid, not merely "absent".
        "DayValidation", "MonthValidation", "YearValidation",
        // Identity checks against a sibling field; emptiness validity depends on the other field.
        "PasswordMatchesString", "PasswordMatchesTextFieldValue",
        // Inverse boolean state check; null/false pass and only `true` fails — not an emptiness gate.
        "IsNotChecked",
        // Combinator (onlyWhen/Optional): delegates blank/null handling to the rule it wraps, so it
        // has no emptiness convention of its own.
        "Optional",
    )

    /** Every concrete rule simple-name the discovery guard expects to find on the classpath. */
    val coveredRuleSimpleNames: Set<String> =
        (emptyPassesStringRules + emptyRejectedStringRules).mapNotNull { it::class.simpleName }.toSet() +
            emptyPassesGenericRuleNames +
            emptyRejectedGenericRuleNames +
            exemptRuleNames
}
