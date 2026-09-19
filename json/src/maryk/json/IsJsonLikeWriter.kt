package maryk.json

/** A writer which handles JSON like data */
interface IsJsonLikeWriter {
    /**
     * Write Object start
     * Set [isCompact] to true for a more compact representation
     */
    fun writeStartObject(isCompact: Boolean = false)

    /** Write Object end */
    fun writeEndObject()

    /**
     * Write Array start
     * Set [isCompact] to true for a more compact representation
     */
    fun writeStartArray(isCompact: Boolean = false)

    /** Write Array end */
    fun writeEndArray()

    /** Writes the field [name] for an object */
    fun writeFieldName(name: String)

    /** Writes a String [value] including quotes */
    fun writeString(value: String)

    /** Writes a [value] excluding quotes */
    fun writeValue(value: String)

    /** Writes a [boolean] */
    fun writeBoolean(boolean: Boolean) = writeValue(boolean.toString())

    /** Writes an [int] */
    fun writeInt(int: Int) = writeValue(int.toString())

    /** Writes a [long] */
    fun writeLong(long: Long) = writeValue(long.toString())

    /** Writes a [double] */
    fun writeDouble(double: Double) = writeValue(double.toString())

    /** Writes a [float] */
    fun writeFloat(float: Float) = writeValue(float.toString())

    /** Writes a null */
    fun writeNull() = writeValue("null")
}

/** Exception for invalid JSON */
class IllegalJsonOperation(description: String) : IllegalStateException(description)
