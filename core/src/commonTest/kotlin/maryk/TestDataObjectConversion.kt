package maryk

import maryk.core.models.IsBaseObjectDataModel
import maryk.core.models.IsObjectDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.values.ObjectValues
import maryk.core.yaml.MarykYamlReader
import maryk.json.JsonReader
import maryk.json.JsonWriter
import maryk.yaml.YamlWriter
import kotlin.test.assertEquals

fun <T : Any, P : IsObjectDataModel<T>, CXI : IsPropertyContext, CX : IsPropertyContext> checkJsonConversion(
    value: T,
    dataModel: IsBaseObjectDataModel<T, P, CXI, CX>,
    context: (() -> CXI)? = null,
    checker: (T, T) -> Unit = { converted, original -> assertEquals(original, converted) },
    resetContextBeforeRead: Boolean = false
): String {
    var newContext = dataModel.Serializer.transformContext(context?.invoke())

    val output = buildString {
        val writer = JsonWriter(pretty = true) {
            append(it)
        }

        dataModel.Serializer.writeObjectAsJson(value, writer, newContext)
    }

    if (resetContextBeforeRead) {
        newContext = dataModel.Serializer.transformContext(context?.invoke())
    }

    val chars = output.iterator()
    val reader = JsonReader { chars.nextChar() }
    val converted = dataModel.Serializer.readJson(reader, newContext).toDataObject()

    checker(converted, value)

    return output
}

fun <T : Any, P : IsObjectDataModel<T>, CXI : IsPropertyContext, CX : IsPropertyContext> checkYamlConversion(
    value: T,
    dataModel: IsBaseObjectDataModel<T, P, CXI, CX>,
    context: (() -> CXI)? = null,
    checker: (T, T) -> Unit = { converted, original -> assertEquals(original, converted) },
    resetContextBeforeRead: Boolean = false
): String {
    var newContext = dataModel.Serializer.transformContext(context?.invoke())

    val output = buildString {
        val writer = YamlWriter {
            append(it)
        }

        dataModel.Serializer.writeObjectAsJson(value, writer, newContext)
    }

    if (resetContextBeforeRead) {
        newContext = dataModel.Serializer.transformContext(context?.invoke())
    }

    val chars = output.iterator()
    val reader = MarykYamlReader {
        chars.nextChar().also {
            if (it == '\u0000') {
                throw Throwable("0 char encountered")
            }
        }
    }
    val converted = dataModel.Serializer.readJson(reader, newContext).toDataObject()

    checker(converted, value)

    return output
}

fun <T : Any, P : IsObjectDataModel<T>, CXI : IsPropertyContext, CX : IsPropertyContext> checkYamlConversion(
    value: ObjectValues<T, P>,
    dataModel: IsBaseObjectDataModel<T, P, CXI, CX>,
    context: (() -> CXI)? = null,
    checker: (ObjectValues<T, P>, ObjectValues<T, P>) -> Unit = { converted, original -> assertEquals(original, converted) },
    resetContextBeforeRead: Boolean = false
): String {
    var newContext = dataModel.Serializer.transformContext(context?.invoke())

    val output = buildString {
        val writer = YamlWriter {
            append(it)
        }

        dataModel.Serializer.writeJson(value, writer, newContext)
    }

    if (resetContextBeforeRead) {
        newContext = dataModel.Serializer.transformContext(context?.invoke())
    }

    val chars = output.iterator()
    val reader = MarykYamlReader {
        chars.nextChar().also {
            if (it == '\u0000') {
                throw Throwable("0 char encountered")
            }
        }
    }
    val converted = dataModel.Serializer.readJson(reader, newContext)

    checker(converted, value)

    return output
}


fun <T : Any, P : IsObjectDataModel<T>, CXI : IsPropertyContext, CX : IsPropertyContext> checkJsonConversion(
    value: ObjectValues<T, P>,
    dataModel: IsBaseObjectDataModel<T, P, CXI, CX>,
    context: (() -> CXI)? = null,
    checker: (ObjectValues<T, P>, ObjectValues<T, P>) -> Unit = { converted, original -> assertEquals(original, converted) },
    resetContextBeforeRead: Boolean = false
): String {
    var newContext = dataModel.Serializer.transformContext(context?.invoke())

    val output = buildString {
        val writer = JsonWriter {
            append(it)
        }

        dataModel.Serializer.writeJson(value, writer, newContext)
    }

    if (resetContextBeforeRead) {
        newContext = dataModel.Serializer.transformContext(context?.invoke())
    }

    val chars = output.iterator()
    val reader = JsonReader { chars.nextChar() }
    val converted = dataModel.Serializer.readJson(reader, newContext)

    checker(converted, value)

    return output
}
