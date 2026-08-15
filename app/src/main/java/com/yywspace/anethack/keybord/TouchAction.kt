package com.yywspace.anethack.keybord

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.yywspace.anethack.R
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt


data class TouchAction(
    val id: String,
    val command: String,
    @StringRes val labelRes: Int = 0,
    val customLabel: String? = null,
    @DrawableRes val iconRes: Int = TouchActionIcons.forCommand(command),
    val keyHint: String = TouchActionKeyHints.forCommand(command),
) {
    fun label(context: Context): String =
        customLabel ?: if (labelRes != 0) context.getString(labelRes) else command
}

data class TouchActionCategory(
    val id: String,
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int,
    val pages: List<List<TouchAction>>,
) {
    val actionCount: Int
        get() = pages.sumOf { it.size }

    fun label(context: Context): String = context.getString(labelRes)
}

object TouchActionParser {
    const val MAX_ACTIONS_PER_PAGE = 5
    internal val LIMIT_TOKEN = Regex("@(\\d+)")
    private val dockCommands = setOf("Setting", "Keyboard", "Center")

    fun parsePages(commandPanel: String): List<List<TouchAction>> {
        val pages = mutableListOf<List<TouchAction>>()
        var customIndex = 0
        commandPanel.lineSequence().forEach { row ->
            val actions = row.trim()
                .split(Regex("\\s+"))
                .filter { it.isNotEmpty() }
                .filterNot { it.matches(LIMIT_TOKEN) }
                .mapNotNull { value -> parseAction(value, customIndex++) }
                .filterNot { it.command in dockCommands }
            actions.chunked(MAX_ACTIONS_PER_PAGE).forEach(pages::add)
        }
        return pages
    }

    internal fun parseAction(value: String, index: Int): TouchAction? {
        val parts = value.split("|", limit = 3)
        val command = parts.firstOrNull()?.trim().orEmpty()
        if (command.isEmpty()) return null
        val customLabel = parts.getOrNull(1)?.trim()?.takeUnless { it.isEmpty() }
        val iconKey = parts.getOrNull(2)?.trim()?.takeUnless { it.isEmpty() }
        val builtIn = TouchActionCatalog.actionForCommand(command)
        return TouchAction(
            id = "custom_${index}_$command",
            command = command,
            labelRes = if (customLabel == null) builtIn?.labelRes ?: 0 else 0,
            customLabel = customLabel,
            iconRes = iconKey?.let(TouchActionIcons::resForKey)
                ?: builtIn?.iconRes
                ?: TouchActionIcons.forCommand(command),
            keyHint = builtIn?.keyHint ?: TouchActionKeyHints.forCommand(command),
        )
    }
}

object TouchActionCatalog {
    private const val LEGACY_DEFAULT_COMMAND_PANEL =
        "Setting SS|Save #quit|Quit S#engrave#-nL\"Elbereth\":|Elber S20s|20s Keyboard|Abc\n" +
            "#|Extend d|Drop e|Eat :|Look ,|Pick .|Rest"

    fun build(commandPanel: String?): List<TouchActionCategory> {
        val categories = builtInCategories().toMutableList()
        val configured = commandPanel.orEmpty()
        if (configured.isNotBlank() && !isLegacyDefault(configured)) {
            val customPages = TouchActionParser.parsePages(configured)
            if (customPages.isNotEmpty()) {
                categories += TouchActionCategory(
                    id = "custom",
                    labelRes = R.string.touch_category_custom,
                    iconRes = R.drawable.ic_touch_extended,
                    pages = customPages,
                )
            }
        }
        return categories
    }

