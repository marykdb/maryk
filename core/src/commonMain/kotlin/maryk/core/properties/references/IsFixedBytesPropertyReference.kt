package maryk.core.properties.references

import maryk.core.properties.definitions.IsFixedStorageBytesEncodable

interface IsFixedBytesPropertyReference<T : Any> : IsFixedStorageBytesEncodable<T>, IsIndexablePropertyReference<T>
