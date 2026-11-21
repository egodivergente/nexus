package com.vidadomu.nexus.assistant

import com.vidadomu.nexus.agent.core.spend.PlanState

/**
 * Your last message and everything after it, and whether that exchange can be taken back: to edit the message or to
 * ask for another answer. Only while nothing it produced went further: no order sent, no change applied to Nexus,
 * no board, and no tool that changes things (those stay done, and a second answer would repeat them).
 */
internal object LastTurn {
    /** Plan states a card can be in and still go away with its turn: nothing was sent for it. */
    val OPEN_PLANS = setOf(PlanState.PREPARED, PlanState.AWAITING_CONFIRMATION, PlanState.DISCARDED)

    private fun start(items: List<ChatItem>): Int = items.indexOfLast { it is ChatItem.User }

    fun rewindable(items: List<ChatItem>, harmlessTools: Set<String>, planBlocks: (String) -> Boolean): Boolean {
        val start = start(items)
        if (start < 0) return false
        return items.drop(start + 1).all { item ->
            when (item) {
                is ChatItem.Assistant -> item.stop != "STREAMING" && item.toolCalls.all { it in harmlessTools }
                is ChatItem.Notice, is ChatItem.ModeSwitch -> true
                is ChatItem.PlanCard -> item.orderId == null && !planBlocks(item.planId)
                is ChatItem.Proposal -> item.state == ProposalState.PENDING || item.state == ProposalState.REJECTED || item.state == ProposalState.FAILED
                else -> false
            }
        }
    }

    /** The last message and, if it has one, the answer that closed it: where «Editar» and «Otra respuesta» go. */
    fun ids(items: List<ChatItem>): Pair<String, String?>? {
        val start = start(items).takeIf { it >= 0 } ?: return null
        return items[start].id to items.drop(start + 1).lastOrNull { it is ChatItem.Assistant }?.id
    }

    /**
     * What is left once the last exchange is taken back, and the message it began with. A summary carried from another
     * conversation that went out with that message is marked unsent again, so it goes with the next one.
     */
    fun takeBack(items: List<ChatItem>): Pair<List<ChatItem>, ChatItem.User>? {
        val start = start(items).takeIf { it >= 0 } ?: return null
        val previousUser = items.subList(0, start).indexOfLast { it is ChatItem.User }
        val kept = items.take(start).mapIndexed { i, item ->
            if (item is ChatItem.Context && item.sent && i > previousUser) item.copy(sent = false) else item
        }
        return kept to (items[start] as ChatItem.User)
    }

    /** The words to put back in the composer: a message of attachments alone was shown as «(adjuntos)». */
    fun editableText(message: ChatItem.User): String =
        if (message.text == "(adjuntos)" && message.attachments.isNotEmpty()) "" else message.text
}
