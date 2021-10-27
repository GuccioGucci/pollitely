package com.gucci

import com.gucci.pollitely.Ids
import com.gucci.pollitely.LongRunning
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.jackson.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.coroutines.delay

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

@kotlin.jvm.JvmOverloads
fun Application.module() {
    install(DoubleReceive) {
        receiveEntireContent = true
    }

    routing {
        route("/api/executions", LongRunning(Ids.Sequential()).with({
            delay(10000)
            val name: Any = it.call().request.queryParameters["name"] ?: "Bob"
            return@with "Hello, $name"
        }))
    }
}

