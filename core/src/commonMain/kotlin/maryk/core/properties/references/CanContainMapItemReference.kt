package maryk.core.properties.references

import maryk.core.properties.definitions.IsPropertyDefinition

/** Defines that this reference part can contain a map item reference like a key or a value. */
interface CanContainMapItemReference<T : Any, out D : IsPropertyDefinition<T>, V : Any> : IsPropertyReference<T, D, V>
