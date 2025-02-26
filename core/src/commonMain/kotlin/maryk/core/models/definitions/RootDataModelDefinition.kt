package maryk.core.models.definitions

import maryk.core.definitions.PrimitiveType.RootModel
import maryk.core.exceptions.SerializationException
import maryk.core.models.DefinitionModel
import maryk.core.models.IsObjectDataModel
import maryk.core.models.serializers.ObjectDataModelSerializer
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
import maryk.core.properties.definitions.number
import maryk.core.properties.definitions.string
import maryk.core.properties.definitions.valueObject
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.Version
import maryk.core.properties.types.numeric.UInt32
import maryk.core.query.ContainsDefinitionsContext
import maryk.core.values.ObjectValues
import maryk.json.IsJsonLikeWriter

/**
 * DataModel definition for metadata for [maryk.core.models.DataModel],
 * so it can be stored and thus can have a key.
 * The key is defined by passing an ordered array of key definitions.
 * If no key is defined the data model will get a UUID.
 */
data class RootDataModelDefinition(
    override val name: String,
    override val keyDefinition: IsIndexable = UUIDKey,
    override val version: Version = Version(1),
    override val indices: List<IsIndexable>? = null,
    override val reservedIndices: List<UInt>? = null,
    override val reservedNames: List<String>? = null,
    override val minimumKeyScanByteRange: UInt? = null,
) : IsRootDataModelDefinition {
    override val primitiveType = RootModel

    override val keyByteSize = checkKeyDefinitionAndCountBytes(keyDefinition)
    override val keyIndices = calculateKeyIndices(keyDefinition)

    object Model : DefinitionModel<RootDataModelDefinition>(){
        val name by string(1u, RootDataModelDefinition::name)
        val version by valueObject(
            index = 2u,
            dataModel = Version,
            default = Version(1),
            getter = RootDataModelDefinition::version
        )
        val key by internalMultiType(
            index = 3u,
            typeEnum = IndexKeyPartType,
            definitionMap = mapOfIndexKeyPartDefinitions,
            getter = RootDataModelDefinition::keyDefinition,
            toSerializable = { value: IsIndexable?, _: ContainsDefinitionsContext? ->
                value?.let { TypedValue(value.indexKeyPartType, value) }
            },
            fromSerializable = { value: TypedValue<IndexKeyPartType<IsIndexable>, Any>? -> value?.value as IsIndexable }
        )
        val indices by list(
            index = 4u,
            getter = RootDataModelDefinition::indices,
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
            index = 5u,
            getter = RootDataModelDefinition::reservedIndices,
            valueDefinition = NumberDefinition(
                type = UInt32,
                minValue = 1u
            )
        )
        val reservedNames by list(
            index = 6u,
            getter = RootDataModelDefinition::reservedNames,
            valueDefinition = StringDefinition()
        )
        val minimumKeyScanByteRange by number(
            index = 7u,
            getter = RootDataModelDefinition::minimumKeyScanByteRange,
            type = UInt32,
        )

        override fun invoke(values: ObjectValues<RootDataModelDefinition, IsObjectDataModel<RootDataModelDefinition>>) =
            RootDataModelDefinition(
                name = values(1u),
                version = values(2u),
                keyDefinition = values(3u) ?: UUIDKey,
                indices = values(4u),
                reservedIndices = values(5u),
                reservedNames = values(6u),
                minimumKeyScanByteRange = values(7u),
            )

        override val Serializer = object: ObjectDataModelSerializer<RootDataModelDefinition, IsObjectDataModel<RootDataModelDefinition>, ContainsDefinitionsContext, ContainsDefinitionsContext>(this){
            override fun writeJson(
                values: ObjectValues<RootDataModelDefinition, IsObjectDataModel<RootDataModelDefinition>>,
                writer: IsJsonLikeWriter,
                context: ContainsDefinitionsContext?
            ) {
                throw SerializationException("Cannot write definitions from Values")
            }
        }
    }
}
