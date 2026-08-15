#!/usr/bin/env python3
"""Import the selected game-icons.net SVGs as Android VectorDrawables.

Also regenerates app/src/main/assets/licenses/game-icons-net.txt so the
attribution always matches the imported set. Run from anywhere:

    python app/scripts/import-game-icons.py
"""

from __future__ import annotations

import base64
from datetime import date
import json
from pathlib import Path
import subprocess
import sys
import xml.etree.ElementTree as ElementTree


# local drawable name -> path inside the game-icons/icons repository.
ICONS = {
    # dock / generic
    "ic_touch_cancel": "sbed/cancel.svg",
    "ic_touch_inventory": "delapouite/backpack.svg",
    "ic_touch_keyboard": "delapouite/keyboard.svg",
    "ic_touch_center": "delapouite/crosshair.svg",
    "ic_touch_settings": "lorc/gears.svg",
    "ic_touch_extended": "skoll/console-controller.svg",
    "ic_touch_fallback": "lorc/cycle.svg",
    "ic_touch_next": "delapouite/next-button.svg",
    # combat
    "ic_touch_combat": "lorc/crossed-swords.svg",
    "ic_touch_apply": "delapouite/swiss-army-knife.svg",
    "ic_touch_fire": "lorc/target-shot.svg",
    "ic_touch_throw": "lorc/thrown-daggers.svg",
    "ic_touch_zap": "lorc/focused-lightning.svg",
    "ic_touch_cast": "lorc/magic-swirl.svg",
    "ic_touch_kick": "delapouite/high-kick.svg",
    "ic_touch_wield": "lorc/broadsword.svg",
    "ic_touch_quiver": "delapouite/quiver.svg",
    "ic_touch_swap": "delapouite/switch-weapon.svg",
    "ic_touch_two_weapon": "lorc/dervish-swords.svg",
    "ic_touch_enhance": "lorc/muscle-up.svg",
    "ic_touch_turn": "lorc/holy-symbol.svg",
    "ic_touch_monster": "lorc/monster-grasp.svg",
    "ic_touch_ride": "lorc/horse-head.svg",
    # items
    "ic_touch_inventory_type": "delapouite/hand-bag.svg",
    "ic_touch_pickup": "lorc/grab.svg",
    "ic_touch_drop": "lorc/drop.svg",
    "ic_touch_drop_type": "delapouite/bolt-drop.svg",
    "ic_touch_eat": "delapouite/eating.svg",
    "ic_touch_quaff": "lorc/drink-me.svg",
    "ic_touch_read": "lorc/scroll-unfurled.svg",
    "ic_touch_wear": "lorc/mail-shirt.svg",
    "ic_touch_takeoff": "delapouite/armor-downgrade.svg",
    "ic_touch_takeoff_all": "delapouite/clothes.svg",
    "ic_touch_put_on": "delapouite/polo-shirt.svg",
    "ic_touch_remove": "delapouite/gloves.svg",
    "ic_touch_loot": "delapouite/chest.svg",
    "ic_touch_dip": "delapouite/paint-bucket.svg",
    "ic_touch_invoke": "delapouite/glowing-artifact.svg",
    "ic_touch_rub": "delapouite/bed-lamp.svg",
    "ic_touch_tip": "delapouite/pouring-pot.svg",
    "ic_touch_pay": "delapouite/pay-money.svg",
    "ic_touch_adjust": "delapouite/pencil-ruler.svg",
    "ic_touch_call": "delapouite/price-tag.svg",
    "ic_touch_autopickup": "delapouite/robot-grab.svg",
    "ic_touch_perminv": "delapouite/bookshelf.svg",
    "ic_touch_see_all": "delapouite/all-seeing-eye.svg",
    "ic_touch_see_weapon": "lorc/shining-sword.svg",
    "ic_touch_see_armor": "delapouite/chest-armor.svg",
    "ic_touch_see_rings": "delapouite/ring.svg",
    "ic_touch_see_amulet": "delapouite/heart-necklace.svg",
    "ic_touch_see_tools": "delapouite/3d-hammer.svg",
    "ic_touch_gold": "delapouite/gold-stack.svg",
    "ic_touch_spells": "lorc/book-aura.svg",
    # explore
    "ic_touch_open": "delapouite/entry-door.svg",
    "ic_touch_close": "delapouite/closed-doors.svg",
    "ic_touch_look": "delapouite/look-at.svg",
    "ic_touch_glance": "delapouite/binoculars.svg",
    "ic_touch_search": "lorc/magnifying-glass.svg",
    "ic_touch_up": "delapouite/stairs.svg",
    "ic_touch_down": "delapouite/3d-stairs.svg",
    "ic_touch_travel": "lorc/compass.svg",
    "ic_touch_retravel": "delapouite/path-distance.svg",
    "ic_touch_run": "delapouite/running-shoe.svg",
    "ic_touch_rush": "darkzaitzev/running-ninja.svg",
    "ic_touch_no_pickup": "delapouite/egyptian-walk.svg",
    "ic_touch_no_pickup_far": "delapouite/crossroad.svg",
    "ic_touch_chat": "delapouite/discussion.svg",
    "ic_touch_force": "delapouite/lockpicks.svg",
    "ic_touch_jump": "delapouite/jump-across.svg",
    "ic_touch_teleport": "lorc/portal.svg",
    "ic_touch_untrap": "lorc/grease-trap.svg",
    "ic_touch_offer": "lorc/holy-grail.svg",
    "ic_touch_pray": "lorc/prayer.svg",
    "ic_touch_sit": "caro-asercion/armchair.svg",
    "ic_touch_engrave": "lorc/quill-ink.svg",
    "ic_touch_wipe": "delapouite/towel.svg",
    "ic_touch_wait": "lorc/hourglass.svg",
    "ic_touch_here_menu": "delapouite/position-marker.svg",
    "ic_touch_there_menu": "delapouite/flag-objective.svg",
    # info
    "ic_touch_help": "sbed/help.svg",
    "ic_touch_whatis": "delapouite/eye-target.svg",
    "ic_touch_whatdoes": "delapouite/info.svg",
    "ic_touch_overview": "delapouite/atlas.svg",
    "ic_touch_attributes": "delapouite/person.svg",
    "ic_touch_known": "lorc/open-book.svg",
    "ic_touch_known_class": "delapouite/book-pile.svg",
    "ic_touch_chronicle": "delapouite/diploma.svg",
    "ic_touch_conduct": "lorc/medal.svg",
    "ic_touch_genocided": "lorc/skull-crossed-bones.svg",
    "ic_touch_vanquished": "delapouite/laurels-trophy.svg",
    "ic_touch_history": "delapouite/backward-time.svg",
    "ic_touch_prev_message": "delapouite/chat-bubble.svg",
    "ic_touch_lookaround": "delapouite/hunter-eyes.svg",
    "ic_touch_show_trap": "lorc/mantrap.svg",
    "ic_touch_terrain": "delapouite/path-tile.svg",
    "ic_touch_annotate": "delapouite/pencil.svg",
    "ic_touch_version_short": "lorc/bookmark.svg",
    "ic_touch_version": "delapouite/notebook.svg",
    # system
    "ic_touch_save": "delapouite/save.svg",
    "ic_touch_quit": "delapouite/exit-door.svg",
    "ic_touch_options": "darkzaitzev/big-gear.svg",
    "ic_touch_options_full": "delapouite/monkey-wrench.svg",
    "ic_touch_extended_list": "delapouite/checklist.svg",
    "ic_touch_repeat": "delapouite/clockwise-rotation.svg",
    "ic_touch_redraw": "delapouite/pencil-brush.svg",
    "ic_touch_save_options": "delapouite/cloud-upload.svg",
    "ic_touch_toggle": "delapouite/toggles.svg",
}

