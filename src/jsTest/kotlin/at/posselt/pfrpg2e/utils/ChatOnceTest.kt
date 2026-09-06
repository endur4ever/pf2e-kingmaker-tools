package at.posselt.pfrpg2e.utils

import kotlinx.browser.document
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.MouseEvent
import org.w3c.dom.events.MouseEventInit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The once-semantics behind every GM-confirmed offer card.
 *
 * The window this closes is real and was measured in production: an offer's idempotency guard
 * reads an actor flag the server has not acknowledged yet, so a second click arriving before the
 * write lands passes the stale guard and applies the effect twice.
 */
class ChatOnceTest {
    private var clicks = 0

    private fun chatLog(): HTMLElement =
        document.getElementById("chat").unsafeCast<HTMLElement>()

    private fun button(): HTMLElement =
        document.querySelector(".km-test-offer").unsafeCast<HTMLElement>()

    private fun click() {
        button().dispatchEvent(MouseEvent("click", MouseEventInit(bubbles = true)))
    }

    @BeforeTest
    fun setUp() {
        clicks = 0
        // a fresh id each run would be ideal, but bindChatClick marks the CONTAINER, and a new
        // container element loses the marker -- which is exactly the reset this needs
        document.body?.innerHTML = """
            <div id="chat">
              <div class="chat-message">
                <button class="km-test-offer" type="button">Apply</button>
              </div>
            </div>
        """.trimIndent()
    }

    @AfterTest
    fun tearDown() {
        document.body?.innerHTML = ""
    }

    @Test
    fun aOnceButtonFiresOnceAndIsDisabledAfterwards() {
        bindChatClick(".km-test-offer", once = true) { _, _, _ -> clicks++ }
        click()
        assertEquals(1, clicks, "the first click must run the handler")
        assertTrue(button().hasAttribute("disabled"), "a consumed offer must be disabled")
        assertTrue(button().hasAttribute("data-km-consumed"), "a consumed offer must be marked")
        assertTrue(
            chatLog().querySelector(".chat-message")!!.unsafeCast<HTMLElement>()
                .classList.contains("km-card-resolved"),
            "the message must read as resolved",
        )
    }

    @Test
    fun aSecondClickOnAOnceButtonIsANoOp() {
        bindChatClick(".km-test-offer", once = true) { _, _, _ -> clicks++ }
        click()
        click()
        click()
        assertEquals(1, clicks, "an offer must apply at most once no matter how often it is clicked")
    }

    @Test
    fun aButtonWithoutOnceStaysClickable() {
        bindChatClick(".km-test-offer") { _, _, _ -> clicks++ }
        click()
        click()
        assertEquals(2, clicks, "a re-clickable control must not be consumed")
        assertFalse(button().hasAttribute("disabled"))
    }

    @Test
    fun releasingAClaimHandsTheClickBack() {
        // the refusal path: the card's actor failed to resolve, or the handler threw, so nothing
        // was applied and the GM must still be able to action the card
        bindChatClick(".km-test-offer", once = true) { _, target, _ ->
            clicks++
            if (clicks == 1) releaseChatClickClaim(target)
        }
        click()
        assertFalse(button().hasAttribute("disabled"), "a released claim must re-enable the button")
        assertFalse(button().hasAttribute("data-km-consumed"))
        click()
        assertEquals(2, clicks, "a released button must accept the next click")
    }

    @Test
    fun releasingOneOfferDoesNotUnGreyASiblingThatWasAnswered() {
        document.body?.innerHTML = """
            <div id="chat">
              <div class="chat-message">
                <button class="km-test-offer" type="button">Apply</button>
                <button class="km-test-sibling" type="button">Other</button>
              </div>
            </div>
        """.trimIndent()
        bindChatClick(".km-test-sibling", once = true) { _, _, _ -> }
        bindChatClick(".km-test-offer", once = true) { _, target, _ -> releaseChatClickClaim(target) }
        document.querySelector(".km-test-sibling").unsafeCast<HTMLElement>()
            .dispatchEvent(MouseEvent("click", MouseEventInit(bubbles = true)))
        click()
        val message = document.querySelector(".chat-message").unsafeCast<HTMLElement>()
        assertTrue(
            message.classList.contains("km-card-resolved"),
            "the sibling was genuinely answered, so the message must stay resolved",
        )
    }
}
