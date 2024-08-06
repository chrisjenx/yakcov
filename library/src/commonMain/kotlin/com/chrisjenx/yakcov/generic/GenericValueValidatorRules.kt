package com.chrisjenx.yakcov.generic

import androidx.compose.runtime.Stable
import com.chrisjenx.yakcov.StringResourceValidation
import com.chrisjenx.yakcov.StringValidation
import com.chrisjenx.yakcov.ValueValidatorRule
import yakcov.library.generated.resources.Res
import yakcov.library.generated.resources.ruleIsChecked
import yakcov.library.generated.resources.ruleIsNotChecked
import yakcov.library.generated.resources.ruleNotEmptyList
import yakcov.library.generated.resources.ruleNotNull

@Stable
class NotNull<T> : ValueValidatorRule<T?> {
    override fun validate(value: T?): StringValidation? {
        return if (value == null) StringResourceValidation(Res.string.ruleNotNull) else null
    }
}

@Stable
class ListNotEmpty<T : List<*>?> : ValueValidatorRule<T> {
    override fun validate(value: T): StringValidation? {
        return if (value.isNullOrEmpty()) StringResourceValidation(Res.string.ruleNotEmptyList)
        else null
    }
}

@Stable
data object IsChecked : ValueValidatorRule<Boolean?> {
    override fun validate(value: Boolean?): StringValidation? {
        return if (value == true) null else StringResourceValidation(Res.string.ruleIsChecked)
    }
}

@Stable
data object IsNotChecked : ValueValidatorRule<Boolean?> {
    override fun validate(value: Boolean?): StringValidation? {
        return if (value != true) null else StringResourceValidation(Res.string.ruleIsNotChecked)
    }
}

