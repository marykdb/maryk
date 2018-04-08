package maryk.core.json.yaml

import maryk.core.bytes.Base64
import maryk.core.json.JsonToken
import maryk.core.json.TokenType
import maryk.core.json.ValueType
import maryk.core.properties.types.DateTime
import kotlin.math.pow

private val trueValues = arrayOf("True", "TRUE", "true", "y", "Y", "yes", "YES", "Yes", "on", "ON", "On")
private val falseValues = arrayOf("False", "FALSE", "false", "n", "N", "no", "NO", "No", "off", "OFF", "Off")
private val nullValues = arrayOf("~", "Null", "null", "NULL")
private val nanValues = arrayOf(".nan", ".NAN", ".Nan")
private val infinityRegEx = Regex("^([-+]?)(\\.inf|\\.Inf|\\.INF)$")
private val base2RegEx = Regex("^[-+]?0b([0-1_]+)$")
private val base8RegEx = Regex("^[-+]?0([0-7_]+)$")
private val base10RegEx = Regex("^[-+]?(0|[1-9][0-9_]*)$")
private val base16RegEx = Regex("^[-+]?0x([0-9a-fA-F_]+)$")
private val base60RegEx = Regex("^[-+]?([1-9][0-9_]*)(:([0-5]?[0-9]))+$")
private val floatRegEx = Regex("^[-+]?(\\.[0-9]+|[0-9]+(\\.[0-9]*)?)([eE][-+]?[0-9]+)?$")
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

internal typealias JsonTokenCreator = (value: String?, isPlainStringReader: Boolean, tag: TokenType?, extraIndentAtStart: Int) -> JsonToken

/**
 * Creates a FieldName based on [fieldName]
 * Will return an exception if [fieldName] was already present in [foundFieldNames]
 * Can create a MergeFieldName if token was << and was read by a PlainStringReader signalled by [isPlainStringReader] = true
 */
internal fun checkAndCreateFieldName(foundFieldNames: MutableList<String?>, fieldName: String?, isPlainStringReader: Boolean) =
    if(!foundFieldNames.contains(fieldName)) {
        foundFieldNames += fieldName
        if (isPlainStringReader && fieldName == "<<") {
            JsonToken.MergeFieldName
        } else {
            JsonToken.FieldName(fieldName)
        }
    } else {
        throw InvalidYamlContent("Duplicate field name $fieldName in flow map")
    }

/**
 * Creates a JsonToken.Value by reading [value] and [tag]
 * If from plain string with [isPlainStringReader] = true and [tag] = false it will try to determine ValueType from contents.
 */
internal fun createYamlValueToken(value: String?, tag: TokenType?, isPlainStringReader: Boolean): JsonToken.Value<Any?> {
    return tag?.let {
        if (value == null && it != ValueType.Null) {
            throw InvalidYamlContent("Cannot have a null value with explicit tag which is not !!null")
        }
        when (it) {
            !is ValueType<*> -> {
                throw InvalidYamlContent("Cannot use non value tag with value $value")
            }
            is ValueType.Bool -> when(value) {
                in trueValues -> JsonToken.Value(true, it)
                in falseValues -> JsonToken.Value(false, it)
                else -> throw InvalidYamlContent("Unknown !!bool value $value")
            }
            is ValueType.Null -> when(value) {
                null, in nullValues -> JsonToken.Value(null, it)
                else -> throw InvalidYamlContent("Unknown !!null value $value")
            }
            is ValueType.Float -> when(value) {
                in nanValues -> JsonToken.Value(Double.NaN, it)
                else -> {
                    findInfinity(value!!)?.let { return it }
                    findFloat(value)?.let { return it }
                    throw InvalidYamlContent("Expected float value")
                }
            }
            is ValueType.Int -> findInt(value!!)?.let { return it }
                    ?: throw InvalidYamlContent("Not an integer")
            is YamlValueType.Binary -> {
                JsonToken.Value(Base64.decode(value!!), it)
            }
            is YamlValueType.TimeStamp -> {
                findTimestamp(value!!)
            }
            else -> JsonToken.Value(value, it)
        }
    } ?: if (value == null) {
        JsonToken.Value(null, ValueType.Null)
    } else {
        if (isPlainStringReader) {
            return when (value) {
                in nullValues -> JsonToken.Value(null, ValueType.Null)
                in trueValues -> JsonToken.Value(true, ValueType.Bool)
                in falseValues -> JsonToken.Value(false, ValueType.Bool)
                in nanValues -> JsonToken.Value(Double.NaN, ValueType.Float)
                else -> {
                    findInfinity(value)?.let { return it }
                    findInt(value)?.let { return it }
                    findFloat(value)?.let { return it }
                    findTimestamp(value)?.let { return it }
                    JsonToken.Value(value, ValueType.String)
                }
            }
        }

        JsonToken.Value(value, ValueType.String)
    }
}

