package maryk.core.properties.definitions

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.exceptions.DefNotFoundException
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.exceptions.AlreadySetException
import maryk.core.properties.exceptions.InvalidValueException
import maryk.core.properties.exceptions.OutOfRangeException
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.numeric.SInt32
import maryk.core.protobuf.WriteCache
import maryk.test.ByteCollector
import maryk.test.models.MultiTypeEnum
import maryk.test.models.MultiTypeEnum.T1
import maryk.test.models.MultiTypeEnum.T2
import maryk.test.models.MultiTypeEnum.T3
import maryk.test.models.TestMarykModel.Properties.multi
import maryk.test.shouldBe
import maryk.test.shouldThrow
import kotlin.test.Test

internal class MultiTypeDefinitionTest {
    private val intDef = NumberDefinition(
        type = SInt32,
        maxValue = 1000
    )

    private val stringDef = StringDefinition(
        regEx = "#.*"
    )

    val def = MultiTypeDefinition<MultiTypeEnum, IsPropertyContext>(
        typeEnum = MultiTypeEnum,
        definitionMap = mapOf(
            T1 to stringDef,
            T2 to intDef
        )
    )

    val defMaxDefined = MultiTypeDefinition<MultiTypeEnum, IsPropertyContext>(
        final = true,
        required = false,
        typeEnum = MultiTypeEnum,
        definitionMap = mapOf(
            T1 to stringDef,
            T2 to intDef
        ),
        default = TypedValue(T1, "test")
    )

    private val multisToTest = arrayOf(
        TypedValue(T1, "#test"),
        TypedValue(T2, 400)
    )

    @Test
    fun getProperties() {
        def.definitionMap[T1] shouldBe stringDef
        def.definitionMap[T2] shouldBe intDef
    }

    @Test
    fun validateContent() {
        def.validateWithRef(newValue = TypedValue(T1, "#test"))
        def.validateWithRef(newValue = TypedValue(T2, 400))

        shouldThrow<OutOfRangeException> {
            def.validateWithRef(newValue = TypedValue(T2, 3000))
        }
        shouldThrow<InvalidValueException> {
            def.validateWithRef(newValue = TypedValue(T1, "WRONG"))
        }

        shouldThrow<AlreadySetException> {
            def.validateWithRef(
                previousValue = TypedValue(T1, "WRONG"),
                newValue = TypedValue(T2, 400),
                refGetter = { multi.ref() }
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
    fun invalidFieldShouldThrowException() {
        shouldThrow<DefNotFoundException> {
            def.validateWithRef(newValue = TypedValue(T3, "NonExistingField"))
        }
    }

    @Test
    fun convertValuesToTransportBytesAndBack() {
        val bc = ByteCollector()
        multisToTest.forEach { checkProtoBufConversion(bc, it, this.def) }
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
        typeEnum: MultiTypeEnum
        typeIsFinal: true
        definitionMap:
          ? 1: T1
          : !String
            required: true
            final: false
            unique: false
            regEx: '#.*'
          ? 2: T2
          : !Number
            required: true
            final: false
            unique: false
            type: SInt32
            maxValue: 1000
            random: false
        default: !T1(1) test

        """.trimIndent()
    }
}
