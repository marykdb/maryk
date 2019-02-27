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
import maryk.test.models.Option
import maryk.test.models.Option.V1
import maryk.test.models.Option.V3
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

    val def = MultiTypeDefinition<Option, IsPropertyContext>(
        typeEnum = Option,
        definitionMap = mapOf(
            Option.V1 to stringDef,
            Option.V2 to intDef
        )
    )

    val defMaxDefined = MultiTypeDefinition<Option, IsPropertyContext>(
        final = true,
        required = false,
        typeEnum = Option,
        definitionMap = mapOf(
            Option.V1 to stringDef,
            Option.V2 to intDef
        ),
        default = TypedValue(Option.V1, "test")
    )

    private val multisToTest = arrayOf(
        TypedValue(Option.V1, "#test"),
        TypedValue(Option.V2, 400)
    )

    @Test
    fun getProperties() {
        def.definitionMap[Option.V1] shouldBe stringDef
        def.definitionMap[Option.V2] shouldBe intDef
    }

    @Test
    fun validateContent() {
        def.validateWithRef(newValue = TypedValue(Option.V1, "#test"))
        def.validateWithRef(newValue = TypedValue(Option.V2, 400))

        shouldThrow<OutOfRangeException> {
            def.validateWithRef(newValue = TypedValue(Option.V2, 3000))
        }
        shouldThrow<InvalidValueException> {
            def.validateWithRef(newValue = TypedValue(Option.V1, "WRONG"))
        }

        shouldThrow<AlreadySetException> {
            def.validateWithRef(
                previousValue = TypedValue(Option.V1, "WRONG"),
                newValue = TypedValue(Option.V2, 400),
                refGetter = { multi.ref() }
            )
        }.reference.toString() shouldBe "multi.*V2"
    }

    @Test
    fun resolveReferenceByName() {
        def.resolveReferenceByName("*V1") shouldBe def.typedValueRef(V1, null)
        def.resolveReferenceByName("*") shouldBe def.typeRef(null)
    }

    @Test
    fun resolveReferenceFromStorageByAnyTypeName() {
        writeAndReadStorageReference(def.typedValueRef(V1, null))
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
        writeAndReadTransportReference(def.typedValueRef(V1, null))
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
            def.validateWithRef(newValue = TypedValue(V3, "NonExistingField"))
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
        typeEnum: Option
        typeIsFinal: true
        definitionMap:
          ? 1: V1
          : !String
            required: true
            final: false
            unique: false
            regEx: '#.*'
          ? 2: V2
          : !Number
            required: true
            final: false
            unique: false
            type: SInt32
            maxValue: 1000
            random: false
        default: !V1 test

        """.trimIndent()
    }
}
