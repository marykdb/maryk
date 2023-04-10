package maryk.core.models

import maryk.core.models.definitions.ObjectDataModelDefinition
import maryk.core.properties.IsPropertyContext
import kotlin.reflect.KClass

/**
 * Object Data Model for defining custom Data Models which can serialize to and from normal Kotlin objects.
 */
abstract class ObjectDataModel<DO: Any, DM: IsObjectDataModel<DO>>(
    objClass: KClass<DO>,
): InternalObjectDataModel<DO, DM, IsPropertyContext, IsPropertyContext>(),
    IsBaseObjectDataModel<DO, DM, IsPropertyContext, IsPropertyContext>,
    IsTypedObjectDataModel<DO, DM, IsPropertyContext>,
    IsStorableDataModel<DO> {
    override val Meta = ObjectDataModelDefinition(objClass.simpleName!!)
}
