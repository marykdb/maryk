package maryk.core.models

import maryk.core.definitions.PrimitiveType
import maryk.core.exceptions.SerializationException
import maryk.core.properties.IsDataModelPropertyDefinitions
import maryk.core.properties.MutablePropertyDefinitions
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.PropertyDefinitionsCollectionDefinitionWrapper
import maryk.core.properties.definitions.IsFixedBytesEncodable
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.index.IndexKeyPartType
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.properties.definitions.index.Multiple
import maryk.core.properties.definitions.index.UUIDKey
import maryk.core.properties.definitions.index.calculateKeyIndices
import maryk.core.properties.definitions.index.checkKeyDefinitionAndCountBytes
import maryk.core.properties.definitions.index.mapOfIndexKeyPartDefinitions
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.references.IsFixedBytesPropertyReference
import maryk.core.properties.types.Key
import maryk.core.properties.types.TypedValue
import maryk.core.query.ContainsDefinitionsContext
import maryk.core.query.DefinitionsConversionContext
import maryk.core.values.MutableValueItems
import maryk.core.values.ObjectValues
import maryk.core.values.Values
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken
import maryk.json.PresetJsonTokenReader
import maryk.yaml.IsYamlReader

typealias RootDataModelImpl = RootDataModel<IsRootValuesDataModel<PropertyDefinitions>, PropertyDefinitions>

/**
 * DataModel defining data objects of model of type [DM] which is on root level so it can be stored and thus can have a [key].
 * The key is defined by passing an ordered array of key definitions.
 * If no key is defined the data model will get a UUID.
 *
 * The dataModel can be referenced by the [name] and the properties are defined by a [properties]
 */
abstract class RootDataModel<DM : IsRootValuesDataModel<P>, P : PropertyDefinitions>(
    final override val keyDefinition: IsIndexable = UUIDKey,
    final override val indices: List<IsIndexable>? = null,
    properties: P
) : DataModel<DM, P>(properties), IsTypedRootDataModel<DM, P>, IsRootValuesDataModel<P> {
    override val primitiveType = PrimitiveType.RootModel

    override val keyByteSize = checkKeyDefinitionAndCountBytes(keyDefinition)
    override val keyIndices = calculateKeyIndices(keyDefinition)

    private object RootModelProperties :
        ObjectPropertyDefinitions<RootDataModel<*, *>>(),
        IsDataModelPropertyDefinitions<RootDataModel<*, *>, PropertyDefinitionsCollectionDefinitionWrapper<RootDataModel<*, *>>> {
        override val name =
            IsNamedDataModel.addName(this as ObjectPropertyDefinitions<RootDataModel<*, *>>, RootDataModel<*, *>::name)
        override val properties = DataModel.addProperties(this as ObjectPropertyDefinitions<RootDataModel<*, *>>)
        val key = add(
            3, "key",
            MultiTypeDefinition(
                typeEnum = IndexKeyPartType,
                definitionMap = mapOfIndexKeyPartDefinitions
            ),
            toSerializable = { value: IsIndexable?, _: ContainsDefinitionsContext? ->
                value?.let { TypedValue(value.indexKeyPartType, value) }
            },
            fromSerializable = { value: TypedValue<IndexKeyPartType, Any>? -> value?.value as IsIndexable },
            getter = RootDataModel<*, *>::keyDefinition
        )
        val indices = add(
            4, "indices",
            ListDefinition(
                valueDefinition = MultiTypeDefinition(
                    typeEnum = IndexKeyPartType,
                    definitionMap = mapOfIndexKeyPartDefinitions
                )
            ),
            toSerializable = { value: IsIndexable ->
                value.let { TypedValue(it.indexKeyPartType, it) }
            },
            fromSerializable = { value: TypedValue<IndexKeyPartType, Any> ->
                value.let { it.value as IsIndexable }
            },
            getter = RootDataModel<*, *>::indices
        )
    }

    object Model :
        AbstractObjectDataModel<RootDataModel<*, *>, ObjectPropertyDefinitions<RootDataModel<*, *>>, ContainsDefinitionsContext, ContainsDefinitionsContext>(
            properties = RootModelProperties
        ) {
        override fun invoke(values: ObjectValues<RootDataModel<*, *>, ObjectPropertyDefinitions<RootDataModel<*, *>>>) =
            object : RootDataModelImpl(
                properties = values(2),
                keyDefinition = values(3) ?: UUIDKey,
                indices = values(4)
            ) {
                override val name: String = values(1)
            }

        override fun writeJson(
            values: ObjectValues<RootDataModel<*, *>, ObjectPropertyDefinitions<RootDataModel<*, *>>>,
            writer: IsJsonLikeWriter,
            context: ContainsDefinitionsContext?
        ) {
            throw SerializationException("Cannot write definitions from Values")
        }

        /**
         * Overridden to handle earlier definition of keys compared to Properties
         */
        override fun writeJson(
            obj: RootDataModel<*, *>,
            writer: IsJsonLikeWriter,
            context: ContainsDefinitionsContext?
        ) {
            this.writeDataModelJson(writer, context, obj, RootModelProperties)
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
                RootModelProperties,
                ::MutablePropertyDefinitions
            ) { definition ->
                when (definition) {
                    RootModelProperties.key -> {
                        keyDefinitionToReadLater = mutableListOf<JsonToken>().apply {
                            reader.skipUntilNextField { add(it) }
                        }
                        true
                    }
                    RootModelProperties.indices -> {
                        indicesToReadLater = mutableListOf<JsonToken>().apply {
                            reader.skipUntilNextField { add(it) }
                        }
                        true
                    }
                    else -> false
                }
            }

            readDelayed(keyDefinitionToReadLater, RootModelProperties.key, reader, values, context)
            readDelayed(indicesToReadLater, RootModelProperties.indices, reader, values, context)
        }

        private fun readDelayed(
            tokensToReadLater: List<JsonToken>?,
            propertyDefinitionWrapper: IsPropertyDefinitionWrapper<*, *, DefinitionsConversionContext, *>,
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

/** Get Key based on [values] */
fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> DM.key(values: Values<DM, P>): Key<DM> {
    val bytes = ByteArray(this.keyByteSize)
    var index = 0
    when (val keyDef = this.keyDefinition) {
        is Multiple -> {
            keyDef.writeStorageBytes(values) {
                bytes[index++] = it
            }
        }
        is IsFixedBytesPropertyReference<*> -> {
            val value = keyDef.getValue(values)

            @Suppress("UNCHECKED_CAST")
            (keyDef as IsFixedBytesEncodable<Any>).writeStorageBytes(value) {
                bytes[index++] = it
            }
        }
    }

    return Key(bytes)
}
