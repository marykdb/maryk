package maryk.core.definitions

import maryk.core.definitions.Operation.Define
import maryk.core.definitions.PrimitiveType.EnumDefinition
import maryk.core.definitions.PrimitiveType.RootModel
import maryk.core.definitions.PrimitiveType.TypeDefinition
import maryk.core.definitions.PrimitiveType.ValueModel
import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.SingleValueDataModel
import maryk.core.models.definitions.DataModelDefinition
import maryk.core.models.definitions.RootDataModelDefinition
import maryk.core.models.definitions.ValueDataModelDefinition
import maryk.core.models.serializers.SingleValueDataModelSerializer
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.InternalMultiTypeDefinition
import maryk.core.properties.definitions.IsSubDefinition
import maryk.core.properties.definitions.contextual.ContextCaptureDefinition
import maryk.core.properties.definitions.contextual.ContextValueTransformDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.enum.MultiTypeEnumDefinition
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

    internal companion object : SingleValueDataModel<List<TypedValue<PrimitiveType, MarykPrimitive>>, List<MarykPrimitive>, Definitions, Companion, ContainsDefinitionsContext>(
        { Companion.definitions }
    ) {
        val definitions by list(
            index = 1u,
            getter = Definitions::definitions,
            valueDefinition = InternalMultiTypeDefinition(
                typeEnum = PrimitiveType,
                definitionMap = mapOf(
                    PrimitiveType.Model to ContextCaptureDefinition(
                        definition = EmbeddedObjectDefinition(
                            dataModel = { DataModelDefinition.Model }
                        ),
                        capturer = { context, model ->
                            context?.let {
                                it.dataModels[model.name] = { model.properties }
                            } ?: throw ContextNotFoundException()
                        }
                    ),
                    ValueModel to ContextCaptureDefinition(
                        definition = EmbeddedObjectDefinition(
                            dataModel = { ValueDataModelDefinition.Model }
                        ),
                        capturer = { context, model ->
                            context?.let {
                                it.dataModels[model.name] = { model.properties }
                            } ?: throw ContextNotFoundException()
                        }
                    ),
                    RootModel to ContextCaptureDefinition(
                        definition = EmbeddedObjectDefinition(
                            dataModel = { RootDataModelDefinition.Model }
                        ),
                        capturer = { context: ContainsDefinitionsContext?, model ->
                            context?.let {
                                it.dataModels[model.name] = { model.properties }
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
                    ),
                    TypeDefinition to ContextCaptureDefinition(
                        // This transformer takes care to catch Enums without cases to replace them
                        // with previously defined Enums which are stored in the context
                        definition = ContextValueTransformDefinition(
                            definition = EmbeddedObjectDefinition(
                                dataModel = { MultiTypeEnumDefinition.Model }
                            ),
                            valueTransformer = { context, value ->
                                if (value.optionalCases == null) {
                                    context?.let {
                                        it.typeEnums[value.name] ?: throw ParseException("Enum ${value.name} has not been defined")
                                    } ?: throw ContextNotFoundException()
                                } else {
                                    value
                                }
                            }
                        ),
                        capturer = { context, value ->
                            context?.let {
                                it.typeEnums[value.name] = value
                            } ?: throw ContextNotFoundException()
                        }
                    )
                ) as Map<PrimitiveType, IsSubDefinition<out Any, ContainsDefinitionsContext>>
            ),
            fromSerializable = { it.value },
            toSerializable = { TypedValue(it.Meta.primitiveType, it) }
        )

        override fun invoke(values: ObjectValues<Definitions, Companion>) = Definitions(
            definitions = values(1u)
        )

        override val Serializer = object: SingleValueDataModelSerializer<List<TypedValue<PrimitiveType, MarykPrimitive>>, List<MarykPrimitive>, Definitions, Companion, ContainsDefinitionsContext>(
            model = this,
            singlePropertyDefinitionGetter = { definitions }
        ) {
            override fun writeJsonValue(
                value: List<TypedValue<PrimitiveType, MarykPrimitive>>,
                writer: IsJsonLikeWriter,
                context: ContainsDefinitionsContext?
            ) {
                writer.writeStartObject()
                for (item in value) {
                    writer.writeFieldName(item.value.Meta.name)
                    context?.currentDefinitionName = item.value.Meta.name
                    definitions.valueDefinition.writeJsonValue(item, writer, context)
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
                                Companion.definitions.valueDefinition.readJson(reader, context)
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
}
