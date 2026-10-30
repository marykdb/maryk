package maryk.core.models.migration

import maryk.core.extensions.bytes.initInt
import maryk.core.extensions.bytes.initIntByVar
import maryk.core.extensions.bytes.initLong
import maryk.core.extensions.bytes.initUIntByVarWithExtraInfo
import maryk.core.models.IsValuesDataModel
import maryk.core.properties.definitions.IsEmbeddedValuesDefinition
import maryk.core.properties.definitions.IsFixedStorageBytesEncodable
import maryk.core.properties.definitions.IsListDefinition
import maryk.core.properties.definitions.IsCollectionDefinition
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsMultiTypeDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsSetDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.canonicalCollectionQualifierBytes
import maryk.core.properties.definitions.ValueObjectDefinition
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.definitions.wrapper.IsSensitiveValueDefinitionWrapper
import maryk.core.properties.enum.MultiTypeEnum
import maryk.core.properties.types.numeric.Float32
import maryk.core.properties.types.numeric.Float64
import maryk.core.query.RequestContext

/** Scoped only to persisted pre-upgrade Protobuf logs containing opaque value-object bytes. */
fun <T> RequestContext.readLegacyValueStorage(read: () -> T): T {
    val previous = legacyValueStorage
    legacyValueStorage = true
    return try { read() } finally { legacyValueStorage = previous }
}

/** Inspect stored definitions, including nested/recursive model dependencies, once at opening. */
fun IsValuesDataModel.hasFloatingPointStorage(): Boolean = hasFloatingPointStorage(mutableSetOf(), false)
fun IsValuesDataModel.hasFloatingPointValueObjects(): Boolean = hasFloatingPointStorage(mutableSetOf(), true)

private fun IsValuesDataModel.hasFloatingPointStorage(visited: MutableSet<IsValuesDataModel>, valueObjectsOnly: Boolean): Boolean =
    visited.add(this) && any { it.definition.hasFloatingPointStorage(visited, valueObjectsOnly) }

fun IsPropertyDefinition<*>.hasFloatingPointStorage(visited: MutableSet<IsValuesDataModel> = mutableSetOf(), valueObjectsOnly: Boolean = false): Boolean = when (this) {
    is IsDefinitionWrapper<*, *, *, *> -> definition.hasFloatingPointStorage(visited, valueObjectsOnly)
    is NumberDefinition<*> -> !valueObjectsOnly && (type == Float32 || type == Float64)
    is ValueObjectDefinition<*, *> -> dataModel.any { it.definition.hasFloatingPointStorage(visited, false) }
    is IsEmbeddedValuesDefinition<*, *> -> dataModel.hasFloatingPointStorage(visited, valueObjectsOnly)
    is IsMapDefinition<*, *, *> -> keyDefinition.hasFloatingPointStorage(visited, valueObjectsOnly) || valueDefinition.hasFloatingPointStorage(visited, valueObjectsOnly)
    is IsCollectionDefinition<*, *, *, *> -> valueDefinition.hasFloatingPointStorage(visited, valueObjectsOnly)
    is IsMultiTypeDefinition<*, *, *> -> typeEnum.cases().any { (it as MultiTypeEnum<*>).definition?.hasFloatingPointStorage(visited, valueObjectsOnly) == true }
    else -> false
}

/** Only the opening migration reads this representation; runtime codecs use the current format. */
fun IsPropertyDefinition<*>.convertLegacyFloatingPointBytes(bytes: ByteArray): ByteArray {
    val definition = if (this is IsDefinitionWrapper<*, *, *, *>) this.definition else this
    return when (definition) {
        is NumberDefinition<*> -> {
            if (definition.type != Float32 && definition.type != Float64) return bytes
            val source = if (definition.reversedStorage == true) ByteArray(bytes.size) { bytes[it].toInt().inv().toByte() } else bytes
            var offset = 0
            when (definition.type) {
                Float32 -> {
                    require(bytes.size == 4) { "Invalid legacy Float32 width" }
                    val old = initInt({ source[offset++] }) - 1
                    val bits = old xor (old.inv() shr 31 or Int.MIN_VALUE)
                    NumberDefinition(type = Float32, reversedStorage = definition.reversedStorage).toStorageBytes(Float.fromBits(bits))
                }
                else -> {
                    require(bytes.size == 8) { "Invalid legacy Float64 width" }
                    val old = initLong({ source[offset++] }) - 1
                    val bits = old xor (old.inv() shr 63 or Long.MIN_VALUE)
                    NumberDefinition(type = Float64, reversedStorage = definition.reversedStorage).toStorageBytes(Double.fromBits(bits))
                }
            }
        }
        is ValueObjectDefinition<*, *> -> {
            require(bytes.size == definition.byteSize) { "Invalid legacy value-object width" }
            val result = bytes.copyOf()
            var offset = 0
            definition.dataModel.forEachIndexed { index, property ->
                if (index > 0) offset++ // value-model separator
                val size = (property.definition as IsFixedStorageBytesEncodable<*>).byteSize
                property.definition.convertLegacyFloatingPointBytes(bytes.copyOfRange(offset, offset + size)).copyInto(result, offset)
                offset += size
            }
            result
        }
        else -> bytes
    }
}

