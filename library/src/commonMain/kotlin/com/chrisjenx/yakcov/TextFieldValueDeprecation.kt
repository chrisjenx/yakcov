package com.chrisjenx.yakcov

/**
 * Message for the value/onValueChange wrappers, deprecated from the Compose 1.13 channel via
 * [TextFieldValueApi]. A `const` so it can be an annotation argument on every channel.
 */
internal const val TextFieldValueDeprecation: String =
    "Compose 1.13 deprecates value/onValueChange text fields (text round-tripped through state can " +
        "drop keystrokes and misplace pastes). Use TextFieldStateValidator / " +
        "rememberTextFieldStateValidator with a field-owned TextFieldState. " +
        "See docs/decisions/textfieldstate.md."
