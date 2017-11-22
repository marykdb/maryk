package maryk

import maryk.core.json.JsonReader
import maryk.core.json.JsonWriter
import maryk.core.objects.DataModel
import maryk.core.properties.ByteCollectorWithLengthCacher
import maryk.core.properties.IsPropertyContext
import maryk.test.shouldBe

fun <T: Any, CX: IsPropertyContext> checkProtoBufConversion(
        value: T,
        dataModel: DataModel<T, CX>,
        context: CX,
        checker: (T) -> Unit = { it shouldBe value }
) {
    val bc = ByteCollectorWithLengthCacher()
    val byteLength = dataModel.calculateProtoBufLength(value, bc::addToCache, context)
    bc.reserve(byteLength)
    dataModel.writeProtoBuf(value, bc::nextLengthFromCache, bc::write, context)

    val converted = dataModel.readProtoBufToObject(byteLength, bc::read, context)

    checker(converted)
}

fun <T: Any, CX: IsPropertyContext> checkJsonConversion(
        value: T,
        dataModel: DataModel<T, CX>,
        context: CX,
        checker: (T) -> Unit = { it shouldBe value }
) {
    var output = ""

    val writer = JsonWriter {
        output += it
    }

    dataModel.writeJson(value, writer, context)

    val chars = output.iterator()
    val reader = JsonReader { chars.nextChar() }
    val converted = dataModel.readJsonToObject(reader, context)

    checker(converted)
}