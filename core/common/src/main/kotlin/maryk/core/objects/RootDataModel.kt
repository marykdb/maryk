package maryk.core.objects

import maryk.core.exceptions.DefNotFoundException
import maryk.core.extensions.bytes.initByteArray
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.FixedBytesProperty
import maryk.core.properties.definitions.IsFixedBytesEncodable
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.PropertyDefinitionType
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.key.Reversed
import maryk.core.properties.definitions.key.UUIDKey
import maryk.core.properties.definitions.key.mapOfKeyPartDefinitions
import maryk.core.properties.definitions.wrapper.FixedBytesPropertyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.references.ValueWithFixedBytesPropertyReference
import maryk.core.properties.types.Key
import maryk.core.properties.types.TypedValue
import maryk.core.query.DataModelContext
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken
import maryk.json.PresetJsonTokenReader
import maryk.lib.bytes.Base64
import maryk.lib.exceptions.ParseException
import maryk.yaml.IsYamlReader

fun definitions(vararg keys: FixedBytesProperty<*>) = arrayOf(*keys)

/**
 * DataModel defining data objects of type [DO] which is on root level so it can be stored and thus can have a [key].
 * The key is defined by passing an ordered array of key definitions.
 * If no key is defined the data model will get a UUID.
 *
 * The dataModel can be referenced by the [name] and the properties are defined by a [properties]
 */
