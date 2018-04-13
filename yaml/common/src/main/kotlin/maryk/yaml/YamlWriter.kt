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
            if (this.lastType == JsonType.FIELD_NAME || this.lastType == JsonType.TAG) {
                writer(" ")
            }
            writer("{")

            super.writeStartObject(isCompact)

            this.compactStartedAtLevel = this.typeStack.size
        } else {
            if (lastType == JsonType.FIELD_NAME || lastType == JsonType.TAG) {
                writer("\n")
            }

            // If starting object within array then add array field
            if (this.typeStack.last() is JsonEmbedType.Array) {
                writer("$prefixToWrite$arraySpacing")
                this.prefixWasWritten = true
            }

            val lastEmbedType= this.typeStack.last()

            super.writeStartObject(isCompact)

            if (typeStack.size > 1 && lastEmbedType !== JsonEmbedType.ComplexField) {
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
            if (this.typeStack.last() !== JsonEmbedType.ComplexField) {
                prefix = prefix.removeSuffix(spacing)
            }
        }
    }

    override fun writeStartArray(isCompact: Boolean) {
        if (!this.lastIsCompact) {
            when (lastType) {
                JsonType.FIELD_NAME, JsonType.TAG -> {
                    if (!isCompact) {
                        writer("\n")
                    } else {
                        writer(" ")
                    }
                }
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
            // If starting object within array then add array field
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
        val lastTypeBeforeOperation = this.lastType

        if (lastTypeBeforeOperation == JsonType.TAG) {
            writer(" ")
        }

        when(typeStack.last()) {
            is JsonEmbedType.Object -> {
                super.checkObjectValueAllowed()
                if (lastTypeBeforeOperation == JsonType.FIELD_NAME) {
                    writer(" ")
                }

                if (this.lastIsCompact) {
                    writer(valueToWrite)
                } else {
                    this.prefixWasWritten = false
                    writer("$valueToWrite\n")
                }
            }
            is JsonEmbedType.Array -> {
                super.checkArrayValueAllowed()
                if (this.lastIsCompact) {
                    if (lastTypeBeforeOperation == JsonType.ARRAY_VALUE) {
                        writer(", ")
                    }
                    writer(valueToWrite)
                } else {
                    if (lastTypeBeforeOperation == JsonType.TAG) {
                        writer("$valueToWrite\n")
                    } else {
                        writer("$prefixToWrite$arraySpacing$valueToWrite\n")
                    }
                }
            }
            is JsonEmbedType.ComplexField -> {
                throw IllegalJsonOperation("Complex fields cannot contain values directly, start an array or object before adding them")
            }
        }
    } else {
        throw IllegalJsonOperation("Cannot checkAndWrite a value outside array or object")
    }

    /** Writes a [tag] to YAML output */
    fun writeTag(tag: String) {
        if (this.lastType == JsonType.FIELD_NAME) {
            writer(" ")
        }

        val lastTypeBeforeCheck = this.lastType

        checkTagAllowed()

        if (!this.lastIsCompact && (lastTypeBeforeCheck == JsonType.START_ARRAY || lastTypeBeforeCheck == JsonType.ARRAY_VALUE)) {
            writer("$prefixToWrite$arraySpacing$tag")
        } else {
            writer(tag)
        }
    }

    fun writeStartComplexField() {
        writer("$prefixToWrite? ")
        lastType = JsonType.COMPLEX_FIELD_NAME_START
        prefixWasWritten = true

        typeStack.add(JsonEmbedType.ComplexField)

        prefix += spacing
    }

    fun writeEndComplexField() {
        lastType = JsonType.COMPLEX_FIELD_NAME_END

        prefix = prefix.removeSuffix(spacing)

        if(typeStack.isEmpty() || typeStack.last() !== JsonEmbedType.ComplexField) {
            throw IllegalJsonOperation("There is no complex field to close")
        }
        typeStack.removeAt(typeStack.lastIndex)

        writer("$prefixToWrite: ")
        this.prefixWasWritten = true
    }

    protected fun checkTagAllowed() {
        checkTypeIsAllowed(
            JsonType.TAG,
            arrayOf(
                JsonType.START,
                JsonType.FIELD_NAME,
                JsonType.ARRAY_VALUE,
                JsonType.START_ARRAY,
                JsonType.COMPLEX_FIELD_NAME_START,
                JsonType.COMPLEX_FIELD_NAME_END
            )
        )
    }

    protected fun checkComplexFieldNameStartAllowed() {
        checkTypeIsAllowed(
            JsonType.TAG,
            arrayOf(JsonType.FIELD_NAME, JsonType.ARRAY_VALUE, JsonType.START_ARRAY, JsonType.COMPLEX_FIELD_NAME_START)
        )
    }

    protected fun checkComplexFieldNameEndAllowed() {
        checkTypeIsAllowed(
            JsonType.TAG,
            arrayOf(JsonType.END_OBJ, JsonType.END_ARRAY, JsonType.OBJ_VALUE)
        )
    }

    /** If value contains yaml incompatible values it will be surrounded by quotes */
    private fun sanitizeValue(value: String) =
        if(value.matches(toSanitizeRegex)) {
            "'${value.replace("'", "''")}'"
        } else {
            value
        }
}
