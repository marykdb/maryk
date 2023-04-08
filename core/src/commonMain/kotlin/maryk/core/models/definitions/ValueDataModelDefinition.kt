package maryk.core.models.definitions

import maryk.core.definitions.MarykPrimitiveDescriptor
import maryk.core.definitions.PrimitiveType.ValueModel
import maryk.core.exceptions.SerializationException
import maryk.core.models.DefinitionModel
import maryk.core.models.IsDataModelWithPropertyDefinitions
import maryk.core.models.IsObjectDataModel
import maryk.core.models.MutableValueDataModel
import maryk.core.models.ObjectDataModelCollectionDefinitionWrapper
import maryk.core.models.addProperties
import maryk.core.models.invoke
import maryk.core.models.serializers.ObjectDataModelSerializer
import maryk.core.models.serializers.readDataModelJson
import maryk.core.models.serializers.writeDataModelJson
import maryk.core.properties.definitions.string
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.types.ValueDataObject
import maryk.core.query.ContainsDefinitionsContext
import maryk.core.values.MutableValueItems
import maryk.core.values.ObjectValues
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter

typealias AnyValueDataModel = ValueDataModelDefinition<*, *>

/**
 * ObjectDataModel of type [DO] for objects that can be encoded in fixed length width.
 * Contains [properties] definitions.
 */
abstract class ValueDataModelDefinition<DO : ValueDataObject, DM : IsObjectDataModel<DO>>(
    name: String,
    properties: DM
) : ObjectDataModelDefinition<DO, DM>(name, properties), MarykPrimitiveDescriptor {
    override val primitiveType = ValueModel

    internal object Model :
        DefinitionModel<AnyValueDataModel>(),
        IsDataModelWithPropertyDefinitions<AnyValueDataModel, ObjectDataModelCollectionDefinitionWrapper<AnyValueDataModel>> {
        override val name by string(1u, ValueDataModelDefinition<*, *>::name)
        override val properties = addProperties(this)

        override fun invoke(values: ObjectValues<ValueDataModelDefinition<*, *>, IsObjectDataModel<ValueDataModelDefinition<*, *>>>) = object : ValueDataModelDefinition<ValueDataObject, IsObjectDataModel<ValueDataObject>>(
            name = values(1u),
            properties = values(2u)
        ) {}.apply {
            (properties as MutableValueDataModel<ValueDataObject>)._model = this
        }

        override val Serializer = object: ObjectDataModelSerializer<ValueDataModelDefinition<*, *>, IsObjectDataModel<ValueDataModelDefinition<*, *>>, ContainsDefinitionsContext, ContainsDefinitionsContext>(this) {
            override fun writeJson(
                values: ObjectValues<AnyValueDataModel, IsObjectDataModel<AnyValueDataModel>>,
                writer: IsJsonLikeWriter,
                context: ContainsDefinitionsContext?
            ) {
                throw SerializationException("Cannot write definitions from values")
            }

            override fun writeObjectAsJson(
                obj: ValueDataModelDefinition<*, *>,
                writer: IsJsonLikeWriter,
                context: ContainsDefinitionsContext?,
                skip: List<IsDefinitionWrapper<*, *, *, ValueDataModelDefinition<*, *>>>?
            ) {
                this.writeDataModelJson(writer, context, obj, Model)
            }

            override fun walkJsonToRead(
                reader: IsJsonLikeReader,
                values: MutableValueItems,
                context: ContainsDefinitionsContext?
            ) {
                readDataModelJson(
                    context, reader, values,
                    properties = Model,
                    propertyDefinitionsCreator = {
                        MutableValueDataModel<ValueDataObject>()
                    }
                )
            }
        }
    }
}
