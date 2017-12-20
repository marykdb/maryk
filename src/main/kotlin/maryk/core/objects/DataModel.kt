package maryk.core.objects

import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.types.TypedValue

/** DataModel for non contextual models
 * @param properties: All definitions for properties contained in this model
 * @param DO: Type of DataObject contained
 * @param P: PropertyDefinitions type for reference retrieval
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
                        (map[0] as List<TypedValue<IsPropertyDefinitionWrapper<*, *, Any>>>).forEach {
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