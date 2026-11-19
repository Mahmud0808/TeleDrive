package com.drdisagree.teledrive.testing

import java.lang.reflect.Proxy

internal inline fun <reified T : Any> unused(): T = Proxy.newProxyInstance(
    T::class.java.classLoader,
    arrayOf(T::class.java)
) { _, method, _ -> throw UnsupportedOperationException(method.name) } as T
