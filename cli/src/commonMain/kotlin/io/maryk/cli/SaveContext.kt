package io.maryk.cli

import maryk.file.File

enum class SaveFormat(val extension: String) {
    YAML("yaml"),
    JSON("json"),
    PROTO("proto"),
    KOTLIN("kt"),
}

data class KotlinSaveResult(
    val files: Map<String, String>,
)

data class SaveContext(
    val key: String,
    val dataYaml: String,
    val dataJson: String,
    val dataProto: ByteArray,
    val metaYaml: String,
    val metaJson: String,
    val metaProto: ByteArray,
    val noDepsYaml: String? = null,
    val noDepsJson: String? = null,
    val noDepsProto: ByteArray? = null,
    val kotlinGenerator: ((packageName: String) -> KotlinSaveResult)? = null,
    val kotlinNoDepsGenerator: ((packageName: String) -> KotlinSaveResult)? = null,
) {
    val supportsNoDeps: Boolean
        get() = noDepsYaml != null || noDepsJson != null || noDepsProto != null || kotlinNoDepsGenerator != null

    fun save(
        directory: String,
        format: SaveFormat,
        includeMeta: Boolean,
        packageName: String? = null,
        noDeps: Boolean = false,
    ): String {
        val basePath = directory.trimEnd('/', '\\')
        val safeKey = sanitizeSaveFileName(key)
        if (format == SaveFormat.KOTLIN) {
            val generator = if (noDeps) kotlinNoDepsGenerator else kotlinGenerator
            generator ?: return if (noDeps) {
                "No-deps Kotlin output not available for this data."
            } else {
                "Kotlin output not available for this data."
            }
            val packageValue = packageName ?: return "Kotlin save requires --package <name>."
            val outputs = generator(packageValue)
            val safeFiles = linkedMapOf<String, String>()
            outputs.files.forEach { (fileName, content) ->
                val safeFileName = sanitizeSaveFileName(fileName)
                if (safeFileName in safeFiles) {
                    return "Kotlin save failed: duplicate output file name `$safeFileName` after sanitizing."
                }
                safeFiles[safeFileName] = content
            }
            safeFiles.forEach { (fileName, content) ->
                File.writeText(joinSavePath(basePath, fileName), content)
            }
            val names = safeFiles.keys.sorted()
            val summary = names.joinToString(", ")
            return "Saved Kotlin files to $basePath (${names.size}): $summary"
        }

        val dataYamlToSave = if (noDeps) noDepsYaml ?: return "No-deps output not available for this data." else dataYaml
        val dataJsonToSave = if (noDeps) noDepsJson ?: return "No-deps output not available for this data." else dataJson
        val dataProtoToSave = if (noDeps) noDepsProto ?: return "No-deps output not available for this data." else dataProto
        val metaYamlToSave = if (noDeps) noDepsYaml ?: return "No-deps output not available for this data." else metaYaml
        val metaJsonToSave = if (noDeps) noDepsJson ?: return "No-deps output not available for this data." else metaJson
        val metaProtoToSave = if (noDeps) noDepsProto ?: return "No-deps output not available for this data." else metaProto

        val dataPath = joinSavePath(basePath, "$safeKey.${format.extension}")
        when (format) {
            SaveFormat.YAML -> File.writeText(dataPath, dataYamlToSave)
            SaveFormat.JSON -> File.writeText(dataPath, dataJsonToSave)
            SaveFormat.PROTO -> File.writeBytes(dataPath, dataProtoToSave)
            SaveFormat.KOTLIN -> Unit
        }

        if (includeMeta) {
            val metaPath = joinSavePath(basePath, "$safeKey.meta.${format.extension}")
            when (format) {
                SaveFormat.YAML -> File.writeText(metaPath, metaYamlToSave)
                SaveFormat.JSON -> File.writeText(metaPath, metaJsonToSave)
                SaveFormat.PROTO -> File.writeBytes(metaPath, metaProtoToSave)
                SaveFormat.KOTLIN -> Unit
            }
            return "Saved to $dataPath and $metaPath"
        }

        return "Saved to $dataPath"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SaveContext) return false

        if (key != other.key) return false
        if (dataYaml != other.dataYaml) return false
        if (dataJson != other.dataJson) return false
        if (!dataProto.contentEquals(other.dataProto)) return false
        if (metaYaml != other.metaYaml) return false
        if (metaJson != other.metaJson) return false
        if (!metaProto.contentEquals(other.metaProto)) return false
        if (noDepsYaml != other.noDepsYaml) return false
        if (noDepsJson != other.noDepsJson) return false
        if (!noDepsProto.contentEqualsOrNull(other.noDepsProto)) return false
        if ((kotlinGenerator != null) != (other.kotlinGenerator != null)) return false
        if ((kotlinNoDepsGenerator != null) != (other.kotlinNoDepsGenerator != null)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = key.hashCode()
        result = 31 * result + dataYaml.hashCode()
        result = 31 * result + dataJson.hashCode()
        result = 31 * result + dataProto.contentHashCode()
        result = 31 * result + metaYaml.hashCode()
        result = 31 * result + metaJson.hashCode()
        result = 31 * result + metaProto.contentHashCode()
        result = 31 * result + (noDepsYaml?.hashCode() ?: 0)
        result = 31 * result + (noDepsJson?.hashCode() ?: 0)
        result = 31 * result + (noDepsProto?.contentHashCode() ?: 0)
        result = 31 * result + (kotlinGenerator != null).hashCode()
        result = 31 * result + (kotlinNoDepsGenerator != null).hashCode()
        return result
    }

    private fun ByteArray?.contentEqualsOrNull(other: ByteArray?): Boolean {
        if (this == null && other == null) return true
        if (this == null || other == null) return false
        return this.contentEquals(other)
    }
}

private const val MAX_SAVE_FILE_NAME_LENGTH = 120

private val windowsReservedSaveFileNames = setOf(
    "CON",
    "PRN",
    "AUX",
    "NUL",
    "COM1",
    "COM2",
    "COM3",
    "COM4",
    "COM5",
    "COM6",
    "COM7",
    "COM8",
    "COM9",
    "LPT1",
    "LPT2",
    "LPT3",
    "LPT4",
    "LPT5",
    "LPT6",
    "LPT7",
    "LPT8",
    "LPT9",
)

internal fun sanitizeSaveFileName(value: String): String {
    val normalized = value
        .trim()
        .replace(Regex("[^A-Za-z0-9._-]"), "_")
        .trim('.')
        .ifBlank { "data" }

    val reservedSafe = if (normalized.substringBefore('.').uppercase() in windowsReservedSaveFileNames) {
        "_$normalized"
    } else {
        normalized
    }

    return reservedSafe
        .take(MAX_SAVE_FILE_NAME_LENGTH)
        .trimEnd('.')
        .ifBlank { "data" }
}

internal fun joinSavePath(directory: String, fileName: String): String {
    val normalized = directory.trimEnd('/', '\\')
    return if (normalized.isEmpty()) fileName else "$normalized/$fileName"
}
