package maryk.core.models.definitions

import maryk.core.definitions.MarykPrimitive
import maryk.core.definitions.PrimitiveType.RootModel
import maryk.core.exceptions.DefNotFoundException
import maryk.core.exceptions.SerializationException
import maryk.core.models.addProperties
import maryk.core.models.serializers.ObjectDataModelSerializer
import maryk.core.models.serializers.readDataModelJson
import maryk.core.models.serializers.writeDataModelJson
import maryk.core.properties.DefinitionModel
import maryk.core.properties.IsDataModelPropertyDefinitions
import maryk.core.properties.IsRootModel
import maryk.core.properties.IsValuesPropertyDefinitions
import maryk.core.properties.MutablePropertyDefinitions
import maryk.core.properties.MutableRootModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.PropertyDefinitionsCollectionDefinitionWrapper
import maryk.core.properties.definitions.InternalMultiTypeDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.index.IndexKeyPartType
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.properties.definitions.index.UUIDKey
import maryk.core.properties.definitions.index.calculateKeyIndices
import maryk.core.properties.definitions.index.checkKeyDefinitionAndCountBytes
import maryk.core.properties.definitions.index.mapOfIndexKeyPartDefinitions
import maryk.core.properties.definitions.internalMultiType
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.string
import maryk.core.properties.definitions.valueObject
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.Version
import maryk.core.properties.types.numeric.UInt32
import maryk.core.query.ContainsDefinitionsContext
import maryk.core.query.DefinitionsConversionContext
import maryk.core.values.MutableValueItems
import maryk.core.values.ObjectValues
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken
import maryk.json.PresetJsonTokenReader
import maryk.yaml.IsYamlReader

/**
 * DataModel defining data objects which is on root level, so it can be stored and thus can have a key.
 * The key is defined by passing an ordered array of key definitions.
 * If no key is defined the data model will get a UUID.
 *
 * The dataModel can be referenced by the [name] and the properties are defined by a [properties]
 */
