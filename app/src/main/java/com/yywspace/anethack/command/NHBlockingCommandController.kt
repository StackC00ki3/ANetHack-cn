package com.yywspace.anethack.command

import android.util.Log
import java.time.LocalDateTime
import java.util.concurrent.LinkedBlockingQueue

class NHBlockingCommandController {
    val cmdQueue = LinkedBlockingQueue<NHCommand>(1000)
    var lastCmdTime: LocalDateTime = LocalDateTime.now()

    fun sendCommand(command: NHCommand) {
        lastCmdTime = LocalDateTime.now()
        // 下面这部分判断只有手动在代码中构建Command时才会用到
        // 其余情况直接使用序列命令：S20l 即可
        if(command.count > 1) {
            command.count.toString().toCharArray().forEach {
                cmdQueue.put(NHCommand(it))
            }
        }
        cmdQueue.put(command)
    }

    fun waitForCommand(): NHCommand {
        val cmd = takeInput()
        try {
            Thread.sleep(50)
        } catch (_: InterruptedException) {
        }
        return cmd
    }

    /** 取下一条输入；排在前面的 [NHQueryCommand] 就地执行掉，不当成输入返回。 */
    fun takeInput(): NHCommand {
        while (true) {
            val cmd = cmdQueue.take()
            if (cmd !is NHQueryCommand)
                return cmd
            runQuery(cmd)
        }
    }

    fun runQuery(query: NHQueryCommand) {
        try {
            query.task()
        } catch (e: Exception) {
            Log.w("NHCommand", "query failed", e)
        }
    }

    /**
     * 队列里还有多少条玩家输入没被游戏取走。
     * 脚本序列和弹窗自己的回答不算，它们不属于"连按积压"。
     */
    fun pendingUserInputCount(): Int = cmdQueue.count { it.isUserInput() }

    private fun NHCommand.isUserInput(): Boolean =
        !fromSequence
            && this !is NHAnswerCommand
            && this !is NHMenuCommand
            && this !is NHLineCommand
            && this !is NHQueryCommand

    inline fun <reified T:NHCommand>findAnyCommand():T?{
        for(cmd in cmdQueue) {
            if (cmd is T) {
                while (cmdQueue.isNotEmpty()) {
                    val taken = cmdQueue.take()
                    if(cmd == taken)
                        return cmd
                    if (taken is NHQueryCommand)
                        runQuery(taken)
                }
                return null
            }
        }
        return null
    }

    inline fun <reified T:NHCommand>waitForAnyCommand(noinline otherCommandDiscard:((NHCommand)->Unit)? = null):T{
        while (true) {
            val cmd = takeInput()
            if (cmd is T)
                return cmd
            try {
                Thread.sleep(50)
            } catch (_: InterruptedException) {
            }
            otherCommandDiscard?.invoke(cmd)
        }
    }

    fun clear() {
        cmdQueue.clear()
        lastCmdTime = LocalDateTime.now()
    }

    /**
     * 弹窗即将弹出时调用：丢弃连按积累下来的普通按键，避免它们被当成对弹窗的回答。
     * 脚本序列排入的指令（[NHCommand.fromSequence]）和弹窗自己的回答指令都保留，
     * 所以按下按钮后的选择不会受影响。
     */
    fun discardPendingInput() {
        cmdQueue.removeIf { it.isUserInput() }
    }
}