package com.gucci.pollitely

import kotlinx.coroutines.Deferred

class LongRunningToken(
    val id: String,
    val response: suspend (LongRunningResponseContext) -> Unit,
    private val deferred: Deferred<*>,
) {

    override fun toString(): String {
        return "${javaClass.simpleName}(id=$id)"
    }

    fun stop() {
        deferred.cancel()
    }

    fun status(): String {
        return when {
            deferred.isCompleted -> "done"
            deferred.isActive -> "wip"
            else -> "todo"
        }
    }

    fun content(): Any {
        return deferred.getCompleted()!!
    }
}