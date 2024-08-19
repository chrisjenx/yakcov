package com.chrisjenx.yakcov.generic

import androidx.compose.runtime.Stable
import com.chrisjenx.yakcov.RegularValidationResult
import com.chrisjenx.yakcov.ResourceValidationResult
import com.chrisjenx.yakcov.ValidationResult
import com.chrisjenx.yakcov.ValueValidatorRule
import yakcov.library.generated.resources.Res
import yakcov.library.generated.resources.ruleIsChecked
import yakcov.library.generated.resources.ruleIsNotChecked
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

