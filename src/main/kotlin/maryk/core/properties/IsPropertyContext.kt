package maryk.core.properties

/**
 * A property context is used to share context state between reading or writing properties.
 * This way it is possible to base the reading or writing of a value of a property on another property.
 *
 * It is then possible to define the type with one property and use that value with other properties to
 * encode or decode them in the right format.
 */
interface IsPropertyContext