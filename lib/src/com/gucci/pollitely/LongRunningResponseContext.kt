package com.gucci.pollitely

import io.ktor.application.*

interface LongRunningResponseContext {
    suspend fun result(): Any
    fun call(): ApplicationCall
}