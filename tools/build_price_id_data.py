"""Build the price-identification data for the app from NetHack's objects.h.

The English side (names, appearances, costs, and the trade-message regexes) is
kept from the existing asset; the Chinese side is regenerated from objects.h so
it always matches the names the game itself prints.

Name ordering follows src/objnam.c, which appends the class word *after* the
object's own name in Chinese ("治愈药水", "挖掘魔杖", "变形戒指"), unlike English.
"""

import json
import re
import sys

MACROS = ("POTION|SCROLL|WAND|RING|AMULET|ARMOR|FOOD|SPELL|"
          "HELM|CLOAK|SHIELD|GLOVES|BOOTS|DRGN_ARMR")

# Macros whose first four args are (ename, edesc, name, desc).
FOUR_ARG = ("POTION", "SCROLL", "WAND", "RING", "AMULET", "ARMOR",
            "HELM", "CLOAK", "SHIELD", "GLOVES", "BOOTS", "SPELL")


def records(text):
    """Yield (macro, [raw args], line_no) for each object macro invocation."""
    lines = text.splitlines()
    i = 0
    while i < len(lines):
        m = re.match(r"\s*(" + MACROS + r")\((.*)$", lines[i])
        if not m:
            i += 1
            continue
        macro, buf, start = m.group(1), m.group(2), i + 1
        depth = 1 + buf.count("(") - buf.count(")")
        while depth > 0 and i + 1 < len(lines):
            i += 1
            buf += " " + lines[i].strip()
            depth = 1 + buf.count("(") - buf.count(")")
        args = split_args(buf)
        if not is_definition(args):
            yield macro, args, start
        i += 1


def is_definition(args):
    """Skip the macro template lines inside #define bodies; they have no literals."""
    return not any(a.startswith('"') for a in args)


def split_args(s):
    args, cur, depth, inq = [], "", 0, False
    for ch in s:
        if ch == '"':
            inq = not inq
            cur += ch
        elif not inq and ch == "(":
            depth += 1
            cur += ch
        elif not inq and ch == ")":
            if depth == 0:
                continue
            depth -= 1
            cur += ch
        elif not inq and ch == "," and depth == 0:
            args.append(cur.strip())
            cur = ""
        else:
            cur += ch
    if cur.strip():
        args.append(cur.strip())
    return args


def unquote(a):
    a = a.strip()
    if len(a) >= 2 and a[0] == '"' and a[-1] == '"':
        return a[1:-1]
    return a


# app object type -> (objects.h macro, Chinese class word, English name prefix)
TYPES = {
    "Potion": ("POTION", "药水", "potion of "),
    "Scroll": ("SCROLL", "卷轴", "scroll of "),
    "Spellbook": ("SPELL", "魔法书", "spellbook of "),
    "Ring": ("RING", "戒指", "ring of "),
    "Wand": ("WAND", "魔杖", "wand of "),
    "Amulet": ("AMULET", "", ""),
    "Boots": ("BOOTS", "", ""),
    "Cloak": ("CLOAK", "", ""),
    "Helm": ("HELM", "", ""),
    "Gloves": ("GLOVES", "", ""),
}

