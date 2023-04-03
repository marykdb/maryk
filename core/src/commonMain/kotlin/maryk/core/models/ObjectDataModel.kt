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
    @Suppress("UNCHECKED_CAST")
    private val typedThis: DM = this as DM

    override val Serializer = ObjectDataModelSerializer<DO, DM, CXI, CX>(typedThis)

    abstract override operator fun invoke(values: ObjectValues<DO, DM>): DO

    operator fun <T : Any, R : IsPropertyReference<T, IsPropertyDefinition<T>, *>> invoke(
        parent: AnyOutPropertyReference? = null,
        referenceGetter: DM.() -> (AnyOutPropertyReference?) -> R
    ) = referenceGetter(typedThis)(parent)

    operator fun <R> invoke(block: DM.() -> R): R {
        return block(typedThis)
    }

    /** Create a new ObjectValues with given [pairs] */
    fun create(
        vararg pairs: ValueItem?,
        setDefaults: Boolean = true,
        context: RequestContext? = null,
    ) = ObjectValues(
        typedThis,
        MutableValueItems().apply {
            fillWithPairs(this@ObjectDataModel, pairs, setDefaults)
        },
        context,
    )
}
