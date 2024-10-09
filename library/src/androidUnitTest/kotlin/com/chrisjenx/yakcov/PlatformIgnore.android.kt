package com.chrisjenx.yakcov

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
actual annotation class IOSIgnore

actual typealias AndroidJUnitIgnore = org.junit.Ignore

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
actual annotation class JSIgnore()