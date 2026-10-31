package maryk.core.models.serializers

import maryk.core.models.IsDataModel
import maryk.core.models.IsObjectDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.query.RequestContext
import maryk.core.values.IsValues
import maryk.core.values.MutableValueItems
import maryk.core.values.ObjectValues
import maryk.lib.exceptions.ParseException

/** A synchronous read frame, isolated per thread and matched by reader identity. */
internal class ProtoBufContinuation(
    val previous: IsValues<*>,
    val reader: () -> Byte,
) {
    var consumed = false
}

internal expect val protoBufContinuations: MutableList<ProtoBufContinuation>

/** Use serialized getters so conversions and omitted properties match ordinary object writes. */
@Suppress("UNCHECKED_CAST")
internal fun <DO : Any, DM : IsObjectDataModel<DO>, CX : IsPropertyContext>
    IsObjectDataModelSerializer<DO, DM, *, CX>.continuationValues(
    model: DM,
    value: DO?,
    context: CX?,
): ObjectValues<DO, DM>? {
    if (value == null) return null
    if (value is ObjectValues<*, *>) return value as ObjectValues<DO, DM>
    val items = MutableValueItems()
    for (property in model) {
        getValueWithDefinition(property, value, context)?.let {
            items[property.index] = it
        }
    }
    return ObjectValues(model, items, context as? RequestContext)
}

/** Keep virtual read hooks (including validation and recursion budgets) on continued messages. */
internal fun <V : IsValues<DM>, DM : IsDataModel, CX : IsPropertyContext>
    IsDataModelSerializer<V, DM, CX>.readProtoBufWithEarlier(
    length: Int,
    reader: () -> Byte,
    context: CX?,
    earlierValue: V?,
): V {
    if (earlierValue == null) return readProtoBuf(length, reader, context)
    val continuation = ProtoBufContinuation(earlierValue, reader)
    protoBufContinuations.add(continuation)
    try {
        val result = readProtoBuf(length, reader, context)
        if (!continuation.consumed) throw ParseException("Serializer does not support continued ProtoBuf messages")
        return result
    } finally {
        check(protoBufContinuations.removeAt(protoBufContinuations.lastIndex) === continuation)
    }
}