# Ring intrinsic / armor effect / school wording, per the translation standards
# in app/src/main/cpp/NetHack/doc/.  Values are English -> Chinese.
FIELD_TERMS = {
    "School": {
        "Attack": "攻击法术",
        "clerical": "神圣法术",
        "divination": "预测法术",
        "enchantment": "附魔法术",
        "escape": "逃脱法术",
        "healing": "治疗法术",
        "matter": "物质法术",
    },
    "Extrinsic granted": {
        "+n charisma": "魅力 +n",
        "+n constitution": "体质 +n",
        "+n damage bonus": "伤害加成 +n",
        "+n protection": "防护 +n",
        "+n strength": "力量 +n",
        "+n to-hit bonus": "命中加成 +n",
        "aggravate monster": "激怒怪物",
        "cold resistance": "寒抗",
        "conflict": "冲突",
        "fire resistance": "火抗",
        "free action": "自由行动",
        "hunger": "饥饿",
        "invisibility": "隐身",
        "levitation": "飘浮",
        "none": "无",
        "poison resistance": "毒抗",
        "polymorph control": "变形控制",
        "polymorphitis": "变形症",
        "protection from shape changers": "怪物现形",
        "regeneration": "再生",
        "searching": "搜索",
        "see invisible": "看见隐形",
        "shock resistance": "电抗",
        "slow digestion": "慢消化",
        "stealth": "潜行",
        "sustain ability": "维持能力",
        "teleport control": "传送控制",
        "teleportitis": "传送症",
        "warning": "警报",
    },
    "Material": {
        "Iron": "铁",
        "iron": "铁",
        "Leather": "皮革",
        "leather": "皮革",
        "cloth": "布料",
        "glass": "玻璃",
    },
    "Attack type": {
        "beam": "光束",
        "non-directional": "非定向",
        "ray": "射线",
        "varies": "不定",
    },
    "Starting charges": {
        "11–15": "11–15",
        "1–3": "1–3",
        "4–8": "4–8",
        "varies": "不定",
    },
    "Notes": {
        "90% generated cursed": "90% 生成时被诅咒",
        "90.5% generated cursed": "90.5% 生成时被诅咒",
        "cannot be put in a container": "不能放入容器",
        "chargeable": "可充能",
        "cursed if from a nazgul": "来自戒灵时被诅咒",
        "may be worn by monsters.": "怪物也可能穿戴。",
        "symbolized by %; never randomly generated": "以 % 为符号；不会随机生成",
        "wizard quest artifact": "巫师任务神器",
    },
    "When eaten": {
        "+2 protection (+2 ac, +2 mc)": "防护 +2（AC +2，MC +2）",
        "breathlessness": "无需呼吸",
        "changes gender": "改变性别",
        "inedible": "不可食用",
        "instantly choke": "立即窒息",
        "poison resistance": "毒抗",
        "restful sleep": "安稳睡眠",
        "revert to original form": "恢复原形",
        "telepathy": "心灵感应",
    },
    "Effect": {
        "Autocursing. Changes alignment of hero. No effect on a monster.":
            "自动诅咒。改变英雄阵营。对怪物无效。",
        "Autocursing. Sets Int and Wis to 6.": "自动诅咒。智力与感知固定为 6。",
        "Can be enchanted to +7.": "可附魔至 +7。",
        "Dexterity bonus (+/− enchantment)": "敏捷加成（+/− 附魔值）",
        "Displacement": "幻影",
        "Enchantment adds to Int and Wis. No penalty to spellcasting.":
            "附魔值加到智力与感知上，不影响施法。",
        "Fumbling, usually generated cursed": "笨拙，通常生成时被诅咒",
        "Grants telepathy.": "获得心灵感应。",
        "Grants warning.": "获得警报。",
        "MC1. Special benefits for wizards and penalties for non-wizards.":
            "MC1。巫师有额外好处，非巫师受罚。",
        "None": "无",
        "Strength 25, interfere with spellcasting": "力量 25，妨碍施法",
        "enhanced kicking": "强化踢击",
        "fumbling": "笨拙",
        "immunity from all grabbing and drowning attacks": "免疫所有抓取与溺水攻击",
        "invisibility": "隐身",
        "jumping": "跳跃",
        "levitation": "飘浮",
        "magic resistance": "魔法抗性",
        "overrides invisibility": "覆盖隐身效果",
        "poison and acid resistance": "毒抗与酸抗",
        "protection": "防护",
        "speed": "加速",
        "spellcasting bonus": "施法加成",
        "stealth": "潜行",
        "water walking": "水上行走",
    },
}


def strip_paren(name):
    return re.sub(r"\s*\([^)]*\)\s*$", "", name).strip()


# Objects that live outside the macros above (FOOD_CLASS specials, artifacts)
# and so are not picked up by extract_obj_names.records().
EXTRA_NAMES = {
    "meat ring": "肉环",
    "the eye of the aethiopica": "埃塞俄比亚之眼",
    "amulet of yendor": "岩德护身符",
    "cheap plastic imitation of the amulet of yendor": "岩德护身符的廉价塑料仿制品",
}

# The asset spells holy/unholy water with a blessing prefix, which the game builds
# from the "-holy water"/"-unholy water" variants of POT_WATER.
WATER_BLESSINGS = {
    "potion of holy water": "圣水",
    "potion of unholy water": "邪水",
}

# Names src/objnam.c does not build by simply appending the class word.
NAME_OVERRIDES = {
    "potion of water": "水",
    "wand of nothing": "无",
    "scroll of blank paper": "空白卷轴",
}

