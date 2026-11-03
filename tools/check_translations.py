#!/usr/bin/env python3
"""Check locale coverage and Android format arguments without requiring the SDK."""

from collections import Counter
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
ANDROID = "{http://schemas.android.com/apk/res/android}"
FORMAT = re.compile(r"%(?:(\d+)\$)?([-#+ 0,(]*\d*(?:\.\d+)?)?([a-zA-Z%])")


def placeholders(value):
    """Ordering may change in translation; argument index and type may not."""
    result = Counter()
    for match in FORMAT.finditer(value):
        index, _, kind = match.groups()
        if kind in ("%", "n"):
            continue
        if index is None:
            raise ValueError(f"Use numbered format arguments: {value}")
        result[(index, kind)] += 1
    return result


def read_strings(path):
    strings = {}
    for element in ET.parse(path).getroot():
        if element.tag != "string" or element.get("translatable") == "false":
            continue
        name = element.attrib["name"]
        if name in strings:
            raise ValueError(f"{path}: duplicate key {name}")
        value = "".join(element.itertext())
        if not value.strip():
            raise ValueError(f"{path}: empty translation {name}")
        strings[name] = placeholders(value)
    return strings


def resource_folder(locale):
    """Android resource qualifiers: zh-CN lives in values-zh-rCN, Indonesian in the legacy values-in."""
    if locale in ("id", "in"):
        return "in"
    if "-" in locale:
        language, region = locale.split("-", 1)
        return f"{language}-r{region}"
    return locale


def check():
    base = read_strings(RES / "values/strings.xml")
    locales = [node.attrib[ANDROID + "name"] for node in
               ET.parse(RES / "xml/locales_config.xml").getroot()]
    if len(locales) != len(set(locales)) or "en" not in locales:
        raise ValueError("Locale config must declare English and have no duplicate locales")
    errors = []
    for locale in locales:
        if locale == "en":
            continue
        translated = read_strings(RES / f"values-{resource_folder(locale)}/strings.xml")
        for key in sorted(base.keys() - translated.keys()):
            errors.append(f"{locale}: missing {key}")
        for key in sorted(translated.keys() - base.keys()):
            errors.append(f"{locale}: unknown {key}")
        for key in sorted(base.keys() & translated.keys()):
            if base[key] != translated[key]:
                errors.append(f"{locale}: format arguments differ for {key}")
    if errors:
        raise ValueError("\n".join(errors))
    print(f"Translations OK: {len(base)} strings across {', '.join(locales)}")


if __name__ == "__main__":
    try:
        check()
    except (ValueError, OSError, ET.ParseError) as error:
        print(error, file=sys.stderr)
        sys.exit(1)
