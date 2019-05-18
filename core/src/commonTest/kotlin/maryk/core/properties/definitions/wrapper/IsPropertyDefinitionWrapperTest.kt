package maryk.core.properties.definitions.wrapper

import maryk.test.shouldBe

internal fun comparePropertyDefinitionWrapper(
    converted: IsDefinitionWrapper<out Any, out Any, *, Any>,
    original: IsDefinitionWrapper<out Any, out Any, *, Any>
) {
    converted.index shouldBe original.index
    converted.name shouldBe original.name
    converted.definition shouldBe original.definition
}
