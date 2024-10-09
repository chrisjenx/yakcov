package com.chrisjenx.yakcov

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
expect annotation class IOSIgnore()

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
expect annotation class AndroidJUnitIgnore()

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
expect annotation class JSIgnore()
