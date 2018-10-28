package maryk.core.definitions

/** Defines the class is a Maryk Primitive like EnumDefinition or DataModel */
interface MarykPrimitive {
    val name: String
    val primitiveType: PrimitiveType
}
