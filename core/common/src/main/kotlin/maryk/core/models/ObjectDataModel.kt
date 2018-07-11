package maryk.core.models

import maryk.core.definitions.MarykPrimitive
import maryk.core.definitions.PrimitiveType
import maryk.core.objects.SimpleObjectValues
import maryk.core.properties.ObjectPropertyDefinitions

/**
 * ObjectDataModel for non contextual models. Contains a [name] to identify the model and [properties] which define how the
 * properties should be validated. It models the DataObjects of type [DO] which can be validated. And it contains a
 * reference to the propertyDefinitions of type [P] which can be used for the references to the properties.
 */
abstract class ObjectDataModel<DO: Any, P: ObjectPropertyDefinitions<DO>>(
    override val name: String,
    properties: P
) : SimpleDataModel<DO, P>(
    properties
), MarykPrimitive, IsNamedDataModel<P> {
    override val primitiveType = PrimitiveType.Model

    internal object Model : DefinitionDataModel<ObjectDataModel<*, *>>(
        properties = object : ObjectPropertyDefinitions<ObjectDataModel<*, *>>() {
            init {
                IsNamedDataModel.addName(this) {
                    it.name
                }
                IsDataModel.addProperties(this)
            }
        }
    ) {
        override fun invoke(map: SimpleObjectValues<ObjectDataModel<*, *>>) = object : ObjectDataModel<Any, ObjectPropertyDefinitions<Any>>(
            name = map(0),
            properties = map(1)
        ) {
            override fun invoke(map: SimpleObjectValues<Any>): Any {
                // TODO: What is the right path here?
                return object : Any(){}
            }
        }
    }
}
