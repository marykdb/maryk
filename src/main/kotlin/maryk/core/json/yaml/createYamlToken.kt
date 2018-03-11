package maryk.core.json.yaml

import maryk.core.json.JsonToken
import maryk.core.json.TokenType
import maryk.core.json.ValueType
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

internal fun createYamlValueToken(value: String?, tag: TokenType?, isPlainStringReader: Boolean): JsonToken.Value<Any?> {
    return tag?.let {
        if (value == null && it != ValueType.Null) {
            throw InvalidYamlContent("Cannot have a null value with explicit tag which is not !!null")
        }
        when (it) {
            !is ValueType<*> -> throw InvalidYamlContent("Cannot use non value tag with value $value")
            is ValueType.Bool -> when(value) {
                in trueValues -> JsonToken.Value(true, ValueType.Bool)
                in falseValues -> JsonToken.Value(false, ValueType.Bool)
                else -> throw InvalidYamlContent("Unknown !!bool value $value")
            }
            is ValueType.Null -> when(value) {
                null, in nullValues -> JsonToken.Value(null, ValueType.Null)
                else -> throw InvalidYamlContent("Unknown !!null value $value")
            }
            is ValueType.Float -> when(value) {
                in nanValues -> JsonToken.Value(Double.NaN, ValueType.Float)
                else -> {
                    findInfinity(value!!)?.let { return it }
                    findFloat(value)?.let { return it }
                    throw InvalidYamlContent("Expected float value")
                }
            }
            is ValueType.Int -> findInt(value!!)?.let { return it }
                    ?: throw InvalidYamlContent("Not an integer")
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
                    JsonToken.Value(value, ValueType.String)
                }
            }
        }

        JsonToken.Value(value, ValueType.String)
    }
}

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


private fun findFloat(value: String): JsonToken.Value<Double>? {
    floatRegEx.find(value)?.let {
        value.replace("_", "").toDoubleOrNull()?.let { double ->
            return JsonToken.Value(
                double,
                ValueType.Float
            )
        }

    }
    return null
}