    fun builtInCategories(): List<TouchActionCategory> = listOf(
        category(
            id = "combat",
            labelRes = R.string.touch_category_combat,
            iconRes = R.drawable.ic_touch_combat,
            action("combat_apply", "a", R.string.touch_action_apply),
            action("combat_fire", "f", R.string.touch_action_fire),
            action("combat_throw", "t", R.string.touch_action_throw),
            action("combat_zap", "z", R.string.touch_action_zap),
            action("combat_cast", "Z", R.string.touch_action_cast),
            action("combat_kick", "4", R.string.touch_action_kick, "Ctrl-D"),
            action("combat_force_fight", "F", R.string.touch_action_force_fight),
            action("combat_wield", "w", R.string.touch_action_wield),
            action("combat_quiver", "Q", R.string.touch_action_quiver),
            action("combat_swap", "x", R.string.touch_action_swap),
            action("combat_two_weapon", "X", R.string.touch_action_two_weapon),
            action("combat_enhance", "#enhance", R.string.touch_action_enhance),
            action("combat_turn", "#turn", R.string.touch_action_turn_undead),
            action("combat_monster", "#monster", R.string.touch_action_monster_ability),
            action("combat_ride", "#ride", R.string.touch_action_ride),
        ),
        category(
            id = "items",
            labelRes = R.string.touch_category_items,
            iconRes = R.drawable.ic_touch_inventory,
            action("items_inventory_type", "I", R.string.touch_action_inventory_type),
            action("items_pickup", ",", R.string.touch_action_pickup),
            action("items_drop", "d", R.string.touch_action_drop),
            action("items_drop_type", "D", R.string.touch_action_drop_type),
            action("items_eat", "e", R.string.touch_action_eat),
            action("items_quaff", "q", R.string.touch_action_quaff),
            action("items_read", "r", R.string.touch_action_read),
            action("items_wear", "W", R.string.touch_action_wear),
            action("items_takeoff", "T", R.string.touch_action_take_off),
            action("items_takeoff_all", "A", R.string.touch_action_take_off_all),
            action("items_put_on", "P", R.string.touch_action_put_on),
            action("items_remove", "R", R.string.touch_action_remove),
            action("items_loot", "#loot", R.string.touch_action_loot),
            action("items_dip", "#dip", R.string.touch_action_dip),
            action("items_invoke", "#invoke", R.string.touch_action_invoke),
            action("items_rub", "#rub", R.string.touch_action_rub),
            action("items_tip", "#tip", R.string.touch_action_tip),
            action("items_pay", "p", R.string.touch_action_pay),
            action("items_adjust", "#adjust", R.string.touch_action_adjust),
            action("items_call", "C", R.string.touch_action_call),
            action("items_autopickup", "@", R.string.touch_action_autopickup),
            action("items_perminv", "|", R.string.touch_action_permanent_inventory),
            action("items_see_all", "*", R.string.touch_action_see_all_equipment),
            action("items_see_weapon", ")", R.string.touch_action_see_weapon),
            action("items_see_armor", "[", R.string.touch_action_see_armor),
            action("items_see_rings", "=", R.string.touch_action_see_rings),
            action("items_see_amulet", "\"", R.string.touch_action_see_amulet),
            action("items_see_tools", "(", R.string.touch_action_see_tools),
            action("items_gold", "$", R.string.touch_action_show_gold),
            action("items_spells", "+", R.string.touch_action_show_spells),
        ),
        category(
            id = "explore",
            labelRes = R.string.touch_category_explore,
            iconRes = R.drawable.ic_touch_look,
            action("explore_open", "o", R.string.touch_action_open),
            action("explore_close", "c", R.string.touch_action_close),
            action("explore_look", ":", R.string.touch_action_look),
            action("explore_glance", ";", R.string.touch_action_glance),
            action("explore_search", "s", R.string.touch_action_search),
            action("explore_up", "<", R.string.touch_action_upstairs),
            action("explore_down", ">", R.string.touch_action_downstairs),
            action("explore_travel", "_", R.string.touch_action_travel),
            action("explore_retravel", "31", R.string.touch_action_retravel, "Ctrl-_"),
            action("explore_run", "G", R.string.touch_action_run),
            action("explore_rush", "g", R.string.touch_action_rush),
            action("explore_no_pickup", "m", R.string.touch_action_move_no_pickup),
            action("explore_no_pickup_far", "M", R.string.touch_action_move_no_pickup_far),
            action("explore_chat", "#chat", R.string.touch_action_chat),
            action("explore_force", "#force", R.string.touch_action_force_lock),
            action("explore_jump", "#jump", R.string.touch_action_jump),
            action("explore_teleport", "20", R.string.touch_action_teleport, "Ctrl-T"),
            action("explore_untrap", "#untrap", R.string.touch_action_untrap),
            action("explore_offer", "#offer", R.string.touch_action_offer),
            action("explore_pray", "#pray", R.string.touch_action_pray),
            action("explore_sit", "#sit", R.string.touch_action_sit),
            action("explore_engrave", "E", R.string.touch_action_engrave),
            action("explore_wipe", "#wipe", R.string.touch_action_wipe),
            action("explore_wait", ".", R.string.touch_action_wait),
            action("explore_here_menu", "#herecmdmenu", R.string.touch_action_here_menu),
            action("explore_there_menu", "#therecmdmenu", R.string.touch_action_there_menu),
        ),
        category(
            id = "info",
            labelRes = R.string.touch_category_info,
            iconRes = R.drawable.ic_touch_extended,
            action("info_help", "?", R.string.touch_action_help),
            action("info_whatis", "/", R.string.touch_action_whatis),
            action("info_whatdoes", "&", R.string.touch_action_whatdoes),
            action("info_overview", "15", R.string.touch_action_overview, "Ctrl-O"),
            action("info_attributes", "24", R.string.touch_action_attributes, "Ctrl-X"),
            action("info_known", "\\", R.string.touch_action_discoveries),
            action("info_known_class", "`", R.string.touch_action_discoveries_by_class),
            action("info_chronicle", "v", R.string.touch_action_chronicle),
            action("info_conduct", "#conduct", R.string.touch_action_conduct),
            action("info_genocided", "#genocided", R.string.touch_action_genocided),
            action("info_vanquished", "#vanquished", R.string.touch_action_vanquished),
            action("info_history", "#history", R.string.touch_action_history),
            action("info_prev_message", "16", R.string.touch_action_previous_message, "Ctrl-P"),
            action("info_lookaround", "#lookaround", R.string.touch_action_look_around),
            action("info_show_trap", "^", R.string.touch_action_show_trap),
            action("info_terrain", "127", R.string.touch_action_terrain, "Del"),
            action("info_annotate", "#annotate", R.string.touch_action_annotate),
            action("info_version_short", "V", R.string.touch_action_version_short),
            action("info_version", "#version", R.string.touch_action_version),
        ),
        category(
            id = "system",
            labelRes = R.string.touch_category_system,
            iconRes = R.drawable.ic_touch_settings,
            action("system_save", "SS", R.string.touch_action_save, "S"),
            action("system_quit", "#quit", R.string.touch_action_quit),
            action("system_options", "O", R.string.touch_action_options),
            action("system_options_full", "#optionsfull", R.string.touch_action_options_full),
            action("system_extended", "#", R.string.touch_action_extended),
            action("system_extended_list", "#?", R.string.touch_action_extended_list),
            action("system_repeat", "1", R.string.touch_action_repeat, "Ctrl-A"),
            action("system_redraw", "18", R.string.touch_action_redraw, "Ctrl-R"),
            action("system_save_options", "#saveoptions", R.string.touch_action_save_options),
            action("system_toggle", "#toggle", R.string.touch_action_toggle_option),
        ),
    )

