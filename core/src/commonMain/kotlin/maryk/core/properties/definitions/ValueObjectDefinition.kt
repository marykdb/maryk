package maryk.core.properties.definitions

import maryk.core.definitions.MarykPrimitive
import maryk.core.exceptions.ContextNotFoundException
import maryk.core.exceptions.DefNotFoundException
import maryk.core.extensions.bytes.writeBytes
import maryk.core.models.ContextualDataModel
import maryk.core.models.IsObjectDataModel
import maryk.core.models.IsTypedObjectDataModel
import maryk.core.models.IsValueDataModel
import maryk.core.models.IsValuesDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.PropertyDefinitionType.Value
import maryk.core.properties.definitions.contextual.ContextualEmbeddedObjectDefinition
import maryk.core.properties.definitions.contextual.ContextualModelReferenceDefinition
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.properties.definitions.contextual.IsDataModelReference
import maryk.core.properties.definitions.contextual.ModelContext
import maryk.core.properties.definitions.wrapper.ContextualDefinitionWrapper
import maryk.core.properties.definitions.wrapper.DefinitionWrapperDelegateLoader
import maryk.core.properties.definitions.wrapper.FixedBytesDefinitionWrapper
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.definitions.wrapper.ObjectDefinitionWrapperDelegateLoader
import maryk.core.properties.definitions.wrapper.contextual
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.ValueDataObject
import maryk.core.protobuf.WireType.LENGTH_DELIMITED
import maryk.core.query.ContainsDefinitionsContext
import maryk.core.values.ObjectValues
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter

private typealias GenericValueModelDefinition = ValueObjectDefinition<*, *>

