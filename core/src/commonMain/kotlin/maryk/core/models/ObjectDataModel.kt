package maryk.core.models

import maryk.core.models.serializers.ObjectDataModelSerializer
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.references.AnyOutPropertyReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.ContainsDefinitionsContext
import maryk.core.query.RequestContext
import maryk.core.values.MutableValueItems
import maryk.core.values.ObjectValues
import maryk.core.values.ValueItem

typealias SimpleObjectModel<DO, DM> = ObjectDataModel<DO, DM, IsPropertyContext, IsPropertyContext>
typealias DefinitionModel<DO> = ObjectDataModel<DO, IsObjectDataModel<DO>, ContainsDefinitionsContext, ContainsDefinitionsContext>
internal typealias QueryModel<DO, DM> = ObjectDataModel<DO, DM, RequestContext, RequestContext>
internal typealias SimpleQueryModel<DO> = ObjectDataModel<DO, IsObjectDataModel<DO>, RequestContext, RequestContext>

abstract class ObjectDataModel<DO: Any, DM: IsObjectDataModel<DO>, CXI : IsPropertyContext, CX : IsPropertyContext>: AbstractObjectDataModel<DO>(),
    IsTypedObjectModel<DO, DM, CXI, CX> {
    @Suppress("UNCHECKED_CAST", "LeakingThis")
    override val Serializer = ObjectDataModelSerializer<DO, DM, CXI, CX>(this as DM)

    abstract override operator fun invoke(values: ObjectValues<DO, DM>): DO

    @Suppress("UNCHECKED_CAST")
    operator fun <T : Any, R : IsPropertyReference<T, IsPropertyDefinition<T>, *>> invoke(
        parent: AnyOutPropertyReference? = null,
        referenceGetter: DM.() -> (AnyOutPropertyReference?) -> R
    ) = referenceGetter(this as DM)(parent)

    operator fun <R> invoke(block: DM.() -> R): R {
        @Suppress("UNCHECKED_CAST")
        return block(this as DM)
    }

    /** Create a new ObjectValues with given [pairs] */
    @Suppress("UNCHECKED_CAST")
    fun create(
        vararg pairs: ValueItem?,
        setDefaults: Boolean = true,
        context: RequestContext? = null,
    ) = ObjectValues(
        this@ObjectDataModel as DM,
        MutableValueItems().apply {
            fillWithPairs(this@ObjectDataModel, pairs, setDefaults)
        },
        context,
    )
}
