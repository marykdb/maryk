package maryk

import maryk.core.models.AbstractObjectDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.protobuf.WriteCache
import maryk.core.values.ObjectValues
import maryk.test.ByteCollector
import kotlin.test.assertEquals

/** Convert dataObject with a object DataModel */
fun <DO : Any, P : ObjectPropertyDefinitions<DO>, CXI : IsPropertyContext, CX : IsPropertyContext> checkProtoBufConversion(
    value: DO,
    dataModel: AbstractObjectDataModel<DO, P, CXI, CX>,
    context: (() -> CXI)? = null,
    checker: (DO, DO) -> Unit = { converted, original -> assertEquals(original, converted) },
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

    val converted = dataModel.readProtoBuf(byteLength, bc::read, newContext).toDataObject()

    checker(converted, value)
}

/** Convert values with a values DataModel */
fun <DO : Any, P : ObjectPropertyDefinitions<DO>, CX : IsPropertyContext> checkProtoBufObjectValuesConversion(
    values: ObjectValues<DO, P>,
    dataModel: AbstractObjectDataModel<DO, P, CX, CX>,
    context: (() -> CX)? = null,
    checker: (ObjectValues<DO, P>, ObjectValues<DO, P>) -> Unit = { converted, original -> assertEquals(original, converted) },
    resetContextBeforeRead: Boolean = false
) {
    val bc = ByteCollector()
    val cache = WriteCache()

    var newContext = context?.invoke()

    val byteLength = dataModel.calculateProtoBufLength(values, cache, newContext)
    bc.reserve(byteLength)
    dataModel.writeProtoBuf(values, cache, bc::write, newContext)

    if (resetContextBeforeRead) {
        newContext = context?.invoke()
    }

    val converted = dataModel.readProtoBuf(byteLength, bc::read, newContext)

    checker(converted, values)
}


