package com.yywspace.anethack.command

import java.io.PushbackReader
import java.io.StringReader

object NHCommandParser {
    /*
    key command: a
    extended command: #read
    sequence command: S#engrave#-L"Elbereth"
        key command in sequence: -
        extended command in sequence: #read#
        line command in sequence: L"Elbereth"
    key notation (case-sensitive, also inside key sequences):
        ctrl+x: ^x
        alt+x:  M-x  (uppercase M only; char code 128+x, same as the Meta soft keyboard)
        escape: esc
        numeric char code (legacy): 16
    anything else is a key sequence executed left to right: 10s^p -> '1','0','s',Ctrl-P
    */
    fun parseNHCommand(command:String):List<NHCommand> {
        if(command.startsWith("#")) {
            return listOf(NHExtendCommand(command))
        } else if(command.startsWith("S")) {
            return parseSequenceCommands(command)
        }
        return parseKeyCommand(command)
    }

    private val ESC = 27.toChar()

    /** Meta/Alt is delivered to the NetHack core as the char with the 8th bit set. */
    const val META_BIT = 128

    /** ASCII caret notation: ^A-^Z -> 1-26, ^@..^_ -> 0-31, ^? -> 127 (Del). */
    fun controlCode(c: Char): Int? = when (c) {
        in 'a'..'z' -> c.code - 'a'.code + 1
        in 'A'..'Z' -> c.code - 'A'.code + 1
        in '@'..'_' -> c.code - '@'.code
        '?' -> 127
        else -> null
    }

    private fun parseKeyCommand(command:String):List<NHCommand> {
        if (command.isEmpty()) return listOf(NHKeyCommand(ESC))
        // legacy numeric char code: 16 -> Ctrl-P, 27 -> ESC
        command.toIntOrNull()?.let { return listOf(NHKeyCommand(it.toChar())) }
        if (command.equals("esc", ignoreCase = true))
            return listOf(NHKeyCommand(ESC))
        return parseKeySequence(command)
    }

    // key sequence executed left to right, with embedded ^x (ctrl) and M-x (alt)
    private fun parseKeySequence(command:String):List<NHCommand> {
        val commands = mutableListOf<NHCommand>()
        var i = 0
        while (i < command.length) {
            if (command[i] == '^' && i + 1 < command.length) {
                val code = controlCode(command[i + 1])
                if (code != null) {
                    commands.add(NHKeyCommand(code.toChar()))
                    i += 2
                    continue
                }
            }
            if (command[i] == 'M' && command.getOrNull(i + 1) == '-') {
                val key = command.getOrNull(i + 2)
                if (key == '^') {
                    val code = command.getOrNull(i + 3)?.let(::controlCode)
                    if (code != null) {
                        commands.add(NHKeyCommand((code + META_BIT).toChar()))
                        i += 4
                        continue
                    }
                } else if (key != null) {
                    commands.add(NHKeyCommand((key.code + META_BIT).toChar()))
                    i += 3
                    continue
                }
            }
            commands.add(NHKeyCommand(command[i]))
            i += 1
        }
        return commands
    }

    private fun readChar(reader:PushbackReader):Char? {
        val next = reader.read()
        if (next == -1)
            return null
        return next.toChar()
    }

    // extended command: #read#
    private fun parseExtendedCommand(reader:PushbackReader):NHExtendCommand {
        val extendedStr = StringBuilder("#")
        var next = readChar(reader)
        while (next != null && next != '#') {
            extendedStr.append(next)
            next = readChar(reader)
        }
        return NHExtendCommand(extendedStr.toString())
    }

    // line command: L"Elbereth"
    private fun parseLineCommand(reader:PushbackReader):NHLineCommand? {
        val extendedStr = StringBuilder()
        // 第二个字符为空或不为"则回退及返回
        var next = readChar(reader)
        if (next == null || next != '"') {
            next?.apply {
                reader.unread(code)
            }
            return null
        }
        // 第三个字符开始向后读取
        next = readChar(reader)
        while (next != null && next != '"') {
            extendedStr.append(next)
            next = readChar(reader)
        }
        if (next != '"') {
            // 不满足格式全部回退，但不回退字符L
            reader.unread(extendedStr.toString().toCharArray())
            reader.unread('"'.code)
            return null
        } else
            return NHLineCommand(extendedStr.toString())
    }

    // S#engrave#-L"Elbereth"
    // TODO 适配数字前缀，涉及Menu、Question，向Sd20a这样的指令并不能达到预期
    private fun parseSequenceCommands(command:String):List<NHCommand> {
        val commands = mutableListOf<NHCommand>()
        val reader = PushbackReader(StringReader(command), command.length)
        readChar(reader) // 排除序列标识S
        var next = readChar(reader)
        while (next != null) {
            when(next) {
                '#' -> {
                    commands.add(parseExtendedCommand(reader))
                }
                'L' -> {
                    val lineCmd = parseLineCommand(reader)
                    if (lineCmd != null)
                        commands.add(lineCmd)
                    else
                        commands.add(NHKeyCommand('L'))
                }
                else -> {
                    commands.add(NHKeyCommand(next))
                }
            }
            next = readChar(reader)
        }
        return commands
    }
}