package maryk.core.properties

import maryk.core.models.IsObjectDataModel
import maryk.core.models.ObjectDataModel
import maryk.core.models.serializers.IsObjectDataModelSerializer
import kotlin.reflect.KClass

interface IsNamedObjectModel<DO: Any, P: IsObjectPropertyDefinitions<DO>>: IsBaseModel<DO, P, IsPropertyContext, IsPropertyContext>, IsTypedObjectPropertyDefinitions<DO, P, IsPropertyContext>, IsSerializableModel {
    override val Serializer: IsObjectDataModelSerializer<DO, P, IsPropertyContext, IsPropertyContext>
    override val Model: IsObjectDataModel<DO, P>
}

abstract class NamedObjectModel<DO: Any, P: ObjectPropertyDefinitions<DO>>(
    objClass: KClass<DO>,
): ObjectModel<DO, P, IsPropertyContext, IsPropertyContext>(), IsNamedObjectModel<DO, P> {
    @Suppress("UNCHECKED_CAST", "LeakingThis")
    override val Model = object: ObjectDataModel<DO, P>(
        objClass.simpleName!!,
        this as P,
    ) {}
}
