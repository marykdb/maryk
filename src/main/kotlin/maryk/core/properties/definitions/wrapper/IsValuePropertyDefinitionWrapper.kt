package maryk.core.properties.definitions.wrapper

import maryk.core.properties.IsPropertyContext

interface IsValuePropertyDefinitionWrapper<T: Any, in CX:IsPropertyContext, in DM> : IsPropertyDefinitionWrapper<T, CX, DM>