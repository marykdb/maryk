package maryk.core.properties.definitions.wrapper

import maryk.core.models.IsObjectDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.models.IsValuesDataModel
import kotlin.reflect.KProperty

/** Loads delegate for wrapper from [wrapperCreator] and sets it on [propertyDefinitions] */
class DefinitionWrapperDelegateLoader<W : IsDefinitionWrapper<*, *, *, Any>>(
    private val propertyDefinitions: IsValuesDataModel,
    private val wrapperCreator: (String) -> W
) {
    operator fun provideDelegate(
        thisRef: Any?,
        prop: KProperty<*>
    ) =
        wrapperCreator(prop.name).also(propertyDefinitions::addSingle)
}

/** Loads delegate for wrapper from [wrapperCreator] and sets it on object [propertyDefinitions] */
class ObjectDefinitionWrapperDelegateLoader<W : IsDefinitionWrapper<*, *, CX, DO>, DO: Any, CX: IsPropertyContext>(
    private val propertyDefinitions: IsObjectDataModel<DO>,
    private val wrapperCreator: (String) -> W
) {
    operator fun provideDelegate(
        thisRef: Any?,
        prop: KProperty<*>
    ) =
        wrapperCreator(prop.name).also(propertyDefinitions::addSingle)
}
