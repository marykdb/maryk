package maryk.datastore.shared.encryption

/** Version marker added by stores around encrypted sensitive values. */
enum class FieldEncryptionEnvelope(val magic: ByteArray) {
    Legacy(byteArrayOf(0x4D, 0x4B, 0x45, 0x31)), // MKE1
    Contextual(byteArrayOf(0x4D, 0x4B, 0x45, 0x32)), // MKE2
    ;

    companion object {
        /**
         * Detect an envelope only after the caller has established that the
         * value belongs to a configured sensitive qualifier.
         */
        fun fromSensitiveValue(
            value: ByteArray,
            offset: Int = 0,
            length: Int = value.size - offset,
        ): FieldEncryptionEnvelope? = from(value, offset, length)

        fun from(value: ByteArray, offset: Int = 0, length: Int = value.size - offset): FieldEncryptionEnvelope? {
            require(offset >= 0 && length >= 0 && offset <= value.size - length) { "Invalid value range" }
            return entries.firstOrNull { envelope ->
                length >= envelope.magic.size && envelope.magic.indices.all { index ->
                    value[offset + index] == envelope.magic[index]
                }
            }
        }
    }
}
