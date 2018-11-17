package maryk.core.models

import maryk.core.definitions.PrimitiveType
import maryk.core.values.ObjectValues
import maryk.core.values.Values
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.FixedBytesProperty
import maryk.core.properties.definitions.IsFixedBytesEncodable
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.PropertyDefinitionType
import maryk.core.properties.definitions.key.KeyPartType
import maryk.core.properties.definitions.key.UUIDKey
import maryk.core.properties.definitions.key.mapOfKeyPartDefinitions
import maryk.core.properties.definitions.wrapper.FixedBytesPropertyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.references.ValueWithFixedBytesPropertyReference
import maryk.core.properties.types.Key
import maryk.core.properties.types.TypedValue
import maryk.core.query.DefinitionsConversionContext
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken
import maryk.json.PresetJsonTokenReader
import maryk.lib.exceptions.ParseException
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
    final override val keyDefinitions: Array<FixedBytesProperty<out Any>> = arrayOf(UUIDKey),
    properties: P
) : DataModel<DM, P>(name, properties), IsTypedRootDataModel<DM, P>, IsRootValuesDataModel<P> {
    override val primitiveType = PrimitiveType.RootModel

    final override val keySize = IsRootDataModel.calculateKeySize(keyDefinitions)

    @Suppress("UNCHECKED_CAST")
    private object RootModelProperties: ObjectPropertyDefinitions<RootDataModel<*, *>>() {
        init {
            IsNamedDataModel.addName(this as ObjectPropertyDefinitions<RootDataModelImpl>) {
                it.name
            }
        }
        val properties = DataModel.addProperties(this as ObjectPropertyDefinitions<RootDataModelImpl>)
        val key = add(3, "key",
            ListDefinition(
                valueDefinition = MultiTypeDefinition(
                    typeEnum = KeyPartType,
                    definitionMap = mapOfKeyPartDefinitions
                )
            ),
            getter = { rootDataModel ->
                rootDataModel.keyDefinitions.map { keyDef ->
                    val def: Any = when(keyDef) {
                        is FixedBytesPropertyDefinitionWrapper<*, *, *, *, *> -> keyDef.getRef()
                        else -> keyDef
                    }
                    TypedValue(keyDef.keyPartType, def)
                }
            }
        )
    }

    @Suppress("UNCHECKED_CAST")
    object Model : SimpleObjectDataModel<RootDataModel<*, *>, ObjectPropertyDefinitions<RootDataModel<*, *>>>(
        properties = RootModelProperties
    ) {
        override fun invoke(map: ObjectValues<RootDataModel<*, *>, ObjectPropertyDefinitions<RootDataModel<*, *>>>) = object : RootDataModelImpl(
            name = map(1),
            properties = map(2),
            keyDefinitions = (map<List<TypedValue<PropertyDefinitionType, *>>?>(3))?.map {
                when(it.value) {
                    is ValueWithFixedBytesPropertyReference<*, *, *, *> -> it.value.propertyDefinition
                    else -> it.value as FixedBytesProperty<*>
                }
            }?.toTypedArray() ?: arrayOf(UUIDKey) as Array<FixedBytesProperty<out Any>>
        ){}

        /**
         * Overridden to handle earlier definition of keys compared to Properties
         */
        override fun writeJson(obj: RootDataModel<*, *>, writer: IsJsonLikeWriter, context: IsPropertyContext?) {
            writer.writeStartObject()
            for (def in this.properties) {
                if (def == RootModelProperties.properties) continue // skip properties to write last

                val value = def.getPropertyAndSerialize(obj, context) ?: continue
                this.writeJsonValue(def, writer, value, context)
            }
            this.writeJsonValue(
                RootModelProperties.properties as IsPropertyDefinitionWrapper<Any, Any, IsPropertyContext, RootDataModel<*, *>>,
                writer,
                obj.properties,
                context
            )
            writer.writeEndObject()
        }

        /**
         * Overridden to handle earlier definition of keys compared to Properties
         */
        override fun walkJsonToRead(
            reader: IsJsonLikeReader,
            valueMap: MutableMap<Int, Any>,
            context: IsPropertyContext?
        ) {
            var keyDefinitionsToProcessLater: List<JsonToken>? = null
            var propertiesAreProcessed = false

            walker@ do {
                val token = reader.currentToken
                when (token) {
                    is JsonToken.FieldName -> {
                        val value = token.value ?: throw ParseException("Empty field name not allowed in JSON")

                        val definition = properties[value]
                        if (definition == null) {
                            reader.skipUntilNextField()
                            continue@walker
                        } else {
                            if (definition == RootModelProperties.properties) {
                                propertiesAreProcessed = true
                            } else if (!propertiesAreProcessed && definition == RootModelProperties.key) {
                                val collectedTokens = mutableListOf<JsonToken>()

                                reader.skipUntilNextField {
                                    collectedTokens.add(it)
                                }

                                keyDefinitionsToProcessLater = collectedTokens
                                continue@walker
                            }

                            reader.nextToken()

                            valueMap[definition.index] = definition.definition.readJson(reader, context)
                        }
                    }
                    else -> break@walker
                }
                reader.nextToken()
            } while (token !is JsonToken.Stopped)

            keyDefinitionsToProcessLater?.let { jsonTokens ->
                val lateReader = if (reader is IsYamlReader) {
                    jsonTokens.map { reader.pushToken(it) }
                    reader.pushToken(reader.currentToken)
                    reader.nextToken()
                    reader
                } else {
                    PresetJsonTokenReader(jsonTokens)
                }

                valueMap[RootModelProperties.key.index] = RootModelProperties.key.readJson(lateReader, context as DefinitionsConversionContext?)

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
        val value = it.getValue(this, values)

        (it as IsFixedBytesEncodable<Any>).writeStorageBytes(value) {
            bytes[index++] = it
        }

        // Add separator
        if (index < this.keySize) {
            bytes[index++] = 1
        }
    }
    return Key(bytes)
}
