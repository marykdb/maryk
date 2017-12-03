package maryk.core.properties.definitions.contextual

import maryk.core.json.JsonReader
import maryk.core.json.JsonWriter
import maryk.core.objects.RootDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.AbstractValueDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsSerializableFlexBytesEncodable
import maryk.core.properties.references.IsPropertyReference
import maryk.core.protobuf.ByteLengthContainer
import maryk.core.protobuf.WireType

/** Definition for a reference to another property */
class ContextualPropertyReferenceDefinition<in CX: IsPropertyContext>(
        name: String? = null,
        index: Int = -1,
        required: Boolean = true,
        val contextualResolver: (context: CX?) -> RootDataModel<Any>
): AbstractValueDefinition<IsPropertyReference<*, IsPropertyDefinition<*>>, CX>(
        name, index, false, true, required, false, WireType.LENGTH_DELIMITED
), IsSerializableFlexBytesEncodable<IsPropertyReference<*, IsPropertyDefinition<*>>, CX> {
    override fun asString(value: IsPropertyReference<*, IsPropertyDefinition<*>>, context: CX?)
            = value.completeName!!

    override fun fromString(string: String, context: CX?)
            = contextualResolver(context).getPropertyReferenceByName(string)

    override fun writeJsonValue(value: IsPropertyReference<*, IsPropertyDefinition<*>>, writer: JsonWriter, context: CX?) {
        writer.writeString(value.completeName!!)
    }

    override fun readJson(reader: JsonReader, context: CX?)
            = fromString(reader.lastValue, context)

    override fun calculateTransportByteLength(value: IsPropertyReference<*, IsPropertyDefinition<*>>, lengthCacher: (length: ByteLengthContainer) -> Unit, context: CX?)
            = value.calculateTransportByteLength(lengthCacher)

    override fun writeTransportBytes(value: IsPropertyReference<*, IsPropertyDefinition<*>>, lengthCacheGetter: () -> Int, writer: (byte: Byte) -> Unit, context: CX?) {
        value.writeTransportBytes(lengthCacheGetter, writer)
    }

    override fun readTransportBytes(length: Int, reader: () -> Byte, context: CX?): IsPropertyReference<*, IsPropertyDefinition<*>>
            = contextualResolver(context).getPropertyReferenceByBytes(length, reader)
}

