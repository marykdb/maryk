package maryk.core.properties.definitions.wrapper

import maryk.core.exceptions.DefNotFoundException
import maryk.core.inject.Inject
import maryk.core.models.SimpleObjectDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsSerializablePropertyDefinition
import maryk.core.properties.definitions.IsTransportablePropertyDefinitionType
import maryk.core.properties.definitions.PropertyDefinitionType
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.internalMultiType
import maryk.core.properties.definitions.mapOfPropertyDefEmbeddedObjectDefinitions
import maryk.core.properties.definitions.mapOfPropertyDefWrappers
import maryk.core.properties.definitions.number
import maryk.core.properties.definitions.set
import maryk.core.properties.definitions.string
import maryk.core.properties.exceptions.ValidationException
import maryk.core.properties.graph.IsPropRefGraphNode
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.numeric.UInt32
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter
import maryk.core.query.ContainsDefinitionsContext
import maryk.core.query.RequestContext
import maryk.core.values.MutableValueItems
import maryk.core.values.SimpleObjectValues
import maryk.core.values.ValueItem
import maryk.core.yaml.readNamedIndexField
import maryk.core.yaml.writeNamedIndexField
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.yaml.IsYamlReader
import maryk.yaml.YamlWriter

typealias AnyDefinitionWrapper = IsDefinitionWrapper<Any, Any, IsPropertyContext, Any>
typealias AnyOutDefinitionWrapper = IsDefinitionWrapper<out Any, out Any, IsPropertyContext, Any>

/**
 * Wraps a Property Definition of type [T] to give it more context [CX] about
 * DataObject [DO] which contains this Definition.
 */
