package com.chrisjenx.yakcov

interface ValueValidatorRule<V> {
    /**
     * Validate the [value] and return an error message if the value is invalid,
     * or null if the value is valid.
     */
    abstract fun validate(value: V): StringValidation?

}
