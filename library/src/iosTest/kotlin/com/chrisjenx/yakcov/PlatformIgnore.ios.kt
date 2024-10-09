package com.chrisjenx.yakcov

actual typealias IOSIgnore = kotlin.test.Ignore

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
actual annotation class AndroidJUnitIgnore

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
actual annotation class JSIgnore()