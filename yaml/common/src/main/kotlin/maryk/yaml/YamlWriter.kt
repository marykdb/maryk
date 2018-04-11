package maryk.yaml

import maryk.json.AbstractJsonLikeWriter
import maryk.json.IllegalJsonOperation
import maryk.json.JsonEmbedType
import maryk.json.JsonType

/** A Yaml writer which writes to [writer] */
class YamlWriter(
    private val spacing: String = "  ",
    private val writer: (String) -> Unit
) : AbstractJsonLikeWriter() {
    private var prefix: String = ""
    private val toSanitizeRegex = Regex(".*[#:\n]+.*")
    private val arraySpacing: String = "-${spacing.removeSuffix(" ")}"
    private var prefixWasWritten = false
    private var compactStartedAtLevel: Int? = null

    private val prefixToWrite: String
        get() = if (this.prefixWasWritten) {
            this.prefixWasWritten = false
            ""
        } else prefix

    private val lastIsCompact: Boolean get() {
        this.compactStartedAtLevel?.let {
            if (this.typeStack.size < it) {
                this.compactStartedAtLevel = null
            } else return true
        }

        return this.typeStack.lastOrNull()?.isSimple ?: false
    }

    override fun writeStartObject(isCompact: Boolean) {
        if (isCompact || this.lastIsCompact) {
            if (lastType == JsonType.FIELD_NAME) {
                writer(" ")
            }
            writer("{")

            super.writeStartObject(isCompact)

            this.compactStartedAtLevel = this.typeStack.size
        } else {
            if (lastType == JsonType.FIELD_NAME) {
                writer("\n")
            }

            super.writeStartObject(isCompact)

            if (typeStack.size > 1) {
                prefix += spacing
            }
        }
    }

    override fun writeEndObject() {
        if (this.lastIsCompact) {
            writer("}")
            super.writeEndObject()
            if (!this.lastIsCompact) {
                writer("\n")
            }
        } else {
            super.writeEndObject()
            prefix = prefix.removeSuffix(spacing)
        }
    }

    override fun writeStartArray(isCompact: Boolean) {
        if (!this.lastIsCompact) {
            when (lastType) {
                JsonType.FIELD_NAME -> writer("\n")
                JsonType.START_ARRAY -> {
                    writer("$prefixToWrite- ")
                    this.prefixWasWritten = true
                    prefix += spacing
                }
                JsonType.END_ARRAY -> {
                    prefix = prefix.removeSuffix(spacing)
                    writer("$prefixToWrite- ")
                    this.prefixWasWritten = true
                    prefix += spacing
                }
                else -> {}
            }
        } else if (lastType != JsonType.START_ARRAY && lastType != JsonType.FIELD_NAME) {
            writer(",")
        }

        if (isCompact || this.lastIsCompact) {
            writer("[")
            this.compactStartedAtLevel = this.typeStack.size + 1 // is written later so + 1
            this.prefixWasWritten = false
        }

        super.writeStartArray(isCompact)
    }

    override fun writeEndArray() {
        if (this.lastIsCompact) {
            writer("]")
            super.writeEndArray()
            if (!this.lastIsCompact) {
                writer("\n")
            }
        } else {
            if (lastType == JsonType.END_ARRAY) {
                prefix = prefix.removeSuffix(spacing)
            }
            super.writeEndArray()
        }
    }

    /** Writes the field [name] for an object */
    override fun writeFieldName(name: String) {
        val lastType = this.lastType

        if (this.lastIsCompact) {
            if (lastType != JsonType.START_OBJ) {
                writer(", ")
            }
            writer("$name:")
        } else {
            if (lastType == JsonType.START_OBJ
                && typeStack.size > 1
                && typeStack[typeStack.size - 2] is JsonEmbedType.Array
            ) {
                writer("${prefixToWrite.removeSuffix(spacing)}$arraySpacing$name:")
            } else {
                writer("$prefixToWrite$name:")
            }
        }
        super.writeFieldName(name)
    }

    /** Writes a string [value] including quotes */
    override fun writeString(value: String) = writeValue(value)

    /** Writes a [value] excluding quotes */
    override fun writeValue(value: String) = if (!typeStack.isEmpty()) {
        val valueToWrite = this.sanitizeValue(value)

        when(typeStack.last()) {
            is JsonEmbedType.Object -> {
                if (lastType == JsonType.FIELD_NAME) {
                    writer(" ")
                }
                super.checkObjectOperation()
                if (this.lastIsCompact) {
                    writer(valueToWrite)
                } else {
                    writer("$valueToWrite\n")
                }
            }
            is JsonEmbedType.Array -> {
                if (this.lastIsCompact) {
                    if (lastType == JsonType.ARRAY_VALUE) {
                        writer(", ")
                    }
                    writer(valueToWrite)
                } else {
                    writer("$prefixToWrite$arraySpacing$valueToWrite\n")
                }

                super.checkArrayOperation()
            }
        }
    } else {
        throw IllegalJsonOperation("Cannot checkAndWrite a value outside array or object")
    }

    /** If value contains yaml incompatible values it will be surrounded by quotes */
    private fun sanitizeValue(value: String) =
        if(value.matches(toSanitizeRegex)) {
            "'$value'"
        } else {
            value
        }
}
