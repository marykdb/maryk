package maryk.generator.kotlin

import maryk.core.exceptions.TypeException
import maryk.core.models.IsRootDataModel
import maryk.core.models.IsStorableDataModel
import maryk.core.properties.definitions.IsEmbeddedDefinition
import maryk.core.properties.definitions.IsMultiTypeDefinition
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.properties.definitions.index.Multiple
import maryk.core.properties.definitions.index.ReferenceToMax
import maryk.core.properties.definitions.index.Reversed
import maryk.core.properties.definitions.index.UUIDKey
import maryk.core.properties.references.IsPropertyReferenceForValues
import maryk.core.properties.references.SimpleTypedValueReference
import maryk.core.properties.references.TypeReference
import maryk.core.properties.references.ValueWithFixedBytesPropertyReference
import maryk.core.properties.references.ValueWithFlexBytesPropertyReference
import maryk.core.properties.types.Version

fun IsRootDataModel.generateKotlin(
    packageName: String,
    generationContext: GenerationContext? = null,
    writer: (String) -> Unit
) {
    val importsToAdd = mutableSetOf(
        "maryk.core.properties.RootDataModel",
    )
    val addImport: (String) -> Unit = { importsToAdd.add(it) }

    // Add key definitions if they are not the default UUID key
    val versionAsKotlin = if (Meta.version != Version(1)) {
        val (major, minor, patch) = Meta.version
        val patchValue = if (patch == UShort.MIN_VALUE) "" else ", $patch"

        addImport("maryk.core.properties.types.Version")

        "version = Version($major, $minor$patchValue)"
    } else null

    // Add key definitions if they are not the default UUID key
    val keyDefAsKotlin = if (Meta.keyDefinition != UUIDKey) {
        val keyDefs = Meta.keyDefinition.generateKotlin(packageName, Meta.name, addImport)

        "keyDefinition = ${keyDefs.prependIndent().prependIndent().trimStart()}"
    } else null

    // Add indexes if they are not null
    val indexesAsKotlin = Meta.indexes?.let { indexables ->
        val output = mutableListOf<String>()
        for (it in indexables) {
            output += it.generateKotlin(packageName, Meta.name, addImport)
        }
        "indexes = listOf(\n${output.joinToString(",\n").prependIndent().prependIndent().prependIndent()}\n        ),"
    }

    val reservedIndices = Meta.reservedIndices.let { indexes ->
        when {
            indexes.isNullOrEmpty() -> null
            else -> "reservedIndices = listOf(${indexes.joinToString(", ", postfix = "u")})"
        }
    }
    val reservedNames = Meta.reservedNames.let { names ->
        when {
            names.isNullOrEmpty() -> null
            else -> "reservedNames = listOf(${names.joinToString(", ", "\"", "\"")})"
        }
    }

    val enumKotlinDefinitions = mutableListOf<String>()
    val propertiesKotlin = this.generateKotlin(addImport, generationContext) {
        enumKotlinDefinitions.add(it)
    }

    val constructorParameters = arrayOf(versionAsKotlin, keyDefAsKotlin, indexesAsKotlin, reservedIndices, reservedNames)
        .filterNotNull()
        .joinToString("\n        ")
        .let { if (it.isBlank()) "" else "\n        $it\n    " }

    val code = """
    object ${Meta.name} : RootDataModel<${Meta.name}>($constructorParameters) {
        ${propertiesKotlin.generateDefinitionsForProperties(addImport).trimStart()}
    }
    """.trimIndent()

    writeKotlinFile(packageName, importsToAdd, enumKotlinDefinitions, code, writer)
}

/**
 * Generate the kotlin for key definitions and adds imports with [addImport]
 */
private fun IsIndexable.generateKotlin(
    packageName: String,
    name: String,
    addImport: (String) -> Unit
): String = when (this) {
    is UUIDKey -> {
        addImport("maryk.core.properties.definitions.index.UUIDKey")
        "UUIDKey"
    }
    is TypeReference<*, *, *> -> {
        val typeId = this
        val parentReference = (typeId.parentReference as IsPropertyReferenceForValues<*, *, *, *>)
        parentReference.generateRef(packageName, name, addImport, refFunction = "typeRef")
    }
    is Reversed<*> -> {
        addImport("maryk.core.properties.definitions.index.Reversed")
        "Reversed(${this.reference.generateRef(packageName, name, addImport)})"
    }
    is ReferenceToMax<*> -> {
        addImport("maryk.core.properties.definitions.index.ReferenceToMax")
        "ReferenceToMax(${this.reference.generateRef(packageName, name, addImport)})"
    }
    is ValueWithFixedBytesPropertyReference<*, *, *, *> -> {
        generateRef(packageName, name, addImport)
    }
    is ValueWithFlexBytesPropertyReference<*, *, *, *> -> {
        generateRef(packageName, name, addImport)
    }
    is SimpleTypedValueReference<*, *, *> -> {
        generateKotlin(packageName, name, addImport)
    }
    is Multiple -> {
        addImport("maryk.core.properties.definitions.index.Multiple")
        val output = mutableListOf<String>()

        for (it in this.references) {
            output += it.generateKotlin(packageName, name, addImport)
        }

        "Multiple(\n${output.joinToString(",\n").prependIndent()}\n)"
    }
    else -> throw TypeException("Unknown IsIndexable type: $this")
}

/**
 * Generate reference variable for indexable using [packageName] and [modelName] for [addImport]
 * [refFunction] can be overridden for other types of ref
 */
private fun IsPropertyReferenceForValues<*, *, *, *>.generateRef(
    packageName: String,
    modelName: String,
    addImport: (String) -> Unit,
    refFunction: String = "ref"
): String {
    var parent = ""
    this.parentReference?.let {
        if (it is IsPropertyReferenceForValues<*, *, *, *>) {
            it.propertyDefinition.let { propDef ->
                if (propDef is IsEmbeddedDefinition<*>) {
                    val embedModelName = (propDef.dataModel as IsStorableDataModel<*>).Meta.name
                    addImport("$packageName.$embedModelName.Properties.${this.name}")
                }
            }

            parent = it.generateRef(packageName, modelName, addImport)
        }
    }
    if (parent.isEmpty()) {
        addImport("$packageName.$modelName.Properties.${this.name}")
    }
    return "${this.name}.$refFunction($parent)"
}

private fun SimpleTypedValueReference<*, *, *>.generateKotlin(
    packageName: String,
    modelName: String,
    addImport: (String) -> Unit
): String {
    val parentRef = this.parentReference as? IsPropertyReferenceForValues<*, *, *, *>
        ?: throw TypeException("SimpleTypedValueReference is missing a parent reference: $this")
    parentRef.generateRef(packageName, modelName, addImport)

    val multiTypeDefinition = parentRef.propertyDefinition as? IsMultiTypeDefinition<*, *, *>
        ?: throw TypeException("SimpleTypedValueReference parent is not a multi type definition: $parentRef")

    val typeEnumName = multiTypeDefinition.typeEnum.name
    val typeName = this.type.name
    val segments = parentRef.completeName.split(".")

    var expression = "${segments.last()} simpleRefAtType $typeEnumName.$typeName"
    for (segment in segments.dropLast(1).asReversed()) {
        expression = "$segment { $expression }"
    }

    return "$modelName { $expression }"
}
