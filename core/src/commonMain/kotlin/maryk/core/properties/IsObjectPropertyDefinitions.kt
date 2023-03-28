package maryk.core.properties

import maryk.core.models.AbstractObjectDataModel
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.IsEmbeddedObjectDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.wrapper.ObjectListDefinitionWrapper
import maryk.core.properties.exceptions.ValidationException
import maryk.core.properties.exceptions.ValidationUmbrellaException
import maryk.core.properties.exceptions.createValidationUmbrellaException
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.RequestContext
import maryk.core.values.IsValueItems
import maryk.core.values.MutableValueItems
import maryk.core.values.ObjectValues

interface IsTypedObjectPropertyDefinitions<DO: Any, P: IsObjectPropertyDefinitions<DO>>: IsObjectPropertyDefinitions<DO> {
    operator fun invoke(values: ObjectValues<DO, P>): DO
}

interface IsObjectPropertyDefinitions<DO: Any>: IsTypedPropertyDefinitions<DO> {
    /**
     * Validate a [dataObject] and get reference from [refGetter] if exception needs to be thrown
     * @throws ValidationUmbrellaException if input was invalid
     */
    fun validate(
        dataObject: DO,
        refGetter: () -> IsPropertyReference<DO, IsPropertyDefinition<DO>, *>? = { null }
    ) {
        createValidationUmbrellaException(refGetter) { addException ->
            for (it in this) {
                try {
                    @Suppress("UNCHECKED_CAST")
                    it.validate(
                        newValue = (Model as AbstractObjectDataModel<DO, *, *, *>).getValueWithDefinition(it, dataObject, null),
                        parentRefFactory = refGetter
                    )
                } catch (e: ValidationException) {
                    addException(e)
                }
            }
        }
    }
}

/** Create a Values object with given [createMap] function */
fun <DO: Any, DM : IsObjectPropertyDefinitions<DO>> DM.values(
    context: RequestContext? = null,
    createMap: DM.() -> IsValueItems
) =
    ObjectValues(this, createMap(this), context)

/**
 * Converts a DataObject back to ObjectValues
 */
fun <DO : Any, DM : IsObjectPropertyDefinitions<DO>> DM.asValues(
    dataObject: DO,
    context: RequestContext? = null
): ObjectValues<DO, DM> {
    val mutableMap = MutableValueItems()

    @Suppress("UNCHECKED_CAST")
    for (property in this) {
        when (property) {
            is ObjectListDefinitionWrapper<out Any, *, *, *, DO> -> {
                val dataModel = (property.definition.valueDefinition as EmbeddedObjectDefinition<Any, IsSimpleBaseModel<Any, *, *>, *, *>).dataModel as IsObjectPropertyDefinitions<Any>
                property.getter(dataObject)?.let { list ->
                    mutableMap[property.index] = list.map {
                        dataModel.asValues(it, context)
                    }.toList()
                }
            }
            is IsEmbeddedObjectDefinition<*, *, *, *> -> {
                val dataModel = property.dataModel as IsObjectPropertyDefinitions<Any>
                property.getter(dataObject)?.let {
                    mutableMap[property.index] = dataModel.asValues(it, context)
                }
            }
            else -> property.getter(dataObject)?.let {
                mutableMap[property.index] = it
            }
        }
    }

    return this.values(context) {
        mutableMap
    }
}
