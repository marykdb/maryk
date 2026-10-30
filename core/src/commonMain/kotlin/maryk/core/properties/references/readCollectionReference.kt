package maryk.core.properties.references

import maryk.core.extensions.bytes.initIntByVar
import maryk.core.extensions.bytes.initUIntByVar
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsListDefinition
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsSetDefinition
import maryk.core.protobuf.ProtoBuf
import maryk.lib.exceptions.ParseException

/** Collection values use the same member names and transport tags at every nesting level. */
@Suppress("UNCHECKED_CAST")
internal fun CanHaveComplexChildReference<*, *, *, *>.readCollectionReference(name: String): IsPropertyReference<Any, *, *>? {
    return when (val definition = propertyDefinition) {
        is IsMapDefinition<*, *, *> -> {
            val map = definition as IsMapDefinition<Any, Any, IsPropertyContext>
            val parent = this as CanContainMapItemReference<*, *, *>
            when (name.firstOrNull()) {
                '@' -> MapValueReference(map.keyDefinition.fromString(name.substring(1).unescapeReferenceSegment()), map, parent)
                '#' -> MapKeyReference(map.keyDefinition.fromString(name.substring(1).unescapeReferenceSegment()), map, parent)
                '*' -> if (name == "*") MapAnyValueReference(map, parent) else throw ParseException("Invalid map wildcard: $name")
                '~' -> if (name == "~") MapAnyKeyReference(map, parent) else throw ParseException("Invalid map wildcard: $name")
                '^' -> IncMapAddIndexReference(name.substring(1).toIntOrNull()?.takeIf { it >= 0 }
                    ?: throw ParseException("Invalid map add index: $name"), map, parent)
                else -> throw ParseException("Unknown map member: $name")
            }
        }
        is IsListDefinition<*, *> -> {
            val list = definition as IsListDefinition<Any, IsPropertyContext>
            val parent = this as CanContainListItemReference<*, *, *>
            when {
                name == "*" -> ListAnyItemReference(list, this)
                name.startsWith('@') -> ListItemReference(name.substring(1).toUIntOrNull()
                    ?: throw ParseException("Invalid list item index: $name"), list, parent)
                else -> throw ParseException("Unknown list member: $name")
            }
        }
        is IsSetDefinition<*, *> -> {
            val set = definition as IsSetDefinition<Any, IsPropertyContext>
            val parent = this as CanContainSetItemReference<*, *, *>
            when {
                name == "*" -> SetAnyValueReference(set, parent)
                name.startsWith('#') -> SetItemReference(set.valueDefinition.fromString(name.substring(1).unescapeReferenceSegment()), set, parent)
                else -> throw ParseException("Unknown set member: $name")
            }
        }
        else -> null
    } as IsPropertyReference<Any, *, *>?
}

@Suppress("UNCHECKED_CAST")
internal fun CanHaveComplexChildReference<*, *, *, *>.readCollectionReference(reader: () -> Byte, context: IsPropertyContext?): IsPropertyReference<Any, *, *>? {
    if (propertyDefinition.anonymousCollectionReferenceType() == null) return null
    val key = ProtoBuf.readReferenceKey(reader)
    return when (val definition = propertyDefinition) {
        is IsMapDefinition<*, *, *> -> {
            val map = definition as IsMapDefinition<Any, Any, IsPropertyContext>
            val parent = this as CanContainMapItemReference<*, *, *>
            when (key.tag) {
                0u -> MapValueReference(map.keyDefinition.readTransportBytes(ProtoBuf.getLength(key.wireType, reader), reader, context), map, parent)
                1u -> MapKeyReference(map.keyDefinition.readTransportBytes(ProtoBuf.getLength(key.wireType, reader), reader, context), map, parent)
                2u -> MapAnyValueReference(map, parent)
                3u -> IncMapAddIndexReference(initIntByVar(reader), map, parent)
                4u -> MapAnyKeyReference(map, parent)
                else -> throw ParseException("Unknown map reference tag ${key.tag}")
            }
        }
        is IsListDefinition<*, *> -> {
            val list = definition as IsListDefinition<Any, IsPropertyContext>
            when (key.tag) {
                0u -> ListItemReference(initUIntByVar(reader), list, this as CanContainListItemReference<*, *, *>)
                1u -> ListAnyItemReference(list, this)
                else -> throw ParseException("Unknown list reference tag ${key.tag}")
            }
        }
        is IsSetDefinition<*, *> -> {
            val set = definition as IsSetDefinition<Any, IsPropertyContext>
            val parent = this as CanContainSetItemReference<*, *, *>
            when (key.tag) {
                0u -> SetItemReference(set.valueDefinition.readTransportBytes(ProtoBuf.getLength(key.wireType, reader), reader, context), set, parent)
                1u -> SetAnyValueReference(set, parent)
                else -> throw ParseException("Unknown set reference tag ${key.tag}")
            }
        }
        else -> null
    } as IsPropertyReference<Any, *, *>?
}