    fun isLegacyDefault(commandPanel: String): Boolean =
        normalize(commandPanel) == normalize(LEGACY_DEFAULT_COMMAND_PANEL)

    /** The former fixed dock row, now the bottom row of the command bar. */
    fun dockActions(): List<TouchAction> = listOf(
        TouchAction(
            id = "dock_cancel",
            command = "27",
            labelRes = R.string.touch_action_cancel,
            iconRes = R.drawable.ic_touch_cancel,
            keyHint = "ESC",
        ),
        TouchAction(
            id = "dock_inventory",
            command = "i",
            labelRes = R.string.touch_action_inventory,
            iconRes = R.drawable.ic_touch_inventory,
        ),
        TouchAction(
            id = "dock_keyboard",
            command = "Keyboard",
            labelRes = R.string.touch_action_keyboard,
            iconRes = R.drawable.ic_touch_keyboard,
        ),
        TouchAction(
            id = "dock_center",
            command = "Center",
            labelRes = R.string.touch_action_center,
            iconRes = R.drawable.ic_touch_center,
        ),
        TouchAction(
            id = "dock_setting",
            command = "Setting",
            labelRes = R.string.touch_action_settings,
            iconRes = R.drawable.ic_touch_settings,
        ),
    )

    fun actionForCommand(command: String): TouchAction? =
        (builtInCategories().flatMap { it.pages.flatten() } + dockActions())
            .firstOrNull { it.command == command }

    private fun category(
        id: String,
        @StringRes labelRes: Int,
        @DrawableRes iconRes: Int,
        vararg actions: TouchAction,
    ) = TouchActionCategory(
        id = id,
        labelRes = labelRes,
        iconRes = iconRes,
        pages = actions.toList().chunked(TouchActionParser.MAX_ACTIONS_PER_PAGE),
    )

    private fun action(
        id: String,
        command: String,
        @StringRes labelRes: Int,
        keyHint: String = TouchActionKeyHints.forCommand(command),
    ) = TouchAction(
        id = id,
        command = command,
        labelRes = labelRes,
        iconRes = TouchActionIcons.forCommand(command),
        keyHint = keyHint,
    )

    private fun normalize(value: String): String = value
        .replace("\r\n", "\n")
        .trim()
        .lineSequence()
        .joinToString("\n") { line -> line.trim().replace(Regex("\\s+"), " ") }
}

object TouchActionKeyHints {
    fun forCommand(command: String): String {
        val characterCode = command.toIntOrNull()
        return when {
            command == "SS" -> "S"
            command == "S20s" -> "20s"
            command == "27" -> "ESC"
            command.startsWith("S#engrave") -> "#engrave"
            characterCode != null && characterCode in 1..26 ->
                "Ctrl-${('A'.code + characterCode - 1).toChar()}"
            command == "127" -> "Del"
            command.length <= 8 -> command
            else -> command.take(7) + "…"
        }
    }
}

