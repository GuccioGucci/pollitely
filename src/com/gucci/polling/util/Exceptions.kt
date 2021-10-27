package api.util

class Exceptions {
    companion object {
        fun describe(exception: Exception): String? {
            return exception.message?.lines()?.joinToString(" ")
        }
    }
}