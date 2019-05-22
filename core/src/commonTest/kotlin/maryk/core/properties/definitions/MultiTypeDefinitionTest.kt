package maryk.core.properties.definitions

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.exceptions.DefNotFoundException
import maryk.core.extensions.toUnitLambda
import maryk.core.properties.definitions.wrapper.MultiTypeDefinitionWrapper
import maryk.core.properties.exceptions.AlreadySetException
import maryk.core.properties.exceptions.InvalidValueException
import maryk.core.properties.exceptions.OutOfRangeException
import maryk.core.properties.exceptions.ValidationUmbrellaException
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.TypedValue
import maryk.core.protobuf.WriteCache
import maryk.core.query.DefinitionsConversionContext
import maryk.core.query.RequestContext
import maryk.test.ByteCollector
import maryk.test.models.EmbeddedMarykModel
import maryk.test.models.MarykTypeEnum
import maryk.test.models.MarykTypeEnum.T1
import maryk.test.models.MarykTypeEnum.T2
import maryk.test.models.MarykTypeEnum.T4
import maryk.test.models.MarykTypeEnum.T5
import maryk.test.models.MarykTypeEnum.T6
import maryk.test.models.MarykTypeEnum.T7
import maryk.test.models.SimpleMarykTypeEnum.S1
import maryk.test.shouldBe
import maryk.test.shouldThrow
import kotlin.test.Test

internal class MultiTypeDefinitionTest {
    private val def = MultiTypeDefinition(
        typeEnum = MarykTypeEnum
    )

    private val defMaxDefined = MultiTypeDefinition(
        final = true,
        required = false,
        typeEnum = MarykTypeEnum,
        default = TypedValue(T1, "test")
    )

    private val defWrapper = MultiTypeDefinitionWrapper<MarykTypeEnum<out Any>, Any, Any, DefinitionsConversionContext, Any>(
        1u, "multi", def
    )

    private val multisToTest = arrayOf<TypedValue<MarykTypeEnum<out Any>, Any>>(
        TypedValue(T1, "#test"),
        TypedValue(T2, 400),
        TypedValue(T4, listOf("#a", "#b", "#c")),
        TypedValue(T5, setOf("#a", "#b", "#c")),
        TypedValue(T6, mapOf(1u to "#a", 2u to "#b", 3u to "#c")),
        TypedValue(T7, TypedValue(S1, "#test"))
    )

    private val context = RequestContext(
        dataModels = mapOf(
            EmbeddedMarykModel.name toUnitLambda { EmbeddedMarykModel }
        )
    )

    @Test
    fun getProperties() {
        def.definition(T1.index) shouldBe T1.definition
        def.definition(T2.index) shouldBe T2.definition
        def.definition(T4.index) shouldBe T4.definition
        def.definition(T5.index) shouldBe T5.definition
        def.definition(T6.index) shouldBe T6.definition
    }

    @Test
    fun validateContent() {
        def.validateWithRef(newValue = TypedValue(T1, "#test"))
        def.validateWithRef(newValue = TypedValue(T2, 400))
        def.validateWithRef(newValue = TypedValue(T4, listOf("#a", "#b", "#c")))
        def.validateWithRef(newValue = TypedValue(T5, setOf("#a", "#b", "#c")))
        def.validateWithRef(newValue = TypedValue(T6, mapOf(1 to "#a")))

        shouldThrow<OutOfRangeException> {
            def.validateWithRef(newValue = TypedValue(T2, 3000))
        }
        shouldThrow<InvalidValueException> {
            def.validateWithRef(newValue = TypedValue(T1, "&WRONG"))
        }
        shouldThrow<ValidationUmbrellaException> {
            def.validateWithRef(newValue = TypedValue(T4, listOf("&WRONG")))
        }
        shouldThrow<ValidationUmbrellaException> {
            def.validateWithRef(newValue = TypedValue(T5, setOf("&WRONG")))
        }
        shouldThrow<ValidationUmbrellaException> {
            def.validateWithRef(newValue = TypedValue(T6, mapOf(1 to "&WRONG")))
        }
        shouldThrow<InvalidValueException> {
            def.validateWithRef(newValue = TypedValue(T7, TypedValue(S1, "&WRONG")))
        }

        shouldThrow<AlreadySetException> {
            def.validateWithRef(
                previousValue = TypedValue(T1, "WRONG"),
                newValue = TypedValue(T2, 400),
                refGetter = { defWrapper.ref() }
            )
        }.reference.toString() shouldBe "multi.*T2"
    }

