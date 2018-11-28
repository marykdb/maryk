package maryk.core.properties.definitions

import maryk.core.models.AbstractObjectDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.values.ObjectValues
import maryk.json.IsJsonLikeReader

/** Interface for property definitions containing embedded DataObjects of [DO] and context [CX]. */
interface IsEmbeddedObjectDefinition<DO : Any, P: ObjectPropertyDefinitions<DO>, out DM : AbstractObjectDataModel<DO, P, CXI, CX>, CXI: IsPropertyContext, CX: IsPropertyContext> :
    IsValueDefinition<DO, CXI>,
    IsTransportablePropertyDefinitionType<DO>,
    HasDefaultValueDefinition<DO>,
    IsEmbeddedDefinition<DM, P>
{
    override val dataModel: DM

    /** Read JSON into ObjectValues */
    fun readJsonToValues(reader: IsJsonLikeReader, context: CXI?): ObjectValues<DO, P> =
        this.dataModel.readJson(reader, this.dataModel.transformContext(context))

    override fun readJson(reader: IsJsonLikeReader, context: CXI?) =
        this.readJsonToValues(reader, context).toDataObject()

    override fun readTransportBytes(length: Int, reader: () -> Byte, context: CXI?) =
        this.readTransportBytesToValues(length, reader, context).toDataObject()

    /** Read ProtoBuf into ObjectValues */
    fun readTransportBytesToValues(length: Int, reader: () -> Byte, context: CXI?) =
        this.dataModel.readProtoBuf(length, reader, this.dataModel.transformContext(context))
}
