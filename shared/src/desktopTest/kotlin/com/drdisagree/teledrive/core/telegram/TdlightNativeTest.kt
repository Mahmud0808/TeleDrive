package com.drdisagree.teledrive.core.telegram

import it.tdlight.ClientFactory
import it.tdlight.ExceptionHandler
import it.tdlight.Init
import it.tdlight.UpdatesHandler
import it.tdlight.jni.TdApi
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertTrue
import org.junit.Test

class TdlightNativeTest {

    @Test
    fun `natives load and TDLib answers a version request`() {
        Init.init()
        ClientFactory.create().use { factory ->
            val client = factory.createClient()
            client.initialize(
                UpdatesHandler { },
                ExceptionHandler { },
                ExceptionHandler { }
            )
            val version = CompletableFuture<String>()
            client.send(TdApi.GetOption("version")) { result ->
                version.complete((result as? TdApi.OptionValueString)?.value ?: result.toString())
            }
            val answer = version.get(20, TimeUnit.SECONDS)
            assertTrue("TDLib version was blank", answer.isNotBlank())
            client.send(TdApi.Close()) { }
        }
    }
}
