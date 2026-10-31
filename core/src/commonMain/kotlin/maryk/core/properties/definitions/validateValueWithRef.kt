package maryk.core.properties.definitions

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.enum.IndexedEnumComparable
import maryk.core.properties.exceptions.InvalidValueException
import maryk.core.properties.exceptions.serializableValidationReference
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.Decimal
import maryk.core.properties.types.GeoPoint
import maryk.core.properties.types.Key
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.ValueDataObject
import maryk.core.values.Values

/** Validate values arriving through erased model slots or mutation references. */
@Suppress("UNCHECKED_CAST")
fun IsPropertyDefinition<*>.validateValueWithRef(
    previousValue: Any? = null,
    newValue: Any?,
    refGetter: () -> AnyPropertyReference? = { null },
) {
    validateValueType(newValue, refGetter)
    (this as IsPropertyDefinition<Any>).validateWithRef(previousValue, newValue) {
        refGetter() as IsPropertyReference<Any, IsPropertyDefinition<Any>, *>?
    }
}

/** Check runtime shapes before dispatch or conversion; this does not check value constraints. */
fun IsPropertyDefinition<*>.validateValueType(value: Any?, refGetter: () -> AnyPropertyReference? = { null }) =
    requireValueType(value, refGetter)

/** Check container contents before generic bridges can cast or coerce them. */
private fun IsPropertyDefinition<*>.requireValueType(value: Any?, refGetter: () -> AnyPropertyReference?, member: Boolean = false) {
    if (value == null) {
        if (member) throw InvalidValueException(serializableValidationReference(refGetter()), "Collection members cannot be null")
        return
    }
    if (this is IsDefinitionWrapper<*, *, *, *>) {
        definition.requireValueType(value, refGetter)
        return
    }
    val valid = when (this) {
        is BooleanDefinition -> value is Boolean
        is DateDefinition -> value is LocalDate
        is DateTimeDefinition -> value is LocalDateTime
        is TimeDefinition -> value is LocalTime
        is GeoPointDefinition -> value is GeoPoint
        is DecimalDefinition -> value is Decimal
        is NumberDefinition<*> -> type.isOfType(value)
        is StringDefinition -> value is String
        is FixedBytesDefinition, is FlexBytesDefinition -> value is Bytes
        is IsReferenceDefinition<*, *> -> value is Key<*>
        is EnumDefinition<*> -> value is IndexedEnumComparable<*>
        is ValueObjectDefinition<*, *> -> value is ValueDataObject
        is IsEmbeddedValuesDefinition<*, *> -> value is Values<*>
        is IsMultiTypeDefinition<*, *, *> -> value is TypedValue<*, *>
        is IsListDefinition<*, *> -> value is List<*>
        is IsSetDefinition<*, *> -> value is Set<*>
        is IsMapDefinition<*, *, *> -> value is Map<*, *>
        else -> true // Custom/contextual definitions retain their own validation.
    }
    if (!valid) {
        val name = (this as? IsTransportablePropertyDefinitionType<*>)?.propertyDefinitionType?.name ?: this::class.simpleName
        throw InvalidValueException(serializableValidationReference(refGetter()), "Expected $name: $value")
    }
    // Use the containing reference for malformed members: their own reference
    // may be impossible to encode in a transportable validation response.
    when {
        this is IsMapDefinition<*, *, *> && value is Map<*, *> -> value.forEach { (key, item) ->
            keyDefinition.requireValueType(key, refGetter, member = true)
            valueDefinition.requireValueType(item, refGetter, member = true)
        }
        this is IsCollectionDefinition<*, *, *, *> && value is Collection<*> -> value.forEach {
            valueDefinition.requireValueType(it, refGetter, member = true)
        }
        this is IsMultiTypeDefinition<*, *, *> && value is TypedValue<*, *> ->
            definition(value.type.index)?.requireValueType(value.value, refGetter)
    }
}