AUTHORS = {
    "delapouite": "Delapouite (http://delapouite.com)",
    "lorc": "Lorc (http://lorcblog.blogspot.com)",
    "sbed": "Sbed (http://opengameart.org/content/95-game-icons)",
    "skoll": "Skoll",
    "darkzaitzev": "DarkZaitzev",
    "caro-asercion": "Caro Asercion",
}

SVG_NAMESPACE = {"svg": "http://www.w3.org/2000/svg"}
REPOSITORY = "game-icons/icons"


def load_svg(path: str) -> bytes:
    response = subprocess.run(
        ["gh", "api", f"repos/{REPOSITORY}/contents/{path}"],
        check=True,
        capture_output=True,
        text=True,
    )
    return base64.b64decode(json.loads(response.stdout)["content"])


def foreground_paths(svg: bytes) -> list[str]:
    root = ElementTree.fromstring(svg)
    paths = []
    for node in root.findall("svg:path", SVG_NAMESPACE):
        path_data = node.attrib.get("d")
        fill = node.attrib.get("fill", "").lower()
        if path_data and fill in {"#fff", "#ffffff", "white"}:
            paths.append(path_data)
    if not paths:
        raise ValueError("SVG does not contain an explicit white foreground path")
    return paths


def vector_drawable(paths: list[str]) -> str:
    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        '    android:width="24dp"',
        '    android:height="24dp"',
        '    android:viewportWidth="512"',
        '    android:viewportHeight="512">',
    ]
    for path_data in paths:
        lines.extend(
            [
                "    <path",
                '        android:fillColor="#FFFFFFFF"',
                f'        android:pathData="{path_data}" />',
            ]
        )
    lines.append("</vector>")
    return "\n".join(lines) + "\n"


