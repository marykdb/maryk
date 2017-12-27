package maryk.core.objects

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.types.TypedValue

/** DataModel for non contextual models. Contains a [name] to identify the model and [properties] which define how the
 * properties should be validated. It models the DataObjects of type [DO] which can be validated. And it contains a
 * reference to the propertyDefinitions of type [P] which can be used for the references to the properties.
 */
abstract class DataModel<DO: Any, out P: PropertyDefinitions<DO>>(
        val name: String,
        properties: P
) : SimpleDataModel<DO, P>(
        properties
) {
    @Suppress("UNCHECKED_CAST")
    object Model : SimpleDataModel<DataModel<*, *>, PropertyDefinitions<DataModel<*, *>>>(
            properties = object : PropertyDefinitions<DataModel<*, *>>() {
                init {
                    AbstractDataModel.addProperties(this as PropertyDefinitions<DataModel<Any, PropertyDefinitions<Any>>>)
                    AbstractDataModel.addName(this as PropertyDefinitions<DataModel<Any, PropertyDefinitions<Any>>>) {
                        it.name
                    }
                }
            }
    ) {
        override fun invoke(map: Map<Int, *>) = object : DataModel<Any, PropertyDefinitions<Any>>(
                properties = object : PropertyDefinitions<Any>(){
                    init {
                        (map[0] as List<TypedValue<IsPropertyDefinitionWrapper<*, IsPropertyContext, Any>>>).forEach {
                            add(it.value)
                        }
                    }
                },
                name = map[1] as String
        ){
            override fun invoke(map: Map<Int, *>): Any {
                return object : Any(){}
            }
        }
    }
}