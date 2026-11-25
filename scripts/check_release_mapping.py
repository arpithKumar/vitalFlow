#!/usr/bin/env python3
"""Fail the build if R8 renamed or removed a data model that is read by reflection.

Gson (Gemini JSON) and Firestore (document mapping) find fields and accessors by NAME at runtime.
R8 cannot see that, so without keep rules it renames or removes them. Debug builds do not run R8,
so the bug only shows up in release. This check reads the release mapping and proves the models
survived. See app/proguard-rules.pro for the rules it verifies.

Usage:
    python3 scripts/check_release_mapping.py app/build/outputs/mapping/release/mapping.txt

Exit code 0 = every protected class and member kept its original name, 1 = problem found.
Standard library only.
"""

import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
JAVA_ROOT = REPO_ROOT / "app" / "src" / "main" / "java"

# Every class declared under these directories must survive R8 untouched.
PROTECTED_SOURCE_DIRS = ["com/vitalflow/app/data/entity"]

# Classes that live elsewhere but are also filled by Gson. Keep in sync with proguard-rules.pro.
EXTRA_PROTECTED_CLASSES = [
    "com.vitalflow.app.data.repository.GeminiRepository$FoodAnalysisResult",
    "com.vitalflow.app.data.repository.GeminiRepository$PrescriptionResult",
    "com.vitalflow.app.data.repository.GeminiRepository$VoiceAction",
]

# Third-party libraries that look up their own classes by name. Their classes must keep their names
# too. Drop an entry together with its -keep rule in proguard-rules.pro if the library is removed.
THIRD_PARTY_PREFIXES = (
    "org.apache.poi.",
    "org.apache.xmlbeans.",
    "org.openxmlformats.schemas.",
    "com.microsoft.schemas.",
)

# These prefixes must appear in the mapping, otherwise the check would pass without checking anything
# (the library was removed, or R8 dropped it).
REQUIRED_THIRD_PARTY_PREFIXES = ("org.apache.poi.", "org.apache.xmlbeans.")

# Fewer expected classes than this means the source layout moved and the check would pass vacuously.
MIN_EXPECTED_CLASSES = 10

_COMMENTS_AND_STRINGS = re.compile(
    r'//[^\n]*|/\*.*?\*/|"(?:\\.|[^"\\\n])*"|\'(?:\\.|[^\'\\\n])*\'', re.DOTALL
)
_TOKEN = re.compile(r"\b(?:class|interface|enum|record)\s+([A-Za-z_]\w*)|[{}]")
_PACKAGE = re.compile(r"^\s*package\s+([\w.]+)\s*;", re.MULTILINE)


def declared_classes(source: str):
    """Binary names (Outer$Inner) of every type declared in one Java source file."""
    code = _COMMENTS_AND_STRINGS.sub(" ", source)
    package = _PACKAGE.search(code)
    prefix = package.group(1) + "." if package else ""
    stack, pending, found = [], None, []
    for token in _TOKEN.finditer(code):
        text = token.group(0)
        if text == "{":
            stack.append(pending)
            pending = None
        elif text == "}":
            if stack:
                stack.pop()
        else:
            outer = [name for name in stack if name]
            name = token.group(1)
            found.append(prefix + "$".join(outer + [name]))
            pending = name
    return found


def expected_classes():
    expected = set(EXTRA_PROTECTED_CLASSES)
    for directory in PROTECTED_SOURCE_DIRS:
        for path in sorted((JAVA_ROOT / directory).rglob("*.java")):
            expected.update(declared_classes(path.read_text(encoding="utf-8")))
    return expected


