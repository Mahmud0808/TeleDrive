package com.drdisagree.teledrive.core.transfer

import java.lang.reflect.Proxy

internal fun namedErrorMessages(): TransferErrorMessages = Proxy.newProxyInstance(
    TransferErrorMessages::class.java.classLoader,
    arrayOf(TransferErrorMessages::class.java)
) { _, method, _ -> method.name } as TransferErrorMessages
