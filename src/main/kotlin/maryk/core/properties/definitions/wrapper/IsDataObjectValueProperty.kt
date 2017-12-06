package maryk.core.properties.definitions.wrapper

import maryk.core.properties.IsPropertyContext

interface IsDataObjectValueProperty<T: Any, in CX:IsPropertyContext, in DM> : IsDataObjectProperty<T, CX, DM>