object TouchActionIcons {
    /** Every built-in command gets its own distinct icon. */
    private val commandIcons = mapOf(
        // combat
        "a" to R.drawable.ic_touch_apply,
        "f" to R.drawable.ic_touch_fire,
        "t" to R.drawable.ic_touch_throw,
        "z" to R.drawable.ic_touch_zap,
        "Z" to R.drawable.ic_touch_cast,
        "4" to R.drawable.ic_touch_kick,
        "F" to R.drawable.ic_touch_combat,
        "w" to R.drawable.ic_touch_wield,
        "Q" to R.drawable.ic_touch_quiver,
        "x" to R.drawable.ic_touch_swap,
        "X" to R.drawable.ic_touch_two_weapon,
        "#enhance" to R.drawable.ic_touch_enhance,
        "#turn" to R.drawable.ic_touch_turn,
        "#monster" to R.drawable.ic_touch_monster,
        "#ride" to R.drawable.ic_touch_ride,
        // items
        "I" to R.drawable.ic_touch_inventory_type,
        "," to R.drawable.ic_touch_pickup,
        "d" to R.drawable.ic_touch_drop,
        "D" to R.drawable.ic_touch_drop_type,
        "e" to R.drawable.ic_touch_eat,
        "q" to R.drawable.ic_touch_quaff,
        "r" to R.drawable.ic_touch_read,
        "W" to R.drawable.ic_touch_wear,
        "T" to R.drawable.ic_touch_takeoff,
        "A" to R.drawable.ic_touch_takeoff_all,
        "P" to R.drawable.ic_touch_put_on,
        "R" to R.drawable.ic_touch_remove,
        "#loot" to R.drawable.ic_touch_loot,
        "#dip" to R.drawable.ic_touch_dip,
        "#invoke" to R.drawable.ic_touch_invoke,
        "#rub" to R.drawable.ic_touch_rub,
        "#tip" to R.drawable.ic_touch_tip,
        "p" to R.drawable.ic_touch_pay,
        "#adjust" to R.drawable.ic_touch_adjust,
        "C" to R.drawable.ic_touch_call,
        "@" to R.drawable.ic_touch_autopickup,
        "|" to R.drawable.ic_touch_perminv,
        "*" to R.drawable.ic_touch_see_all,
        ")" to R.drawable.ic_touch_see_weapon,
        "[" to R.drawable.ic_touch_see_armor,
        "=" to R.drawable.ic_touch_see_rings,
        "\"" to R.drawable.ic_touch_see_amulet,
        "(" to R.drawable.ic_touch_see_tools,
        "$" to R.drawable.ic_touch_gold,
        "+" to R.drawable.ic_touch_spells,
        // explore
        "o" to R.drawable.ic_touch_open,
        "c" to R.drawable.ic_touch_close,
        ":" to R.drawable.ic_touch_look,
        ";" to R.drawable.ic_touch_glance,
        "s" to R.drawable.ic_touch_search,
        "<" to R.drawable.ic_touch_up,
        ">" to R.drawable.ic_touch_down,
        "_" to R.drawable.ic_touch_travel,
        "31" to R.drawable.ic_touch_retravel,
        "G" to R.drawable.ic_touch_run,
        "g" to R.drawable.ic_touch_rush,
        "m" to R.drawable.ic_touch_no_pickup,
        "M" to R.drawable.ic_touch_no_pickup_far,
        "#chat" to R.drawable.ic_touch_chat,
        "#force" to R.drawable.ic_touch_force,
        "#jump" to R.drawable.ic_touch_jump,
        "20" to R.drawable.ic_touch_teleport,
        "#untrap" to R.drawable.ic_touch_untrap,
        "#offer" to R.drawable.ic_touch_offer,
        "#pray" to R.drawable.ic_touch_pray,
        "#sit" to R.drawable.ic_touch_sit,
        "E" to R.drawable.ic_touch_engrave,
        "#wipe" to R.drawable.ic_touch_wipe,
        "." to R.drawable.ic_touch_wait,
        "S20s" to R.drawable.ic_touch_wait,
        "#herecmdmenu" to R.drawable.ic_touch_here_menu,
        "#therecmdmenu" to R.drawable.ic_touch_there_menu,
        // info
        "?" to R.drawable.ic_touch_help,
        "/" to R.drawable.ic_touch_whatis,
        "&" to R.drawable.ic_touch_whatdoes,
        "15" to R.drawable.ic_touch_overview,
        "24" to R.drawable.ic_touch_attributes,
        "\\" to R.drawable.ic_touch_known,
        "`" to R.drawable.ic_touch_known_class,
        "v" to R.drawable.ic_touch_chronicle,
        "#conduct" to R.drawable.ic_touch_conduct,
        "#genocided" to R.drawable.ic_touch_genocided,
        "#vanquished" to R.drawable.ic_touch_vanquished,
        "#history" to R.drawable.ic_touch_history,
        "16" to R.drawable.ic_touch_prev_message,
        "#lookaround" to R.drawable.ic_touch_lookaround,
        "^" to R.drawable.ic_touch_show_trap,
        "127" to R.drawable.ic_touch_terrain,
        "#annotate" to R.drawable.ic_touch_annotate,
        "V" to R.drawable.ic_touch_version_short,
        "#version" to R.drawable.ic_touch_version,
        // system
        "SS" to R.drawable.ic_touch_save,
        "#quit" to R.drawable.ic_touch_quit,
        "O" to R.drawable.ic_touch_options,
        "#optionsfull" to R.drawable.ic_touch_options_full,
        "#" to R.drawable.ic_touch_extended,
        "#?" to R.drawable.ic_touch_extended_list,
        "1" to R.drawable.ic_touch_repeat,
        "18" to R.drawable.ic_touch_redraw,
        "#saveoptions" to R.drawable.ic_touch_save_options,
        "#toggle" to R.drawable.ic_touch_toggle,
        // dock
        "27" to R.drawable.ic_touch_cancel,
        "i" to R.drawable.ic_touch_inventory,
        "Keyboard" to R.drawable.ic_touch_keyboard,
        "Center" to R.drawable.ic_touch_center,
        "Setting" to R.drawable.ic_touch_settings,
    )

