#!/usr/bin/env python3
"""Import the selected game-icons.net SVGs as Android VectorDrawables."""

from __future__ import annotations

import base64
import json
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ElementTree


ICONS = {
    "ic_touch_cancel": "sbed/cancel.svg",
    "ic_touch_inventory": "delapouite/backpack.svg",
    "ic_touch_keyboard": "delapouite/keyboard.svg",
    "ic_touch_center": "delapouite/crosshair.svg",
    "ic_touch_settings": "lorc/gears.svg",
    "ic_touch_save": "delapouite/save.svg",
    "ic_touch_quit": "delapouite/exit-door.svg",
    "ic_touch_engrave": "lorc/quill-ink.svg",
    "ic_touch_wait": "lorc/hourglass.svg",
    "ic_touch_extended": "skoll/console-controller.svg",
    "ic_touch_drop": "lorc/drop.svg",
    "ic_touch_eat": "delapouite/eating.svg",
    "ic_touch_look": "delapouite/look-at.svg",
    "ic_touch_pickup": "lorc/grab.svg",
    "ic_touch_fallback": "lorc/cycle.svg",
    "ic_touch_next": "delapouite/next-button.svg",
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


def main() -> None:
    drawable_dir = Path(__file__).resolve().parents[1] / "src/main/res/drawable"
    for local_name, source_path in ICONS.items():
        paths = foreground_paths(load_svg(source_path))
        target = drawable_dir / f"{local_name}.xml"
        target.write_text(vector_drawable(paths), encoding="utf-8")
        print(f"{target.name} <- {source_path}")


if __name__ == "__main__":
    main()