    @Test
    fun resolveReferenceByName() {
        def.resolveReferenceByName("*T1") shouldBe def.typedValueRef(T1, null)
        def.resolveReferenceByName("*") shouldBe def.typeRef(null)
    }

    @Test
    fun resolveReferenceFromStorageByAnyTypeName() {
        writeAndReadStorageReference(def.typedValueRef(T1, null))
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
        writeAndReadTransportReference(def.typedValueRef(T1, null))
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
        multisToTest.forEach { checkProtoBufConversion(bc, it, this.def, context = context) }
    }

    @Test
    fun invalidFieldShouldThrowException() {
        shouldThrow<DefNotFoundException> {
            def.validateWithRef(newValue = TypedValue(MarykTypeEnum.UnknownMarykTypeEnum(99u, "UNKNOWN"), "NonExistingField"))
        }
    }

    @Test
    fun convertDefinitionToProtoBufAndBack() {
        checkProtoBufConversion(this.def, MultiTypeDefinition.Model, { context })
        checkProtoBufConversion(this.defMaxDefined, MultiTypeDefinition.Model, { context })
    }

    @Test
    fun convertDefinitionToJSONAndBack() {
        checkJsonConversion(this.def, MultiTypeDefinition.Model, { context })
        checkJsonConversion(this.defMaxDefined, MultiTypeDefinition.Model, { context })
    }

    @Test
    fun convertDefinitionToYAMLAndBack() {
        checkYamlConversion(this.def, MultiTypeDefinition.Model, { context })
        checkYamlConversion(this.defMaxDefined, MultiTypeDefinition.Model, { context }) shouldBe """
        required: false
        final: true
        typeEnum:
          name: MarykTypeEnum
          cases:
            ? 1: [T1, Type1]
            : !String
              required: true
              final: false
              unique: false
              regEx: '[^&]+'
            ? 2: T2
            : !Number
              required: true
              final: false
              unique: false
              type: SInt32
              maxValue: 2000
              random: false
            ? 3: T3
            : !Embed
              required: true
              final: false
              dataModel: EmbeddedMarykModel
            ? 4: T4
            : !List
              required: true
              final: false
              valueDefinition: !String
                required: true
                final: false
                unique: false
                regEx: '[^&]+'
            ? 5: T5
            : !Set
              required: true
              final: false
              valueDefinition: !String
                required: true
                final: false
                unique: false
                regEx: '[^&]+'
            ? 6: T6
            : !Map
              required: true
              final: false
              keyDefinition: !Number
                required: true
                final: false
                unique: false
                type: UInt32
                random: false
              valueDefinition: !String
                required: true
                final: false
                unique: false
                regEx: '[^&]+'
            ? 7: T7
            : !MultiType
              required: true
              final: false
              typeEnum:
                name: SimpleMarykTypeEnum
                cases:
                  ? 1: [S1, Type1]
                  : !String
                    required: true
                    final: false
                    unique: false
                    regEx: '[^&]+'
                  ? 2: S2
                  : !Number
                    required: true
                    final: false
                    unique: false
                    type: SInt16
                    random: false
                  ? 3: S3
                  : !Embed
                    required: true
                    final: false
                    dataModel: EmbeddedMarykModel
                reservedIndices: [99]
                reservedNames: [O99]
              typeIsFinal: true
          reservedIndices: [99]
          reservedNames: [O99]
        typeIsFinal: true
        default: !T1(1) test

        """.trimIndent()
    }
}
