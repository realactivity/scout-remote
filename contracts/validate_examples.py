#!/usr/bin/env python3
"""Validate every canonical example against its JSON Schema (draft 2020-12).

This enforces the strict-writer half of the compatibility policy (spec §15.2):
the golden payloads in examples/ must always validate against the current
schemas. Run from anywhere; requires `pip install jsonschema`.
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

from jsonschema import Draft202012Validator, FormatChecker

CONTRACTS_DIR = Path(__file__).resolve().parent

EXAMPLE_TO_SCHEMA = {
    "protocol.json": "protocol.schema.json",
    "status.json": "agent-status.schema.json",
    "command.message.json": "command.schema.json",
    "response.completed.json": "response.schema.json",
    "conversation-event.response.json": "conversation-event.schema.json",
    "activity-event.work.json": "activity-event.schema.json",
    "usage-daily.json": "usage-daily.schema.json",
}


def main() -> int:
    examples = sorted(p.name for p in (CONTRACTS_DIR / "examples").glob("*.json"))
    unmapped = [name for name in examples if name not in EXAMPLE_TO_SCHEMA]
    if unmapped:
        print(f"FAIL: examples without a schema mapping: {unmapped}")
        return 1

    failures = 0
    for example_name, schema_name in EXAMPLE_TO_SCHEMA.items():
        schema = json.loads((CONTRACTS_DIR / "schemas" / schema_name).read_text())
        Draft202012Validator.check_schema(schema)
        instance = json.loads((CONTRACTS_DIR / "examples" / example_name).read_text())
        validator = Draft202012Validator(schema, format_checker=FormatChecker())
        errors = sorted(validator.iter_errors(instance), key=lambda e: list(e.path))
        status = "OK" if not errors else "FAIL"
        print(f"{example_name} vs {schema_name}: {status}")
        for error in errors:
            failures += 1
            print(f"    {'/'.join(map(str, error.path)) or '<root>'}: {error.message}")

    if failures:
        print(f"{failures} validation error(s).")
        return 1
    print("All examples validate against their schemas.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
