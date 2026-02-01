package io.maryk.app.config

import kotlin.random.Random
import maryk.file.File

enum class StoreKind(val label: String) {
    ROCKS_DB("RocksDB"),
    FOUNDATION_DB("FoundationDB"),
    REMOTE("Remote"),
}

data class StoreDefinition(
    val id: String,
    val name: String,
    val type: StoreKind,
    val directory: String,
    val clusterFile: String? = null,
    val tenant: String? = null,
    val sshHost: String? = null,
    val sshUser: String? = null,
    val sshPort: Int? = null,
    val sshLocalPort: Int? = null,
    val sshIdentityFile: String? = null,
) {
    fun displayLocation(): String = when (type) {
        StoreKind.ROCKS_DB -> directory
        StoreKind.FOUNDATION_DB -> buildString {
            append(directory)
            clusterFile?.takeIf { it.isNotBlank() }?.let { append(" (cluster: ").append(it).append(')') }
            tenant?.takeIf { it.isNotBlank() }?.let { append(" (tenant: ").append(it).append(')') }
        }
        StoreKind.REMOTE -> buildString {
            append(directory)
            sshHost?.takeIf { it.isNotBlank() }?.let { host ->
                append(" (ssh: ")
                sshUser?.takeIf { it.isNotBlank() }?.let { user -> append(user).append('@') }
                append(host)
                sshPort?.takeIf { it != 22 }?.let { port -> append(':').append(port) }
                append(')')
            }
        }
    }
}

class StoreRepository(
    private val path: String = storesFilePath(),
) {
    fun load(): List<StoreDefinition> {
        val content = File.readText(path) ?: return emptyList()
        return content.lineSequence()
            .mapNotNull { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#")) return@mapNotNull null
                parseLine(trimmed)
            }
            .toList()
    }

    fun save(stores: List<StoreDefinition>) {
        ensureParentDirectory(path)
        val body = buildString {
            append("# Maryk app store connections\n")
            append("# id\tname\ttype\tpath_or_url\tcluster\ttenant\tssh_host\tssh_user\tssh_port\tssh_local_port\tssh_identity_file\n")
            stores.forEach { store ->
                append(encode(store.id))
                append('\t')
                append(encode(store.name))
                append('\t')
                append(encode(store.type.name))
                append('\t')
                append(encode(store.directory))
                append('\t')
                append(encode(store.clusterFile.orEmpty()))
                append('\t')
                append(encode(store.tenant.orEmpty()))
                append('\t')
                append(encode(store.sshHost.orEmpty()))
                append('\t')
                append(encode(store.sshUser.orEmpty()))
                append('\t')
                append(encode(store.sshPort?.toString().orEmpty()))
                append('\t')
                append(encode(store.sshLocalPort?.toString().orEmpty()))
                append('\t')
                append(encode(store.sshIdentityFile.orEmpty()))
                append('\n')
            }
        }
        File.writeText(path, body)
    }

    private fun parseLine(line: String): StoreDefinition? {
        val parts = splitFields(line)
        if (parts.size < 4) return null
        val id = decode(parts[0]).ifBlank { generateId() }
        val name = decode(parts[1])
        val type = decode(parts[2]).let { value ->
            StoreKind.entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
        } ?: return null
        val directory = decode(parts[3])
        if (name.isBlank() || directory.isBlank()) return null
        val clusterFile = parts.getOrNull(4)?.let { decode(it) }?.ifBlank { null }
        val tenant = parts.getOrNull(5)?.let { decode(it) }?.ifBlank { null }
        val sshHost = parts.getOrNull(6)?.let { decode(it) }?.ifBlank { null }
        val sshUser = parts.getOrNull(7)?.let { decode(it) }?.ifBlank { null }
        val sshPort = parts.getOrNull(8)?.let { decode(it) }?.ifBlank { null }?.toIntOrNull()
        val sshLocalPort = parts.getOrNull(9)?.let { decode(it) }?.ifBlank { null }?.toIntOrNull()
        val sshIdentityFile = parts.getOrNull(10)?.let { decode(it) }?.ifBlank { null }
        return StoreDefinition(
            id = id,
            name = name,
            type = type,
            directory = directory,
            clusterFile = clusterFile,
            tenant = tenant,
            sshHost = sshHost,
            sshUser = sshUser,
            sshPort = sshPort,
            sshLocalPort = sshLocalPort,
            sshIdentityFile = sshIdentityFile,
        )
    }

    private fun encode(value: String): String = buildString {
        value.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                '\t' -> append("\\t")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                else -> append(ch)
            }
        }
    }

    private fun decode(value: String): String {
        if (!value.contains('\\')) return value
        val result = StringBuilder()
        var index = 0
        while (index < value.length) {
            val ch = value[index]
            if (ch != '\\' || index == value.lastIndex) {
                result.append(ch)
                index += 1
                continue
            }
            val next = value[index + 1]
            when (next) {
                't' -> result.append('\t')
                'n' -> result.append('\n')
                'r' -> result.append('\r')
                '\\' -> result.append('\\')
                else -> result.append(next)
            }
            index += 2
        }
        return result.toString()
    }

    private fun splitFields(line: String): List<String> {
        val parts = mutableListOf<String>()
        val buffer = StringBuilder()
        var index = 0
        while (index < line.length) {
            val ch = line[index]
            if (ch == '\t') {
                parts += buffer.toString()
                buffer.clear()
            } else {
                buffer.append(ch)
            }
            index += 1
        }
        parts += buffer.toString()
        return parts
    }

    private fun generateId(): String {
        val bytes = ByteArray(8)
        repeat(bytes.size) { idx ->
            bytes[idx] = Random.nextInt(0, 256).toByte()
        }
        return bytes.joinToString(separator = "") { byte ->
            val hex = (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
            hex
        }
    }
}