/** Definition for value object properties containing dataObjects of [DO] defined by [dataModel] of [DM] */
data class ValueObjectDefinition<DO : ValueDataObject, DM : IsValueDataModel<DO, *>>(
    override val required: Boolean = true,
    override val final: Boolean = false,
    override val unique: Boolean = false,
    override val dataModel: DM,
    override val minValue: DO? = null,
    override val maxValue: DO? = null,
    override val default: DO? = null
) :
    IsDefinitionWithDataModel<DM>,
    IsComparableDefinition<DO, IsPropertyContext>,
    IsSerializableFixedBytesEncodable<DO, IsPropertyContext>,
    IsTransportablePropertyDefinitionType<DO>,
    HasDefaultValueDefinition<DO> {

    override val propertyDefinitionType = Value
    override val wireType = LENGTH_DELIMITED
    override val byteSize = dataModel.Serializer.byteSize

    override fun calculateStorageByteLength(value: DO) = this.byteSize

    override fun writeStorageBytes(value: DO, writer: (byte: Byte) -> Unit) = value._bytes.writeBytes(writer)

    override fun readStorageBytes(length: Int, reader: () -> Byte) =
        this.dataModel.Serializer.readFromBytes(reader)

    override fun calculateTransportByteLength(value: DO) = this.dataModel.Serializer.byteSize

    override fun asString(value: DO) = value.toBase64()

    override fun fromString(string: String) = this.dataModel.Serializer.fromBase64(string)

    override fun getEmbeddedByName(name: String): IsDefinitionWrapper<*, *, *, *>? = dataModel[name]

    override fun getEmbeddedByIndex(index: UInt): IsDefinitionWrapper<*, *, *, *>? = dataModel[index]

    override fun validateWithRef(
        previousValue: DO?,
        newValue: DO?,
        refGetter: () -> IsPropertyReference<DO, IsPropertyDefinition<DO>, *>?
    ) {
        super<IsComparableDefinition>.validateWithRef(previousValue, newValue, refGetter)
        if (newValue != null) {
            this.dataModel.validate(
                refGetter = refGetter,
                dataObject = newValue
            )
        }
    }

    /** Writes a [value] to JSON with [writer] */
    override fun writeJsonValue(value: DO, writer: IsJsonLikeWriter, context: IsPropertyContext?) =
        dataModel.Serializer.writeObjectAsJson(value, writer, context)

    override fun readJson(reader: IsJsonLikeReader, context: IsPropertyContext?): DO =
        dataModel.Serializer.readJson(reader, context).toDataObject()

    override fun fromNativeType(value: Any) =
        if (value is ByteArray && value.size == this.byteSize) {
            var i = 0
            this.dataModel.Serializer.readFromBytes {
                value[i++]
            }
        } else {
            null
        }

    override fun compatibleWith(
        definition: IsPropertyDefinition<*>,
        addIncompatibilityReason: ((String) -> Unit)?
    ): Boolean {
        var compatible = super<IsComparableDefinition>.compatibleWith(definition, addIncompatibilityReason)

        (definition as? ValueObjectDefinition<*, *>)?.let {
            compatible = this.compatibleWithDefinitionWithDataModel(definition, addIncompatibilityReason) && compatible
        }

        return compatible
    }

    override fun getAllDependencies(dependencySet: MutableList<MarykPrimitive>) {
        if (!dependencySet.contains(dataModel as MarykPrimitive)) {
            dataModel.getAllDependencies(dependencySet)
            dependencySet.add(dataModel as MarykPrimitive)
        }
    }

    object Model : ContextualDataModel<ValueObjectDefinition<*, *>, Model, ContainsDefinitionsContext, ModelContext>(
        contextTransformer = { ModelContext(it) },
    ) {
        val required by boolean(1u, ValueObjectDefinition<*, *>::required, default = true)
        val final by boolean(2u, ValueObjectDefinition<*, *>::final, default = false)
        val unique by boolean(3u, ValueObjectDefinition<*, *>::unique, default = false)
        val dataModel: ContextualDefinitionWrapper<IsDataModelReference<IsValueDataModel<*, *>>, IsValueDataModel<*, *>, ModelContext, ContextualModelReferenceDefinition<IsValueDataModel<*, *>, ModelContext, ModelContext>, ValueObjectDefinition<*, *>> by contextual(
            index = 4u,
            getter = ValueObjectDefinition<*, *>::dataModel,
            definition = ContextualModelReferenceDefinition(
                contextualResolver = { context, name ->
                    context?.definitionsContext?.let {
                        @Suppress("UNCHECKED_CAST")
                        it.dataModels[name] as (Unit.() -> IsValueDataModel<*, *>)?
                            ?: throw DefNotFoundException("DataModel with name $name not found on dataModels")
                    } ?: throw ContextNotFoundException()
                }
            ),
            toSerializable = { value: IsValueDataModel<*, *>?, _: ModelContext? ->
                value?.let {
                    DataModelReference(it.Meta.name) { it }
                }
            },
            fromSerializable = {
                it?.get?.invoke(Unit)
            },
            capturer = { context, dataModel ->
                context.let {
                    context.definitionsContext?.let { modelContext ->
                        if (!modelContext.dataModels.containsKey(dataModel.name)) {
                            modelContext.dataModels[dataModel.name] = dataModel.get
                        }
                    } ?: throw ContextNotFoundException()

                    context.model = dataModel.get
                }
            }
        )
        val minValue by contextual(
            index = 5u,
            getter = ValueObjectDefinition<*, *>::minValue,
            definition = ContextualEmbeddedObjectDefinition(
                contextualResolver = { context: ModelContext? ->
                    @Suppress("UNCHECKED_CAST")
                    context?.model?.invoke(Unit) as? IsTypedObjectDataModel<Any, *, *, ModelContext>?
                        ?: throw ContextNotFoundException()
                }
            )
        )
        val maxValue by contextual(
            index = 6u,
            getter = ValueObjectDefinition<*, *>::maxValue,
            definition = ContextualEmbeddedObjectDefinition(
                contextualResolver = { context: ModelContext? ->
                    @Suppress("UNCHECKED_CAST")
                    context?.model?.invoke(Unit) as? IsTypedObjectDataModel<Any, *, *, ModelContext>?
                        ?: throw ContextNotFoundException()
                }
            )
        )
        val default by contextual(
            index = 7u,
            getter = ValueObjectDefinition<*, *>::default,
            definition = ContextualEmbeddedObjectDefinition(
                contextualResolver = { context: ModelContext? ->
                    @Suppress("UNCHECKED_CAST")
                    context?.model?.invoke(Unit) as? IsTypedObjectDataModel<Any, *, *, ModelContext>?
                        ?: throw ContextNotFoundException()
                }
            )
        )

        override fun invoke(values: ObjectValues<ValueObjectDefinition<*, *>, Model>): GenericValueModelDefinition =
            ValueObjectDefinition(
                required = values(1u),
                final = values(2u),
                unique = values(3u),
                dataModel = values(4u),
                minValue = values(5u),
                maxValue = values(6u),
                default = values(7u)
            )
    }
}

