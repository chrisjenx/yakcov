package com.chrisjenx.yakcov

interface ValueValidatorRule<V> {
    /**
     * Validate the [value] and return an [ValidationResult].
     *
     * @param value The value to validate.
     * @return The result of the validation. [ValidationResult.outcome]
     *  will default to ERROR unless overridden
     */
    fun validate(value: V): ValidationResult

}
