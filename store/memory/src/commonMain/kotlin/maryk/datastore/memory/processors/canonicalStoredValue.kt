package maryk.datastore.memory.processors

import maryk.core.properties.definitions.DateTimeDefinition
import maryk.core.properties.definitions.DecimalDefinition
import maryk.core.properties.definitions.EnumDefinition
import maryk.core.properties.definitions.GeoPointDefinition
import maryk.core.properties.definitions.IsMultiTypeDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsStorageBytesEncodable
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.TimeDefinition
import maryk.core.properties.definitions.ValueObjectDefinition
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.enum.TypeEnum
import maryk.core.properties.exceptions.InvalidValueException
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.invoke
import maryk.lib.exceptions.ParseException

/** Retain the declared storage precision even though Memory keeps decoded cells. */
@Suppress("UNCHECKED_CAST")
internal fun <T : Any> canonicalStoredValue(definition: IsPropertyDefinition<*>, value: T, reference: AnyPropertyReference? = null): T {
    if (definition is IsDefinitionWrapper<*, *, *, *>) return canonicalStoredValue(definition.definition, value, reference)
    if (definition is IsMultiTypeDefinition<*, *, *> && value is TypedValue<*, *>) {
        val case = definition.definition(value.type.index) ?: return value
        val typed = value as TypedValue<TypeEnum<Any>, Any>
        return typed.type(canonicalStoredValue(case, typed.value, reference)) as T
    }
    // These families can decode differently from the caller's representation.
    // Exact immutable strings, byte arrays, keys, dates and booleans need no copy.
    if (definition !is TimeDefinition && definition !is DateTimeDefinition && definition !is GeoPointDefinition &&
        definition !is DecimalDefinition && definition !is NumberDefinition<*> && definition !is EnumDefinition<*> &&
        definition !is ValueObjectDefinition<*, *>) return value
    val codec = definition as IsStorageBytesEncodable<T>
    return try {
        if (definition is EnumDefinition<*>) {
            (definition as IsPropertyDefinition<T>).validateWithRef(null, value) {
                reference as IsPropertyReference<T, IsPropertyDefinition<T>, *>?
            }
        }
        val bytes = codec.toStorageBytes(value)
        var offset = 0
        codec.readStorageBytes(bytes.size) { bytes[offset++] }
    } catch (_: ParseException) {
        throw InvalidValueException(reference, value.toString())
    } catch (_: IllegalArgumentException) {
        throw InvalidValueException(reference, value.toString())
    }
}
