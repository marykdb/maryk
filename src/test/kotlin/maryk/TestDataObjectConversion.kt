package maryk

import maryk.core.json.JsonReader
import maryk.core.json.JsonWriter
import maryk.core.json.yaml.MarykYamlReader
import maryk.core.json.yaml.YamlWriter
import maryk.core.objects.AbstractDataModel
import maryk.core.properties.ByteCollector
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.protobuf.WriteCache
import maryk.test.shouldBe

fun <T: Any, CXI: IsPropertyContext, CX: IsPropertyContext> checkProtoBufConversion(
    value: T,
    dataModel: AbstractDataModel<T, PropertyDefinitions<T>, CXI, CX>,
    context: CXI? = null,
    checker: (T, T) -> Unit = { converted, original -> converted shouldBe original }
) {
    val newContext = dataModel.transformContext(context)

    val bc = ByteCollector()
    val cache = WriteCache()

    val byteLength = dataModel.calculateProtoBufLength(value, cache, newContext)
    bc.reserve(byteLength)
    dataModel.writeProtoBuf(value, cache, bc::write, newContext)

    val converted = dataModel.readProtoBufToObject(byteLength, bc::read, newContext)

    checker(converted, value)
}

fun <T: Any, CXI: IsPropertyContext, CX: IsPropertyContext> checkJsonConversion(
    value: T,
    dataModel: AbstractDataModel<T, PropertyDefinitions<T>, CXI, CX>,
    context: CXI? = null,
    checker: (T, T) -> Unit = { converted, original -> converted shouldBe original }
) {
    var output = ""

    val writer = JsonWriter {
        output += it
    }

    val newContext = dataModel.transformContext(context)

    dataModel.writeJson(value, writer, newContext)

    val chars = output.iterator()
    val reader = JsonReader { chars.nextChar() }
    val converted = dataModel.readJsonToObject(reader, newContext)

    checker(converted, value)
}

fun <T: Any, CXI: IsPropertyContext, CX: IsPropertyContext> checkYamlConversion(
    value: T,
    dataModel: AbstractDataModel<T, PropertyDefinitions<T>, CXI, CX>,
    context: CXI? = null,
    checker: (T, T) -> Unit = { converted, original -> converted shouldBe original }
) {
    var output = ""

    val writer = YamlWriter {
        output += it
    }

    val newContext = dataModel.transformContext(context)

    dataModel.writeJson(value, writer, newContext)

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
}