    /** All icons the visual editor offers, grouped: dock, combat, items, explore, info, system. */
    val selectableIcons: List<Pair<String, Int>> = listOf(
        "ic_touch_cancel" to R.drawable.ic_touch_cancel,
        "ic_touch_inventory" to R.drawable.ic_touch_inventory,
        "ic_touch_keyboard" to R.drawable.ic_touch_keyboard,
        "ic_touch_center" to R.drawable.ic_touch_center,
        "ic_touch_settings" to R.drawable.ic_touch_settings,
        "ic_touch_extended" to R.drawable.ic_touch_extended,
        "ic_touch_fallback" to R.drawable.ic_touch_fallback,
        "ic_touch_next" to R.drawable.ic_touch_next,
        "ic_touch_combat" to R.drawable.ic_touch_combat,
        "ic_touch_apply" to R.drawable.ic_touch_apply,
        "ic_touch_fire" to R.drawable.ic_touch_fire,
        "ic_touch_throw" to R.drawable.ic_touch_throw,
        "ic_touch_zap" to R.drawable.ic_touch_zap,
        "ic_touch_cast" to R.drawable.ic_touch_cast,
        "ic_touch_kick" to R.drawable.ic_touch_kick,
        "ic_touch_wield" to R.drawable.ic_touch_wield,
        "ic_touch_quiver" to R.drawable.ic_touch_quiver,
        "ic_touch_swap" to R.drawable.ic_touch_swap,
        "ic_touch_two_weapon" to R.drawable.ic_touch_two_weapon,
        "ic_touch_enhance" to R.drawable.ic_touch_enhance,
        "ic_touch_turn" to R.drawable.ic_touch_turn,
        "ic_touch_monster" to R.drawable.ic_touch_monster,
        "ic_touch_ride" to R.drawable.ic_touch_ride,
        "ic_touch_inventory_type" to R.drawable.ic_touch_inventory_type,
        "ic_touch_pickup" to R.drawable.ic_touch_pickup,
        "ic_touch_drop" to R.drawable.ic_touch_drop,
        "ic_touch_drop_type" to R.drawable.ic_touch_drop_type,
        "ic_touch_eat" to R.drawable.ic_touch_eat,
        "ic_touch_quaff" to R.drawable.ic_touch_quaff,
        "ic_touch_read" to R.drawable.ic_touch_read,
        "ic_touch_wear" to R.drawable.ic_touch_wear,
        "ic_touch_takeoff" to R.drawable.ic_touch_takeoff,
        "ic_touch_takeoff_all" to R.drawable.ic_touch_takeoff_all,
        "ic_touch_put_on" to R.drawable.ic_touch_put_on,
        "ic_touch_remove" to R.drawable.ic_touch_remove,
        "ic_touch_loot" to R.drawable.ic_touch_loot,
        "ic_touch_dip" to R.drawable.ic_touch_dip,
        "ic_touch_invoke" to R.drawable.ic_touch_invoke,
        "ic_touch_rub" to R.drawable.ic_touch_rub,
        "ic_touch_tip" to R.drawable.ic_touch_tip,
        "ic_touch_pay" to R.drawable.ic_touch_pay,
        "ic_touch_adjust" to R.drawable.ic_touch_adjust,
        "ic_touch_call" to R.drawable.ic_touch_call,
        "ic_touch_autopickup" to R.drawable.ic_touch_autopickup,
        "ic_touch_perminv" to R.drawable.ic_touch_perminv,
        "ic_touch_see_all" to R.drawable.ic_touch_see_all,
        "ic_touch_see_weapon" to R.drawable.ic_touch_see_weapon,
        "ic_touch_see_armor" to R.drawable.ic_touch_see_armor,
        "ic_touch_see_rings" to R.drawable.ic_touch_see_rings,
        "ic_touch_see_amulet" to R.drawable.ic_touch_see_amulet,
        "ic_touch_see_tools" to R.drawable.ic_touch_see_tools,
        "ic_touch_gold" to R.drawable.ic_touch_gold,
        "ic_touch_spells" to R.drawable.ic_touch_spells,
        "ic_touch_open" to R.drawable.ic_touch_open,
        "ic_touch_close" to R.drawable.ic_touch_close,
        "ic_touch_look" to R.drawable.ic_touch_look,
        "ic_touch_glance" to R.drawable.ic_touch_glance,
        "ic_touch_search" to R.drawable.ic_touch_search,
        "ic_touch_up" to R.drawable.ic_touch_up,
        "ic_touch_down" to R.drawable.ic_touch_down,
        "ic_touch_travel" to R.drawable.ic_touch_travel,
        "ic_touch_retravel" to R.drawable.ic_touch_retravel,
        "ic_touch_run" to R.drawable.ic_touch_run,
        "ic_touch_rush" to R.drawable.ic_touch_rush,
        "ic_touch_no_pickup" to R.drawable.ic_touch_no_pickup,
        "ic_touch_no_pickup_far" to R.drawable.ic_touch_no_pickup_far,
        "ic_touch_chat" to R.drawable.ic_touch_chat,
        "ic_touch_force" to R.drawable.ic_touch_force,
        "ic_touch_jump" to R.drawable.ic_touch_jump,
        "ic_touch_teleport" to R.drawable.ic_touch_teleport,
        "ic_touch_untrap" to R.drawable.ic_touch_untrap,
        "ic_touch_offer" to R.drawable.ic_touch_offer,
        "ic_touch_pray" to R.drawable.ic_touch_pray,
        "ic_touch_sit" to R.drawable.ic_touch_sit,
        "ic_touch_engrave" to R.drawable.ic_touch_engrave,
        "ic_touch_wipe" to R.drawable.ic_touch_wipe,
        "ic_touch_wait" to R.drawable.ic_touch_wait,
        "ic_touch_here_menu" to R.drawable.ic_touch_here_menu,
        "ic_touch_there_menu" to R.drawable.ic_touch_there_menu,
        "ic_touch_help" to R.drawable.ic_touch_help,
        "ic_touch_whatis" to R.drawable.ic_touch_whatis,
        "ic_touch_whatdoes" to R.drawable.ic_touch_whatdoes,
        "ic_touch_overview" to R.drawable.ic_touch_overview,
        "ic_touch_attributes" to R.drawable.ic_touch_attributes,
        "ic_touch_known" to R.drawable.ic_touch_known,
        "ic_touch_known_class" to R.drawable.ic_touch_known_class,
        "ic_touch_chronicle" to R.drawable.ic_touch_chronicle,
        "ic_touch_conduct" to R.drawable.ic_touch_conduct,
        "ic_touch_genocided" to R.drawable.ic_touch_genocided,
        "ic_touch_vanquished" to R.drawable.ic_touch_vanquished,
        "ic_touch_history" to R.drawable.ic_touch_history,
        "ic_touch_prev_message" to R.drawable.ic_touch_prev_message,
        "ic_touch_lookaround" to R.drawable.ic_touch_lookaround,
        "ic_touch_show_trap" to R.drawable.ic_touch_show_trap,
        "ic_touch_terrain" to R.drawable.ic_touch_terrain,
        "ic_touch_annotate" to R.drawable.ic_touch_annotate,
        "ic_touch_version_short" to R.drawable.ic_touch_version_short,
        "ic_touch_version" to R.drawable.ic_touch_version,
        "ic_touch_save" to R.drawable.ic_touch_save,
        "ic_touch_quit" to R.drawable.ic_touch_quit,
        "ic_touch_options" to R.drawable.ic_touch_options,
        "ic_touch_options_full" to R.drawable.ic_touch_options_full,
        "ic_touch_extended_list" to R.drawable.ic_touch_extended_list,
        "ic_touch_repeat" to R.drawable.ic_touch_repeat,
        "ic_touch_redraw" to R.drawable.ic_touch_redraw,
        "ic_touch_save_options" to R.drawable.ic_touch_save_options,
        "ic_touch_toggle" to R.drawable.ic_touch_toggle,
    )

