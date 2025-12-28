package io.maryk.cli

import maryk.file.File

enum class SaveFormat(val extension: String) {
    YAML("yaml"),
    JSON("json"),
    PROTO("proto"),
}

data class SaveContext(
    val key: String,
    val dataYaml: String,
    val dataJson: String,
    val dataProto: ByteArray,
    val metaYaml: String,
    val metaJson: String,
    val metaProto: ByteArray,
) {
    fun save(
        directory: String,
        format: SaveFormat,
        includeMeta: Boolean,
    ): String {
        val basePath = directory.trimEnd('/', '\\')
        val dataPath = "$basePath/$key.${format.extension}"
        when (format) {
            SaveFormat.YAML -> File.writeText(dataPath, dataYaml)
            SaveFormat.JSON -> File.writeText(dataPath, dataJson)
            SaveFormat.PROTO -> File.writeBytes(dataPath, dataProto)
        }

        if (includeMeta) {
            val metaPath = "$basePath/$key.meta.${format.extension}"
            when (format) {
                SaveFormat.YAML -> File.writeText(metaPath, metaYaml)
                SaveFormat.JSON -> File.writeText(metaPath, metaJson)
                SaveFormat.PROTO -> File.writeBytes(metaPath, metaProto)
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
        return result
    }
}
