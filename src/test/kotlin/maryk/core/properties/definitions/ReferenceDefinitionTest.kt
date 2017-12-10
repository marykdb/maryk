package maryk.core.properties.definitions

import maryk.TestMarykObject
import maryk.checkProtoBufConversion
import maryk.core.extensions.bytes.MAXBYTE
import maryk.core.extensions.bytes.ZEROBYTE
import maryk.core.properties.ByteCollector
import maryk.core.properties.ByteCollectorWithLengthCacher
import maryk.core.properties.exceptions.ParseException
import maryk.core.properties.types.Key
import maryk.test.shouldBe
import maryk.test.shouldThrow
import kotlin.test.Test

internal class ReferenceDefinitionTest {
    private val refToTest = arrayOf<Key<TestMarykObject>>(
            Key(ByteArray(9, { ZEROBYTE })),
            Key(ByteArray(9, { MAXBYTE })),
            Key(ByteArray(9, { if (it % 2 == 1) 0b1000_1000.toByte() else MAXBYTE }))
    )

    val def = ReferenceDefinition(
            dataModel = { TestMarykObject }
    )

    @Test
    fun hasValues() {
        def.dataModel shouldBe TestMarykObject
    }

    @Test
    fun `convert values to String and back`() {
        refToTest.forEach {
            val b = def.asString(it)
            def.fromString(b) shouldBe it
        }
    }
    @Test
    fun `invalid String value should throw exception`() {
        shouldThrow<ParseException> {
            def.fromString("wrong")
        }
    }

    @Test
    fun `convert values to storage bytes and back`() {
        val bc = ByteCollector()
        refToTest.forEach {
            bc.reserve(
                    def.calculateStorageByteLength(it)
            )
            def.writeStorageBytes(it, bc::write)
            def.readStorageBytes(bc.size, bc::read) shouldBe it
            bc.reset()
        }
    }

    @Test
    fun `convert values to transport bytes and back`() {
        val bc = ByteCollectorWithLengthCacher()
        refToTest.forEach { checkProtoBufConversion(bc, it, this.def) }
    }
}