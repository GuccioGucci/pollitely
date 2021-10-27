package com.gucci.polling

import java.util.concurrent.atomic.AtomicInteger

interface Ids {
    fun next(): String

    class Sequential(private val seed: AtomicInteger = AtomicInteger(0)) : Ids {
        override fun next(): String {
            return seed.incrementAndGet().toString()
        }
    }

}