interface IsDefinitionWrapper<T : Any, TO : Any, in CX : IsPropertyContext, in DO> :
    IsSerializablePropertyDefinition<T, CX>,
    IsPropRefGraphNode<PropertyDefinitions> {
    override val index: UInt
    val name: String
    val alternativeNames: Set<String>?
    val definition: IsSerializablePropertyDefinition<T, CX>
    val getter: (DO) -> TO?
    val capturer: (Unit.(CX, T) -> Unit)?
    val toSerializable: (Unit.(TO?, CX?) -> T?)?
    val fromSerializable: (Unit.(T?) -> TO?)?
    val shouldSerialize: (Unit.(Any) -> Boolean)?

    /** Create an index [value] pair for maps */
    infix fun withNotNull(value: Any): ValueItem {
        return ValueItem(this.index, value)
    }

    /** Create an index [value] pair for maps */
    infix fun with(value: TO?) = value?.let {
        val serializedValue = try {
            if (shouldSerialize == null || shouldSerialize!!(Unit, value)) {
                toSerializable?.let { serializer ->
                    serializer(Unit, value, null) ?: value
                } ?: value
            } else {
                value
            }
        } catch (_: Throwable) {
            value
        }

        if (serializedValue != null) {
            ValueItem(this.index, serializedValue)
        } else null
    }

    /** Create an index [value] pair for maps */
    infix fun injectWith(value: Inject<*, *>?) = value?.let {
        ValueItem(this.index, value)
    }

    /** Create an index [value] pair for maps */
    infix fun withSerializable(value: T?) = value?.let {
        ValueItem(this.index, value)
    }

    /** Get a reference to this definition inside [parentRef] */
    fun ref(parentRef: AnyPropertyReference? = null): IsPropertyReference<T, IsPropertyDefinition<T>, *>

    /**
     * Validates [newValue] against [previousValue] on propertyDefinition and if fails creates
     * reference with [parentRefFactory]
     * @throws ValidationException when encountering invalid new value
     */
    fun validate(previousValue: T? = null, newValue: T?, parentRefFactory: () -> AnyPropertyReference? = { null }) {
        this.validateWithRef(previousValue, newValue) { this.ref(parentRefFactory()) }
    }

    /** Calculates the needed byte size to transport [value] within optional [context] and caches it with [cacher] */
    fun calculateTransportByteLengthWithKey(value: T, cacher: WriteCacheWriter, context: CX? = null) =
        this.calculateTransportByteLengthWithKey(this.index, value, cacher, context)

    /**
     * Writes [value] to bytes with [writer] for transportation and adds the key with tag and wire type.
     * Uses the [cacheGetter] to get length.
     * Optionally pass a [context] to write more complex properties which depend on other properties
     */
    fun writeTransportBytesWithKey(
        value: T,
        cacheGetter: WriteCacheReader,
        writer: (byte: Byte) -> Unit,
        context: CX? = null
    ) =
        this.writeTransportBytesWithKey(this.index, value, cacheGetter, writer, context)

    /** Get the property from the [dataObject] and serialize it for transportation */
    fun getPropertyAndSerialize(dataObject: DO, context: CX?): T? {
        @Suppress("UNCHECKED_CAST")
        this.toSerializable?.let {
            return it.invoke(Unit, this.getter(dataObject), context)
        } ?: return this.getter(dataObject) as T?
    }

    /** Capture the [value] in the [context] if needed */
    fun capture(context: CX?, value: T) {
        if (this.capturer != null && context != null) {
            this.capturer!!.invoke(Unit, context, value)
        }
    }

    /**
     * Checks if this property definition wrapper is compatible with passed definition wrapper.
     * It checks the index, names and alternative names and the definition itself.
     * It is compatible if any property validated by the passed definition
     * is accepted by this definition.
     *
     * Validation rules which are less strict are accepted but more strict or incompatible rules are not.
     */
    fun compatibleWith(wrapper: IsDefinitionWrapper<*, *, *, *>, addIncompatibilityReason: ((String) -> Unit)? = null): Boolean {
        var compatible = true

        if (index != wrapper.index) {
            addIncompatibilityReason?.invoke("$index: Property index ${wrapper.index} of passed property was not the same as on this definition")
            compatible = false
        }

        if (name != wrapper.name && alternativeNames?.contains(wrapper.name) != true) {
            addIncompatibilityReason?.invoke("$index: Property name ${wrapper.name} not found on this definition ($name) or in alternative names")
            compatible = false
        }

        wrapper.alternativeNames?.forEach { altName ->
            if (alternativeNames?.contains(altName) != true && name != altName) {
                addIncompatibilityReason?.invoke("$index: Alternative property name ${wrapper.name} not found on this definition name ($name) or in alternative names")
                compatible = false
            }
        }

        if (!this.definition.compatibleWith(wrapper.definition) { addIncompatibilityReason?.invoke("$index: $it") }) {
            compatible = false
        }

        return compatible
    }

    private object Properties :
        ObjectPropertyDefinitions<AnyOutDefinitionWrapper>() {
        val index by number(1u, IsDefinitionWrapper<*, *, *, *>::index, UInt32)
        val name by string(2u, IsDefinitionWrapper<*, *, *, *>::name)
        val alternativeNames by set(
            index = 3u,
            getter = IsDefinitionWrapper<*, *, *, *>::alternativeNames,
            valueDefinition = StringDefinition()
        )
        val definition by internalMultiType(
            index = 4u,
            getter = {
                val def = it.definition as IsTransportablePropertyDefinitionType<*>
                TypedValue(def.propertyDefinitionType, def)
            },
            typeEnum = PropertyDefinitionType,
            definitionMap = mapOfPropertyDefEmbeddedObjectDefinitions
        )
    }

    object Model :
        SimpleObjectDataModel<AnyOutDefinitionWrapper, ObjectPropertyDefinitions<AnyOutDefinitionWrapper>>(
            properties = Properties
        ) {
        override fun invoke(values: SimpleObjectValues<AnyOutDefinitionWrapper>): AnyOutDefinitionWrapper {
            val typedDefinition =
                values<TypedValue<PropertyDefinitionType, IsTransportablePropertyDefinitionType<*>>>(
                    Properties.definition.index
                )
            val type = typedDefinition.type

            return mapOfPropertyDefWrappers[type]?.invoke(
                values(Properties.index.index),
                values(Properties.name.index),
                values(Properties.alternativeNames.index),
                typedDefinition.value
            ) ?: throw DefNotFoundException("Property type $type not found")
        }

        override fun writeJson(
            obj: AnyOutDefinitionWrapper,
            writer: IsJsonLikeWriter,
            context: IsPropertyContext?
        ) {
            // When writing YAML, use YAML optimized format with complex field names
            if (writer is YamlWriter) {
                val typedDefinition =
                    Properties.definition.getPropertyAndSerialize(obj, context as ContainsDefinitionsContext)
                        ?: throw DefNotFoundException("Unknown type ${obj.definition} so cannot serialize contents")

                writer.writeNamedIndexField(obj.name, obj.index, obj.alternativeNames)

                Properties.definition.writeJsonValue(typedDefinition, writer, context)
            } else {
                super.writeJson(obj, writer, context)
            }
        }

        override fun readJson(
            reader: IsJsonLikeReader,
            context: IsPropertyContext?
        ): SimpleObjectValues<AnyOutDefinitionWrapper> {
            // When reading YAML, use YAML optimized format with complex field names
            return if (reader is IsYamlReader) {
                val valueMap = MutableValueItems()

                reader.readNamedIndexField(valueMap, Properties.name, Properties.index, Properties.alternativeNames)
                valueMap[Properties.definition.index] =
                    Properties.definition.readJson(reader, context as ContainsDefinitionsContext)

                this.values(context as? RequestContext) {
                    valueMap
                }
            } else {
                super.readJson(reader, context)
            }
        }
    }
}
