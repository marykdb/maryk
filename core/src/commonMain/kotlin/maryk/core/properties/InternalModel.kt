package maryk.core.properties

import maryk.core.models.AbstractObjectDataModel
import maryk.core.models.IsObjectDataModel
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.references.AnyOutPropertyReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.ContainsDefinitionsContext
import maryk.core.query.RequestContext
import maryk.core.values.ObjectValues

interface IsInternalModel<DO: Any, P: IsObjectPropertyDefinitions<DO>> {
    @Suppress("PropertyName")
    val Model: IsObjectDataModel<DO, P>
}

typealias SimpleObjectModel<DO, P> = InternalModel<DO, P, IsPropertyContext, IsPropertyContext>
typealias DefinitionModel<DO> = InternalModel<DO, ObjectPropertyDefinitions<DO>, ContainsDefinitionsContext, ContainsDefinitionsContext>
internal typealias QueryModel<DO, P> = InternalModel<DO, P, RequestContext, RequestContext>
internal typealias SimpleQueryModel<DO> = InternalModel<DO, ObjectPropertyDefinitions<DO>, RequestContext, RequestContext>

abstract class InternalModel<DO: Any, P: ObjectPropertyDefinitions<DO>, in CXI : IsPropertyContext, CX : IsPropertyContext>: ObjectPropertyDefinitions<DO>(), IsInternalModel<DO, P> {
    abstract fun invoke(values: ObjectValues<DO, P>): DO

    @Suppress("UNCHECKED_CAST")
    operator fun <T : Any, R : IsPropertyReference<T, IsPropertyDefinition<T>, *>> invoke(
        parent: AnyOutPropertyReference? = null,
        referenceGetter: P.() -> (AnyOutPropertyReference?) -> R
    ) = referenceGetter(this as P)(parent)

    operator fun <R> invoke(block: P.() -> R): R {
        @Suppress("UNCHECKED_CAST")
        return block(this as P)
    }

    @Suppress("UNCHECKED_CAST")
    override val Model = object: AbstractObjectDataModel<DO, P, CXI, CX>(
        properties = this@InternalModel as P,
    ) {
        override fun invoke(values: ObjectValues<DO, P>): DO =
            this@InternalModel.invoke(values)
    }
}
