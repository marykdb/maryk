package maryk.core.properties.definitions

import maryk.core.exceptions.DefNotFoundException
import maryk.core.models.AbstractDataModel
import maryk.core.objects.ValueMap
import maryk.core.properties.IsPropertyContext
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter
import maryk.core.protobuf.calculateKeyAndContentLength
import maryk.core.protobuf.writeKeyWithLength
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.lib.exceptions.ParseException

/** Interface for property definitions containing embedded DataObjects of [DO] and context [CX]. */
interface IsEmbeddedObjectDefinition<DO : Any, P: PropertyDefinitions<DO>, out DM : AbstractDataModel<DO, P, CXI, CX>, CXI: IsPropertyContext, CX: IsPropertyContext> :
    IsValueDefinition<DO, CXI>,
    IsTransportablePropertyDefinitionType<DO>,
    HasDefaultValueDefinition<DO>
{
    val dataModel: DM

    /** Writes a ValueMap to Json with [writer] */
    fun writeJsonValue(value: ValueMap<DO, P>, writer: IsJsonLikeWriter, context: CXI? = null)

    /**
     * Reads JSON values from [reader] with [context] to a ValueMap
     * @throws ParseException when encountering unparsable content
     */
    fun readJsonToMap(reader: IsJsonLikeReader, context: CXI? = null): ValueMap<DO, P>

    fun calculateTransportByteLengthWithKey(index: Int, value: ValueMap<DO, P>, cacher: WriteCacheWriter, context: CXI? = null) =
        calculateKeyAndContentLength(this.wireType, index, cacher) {
            this.calculateTransportByteLength(value, cacher, context)
        }

    /**
     * Calculates the needed bytes to transport [value] with [context]
     * Caches any calculated lengths in [cacher]
     */
    fun calculateTransportByteLength(value: ValueMap<DO, P>, cacher: WriteCacheWriter, context: CXI? = null): Int

    fun writeTransportBytesWithKey(
        index: Int,
        value: ValueMap<DO, P>,
        cacheGetter: WriteCacheReader,
        writer: (byte: Byte) -> Unit,
        context: CXI? = null
    ) {
        writeKeyWithLength(this.wireType, index, writer, cacheGetter)
        this.writeTransportBytes(value, cacheGetter, writer, context)
    }

    /** Writes ValueMap to bytes with [writer] and [context] for transportation */
    fun writeTransportBytes(value: ValueMap<DO, P>, cacheGetter: WriteCacheReader, writer: (byte: Byte) -> Unit, context: CXI? = null)

    /**
     * Read value from [reader] with [context] until [length] is reached
     * @throws DefNotFoundException if definition is not found to translate bytes
     */
    fun readTransportBytesToMap(length: Int, reader: () -> Byte, context: CXI? = null): ValueMap<DO, P>
}
