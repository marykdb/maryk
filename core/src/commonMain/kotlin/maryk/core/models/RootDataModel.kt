package maryk.core.models

import maryk.core.definitions.PrimitiveType
import maryk.core.properties.IsDataModelPropertyDefinitions
import maryk.core.properties.MutablePropertyDefinitions
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.PropertyDefinitionsCollectionDefinitionWrapper
import maryk.core.properties.definitions.IsFixedBytesEncodable
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.PropertyDefinitionType
import maryk.core.properties.definitions.key.KeyPartType
import maryk.core.properties.definitions.key.UUIDKey
import maryk.core.properties.definitions.key.mapOfKeyPartDefinitions
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
abstract class RootDataModel<DM: IsRootValuesDataModel<P>, P: PropertyDefinitions>(
    name: String,
    final override val keyDefinitions: Array<IsFixedBytesPropertyReference<out Any>> = arrayOf(UUIDKey),
    properties: P
) : DataModel<DM, P>(name, properties), IsTypedRootDataModel<DM, P>, IsRootValuesDataModel<P> {
    override val primitiveType = PrimitiveType.RootModel

    final override val keySize = IsRootDataModel.calculateKeySize(keyDefinitions)
    final override val keyIndices: IntArray

    init {
        var index = 0
        // Add indices to array. Also account for the 1 sized separator
        keyIndices = keyDefinitions.map { def -> index.also { index += def.propertyDefinition.byteSize + 1 } }.toIntArray()
    }

    @Suppress("UNCHECKED_CAST")
    private object RootModelProperties:
        ObjectPropertyDefinitions<RootDataModel<*, *>>(),
        IsDataModelPropertyDefinitions<RootDataModel<*, *>, PropertyDefinitionsCollectionDefinitionWrapper<RootDataModel<*, *>>>
    {
        override val name = IsNamedDataModel.addName(this as ObjectPropertyDefinitions<RootDataModel<*, *>>, RootDataModel<*, *>::name)
        override val properties = DataModel.addProperties(this as ObjectPropertyDefinitions<RootDataModel<*, *>>)
        val key = add(3, "key",
            ListDefinition(
                valueDefinition = MultiTypeDefinition(
                    typeEnum = KeyPartType,
                    definitionMap = mapOfKeyPartDefinitions
                )
            ),
            getter = { rootDataModel ->
                rootDataModel.keyDefinitions.map { keyDef ->
                    TypedValue(keyDef.keyPartType, keyDef)
                }
            }
        )
    }

    @Suppress("UNCHECKED_CAST")
    object Model : AbstractObjectDataModel<RootDataModel<*, *>, ObjectPropertyDefinitions<RootDataModel<*, *>>, ContainsDefinitionsContext, ContainsDefinitionsContext>(
        properties = RootModelProperties
    ) {
        override fun invoke(values: ObjectValues<RootDataModel<*, *>, ObjectPropertyDefinitions<RootDataModel<*, *>>>) = object : RootDataModelImpl(
            name = values(1),
            properties = values(2),
            keyDefinitions = (values<List<TypedValue<PropertyDefinitionType, *>>?>(3))?.map {
                it.value as IsFixedBytesPropertyReference<*>
            }?.toTypedArray() ?: arrayOf(UUIDKey) as Array<IsFixedBytesPropertyReference<out Any>>
        ){}

        override fun writeJson(
            values: ObjectValues<RootDataModel<*, *>, ObjectPropertyDefinitions<RootDataModel<*, *>>>,
            writer: IsJsonLikeWriter,
            context: ContainsDefinitionsContext?
        ) {
            throw Exception("Cannot write definitions from Values")
        }

        /**
         * Overridden to handle earlier definition of keys compared to Properties
         */
        override fun writeJson(obj: RootDataModel<*, *>, writer: IsJsonLikeWriter, context: ContainsDefinitionsContext?) {
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
            var keyDefinitionsToProcessLater: List<JsonToken>? = null

            readDataModelJson(
                context,
                reader,
                values,
                RootModelProperties,
                ::MutablePropertyDefinitions
            ) { definition ->
                when (definition) {
                    RootModelProperties.key -> {
                        val collectedTokens = mutableListOf<JsonToken>()

                        reader.skipUntilNextField {
                            collectedTokens.add(it)
                        }

                        keyDefinitionsToProcessLater = collectedTokens
                        true
                    }
                    else -> false
                }
            }

            keyDefinitionsToProcessLater?.let { jsonTokens ->
                val lateReader = if (reader is IsYamlReader) {
                    jsonTokens.map { reader.pushToken(it) }
                    reader.pushToken(reader.currentToken)
                    reader.nextToken()
                    reader
                } else {
                    PresetJsonTokenReader(jsonTokens)
                }

                values[RootModelProperties.key.index] = RootModelProperties.key.readJson(lateReader, context as DefinitionsConversionContext?)

                if (reader is IsYamlReader) {
                    reader.nextToken()
                }
            }
        }
    }
}

/** Get Key based on [values] */
@Suppress("UNCHECKED_CAST")
fun <DM: IsRootValuesDataModel<P>, P: PropertyDefinitions> DM.key(values: Values<DM, P>): Key<DM> {
    val bytes = ByteArray(this.keySize)
    var index = 0
    for (it in this.keyDefinitions) {
        val value = it.getValue(values)

        (it.propertyDefinition as IsFixedBytesEncodable<Any>).writeStorageBytes(value) {
            bytes[index++] = it
        }

        // Add separator
        if (index < this.keySize) {
            bytes[index++] = 1
        }
    }
    return Key(bytes)
}
