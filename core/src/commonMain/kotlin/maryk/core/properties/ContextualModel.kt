package maryk.core.properties

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
}
