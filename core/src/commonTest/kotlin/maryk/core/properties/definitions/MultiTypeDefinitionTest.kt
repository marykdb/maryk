package maryk.core.properties.definitions

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.exceptions.DefNotFoundException
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.MultiTypeEnumImpl.INT
import maryk.core.properties.definitions.MultiTypeEnumImpl.LIST
import maryk.core.properties.definitions.MultiTypeEnumImpl.MAP
import maryk.core.properties.definitions.MultiTypeEnumImpl.MULTI
import maryk.core.properties.definitions.MultiTypeEnumImpl.SET
import maryk.core.properties.definitions.MultiTypeEnumImpl.STRING
import maryk.core.properties.definitions.MultiTypeEnumImpl.UNUSED
import maryk.core.properties.definitions.wrapper.MultiTypeDefinitionWrapper
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.enum.IndexedEnumImpl
import maryk.core.properties.enum.MultiTypeEnum
import maryk.core.properties.exceptions.AlreadySetException
import maryk.core.properties.exceptions.InvalidValueException
import maryk.core.properties.exceptions.OutOfRangeException
import maryk.core.properties.exceptions.ValidationUmbrellaException
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.numeric.SInt16
import maryk.core.properties.types.numeric.SInt32
import maryk.core.protobuf.WriteCache
import maryk.test.ByteCollector
import maryk.test.models.SimpleMarykTypeEnum
import maryk.test.models.SimpleMarykTypeEnum.S1
import maryk.test.models.SimpleMarykTypeEnum.S2
import maryk.test.shouldBe
import maryk.test.shouldThrow
import kotlin.test.Test

private sealed class MultiTypeEnumImpl(
    index: UInt
) : IndexedEnumImpl<MultiTypeEnumImpl>(index), MultiTypeEnum<Any> {
    object STRING: MultiTypeEnumImpl(1u)
    object INT: MultiTypeEnumImpl(2u)
    object LIST: MultiTypeEnumImpl(3u)
    object SET: MultiTypeEnumImpl(4u)
    object MAP: MultiTypeEnumImpl(5u)
    object MULTI: MultiTypeEnumImpl(6u)
    object UNUSED: MultiTypeEnumImpl(99u)

    companion object : IndexedEnumDefinition<MultiTypeEnumImpl>(MultiTypeEnumImpl::class, { arrayOf(STRING, INT, LIST, SET, MAP, MULTI, UNUSED) })
}

internal class MultiTypeDefinitionTest {
    private val stringDef = StringDefinition(
        regEx = "#.*"
    )

    private val intDef = NumberDefinition(
        type = SInt32,
        maxValue = 1000
    )

    private val listDef = ListDefinition(
        valueDefinition = stringDef
    )

    private val setDef = SetDefinition(
        valueDefinition = stringDef
    )
    private val mapDef = MapDefinition(
        keyDefinition = intDef,
        valueDefinition = stringDef
    )
    private val subMultiDef = MultiTypeDefinition<SimpleMarykTypeEnum<*>, Any, IsPropertyContext>(
        typeEnum = SimpleMarykTypeEnum,
        definitionMap = mapOf(
            S1 to stringDef,
            S2 to NumberDefinition(
                type = SInt16,
                maxValue = 1000.toShort()
            )
        )
    )

    private val def = MultiTypeDefinition<MultiTypeEnumImpl, Any, IsPropertyContext>(
        typeEnum = MultiTypeEnumImpl,
        definitionMap = mapOf(
            STRING to stringDef,
            INT to intDef,
            LIST to listDef,
            SET to setDef,
            MAP to mapDef,
            MULTI to subMultiDef
        )
    )

    private val defMaxDefined = MultiTypeDefinition<MultiTypeEnumImpl, Any, IsPropertyContext>(
        final = true,
        required = false,
        typeEnum = MultiTypeEnumImpl,
        definitionMap = mapOf(
            STRING to stringDef,
            INT to intDef,
            LIST to listDef,
            SET to setDef,
            MAP to mapDef,
            MULTI to subMultiDef
        ),
        default = TypedValue(STRING, "test")
    )

    private val defWrapper = MultiTypeDefinitionWrapper<MultiTypeEnumImpl, Any, Any, IsPropertyContext, Any>(
        1u, "multi", def
    )

    private val multisToTest = arrayOf<TypedValue<MultiTypeEnumImpl, Any>>(
        TypedValue(STRING, "#test"),
        TypedValue(INT, 400),
        TypedValue(LIST, listOf("#a", "#b", "#c")),
        TypedValue(SET, setOf("#a", "#b", "#c")),
        TypedValue(MAP, mapOf(1 to "#a", 2 to "#b", 3 to "#c")),
        TypedValue(MULTI, TypedValue(S1, "#test"))
    )

    @Test
    fun getProperties() {
        def.definitionMap[STRING] shouldBe stringDef
        def.definitionMap[INT] shouldBe intDef
        def.definitionMap[LIST] shouldBe listDef
        def.definitionMap[SET] shouldBe setDef
        def.definitionMap[MAP] shouldBe mapDef
    }

