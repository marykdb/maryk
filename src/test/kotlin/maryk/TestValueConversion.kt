package maryk

import maryk.core.properties.ByteCollectorWithLengthCacher
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.protobuf.ProtoBuf
import maryk.test.shouldBe

fun <T: Any, CX: IsPropertyContext> checkProtoBufConversion(
        bc: ByteCollectorWithLengthCacher = ByteCollectorWithLengthCacher(),
        value: T,
        def: IsValueDefinition<T, CX>,
        context: CX? = null
) {
    bc.reserve(
            def.calculateTransportByteLengthWithKey(22, value, bc::addToCache, context)
    )
    def.writeTransportBytesWithKey(22, value, bc::nextLengthFromCache, bc::write, context)
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