package maryk

import maryk.core.models.AbstractObjectDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.values.ObjectValues
import maryk.core.yaml.MarykYamlReaders
import maryk.json.JsonReader
import maryk.json.JsonWriter
import maryk.test.shouldBe
import maryk.yaml.YamlWriter

fun <T : Any, P : ObjectPropertyDefinitions<T>, CXI : IsPropertyContext, CX : IsPropertyContext> checkJsonConversion(
    value: T,
    dataModel: AbstractObjectDataModel<T, P, CXI, CX>,
    context: (() -> CXI)? = null,
    checker: (T, T) -> Unit = { converted, original -> converted shouldBe original },
    resetContextBeforeRead: Boolean = false
): String {
    var output = ""

    val writer = JsonWriter(pretty = true) {
        output += it
    }

    var newContext = dataModel.transformContext(context?.invoke())

    dataModel.writeJson(value, writer, newContext)

    if (resetContextBeforeRead) {
        newContext = dataModel.transformContext(context?.invoke())
    }

    val chars = output.iterator()
    val reader = JsonReader { chars.nextChar() }
    val converted = dataModel.readJson(reader, newContext).toDataObject()

    checker(converted, value)

    return output
}

fun <T : Any, P : ObjectPropertyDefinitions<T>, CXI : IsPropertyContext, CX : IsPropertyContext> checkYamlConversion(
    value: T,
    dataModel: AbstractObjectDataModel<T, P, CXI, CX>,
    context: (() -> CXI)? = null,
    checker: (T, T) -> Unit = { converted, original -> converted shouldBe original },
    resetContextBeforeRead: Boolean = false
): String {
    var output = ""

    val writer = YamlWriter {
        output += it
    }

    var newContext = dataModel.transformContext(context?.invoke())

    dataModel.writeJson(value, writer, newContext)

    if (resetContextBeforeRead) {
        newContext = dataModel.transformContext(context?.invoke())
    }

    val chars = output.iterator()
    val reader = MarykYamlReaders {
        chars.nextChar().also {
            if (it == '\u0000') {
                throw Throwable("0 char encountered")
            }
        }
    }
    val converted = dataModel.readJson(reader, newContext).toDataObject()

    checker(converted, value)

    return output
}

fun <T : Any, P : ObjectPropertyDefinitions<T>, CXI : IsPropertyContext, CX : IsPropertyContext> checkYamlConversion(
    value: ObjectValues<T, P>,
    dataModel: AbstractObjectDataModel<T, P, CXI, CX>,
    context: (() -> CXI)? = null,
    checker: (ObjectValues<T, P>, ObjectValues<T, P>) -> Unit = { converted, original -> converted shouldBe original },
    resetContextBeforeRead: Boolean = false
): String {
    var output = ""

    val writer = YamlWriter {
        output += it
    }

    var newContext = dataModel.transformContext(context?.invoke())

    dataModel.writeJson(value, writer, newContext)

    if (resetContextBeforeRead) {
        newContext = dataModel.transformContext(context?.invoke())
    }

    val chars = output.iterator()
    val reader = MarykYamlReaders {
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
    checker: (ObjectValues<T, P>, ObjectValues<T, P>) -> Unit = { converted, original -> converted shouldBe original },
    resetContextBeforeRead: Boolean = false
): String {
    var output = ""

    val writer = JsonWriter {
        output += it
    }

    var newContext = dataModel.transformContext(context?.invoke())

    dataModel.writeJson(value, writer, newContext)

    if (resetContextBeforeRead) {
        newContext = dataModel.transformContext(context?.invoke())
    }

    val chars = output.iterator()
    val reader = JsonReader { chars.nextChar() }
    val converted = dataModel.readJson(reader, newContext)

    checker(converted, value)

    return output
}
