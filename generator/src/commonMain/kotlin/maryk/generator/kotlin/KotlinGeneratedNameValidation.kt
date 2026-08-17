package maryk.generator.kotlin

import maryk.core.models.IsTypedDataModel
import maryk.core.properties.enum.IsIndexedEnumDefinition

internal fun IsTypedDataModel<*>.validateKotlinGeneratedModelNames(
    modelName: String,
    frameworkTypes: Set<String>,
) {
    modelName.kotlinIdentifier()
    require(modelName !in frameworkTypes) {
        "Kotlin model name $modelName shadows generated framework type $modelName"
    }

    val properties = toList()
    val generatedGetters = mutableMapOf<String, String>()
    properties.forEach { property ->
        val getter = property.name.jvmGetterName()
        generatedGetters.put(getter, property.name)?.let { first ->
            require(first == property.name) {
                "Kotlin properties $first and ${property.name} in model $modelName both generate JVM getter $getter"
            }
        }
    }

    for (frameworkMember in kotlinFrameworkMembers) {
        val getter = frameworkMember.jvmGetterName()
        generatedGetters[getter]?.let { property ->
            throw IllegalArgumentException(
                "Kotlin property $property in model $modelName generates JVM getter $getter " +
                    "which collides with framework member $frameworkMember",
            )
        }
    }
}

internal fun IsIndexedEnumDefinition<*>.validateKotlinGeneratedEnumNames(
    frameworkTypes: Set<String>,
) {
    name.kotlinIdentifier()
    require(name !in frameworkTypes) {
        "Kotlin enum name $name shadows generated framework type $name"
    }

    val helperNames = setOf("Companion", "Unknown$name")
    cases().forEach { case ->
        val caseIdentifier = case.name.kotlinIdentifier()
        val helper = helperNames.firstOrNull { it.kotlinIdentifier() == caseIdentifier }
        require(helper == null) {
            "Kotlin enum $name case ${case.name} collides with generated helper $helper"
        }
    }
}

internal fun Iterable<UInt>.toKotlinUIntList(): String =
    joinToString(", ") { "${it}u" }

private val kotlinFrameworkMembers = setOf("Meta", "Serializer")

private fun String.jvmGetterName(): String =
    if (length > 2 && startsWith("is") && !this[2].isLowerCase()) {
        this
    } else {
        "get${replaceFirstChar { it.titlecase() }}"
    }
