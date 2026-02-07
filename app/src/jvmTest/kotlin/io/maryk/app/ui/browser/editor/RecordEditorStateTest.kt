package io.maryk.app.ui.browser.editor

import maryk.core.models.IsRootDataModel
import maryk.core.models.DataModel
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.embed
import maryk.core.properties.definitions.incrementingMap
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.map
import maryk.core.properties.definitions.number
import maryk.core.properties.definitions.string
import maryk.core.properties.references.IncMapReference
import maryk.core.properties.types.numeric.UInt32
import maryk.core.query.changes.Change
import maryk.core.query.changes.IncMapChange
import maryk.core.query.pairs.with
import maryk.core.values.Values
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecordEditorStateTest {
    @Test
    fun buildChangesReturnsEmptyWithoutUpdates() {
        val initialValues = EditorStateModel.create {
            id with 1u
            title with "old"
        }
        @Suppress("UNCHECKED_CAST")
        val state = RecordEditState(
            EditorStateModel,
            initialValues as Values<IsRootDataModel>,
            allowFinalEdit = true,
        )

        assertTrue(state.buildChanges().isEmpty())
    }

    @Test
    fun buildChangesContainsValueAndIncMapChanges() {
        val initialValues = EditorStateModel.create {
            id with 1u
            title with "old"
        }
        @Suppress("UNCHECKED_CAST")
        val state = RecordEditState(
            EditorStateModel,
            initialValues as Values<IsRootDataModel>,
            allowFinalEdit = true,
        )
        state.updateValue(EditorStateModel.title.ref(), "new")
        val incMapRef = EditorStateModel.incMap.ref(null)
        val path = incMapRef.completeName
        @Suppress("UNCHECKED_CAST")
        state.addIncMapValue(path, incMapRef as IncMapReference<Comparable<Any>, Any, *>, "aa")

        val changes = state.buildChanges()

        assertEquals(2, changes.size)
        assertTrue(changes.any { it is Change })
        assertTrue(changes.any { it is IncMapChange })
    }

    @Test
    fun setErrorStoresOnlyLiveErrorsUntilValidation() {
        val initialValues = RequiredStateModel.create {
            id with 1u
        }
        @Suppress("UNCHECKED_CAST")
        val state = RecordEditState(
            RequiredStateModel,
            initialValues as Values<IsRootDataModel>,
            allowFinalEdit = true,
        )

        state.setError("name", "Required.")
        assertNull(state.errorFor("name"))

        state.setError("name", "Invalid format.")
        assertEquals("Invalid format.", state.errorFor("name"))
    }

    @Test
    fun setErrorStoresRequiredAfterValidateAll() {
        val initialValues = RequiredStateModel.create {
            id with 1u
        }
        @Suppress("UNCHECKED_CAST")
        val state = RecordEditState(
            RequiredStateModel,
            initialValues as Values<IsRootDataModel>,
            allowFinalEdit = true,
        )

        assertFalse(state.validateAll())
        state.setError("manual", "Required.")

        assertEquals("Required.", state.errorFor("manual"))
    }

    @Test
    fun validateAllReportsNestedListMapAndEmbeddedErrors() {
        val initialValues = NestedStateModel.create {
            id with 1u
        }
        @Suppress("UNCHECKED_CAST")
        val state = RecordEditState(
            NestedStateModel,
            initialValues as Values<IsRootDataModel>,
            allowFinalEdit = true,
        )
        state.updateValue(
            NestedStateModel.child.ref(),
            NestedEmbeddedModel.create {
                name with ""
            },
        )
        state.updateValue(NestedStateModel.tags.ref(), listOf(""))
        state.updateValue(NestedStateModel.attributes.ref(), mapOf("k" to ""))

        assertFalse(state.validateAll())
        assertNotNull(state.errorFor("child.name"))
        assertNotNull(state.errorFor("tags[0]"))
        assertNotNull(state.errorFor("attributes[k].value"))
    }

    @Test
    fun pendingIncMapFocusLifecycleClearsOnConsumeAndFinalRemove() {
        val initialValues = EditorStateModel.create {
            id with 1u
        }
        @Suppress("UNCHECKED_CAST")
        val state = RecordEditState(
            EditorStateModel,
            initialValues as Values<IsRootDataModel>,
            allowFinalEdit = true,
        )
        val incMapRef = EditorStateModel.incMap.ref(null)
        val path = incMapRef.completeName
        @Suppress("UNCHECKED_CAST")
        state.addIncMapValue(path, incMapRef as IncMapReference<Comparable<Any>, Any, *>, "aa")

        assertEquals(path, state.pendingIncMapFocusPath)
        state.consumeIncMapFocus("other")
        assertEquals(path, state.pendingIncMapFocusPath)
        state.consumeIncMapFocus(path)
        assertNull(state.pendingIncMapFocusPath)

        state.addIncMapValue(path, incMapRef, "bb")
        assertEquals(path, state.pendingIncMapFocusPath)
        state.removeIncMapValue(path, 0)
        state.removeIncMapValue(path, 0)
        assertTrue(state.pendingIncMapValues(path).isEmpty())
        assertNull(state.pendingIncMapFocusPath)
    }

    @Test
    fun hasChangesTracksPendingIncMapAdds() {
        val initialValues = EditorStateModel.create {
            id with 1u
        }
        @Suppress("UNCHECKED_CAST")
        val state = RecordEditState(
            EditorStateModel,
            initialValues as Values<IsRootDataModel>,
            allowFinalEdit = true,
        )
        val incMapRef = EditorStateModel.incMap.ref(null)
        val path = incMapRef.completeName

        assertFalse(state.hasChanges)
        @Suppress("UNCHECKED_CAST")
        state.addIncMapValue(path, incMapRef as IncMapReference<Comparable<Any>, Any, *>, "aa")
        assertTrue(state.hasChanges)
        state.removeIncMapValue(path, 0)
        assertFalse(state.hasChanges)
    }
}

private object EditorStateModel : RootDataModel<EditorStateModel>(
    keyDefinition = {
        EditorStateModel.run { id.ref() }
    },
) {
    val id by number(index = 1u, type = UInt32, final = true)
    val title by string(index = 2u, required = false)
    val incMap by incrementingMap(
        index = 3u,
        keyNumberDescriptor = UInt32,
        valueDefinition = StringDefinition(minSize = 2u),
        required = false,
    )
}

private object RequiredStateModel : RootDataModel<RequiredStateModel>(
    keyDefinition = {
        RequiredStateModel.run { id.ref() }
    },
) {
    val id by number(index = 1u, type = UInt32, final = true)
    val name by string(index = 2u)
}

private object NestedEmbeddedModel : DataModel<NestedEmbeddedModel>() {
    val name by string(
        index = 1u,
        minSize = 2u,
    )
}

private object NestedStateModel : RootDataModel<NestedStateModel>(
    keyDefinition = {
        NestedStateModel.run { id.ref() }
    },
) {
    val id by number(index = 1u, type = UInt32, final = true)
    val child by embed(
        index = 2u,
        dataModel = { NestedEmbeddedModel },
        required = false,
    )
    val tags by list(
        index = 3u,
        required = false,
        valueDefinition = StringDefinition(minSize = 2u),
    )
    val attributes by map(
        index = 4u,
        required = false,
        keyDefinition = StringDefinition(minSize = 1u),
        valueDefinition = StringDefinition(minSize = 2u),
    )
}
