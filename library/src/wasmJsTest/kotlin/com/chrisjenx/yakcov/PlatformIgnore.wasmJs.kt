package com.chrisjenx.yakcov

import kotlin.test.Ignore

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
actual annotation class IOSIgnore()

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
actual annotation class AndroidJUnitIgnore()

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
actual annotation class JSIgnore()

actual typealias WasmJsIgnore = Ignore
