package maryk.yaml

import maryk.json.AbstractJsonLikeWriter
import maryk.json.IllegalJsonOperation
import maryk.json.JsonEmbedType
import maryk.json.JsonType.ARRAY_VALUE
import maryk.json.JsonType.COMPLEX_FIELD_NAME_END
import maryk.json.JsonType.COMPLEX_FIELD_NAME_START
import maryk.json.JsonType.END_ARRAY
import maryk.json.JsonType.END_OBJ
import maryk.json.JsonType.FIELD_NAME
import maryk.json.JsonType.OBJ_VALUE
import maryk.json.JsonType.START
import maryk.json.JsonType.START_ARRAY
import maryk.json.JsonType.START_OBJ
import maryk.json.JsonType.TAG

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

    private val lastIsCompact: Boolean
        get() {
            this.compactStartedAtLevel?.let {
                if (this.typeStack.size < it) {
                    this.compactStartedAtLevel = null
                } else return true
            }

            return this.typeStack.lastOrNull()?.isSimple ?: false
        }

    override fun writeStartObject(isCompact: Boolean) {
        if (isCompact || this.lastIsCompact) {
            if (this.lastType == FIELD_NAME
                || this.lastType == TAG
                || this.lastType == COMPLEX_FIELD_NAME_END
            ) {
                writer(" ")
            } else if (this.lastType == END_OBJ) {
                val lastEmbedType = this.typeStack.lastOrNull()
                if (lastEmbedType is JsonEmbedType.Array) {
                    writer(", ")
                }
            }
            writer("{")

            super.writeStartObject(isCompact)

            this.compactStartedAtLevel = this.typeStack.size
        } else {
            val prefixWasWrittenBefore = this.prefixWasWritten
            if (lastType == FIELD_NAME || lastType == TAG) {
                writer("\n")
                this.prefixWasWritten = false
            } else if (lastType == COMPLEX_FIELD_NAME_END) {
                writer(" ")
            }

            val lastEmbedType = this.typeStack.lastOrNull()

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
                TAG -> {
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
                FIELD_NAME, COMPLEX_FIELD_NAME_END -> {
                    if (!isCompact) {
                        writer("\n")
                        this.prefixWasWritten = false
                    } else {
                        writer(" ")
                    }
                }
                START_ARRAY -> {
                    writer("$prefixToWrite- ")
                    this.prefixWasWritten = true
                    prefix += spacing
                }
                END_ARRAY -> {
                    prefix = prefix.removeSuffix(spacing)
                    writer("$prefixToWrite- ")
                    this.prefixWasWritten = true
                    prefix += spacing
                }
                else -> Unit
            }
        } else if (lastType != START_ARRAY && lastType != FIELD_NAME) {
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
            val lastType = if (typeStack.isEmpty()) null else typeStack.last()

            if (lastType == null || (lastType !is JsonEmbedType.Object && lastType !is JsonEmbedType.ComplexField)) {
                prefix = prefix.removeSuffix(spacing)
            }
        }
    }

    /** Writes the field [name] for an object */
    override fun writeFieldName(name: String) {
        val lastType = this.lastType

        if (this.lastIsCompact) {
            if (lastType != START_OBJ) {
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

        if ((lastTypeBeforeOperation == TAG && value != "") || lastTypeBeforeOperation == COMPLEX_FIELD_NAME_END) {
            writer(" ")
        }

        when (typeStack.last()) {
            is JsonEmbedType.Object -> {
                super.checkObjectValueAllowed()
                if (lastTypeBeforeOperation == FIELD_NAME) {
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
                    if (lastTypeBeforeOperation == ARRAY_VALUE) {
                        writer(", ")
                    }
                    writer(valueToWrite)
                } else {
                    if (lastTypeBeforeOperation == TAG) {
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
        if (this.lastType == TAG) {
            writer(" ")
        }
        writer(value)
    }

    /** Writes a [tag] to YAML output */
    fun writeTag(tag: String) {
        if (this.lastType == FIELD_NAME || this.lastType == COMPLEX_FIELD_NAME_END) {
            writer(" ")
        }

        val lastTypeBeforeCheck = this.lastType

        // If last type is TAG then write it away with an empty value for it
        if (lastType == TAG) {
            writeValue("")
        }

        checkTypeIsAllowed(
            TAG,
            arrayOf(
                START,
                FIELD_NAME,
                ARRAY_VALUE,
                START_ARRAY,
                END_ARRAY,
                END_OBJ,
                COMPLEX_FIELD_NAME_START,
                COMPLEX_FIELD_NAME_END
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
                && lastTypeBeforeCheck != START_ARRAY
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
            COMPLEX_FIELD_NAME_START,
            arrayOf(START_OBJ, START_ARRAY, OBJ_VALUE, END_OBJ, END_ARRAY)
        )

        writer("$prefixToWrite? ")
        prefixWasWritten = true

        typeStack.add(JsonEmbedType.ComplexField)

        prefix += spacing
    }

    fun writeEndComplexField() {
        checkTypeIsAllowed(
            COMPLEX_FIELD_NAME_END,
            arrayOf(END_OBJ, END_ARRAY, OBJ_VALUE)
        )

        prefix = prefix.removeSuffix(spacing)

        if (typeStack.isEmpty() || typeStack.last() !== JsonEmbedType.ComplexField) {
            throw IllegalJsonOperation("There is no complex field to close")
        }
        typeStack.removeAt(typeStack.lastIndex)

        writer("$prefixToWrite:")
        this.prefixWasWritten = true
    }

    /** If value contains yaml incompatible values it will be surrounded by quotes */
    private fun sanitizeValue(value: String) =
        if (value.matches(toSanitizeRegex)) {
            "'${value.replace("'", "''")}'"
        } else {
            value
        }
}
