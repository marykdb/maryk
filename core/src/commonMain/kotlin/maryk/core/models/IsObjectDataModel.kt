package maryk.core.models

import maryk.core.models.serializers.IsObjectDataModelSerializer
import maryk.core.properties.IsPropertyContext
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

interface IsTypedObjectDataModel<DO: Any, DM: IsObjectDataModel<DO>, CX: IsPropertyContext>: IsObjectDataModel<DO> {
    override val Serializer : IsObjectDataModelSerializer<DO, DM, *, CX>

    operator fun invoke(values: ObjectValues<DO, DM>): DO
}

@Suppress("UNCHECKED_CAST")
fun <DO: Any, DM: IsObjectDataModel<DO>> DM.invoke(
    values: ObjectValues<DO, DM>
) = (this as IsTypedObjectDataModel<DO, DM, *>)(values)

interface IsObjectDataModel<DO: Any>: IsTypedDataModel<DO> {
    override val Serializer : IsObjectDataModelSerializer<DO, *, *, *>

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
                    it.validate(
                        newValue = Serializer.getValueWithDefinition(it, dataObject, null),
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
fun <DO: Any, DM : IsObjectDataModel<DO>> DM.values(
    context: RequestContext? = null,
    createMap: DM.() -> IsValueItems
) =
    ObjectValues(this, createMap(this), context)

/**
 * Converts a DataObject back to ObjectValues
 */
fun <DO : Any, DM : IsObjectDataModel<DO>> DM.asValues(
    dataObject: DO,
    context: RequestContext? = null
): ObjectValues<DO, DM> {
    val mutableMap = MutableValueItems()

    for (property in this) {
        when (property) {
            is ObjectListDefinitionWrapper<out Any, *, *, *, DO> -> {
                @Suppress("UNCHECKED_CAST")
                val dataModel = (property.definition.valueDefinition as EmbeddedObjectDefinition<Any, IsSimpleBaseObjectDataModel<Any, *, *>, *, *>).dataModel as IsObjectDataModel<Any>
                property.getter(dataObject)?.let { list ->
                    mutableMap[property.index] = list.map {
                        dataModel.asValues(it, context)
                    }.toList()
                }
            }
            is IsEmbeddedObjectDefinition<*, *, *, *> -> {
                @Suppress("UNCHECKED_CAST")
                val dataModel = property.dataModel as IsObjectDataModel<in Any>
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
