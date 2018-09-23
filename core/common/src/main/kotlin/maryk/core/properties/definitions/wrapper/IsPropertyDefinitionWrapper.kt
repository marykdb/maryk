package maryk.core.properties.definitions.wrapper

import maryk.core.exceptions.DefNotFoundException
import maryk.core.models.IsDataModel
import maryk.core.models.SimpleObjectDataModel
import maryk.core.objects.SimpleObjectValues
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsSerializablePropertyDefinition
import maryk.core.properties.definitions.IsTransportablePropertyDefinitionType
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.PropertyDefinitionType
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.mapOfPropertyDefEmbeddedObjectDefinitions
import maryk.core.properties.definitions.mapOfPropertyDefWrappers
import maryk.core.properties.exceptions.ValidationException
import maryk.core.properties.graph.IsPropRefGraphable
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.Inject
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.numeric.UInt32
import maryk.core.properties.types.numeric.toUInt32
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter
import maryk.core.query.ContainsDefinitionsContext
import maryk.core.query.RequestContext
import maryk.core.yaml.readNamedIndexField
import maryk.core.yaml.writeNamedIndexField
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.yaml.IsYamlReader
import maryk.yaml.YamlWriter

/**
 * Wraps a Property Definition of type [T] to give it more context [CX] about
 * DataObject [DO] which contains this Definition.
 */
interface IsPropertyDefinitionWrapper<T: Any, TO: Any, in CX:IsPropertyContext, in DO> :
    IsSerializablePropertyDefinition<T, CX>,
    IsPropRefGraphable<IsDataModel<*>>
{
    val index: Int
    val name: String
    val definition: IsSerializablePropertyDefinition<T, CX>
    val getter: (DO) -> TO?
    val capturer: ((CX, T) -> Unit)?
    val toSerializable: ((TO?, CX?) -> T?)?
    val fromSerializable: ((T?) -> TO?)?

    /** Create an index [value] pair for maps */
    infix fun withNotNull(value: Any): Pair<Int, Any> {
        return Pair(this.index, value)
    }

    /** Create an index [value] pair for maps */
    infix fun with(value: TO?) = value?.let {
        val serializedValue = try {
            toSerializable?.let { serializer ->
                serializer(value, null) ?: value
            } ?: value
        } catch (_: Throwable) {
            value
        }

        Pair(this.index, serializedValue)
    }

    /** Create an index [value] pair for maps */
    infix fun injectWith(value: Inject<*, *>?) = value?.let {
        Pair(this.index, value)
    }

    /** Create an index [value] pair for maps */
    infix fun withSerializable(value: T?) = value?.let {
        Pair(this.index, value)
    }

    /** Get a reference to this definition inside [parentRef] */
    fun getRef(parentRef: AnyPropertyReference? = null): IsPropertyReference<T, *, *>

    /**
     * Validates [newValue] against [previousValue] on propertyDefinition and if fails creates
     * reference with [parentRefFactory]
     * @throws ValidationException when encountering invalid new value
     */
    fun validate(previousValue: T? = null, newValue: T?, parentRefFactory: () -> AnyPropertyReference? = { null }) {
        this.validateWithRef(previousValue, newValue) { this.getRef(parentRefFactory()) }
    }

    /** Calculates the needed byte size to transport [value] within optional [context] and caches it with [cacher] */
    fun calculateTransportByteLengthWithKey(value: T, cacher: WriteCacheWriter, context: CX? = null) =
        this.calculateTransportByteLengthWithKey(this.index, value, cacher, context)

    /**
     * Writes [value] to bytes with [writer] for transportation and adds the key with tag and wire type.
     * Uses the [cacheGetter] to get length.
     * Optionally pass a [context] to write more complex properties which depend on other properties
     */
    fun writeTransportBytesWithKey(value: T, cacheGetter: WriteCacheReader, writer: (byte: Byte) -> Unit, context: CX? = null) =
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
        if(this.capturer != null && context != null) {
            this.capturer!!.invoke(context, value)
        }
    }

    companion object {
        private fun <DO:Any> addIndex(definitions: ObjectPropertyDefinitions<DO>, getter: (DO) -> Int) =
            definitions.add(1, "index",
                NumberDefinition(type = UInt32),
                getter,
                toSerializable = { value, _ -> value?.toUInt32() },
                fromSerializable = { it?.toInt() }
            )

        private fun <DO:Any> addName(definitions: ObjectPropertyDefinitions<DO>, getter: (DO) -> String) =
            definitions.add(2, "name", StringDefinition(), getter)

        private fun <DO:Any> addDefinition(definitions: ObjectPropertyDefinitions<DO>, getter: (DO) -> IsSerializablePropertyDefinition<*, *>) =
            definitions.add(3, "definition",
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

    private object Properties: ObjectPropertyDefinitions<IsPropertyDefinitionWrapper<out Any, out Any, IsPropertyContext, Any>>() {
        val index = IsPropertyDefinitionWrapper.addIndex(this, IsPropertyDefinitionWrapper<*, *, *, *>::index)
        val name = IsPropertyDefinitionWrapper.addName(this, IsPropertyDefinitionWrapper<*, *, *, *>::name)
        val definition = IsPropertyDefinitionWrapper.addDefinition(this, IsPropertyDefinitionWrapper<*, *, *, *>::definition)
    }

    object Model : SimpleObjectDataModel<IsPropertyDefinitionWrapper<out Any, out Any, IsPropertyContext, Any>, ObjectPropertyDefinitions<IsPropertyDefinitionWrapper<out Any, out Any, IsPropertyContext, Any>>>(
        properties = Properties
    ) {
        override fun invoke(map: SimpleObjectValues<IsPropertyDefinitionWrapper<out Any, out Any, IsPropertyContext, Any>>): IsPropertyDefinitionWrapper<out Any, out Any, IsPropertyContext, Any> {
            val typedDefinition =
                map<TypedValue<PropertyDefinitionType, IsPropertyDefinition<Any>>>(3)
            val type = typedDefinition.type

            return mapOfPropertyDefWrappers[type]?.invoke(
                map(1),
                map(2),
                typedDefinition.value
            ) { _: Any -> null } ?: throw DefNotFoundException("Property type $type not found")
        }

        override fun writeJson(
            obj: IsPropertyDefinitionWrapper<out Any, out Any, IsPropertyContext, Any>,
            writer: IsJsonLikeWriter,
            context: IsPropertyContext?
        ) {
            // When writing YAML, use YAML optimized format with complex field names
            if (writer is YamlWriter) {
                val typedDefinition = Properties.definition.getPropertyAndSerialize(obj, context as ContainsDefinitionsContext)
                        ?: throw Exception("Unknown type ${obj.definition} so cannot serialize contents")

                writer.writeNamedIndexField(obj.name, obj.index)

                Properties.definition.writeJsonValue(typedDefinition, writer, context)
            } else {
                super.writeJson(obj, writer, context)
            }
        }

        override fun readJson(reader: IsJsonLikeReader, context: IsPropertyContext?): SimpleObjectValues<IsPropertyDefinitionWrapper<out Any, out Any, IsPropertyContext, Any>> {
            // When reading YAML, use YAML optimized format with complex field names
            return if (reader is IsYamlReader) {
                val valueMap: MutableMap<Int, Any?> = mutableMapOf()

                reader.readNamedIndexField(valueMap, Properties.name, Properties.index)
                valueMap[Properties.definition.index] = Properties.definition.readJson(reader, context as ContainsDefinitionsContext)

                this.map(context as? RequestContext) {
                    valueMap
                }
            } else {
                super.readJson(reader, context)
            }
        }
    }
}
