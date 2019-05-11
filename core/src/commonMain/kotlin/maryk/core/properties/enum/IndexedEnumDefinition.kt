package maryk.core.properties.enum

import maryk.core.definitions.MarykPrimitive
import maryk.core.definitions.PrimitiveType.EnumDefinition
import maryk.core.exceptions.DefNotFoundException
import maryk.core.exceptions.SerializationException
import maryk.core.extensions.bytes.initUInt
import maryk.core.extensions.bytes.writeBytes
import maryk.core.models.ContextualDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.IsFixedStorageBytesEncodable
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MapDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.SingleOrListDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.contextual.ContextCaptureDefinition
import maryk.core.properties.types.numeric.UInt32
import maryk.core.query.ContainsDefinitionsContext
import maryk.core.values.ObjectValues
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken.StartDocument
import maryk.json.JsonToken.Value
import maryk.lib.exceptions.ParseException
import kotlin.reflect.KClass

/** Enum Definitions with a [name] and [cases] */
open class IndexedEnumDefinition<E : IndexedEnum> internal constructor(
    internal val optionalCases: (() -> Array<E>)?,
    final override val name: String,
    val reservedIndices: List<UInt>? = null,
    val reservedNames: List<String>? = null,
    private val unknownCreator: ((UInt, String) -> E)? = null
) : MarykPrimitive,
    IsPropertyDefinition<E>,
    IsFixedStorageBytesEncodable<E> {

    override val primitiveType = EnumDefinition

    override val byteSize = 2
    override val required = true
    override val final = true

    // Because of compilation issue in Native this map contains IndexedEnum<E> instead of E as value
    private val valueByString: Map<String, E> by lazy<Map<String, E>> {
        mutableMapOf<String, E>().also { output ->
            cases().forEach {
                output[it.name] = it
                it.alternativeNames?.forEach { name: String ->
                    if (output.containsKey(name)) throw ParseException("Enum ${this.name} already has a case for $name")
                    output[name] = it
                }
            }
        }
    }

    // Because of compilation issue in Native this map contains IndexedEnum<E> instead of E as value
    private val valueByIndex: Map<UInt, E> by lazy {
        cases().associate { Pair(it.index, it) }
    }

    val cases get() = optionalCases!!

    constructor(
        enumClass: KClass<E>,
        values: () -> Array<E>,
        reservedIndices: List<UInt>? = null,
        reservedNames: List<String>? = null,
        unknownCreator: ((UInt, String) -> E)? = null
    ) : this(
        name = enumClass.simpleName ?: throw DefNotFoundException("No name for enum class"),
        optionalCases = values,
        reservedIndices = reservedIndices,
        reservedNames = reservedNames,
        unknownCreator = unknownCreator
    )

    internal constructor(
        name: String,
        values: () -> Array<E>,
        reservedIndices: List<UInt>? = null,
        reservedNames: List<String>? = null,
        unknownCreator: ((UInt, String) -> E)? = null
    ) : this(
        name = name,
        optionalCases = values,
        reservedIndices = reservedIndices,
        reservedNames = reservedNames,
        unknownCreator = unknownCreator
    )

    override fun getEmbeddedByName(name: String): Nothing? = null
    override fun getEmbeddedByIndex(index: UInt): Nothing? = null

    /** Check the enum values */
    fun check() {
        this.reservedIndices?.let {
            this.optionalCases?.invoke()?.forEach { case ->
                require(!reservedIndices.contains(case.index)) {
                    "Enum $name has ${case.index} defined in option ${case.name} while it is reserved"
                }
            }
        }
        this.reservedNames?.let {
            this.optionalCases?.invoke()?.forEach { case ->
                require(!reservedNames.contains(case.name)) {
                    "Enum $name has a reserved name defined ${case.name}"
                }
            }
        }
    }

    /** Get Enum value by [index] */
    fun resolve(index: UInt) = valueByIndex[index] ?: unknownCreator?.invoke(index, "%Unknown")

    /** Get Enum value by [name] */
    fun resolve(name: String): E? =
        if (name.endsWith(')')) {
            val found = name.split('(', ')')
            try {
                val index = found[1].toUInt()
                val valueName = found[0]

                val typeByName = valueByString[valueName]
                if (typeByName != null && typeByName.index != index) {
                    throw ParseException("Non matching name $valueName with index $index, expected ${typeByName.index}")
                }

                valueByIndex[index] ?: unknownCreator?.invoke(index, valueName)
            } catch (e: NumberFormatException) {
                throw ParseException("Not a correct number between brackets in type $name")
            }
        } else {
            valueByString[name] ?: unknownCreator?.invoke(0u, name)
        }

    override fun readStorageBytes(length: Int, reader: () -> Byte): E {
        val index = initUInt(reader, 2)
        return resolve(index)
            ?: throw DefNotFoundException("Unknown index $index for $name")
    }

    override fun writeStorageBytes(value: E, writer: (byte: Byte) -> Unit) {
        value.index.writeBytes(writer, 2)
    }

    internal object Properties : ObjectPropertyDefinitions<IndexedEnumDefinition<IndexedEnum>>() {
        val name = add(1u, "name",
            ContextCaptureDefinition(
                definition = StringDefinition(),
                capturer = { context: EnumNameContext?, value ->
                    context?.let {
                        it.name = value
                    }
                }
            ),
            IndexedEnumDefinition<*>::name
        )

        @Suppress("UNCHECKED_CAST")
        val cases = add(2u, "cases",
            MapDefinition(
                keyDefinition = NumberDefinition(
                    type = UInt32,
                    minValue = 1u
                ),
                valueDefinition = SingleOrListDefinition(
                    valueDefinition = StringDefinition()
                )
            ) as MapDefinition<UInt, List<String>, EnumNameContext>,
            IndexedEnumDefinition<*>::cases,
            toSerializable = { value, context ->
                // If Enum was defined before and is thus available in context, don't include the cases again
                val toReturnNull = context?.let { enumNameContext ->
                    if (enumNameContext.isOriginalDefinition == true) {
                        false
                    } else {
                        enumNameContext.definitionsContext?.let {
                            if (it.enums[enumNameContext.name] == null) {
                                enumNameContext.isOriginalDefinition = true
                                false
                            } else {
                                true
                            }
                        }
                    }
                } ?: false

                if (toReturnNull) {
                    null
                } else {
                    value?.invoke()?.map { v: IndexedEnum ->
                        // Combine name and alternative names into a list
                        val names: List<String> = v.alternativeNames?.toMutableList()?.also {
                            it.add(0, v.name)
                        } ?: listOf(v.name)
                        Pair(v.index, names)
                    }?.toMap() as Map<UInt, List<String>>
                }
            },
            fromSerializable = {
                {
                    it?.map { (key, value) ->
                        IndexedEnumComparable(
                            key,
                            value.first(),
                            value.subList(1, value.size).toSet()
                        )
                    }?.toTypedArray() as Array<IndexedEnum>
                }
            }
        )

        init {
            add(
                3u, "reservedIndices",
                ListDefinition(
                    valueDefinition = NumberDefinition(
                        type = UInt32,
                        minValue = 1u
                    )
                ),
                IndexedEnumDefinition<*>::reservedIndices
            )

            add(
                4u, "reservedNames",
                ListDefinition(
                    valueDefinition = StringDefinition()
                ),
                IndexedEnumDefinition<*>::reservedNames
            )
        }
    }

    internal object Model :
        ContextualDataModel<IndexedEnumDefinition<IndexedEnum>, Properties, ContainsDefinitionsContext, EnumNameContext>(
            properties = Properties,
            contextTransformer = { EnumNameContext(it) }
        ) {
        override fun invoke(values: ObjectValues<IndexedEnumDefinition<IndexedEnum>, Properties>) =
            IndexedEnumDefinition<IndexedEnum>(
                name = values(1u),
                optionalCases = values(2u),
                reservedIndices = values(3u),
                reservedNames = values(4u),
                unknownCreator = { index, name -> IndexedEnumComparable(index, name) }
            )

        override fun writeJson(
            values: ObjectValues<IndexedEnumDefinition<IndexedEnum>, Properties>,
            writer: IsJsonLikeWriter,
            context: EnumNameContext?
        ) {
            throw SerializationException("Cannot write definitions from Values")
        }

        override fun writeJson(
            obj: IndexedEnumDefinition<IndexedEnum>,
            writer: IsJsonLikeWriter,
            context: EnumNameContext?
        ) {
            if (context?.definitionsContext?.enums?.containsKey(obj.name) == true) {
                // Write a single string name if no options was defined
                val value = Properties.name.getPropertyAndSerialize(obj, context)
                    ?: throw ParseException("Missing requests in Requests")
                Properties.name.writeJsonValue(value, writer, context)
                Properties.name.capture(context, value)
            } else {
                // Only skip when DefinitionsContext was set
                when {
                    context?.definitionsContext != null && context.definitionsContext.currentDefinitionName == obj.name -> {
                        super.writeJson(obj, writer, context, skip = listOf(Properties.name))
                        context.definitionsContext.currentDefinitionName = ""
                    }
                    else -> {
                        super.writeJson(obj, writer, context)
                    }
                }
            }
        }

        override fun readJson(
            reader: IsJsonLikeReader,
            context: EnumNameContext?
        ): ObjectValues<IndexedEnumDefinition<IndexedEnum>, Properties> {
            if (reader.currentToken == StartDocument) {
                reader.nextToken()
            }

            return if (reader.currentToken is Value<*>) {
                val value = Properties.name.readJson(reader, context)
                Properties.name.capture(context, value)

                this.values {
                    mapNonNulls(
                        name withSerializable value
                    )
                }
            } else {
                super.readJson(reader, context)
            }
        }

        override fun readJsonToMap(reader: IsJsonLikeReader, context: EnumNameContext?) =
            context?.definitionsContext?.currentDefinitionName.let { name ->
                when (name) {
                    null, "" -> super.readJsonToMap(reader, context)
                    else -> {
                        context?.definitionsContext?.currentDefinitionName = ""
                        super.readJsonToMap(reader, context).also {
                            it[Properties.name.index] = name
                        }
                    }
                }
            }
    }
}
