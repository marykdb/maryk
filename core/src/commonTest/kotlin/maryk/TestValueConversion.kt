package maryk

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WriteCache
import maryk.test.ByteCollector
import kotlin.test.assertEquals
import kotlin.test.expect

fun <T : Any, CX : IsPropertyContext> checkProtoBufConversion(
    bc: ByteCollector = ByteCollector(),
    value: T,
    def: IsValueDefinition<T, CX>,
    context: CX? = null,
    compare: (T, T) -> Unit = { converted, original -> assertEquals(original, converted) }
) {
    val cache = WriteCache()

    bc.reserve(
        def.calculateTransportByteLengthWithKey(22, value, cache, context)
    )
    def.writeTransportBytesWithKey(22, value, cache, bc::write, context)
    val key = ProtoBuf.readKey(bc::read)
    expect(22u) { key.tag }
    expect(def.wireType) { key.wireType }

    val converted = def.readTransportBytes(
        ProtoBuf.getLength(key.wireType, bc::read),
        bc::read,
        context
    )

    compare(converted, value)
    bc.reset()
}
