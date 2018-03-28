package maryk.core.json.yaml

import maryk.core.json.AbstractJsonLikeWriter
import maryk.core.json.IllegalJsonOperation
import maryk.core.json.JsonComplexType
import maryk.core.json.JsonType

/** A Yaml writer which writes to [writer] */
class YamlWriter(
    private val spacing: String = "  ",
    private val writer: (String) -> Unit
) : AbstractJsonLikeWriter() {
    private var prefix: String = ""
    private val toSanitizeRegex = Regex(".*[#:\n]+.*")
    private val arraySpacing: String = "-${spacing.removeSuffix(" ")}"

    override fun writeStartObject() {
        if (lastType == JsonType.FIELD_NAME) {
            writer("\n")
        }
        super.writeStartObject()
        if (typeStack.size > 1) {
            prefix += spacing
        }
    }

    override fun writeEndObject() {
        super.writeEndObject()
        prefix = prefix.removeSuffix(spacing)
    }

    override fun writeStartArray() {
        if (lastType == JsonType.FIELD_NAME) {
            writer("\n")
        } else if (lastType == JsonType.START_ARRAY) {
            writer("$prefix-\n")
            prefix += spacing
        } else if (lastType == JsonType.END_ARRAY) {
            prefix = prefix.removeSuffix(spacing)
            writer("$prefix-\n")
            prefix += spacing
        }

        super.writeStartArray()
    }

    override fun writeEndArray() {
        if (lastType == JsonType.END_ARRAY) {
            prefix = prefix.removeSuffix(spacing)
        }

        super.writeEndArray()
    }

    /** Writes the field [name] for an object */
    override fun writeFieldName(name: String) {
        val lastType = this.lastType
        super.writeFieldName(name)
        if (lastType == JsonType.START_OBJ
            && typeStack.size > 1
            && typeStack[typeStack.size - 2] == JsonComplexType.ARRAY
        ) {
            writer("${prefix.removeSuffix(spacing)}$arraySpacing$name:")
        } else {
            writer("$prefix$name:")
        }
    }

    /** Writes a string [value] including quotes */
    override fun writeString(value: String) = writeValue(value)

    /** Writes a [value] excluding quotes */
    override fun writeValue(value: String) = if (!typeStack.isEmpty()) {
        val valueToWrite = this.sanitizeValue(value)

        when(typeStack.last()) {
            JsonComplexType.OBJECT -> {
                if (lastType == JsonType.FIELD_NAME) {
                    writer(" ")
                }
                super.checkObjectOperation()
                writer("$valueToWrite\n")
            }
            JsonComplexType.ARRAY -> {
                super.checkArrayOperation()
                writer("$prefix$arraySpacing$valueToWrite\n")
            }
        }
    } else {
        throw IllegalJsonOperation("Cannot checkAndWrite a value outside array or object")
    }

    private fun sanitizeValue(value: String) = if(value.matches(toSanitizeRegex)) {
        "'$value'"
    } else {
        value
    }
}