    private val iconsByKey = selectableIcons.toMap()
    private val keysByRes = selectableIcons.associate { (key, resId) -> resId to key }

    @DrawableRes
    fun forCommand(command: String): Int = commandIcons[command]
        ?: when {
            command.startsWith("S#engrave") -> R.drawable.ic_touch_engrave
            else -> R.drawable.ic_touch_fallback
        }

    @DrawableRes
    fun resForKey(key: String): Int? = iconsByKey[key]

    fun keyForRes(@DrawableRes resId: Int): String? = keysByRes[resId]
}

/**
 * One row of the bottom command bar. [visibleLimit] caps how many buttons are
 * visible at once; the rest are revealed by panning the row horizontally.
 */
data class TouchCommandRow(
    val visibleLimit: Int = TouchCommandBar.DEFAULT_VISIBLE_LIMIT,
    val actions: List<TouchAction> = emptyList(),
) {
    val visibleCount: Int
        get() = minOf(actions.size, visibleLimit)
}

/**
 * Serializable model of the customizable bottom command bar. Rows are ordered
 * bottom-up: the first config line is the bottommost row (by default the
 * former system dock). Each line is `[ @limit ] button button ...` where a
 * button is `command`, `command|label` or `command|label|iconKey`.
 */
object TouchCommandBar {
    const val MAX_ROWS = 4
    const val MAX_BUTTONS_PER_ROW = 16
    const val DEFAULT_VISIBLE_LIMIT = 8
    const val MAX_VISIBLE_LIMIT = 12
    const val DOCK_CONFIG = "27 i Keyboard Center Setting"
    const val DEFAULT_CONFIG = "$DOCK_CONFIG\na f t z Z 4"

