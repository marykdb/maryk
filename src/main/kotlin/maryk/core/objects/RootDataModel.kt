package maryk.core.objects

import maryk.core.bytes.Base64
import maryk.core.exceptions.DefNotFoundException
import maryk.core.extensions.bytes.initByteArray
import maryk.core.extensions.bytes.initIntByVar
import maryk.core.properties.definitions.IsFixedBytesEncodable
import maryk.core.properties.definitions.IsFixedBytesProperty
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.key.Reversed
import maryk.core.properties.definitions.key.UUIDKey
import maryk.core.properties.definitions.wrapper.FixedBytesPropertyDefinitionWrapper
import maryk.core.properties.exceptions.ParseException
import maryk.core.properties.references.HasEmbeddedPropertyReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.Key

fun definitions(vararg keys: IsFixedBytesProperty<*>) = arrayOf(*keys)

/** DataModel which is on root level so it can be stored and thus can have a key
 * If no key is defined the datamodel will get a UUID
 *
 * @param name: Name of the data model. Used also to resolve DataModels
 * @param keyDefinitions: Ordered array with all key part definitions
 * @param properties: All definitions for properties contained in this model
 * @param DO: Type of DataObject contained
 */
abstract class RootDataModel<DO: Any, P: PropertyDefinitions<DO>>(
        name: String,
        keyDefinitions: Array<IsFixedBytesProperty<out Any>> = arrayOf(UUIDKey),
        properties: P
) : DataModel<DO, P>(name, properties){
    val key = KeyDefinition(*keyDefinitions)

    /** Defines the structure of the Key */
    inner class KeyDefinition(vararg val keyDefinitions: IsFixedBytesProperty<out Any>) {
        val size: Int

        init {
            var totalBytes = keyDefinitions.size - 1 // Start with adding size of separators

            keyDefinitions.forEach {
                when {
                    it is FixedBytesPropertyDefinitionWrapper<*, *, *, *>
                            && it.definition is IsValueDefinition<*, *>-> {
                        checkDefinition(it.name, it.definition as IsValueDefinition<*, *>)
                    }
                    it is Reversed<*>
                            && it.definition is FixedBytesPropertyDefinitionWrapper<*, *, *, *>
                            && it.definition.definition is IsValueDefinition<*, *> -> {
                        checkDefinition(it.definition.name, it.definition.definition)
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

        /** Get Key by byte array */
        fun get(bytes: ByteArray): Key<DO> {
            if (bytes.size != this.size) {
               throw ParseException("Invalid byte length for key")
            }
            return Key(bytes)
        }

        /** Get Key by base64 byte representation */
        fun get(base64: String): Key<DO> = this.get(Base64.decode(base64))

        /** Get Key by byte reader */
        fun get(reader: () -> Byte): Key<DO> = Key<DO>(
                initByteArray(size, reader)
        )

        /** Get Key based on DataObject */
        fun getKey(dataObject: DO): Key<DO> {
            val bytes = ByteArray(this.size)
            var index = 0
            keyDefinitions.forEach {
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

    /** Get PropertyReference by name
     * @param referenceName to parse for a property reference
     */
    fun getPropertyReferenceByName(referenceName: String): IsPropertyReference<*, IsPropertyDefinition<*>> {
        val names = referenceName.split(".")

        var propertyReference: IsPropertyReference<*, *>? = null
        for (name in names) {
            propertyReference = when (propertyReference) {
                null -> getDefinition(name)?.getRef(propertyReference)
                is HasEmbeddedPropertyReference<*> -> propertyReference.getEmbedded(name)
                else -> throw DefNotFoundException("${this.name}: Illegal $referenceName, ${propertyReference.completeName} does not contain embedded property definitions for $name")
            } ?: throw DefNotFoundException("Property reference «$referenceName» does not exist on ${this.name}")
        }

        return propertyReference!!
    }

    /** Get PropertyReference by bytes
     * @param length of bytes to read
     * @param reader to read for a property reference
     */
    fun getPropertyReferenceByBytes(length: Int, reader: () -> Byte): IsPropertyReference<*, IsPropertyDefinition<*>> {
        var readLength = 0

        val lengthReader = {
            readLength++
            reader()
        }

        var propertyReference: IsPropertyReference<*, *>? = null
        while (readLength < length) {
            propertyReference = when (propertyReference) {
                null -> {
                    val index = initIntByVar(lengthReader)
                    getDefinition(index)?.getRef(propertyReference)
                }
                is HasEmbeddedPropertyReference<*> -> propertyReference.getEmbeddedRef(lengthReader)
                else -> throw DefNotFoundException("More property references found on property ${this.name} that cannot have any ")
            } ?: throw DefNotFoundException("Property reference does not exist on ${this.name}")
        }

        return propertyReference!!
    }
}