def icon_title(source_path: str) -> str:
    slug = Path(source_path).stem
    return slug.replace("-", " ").capitalize()


def license_text() -> str:
    lines = [
        "game-icons.net touch control icons",
        "==================================",
        "",
        "Source repository: https://github.com/game-icons/icons",
        "Project website: https://game-icons.net/",
        "License: Creative Commons Attribution 3.0 (CC BY 3.0)",
        "License URL: https://creativecommons.org/licenses/by/3.0/",
        f"Imported/updated: {date.today().isoformat()}",
        "",
        'The upstream license requests the notice "Icons made by {author}" in derivative works.',
        "All listed icons were converted from SVG to Android VectorDrawable XML. The black",
        "background path was removed, the white foreground path was retained, and Android",
        "runtime tinting is applied. No other artistic changes were made.",
        "",
    ]
    by_author: dict[str, list[tuple[str, str]]] = {}
    for local_name, source_path in ICONS.items():
        author = source_path.split("/", 1)[0]
        by_author.setdefault(author, []).append((local_name, source_path))
    for author, display in AUTHORS.items():
        entries = by_author.get(author)
        if not entries:
            continue
        lines.append(f"Icons made by {display}:")
        for local_name, source_path in entries:
            page_path = source_path.removesuffix(".svg") + ".html"
            lines.append(f"- {local_name}.xml: {icon_title(source_path)}")
            lines.append(f"  https://game-icons.net/1x1/{page_path}")
            lines.append(f"  Source: https://github.com/game-icons/icons/blob/master/{source_path}")
        lines.append("")
    lines.extend(
        [
            "Upstream license text is available at:",
            "https://github.com/game-icons/icons/blob/master/license.txt",
            "",
        ]
    )
    return "\n".join(lines)


def main() -> None:
    app_dir = Path(__file__).resolve().parents[1]
    drawable_dir = app_dir / "src/main/res/drawable"
    failures = []
    for local_name, source_path in ICONS.items():
        try:
            paths = foreground_paths(load_svg(source_path))
        except Exception as error:  # report every failure, then exit non-zero
            failures.append(f"{local_name} <- {source_path}: {error}")
            continue
        target = drawable_dir / f"{local_name}.xml"
        target.write_text(vector_drawable(paths), encoding="utf-8")
        print(f"{target.name} <- {source_path}")
    if failures:
        print("\nFailed imports:", file=sys.stderr)
        for failure in failures:
            print(f"  {failure}", file=sys.stderr)
        sys.exit(1)
    license_file = app_dir / "src/main/assets/licenses/game-icons-net.txt"
    license_file.write_text(license_text(), encoding="utf-8")
    print(f"{license_file.name} regenerated")


if __name__ == "__main__":
    main()
