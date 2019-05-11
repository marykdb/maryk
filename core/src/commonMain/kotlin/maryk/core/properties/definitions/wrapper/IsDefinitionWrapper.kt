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
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.PropertyDefinitionType
import maryk.core.properties.definitions.SetDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.mapOfPropertyDefEmbeddedObjectDefinitions
import maryk.core.properties.definitions.mapOfPropertyDefWrappers
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

typealias AnyPropertyDefinitionWrapper = IsDefinitionWrapper<Any, Any, IsPropertyContext, Any>

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
    val capturer: ((CX, T) -> Unit)?
    val toSerializable: ((TO?, CX?) -> T?)?
    val fromSerializable: ((T?) -> TO?)?
    val shouldSerialize: ((Any) -> Boolean)?

    /** Create an index [value] pair for maps */
    infix fun withNotNull(value: Any): ValueItem {
        return ValueItem(this.index, value)
    }

    /** Create an index [value] pair for maps */
    infix fun with(value: TO?) = value?.let {
        val serializedValue = try {
            if (shouldSerialize == null || shouldSerialize!!(value)) {
                toSerializable?.let { serializer ->
                    serializer(value, null) ?: value
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
            return it.invoke(this.getter(dataObject), context)
        } ?: return this.getter(dataObject) as T?
    }

    /** Capture the [value] in the [context] if needed */
    fun capture(context: CX?, value: T) {
        if (this.capturer != null && context != null) {
            this.capturer!!.invoke(context, value)
        }
    }

    companion object {
        private fun <DO : Any> addIndex(definitions: ObjectPropertyDefinitions<DO>, getter: (DO) -> UInt) =
            definitions.add(
                1u, "index",
                NumberDefinition(type = UInt32),
                getter = getter
            )

        private fun <DO : Any> addName(definitions: ObjectPropertyDefinitions<DO>, getter: (DO) -> String) =
            definitions.add(2u, "name", StringDefinition(), getter)

        private fun <DO : Any> addAlternativeNames(
            definitions: ObjectPropertyDefinitions<DO>,
            getter: (DO) -> Set<String>?
        ) =
            definitions.add(
                3u, "alternativeNames",
                SetDefinition(
                    valueDefinition = StringDefinition()
                ),
                getter = getter
            )

        private fun <DO : Any> addDefinition(
            definitions: ObjectPropertyDefinitions<DO>,
            getter: (DO) -> IsSerializablePropertyDefinition<*, *>
        ) =
            definitions.add(
                4u, "definition",
                MultiTypeDefinition(
                    typeEnum = PropertyDefinitionType,
                    definitionMap = mapOfPropertyDefEmbeddedObjectDefinitions
                ),
                getter = {
                    val def = getter(it) as IsTransportablePropertyDefinitionType<*>
                    TypedValue(def.propertyDefinitionType, def)
                }
            )
    }

    private object Properties :
        ObjectPropertyDefinitions<IsDefinitionWrapper<out Any, out Any, IsPropertyContext, Any>>() {
        val index = addIndex(this, IsDefinitionWrapper<*, *, *, *>::index)
        val name = addName(this, IsDefinitionWrapper<*, *, *, *>::name)
        val alternativeNames = addAlternativeNames(this, IsDefinitionWrapper<*, *, *, *>::alternativeNames)
        val definition =
            addDefinition(this, IsDefinitionWrapper<*, *, *, *>::definition)
    }

    object Model :
        SimpleObjectDataModel<IsDefinitionWrapper<out Any, out Any, IsPropertyContext, Any>, ObjectPropertyDefinitions<IsDefinitionWrapper<out Any, out Any, IsPropertyContext, Any>>>(
            properties = Properties
        ) {
        override fun invoke(values: SimpleObjectValues<IsDefinitionWrapper<out Any, out Any, IsPropertyContext, Any>>): IsDefinitionWrapper<out Any, out Any, IsPropertyContext, Any> {
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
            obj: IsDefinitionWrapper<out Any, out Any, IsPropertyContext, Any>,
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
        ): SimpleObjectValues<IsDefinitionWrapper<out Any, out Any, IsPropertyContext, Any>> {
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
