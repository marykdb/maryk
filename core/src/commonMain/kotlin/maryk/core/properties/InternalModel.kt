package maryk.core.properties

import maryk.core.models.BaseDataModel
import maryk.core.models.serializers.IsObjectDataModelSerializer
import maryk.core.models.serializers.ObjectDataModelSerializer
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.references.AnyOutPropertyReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.ContainsDefinitionsContext
import maryk.core.query.RequestContext
import maryk.core.values.MutableValueItems
import maryk.core.values.ObjectValues
import maryk.core.values.ValueItem

interface IsInternalModel<DO: Any, P: IsObjectPropertyDefinitions<DO>, in CXI : IsPropertyContext, CX : IsPropertyContext>: IsBaseModel<DO, P, CXI, CX>, IsTypedObjectPropertyDefinitions<DO, P, CX> {
    override val Serializer: IsObjectDataModelSerializer<DO, P, CXI, CX>
}

typealias SimpleObjectModel<DO, P> = InternalModel<DO, P, IsPropertyContext, IsPropertyContext>
typealias DefinitionModel<DO> = InternalModel<DO, ObjectPropertyDefinitions<DO>, ContainsDefinitionsContext, ContainsDefinitionsContext>
internal typealias QueryModel<DO, P> = InternalModel<DO, P, RequestContext, RequestContext>
internal typealias SimpleQueryModel<DO> = InternalModel<DO, ObjectPropertyDefinitions<DO>, RequestContext, RequestContext>

abstract class InternalModel<DO: Any, DM: IsObjectPropertyDefinitions<DO>, CXI : IsPropertyContext, CX : IsPropertyContext>: ObjectPropertyDefinitions<DO>(), IsInternalModel<DO, DM, CXI, CX> {
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
        this@InternalModel as DM,
        MutableValueItems().apply {
            fillWithPairs(this@InternalModel, pairs, setDefaults)
        },
        context,
    )

    @Suppress("UNCHECKED_CAST", "LeakingThis")
    override val Model = object: BaseDataModel<DM>(
        properties = this as DM,
    ) {}
}
