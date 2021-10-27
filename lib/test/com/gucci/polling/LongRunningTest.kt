package com.gucci.polling

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.jackson.*
import io.ktor.request.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import kotlinx.coroutines.delay
import mu.KotlinLogging
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.Test

class LongRunningTest {

    private val logger = KotlinLogging.logger {}

    private fun samples(application: Application) {
        application.install(ContentNegotiation) {
            jackson()
        }

        application.routing {
            route("/naps", LongRunning(ids = Ids.Sequential()).with({
                logger.info("Going to sleep")
                delay(1000)
                logger.info("Awake!")
                return@with Nap(
                    id = it.id(),
                    message = it.call().parameters["message"]
                )
            }))
            route("/failures", LongRunning(ids = Ids.Sequential()).with({
                logger.info("Going to sabotate...")
                throw Exception("Sabotage!")
            }))
            route("/validations", LongRunning(ids = Ids.Sequential()).with({
                logger.info("Going to invalidate client data...")
                throw NoDataException("No data!")
            }))
        }
    }

    data class Nap(val id: String, val message: String?)
    class NoDataException(message: String) : Exception(message), ClientError

    @Test
    fun submitWithParameters() {
        withTestApplication(this::samples) {
            handleRequest(HttpMethod.Post, "/naps?message=hello").apply {
                assertThat(response.headers["Location"], `is`("/naps/1"))
            }
            handleRequest(HttpMethod.Get, "/naps/1").apply {
                assertThat(response.content, `is`("{\"id\":\"1\",\"message\":\"hello\"}"))
            }
        }
    }

    @Test
    fun submitAndPoll() {
        withTestApplication(this::samples) {
            logger.info("Before submitting, not found")
            handleRequest(HttpMethod.Get, "/naps/1").apply {
                assertThat(response.status(), `is`(HttpStatusCode.NotFound))
            }

            logger.info("Submitted")
            handleRequest(HttpMethod.Post, "/naps").apply {
                assertThat(response.status(), `is`(HttpStatusCode.Accepted))
            }

            logger.info("Once submitted, not yet done")
//            handleRequest(HttpMethod.Get, "/naps/1").apply {
//                assertThat(response.status(), `is`(HttpStatusCode.NoContent))
//                assertThat(response.headers["Retry-After"], `is`("10"))
//            }
//
//            logger.info("Waiting...")
//            Thread.sleep(2000)

            logger.info("Once done, found")
            handleRequest(HttpMethod.Get, "/naps/1").apply {
                assertThat(response.status(), `is`(HttpStatusCode.OK))
            }

            logger.info("Then, no more found")
            handleRequest(HttpMethod.Get, "/naps/1").apply {
                assertThat(response.status(), `is`(HttpStatusCode.NotFound))
            }
        }
    }

    @Test
    fun listNotYetStarted() {
        withTestApplication(this::samples) {
            handleRequest(HttpMethod.Get, "/naps").apply {
                assertThat(response.status(), `is`(HttpStatusCode.OK))
                assertThat(response.content, `is`("{\"tokens\":[]}"))
            }
        }
    }

    @Test
    fun listStarted() {
        withTestApplication(this::samples) {
            handleRequest(HttpMethod.Post, "/naps")
            handleRequest(HttpMethod.Get, "/naps").apply {
                assertThat(response.status(), `is`(HttpStatusCode.OK))
                assertThat(
                    response.content,
                    `is`("{\"tokens\":[{\"id\":\"1\",\"status\":\"done\",\"href\":\"/naps/1\"}]}")
                )
            }
        }
    }

    @Test
    fun stopNotYetRunning() {
        withTestApplication(this::samples) {
            handleRequest(HttpMethod.Delete, "/naps/1").apply {
                assertThat(response.status(), `is`(HttpStatusCode.NotFound))
            }
        }
    }

    @Test
    fun stopRunning() {
        withTestApplication(this::samples) {
            handleRequest(HttpMethod.Post, "/naps").apply {
                assertThat(response.status(), `is`(HttpStatusCode.Accepted))
            }

            handleRequest(HttpMethod.Delete, "/naps/1").apply {
                assertThat(response.status(), `is`(HttpStatusCode.OK))
            }

            handleRequest(HttpMethod.Get, "/naps/1").apply {
                assertThat(response.status(), `is`(HttpStatusCode.NotFound))
            }
        }
    }

    @Test
    fun onApplicationError() {
        withTestApplication(this::samples) {
            logger.info("Submitted")

            safely { handleRequest(HttpMethod.Post, "/failures") }?.apply {
                assertThat(response.status(), `is`(HttpStatusCode.Accepted))
            }

            logger.info("Once done, found - with error detail")
            handleRequest(HttpMethod.Get, "/failures/1").apply {
                assertThat(response.status(), `is`(HttpStatusCode.InternalServerError))
                assertThat(response.content, `is`("Sabotage!"))
            }
        }
    }

    @Test
    fun onClientError() {
        withTestApplication(this::samples) {
            logger.info("Submitted")

            safely { handleRequest(HttpMethod.Post, "/validations") }?.apply {
                assertThat(response.status(), `is`(HttpStatusCode.Accepted))
            }

            logger.info("Once done, found - with error detail")
            handleRequest(HttpMethod.Get, "/validations/1").apply {
                assertThat(response.status(), `is`(HttpStatusCode.BadRequest))
                assertThat(response.content, `is`("No data!"))
            }
        }
    }

    private fun <T> safely(action: () -> T): T? {
        return try {
            action.invoke()
        } catch (e: Exception) {
            null
        }
    }
}