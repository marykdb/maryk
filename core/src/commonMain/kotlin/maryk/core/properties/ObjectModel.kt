package maryk.core.properties

import maryk.core.models.ObjectDataModel
import kotlin.reflect.KClass

interface IsObjectModel<DO: Any, P: IsObjectPropertyDefinitions<DO>>: IsBaseModel<DO, P, IsPropertyContext, IsPropertyContext>, IsTypedObjectPropertyDefinitions<DO, P, IsPropertyContext>, IsSerializableModel {
    override val Model: ObjectDataModel<DO, P>
}

abstract class ObjectModel<DO: Any, P: ObjectPropertyDefinitions<DO>>(
    objClass: KClass<DO>,
): InternalModel<DO, P, IsPropertyContext, IsPropertyContext>(), IsObjectModel<DO, P> {
    @Suppress("UNCHECKED_CAST")
    override val Model = object: ObjectDataModel<DO, P>(
        objClass.simpleName!!,
        this@ObjectModel as P,
    ) {}
}