    @Test
    fun validateContent() {
        def.validateWithRef(newValue = TypedValue(STRING, "#test"))
        def.validateWithRef(newValue = TypedValue(INT, 400))
        def.validateWithRef(newValue = TypedValue(LIST, listOf("#a", "#b", "#c")))
        def.validateWithRef(newValue = TypedValue(SET, setOf("#a", "#b", "#c")))
        def.validateWithRef(newValue = TypedValue(MAP, mapOf(1 to "#a")))

        shouldThrow<OutOfRangeException> {
            def.validateWithRef(newValue = TypedValue(INT, 3000))
        }
        shouldThrow<InvalidValueException> {
            def.validateWithRef(newValue = TypedValue(STRING, "WRONG"))
        }
        shouldThrow<ValidationUmbrellaException> {
            def.validateWithRef(newValue = TypedValue(LIST, listOf("WRONG")))
        }
        shouldThrow<ValidationUmbrellaException> {
            def.validateWithRef(newValue = TypedValue(SET, setOf("WRONG")))
        }
        shouldThrow<ValidationUmbrellaException> {
            def.validateWithRef(newValue = TypedValue(MAP, mapOf(1 to "WRONG")))
        }
        shouldThrow<InvalidValueException> {
            def.validateWithRef(newValue = TypedValue(MULTI, TypedValue(S1, "WRONG")))
        }

        shouldThrow<AlreadySetException> {
            def.validateWithRef(
                previousValue = TypedValue(STRING, "WRONG"),
                newValue = TypedValue(INT, 400),
                refGetter = { defWrapper.ref() }
            )
        }.reference.toString() shouldBe "multi.*INT"
    }

    @Test
    fun resolveReferenceByName() {
        def.resolveReferenceByName("*STRING") shouldBe def.typedValueRef(STRING, null)
        def.resolveReferenceByName("*") shouldBe def.typeRef(null)
    }

    @Test
    fun resolveReferenceFromStorageByAnyTypeName() {
        writeAndReadStorageReference(def.typedValueRef(STRING, null))
        writeAndReadStorageReference(def.typeRef(null))
    }

    private fun writeAndReadTransportReference(ref: IsPropertyReference<*, *, *>) {
        val byteCollector = ByteCollector()
        byteCollector.reserve(ref.calculateStorageByteLength())
        ref.writeStorageBytes(byteCollector::write)

        def.resolveReferenceFromStorage(byteCollector::read, null) shouldBe ref
    }

    @Test
    fun resolveReferenceFromTransportByAnyTypeName() {
        writeAndReadTransportReference(def.typedValueRef(STRING, null))
        writeAndReadTransportReference(def.typeRef(null))
    }

    private fun writeAndReadStorageReference(ref: IsPropertyReference<*, *, *>) {
        val byteCollector = ByteCollector()
        val cache = WriteCache()
        byteCollector.reserve(ref.calculateTransportByteLength(cache))
        ref.writeTransportBytes(cache, byteCollector::write)

        def.resolveReference(byteCollector::read, null) shouldBe ref
    }

    @Test
    fun convertValuesToTransportBytesAndBack() {
        val bc = ByteCollector()
        multisToTest.forEach { checkProtoBufConversion(bc, it, this.def) }
    }

    @Test
    fun invalidFieldShouldThrowException() {
        shouldThrow<DefNotFoundException> {
            def.validateWithRef(newValue = TypedValue(UNUSED, "NonExistingField"))
        }
    }

    @Test
    fun convertDefinitionToProtoBufAndBack() {
        checkProtoBufConversion(this.def, MultiTypeDefinition.Model)
        checkProtoBufConversion(this.defMaxDefined, MultiTypeDefinition.Model)
    }

    @Test
    fun convertDefinitionToJSONAndBack() {
        checkJsonConversion(this.def, MultiTypeDefinition.Model)
        checkJsonConversion(this.defMaxDefined, MultiTypeDefinition.Model)
    }

    @Test
    fun convertDefinitionToYAMLAndBack() {
        checkYamlConversion(this.def, MultiTypeDefinition.Model)
        checkYamlConversion(this.defMaxDefined, MultiTypeDefinition.Model) shouldBe """
        required: false
        final: true
        typeEnum: MultiTypeEnumImpl
        typeIsFinal: true
        definitionMap:
          ? 1: STRING
          : !String
            required: true
            final: false
            unique: false
            regEx: '#.*'
          ? 2: INT
          : !Number
            required: true
            final: false
            unique: false
            type: SInt32
            maxValue: 1000
            random: false
          ? 3: LIST
          : !List
            required: true
            final: false
            valueDefinition: !String
              required: true
              final: false
              unique: false
              regEx: '#.*'
          ? 4: SET
          : !Set
            required: true
            final: false
            valueDefinition: !String
              required: true
              final: false
              unique: false
              regEx: '#.*'
          ? 5: MAP
          : !Map
            required: true
            final: false
            keyDefinition: !Number
              required: true
              final: false
              unique: false
              type: SInt32
              maxValue: 1000
              random: false
            valueDefinition: !String
              required: true
              final: false
              unique: false
              regEx: '#.*'
          ? 6: MULTI
          : !MultiType
            required: true
            final: false
            typeEnum: SimpleMarykTypeEnum
            typeIsFinal: true
            definitionMap:
              ? 1: S1
              : !String
                required: true
                final: false
                unique: false
                regEx: '#.*'
              ? 2: S2
              : !Number
                required: true
                final: false
                unique: false
                type: SInt16
                maxValue: 1000
                random: false
        default: !STRING(1) test

        """.trimIndent()
    }
}
