package maryk.core.definitions

import maryk.core.definitions.Operation.Define
import maryk.core.definitions.PrimitiveType.EnumDefinition
import maryk.core.definitions.PrimitiveType.Model
import maryk.core.definitions.PrimitiveType.RootModel
import maryk.core.definitions.PrimitiveType.ValueModel
import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.DataModel
import maryk.core.models.RootDataModel
import maryk.core.models.SingleValueDataModel
import maryk.core.models.ValueDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.IsSubDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.contextual.ContextCaptureDefinition
import maryk.core.properties.definitions.contextual.ContextValueTransformDefinition
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.types.TypedValue
import maryk.core.query.ContainsDefinitionsContext
import maryk.core.query.requests.IsOperation
import maryk.core.values.ObjectValues
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken.EndObject
import maryk.json.JsonToken.FieldName
import maryk.json.JsonToken.StartObject
import maryk.lib.exceptions.ParseException

/**
 * Contains multiple definitions of models and enums. Is passed MarykPrimitives like
 * DataModels and Enums to [definitions]
 */
data class Definitions(
    val definitions: List<MarykPrimitive>
) : IsOperation {
    override val operationType = Define

    constructor(vararg definition: MarykPrimitive) : this(definition.toList())

    internal object Properties : ObjectPropertyDefinitions<Definitions>() {
        @Suppress("UNCHECKED_CAST")
        val definitions = add(1u, "definitions",
            ListDefinition(
                valueDefinition = MultiTypeDefinition(
                    typeEnum = PrimitiveType,
                    definitionMap = mapOf(
                        Model to ContextCaptureDefinition(
                            definition = EmbeddedObjectDefinition(
                                dataModel = { DataModel.Model }
                            ),
                            capturer = { context, model ->
                                context?.let {
                                    it.dataModels[model.name] = { model }
                                } ?: throw ContextNotFoundException()
                            }
                        ),
                        ValueModel to ContextCaptureDefinition(
                            definition = EmbeddedObjectDefinition(
                                dataModel = { ValueDataModel.Model }
                            ),
                            capturer = { context, model ->
                                context?.let {
                                    it.dataModels[model.name] = { model }
                                } ?: throw ContextNotFoundException()
                            }
                        ),
                        RootModel to ContextCaptureDefinition(
                            definition = EmbeddedObjectDefinition(
                                dataModel = { RootDataModel.Model }
                            ),
                            capturer = { context: ContainsDefinitionsContext?, model ->
                                context?.let {
                                    it.dataModels[model.name] = { model }
                                } ?: throw ContextNotFoundException()
                            }
                        ),
                        EnumDefinition to ContextCaptureDefinition(
                            // This transformer takes care to catch Enums without cases to replace them
                            // with previously defined Enums which are stored in the context
                            definition = ContextValueTransformDefinition(
                                definition = EmbeddedObjectDefinition(
                                    dataModel = { IndexedEnumDefinition.Model }
                                ),
                                valueTransformer = { context, value ->
                                    if (value.optionalCases == null) {
                                        context?.let {
                                            it.enums[value.name] ?: throw ParseException("Enum ${value.name} has not been defined")
                                        } ?: throw ContextNotFoundException()
                                    } else {
                                        value
                                    }
                                }
                            ),
                            capturer = { context, value ->
                                context?.let {
                                    it.enums[value.name] = value
                                } ?: throw ContextNotFoundException()
                            }
                        )
                    ) as Map<PrimitiveType, IsSubDefinition<out Any, ContainsDefinitionsContext>>
                )
            ) as ListDefinition<TypedValue<PrimitiveType, MarykPrimitive>, ContainsDefinitionsContext>,
            Definitions::definitions,
            fromSerializable = { it.value },
            toSerializable = { TypedValue(it.primitiveType, it) }
        )
    }

    internal companion object :
        SingleValueDataModel<List<TypedValue<PrimitiveType, MarykPrimitive>>, List<MarykPrimitive>, Definitions, Properties, ContainsDefinitionsContext>(
            properties = Properties,
            singlePropertyDefinition = Properties.definitions as IsPropertyDefinitionWrapper<List<TypedValue<PrimitiveType, MarykPrimitive>>, List<MarykPrimitive>, ContainsDefinitionsContext, Definitions>
        ) {
        override fun invoke(values: ObjectValues<Definitions, Properties>) = Definitions(
            definitions = values(1u)
        )

        override fun writeJsonValue(
            value: List<TypedValue<PrimitiveType, MarykPrimitive>>,
            writer: IsJsonLikeWriter,
            context: ContainsDefinitionsContext?
        ) {
            writer.writeStartObject()
            for (item in value) {
                writer.writeFieldName(item.value.name)
                context?.currentDefinitionName = item.value.name
                Properties.definitions.valueDefinition.writeJsonValue(item, writer, context)
            }
            writer.writeEndObject()
        }

        override fun readJsonValue(
            reader: IsJsonLikeReader,
            context: ContainsDefinitionsContext?
        ): List<TypedValue<PrimitiveType, MarykPrimitive>> {
            if (reader.currentToken !is StartObject) {
                throw ParseException("JSON value should be an Object")
            }
            val definitions = mutableListOf<TypedValue<PrimitiveType, MarykPrimitive>>()

            while (reader.nextToken() !== EndObject) {
                reader.currentToken.apply {
                    if (this is FieldName) {
                        if (context == null) throw ContextNotFoundException()
                        context.currentDefinitionName = this.value ?: throw ParseException("Map key cannot be null")

                        reader.nextToken()
                        definitions.add(
                            Properties.definitions.valueDefinition.readJson(reader, context)
                        )
                    } else {
                        throw ParseException("JSON value should be an Object Field but was ${this.name}")
                    }
                }
            }
            return definitions
        }
    }
}
