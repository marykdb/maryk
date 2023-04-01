package maryk.core.properties

import maryk.core.models.AbstractObjectDataModel
import maryk.core.models.serializers.ObjectDataModelSerializer
import maryk.core.values.ObjectValues

/**
 * ObjectDataModel of type [DO] which create contexts so properties know the values of other properties.
 * Use it to create a context on starting an action which needs a context.
 * This context is cached if it is needed to read multiple times
 */
abstract class ContextualModel<DO: Any, P: ContextualModel<DO, P, CXI, CX>, CXI: IsPropertyContext, CX: IsPropertyContext>(
    val contextTransformer: Unit.(CXI?) -> CX?,
) : InternalModel<DO, P, CXI, CX>(), IsObjectPropertyDefinitions<DO>, IsInternalModel<DO, P, CXI, CX> {
    abstract override fun invoke(values: ObjectValues<DO, P>): DO

    @Suppress("UNCHECKED_CAST", "LeakingThis")
    override val Serializer = object: ObjectDataModelSerializer<DO, P, CXI, CX>(this as P) {
        override fun transformContext(context: CXI?) = contextTransformer(Unit, context)
    }

    @Suppress("UNCHECKED_CAST")
    override val Model = object: AbstractObjectDataModel<DO, P, CXI, CX>(
        this@ContextualModel as P,
    ) {}
}
