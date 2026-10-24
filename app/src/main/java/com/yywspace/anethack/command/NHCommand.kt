package com.yywspace.anethack.command

open class NHCommand(val key:Char, var count:Int = 1) {
    /**
     * 由多键序列（如 S#engrave#-nL"Elbereth":）一次性排入的指令。
     * 弹窗出现时会清理连按积累的普通按键，但脚本序列要保留下来当预设回答。
     */
    var fromSequence: Boolean = false
}
