package com.chrisjenx.yakcov

import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue

/**
 * Runs [rule] only while [enabled] is `true`; otherwise the value is treated as valid
 * ([RegularValidationResult.success]). Collapses the "apply rule R only when some flag holds"
 * pattern (conditionally-required fields, optional-when-hidden inputs) into one combinator instead
 * of a bespoke rule per case.
 *
 * Prefer the [onlyWhen] extension for readability: `Required.onlyWhen(isBusiness)`.
 */
@Stable
class Optional<V>(
    private val enabled: State<Boolean>,
    private val rule: ValueValidatorRule<V>,
) : ValueValidatorRule<V> {
    constructor(enabled: Boolean, rule: ValueValidatorRule<V>) :
        this(ImmutableBooleanState(enabled), rule)

    private val _enabled by enabled

    override fun validate(value: V): ValidationResult =
        if (_enabled) rule.validate(value) else RegularValidationResult.success()
}

/**
 * Applies this rule only while [enabled] is `true`. See [Optional].
 */
fun <V> ValueValidatorRule<V>.onlyWhen(enabled: State<Boolean>): ValueValidatorRule<V> =
    Optional(enabled, this)

/**
 * Applies this rule only when [enabled] is `true`. See [Optional].
 */
fun <V> ValueValidatorRule<V>.onlyWhen(enabled: Boolean): ValueValidatorRule<V> =
    Optional(enabled, this)
