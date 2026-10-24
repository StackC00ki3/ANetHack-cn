package com.yywspace.anethack.command


class NHPosCommand(key:Char, val x:Int, val y:Int, mod:PosMod) :NHCommand(key) {
    var mod:Int = mod.ordinal

    constructor(x:Int, y:Int, mod:PosMod) : this(0.toChar(), x, y, mod)

    enum class PosMod {
        NONE, TRAVEL, LOOK,
        /** 点在自己的格子上；androidwin.c 的 AND_POS_SELF_ACTION 与序号 3 对应。 */
        SELF,
        /** 直接旅行到该格，不弹 therecmdmenu 菜单；对应 androidentry.h 的 AND_POS_TRAVEL_TO（序号 4）。 */
        TRAVEL_TO
    }
}