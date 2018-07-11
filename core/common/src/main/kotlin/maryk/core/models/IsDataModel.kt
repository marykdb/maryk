package maryk.core.models

import maryk.core.objects.Values
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.exceptions.ValidationUmbrellaException
import maryk.core.properties.references.IsPropertyReference

typealias IsSimpleDataModel<DO> = IsDataModel<DO, ObjectPropertyDefinitions<DO>>

/** A DataModel which holds properties and can be validated */
interface IsDataModel<DO: Any, P: ObjectPropertyDefinitions<DO>> {
    /** Object which contains all property definitions. Can also be used to get property references. */
    val properties: P

    /** For quick notation to return [T] that operates with [runner] on Properties */
    fun <T: Any> props(
        runner: P.() -> T
    ) = runner(this.properties)

    /**
     * Get property reference fetcher of this DataModel with [referenceGetter]
     * Optionally pass an already resolved [parent]
     * For Strongly typed reference notation
     */
    operator fun <T: Any, W: IsPropertyDefinition<T>> invoke(
        parent: IsPropertyReference<out Any, IsPropertyDefinition<*>>? = null,
        referenceGetter: P.() ->
            (IsPropertyReference<out Any, IsPropertyDefinition<*>>?) -> IsPropertyReference<T, W>
    ): IsPropertyReference<T, W> {
        return referenceGetter(this.properties)(parent)
    }

    /**
     * To get a top level reference on a model by passing a [propertyDefinitionGetter] from its defined Properties
     * Optionally pass an already resolved [parent]
     */
    fun <T: Any, W: IsPropertyDefinitionWrapper<T, *, *, *>> ref(
        parent: IsPropertyReference<out Any, IsPropertyDefinition<*>>? = null,
        propertyDefinitionGetter: P.()-> W
    ): IsPropertyReference<T, W> {
        @Suppress("UNCHECKED_CAST")
        return propertyDefinitionGetter(this.properties).getRef(parent) as IsPropertyReference<T, W>
    }

    /**
     * Validate a [dataObject] and get reference from [refGetter] if exception needs to be thrown
     * @throws ValidationUmbrellaException if input was invalid
     */
    fun validate(dataObject: DO, refGetter: () -> IsPropertyReference<DO, IsPropertyDefinition<DO>>? = { null })

    /**
     * Validate a [map] with values and get reference from [refGetter] if exception needs to be thrown
     * @throws ValidationUmbrellaException if input was invalid
     */
    fun validate(map: Values<DO, P>, refGetter: () -> IsPropertyReference<DO, IsPropertyDefinition<DO>>? = { null })

    /** Creates a Data Object by [map] */
    operator fun invoke(map: Values<DO, P>): DO

    /** Create a Values with given [createMap] function */
    fun map(createMap: P.() -> Map<Int, Any?>) = Values(this, createMap(this.properties))

    /**
     * To get a top level reference on a model by passing a [propertyDefinitionGetter] from its defined Properties
     * Optionally pass an already resolved [parent]
     */
    fun <T: Any, W: IsPropertyDefinitionWrapper<T, *, *, *>> graph(
        parent: IsPropertyReference<out Any, IsPropertyDefinition<*>>?,
        propertyDefinitionGetter: P.()-> W
    ): IsPropertyReference<T, W> {
        @Suppress("UNCHECKED_CAST")
        return propertyDefinitionGetter(this.properties).getRef(parent) as IsPropertyReference<T, W>
    }
}
