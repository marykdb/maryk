package maryk.yaml

import maryk.json.AbstractJsonLikeWriter
import maryk.json.IllegalJsonOperation
import maryk.json.JsonEmbedType
import maryk.json.JsonEmbedType.ComplexField
import maryk.json.JsonEmbedType.Object
import maryk.json.JsonType
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
    private val toSanitizeRegex = Regex("[\\[{]+.*|.*[#:\n]+.*")
    private val flowStructureRegex = Regex(".*[\\[\\]{},].*")
    private val trueValues = setOf("True", "TRUE", "true")
    private val falseValues = setOf("False", "FALSE", "false")
    private val nullValues = setOf("~", "Null", "null", "NULL")
    private val nanValues = setOf(".nan", ".NAN", ".Nan")
    private val infinityRegEx = Regex("^([-+]?)(\\.inf|\\.Inf|\\.INF)$")
    private val base2RegEx = Regex("^[-+]?0b([0-1_]+)$")
    private val base8RegEx = Regex("^[-+]?0([0-7_]+)$")
    private val base10RegEx = Regex("^[-+]?(0|[1-9][0-9_]*)$")
    private val base16RegEx = Regex("^[-+]?0x([0-9a-fA-F_]+)$")
    private val base60RegEx = Regex("^[-+]?([1-9][0-9_]*)(:([0-5]?[0-9]))+$")
    private val floatRegEx = Regex("^[-+]?(\\.[0-9]+|[0-9]+(\\.[0-9]*)?)([eE][-+]?[0-9]+)?$")
    private val indicatorStartChars = setOf('*', '&', '!', '|', '>', '%', '@', '`', '\'', '"')
    private val timestampRegex = Regex(
        "^([0-9][0-9][0-9][0-9])" + // year
            "-([0-9][0-9]?)" + // month
            "-([0-9][0-9]?)" + // day
            "(([Tt]|[ \\t]+)([0-9][0-9]?)" + // hour
            ":([0-9][0-9])" + // minute
            ":([0-9][0-9])" + // second
            "(\\.([0-9]*))?" + // fraction
            "(([ \\t]*)Z|([-+][0-9][0-9])?(:([0-9][0-9]))?)?)?$"  // time zone
    )

    private var prefix: String = ""
    private var prefixWasWritten = false
    private var compactStartedAtLevel: Int? = null
    private var pendingObjectStart = false
    private var pendingObjectStartHasTag = false

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

            return this.typeStack.lastOrNull()?.isSimple == true
        }

    private fun writePendingObjectStart() {
        if (this.pendingObjectStart) {
            writer("\n")
            this.prefixWasWritten = false
            this.pendingObjectStart = false
            this.pendingObjectStartHasTag = false
        }
    }

    override fun writeStartObject(isCompact: Boolean) {
        writePendingObjectStart()
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
                this.prefixWasWritten = false
                this.pendingObjectStart = true
                this.pendingObjectStartHasTag = lastType == TAG
            } else if (lastType == COMPLEX_FIELD_NAME_END) {
                writer(" ")
            }

            val lastEmbedType = this.typeStack.lastOrNull()

            // If starting object within array then add array field
            if (lastEmbedType is JsonEmbedType.Array && (!prefixWasWrittenBefore || lastType == START_ARRAY)) {
                writer("$prefixToWrite$arraySpacing")
                this.prefixWasWritten = true
            }

            super.writeStartObject(isCompact)

            if (lastEmbedType != null && lastEmbedType != ComplexField) {
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
            if (this.lastType == START_OBJ) {
                if (this.pendingObjectStart) {
                    if (!this.pendingObjectStartHasTag) {
                        writer(" {}")
                    }
                } else {
                    writer("$prefixToWrite{}")
                }
                this.pendingObjectStart = false
                this.pendingObjectStartHasTag = false
                if (this.typeStack.size > 1) {
                    writer("\n")
                    this.prefixWasWritten = false
                }
            }
            super.writeEndObject()
            if (this.typeStack.isNotEmpty() && this.typeStack.last() !== ComplexField) {
                prefix = prefix.removeSuffix(spacing)
            }
        }
    }

    override fun writeStartArray(isCompact: Boolean) {
        writePendingObjectStart()
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

            if (lastType == null || (lastType !is Object && lastType !is ComplexField)) {
                prefix = prefix.removeSuffix(spacing)
            }
        }
    }

    /** Writes the field [name] for an object */
    override fun writeFieldName(name: String) {
        writePendingObjectStart()
        val isCompact = this.lastIsCompact
        val renderedName = sanitizeFieldName(name, quoteFlowDelimiters = isCompact)
        val lastType = this.lastType

        if (isCompact) {
            if (lastType != START_OBJ) {
                writer(", ")
            }
            writer("$renderedName:")
        } else {
            writer("$prefixToWrite$renderedName:")
        }
        super.writeFieldName(name)
    }

    /** Writes a string [value] including quotes */
    override fun writeString(value: String) = writeValueInternal(value, quoteStrings = true)

    /** Writes a [value] excluding quotes */
    override fun writeValue(value: String) = writeValueInternal(value, quoteStrings = false)

    private fun writeValueInternal(value: String, quoteStrings: Boolean) {
        writePendingObjectStart()
        val renderedValue = if (quoteStrings) {
            sanitizeValue(value, quoteFlowDelimiters = this.lastIsCompact)
        } else {
            value
        }
        if (typeStack.isNotEmpty()) {
            val lastTypeBeforeOperation = this.lastType

            if ((lastTypeBeforeOperation == TAG && value != "") || lastTypeBeforeOperation == COMPLEX_FIELD_NAME_END) {
                writer(" ")
            }

            when (typeStack.last()) {
                is Object -> {
                    super.checkObjectValueAllowed()
                    if (lastTypeBeforeOperation == FIELD_NAME) {
                        writer(" ")
                    }

                    if (this.lastIsCompact) {
                        writer(renderedValue)
                    } else {
                        if (value.contains('\n') && !requiresEscapedScalar(value)) {
                            writeMultilineValue(value, lastTypeBeforeOperation)
                        } else {
                            writer("$renderedValue\n")
                            this.prefixWasWritten = false
                        }
                        return
                    }
                    this.prefixWasWritten = false
                }
                is JsonEmbedType.Array -> {
                    super.checkArrayValueAllowed()
                    if (this.lastIsCompact) {
                        if (lastTypeBeforeOperation == ARRAY_VALUE) {
                            writer(", ")
                        }
                        writer(renderedValue)
                    } else {
                        if (value.contains('\n') && !requiresEscapedScalar(value)) {
                            writeMultilineValue(value, lastTypeBeforeOperation)
                        } else {
                            if (lastTypeBeforeOperation == TAG) {
                                writer("$renderedValue\n")
                            } else {
                                writer("$prefixToWrite$arraySpacing$renderedValue\n")
                            }
                            this.prefixWasWritten = false
                        }
                        return
                    }
                }
                is ComplexField -> {
                    throw IllegalJsonOperation("Complex fields cannot contain values directly, start an array or object before adding them")
                }
            }
        } else {
            if (this.lastType == TAG) {
                writer(" ")
            }
            writer(renderedValue)
        }
    }

    private fun writeMultilineValue(value: String, lastTypeBeforeOperation: JsonType) {
        val trailingNewlines = value.takeLastWhile { it == '\n' }.length
        val lines = value.split("\n").dropLast(if (trailingNewlines > 1) 1 else 0)
        val chompIndicator = when (trailingNewlines) {
            0 -> "-"
            1 -> ""
            else -> "+"
        }
        val indentationIndicator = if (value.all { it.isWhitespace() }) "2" else ""
        when (typeStack.last()) {
            is Object -> {
                writer("|$chompIndicator$indentationIndicator")
                writer("\n")
                lines.forEach { line ->
                    writer("$prefix$spacing$line\n")
                }
                this.prefixWasWritten = false
            }
            is JsonEmbedType.Array -> {
                if (lastTypeBeforeOperation == TAG) {
                    writer("|$chompIndicator$indentationIndicator\n")
                } else {
                    writer("$prefixToWrite$arraySpacing|$chompIndicator$indentationIndicator\n")
                }
                lines.forEach { line ->
                    writer("$prefix$spacing$line\n")
                }
                this.prefixWasWritten = false
            }
            is ComplexField -> {
                throw IllegalJsonOperation("Complex fields cannot contain values directly, start an array or object before adding them")
            }
        }
    }

    /** Writes a [tag] to YAML output */
    fun writeTag(tag: String) {
        writePendingObjectStart()
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
            if (this.typeStack.isNotEmpty()
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
        writePendingObjectStart()
        checkTypeIsAllowed(
            COMPLEX_FIELD_NAME_START,
            arrayOf(START_OBJ, START_ARRAY, OBJ_VALUE, END_OBJ, END_ARRAY)
        )

        writer("$prefixToWrite? ")
        prefixWasWritten = true

        typeStack.add(ComplexField)

        prefix += spacing
    }

    fun writeEndComplexField() {
        writePendingObjectStart()
        checkTypeIsAllowed(
            COMPLEX_FIELD_NAME_END,
            arrayOf(END_OBJ, END_ARRAY, OBJ_VALUE)
        )

        prefix = prefix.removeSuffix(spacing)

        if (typeStack.isEmpty() || typeStack.last() !== ComplexField) {
            throw IllegalJsonOperation("There is no complex field to close")
        }
        typeStack.removeAt(typeStack.lastIndex)

        writer("$prefixToWrite:")
        this.prefixWasWritten = true
    }

    /** If value contains yaml incompatible values it will be surrounded by quotes */
    private fun sanitizeValue(value: String, quoteFlowDelimiters: Boolean) =
        renderScalar(value, shouldQuote(value, quoteFlowDelimiters))

    private fun sanitizeFieldName(value: String, quoteFlowDelimiters: Boolean) =
        renderScalar(value, shouldQuoteFieldName(value, quoteFlowDelimiters))

    private fun renderScalar(value: String, quote: Boolean): String = when {
        requiresEscapedScalar(value) -> buildString {
            append('"')
            value.forEach { character ->
                append(
                    when (character) {
                        '\u0000' -> "\\0"
                        '\u0007' -> "\\a"
                        '\b' -> "\\b"
                        '\t' -> "\\t"
                        '\n' -> "\\n"
                        '\u000B' -> "\\v"
                        '\u000C' -> "\\f"
                        '\r' -> "\\r"
                        '\u001B' -> "\\e"
                        '"' -> "\\\""
                        '\\' -> "\\\\"
                        else -> if (character.code in 0x7F..0x9F) {
                            "\\x${character.code.toString(16).padStart(2, '0')}"
                        } else {
                            character.toString()
                        }
                    }
                )
            }
            append('"')
        }
        quote -> "'${value.replace("'", "''")}'"
        else -> value
    }

    private fun requiresEscapedScalar(value: String) = value.any {
        it.code in 0x00..0x08 || it.code in 0x0B..0x1F || it.code in 0x7F..0x9F
    }

    private fun shouldQuote(value: String, quoteFlowDelimiters: Boolean): Boolean {
        if (value.isEmpty() || value != value.trim()) return true
        if (value == "---" || value == "...") return true
        if (value.first() in indicatorStartChars) return true
        if (value == "?") return true
        if (value.startsWith("- ") || value.startsWith("? ") || value.startsWith(": ")) return true
        if (value.matches(toSanitizeRegex)) return true
        if (quoteFlowDelimiters && value.matches(flowStructureRegex)) return true
        if (value in nullValues || value in trueValues || value in falseValues || value in nanValues) return true
        if (infinityRegEx.matches(value)) return true
        if (base2RegEx.matches(value)
            || base8RegEx.matches(value)
            || base10RegEx.matches(value)
            || base16RegEx.matches(value)
            || base60RegEx.matches(value)
            || floatRegEx.matches(value)
            || timestampRegex.matches(value)
        ) {
            return true
        }
        return false
    }

    private fun shouldQuoteFieldName(value: String, quoteFlowDelimiters: Boolean): Boolean {
        if (value.isEmpty() || value != value.trim()) return true
        if (value == "---" || value == "...") return true
        if (value.first() in indicatorStartChars) return true
        if (value == "?") return true
        if (value.startsWith("- ") || value.startsWith("? ") || value.startsWith(": ")) return true
        if (value.contains('\n') || value.contains(": ") || value.contains(":\t") || value.contains(" #")) return true
        if (quoteFlowDelimiters && value.matches(flowStructureRegex)) return true
        return false
    }
}
