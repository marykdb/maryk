package maryk.core.models

import maryk.core.definitions.MarykPrimitive
import maryk.core.definitions.PrimitiveType
import maryk.core.objects.ValueMap
import maryk.core.properties.definitions.PropertyDefinitions

/**
 * DataModel for non contextual models. Contains a [name] to identify the model and [properties] which define how the
 * properties should be validated. It models the DataObjects of type [DO] which can be validated. And it contains a
 * reference to the propertyDefinitions of type [P] which can be used for the references to the properties.
 */
abstract class DataModel<DO: Any, out P: PropertyDefinitions<DO>>(
    override val name: String,
    properties: P
) : SimpleDataModel<DO, P>(
    properties
), MarykPrimitive {
    override val primitiveType = PrimitiveType.Model

    internal object Model : DefinitionDataModel<DataModel<*, *>>(
        properties = object : PropertyDefinitions<DataModel<out Any, PropertyDefinitions<out Any>>>() {
            init {
                AbstractDataModel.addName(this) {
                    it.name
                }
                AbstractDataModel.addProperties(this)
            }
        }
    ) {
        override fun invoke(map: ValueMap<DataModel<*, *>>) = object : DataModel<Any, PropertyDefinitions<Any>>(
            name = map(0),
            properties = map(1)
        ){
            override fun invoke(map: ValueMap<Any>): Any {
                // TODO: What is the right path here?
                return object : Any(){}
            }
        }
    }
}
