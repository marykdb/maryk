package maryk

import maryk.core.properties.ByteCollectorWithLengthCacher
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.AbstractValueDefinition
import maryk.core.protobuf.ProtoBuf
import maryk.test.shouldBe

fun <T: Any, CX: IsPropertyContext> checkProtoBufConversion(
        bc: ByteCollectorWithLengthCacher = ByteCollectorWithLengthCacher(),
        value: T,
        def: AbstractValueDefinition<T, CX>,
        context: CX? = null
) {
    bc.reserve(
            def.calculateTransportByteLengthWithKey(value, bc::addToCache, context)
    )
    def.writeTransportBytesWithKey(value, bc::nextLengthFromCache, bc::write, context)
    val key = ProtoBuf.readKey(bc::read)
    key.tag shouldBe def.index
    key.wireType shouldBe def.wireType

    def.readTransportBytes(
            ProtoBuf.getLength(key.wireType, bc::read),
            bc::read,
            context
    ) shouldBe value
    bc.reset()
}