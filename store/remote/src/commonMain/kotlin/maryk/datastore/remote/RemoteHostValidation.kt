package maryk.datastore.remote

internal fun String.isLoopbackRemoteHost(): Boolean = when (lowercase().removePrefix("[").removeSuffix("]")) {
    "localhost", "127.0.0.1", "::1", "[::1]", "0:0:0:0:0:0:0:1" -> true
    else -> false
}