# Appearance words that appear in the asset but have no objects.h counterpart.
APPEARANCE_TERMS = {
    "clear": "清澈的",
    "brown": "棕色",
    "dark": "深色的",
    "magenta": "洋红色",
    "fizzy": "起泡的",
    "orange": "橙色",
    "stamped": "有邮戳的",
    "unlabeled": "无标签的",
    "red": "红色的",
    "velvet": "天鹅绒",
    "vellum": "牛皮纸",
    "dog eared": "卷边",
    "ragged": "粗糙的",
    "stained": "褪色的",
    "wooden": "木制",
    "topaz": "黄宝石",
    "black onyx": "黑玛瑙",
    "shiny": "闪耀的",
    "jade": "翡翠",
    "glass": "玻璃",
    "oak": "橡木",
    "iron": "铁制",
    "crystal": "水晶",
    "aluminum": "铝制",
    "steel": "钢铁",
    "square": "方形",
    "circular": "圆形",
    "spherical": "球形",
    "octagonal": "八角形",
    "hexagonal": "六角形",
    "triangular": "三角形",
}


def paren_suffix(name):
    m = re.search(r"\(([^)]*)\)\s*$", name)
    return m.group(1).strip() if m else ""


def chinese_name(entry, type_name):
    """Compose the name the way src/objnam.c prints it in Chinese."""
    macro, class_word, en_prefix = TYPES[type_name]
    zh = entry["zh"]
    if not class_word:
        return zh
    body = entry["en"]
    if body.lower().startswith(en_prefix):
        body = body[len(en_prefix):]
    if body.lower() == zh.lower():
        # name already contains the class word (e.g. "spellbook of blank paper")
        return zh
    return zh + class_word


def main(objects_h, asset_path):
    text = open(objects_h, encoding="utf-8").read()
    src = {}
    for macro, args, _line in records(text):
        vals = [unquote(a) for a in args]
        if macro in FOUR_ARG:
            en, en_desc, zh, zh_desc = vals[0], vals[1], vals[2], vals[3]
        elif macro == "DRGN_ARMR":
            en, zh = vals[0], vals[1]
            en_desc = zh_desc = ""
        elif macro == "FOOD":
            en, zh = vals[0], vals[1]
            en_desc = zh_desc = ""
        else:
            continue
        if not en or en == "ename":
            continue
        src.setdefault(macro, []).append(
            {"en": en, "zh": zh, "en_desc": en_desc, "zh_desc": zh_desc}
        )

    data = json.load(open(asset_path, encoding="utf-8"))
    missing = []

    for type_name, objs in data["objects"].items():
        macro, _class_word, _prefix = TYPES[type_name]
        by_en = {r["en"].lower(): r for r in src.get(macro, [])}
        for obj in objs:
            en = strip_paren(obj["Name"])
            key = en.lower()
            special_zh = WATER_BLESSINGS.get(key) or EXTRA_NAMES.get(key)
            entry = by_en.get(key)
            if entry is None and _prefix and key.startswith(_prefix.lower()):
                entry = by_en.get(key[len(_prefix):])
            if entry is None and special_zh:
                obj["NameZh"] = special_zh
                continue
            if entry is None:
                missing.append((type_name, obj["Name"]))
                continue
            obj["NameZh"] = NAME_OVERRIDES.get(key) or chinese_name(entry, type_name)

            # Appearance is left as the asset has it: for shuffled items the game
            # assigns one of a pool of descriptions per save, so a single value
            # from objects.h would be a guess.  Only the Chinese text is added.
            app = obj.get("Appearance", "").strip()
            if not app:
                pass
            elif app in ("random", "randomized", "(randomized)"):
                obj["AppearanceZh"] = "随机"
            elif entry["en_desc"] and entry["en_desc"] != "NoDes" and app == entry["en_desc"]:
                obj["AppearanceZh"] = entry["zh_desc"]
            else:
                obj["AppearanceZh"] = APPEARANCE_TERMS.get(app, app)

            for field, table in FIELD_TERMS.items():
                if field in obj and obj[field] in table:
                    obj[field + "Zh"] = table[obj[field]]

    if missing:
        for type_name, name in missing:
            print(f"missing: {type_name} {name!r}", file=sys.stderr)
        return 1

    with open(asset_path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1], sys.argv[2]))