fun <DO : ValueDataObject, DM : IsValueDataModel<DO, DM>> IsValuesDataModel.valueObject(
    index: UInt,
    dataModel: DM,
    name: String? = null,
    required: Boolean = true,
    final: Boolean = false,
    unique: Boolean = false,
    minValue: DO? = null,
    maxValue: DO? = null,
    default: DO? = null,
    alternativeNames: Set<String>? = null
) = DefinitionWrapperDelegateLoader(this) { propName ->
    FixedBytesDefinitionWrapper<DO, DO, IsPropertyContext, ValueObjectDefinition<DO, DM>, Any>(
        index,
        name ?: propName,
        ValueObjectDefinition(required, final, unique, dataModel, minValue, maxValue, default),
        alternativeNames
    )
}

fun <TO: Any, DO: Any, VDO: ValueDataObject, DM : IsValueDataModel<VDO, DM>> IsObjectDataModel<DO>.valueObject(
    index: UInt,
    getter: (DO) -> TO?,
    dataModel: DM,
    name: String? = null,
    required: Boolean = true,
    final: Boolean = false,
    unique: Boolean = false,
    minValue: VDO? = null,
    maxValue: VDO? = null,
    default: VDO? = null,
    alternativeNames: Set<String>? = null
): ObjectDefinitionWrapperDelegateLoader<FixedBytesDefinitionWrapper<VDO, TO, IsPropertyContext, ValueObjectDefinition<VDO, DM>, DO>, DO, IsPropertyContext> =
    valueObject(index, getter, dataModel, name, required, final, unique, minValue, maxValue, default, alternativeNames, toSerializable = null)

fun <TO: Any, DO: Any, VDO: ValueDataObject, DM : IsValueDataModel<VDO, DM>, CX: IsPropertyContext> IsObjectDataModel<DO>.valueObject(
    index: UInt,
    getter: (DO) -> TO?,
    dataModel: DM,
    name: String? = null,
    required: Boolean = true,
    final: Boolean = false,
    unique: Boolean = false,
    minValue: VDO? = null,
    maxValue: VDO? = null,
    default: VDO? = null,
    alternativeNames: Set<String>? = null,
    toSerializable: (Unit.(TO?, CX?) -> VDO?)? = null,
    fromSerializable: (Unit.(VDO?) -> TO?)? = null,
    shouldSerialize: (Unit.(Any) -> Boolean)? = null,
    capturer: (Unit.(CX, VDO) -> Unit)? = null
) = ObjectDefinitionWrapperDelegateLoader(this) { propName ->
    FixedBytesDefinitionWrapper(
        index,
        name ?: propName,
        ValueObjectDefinition(required, final, unique, dataModel, minValue, maxValue, default),
        alternativeNames,
        getter = getter,
        capturer = capturer,
        toSerializable = toSerializable,
        fromSerializable = fromSerializable,
        shouldSerialize = shouldSerialize
    )
}
