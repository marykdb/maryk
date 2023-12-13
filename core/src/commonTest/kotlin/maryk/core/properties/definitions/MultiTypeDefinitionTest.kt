package maryk.core.properties.definitions

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.exceptions.DefNotFoundException
import maryk.core.extensions.toUnitLambda
import maryk.core.properties.definitions.wrapper.MultiTypeDefinitionWrapper
import maryk.core.properties.enum.MultiTypeEnumDefinition
import maryk.core.properties.types.invoke
import maryk.core.properties.exceptions.AlreadySetException
import maryk.core.properties.exceptions.InvalidValueException
import maryk.core.properties.exceptions.OutOfRangeException
import maryk.core.properties.exceptions.ValidationUmbrellaException
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.TypeReference
import maryk.core.properties.types.TypedValue
import maryk.core.protobuf.WriteCache
import maryk.core.query.DefinitionsConversionContext
import maryk.core.query.RequestContext
import maryk.test.ByteCollector
import maryk.test.models.EmbeddedMarykModel
import maryk.test.models.MarykTypeEnum
import maryk.test.models.MarykTypeEnum.T1
import maryk.test.models.MarykTypeEnum.T2
import maryk.test.models.MarykTypeEnum.T3
import maryk.test.models.MarykTypeEnum.T4
import maryk.test.models.MarykTypeEnum.T5
import maryk.test.models.MarykTypeEnum.T6
import maryk.test.models.MarykTypeEnum.T7
import maryk.test.models.SimpleMarykTypeEnum.S1
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.expect

internal class MultiTypeDefinitionTest {
    private val def = MultiTypeDefinition(
        typeEnum = MarykTypeEnum
    )

    private val defMaxDefined = MultiTypeDefinition(
        final = true,
        required = false,
        typeEnum = MarykTypeEnum,
        default = T1( "test"),
    )

    private val defWrapper = MultiTypeDefinitionWrapper<MarykTypeEnum<out Any>, Any, Any, DefinitionsConversionContext, Any>(
        1u, "multi", def
    )

    private val multisToTest = arrayOf<TypedValue<MarykTypeEnum<out Any>, Any>>(
        T1( "#test"),
        T2( 400),
        T4(listOf("#a", "#b", "#c")),
        T5(setOf("#a", "#b", "#c")),
        T6(mapOf(1u to "#a", 2u to "#b", 3u to "#c")),
        T7(S1("#test"))
    )

    private val context = RequestContext(
        dataModels = mapOf(
            EmbeddedMarykModel.Meta.name toUnitLambda { EmbeddedMarykModel }
        )
    )

    @Test
    fun getProperties() {
        expect(T1.definition as IsSubDefinition<*, *>) { def.definition(T1.index) as IsSubDefinition<*, *> }
        expect(T2.definition as IsSubDefinition<*, *>) { def.definition(T2.index) as IsSubDefinition<*, *> }
        expect(T4.definition as IsSubDefinition<*, *>) { def.definition(T4.index) as IsSubDefinition<*, *> }
        expect(T5.definition as IsSubDefinition<*, *>) { def.definition(T5.index) as IsSubDefinition<*, *> }
        expect(T6.definition as IsSubDefinition<*, *>) { def.definition(T6.index) as IsSubDefinition<*, *> }
    }

    @Test
    fun validateContent() {
        def.validateWithRef(newValue = T1("#test"))
        def.validateWithRef(newValue = T2(400))
        def.validateWithRef(newValue = T4(listOf("#a", "#b", "#c")))
        def.validateWithRef(newValue = T5(setOf("#a", "#b", "#c")))
        def.validateWithRef(newValue = T6(mapOf(1 to "#a")))

        assertFailsWith<OutOfRangeException> {
            def.validateWithRef(newValue = T2(3000))
        }
        assertFailsWith<InvalidValueException> {
            def.validateWithRef(newValue = T1("&WRONG"))
        }
        assertFailsWith<ValidationUmbrellaException> {
            def.validateWithRef(newValue = T4(listOf("&WRONG")))
        }
        assertFailsWith<ValidationUmbrellaException> {
            def.validateWithRef(newValue = T5(setOf("&WRONG")))
        }
        assertFailsWith<ValidationUmbrellaException> {
            def.validateWithRef(newValue = T6(mapOf(1 to "&WRONG")))
        }
        assertFailsWith<InvalidValueException> {
            def.validateWithRef(newValue = T7(TypedValue(S1, "&WRONG")))
        }

        expect("multi.*T2") {
            assertFailsWith<AlreadySetException> {
                def.validateWithRef(
                    previousValue = T1("WRONG"),
                    newValue = T2(400),
                    refGetter = { defWrapper.ref() }
                )
            }.reference.toString()
        }
    }

    @Test
    fun resolveReferenceByName() {
        expect(def.typedValueRef(T1, null)) {
            def.resolveReferenceByName("*T1")
        }
        expect(def.typeRef(null)) {
            def.resolveReferenceByName("*") as TypeReference<*, *, *>
        }
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

        expect(ref) { def.resolveReferenceFromStorage(byteCollector::read, null) }
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

        expect(ref) { def.resolveReference(byteCollector::read, null) }
    }

    @Test
    fun convertValuesToTransportBytesAndBack() {
        val bc = ByteCollector()
        multisToTest.forEach { checkProtoBufConversion(bc, it, this.def, context = context) }
    }

    @Test
    fun invalidFieldShouldThrowException() {
        assertFailsWith<DefNotFoundException> {
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
        expect(
            """
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
        ) {
            checkYamlConversion(this.defMaxDefined, MultiTypeDefinition.Model, { context })
        }
    }

    @Test
    fun isCompatible() {
        val enum1 = MultiTypeEnumDefinition("Test", { listOf(T1, T2) })
        val enum2 = MultiTypeEnumDefinition("Test", { listOf(T1, T2, T3) })
        val enumWrong = MultiTypeEnumDefinition("Test", { listOf(T4, T5)})

        assertTrue {
            MultiTypeDefinition(typeEnum = enum2).compatibleWith(
                MultiTypeDefinition(typeEnum = enum1)
            )
        }

        assertFalse {
            MultiTypeDefinition(typeEnum = enumWrong).compatibleWith(
                MultiTypeDefinition(typeEnum = enum1)
            )
        }
    }
}
