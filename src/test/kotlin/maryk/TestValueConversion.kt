package maryk

import maryk.core.properties.ByteCollector
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WriteCache
import maryk.test.shouldBe

fun <T: Any, CX: IsPropertyContext> checkProtoBufConversion(
        bc: ByteCollector = ByteCollector(),
        value: T,
        def: IsValueDefinition<T, CX>,
        context: CX? = null
) {
    val cache = WriteCache()

    bc.reserve(
            def.calculateTransportByteLengthWithKey(22, value, cache, context)
    )
    def.writeTransportBytesWithKey(22, value, cache, bc::write, context)
    val key = ProtoBuf.readKey(bc::read)
    key.tag shouldBe 22
    key.wireType shouldBe def.wireType

    def.readTransportBytes(
            ProtoBuf.getLength(key.wireType, bc::read),
            bc::read,
            context
    ) shouldBe value
    bc.reset()
}