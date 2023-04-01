package maryk.core.properties

import maryk.core.models.ContextualDataModel
import maryk.core.models.serializers.ObjectDataModelSerializer
import maryk.core.values.ObjectValues

abstract class ContextualModel<DO: Any, P: ContextualModel<DO, P, CXI, CX>, CXI: IsPropertyContext, CX: IsPropertyContext>(
    val contextTransformer: Unit.(CXI?) -> CX?,
) : InternalModel<DO, P, CXI, CX>(), IsObjectPropertyDefinitions<DO>, IsInternalModel<DO, P, CXI, CX> {
    abstract override fun invoke(values: ObjectValues<DO, P>): DO

    @Suppress("UNCHECKED_CAST", "LeakingThis")
    override val Serializer = object: ObjectDataModelSerializer<DO, P, CXI, CX>(this as P) {
        override fun transformContext(context: CXI?) = contextTransformer(Unit, context)
    }

    @Suppress("UNCHECKED_CAST")
    override val Model = object: ContextualDataModel<DO, P, CXI, CX>(
        this@ContextualModel as P,
        contextTransformer = contextTransformer,
    ) {}
}
