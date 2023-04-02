package maryk.core.properties

import maryk.core.models.definitions.IsObjectDataModel
import maryk.core.models.definitions.ObjectDataModelDefinition
import maryk.core.models.serializers.IsObjectDataModelSerializer
import kotlin.reflect.KClass

interface IsNamedObjectModel<DO: Any, DM: IsObjectPropertyDefinitions<DO>>: IsBaseModel<DO, DM, IsPropertyContext, IsPropertyContext>, IsTypedObjectPropertyDefinitions<DO, DM, IsPropertyContext>, IsStorableModel {
    override val Serializer: IsObjectDataModelSerializer<DO, DM, IsPropertyContext, IsPropertyContext>
    override val Model: IsObjectDataModel<DO, DM>
}

abstract class NamedObjectModel<DO: Any, DM: ObjectPropertyDefinitions<DO>>(
    objClass: KClass<DO>,
): ObjectModel<DO, DM, IsPropertyContext, IsPropertyContext>(), IsNamedObjectModel<DO, DM> {
    @Suppress("UNCHECKED_CAST", "LeakingThis")
    override val Model = object: ObjectDataModelDefinition<DO, DM>(
        objClass.simpleName!!,
        this as DM,
    ) {}
}
