package maryk.core.properties.definitions

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.exceptions.DefNotFoundException
import maryk.core.models.DefinitionDataModel
import maryk.core.models.IsRootDataModel
import maryk.core.models.IsTypedRootDataModel
import maryk.core.objects.SimpleObjectValues
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.IsPropertyDefinitions
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.contextual.ContextualModelReferenceDefinition
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.Key
import maryk.core.protobuf.WireType
import maryk.core.query.DataModelContext
import maryk.lib.exceptions.ParseException

/** Definition for a reference to another DataObject*/
class ReferenceDefinition<DM: IsRootDataModel<*>>(
    override val indexed: Boolean = false,
    override val required: Boolean = true,
    override val final: Boolean = false,
    override val unique: Boolean = false,
    override val minValue: Key<DM>? = null,
    override val maxValue: Key<DM>? = null,
    override val default: Key<DM>? = null,
    dataModel: () -> DM
):
    IsComparableDefinition<Key<DM>, IsPropertyContext>,
    IsSerializableFixedBytesEncodable<Key<DM>, IsPropertyContext>,
    IsTransportablePropertyDefinitionType<Key<DM>>,
    HasDefaultValueDefinition<Key<DM>>
{
    override val propertyDefinitionType = PropertyDefinitionType.Reference
    override val wireType = WireType.LENGTH_DELIMITED
    override val byteSize get() = dataModel.keySize

    private val internalDataModel = lazy(dataModel)
    val dataModel: DM get() = internalDataModel.value

    override fun calculateStorageByteLength(value: Key<DM>) = this.byteSize

    override fun writeStorageBytes(value: Key<DM>, writer: (byte: Byte) -> Unit)  = value.writeBytes(writer)

    @Suppress("UNCHECKED_CAST")
    override fun readStorageBytes(length: Int, reader: () -> Byte) = dataModel.key(reader) as Key<DM>

    override fun calculateTransportByteLength(value: Key<DM>) = this.byteSize

    override fun fromString(string: String) = try {
        @Suppress("UNCHECKED_CAST")
        dataModel.key(string) as Key<DM>
    } catch (e: Throwable) { throw ParseException(string, e) }

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

        if (indexed != other.indexed) return false
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
        var result = indexed.hashCode()
        result = 31 * result + required.hashCode()
        result = 31 * result + final.hashCode()
        result = 31 * result + unique.hashCode()
        result = 31 * result + (minValue?.hashCode() ?: 0)
        result = 31 * result + (maxValue?.hashCode() ?: 0)
        result = 31 * result + (default?.hashCode() ?: 0)
        result = 31 * result + dataModel.name.hashCode()
        return result
    }

    object Model : DefinitionDataModel<ReferenceDefinition<*>>(
        properties = object : ObjectPropertyDefinitions<ReferenceDefinition<*>>() {
            init {
                IsPropertyDefinition.addIndexed(this, ReferenceDefinition<*>::indexed)
                IsPropertyDefinition.addRequired(this, ReferenceDefinition<*>::required)
                IsPropertyDefinition.addFinal(this, ReferenceDefinition<*>::final)
                IsComparableDefinition.addUnique(this, ReferenceDefinition<*>::unique)
                add(4, "minValue", FlexBytesDefinition(), ReferenceDefinition<*>::minValue)
                add(5, "maxValue", FlexBytesDefinition(), ReferenceDefinition<*>::maxValue)
                add(6, "default", FlexBytesDefinition(), ReferenceDefinition<*>::default)
                add(7, "dataModel",
                    definition = ContextualModelReferenceDefinition(
                        contextualResolver = { context: DataModelContext?, name ->
                            context?.let {
                                @Suppress("UNCHECKED_CAST")
                                it.dataModels[name] as (() -> IsRootDataModel<*>)? ?: throw DefNotFoundException("ObjectDataModel of name $name not found on dataModels")
                            } ?: throw ContextNotFoundException()
                        }
                    ),
                    getter = { it: ReferenceDefinition<*> ->
                        { it.dataModel }
                    },
                    toSerializable = { value, _ ->
                        value?.invoke()?.let { model: IsRootDataModel<*> ->
                            DataModelReference(model.name, value)
                        }
                    },
                    fromSerializable = {
                        @Suppress("UNCHECKED_CAST")
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
        override fun invoke(map: SimpleObjectValues<ReferenceDefinition<*>>) = ReferenceDefinition(
            indexed = map(0),
            required = map(1),
            final = map(2),
            unique = map(3),
            minValue = map<Bytes?>(4)?.let { Key<IsTypedRootDataModel<IsRootDataModel<IsPropertyDefinitions>, IsPropertyDefinitions>>(it.bytes) },
            maxValue = map<Bytes?>(5)?.let { Key<IsTypedRootDataModel<IsRootDataModel<IsPropertyDefinitions>, IsPropertyDefinitions>>(it.bytes) },
            default = map<Bytes?>(6)?.let { Key<IsTypedRootDataModel<IsRootDataModel<IsPropertyDefinitions>, IsPropertyDefinitions>>(it.bytes) },
            dataModel = map(7)
        )
    }
}
