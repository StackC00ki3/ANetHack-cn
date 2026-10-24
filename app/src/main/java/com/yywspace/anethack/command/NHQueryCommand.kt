package com.yywspace.anethack.command

/**
 * 需要读游戏状态的查询（如旅行路线预览）。它和输入走同一个队列，
 * 游戏线程等输入时取到就地执行 [task]，然后接着等真正的输入，不算一次按键。
 * 这样查询只会在游戏停下来等输入时运行，不会和游戏逻辑同时读写关卡数据。
 */
class NHQueryCommand(val task: () -> Unit) : NHCommand(0.toChar())
