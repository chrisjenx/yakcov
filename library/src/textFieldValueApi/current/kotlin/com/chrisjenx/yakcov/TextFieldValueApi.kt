package com.chrisjenx.yakcov

/**
 * Pre-1.13 channels: the value/onValueChange text-field wrappers are NOT deprecated, because
 * Compose itself doesn't deprecate that shape before 1.13. A source-only no-op with the same
 * signature as [Deprecated], so the annotated declarations compile unchanged on every channel.
 * On Compose 1.13+ this file is not compiled: the build rewrites every use to [Deprecated]
 * (see `deprecateTextFieldValueApi` in library/build.gradle.kts).
 */
@Retention(AnnotationRetention.SOURCE)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.PROPERTY,
)
internal annotation class TextFieldValueApi(
    val message: String,
    val replaceWith: ReplaceWith = ReplaceWith(""),
    val level: DeprecationLevel = DeprecationLevel.WARNING,
)
