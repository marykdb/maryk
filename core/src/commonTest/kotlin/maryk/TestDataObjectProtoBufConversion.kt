package maryk

import maryk.core.models.IsObjectDataModel
import maryk.core.models.IsTypedObjectDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.protobuf.WriteCache
import maryk.core.values.ObjectValues
import maryk.test.ByteCollector
import kotlin.test.assertEquals

/** Convert dataObject with an object DataModel */
fun <DO : Any, P : IsObjectDataModel<DO>, CXI : IsPropertyContext, CX : IsPropertyContext> checkProtoBufConversion(
    value: DO,
    dataModel: IsTypedObjectDataModel<DO, P, CXI, CX>,
    context: (() -> CXI)? = null,
    checker: (DO, DO) -> Unit = { converted, original -> assertEquals(original, converted) },
    resetContextBeforeRead: Boolean = false
) {
    var newContext = dataModel.Serializer.transformContext(context?.invoke())

    val bc = ByteCollector()
    val cache = WriteCache()

    val byteLength = dataModel.Serializer.calculateObjectProtoBufLength(value, cache, newContext)
    bc.reserve(byteLength)
    dataModel.Serializer.writeObjectProtoBuf(value, cache, bc::write, newContext)

    if (resetContextBeforeRead) {
        newContext = dataModel.Serializer.transformContext(context?.invoke())
    }

    val converted = dataModel.Serializer.readProtoBuf(byteLength, bc::read, newContext).toDataObject()

    checker(converted, value)
}

/** Convert values with a values DataModel */
fun <DO : Any, P : IsObjectDataModel<DO>, CX : IsPropertyContext> checkProtoBufObjectValuesConversion(
    values: ObjectValues<DO, P>,
    dataModel: IsTypedObjectDataModel<DO, P, CX, CX>,
    context: (() -> CX)? = null,
    checker: (ObjectValues<DO, P>, ObjectValues<DO, P>) -> Unit = { converted, original -> assertEquals(original, converted) },
    resetContextBeforeRead: Boolean = false
) {
    val bc = ByteCollector()
    val cache = WriteCache()

    var newContext = context?.invoke()

    val byteLength = dataModel.Serializer.calculateProtoBufLength(values, cache, newContext)
    bc.reserve(byteLength)
    dataModel.Serializer.writeProtoBuf(values, cache, bc::write, newContext)

    if (resetContextBeforeRead) {
        newContext = context?.invoke()
    }

    val converted = dataModel.Serializer.readProtoBuf(byteLength, bc::read, newContext)

    checker(converted, values)
}


