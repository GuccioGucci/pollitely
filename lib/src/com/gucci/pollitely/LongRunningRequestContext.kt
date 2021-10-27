package com.gucci.pollitely

import io.ktor.application.*

interface LongRunningRequestContext {
    fun id(): String
    fun call(): ApplicationCall
}