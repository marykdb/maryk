@file:OptIn(ExperimentalTypeInference::class)

package maryk.core.models

import maryk.core.models.serializers.DataModelSerializer
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.wrapper.IsReferenceCreator
import maryk.core.properties.exceptions.InvalidValueException
import maryk.core.properties.exceptions.ValidationException
import maryk.core.properties.exceptions.createValidationUmbrellaException
import maryk.core.properties.references.AnyOutPropertyReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.dsl.ref as selectReference
import maryk.core.query.RequestContext
import maryk.core.values.MutableValueItems
import maryk.core.values.ValueItem
import maryk.core.values.Values
import kotlin.experimental.ExperimentalTypeInference
import kotlin.jvm.JvmName

/**
 * Class for typed DataModels which describe how to work with [Values] objects.
 */
abstract class TypedValuesDataModel<DM: IsValuesDataModel> : BaseDataModel<Any>(), IsTypedValuesDataModel<DM> {
    @Suppress("UNCHECKED_CAST", "LeakingThis")
    override val Serializer = DataModelSerializer<Any, Values<DM>, DM, IsPropertyContext>(this as DM)

    /** Select a typed property or embedded path without an extension import. */
    @OverloadResolutionByLambdaReturnType
    @Suppress("UNCHECKED_CAST")
    fun <R : IsPropertyReference<*, *, *>> ref(
        selector: DM.() -> IsReferenceCreator<R>
    ): R = (this as DM).selectReference(selector)

    /** Select a typed property below [parent] without an extension import. */
    @OverloadResolutionByLambdaReturnType
    @Suppress("UNCHECKED_CAST")
    fun <R : IsPropertyReference<*, *, *>> ref(
        parent: AnyOutPropertyReference?,
        selector: DM.() -> IsReferenceCreator<R>
    ): R = (this as DM).selectReference(parent, selector)

    /** Resolve a nested selection below [parent] without an extension import. */
    @JvmName("refFromReferenceGetterWithParent")
    @Suppress("UNCHECKED_CAST")
    fun <T : Any, D : IsPropertyDefinition<T>, R : IsPropertyReference<T, D, *>> ref(
        parent: AnyOutPropertyReference?,
        selector: DM.() -> (AnyOutPropertyReference?) -> R
    ): R = (this as DM).selectReference(parent, selector)

    /** Resolve a nested selection or a legacy callable-reference selector. */
    @JvmName("refFromReferenceGetter")
    @Suppress("UNCHECKED_CAST")
    fun <T : Any, D : IsPropertyDefinition<T>, R : IsPropertyReference<T, D, *>> ref(
        selector: DM.() -> (AnyOutPropertyReference?) -> R
    ): R = (this as DM).selectReference(selector)

    /**
     * Create a new [Values] object with [pairs] and set defaults if [setDefaults] is true
     */
    @Suppress("UNCHECKED_CAST")
    @Deprecated(
        message = "Marked for removal in a future version. Use create(setDefaults, block) instead",
        replaceWith = ReplaceWith("this.create(setDefaults = setDefaults, block = block)")
    )
    fun create(
        vararg pairs: ValueItem?,
        setDefaults: Boolean = true,
    ) = Values(
        this as DM,
        MutableValueItems().apply {
            fillWithPairs(this@TypedValuesDataModel, pairs, setDefaults)
        }
    )

    /**
     * Create a new [Values] object using a DSL block with direct property calls.
     */
    @Suppress("UNCHECKED_CAST")
    fun create(
        setDefaults: Boolean = true,
        context: RequestContext? = null,
        block: DM.() -> Unit
    ): Values<DM> {
        val dm = this as DM
        val items = ValuesCollectorContext.push(setDefaults)
        try {
            dm.block()
        } finally {
            ValuesCollectorContext.pop()
        }
        items.fillWithPairs(dm, emptyArray(), setDefaults)
        return Values(dm, items, context)
    }

    /** Convenience overload which applies defaults. */
    fun create(block: DM.() -> Unit): Values<DM> = create(setDefaults = true, block = block)

    override fun validate(
        values: Values<DM>,
        refGetter: () -> IsPropertyReference<Values<DM>, IsPropertyDefinition<Values<DM>>, *>?,
        failOnUnknownProperties: Boolean,
        failOnMissingRequiredValues: Boolean,
    ) {
        createValidationUmbrellaException(refGetter) { addException ->
            for ((index, orgValue) in values.values) {
                val definition = this[index] ?:
                    if (failOnUnknownProperties) {
                        addException(InvalidValueException(null, "Unknown index in Values: $index"))
                        continue
                    } else continue

                val value = values.process<Any?>(definition, orgValue, true) { true } ?: continue // skip empty values
                try {
                    definition.validate(
                        newValue = value,
                        parentRefFactory = refGetter
                    )
                } catch (e: ValidationException) {
                    addException(e)
                }
            }
            if (failOnMissingRequiredValues) {
                for (def in this) {
                    if (values.original(def.index) == null) {
                        try {
                            def.validate(newValue = null, parentRefFactory = refGetter)
                        } catch (e: ValidationException) {
                            addException(e)
                        }
                    }
                }
            }
        }
    }
}