class RootDataModelDefinition<DM : IsValuesPropertyDefinitions>(
    override val keyDefinition: IsIndexable = UUIDKey,
    override val version: Version = Version(1),
    override val indices: List<IsIndexable>? = null,
    override val reservedIndices: List<UInt>? = null,
    override val reservedNames: List<String>? = null,
    properties: DM,
    override val name: String = properties::class.simpleName ?: throw DefNotFoundException("Class $properties has no name")
) : BaseDataModelDefinition<DM>(properties),
    IsRootDataModelDefinition<DM>,
    MarykPrimitive {
    override val primitiveType = RootModel

    override val keyByteSize = checkKeyDefinitionAndCountBytes(keyDefinition)
    override val keyIndices = calculateKeyIndices(keyDefinition)

    override val orderedIndices: List<IsIndexable>? = indices?.sortedBy { it.referenceStorageByteArray }

    internal object InternalModel :
        DefinitionModel<RootDataModelDefinition<*>>(),
        IsDataModelPropertyDefinitions<RootDataModelDefinition<*>, PropertyDefinitionsCollectionDefinitionWrapper<RootDataModelDefinition<*>>> {
        override val name by string(1u, RootDataModelDefinition<*>::name)
        override val properties = addProperties(true, this as ObjectPropertyDefinitions<RootDataModelDefinition<*>>)
        val version by valueObject(
            index = 3u,
            dataModel = Version,
            default = Version(1),
            getter = RootDataModelDefinition<*>::version
        )
        val key by internalMultiType(
            index = 4u,
            typeEnum = IndexKeyPartType,
            definitionMap = mapOfIndexKeyPartDefinitions,
            getter = RootDataModelDefinition<*>::keyDefinition,
            toSerializable = { value: IsIndexable?, _: ContainsDefinitionsContext? ->
                value?.let { TypedValue(value.indexKeyPartType, value) }
            },
            fromSerializable = { value: TypedValue<IndexKeyPartType<IsIndexable>, Any>? -> value?.value as IsIndexable }
        )
        val indices by list(
            index = 5u,
            getter = RootDataModelDefinition<*>::indices,
            valueDefinition = InternalMultiTypeDefinition(
                typeEnum = IndexKeyPartType,
                definitionMap = mapOfIndexKeyPartDefinitions
            ),
            toSerializable = { value: IsIndexable ->
                value.let { TypedValue(it.indexKeyPartType, it) }
            },
            fromSerializable = { value: TypedValue<IndexKeyPartType<IsIndexable>, Any> ->
                value.let { it.value as IsIndexable }
            }
        )
        val reservedIndices by list(
            index = 6u,
            getter = RootDataModelDefinition<*>::reservedIndices,
            valueDefinition = NumberDefinition(
                type = UInt32,
                minValue = 1u
            )
        )
        val reservedNames by list(
            index = 7u,
            getter = RootDataModelDefinition<*>::reservedNames,
            valueDefinition = StringDefinition()
        )

        override fun invoke(values: ObjectValues<RootDataModelDefinition<*>, ObjectPropertyDefinitions<RootDataModelDefinition<*>>>) =
            RootDataModelDefinition(
                name = values(1u),
                properties = values(2u),
                version = values(3u),
                keyDefinition = values(4u) ?: UUIDKey,
                indices = values(5u),
                reservedIndices = values(6u),
                reservedNames = values(7u)
            ).apply {
                @Suppress("UNCHECKED_CAST")
                (properties as MutablePropertyDefinitions<*, IsRootModel>)._model = this as IsValuesDataModel<IsRootModel>
            }

        override val Serializer = object: ObjectDataModelSerializer<RootDataModelDefinition<*>, ObjectPropertyDefinitions<RootDataModelDefinition<*>>, ContainsDefinitionsContext, ContainsDefinitionsContext>(this){
            override fun writeJson(
                values: ObjectValues<RootDataModelDefinition<*>, ObjectPropertyDefinitions<RootDataModelDefinition<*>>>,
                writer: IsJsonLikeWriter,
                context: ContainsDefinitionsContext?
            ) {
                throw SerializationException("Cannot write definitions from Values")
            }

            override fun writeObjectAsJson(
                obj: RootDataModelDefinition<*>,
                writer: IsJsonLikeWriter,
                context: ContainsDefinitionsContext?,
                skip: List<IsDefinitionWrapper<*, *, *, RootDataModelDefinition<*>>>?
            ) {
                this.writeDataModelJson(writer, context, obj, InternalModel)
            }

            /**
             * Overridden to handle earlier definition of keys compared to Properties
             */
            override fun walkJsonToRead(
                reader: IsJsonLikeReader,
                values: MutableValueItems,
                context: ContainsDefinitionsContext?
            ) {
                var keyDefinitionToReadLater: List<JsonToken>? = null
                var indicesToReadLater: List<JsonToken>? = null

                readDataModelJson(
                    context,
                    reader,
                    values,
                    InternalModel,
                    ::MutableRootModel
                ) { definition ->
                    when (definition) {
                        key -> {
                            keyDefinitionToReadLater = mutableListOf<JsonToken>().apply {
                                reader.skipUntilNextField(::add)
                            }
                            true
                        }
                        indices -> {
                            indicesToReadLater = mutableListOf<JsonToken>().apply {
                                reader.skipUntilNextField(::add)
                            }
                            true
                        }
                        else -> false
                    }
                }

                readDelayed(keyDefinitionToReadLater, key, reader, values, context)
                readDelayed(indicesToReadLater, indices, reader, values, context)
            }

            private fun readDelayed(
                tokensToReadLater: List<JsonToken>?,
                propertyDefinitionWrapper: IsDefinitionWrapper<*, *, DefinitionsConversionContext, *>,
                reader: IsJsonLikeReader,
                values: MutableValueItems,
                context: ContainsDefinitionsContext?
            ) {
                tokensToReadLater?.let { jsonTokens ->
                    val lateReader = if (reader is IsYamlReader) {
                        jsonTokens.map { reader.pushToken(it) }
                        reader.pushToken(reader.currentToken)
                        reader.nextToken()
                        reader
                    } else {
                        PresetJsonTokenReader(jsonTokens)
                    }

                    values[propertyDefinitionWrapper.index] =
                        propertyDefinitionWrapper.readJson(lateReader, context as DefinitionsConversionContext?)

                    if (reader is IsYamlReader) {
                        reader.nextToken()
                    }
                }
            }
        }

    }

    companion object {
        val Model: DefinitionModel<RootDataModelDefinition<*>> = InternalModel
    }
}
