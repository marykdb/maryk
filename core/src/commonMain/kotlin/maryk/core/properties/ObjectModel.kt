package maryk.core.properties

import maryk.core.models.ObjectDataModel
import kotlin.reflect.KClass

abstract class ObjectModel<DO: Any, P: ObjectPropertyDefinitions<DO>>(
    objClass: KClass<DO>,
): InternalModel<DO, P, IsPropertyContext, IsPropertyContext>(), IsInternalModel<DO, P, IsPropertyContext, IsPropertyContext> {

    @Suppress("UNCHECKED_CAST")
    override val Model = object: ObjectDataModel<DO, P>(
        objClass.simpleName!!,
        this@ObjectModel as P,
    ) {}
}
