package com.yywspace.anethack.command

import org.junit.Assert.assertEquals
import org.junit.Test


class NHBlockingCommandControllerTest {
    private fun controllerOf(vararg commands: NHCommand) = NHBlockingCommandController().apply {
        commands.forEach { sendCommand(it) }
    }

    @Test
    fun discardingPendingInputDropsMashedKeys() {
        val controller = controllerOf(
            NHKeyCommand('h'), NHKeyCommand('h'), NHKeyCommand('l'),
        )

        controller.discardPendingInput()

        assertEquals(0, controller.cmdQueue.size)
    }

    @Test
    fun discardingPendingInputKeepsDialogAnswers() {
        val answer = NHAnswerCommand('y')
        val menu = NHMenuCommand(13.toChar(), listOf(1L, 1L))
        val line = NHLineCommand("Elbereth")
        val controller = controllerOf(NHKeyCommand('h'), answer, menu, line)

        controller.discardPendingInput()

        assertEquals(listOf<NHCommand>(answer, menu, line), controller.cmdQueue.toList())
    }

    @Test
    fun discardingPendingInputKeepsScriptedSequences() {
        // S#engrave#-nL"Elbereth": 会被拆成多条指令一次性排入，弹窗时仍要当预设回答
        val scripted = NHKeyCommand('E').apply { fromSequence = true }
        val controller = controllerOf(NHKeyCommand('h'), scripted)

        controller.discardPendingInput()

        assertEquals(listOf(scripted), controller.cmdQueue.toList())
    }

    @Test
    fun countsOnlyPendingUserInput() {
        val controller = controllerOf(
            NHKeyCommand('h'),
            NHKeyCommand('h'),
            NHAnswerCommand('y'),
            NHKeyCommand('E').apply { fromSequence = true },
        )

        // 脚本序列和弹窗回答不算连按积压
        assertEquals(2, controller.pendingUserInputCount())

        controller.discardPendingInput()

        assertEquals(0, controller.pendingUserInputCount())
        assertEquals(2, controller.cmdQueue.size)
    }
}
