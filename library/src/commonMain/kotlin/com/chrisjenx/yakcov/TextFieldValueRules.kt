package com.chrisjenx.yakcov

import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.datetime.LocalDate
import yakcov.library.generated.resources.Res
import yakcov.library.generated.resources.ruleDay
import yakcov.library.generated.resources.ruleDecimal
import yakcov.library.generated.resources.ruleEmail
import yakcov.library.generated.resources.ruleInvalidDate
import yakcov.library.generated.resources.ruleMaxLength
import yakcov.library.generated.resources.ruleMaxValue
import yakcov.library.generated.resources.ruleMinLength
import yakcov.library.generated.resources.ruleMinValue
import yakcov.library.generated.resources.ruleMonth
import yakcov.library.generated.resources.ruleNumeric
import yakcov.library.generated.resources.rulePasswords
import yakcov.library.generated.resources.rulePhone
import yakcov.library.generated.resources.ruleRequired
import yakcov.library.generated.resources.ruleYear

@Stable
sealed class TextFieldValueRule {
    /**
     * Validate the [value] and return an error message if the value is invalid,
     * or null if the value is valid.
     */
    abstract fun validate(value: TextFieldValue): StringValidation?
}

@Stable
data object Required : TextFieldValueRule() {
    override fun validate(value: TextFieldValue): StringValidation? {
        return if (value.text.isBlank()) StringValidation(Res.string.ruleRequired) else null
    }
}


@Stable
data object Numeric : TextFieldValueRule() {
    override fun validate(value: TextFieldValue): StringValidation? {
        return if (value.text.toLongOrNull() == null) {
            StringValidation(Res.string.ruleNumeric)
        } else {
            null
        }
    }
}


@Stable
data object Decimal : TextFieldValueRule() {
    override fun validate(value: TextFieldValue): StringValidation? {
        return if (value.text.toFloatOrNull() == null) {
            StringValidation(Res.string.ruleDecimal)
        } else {
            null
        }
    }
}

@Stable
data class MinValue(val minValue: Number) : TextFieldValueRule() {
    override fun validate(value: TextFieldValue): StringValidation? {
        val number = value.text.toDoubleOrNull()
        return if (number == null || number < minValue.toDouble()) {
            StringValidation(Res.string.ruleMinValue, minValue)
        } else null
    }
}

@Stable
data class MaxValue(val maxValue: Float) : TextFieldValueRule() {
    override fun validate(value: TextFieldValue): StringValidation? {
        val number = value.text.toFloatOrNull()
        return if (value.text.isNotBlank() && (number == null || number > maxValue)) {
            StringValidation(Res.string.ruleMaxValue, maxValue)
        } else null
    }
}

@Stable
data class MinLength(val minLength: Int) : TextFieldValueRule() {
    override fun validate(value: TextFieldValue): StringValidation? {
        return if (value.text.length < minLength) {
            StringValidation(Res.string.ruleMinLength, minLength)
        } else null
    }
}

@Stable
data class MaxLength(val maxLength: Int) : TextFieldValueRule() {
    override fun validate(value: TextFieldValue): StringValidation? {
        return if (value.text.length > maxLength) {
            StringValidation(Res.string.ruleMaxLength, maxLength)
        } else null
    }
}

// Email
@Stable
data object Email : TextFieldValueRule() {
    override fun validate(value: TextFieldValue): StringValidation? {
        // only validate if not empty as Required will check if not empty
        return if (!value.text.isEmail()) {
            StringValidation(Res.string.ruleEmail)
        } else null
    }
}

// Phone
@Stable
data object Phone : TextFieldValueRule() {
    override fun validate(value: TextFieldValue): StringValidation? {
        // only validate if not empty as Required will check if not empty
        return if (!value.text.isPhoneNumber()) {
            StringValidation(Res.string.rulePhone)
        } else null
    }
}

@Stable
data class DayValidation(val localDate: State<LocalDate>) : TextFieldValueRule() {
    override fun validate(value: TextFieldValue): StringValidation? {
        val current = localDate.value
        val dayOfMonth = value.text.toIntOrNull() ?: return StringValidation(Res.string.ruleDay)
        runCatching {
            LocalDate(
                current.year,
                current.monthNumber,
                dayOfMonth
            )
        }.onFailure { return StringValidation(Res.string.ruleInvalidDate, it.message ?: "") }
        return null
    }
}

@Stable
data class MonthValidation(val localDate: State<LocalDate>) : TextFieldValueRule() {
    override fun validate(value: TextFieldValue): StringValidation? {
        val current = localDate.value
        val monthOfYear = value.text.toIntOrNull() ?: return StringValidation(Res.string.ruleMonth)
        runCatching {
            LocalDate(
                current.year,
                monthOfYear,
                current.dayOfMonth
            )
        }.onFailure { return StringValidation(Res.string.ruleInvalidDate, it.message ?: "") }
        return null
    }
}

@Stable
data class YearValidation(val localDate: State<LocalDate>) : TextFieldValueRule() {
    override fun validate(value: TextFieldValue): StringValidation? {
        val current = localDate.value
        val year = value.text.toIntOrNull() ?: return StringValidation(Res.string.ruleYear)
        runCatching {
            LocalDate(
                year,
                current.monthNumber,
                current.dayOfMonth
            )
        }.onFailure { return StringValidation(Res.string.ruleInvalidDate, it.message ?: "") }
        return null
    }
}

@Stable
data class PasswordMatches(val otherField: TextFieldValueValidator) : TextFieldValueRule() {

    override fun validate(value: TextFieldValue): StringValidation? {
        return if (value.text != otherField.value.text) {
            StringValidation(Res.string.rulePasswords)
        } else null
    }

}