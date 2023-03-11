package maryk.core.properties

import maryk.core.models.ContextualDataModel
import maryk.core.values.ObjectValues

abstract class ContextualModel<DO: Any, P: ContextualModel<DO, P, CXI, CX>, CXI: IsPropertyContext, CX: IsPropertyContext>(
    val contextTransformer: Unit.(CXI?) -> CX?,
) : ObjectPropertyDefinitions<DO>(), IsObjectPropertyDefinitions<DO>, IsInternalModel<DO, P, CXI, CX> {
    abstract fun invoke(values: ObjectValues<DO, P>): DO

    @Suppress("UNCHECKED_CAST")
    override val Model = object: ContextualDataModel<DO, P, CXI, CX>(
        this@ContextualModel as P,
        contextTransformer = contextTransformer,
    ) {
        override fun invoke(values: ObjectValues<DO, P>): DO =
            this@ContextualModel.invoke(values)
    }
}