def parse_mapping(text: str):
    """{original class: (obfuscated class, [(original member, obfuscated member)])}"""
    classes, current = {}, None
    for line in text.splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#"):
            continue
        if not line[0].isspace():
            original, _, obfuscated = line.partition(" -> ")
            current = (obfuscated.rstrip(":").strip(), [])
            classes[original.strip()] = current
        elif current is not None and " -> " in line:
            left, _, obfuscated = stripped.rpartition(" -> ")
            left = re.sub(r"^\d+:\d+:", "", left)
            name = left.split("(")[0].split()[-1]
            current[1].append((name, obfuscated.strip()))
    return classes


def _is_generated(class_name: str) -> bool:
    """Lambda and similar classes that D8/R8 generates after shrinking; nothing looks them up by name."""
    return "$$ExternalSynthetic" in class_name or "$$Lambda" in class_name


def check(mapping_text: str, expected, protected_prefixes, third_party_prefixes=(), required_prefixes=()):
    """Return a list of human-readable problems (empty list = all good)."""
    classes = parse_mapping(mapping_text)
    problems = []
    for name in sorted(expected):
        if name not in classes:
            problems.append(f"REMOVED  {name}  (R8 dropped the class)")
        elif classes[name][0] != name:
            problems.append(f"RENAMED  {name} -> {classes[name][0]}")
    for name, (obfuscated, _) in sorted(classes.items()):
        if name.startswith(third_party_prefixes) and not _is_generated(name) and obfuscated != name:
            problems.append(f"RENAMED  {name} -> {obfuscated}")
    for prefix in required_prefixes:
        if not any(name.startswith(prefix) for name in classes):
            problems.append(f"MISSING  no class under {prefix} in the mapping "
                            "(library removed? then drop it from this script and from proguard-rules.pro)")
    for name, (obfuscated, members) in sorted(classes.items()):
        if not (name in expected or name.startswith(protected_prefixes + tuple(third_party_prefixes))):
            continue
        if _is_generated(name):
            continue
        for original, renamed in members:
            # "-$$Nest$..." (private access between nested classes) and "$r8$lambda$..." (lambda
            # helpers) are generated by the compiler and R8. Nothing reflects on them, so renaming
            # them is fine.
            if original.startswith(("-$$", "$r8$")):
                continue
            # A name with a dot is a call that R8 inlined into this method ("pkg.Other.method"), not a
            # member of this class.
            if "." in original:
                continue
            if original != renamed:
                problems.append(f"RENAMED  {name}.{original} -> {renamed}")
    return problems


def main(argv):
    if len(argv) != 2:
        print(__doc__)
        return 2
    mapping_path = Path(argv[1])
    if not mapping_path.is_file():
        print(f"mapping file not found: {mapping_path} (run ./gradlew assembleRelease first)")
        return 2

    expected = expected_classes()
    if len(expected) < MIN_EXPECTED_CLASSES:
        print(f"Found only {len(expected)} model classes in the sources; "
              f"expected at least {MIN_EXPECTED_CLASSES}. Did the package move? Update this script.")
        return 1

    prefixes = tuple(
        "com.vitalflow.app." + d.split("com/vitalflow/app/", 1)[1].replace("/", ".") + "."
        for d in PROTECTED_SOURCE_DIRS
    )
    mapping_text = mapping_path.read_text(encoding="utf-8", errors="replace")
    problems = check(mapping_text, expected, prefixes, THIRD_PARTY_PREFIXES, REQUIRED_THIRD_PARTY_PREFIXES)
    if problems:
        print(f"R8 broke {len(problems)} class(es) that are looked up by name in the release build:")
        for problem in problems[:40]:
            print("  " + problem)
        if len(problems) > 40:
            print(f"  ... and {len(problems) - 40} more")
        print("\nFix: add or correct the -keep rules in app/proguard-rules.pro "
              "(models belong in data/entity).")
        return 1

    third_party = sum(1 for name in parse_mapping(mapping_text) if name.startswith(THIRD_PARTY_PREFIXES))
    print(f"OK: {len(expected)} model classes and {third_party} third-party classes "
          "(POI, XMLBeans) kept their names and members in the release build.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
