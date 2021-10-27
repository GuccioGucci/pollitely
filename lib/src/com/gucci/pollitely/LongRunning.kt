package com.gucci.pollitely

import api.util.Exceptions
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.async
import mu.KotlinLogging
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

class LongRunning(private val ids: Ids, private val every: Int = 1) {

    private val logger = KotlinLogging.logger {}
    private val tokens = arrayListOf<LongRunningToken>()

    fun with(
        request: suspend (LongRunningRequestContext) -> Any,
        response: suspend (LongRunningResponseContext) -> Unit = defaultResponse()
    ): Route.() -> Unit {
        return {
            submit(request, response)
            find()
            stop()
            list()
        }
    }

    private fun Route.submit(
        request: suspend (LongRunningRequestContext) -> Any,
        response: suspend (LongRunningResponseContext) -> Unit
    ) {
        post {
            val id = ids.next()
            logger.debug("Next id: [{}]", id)

            warmUp(call)
            logger.debug("Application call: [{}]", call)

            val context = coroutineContext + ApplicationCallContext(call) + CoroutineName("tokens")
            val token = LongRunningToken(id = id, response = response, deferred = async(context) {
                logger.debug("Starting: [{}]", this)
                val applicationCall = coroutineContext[ApplicationCallContext]!!.call
                logger.debug("Application call: [{}]", applicationCall)

                try {
                    val result = request.invoke(LongRunningRequestContextWrapper(id, applicationCall))
                    logger.debug("Done: [{}]", this)
                    return@async result
                } catch (e: Exception) {
                    logger.error("Error: [{}]. Cause: [{}]", this, e.message)
                    throw e
                }
            })

            logger.info("Submitting: [{}]", token)
            tokens.add(token)

            logger.debug("Submitted: [{}]", token)
            call.response.header(HttpHeaders.Location, locate(call, token.id))
            call.respond(HttpStatusCode.Accepted)
        }
    }

    private fun Route.find() {
        get("{id}") {
            val id = call.parameters["id"]
            val token = tokens.firstOrNull { it.id == id }

            if (token == null) {
                logger.debug("Not found, id: [{}]", id)
                call.respond(HttpStatusCode.NotFound)
                return@get
            }

            when (token.status()) {
                "done" -> {
                    logger.debug("Found, done: [{}]", token)
                    tokens.remove(token)
                    token.response.invoke(LongRunningResponseContextWrapper(token, call))
                }
                else -> {
                    logger.debug("Found, not yet done: [{}]", token)
                    call.response.header(HttpHeaders.RetryAfter, every)
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }
    }

    private fun Route.stop() {
        delete("{id}") {
            val id = call.parameters["id"]
            val token = tokens.firstOrNull { it.id == id }

            if (token == null) {
                logger.debug("Not found, id: [{}]", id)
                call.respond(HttpStatusCode.NotFound)
                return@delete
            }

            logger.info("Found, stopping: [{}]", token)
            token.stop()

            logger.debug("Removing: [{}]", token)
            tokens.remove(token)
            call.respond(HttpStatusCode.OK)
        }
    }

    private fun Route.list() {
        get {
            call.respond(
                HttpStatusCode.OK,
                mapOf("tokens" to tokens.map {
                    mapOf(
                        "id" to it.id,
                        "status" to it.status(),
                        "href" to locate(call, it.id)
                    )
                })
            )
        }
    }

    private fun defaultResponse(): suspend (LongRunningResponseContext) -> Unit {
        return {
            try {
                it.call().respond(HttpStatusCode.OK, it.result())
            } catch (e: Exception) {
                logger.error("Application responded with error: [{}]", e.message)
                logger.debug("Cause: {}", Exceptions.describe(e))
                it.call().respond(
                    status = if (e is ClientError) HttpStatusCode.BadRequest else HttpStatusCode.InternalServerError,
                    message = e.message.toString()
                )
            }
        }
    }

    private suspend fun warmUp(call: ApplicationCall) {
        // please, ensure DoubleReceive feature is installed
        logger.debug("Warming up call: [{}]", call)
        call.receiveChannel()
    }

    private fun locate(call: ApplicationCall, id: String): String {
        return "${call.request.path()}/${id}"
    }

    class ApplicationCallContext(val call: ApplicationCall) : AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<ApplicationCallContext>
    }

    class LongRunningRequestContextWrapper(val id: String, val call: ApplicationCall) : LongRunningRequestContext {
        override fun id(): String {
            return id
        }

        override fun call(): ApplicationCall {
            return call
        }
    }

    class LongRunningResponseContextWrapper(private val token: LongRunningToken, private val call: ApplicationCall) :
        LongRunningResponseContext {

        override suspend fun result(): Any {
            return token.content()
        }

        override fun call(): ApplicationCall {
            return call
        }
    }
}