    fun parseRows(config: String?): List<TouchCommandRow> {
        val rows = mutableListOf<TouchCommandRow>()
        var customIndex = 0
        config.orEmpty().lineSequence().forEach { line ->
            val tokens = line.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (tokens.isEmpty()) return@forEach
            var limit = DEFAULT_VISIBLE_LIMIT
            var buttonTokens = tokens
            TouchActionParser.LIMIT_TOKEN.matchEntire(tokens.first())?.let { match ->
                limit = clampVisibleLimit(match.groupValues[1].toInt())
                buttonTokens = tokens.drop(1)
            }
            val actions = buttonTokens
                .mapNotNull { value -> TouchActionParser.parseAction(value, customIndex++) }
                .take(MAX_BUTTONS_PER_ROW)
            if (actions.isNotEmpty()) rows.add(TouchCommandRow(limit, actions))
        }
        return rows.take(MAX_ROWS)
    }

    fun serialize(rows: List<TouchCommandRow>): String =
        rows.filter { it.actions.isNotEmpty() }
            .joinToString("\n") { row ->
                val prefix =
                    if (row.visibleLimit != DEFAULT_VISIBLE_LIMIT) "@${row.visibleLimit} " else ""
                prefix + row.actions.joinToString(" ", transform = ::serializeAction)
            }

    /** Upgrades pre-dock-bar configs by prepending the dock as the new bottom row. */
    fun migrateLegacy(config: String?): String =
        if (config.isNullOrBlank()) DEFAULT_CONFIG else "$DOCK_CONFIG\n${config.trim()}"

    fun newAction(command: String): TouchAction {
        val builtIn = TouchActionCatalog.actionForCommand(command)
        return TouchAction(
            id = "bar_$command",
            command = command,
            labelRes = builtIn?.labelRes ?: 0,
            iconRes = builtIn?.iconRes ?: TouchActionIcons.forCommand(command),
            keyHint = builtIn?.keyHint ?: TouchActionKeyHints.forCommand(command),
        )
    }

    fun clampVisibleLimit(limit: Int): Int = limit.coerceIn(1, MAX_VISIBLE_LIMIT)

    fun canAddRow(rows: List<TouchCommandRow>): Boolean = rows.size < MAX_ROWS

    fun canAddButton(rows: List<TouchCommandRow>, rowIndex: Int): Boolean =
        rows.getOrNull(rowIndex)?.let { it.actions.size < MAX_BUTTONS_PER_ROW } ?: false

    fun addRow(rows: List<TouchCommandRow>): List<TouchCommandRow> =
        if (canAddRow(rows)) rows + TouchCommandRow() else rows

    fun removeRow(rows: List<TouchCommandRow>, rowIndex: Int): List<TouchCommandRow> =
        rows.filterIndexed { index, _ -> index != rowIndex }

