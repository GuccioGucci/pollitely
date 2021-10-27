package com.gucci.polling

import io.ktor.application.*

interface LongRunningRequestContext {
    fun id(): String
    fun call(): ApplicationCall
}