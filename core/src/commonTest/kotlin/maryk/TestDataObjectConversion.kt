package maryk

import maryk.core.models.AbstractObjectDataModel
import maryk.core.properties.IsBaseModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.values.ObjectValues
import maryk.core.yaml.MarykYamlReader
import maryk.json.JsonReader
import maryk.json.JsonWriter
import maryk.yaml.YamlWriter
import kotlin.test.assertEquals

fun <T : Any, P : ObjectPropertyDefinitions<T>, CXI : IsPropertyContext, CX : IsPropertyContext> checkJsonConversion(
    value: T,
    dataModel: IsBaseModel<T, P, CXI, CX>,
    context: (() -> CXI)? = null,
    checker: (T, T) -> Unit = { converted, original -> assertEquals(original, converted) },
    resetContextBeforeRead: Boolean = false
): String {
    var newContext = dataModel.Model.transformContext(context?.invoke())

    val output = buildString {
        val writer = JsonWriter(pretty = true) {
            append(it)
        }

        dataModel.Model.writeJson(value, writer, newContext)
    }

    if (resetContextBeforeRead) {
        newContext = dataModel.Model.transformContext(context?.invoke())
    }

    val chars = output.iterator()
    val reader = JsonReader { chars.nextChar() }
    val converted = dataModel.Model.readJson(reader, newContext).toDataObject()

    checker(converted, value)

    return output
}

fun <T : Any, P : ObjectPropertyDefinitions<T>, CXI : IsPropertyContext, CX : IsPropertyContext> checkYamlConversion(
    value: T,
    dataModel: IsBaseModel<T, P, CXI, CX>,
    context: (() -> CXI)? = null,
    checker: (T, T) -> Unit = { converted, original -> assertEquals(original, converted) },
    resetContextBeforeRead: Boolean = false
): String {
    var newContext = dataModel.Model.transformContext(context?.invoke())

    val output = buildString {
        val writer = YamlWriter {
            append(it)
        }

        dataModel.Model.writeJson(value, writer, newContext)
    }

    if (resetContextBeforeRead) {
        newContext = dataModel.Model.transformContext(context?.invoke())
    }

    val chars = output.iterator()
    val reader = MarykYamlReader {
        chars.nextChar().also {
            if (it == '\u0000') {
                throw Throwable("0 char encountered")
            }
        }
    }
    val converted = dataModel.Model.readJson(reader, newContext).toDataObject()

    checker(converted, value)

    return output
}

fun <T : Any, P : ObjectPropertyDefinitions<T>, CXI : IsPropertyContext, CX : IsPropertyContext> checkYamlConversion(
    value: ObjectValues<T, P>,
    dataModel: AbstractObjectDataModel<T, P, CXI, CX>,
    context: (() -> CXI)? = null,
    checker: (ObjectValues<T, P>, ObjectValues<T, P>) -> Unit = { converted, original -> assertEquals(original, converted) },
    resetContextBeforeRead: Boolean = false
): String {
    var newContext = dataModel.transformContext(context?.invoke())

    val output = buildString {
        val writer = YamlWriter {
            append(it)
        }

        dataModel.writeJson(value, writer, newContext)
    }

    if (resetContextBeforeRead) {
        newContext = dataModel.transformContext(context?.invoke())
    }

    val chars = output.iterator()
    val reader = MarykYamlReader {
        chars.nextChar().also {
            if (it == '\u0000') {
                throw Throwable("0 char encountered")
            }
        }
    }
    val converted = dataModel.readJson(reader, newContext)

    checker(converted, value)

    return output
}


fun <T : Any, P : ObjectPropertyDefinitions<T>, CXI : IsPropertyContext, CX : IsPropertyContext> checkJsonConversion(
    value: ObjectValues<T, P>,
    dataModel: AbstractObjectDataModel<T, P, CXI, CX>,
    context: (() -> CXI)? = null,
    checker: (ObjectValues<T, P>, ObjectValues<T, P>) -> Unit = { converted, original -> assertEquals(original, converted) },
    resetContextBeforeRead: Boolean = false
): String {
    var newContext = dataModel.transformContext(context?.invoke())

    val output = buildString {
        val writer = JsonWriter {
            append(it)
        }

        dataModel.writeJson(value, writer, newContext)
    }

    if (resetContextBeforeRead) {
        newContext = dataModel.transformContext(context?.invoke())
    }

    val chars = output.iterator()
    val reader = JsonReader { chars.nextChar() }
    val converted = dataModel.readJson(reader, newContext)

    checker(converted, value)

    return output
}
