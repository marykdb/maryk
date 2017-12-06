package maryk.core.properties.definitions.contextual

import maryk.core.json.JsonReader
import maryk.core.json.JsonWriter
import maryk.core.objects.RootDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.AbstractValueDefinition
import maryk.core.properties.definitions.IsSerializableFlexBytesEncodable
import maryk.core.properties.references.IsPropertyReference
import maryk.core.protobuf.ByteLengthContainer
import maryk.core.protobuf.WireType

/** Definition for a reference to another property */
class ContextualPropertyReferenceDefinition<in CX: IsPropertyContext>(
        required: Boolean = true,
        val contextualResolver: (context: CX?) -> RootDataModel<Any>
): AbstractValueDefinition<IsPropertyReference<*, *>, CX>(
        false, true, required, false, WireType.LENGTH_DELIMITED
), IsSerializableFlexBytesEncodable<IsPropertyReference<*, *>, CX> {
    override fun asString(value: IsPropertyReference<*, *>, context: CX?)
            = value.completeName!!

    override fun fromString(string: String, context: CX?)
            = contextualResolver(context).getPropertyReferenceByName(string)

    override fun writeJsonValue(value: IsPropertyReference<*, *>, writer: JsonWriter, context: CX?) {
        writer.writeString(value.completeName!!)
    }

    override fun readJson(reader: JsonReader, context: CX?)
            = fromString(reader.lastValue, context)

    override fun calculateTransportByteLength(value: IsPropertyReference<*, *>, lengthCacher: (length: ByteLengthContainer) -> Unit, context: CX?)
            = value.calculateTransportByteLength(lengthCacher)

    override fun writeTransportBytes(value: IsPropertyReference<*, *>, lengthCacheGetter: () -> Int, writer: (byte: Byte) -> Unit, context: CX?) {
        value.writeTransportBytes(lengthCacheGetter, writer)
    }

    override fun readTransportBytes(length: Int, reader: () -> Byte, context: CX?): IsPropertyReference<*, *>
            = contextualResolver(context).getPropertyReferenceByBytes(length, reader)
}

