package maryk.datastore.remote

internal fun String.isLoopbackRemoteHost(): Boolean = when (lowercase().removePrefix("[").removeSuffix("]")) {
    "localhost", "127.0.0.1", "::1", "[::1]", "0:0:0:0:0:0:0:1" -> true
    else -> false
}

internal fun String.isLoopbackIpLiteral(): Boolean {
    val host = lowercase().removePrefix("[").removeSuffix("]")
    if (host == "::1" || host == "0:0:0:0:0:0:0:1") return true
    val octets = host.split('.')
    return octets.size == 4 &&
        octets.first() == "127" &&
        octets.all { octet ->
            octet.isNotEmpty() && octet.all(Char::isDigit) && octet.toUIntOrNull()?.let { it <= 255u } == true
        }
}
