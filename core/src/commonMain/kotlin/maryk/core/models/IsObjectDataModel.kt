package maryk.core.models

import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.IsEmbeddedObjectDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.wrapper.ObjectListPropertyDefinitionWrapper
import maryk.core.properties.exceptions.ValidationUmbrellaException
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.RequestContext
import maryk.core.values.IsValueItems
import maryk.core.values.MutableValueItems
import maryk.core.values.ObjectValues

typealias IsSimpleObjectDataModel<DO> = IsObjectDataModel<DO, ObjectPropertyDefinitions<DO>>

/** A DataModel which holds properties and can be validated */
interface IsObjectDataModel<DO : Any, P : ObjectPropertyDefinitions<DO>> :
    IsDataModelWithValues<DO, P, ObjectValues<DO, P>> {
    /**
     * Validate a [dataObject] and get reference from [refGetter] if exception needs to be thrown
     * @throws ValidationUmbrellaException if input was invalid
     */
    fun validate(dataObject: DO, refGetter: () -> IsPropertyReference<DO, IsPropertyDefinition<DO>, *>? = { null })

    /** Creates a Data Object by [values] */
    operator fun invoke(values: ObjectValues<DO, P>): DO

    /** Create a ObjectValues with given [createValues] function */
    override fun values(context: RequestContext?, createValues: P.() -> IsValueItems) =
        ObjectValues(this, createValues(this.properties), context)
}

/**
 * Converts a DataObject back to ObjectValues
 */
fun <DO : Any, DM : IsObjectDataModel<DO, P>, P : ObjectPropertyDefinitions<DO>> DM.asValues(
    dataObject: DO,
    context: RequestContext? = null
): ObjectValues<DO, P> {
    val mutableMap = MutableValueItems()

    @Suppress("UNCHECKED_CAST")
    for (property in this.properties) {
        when (property) {
            is ObjectListPropertyDefinitionWrapper<*, *, *, *, *> -> {
                val dataModel =
                    (property.definition.valueDefinition as EmbeddedObjectDefinition<*, *, *, *, *>).dataModel as IsObjectDataModel<Any, ObjectPropertyDefinitions<Any>>
                property.getter(dataObject)?.let { list ->
                    mutableMap[property.index] = (list as List<Any>).map {
                        dataModel.asValues(it, context)
                    }.toList()
                }
            }
            is IsEmbeddedObjectDefinition<*, *, *, *, *> -> {
                val dataModel = property.dataModel as IsObjectDataModel<Any, ObjectPropertyDefinitions<Any>>
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
