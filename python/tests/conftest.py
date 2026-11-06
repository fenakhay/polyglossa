"""What the tests share: ``spec/expectations.json``, what every package must answer."""

from __future__ import annotations

import json
import pathlib
from typing import Any

SPEC_PATH = pathlib.Path(__file__).resolve().parents[2] / "spec" / "expectations.json"
SPEC: dict[str, Any] = json.loads(SPEC_PATH.read_text("utf-8"))
