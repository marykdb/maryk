package maryk.core.definitions

/** Defines the class is a Maryk Primitive like EnumDefinition or DataModel */
interface MarykPrimitiveDescriptor {
    val name: String
    val primitiveType: PrimitiveType
}

interface MarykPrimitive {
    val Meta: MarykPrimitiveDescriptor
}