/** Tries to find infinity value in [value] and returns a Value with infinity if found */
private fun findInfinity(value: String): JsonToken.Value<Double>? {
    infinityRegEx.find(value)?.let {
        return if(value.startsWith("-")) {
            JsonToken.Value(Double.NEGATIVE_INFINITY, ValueType.Float)
        } else {
            JsonToken.Value(Double.POSITIVE_INFINITY, ValueType.Float)
        }
    }
    return null
}

/** Tries to find Integer value in [value] and returns a Value with a Long if found */
private fun findInt(value: String): JsonToken.Value<Long>? {
    val minus = if (value.startsWith('-')) {
        -1
    } else {
        1
    }
    base2RegEx.find(value)?.let {
        val result = it.groupValues[1].replace("_", "").toLong(2)

        return JsonToken.Value(
            result * minus,
            ValueType.Int
        )
    }
    base8RegEx.find(value)?.let {
        return JsonToken.Value(
            value.replace("_", "").toLong(8),
            ValueType.Int)
    }
    base10RegEx.find(value)?.let {
        return JsonToken.Value(
            value.replace("_", "").toLong(10),
            ValueType.Int
        )
    }
    base16RegEx.find(value)?.let {
        val result = it.groupValues[1].replace("_", "").toLong(16)
        return JsonToken.Value(
            result * minus,
            ValueType.Int
        )
    }
    base60RegEx.find(value)?.let {
        val segments = value.replace("_", "").split(':')
        var result = 0L
        val power = segments.size - 1
        segments.forEachIndexed { index, segment ->
            result += (segment.toInt() * 60.0.pow(power - index)).toLong()
        }
        return JsonToken.Value(result, ValueType.Int)
    }
    return null
}

/** Tries to find float value in [value] and returns a Double if found */
private fun findFloat(value: String): JsonToken.Value<Double>? {
    floatRegEx.find(value)?.let {
        return value.replace("_", "").toDoubleOrNull()?.let { double ->
            JsonToken.Value(
                double,
                ValueType.Float
            )
        }
    }
    return null
}

/** Tries to find timestamp in [value] and returns a DateTime if found */
private fun findTimestamp(value: String): JsonToken.Value<DateTime>? {
    timestampRegex.find(value)?.let {
        return if (it.groups[4] == null) {
            JsonToken.Value(
                DateTime(
                    it.groups[1]!!.value.toInt(),
                    it.groups[2]!!.value.toByte(),
                    it.groups[3]!!.value.toByte()
                ),
                YamlValueType.TimeStamp
            )
        } else {
            val dateTime = if (it.groups[11] == null || it.groups[11]!!.value == "Z" || it.groups[11]!!.value.isEmpty()) {
                DateTime(
                    it.groups[1]!!.value.toInt(),
                    it.groups[2]!!.value.toByte(),
                    it.groups[3]!!.value.toByte(),
                    it.groups[6]!!.value.toByte(),
                    it.groups[7]!!.value.toByte(),
                    it.groups[8]!!.value.toByte(),
                    it.groups[10]?.value?.let {
                        when {
                            it.length < 3 -> {
                                var longer = it
                                (1..3 - it.length).forEach {
                                    longer += "0"
                                }
                                longer
                            }
                            it.length == 3 -> it
                            else -> it.substring(0, 3)
                        }
                    }?.toShort() ?: 0
                )
            } else {
                DateTime.parse(value)
            }

            JsonToken.Value(dateTime, YamlValueType.TimeStamp)

        }
    }
    return null
}
