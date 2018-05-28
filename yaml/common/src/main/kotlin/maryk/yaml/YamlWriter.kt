package maryk.yaml

import maryk.json.AbstractJsonLikeWriter
import maryk.json.IllegalJsonOperation
import maryk.json.JsonEmbedType
import maryk.json.JsonType

/** A Yaml writer which writes to [writer] */
class YamlWriter(
    private val writer: (String) -> Unit
) : AbstractJsonLikeWriter() {
    private val spacing: String = "  "
    private val arraySpacing: String = "- "
    private val toSanitizeRegex = Regex(".*[#:\n]+.*")

    private var prefix: String = ""
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
            if (this.lastType == JsonType.FIELD_NAME
                || this.lastType == JsonType.TAG
                || this.lastType == JsonType.COMPLEX_FIELD_NAME_END
            ) {
                writer(" ")
            }
            writer("{")

            super.writeStartObject(isCompact)

            this.compactStartedAtLevel = this.typeStack.size
        } else {
            val prefixWasWrittenBefore = this.prefixWasWritten
            if (lastType == JsonType.FIELD_NAME || lastType == JsonType.TAG) {
                writer("\n")
                this.prefixWasWritten = false
            } else if (lastType == JsonType.COMPLEX_FIELD_NAME_END) {
                writer(" ")
            }

            val lastEmbedType= this.typeStack.lastOrNull()

            // If starting object within array then add array field
            if (lastEmbedType != null && lastEmbedType is JsonEmbedType.Array && !prefixWasWrittenBefore) {
                writer("$prefixToWrite$arraySpacing")
                this.prefixWasWritten = true
            }

            super.writeStartObject(isCompact)

            if (lastEmbedType != null && lastEmbedType != JsonEmbedType.ComplexField) {
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
                this.prefixWasWritten = false
            }
        } else {
            super.writeEndObject()
            if (!this.typeStack.isEmpty() && this.typeStack.last() !== JsonEmbedType.ComplexField) {
                prefix = prefix.removeSuffix(spacing)
            }
        }
    }

    override fun writeStartArray(isCompact: Boolean) {
        if (!this.lastIsCompact) {
            when (lastType) {
                JsonType.TAG -> {
                    if (!isCompact) {
                        writer("\n")
                        if (typeStack.last() is JsonEmbedType.Array) {
                            this.prefix += "  "
                        }
                        this.prefixWasWritten = false
                    } else {
                        writer(" ")
                    }
                }
                JsonType.FIELD_NAME, JsonType.COMPLEX_FIELD_NAME_END -> {
                    if (!isCompact) {
                        writer("\n")
                        this.prefixWasWritten = false
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
                this.prefixWasWritten = false
            }
        } else {
            super.writeEndArray()
            val lastType = if(typeStack.isEmpty()) null else typeStack.last()

            if (lastType == null || (lastType !is JsonEmbedType.Object && lastType !is JsonEmbedType.ComplexField)) {
                prefix = prefix.removeSuffix(spacing)
            }
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
            writer("$prefixToWrite$name:")
        }
        super.writeFieldName(name)
    }

    /** Writes a string [value] including quotes */
    override fun writeString(value: String) = writeValue(value)

    /** Writes a [value] excluding quotes */
    override fun writeValue(value: String) = if (!typeStack.isEmpty()) {
        val valueToWrite = this.sanitizeValue(value)
        val lastTypeBeforeOperation = this.lastType

        if (lastTypeBeforeOperation == JsonType.TAG || lastTypeBeforeOperation == JsonType.COMPLEX_FIELD_NAME_END) {
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
                    writer("$valueToWrite\n")
                    this.prefixWasWritten = false
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
                    this.prefixWasWritten = false
                }
            }
            is JsonEmbedType.ComplexField -> {
                throw IllegalJsonOperation("Complex fields cannot contain values directly, start an array or object before adding them")
            }
        }
    } else {
        if (this.lastType == JsonType.TAG) {
            writer(" ")
        }
        writer(value)
    }

    /** Writes a [tag] to YAML output */
    fun writeTag(tag: String) {
        if (this.lastType == JsonType.FIELD_NAME || this.lastType == JsonType.COMPLEX_FIELD_NAME_END) {
            writer(" ")
        }

        val lastTypeBeforeCheck = this.lastType

        checkTypeIsAllowed(
            JsonType.TAG,
            arrayOf(
                JsonType.START,
                JsonType.FIELD_NAME,
                JsonType.ARRAY_VALUE,
                JsonType.START_ARRAY,
                JsonType.END_ARRAY,
                JsonType.END_OBJ,
                JsonType.COMPLEX_FIELD_NAME_START,
                JsonType.COMPLEX_FIELD_NAME_END
            )
        )

        if (!this.lastIsCompact) {
            if (this.typeStack.lastOrNull() is JsonEmbedType.Array) {
                writer("$prefixToWrite$arraySpacing$tag")
                this.prefixWasWritten = true
            } else {
                writer(tag)
            }
        } else {
            if (!this.typeStack.isEmpty()
                && lastTypeBeforeCheck != JsonType.START_ARRAY
                && this.typeStack.last() is JsonEmbedType.Array
            ) {
                writer(", $tag")
            } else {
                writer(tag)
            }
        }
    }

    fun writeStartComplexField() {
        checkTypeIsAllowed(
            JsonType.COMPLEX_FIELD_NAME_START,
            arrayOf(JsonType.START_OBJ, JsonType.START_ARRAY, JsonType.OBJ_VALUE, JsonType.END_OBJ, JsonType.END_ARRAY)
        )

        writer("$prefixToWrite? ")
        prefixWasWritten = true

        typeStack.add(JsonEmbedType.ComplexField)

        prefix += spacing
    }

    fun writeEndComplexField() {
        checkTypeIsAllowed(
            JsonType.COMPLEX_FIELD_NAME_END,
            arrayOf(JsonType.END_OBJ, JsonType.END_ARRAY, JsonType.OBJ_VALUE)
        )

        prefix = prefix.removeSuffix(spacing)

        if(typeStack.isEmpty() || typeStack.last() !== JsonEmbedType.ComplexField) {
            throw IllegalJsonOperation("There is no complex field to close")
        }
        typeStack.removeAt(typeStack.lastIndex)

        writer("$prefixToWrite:")
        this.prefixWasWritten = true
    }

    /** If value contains yaml incompatible values it will be surrounded by quotes */
    private fun sanitizeValue(value: String) =
        if(value.matches(toSanitizeRegex)) {
            "'${value.replace("'", "''")}'"
        } else {
            value
        }
}
