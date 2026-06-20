package com.chrisjenx.yakcov.generic

import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import com.chrisjenx.yakcov.ImmutableListState
import com.chrisjenx.yakcov.ImmutableValueState
import com.chrisjenx.yakcov.RegularValidationResult
import com.chrisjenx.yakcov.ResourceValidationResult
import com.chrisjenx.yakcov.ValidationResult
import com.chrisjenx.yakcov.ValueValidatorRule
import yakcov.library.generated.resources.Res
import yakcov.library.generated.resources.ruleInList
import yakcov.library.generated.resources.ruleIsChecked
import yakcov.library.generated.resources.ruleIsNotChecked
import yakcov.library.generated.resources.ruleMaxValue
import yakcov.library.generated.resources.ruleMinValue
import yakcov.library.generated.resources.ruleNotEmptyList
import yakcov.library.generated.resources.ruleNotNull

@Stable
class Required<T> : ValueValidatorRule<T?> {
    override fun validate(value: T?): ValidationResult {
        return if (value == null) ResourceValidationResult.error(Res.string.ruleNotNull)
        else RegularValidationResult.success()
    }
}

@Stable
class InList<T>(list: State<List<T>>) : ValueValidatorRule<T?> {
    constructor(list: List<T>) : this(ImmutableListState(list))

    private val list: List<T> by list
    override fun validate(value: T?): ValidationResult {
        // value must be one of the allowed values; null passes (Required owns presence)
        return if (value == null || value in list) RegularValidationResult.success()
        else ResourceValidationResult.error(Res.string.ruleInList)
    }
}

@Stable
class ListNotEmpty<T : List<*>?> : ValueValidatorRule<T> {
    override fun validate(value: T): ValidationResult {
        return if (value.isNullOrEmpty()) ResourceValidationResult.error(Res.string.ruleNotEmptyList)
        else RegularValidationResult.success()
    }
}

@Stable
data object IsChecked : ValueValidatorRule<Boolean?> {
    override fun validate(value: Boolean?): ValidationResult {
        return if (value == true) RegularValidationResult.success()
        else ResourceValidationResult.error(Res.string.ruleIsChecked)
    }
}

@Stable
data object IsNotChecked : ValueValidatorRule<Boolean?> {
    override fun validate(value: Boolean?): ValidationResult {
        return if (value != true) RegularValidationResult.success()
        else ResourceValidationResult.error(Res.string.ruleIsNotChecked)
    }
}

/**
 * Typed lower bound for a numeric field, complementing the String-based [strings.MinValue].
 * `null` passes through (use [Required] for presence), otherwise the value must be `>= min`.
 */
@Stable
class Min<N>(min: State<N>) : ValueValidatorRule<N?> where N : Number, N : Comparable<N> {
    constructor(min: N) : this(ImmutableValueState(min))

    private val _min: N by min
    override fun validate(value: N?): ValidationResult {
        return when {
            value == null -> RegularValidationResult.success()
            value < _min -> ResourceValidationResult.error(Res.string.ruleMinValue, _min)
            else -> RegularValidationResult.success()
        }
    }
}

/**
 * Typed upper bound for a numeric field, complementing the String-based [strings.MaxValue].
 * `null` passes through (use [Required] for presence), otherwise the value must be `<= max`.
 */
@Stable
class Max<N>(max: State<N>) : ValueValidatorRule<N?> where N : Number, N : Comparable<N> {
    constructor(max: N) : this(ImmutableValueState(max))

    private val _max: N by max
    override fun validate(value: N?): ValidationResult {
        return when {
            value == null -> RegularValidationResult.success()
            value > _max -> ResourceValidationResult.error(Res.string.ruleMaxValue, _max)
            else -> RegularValidationResult.success()
        }
    }
}

/**
 * Typed inclusive range `[min, max]` for a numeric field. `null` passes through; below [min]
 * reports the min message, above [max] the max message.
 */
@Stable
class InRange<N>(min: State<N>, max: State<N>) : ValueValidatorRule<N?> where N : Number, N : Comparable<N> {
    constructor(min: N, max: N) : this(ImmutableValueState(min), ImmutableValueState(max))

    private val _min: N by min
    private val _max: N by max
    override fun validate(value: N?): ValidationResult {
        return when {
            value == null -> RegularValidationResult.success()
            value < _min -> ResourceValidationResult.error(Res.string.ruleMinValue, _min)
            value > _max -> ResourceValidationResult.error(Res.string.ruleMaxValue, _max)
            else -> RegularValidationResult.success()
        }
    }
}

