package maryk.core.properties

import maryk.core.models.IsDataModelPropertiesCollectionDefinition
import maryk.core.models.IsTypedDataModel
import maryk.core.models.IsTypedObjectDataModel
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.IsCollectionDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.wrapper.AnyTypedDefinitionWrapper
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.DefinitionsConversionContext
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken
import maryk.lib.exceptions.ParseException
import maryk.yaml.IsYamlReader
import maryk.yaml.YamlWriter

/**
 * A Definition which defines a collection of property definitions
 * Useful to serialize and deserialize a collection of property definitions
 */
data class PropertiesCollectionDefinition<DO: Any>(
    override val capturer: Unit.(DefinitionsConversionContext?, IsTypedDataModel<DO>) -> Unit
) : IsCollectionDefinition<
        AnyTypedDefinitionWrapper<DO>,
        IsTypedDataModel<DO>,
        DefinitionsConversionContext,
        EmbeddedObjectDefinition<
                AnyTypedDefinitionWrapper<DO>,
                IsTypedObjectDataModel<AnyTypedDefinitionWrapper<DO>, *, IsPropertyContext, IsPropertyContext>,
                IsPropertyContext,
                IsPropertyContext
                >
        >, IsDataModelPropertiesCollectionDefinition<IsTypedDataModel<DO>> {
    override val required = true
    override val final = true
    override val minSize: UInt? = null
    override val maxSize: UInt? = null

    override val valueDefinition = EmbeddedObjectDefinition(
        dataModel = {
            @Suppress("UNCHECKED_CAST")
            IsDefinitionWrapper.Model as IsTypedObjectDataModel<AnyTypedDefinitionWrapper<DO>, *, IsPropertyContext, IsPropertyContext>
        }
    )

    override fun validateCollectionForExceptions(
        refGetter: () -> IsPropertyReference<IsTypedDataModel<DO>, IsPropertyDefinition<IsTypedDataModel<DO>>, *>?,
        newValue: IsTypedDataModel<DO>,
        validator: (item: AnyTypedDefinitionWrapper<DO>, itemRefFactory: () -> IsPropertyReference<AnyTypedDefinitionWrapper<DO>, IsPropertyDefinition<AnyTypedDefinitionWrapper<DO>>, *>?) -> Any
    ) {}

    override fun newMutableCollection(context: DefinitionsConversionContext?): MutablePropertiesCollection<DO> =
        MutablePropertiesCollection<DO>().apply {
            capturer(Unit, context, this)
        }

    /**
     * Overridden to render definitions list in YAML as objects
     */
    override fun writeJsonValue(
        value: IsTypedDataModel<DO>,
        writer: IsJsonLikeWriter,
        context: DefinitionsConversionContext?
    ) {
        if (writer is YamlWriter) {
            writer.writeStartObject()
            for (it in value) {
                valueDefinition.writeJsonValue(it, writer, context)
            }
            writer.writeEndObject()
        } else {
            super.writeJsonValue(value, writer, context)
        }
    }

    override fun readJson(reader: IsJsonLikeReader, context: DefinitionsConversionContext?): IsTypedDataModel<DO> {
        return if (reader is IsYamlReader) {
            if (reader.currentToken !is JsonToken.StartObject) {
                throw ParseException("Property definitions should be an Object")
            }
            val collection = newMutableCollection(context)

            while (reader.nextToken() !== JsonToken.EndObject) {
                collection.add(
                    valueDefinition.readJson(reader, context)
                )
            }
            collection
        } else {
            super.readJson(reader, context)
        }
    }
}
