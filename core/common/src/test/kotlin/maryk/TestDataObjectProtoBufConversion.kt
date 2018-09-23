package maryk

import maryk.core.models.AbstractObjectDataModel
import maryk.core.models.AbstractValuesDataModel
import maryk.core.models.IsObjectDataModel
import maryk.core.models.IsValuesDataModel
import maryk.core.objects.ObjectValues
import maryk.core.objects.Values
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.PropertyDefinitions
import maryk.core.protobuf.WriteCache
import maryk.test.ByteCollector
import maryk.test.shouldBe

/** Convert dataObject with a object DataModel */
fun <DO: Any, P: ObjectPropertyDefinitions<DO>, CXI: IsPropertyContext, CX: IsPropertyContext> checkProtoBufConversion(
    value: DO,
    dataModel: AbstractObjectDataModel<DO, P, CXI, CX>,
    context: (() -> CXI)? = null,
    checker: (DO, DO) -> Unit = { converted, original -> converted shouldBe original },
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
fun <DM: IsValuesDataModel<P>, P: PropertyDefinitions, CX: IsPropertyContext> checkProtoBufValuesConversion(
    values: Values<DM, P>,
    dataModel: AbstractValuesDataModel<DM, P, CX>,
    context: (() -> CX)? = null,
    checker: (Values<DM, P>, Values<DM, P>) -> Unit = { converted, original -> converted shouldBe original },
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

/** Convert values with a values DataModel */
fun <DO: Any, DM: IsObjectDataModel<DO, P>, P: ObjectPropertyDefinitions<DO>, CX: IsPropertyContext> checkProtoBufObjectValuesConversion(
    values: ObjectValues<DO, P>,
    dataModel: AbstractObjectDataModel<DO, P, CX, CX>,
    context: (() -> CX)? = null,
    checker: (ObjectValues<DO, P>, ObjectValues<DO, P>) -> Unit = { converted, original -> converted shouldBe original },
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