/** Schema-directed qualifier conversion also resolves the definition of its cell or unique value. */
data class LegacyStorageQualifier(
    val bytes: ByteArray,
    val length: Int,
    val definition: IsPropertyDefinition<*>?,
    val sensitive: Boolean,
    val valueCell: Boolean,
)

/** [unique] allows an ordinary encoded value to follow a complete simple-property qualifier. */
fun IsValuesDataModel.convertLegacyStorageQualifier(bytes: ByteArray, unique: Boolean = false): LegacyStorageQualifier {
    val result = bytes.copyOf()
    var offset = 0
    var sensitive = false
    var valueCell = false
    fun read(): Byte {
        require(offset < bytes.size) { "Truncated legacy storage qualifier" }
        return bytes[offset++]
    }
    fun tag(): Pair<UInt, Byte> = initUIntByVarWithExtraInfo(::read) { index, type -> index to type }
    fun skip(size: Int) {
        require(size >= 0 && size <= bytes.size - offset) { "Invalid legacy qualifier component length" }
        offset += size
    }
    fun convertComponent(definition: IsPropertyDefinition<*>) {
        val size = initIntByVar(::read)
        val start = offset
        skip(size)
        definition.canonicalCollectionQualifierBytes(definition.convertLegacyFloatingPointBytes(bytes.copyOfRange(start, offset))).copyInto(result, start)
    }
    fun unwrap(definition: IsPropertyDefinition<*>): IsPropertyDefinition<*> {
        if (definition is IsSensitiveValueDefinitionWrapper<*, *, *, *> && definition.sensitive) sensitive = true
        return if (definition is IsDefinitionWrapper<*, *, *, *>) definition.definition else definition
    }
    var model = this
    val (index, type) = tag()
    if (type == 0.toByte()) return LegacyStorageQualifier(result, offset, null, false, false)
    var definition = unwrap(requireNotNull(model[index]) { "Missing legacy property $index" })
    valueCell = type == 1.toByte()
    while (offset < bytes.size) {
        val embeddedChild = definition is IsEmbeddedValuesDefinition<*, *>
        definition = when (val current = definition) {
            is IsEmbeddedValuesDefinition<*, *> -> {
                model = current.dataModel
                val (child, childType) = tag()
                valueCell = childType == 1.toByte()
                unwrap(requireNotNull(model[child]) { "Missing legacy embedded property $child" })
            }
            is IsListDefinition<*, *> -> {
                skip(4)
                valueCell = true
                unwrap(current.valueDefinition)
            }
            is IsMapDefinition<*, *, *> -> {
                convertComponent(current.keyDefinition)
                valueCell = true
                unwrap(current.valueDefinition)
            }
            is IsSetDefinition<*, *> -> {
                convertComponent(current.valueDefinition)
                valueCell = true
                unwrap(current.valueDefinition)
            }
            is IsMultiTypeDefinition<*, *, *> -> {
                val (case, caseType) = tag()
                require(caseType == 5.toByte()) { "Invalid legacy type qualifier" }
                valueCell = true
                unwrap(requireNotNull((current.typeEnum.resolve(case) as? MultiTypeEnum<*>)?.definition) { "Missing legacy type $case" })
            }
            else -> {
                require(unique) { "Trailing bytes in legacy storage qualifier" }
                break
            }
        }
        // Nested collections have an anonymous qualifier tag; embedded models do not.
        if (!embeddedChild && offset < bytes.size && (definition is IsListDefinition<*, *> || definition is IsMapDefinition<*, *, *> || definition is IsSetDefinition<*, *>)) {
            val (anonymous, _) = tag()
            require(anonymous == 0u) { "Invalid nested legacy collection qualifier" }
            valueCell = false
        }
    }
    return LegacyStorageQualifier(result, offset, definition, sensitive, valueCell)
}
