package maryk.core.models.definitions

import maryk.core.definitions.MarykPrimitiveDescriptor
import maryk.core.models.IsDataModel

interface IsNamedDataModelDefinition<DM : IsDataModel> : IsDataModelDefinition<DM>, MarykPrimitiveDescriptor
