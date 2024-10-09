package com.chrisjenx.yakcov

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
actual annotation class IOSIgnore

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
actual annotation class AndroidJUnitIgnore

actual typealias JSIgnore = kotlin.test.Ignore
