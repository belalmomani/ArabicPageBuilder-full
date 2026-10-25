package com.arabicpagebuilder.core.storage

import com.arabicpagebuilder.core.model.Action
import com.arabicpagebuilder.core.model.ActionType
import com.arabicpagebuilder.core.model.Condition
import com.arabicpagebuilder.core.model.ConditionType
import com.arabicpagebuilder.core.model.Element
import com.arabicpagebuilder.core.model.ElementType
import com.arabicpagebuilder.core.model.Interaction
import com.arabicpagebuilder.core.model.PageDocument
import com.arabicpagebuilder.core.model.TextDirection
import com.arabicpagebuilder.core.model.Trigger
import com.arabicpagebuilder.core.model.TriggerType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class InteractionFormatTest {

    private fun rule(id: String = "r1", value: String = "تم", actions: Int = 1) = Interaction(
        id = id,
        name = "إظهار التفاصيل",
        trigger = Trigger(TriggerType.CLICK, "btn"),
        matchAll = false,
        conditions = listOf(Condition(ConditionType.NOT_EMPTY, "name"), Condition(ConditionType.EQUALS, "name", "\"x\" <y>")),
        actions = List(actions) { Action(ActionType.SET_TEXT, "details", value) },
        elseActions = listOf(Action(ActionType.MESSAGE, "", "املأ الاسم")),
        preventDefault = false,
    )

    private fun data(interactions: List<Interaction>): ProjectData {
        val root = Element("root", ElementType.PAGE_ROOT, emptyMap(), listOf(Element("btn", ElementType.BUTTON), Element("details", ElementType.SECTION)))
        return ProjectData("م", 1L, 2L, PageDocument("ع", TextDirection.RTL, root, interactions))
    }

    private fun failure(text: String) = assertIs<DecodeResult.Failure>(ProjectFormat.decode(text))

    @Test
    fun interactionsRoundTripExactly() {
        val d = data(listOf(rule("a"), rule("b", "نص آخر")))
        val back = assertIs<DecodeResult.Success>(ProjectFormat.decode(ProjectFormat.encode(d)))
        assertEquals(d, back.data)
        assertEquals(2, back.data.document.interactions.size)
        assertEquals(false, back.data.document.interactions[0].preventDefault)
    }

    @Test
    fun oldProjectsWithoutInteractionsStillLoad() {
        val json = ProjectFormat.encode(data(emptyList()))
        val old = json.replace(",\"interactions\":[]", "")
        assertTrue(!old.contains("interactions"))
        val back = assertIs<DecodeResult.Success>(ProjectFormat.decode(old))
        assertTrue(back.data.document.interactions.isEmpty())
    }

    @Test
    fun danglingReferencesAreAllowedWhenLoading() {
        val dangling = rule().copy(trigger = Trigger(TriggerType.CLICK, "deleted"))
        assertIs<DecodeResult.Success>(ProjectFormat.decode(ProjectFormat.encode(data(listOf(dangling)))))
    }

    @Test
    fun unknownTypesAreRejected() {
        val json = ProjectFormat.encode(data(listOf(rule())))
        assertEquals(FormatError.INVALID_STRUCTURE, failure(json.replace("\"type\":\"CLICK\"", "\"type\":\"WARP\"")).error)
        assertEquals(FormatError.INVALID_STRUCTURE, failure(json.replace("\"type\":\"SET_TEXT\"", "\"type\":\"EXEC\"")).error)
        assertEquals(FormatError.INVALID_STRUCTURE, failure(json.replace("\"type\":\"NOT_EMPTY\"", "\"type\":\"EVAL\"")).error)
    }

    @Test
    fun duplicateOrBlankInteractionIdsAreRejected() {
        assertEquals(FormatError.INVALID_STRUCTURE, failure(ProjectFormat.encode(data(listOf(rule("a"), rule("a"))))).error)
        assertEquals(FormatError.INVALID_STRUCTURE, failure(ProjectFormat.encode(data(listOf(rule(" "))))).error)
    }

    @Test
    fun limitsAreEnforced() {
        val many = List(201) { rule("r$it") }
        assertEquals(FormatError.INVALID_STRUCTURE, failure(ProjectFormat.encode(data(many))).error)
        assertEquals(FormatError.INVALID_STRUCTURE, failure(ProjectFormat.encode(data(listOf(rule(actions = 21))))).error)
        assertEquals(FormatError.INVALID_STRUCTURE, failure(ProjectFormat.encode(data(listOf(rule(value = "x".repeat(501)))))).error)
        assertIs<DecodeResult.Success>(ProjectFormat.decode(ProjectFormat.encode(data(listOf(rule(actions = 20, value = "x".repeat(500)))))))
    }
}
