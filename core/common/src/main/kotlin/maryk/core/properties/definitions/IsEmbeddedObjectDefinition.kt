package maryk.core.properties.definitions

import maryk.core.exceptions.DefNotFoundException
import maryk.core.extensions.bytes.initIntByVar
import maryk.core.models.AbstractObjectDataModel
import maryk.core.objects.ObjectValues
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.references.CanHaveComplexChildReference
import maryk.core.properties.references.IsPropertyReference
import maryk.json.IsJsonLikeReader

/** Interface for property definitions containing embedded DataObjects of [DO] and context [CX]. */
interface IsEmbeddedObjectDefinition<DO : Any, P: ObjectPropertyDefinitions<DO>, out DM : AbstractObjectDataModel<DO, P, CXI, CX>, CXI: IsPropertyContext, CX: IsPropertyContext> :
    IsValueDefinition<DO, CXI>,
    IsTransportablePropertyDefinitionType<DO>,
    HasDefaultValueDefinition<DO>
{
    val dataModel: DM

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

    /** Resolve a reference from [reader] found on a [parentReference] */
    fun resolveReference(
        reader: () -> Byte,
        parentReference: CanHaveComplexChildReference<*, *, *, *>? = null
    ): IsPropertyReference<Any, *, *> {
        val index = initIntByVar(reader)
        return this.dataModel.properties[index]?.getRef(parentReference)
                ?: throw DefNotFoundException("Embedded Definition with $index not found")
    }

    /** Resolve a reference from [name] found on a [parentReference] */
    fun resolveReferenceByName(
        name: String,
        parentReference: CanHaveComplexChildReference<*, *, *, *>? = null
    ): IsPropertyReference<Any, *, *> {
        return this.dataModel.properties[name]?.getRef(parentReference)
                ?: throw DefNotFoundException("Embedded Definition with $name not found")
    }
}
