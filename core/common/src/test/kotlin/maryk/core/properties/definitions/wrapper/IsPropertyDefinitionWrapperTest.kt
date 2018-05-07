package maryk.core.properties.definitions.wrapper

import maryk.core.properties.IsPropertyContext
import maryk.test.shouldBe

internal fun comparePropertyDefinitionWrapper(converted: IsPropertyDefinitionWrapper<out Any, out Any, IsPropertyContext, Any>, original: IsPropertyDefinitionWrapper<out Any, out Any, IsPropertyContext, Any>) {
    converted.index shouldBe original.index
    converted.name shouldBe original.name
    converted.definition shouldBe original.definition
}
