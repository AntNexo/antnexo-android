package com.antnexo

/** Marks API for wrappers of this library (the React Native SDK), not for apps. */
@RequiresOptIn(message = "For wrappers of the AntNexo SDK (React Native), not for apps.", level = RequiresOptIn.Level.ERROR)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION)
annotation class AntNexoWrapperApi
