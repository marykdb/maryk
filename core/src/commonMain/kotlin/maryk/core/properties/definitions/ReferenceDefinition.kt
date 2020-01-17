package maryk.core.properties.definitions

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.exceptions.DefNotFoundException
import maryk.core.models.DefinitionDataModel
import maryk.core.models.IsRootDataModel
import maryk.core.models.IsTypedRootDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.IsPropertyDefinitions
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.contextual.ContextualModelReferenceDefinition
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.properties.definitions.wrapper.FixedBytesDefinitionWrapper
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.Key
import maryk.core.protobuf.WireType.LENGTH_DELIMITED
import maryk.core.query.ContainsDefinitionsContext
import maryk.core.values.SimpleObjectValues
import maryk.lib.exceptions.ParseException
import maryk.lib.safeLazy

/** Definition for a reference to another DataObject*/
class ReferenceDefinition<DM : IsRootDataModel<*>>(
    override val required: Boolean = true,
    override val final: Boolean = false,
    override val unique: Boolean = false,
    override val minValue: Key<DM>? = null,
    override val maxValue: Key<DM>? = null,
    override val default: Key<DM>? = null,
    dataModel: Unit.() -> DM
) :
    IsComparableDefinition<Key<DM>, IsPropertyContext>,
    IsSerializableFixedBytesEncodable<Key<DM>, IsPropertyContext>,
    IsTransportablePropertyDefinitionType<Key<DM>>,
    HasDefaultValueDefinition<Key<DM>>,
    IsWrappableDefinition<Key<DM>, IsPropertyContext, FixedBytesDefinitionWrapper<Key<DM>, Key<DM>, IsPropertyContext, ReferenceDefinition<DM>, Any>>
{
    override val propertyDefinitionType = PropertyDefinitionType.Reference
    override val wireType = LENGTH_DELIMITED
    override val byteSize get() = dataModel.keyByteSize

    private val internalDataModel = safeLazy(dataModel)
    val dataModel: DM get() = internalDataModel.value

    override fun calculateStorageByteLength(value: Key<DM>) = this.byteSize

    override fun writeStorageBytes(value: Key<DM>, writer: (byte: Byte) -> Unit) = value.writeBytes(writer)

    @Suppress("UNCHECKED_CAST")
    override fun readStorageBytes(length: Int, reader: () -> Byte) = dataModel.key(reader) as Key<DM>

    override fun calculateTransportByteLength(value: Key<DM>) = this.byteSize

    override fun fromString(string: String) = try {
        @Suppress("UNCHECKED_CAST")
        dataModel.key(string) as Key<DM>
    } catch (e: Throwable) {
        throw ParseException(string, e)
    }

    override fun fromNativeType(value: Any) =
        if (value is ByteArray && value.size == this.byteSize) {
            @Suppress("UNCHECKED_CAST")
            dataModel.key(value) as Key<DM>
        } else {
            null
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ReferenceDefinition<*>) return false

        if (required != other.required) return false
        if (final != other.final) return false
        if (unique != other.unique) return false
        if (minValue != other.minValue) return false
        if (maxValue != other.maxValue) return false
        if (default != other.default) return false
        if (dataModel.name != other.dataModel.name) return false

        return true
    }

    override fun hashCode(): Int {
        var result = required.hashCode()
        result = 31 * result + final.hashCode()
        result = 31 * result + unique.hashCode()
        result = 31 * result + (minValue?.hashCode() ?: 0)
        result = 31 * result + (maxValue?.hashCode() ?: 0)
        result = 31 * result + (default?.hashCode() ?: 0)
        result = 31 * result + dataModel.name.hashCode()
        return result
    }

    override fun wrap(
        index: UInt,
        name: String,
        alternativeNames: Set<String>?
    ) =
        FixedBytesDefinitionWrapper<Key<DM>, Key<DM>, IsPropertyContext, ReferenceDefinition<DM>, Any>(index, name, this, alternativeNames)

    object Model : DefinitionDataModel<ReferenceDefinition<*>>(
        properties = object : ObjectPropertyDefinitions<ReferenceDefinition<*>>() {
            init {
                IsPropertyDefinition.addRequired(this, ReferenceDefinition<*>::required)
                IsPropertyDefinition.addFinal(this, ReferenceDefinition<*>::final)
                IsComparableDefinition.addUnique(this, ReferenceDefinition<*>::unique)
                add(4u, "minValue", FlexBytesDefinition(), ReferenceDefinition<*>::minValue)
                add(5u, "maxValue", FlexBytesDefinition(), ReferenceDefinition<*>::maxValue)
                add(6u, "default", FlexBytesDefinition(), ReferenceDefinition<*>::default)
                add(7u, "dataModel",
                    definition = ContextualModelReferenceDefinition(
                        contextualResolver = { context: ContainsDefinitionsContext?, name ->
                            context?.let {
                                @Suppress("UNCHECKED_CAST")
                                it.dataModels[name] as (Unit.() -> IsRootDataModel<*>)?
                                    ?: throw DefNotFoundException("ObjectDataModel of name $name not found on dataModels")
                            } ?: throw ContextNotFoundException()
                        }
                    ),
                    getter = {
                        { it.dataModel }
                    },
                    toSerializable = { value: (Unit.() -> IsRootDataModel<*>)?, _ ->
                        value?.invoke(Unit)?.let { model: IsRootDataModel<*> ->
                            DataModelReference(model.name, value)
                        }
                    },
                    fromSerializable = {
                        it?.get
                    },
                    capturer = { context, dataModel ->
                        if (!context.dataModels.containsKey(dataModel.name)) {
                            context.dataModels[dataModel.name] = dataModel.get
                        }
                    }
                )
            }
        }
    ) {
        override fun invoke(values: SimpleObjectValues<ReferenceDefinition<*>>) = ReferenceDefinition(
            required = values(1u),
            final = values(2u),
            unique = values(3u),
            minValue = values<Bytes?>(4u)?.let {
                Key<IsTypedRootDataModel<IsRootDataModel<IsPropertyDefinitions>, IsPropertyDefinitions>>(
                    it.bytes
                )
            },
            maxValue = values<Bytes?>(5u)?.let {
                Key<IsTypedRootDataModel<IsRootDataModel<IsPropertyDefinitions>, IsPropertyDefinitions>>(
                    it.bytes
                )
            },
            default = values<Bytes?>(6u)?.let {
                Key<IsTypedRootDataModel<IsRootDataModel<IsPropertyDefinitions>, IsPropertyDefinitions>>(
                    it.bytes
                )
            },
            dataModel = values(7u)
        )
    }
}
