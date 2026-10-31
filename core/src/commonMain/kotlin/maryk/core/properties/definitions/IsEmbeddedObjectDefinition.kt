package maryk.core.properties.definitions

import maryk.core.models.IsObjectDataModel
import maryk.core.models.IsTypedObjectDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.values.ObjectValues
import maryk.json.IsJsonLikeReader
import maryk.core.models.serializers.IsObjectDataModelSerializer
import maryk.core.models.serializers.continuationValues
import maryk.core.models.serializers.readProtoBufWithEarlier

/** Interface for property definitions containing embedded DataObjects of [DO] and context [CX]. */
interface IsEmbeddedObjectDefinition<DO : Any, out DM : IsTypedObjectDataModel<DO, *, CXI, CX>, in CXI : IsPropertyContext, CX : IsPropertyContext> :
    IsValueDefinition<DO, CXI>,
    HasDefaultValueDefinition<DO>,
    IsEmbeddedDefinition<DM>,
    IsUsableInMultiType<DO, CXI>,
    IsUsableInMapValue<DO, CXI> {
    override val dataModel: DM

    /** Read JSON into ObjectValues */
    fun readJsonToValues(reader: IsJsonLikeReader, context: CXI?): ObjectValues<DO, out IsObjectDataModel<DO>> =
        this.dataModel.Serializer.readJson(reader, this.dataModel.Serializer.transformContext(context))

    override fun readJson(reader: IsJsonLikeReader, context: CXI?) =
        this.readJsonToValues(reader, context).toDataObject()

    override fun readTransportBytes(length: Int, reader: () -> Byte, context: CXI?, earlierValue: DO?): DO =
        readTransportBytesToValues(length, reader, context, earlierValue).toDataObject()

    /** Read ProtoBuf into ObjectValues */
    fun readTransportBytesToValues(length: Int, reader: () -> Byte, context: CXI?) =
        this.dataModel.Serializer.readProtoBuf(length, reader, this.dataModel.Serializer.transformContext(context))

    override fun compatibleWith(
        definition: IsPropertyDefinition<*>,
        checkedDataModelNames: MutableList<String>?,
        addIncompatibilityReason: ((String) -> Unit)?,
    ): Boolean {
        var compatible = super<IsValueDefinition>.compatibleWith(definition, checkedDataModelNames, addIncompatibilityReason)

        (definition as? IsEmbeddedObjectDefinition<*, *, *, *>)?.let {
            compatible = this.compatibleWithDefinitionWithDataModel(definition, addIncompatibilityReason, checkedDataModelNames) && compatible
        }

        return compatible
    }
}

/** Continue an embedded message without constructing a possibly incomplete data object. */
@Suppress("UNCHECKED_CAST")
internal fun <DO : Any, CXI : IsPropertyContext, CX : IsPropertyContext>
    IsEmbeddedObjectDefinition<DO, *, CXI, CX>.readTransportBytesToValues(
    length: Int,
    reader: () -> Byte,
    context: CXI?,
    earlierValue: Any?,
): ObjectValues<DO, IsObjectDataModel<DO>> {
    val serializer = dataModel.Serializer as IsObjectDataModelSerializer<DO, IsObjectDataModel<DO>, CXI, CX>
    val innerContext = serializer.transformContext(context)
    val earlier = serializer.continuationValues(dataModel, earlierValue as DO?, innerContext)
    return serializer.readProtoBufWithEarlier(
        length, reader, innerContext, earlier,
    )
}
