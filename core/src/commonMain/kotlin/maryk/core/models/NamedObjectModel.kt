package maryk.core.models

import maryk.core.models.definitions.IsObjectDataModelDefinition
import maryk.core.models.definitions.ObjectDataModelDefinition
import maryk.core.models.serializers.IsObjectDataModelSerializer
import maryk.core.properties.IsPropertyContext
import kotlin.reflect.KClass

interface IsNamedObjectModel<DO: Any, DM: IsObjectDataModel<DO>>: IsBaseObjectDataModel<DO, DM, IsPropertyContext, IsPropertyContext>,
    IsTypedObjectDataModel<DO, DM, IsPropertyContext>, IsStorableDataModel {
    override val Serializer: IsObjectDataModelSerializer<DO, DM, IsPropertyContext, IsPropertyContext>
    override val Meta: IsObjectDataModelDefinition<DO, DM>
}

abstract class NamedObjectModel<DO: Any, DM: IsObjectDataModel<DO>>(
    objClass: KClass<DO>,
): ObjectDataModel<DO, DM, IsPropertyContext, IsPropertyContext>(), IsNamedObjectModel<DO, DM> {
    @Suppress("UNCHECKED_CAST", "LeakingThis")
    override val Meta = object: ObjectDataModelDefinition<DO, DM>(
        objClass.simpleName!!,
        this as DM,
    ) {}
}
