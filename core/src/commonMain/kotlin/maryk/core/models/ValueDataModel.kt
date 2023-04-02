package maryk.core.models

import maryk.core.definitions.MarykPrimitive
import maryk.core.definitions.PrimitiveType.ValueModel
import maryk.core.exceptions.SerializationException
import maryk.core.models.serializers.ObjectDataModelSerializer
import maryk.core.models.serializers.readDataModelJson
import maryk.core.models.serializers.writeDataModelJson
import maryk.core.properties.DefinitionModel
import maryk.core.properties.IsDataModelPropertyDefinitions
import maryk.core.properties.IsObjectPropertyDefinitions
import maryk.core.properties.MutableValueModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.ObjectPropertyDefinitionsCollectionDefinitionWrapper
import maryk.core.properties.definitions.IsFixedStorageBytesEncodable
import maryk.core.properties.definitions.string
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.invoke
import maryk.core.properties.types.ValueDataObject
import maryk.core.query.ContainsDefinitionsContext
import maryk.core.values.MutableValueItems
import maryk.core.values.ObjectValues
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter

typealias AnyValueDataModel = ValueDataModel<*, *>

/**
 * ObjectDataModel of type [DO] for objects that can be encoded in fixed length width.
 * Contains [properties] definitions.
 */
abstract class ValueDataModel<DO : ValueDataObject, DM : IsObjectPropertyDefinitions<DO>>(
    name: String,
    properties: DM
) : ObjectDataModel<DO, DM>(name, properties), MarykPrimitive {
    override val primitiveType = ValueModel

    internal val byteSize by lazy {
        var size = -1
        for (it in this.properties) {
            val def = it.definition as IsFixedStorageBytesEncodable<*>
            size += def.byteSize + 1
        }
        size
    }

    internal object Model :
        DefinitionModel<AnyValueDataModel>(),
        IsDataModelPropertyDefinitions<AnyValueDataModel, ObjectPropertyDefinitionsCollectionDefinitionWrapper<AnyValueDataModel>> {
        override val name by string(1u, ValueDataModel<*, *>::name)
        override val properties = addProperties(this)

        override fun invoke(values: ObjectValues<ValueDataModel<*, *>, ObjectPropertyDefinitions<ValueDataModel<*, *>>>) = object : ValueDataModel<ValueDataObject, ObjectPropertyDefinitions<ValueDataObject>>(
            name = values(1u),
            properties = values(2u)
        ) {}.apply {
            (properties as MutableValueModel<ValueDataObject>)._model = this
        }

        override val Serializer = object: ObjectDataModelSerializer<ValueDataModel<*, *>, ObjectPropertyDefinitions<ValueDataModel<*, *>>, ContainsDefinitionsContext, ContainsDefinitionsContext>(this) {
            override fun writeJson(
                values: ObjectValues<AnyValueDataModel, ObjectPropertyDefinitions<AnyValueDataModel>>,
                writer: IsJsonLikeWriter,
                context: ContainsDefinitionsContext?
            ) {
                throw SerializationException("Cannot write definitions from values")
            }

            override fun writeObjectAsJson(
                obj: ValueDataModel<*, *>,
                writer: IsJsonLikeWriter,
                context: ContainsDefinitionsContext?,
                skip: List<IsDefinitionWrapper<*, *, *, ValueDataModel<*, *>>>?
            ) {
                this.writeDataModelJson(writer, context, obj, ValueDataModel.Model)
            }

            override fun walkJsonToRead(
                reader: IsJsonLikeReader,
                values: MutableValueItems,
                context: ContainsDefinitionsContext?
            ) {
                readDataModelJson(
                    context, reader, values,
                    properties = ValueDataModel.Model,
                    propertyDefinitionsCreator = {
                        MutableValueModel<ValueDataObject>()
                    }
                )
            }
        }
    }
}
