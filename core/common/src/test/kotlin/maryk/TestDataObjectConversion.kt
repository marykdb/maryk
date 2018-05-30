package maryk

import maryk.core.objects.AbstractDataModel
import maryk.core.properties.ByteCollector
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.protobuf.WriteCache
import maryk.core.yaml.MarykYamlReader
import maryk.json.JsonReader
import maryk.json.JsonWriter
import maryk.test.shouldBe
import maryk.yaml.YamlWriter

fun <T: Any, CXI: IsPropertyContext, CX: IsPropertyContext> checkProtoBufConversion(
    value: T,
    dataModel: AbstractDataModel<T, PropertyDefinitions<T>, CXI, CX>,
    context: (() -> CXI)? = null,
    checker: (T, T) -> Unit = { converted, original -> converted shouldBe original },
    resetContextBeforeRead: Boolean = false
) {
    var newContext = dataModel.transformContext(context?.invoke())

    val bc = ByteCollector()
    val cache = WriteCache()

    val byteLength = dataModel.calculateProtoBufLength(value, cache, newContext)
    bc.reserve(byteLength)
    dataModel.writeProtoBuf(value, cache, bc::write, newContext)

    if (resetContextBeforeRead) {
        newContext = dataModel.transformContext(context?.invoke())
    }

    val converted = dataModel.readProtoBufToObject(byteLength, bc::read, newContext)

    checker(converted, value)
}

fun <T: Any, CXI: IsPropertyContext, CX: IsPropertyContext> checkJsonConversion(
    value: T,
    dataModel: AbstractDataModel<T, PropertyDefinitions<T>, CXI, CX>,
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
    val converted = dataModel.readJsonToObject(reader, newContext)

    checker(converted, value)

    return output
}

fun <T: Any, CXI: IsPropertyContext, CX: IsPropertyContext> checkYamlConversion(
    value: T,
    dataModel: AbstractDataModel<T, PropertyDefinitions<T>, CXI, CX>,
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
    val reader = MarykYamlReader {
        chars.nextChar().also {
            if (it == '\u0000') {
                throw Throwable("0 char encountered")
            }
        }
    }
    val converted = dataModel.readJsonToObject(reader, newContext)

    checker(converted, value)

    return output
}