    fun addButton(
        rows: List<TouchCommandRow>,
        rowIndex: Int,
        action: TouchAction,
    ): List<TouchCommandRow> =
        rows.mapIndexed { index, row ->
            if (index == rowIndex && row.actions.size < MAX_BUTTONS_PER_ROW)
                row.copy(actions = row.actions + action)
            else row
        }

    fun updateButton(
        rows: List<TouchCommandRow>,
        rowIndex: Int,
        buttonIndex: Int,
        action: TouchAction,
    ): List<TouchCommandRow> =
        rows.mapIndexed { index, row ->
            if (index != rowIndex) row
            else row.copy(
                actions = row.actions.mapIndexed { i, button ->
                    if (i == buttonIndex) action else button
                },
            )
        }

    /** Removing the last button of a row removes the row as well. */
    fun removeButton(
        rows: List<TouchCommandRow>,
        rowIndex: Int,
        buttonIndex: Int,
    ): List<TouchCommandRow> =
        rows.mapIndexedNotNull { index, row ->
            if (index != rowIndex) row
            else row.copy(actions = row.actions.filterIndexed { i, _ -> i != buttonIndex })
                .takeIf { it.actions.isNotEmpty() }
        }

    /**
     * Moves a button to [toRow]/[toIndex], where [toIndex] is the insertion
     * position among the target row's current buttons. Keeps an emptied source
     * row in place so the user can drag a button back into it.
     */
    fun moveButton(
        rows: List<TouchCommandRow>,
        fromRow: Int,
        fromIndex: Int,
        toRow: Int,
        toIndex: Int,
    ): List<TouchCommandRow> {
        val action = rows.getOrNull(fromRow)?.actions?.getOrNull(fromIndex) ?: return rows
        val target = rows.getOrNull(toRow) ?: return rows
        val sameRow = fromRow == toRow
        if (!sameRow && target.actions.size >= MAX_BUTTONS_PER_ROW) return rows
        val removed = rows.mapIndexed { index, row ->
            if (index == fromRow)
                row.copy(actions = row.actions.filterIndexed { i, _ -> i != fromIndex })
            else row
        }
        val insertAt = (if (sameRow && toIndex > fromIndex) toIndex - 1 else toIndex)
        return removed.mapIndexed { index, row ->
            if (index != toRow) row
            else row.copy(
                actions = row.actions.toMutableList().apply {
                    add(insertAt.coerceIn(0, size), action)
                },
            )
        }
    }

    fun setRowLimit(
        rows: List<TouchCommandRow>,
        rowIndex: Int,
        limit: Int,
    ): List<TouchCommandRow> =
        rows.mapIndexed { index, row ->
            if (index == rowIndex) row.copy(visibleLimit = clampVisibleLimit(limit)) else row
        }

    private fun serializeAction(action: TouchAction): String {
        val label = action.customLabel?.let(::sanitizeToken)?.takeUnless { it.isEmpty() }
        val defaultKey = TouchActionIcons.keyForRes(TouchActionIcons.forCommand(action.command))
        val iconKey = TouchActionIcons.keyForRes(action.iconRes)?.takeUnless { it == defaultKey }
        if (label == null && iconKey == null) return action.command
        return buildString {
            append(action.command)
            append("|").append(label.orEmpty())
            if (iconKey != null) append("|").append(iconKey)
        }
    }

    /** Config tokens are separated by `|` and whitespace, so labels can contain neither. */
    fun sanitizeToken(value: String): String = value.replace(Regex("[|\\s]"), "")
}

data class WheelPoint(val x: Float, val y: Float)

object TouchWheelGeometry {
    fun clampCenter(
        anchor: WheelPoint,
        width: Float,
        height: Float,
        extentX: Float,
        extentY: Float,
        safeLeft: Float = 0f,
        safeTop: Float = 0f,
        safeRight: Float = 0f,
        safeBottom: Float = 0f,
    ): WheelPoint {
        val minX = safeLeft + extentX
        val maxX = max(minX, width - safeRight - extentX)
        val minY = safeTop + extentY
        val maxY = max(minY, height - safeBottom - extentY)
        return WheelPoint(
            min(max(anchor.x, minX), maxX),
            min(max(anchor.y, minY), maxY),
        )
    }
}

object TouchWheelOpacity {
    const val MIN_PERCENT = 35
    const val MAX_PERCENT = 100
    const val DEFAULT_PERCENT = 95

    fun clamp(percent: Int): Int = percent.coerceIn(MIN_PERCENT, MAX_PERCENT)

    fun toAlpha(percent: Int): Int = (clamp(percent) / 100f * 255f).roundToInt()
}