abstract class RootDataModel<DO: Any, P: PropertyDefinitions<DO>>(
    name: String,
    keyDefinitions: Array<FixedBytesProperty<out Any>> = arrayOf(UUIDKey),
    properties: P
) : DataModel<DO, P>(name, properties){
    val key = KeyDefinition(*keyDefinitions)

    /** Defines the structure of the Key by passing [keyDefinitions] */
    inner class KeyDefinition(vararg val keyDefinitions: FixedBytesProperty<out Any>) {
        val size: Int

        init {
            var totalBytes = keyDefinitions.size - 1 // Start with adding size of separators

            for (it in keyDefinitions) {
                when {
                    it is FixedBytesPropertyDefinitionWrapper<*, *, *, *>
                            && it.definition is IsValueDefinition<*, *>-> {
                        checkDefinition(it.name, it.definition as IsValueDefinition<*, *>)
                    }
                    it is Reversed<out Any> -> {
                        val reference = it.reference as ValueWithFixedBytesPropertyReference<out Any, *, *>
                        checkDefinition(reference.propertyDefinition.name, reference.propertyDefinition.definition)
                    }
                }
                totalBytes += it.byteSize
            }
            this.size = totalBytes
        }

        private fun checkDefinition(name: String, it: IsPropertyDefinition<*>) {
            require(it.required, { "Definition of $name should be required" })
            require(it.final, { "Definition of $name should be final" })
        }

        /** Get Key by [bytes] array */
        operator fun invoke(bytes: ByteArray): Key<DO> {
            if (bytes.size != this.size) {
                throw ParseException("Invalid byte length for key")
            }
            return Key(bytes)
        }

        /** Get Key by [base64] bytes as string representation */
        operator fun invoke(base64: String): Key<DO> = this(Base64.decode(base64))

        /** Get Key by byte [reader] */
        internal fun get(reader: () -> Byte): Key<DO> = Key(
            initByteArray(size, reader)
        )

        /** Get Key based on [dataObject] */
        operator fun invoke(dataObject: DO): Key<DO> {
            val bytes = ByteArray(this.size)
            var index = 0
            for (it in keyDefinitions) {
                val value = it.getValue(this@RootDataModel, dataObject)

                @Suppress("UNCHECKED_CAST")
                (it as IsFixedBytesEncodable<Any>).writeStorageBytes(value, {
                    bytes[index++] = it
                })

                // Add separator
                if (index < this.size) {
                    bytes[index++] = 1
                }
            }
            return Key(bytes)
        }
    }

    /** Get PropertyReference by [referenceName] */
    internal fun getPropertyReferenceByName(referenceName: String) = try {
        this.properties.getPropertyReferenceByName(referenceName)
    } catch (e: DefNotFoundException) {
        throw DefNotFoundException("Model ${this.name}: ${e.message}")
    }

    /** Get PropertyReference by bytes by reading the [reader] until [length] is reached. */
    internal fun getPropertyReferenceByBytes(length: Int, reader: () -> Byte) = try {
        this.properties.getPropertyReferenceByBytes(length, reader)
    } catch (e: DefNotFoundException) {
        throw DefNotFoundException("Model ${this.name}: ${e.message}")
    }

    @Suppress("UNCHECKED_CAST")
    private object RootModelProperties: PropertyDefinitions<RootDataModel<*, *>>() {
        init {
            AbstractDataModel.addName(this as PropertyDefinitions<RootDataModel<Any, PropertyDefinitions<Any>>>) {
                it.name
            }
        }
        val properties = AbstractDataModel.addProperties(this as PropertyDefinitions<RootDataModel<Any, PropertyDefinitions<Any>>>)
        val key = add(2, "key", ListDefinition(
            valueDefinition = MultiTypeDefinition(
                definitionMap = mapOfKeyPartDefinitions
            )
        )) {
            it.key.keyDefinitions.map {
                val def: Any = when(it) {
                    is FixedBytesPropertyDefinitionWrapper<*, *, *, *> -> it.getRef()
                    else -> it
                }
                TypedValue(it.keyPartType, def)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    object Model : SimpleDataModel<RootDataModel<*, *>, PropertyDefinitions<RootDataModel<*, *>>>(
        properties = RootModelProperties
    ) {
        override fun invoke(map: Map<Int, *>) = object : RootDataModel<Any, PropertyDefinitions<Any>>(
            name = map(0),
            properties = map(1),
            keyDefinitions = (map<List<TypedValue<PropertyDefinitionType, *>>?>(2))?.map {
                when(it.value) {
                    is ValueWithFixedBytesPropertyReference<*, *, *> -> it.value.propertyDefinition
                    else -> it.value as FixedBytesProperty<*>
                }
            }?.toTypedArray() ?: arrayOf(UUIDKey) as Array<FixedBytesProperty<out Any>>
        ){
            override fun invoke(map: Map<Int, *>): Any {
                return object : Any(){}
            }
        }

        /**
         * Overridden to handle earlier definition of keys compared to Properties
         */
        override fun writeJson(map: Map<Int, Any>, writer: IsJsonLikeWriter, context: IsPropertyContext?) {
            writer.writeStartObject()
            for ((key, value) in map) {
                if (key == RootModelProperties.properties.index) continue // skip properties to write last

                val def = properties.getDefinition(key) ?: continue
                this.writeJsonValue(def, writer, value, context)
            }
            // Write properties last
            map[RootModelProperties.properties.index]?.let {
                this.writeJsonValue(
                    RootModelProperties.properties as IsPropertyDefinitionWrapper<Any, IsPropertyContext, RootDataModel<*, *>>,
                    writer,
                    it,
                    context
                )
            }

            writer.writeEndObject()
        }

        /**
         * Overridden to handle earlier definition of keys compared to Properties
         */
        override fun writeJson(obj: RootDataModel<*, *>, writer: IsJsonLikeWriter, context: IsPropertyContext?) {
            writer.writeStartObject()
            for (def in this.properties) {
                if (def == RootModelProperties.properties) continue // skip properties to write last

                val value = def.getter(obj) ?: continue
                this.writeJsonValue(def, writer, value, context)
            }
            this.writeJsonValue(
                RootModelProperties.properties as IsPropertyDefinitionWrapper<Any, IsPropertyContext, RootDataModel<*, *>>,
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

                        val definition = properties.getDefinition(value)
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

            keyDefinitionsToProcessLater?.let {
                val lateReader = if (reader is IsYamlReader) {
                    it.map { reader.pushToken(it) }
                    reader.pushToken(reader.currentToken)
                    reader.nextToken()
                    reader
                } else {
                    PresetJsonTokenReader(it)
                }

                valueMap[RootModelProperties.key.index] = RootModelProperties.key.readJson(lateReader, context as DataModelContext?)
            }

            super.walkJsonToRead(reader, valueMap, context)
        }
    }
}
