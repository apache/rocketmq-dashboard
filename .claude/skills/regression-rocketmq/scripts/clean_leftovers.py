#!/usr/bin/env python3
"""Delete resources left behind by earlier e2e / regression runs.

A crashed run leaves alert rules (same evaluation conditions then make the next create
return 409), topics and groups behind. Run this before every regression round.
"""

import os
import sys
from pathlib import Path

import requests
from dotenv import load_dotenv

E2E_DIR = Path(os.getenv("E2E_DIR", Path(__file__).resolve().parents[5] / "rocketmq-studio-docs" / "e2e-tests"))
load_dotenv(E2E_DIR / ".env")

BASE = os.getenv("STUDIO_URL", "http://47.98.43.243:6789").rstrip("/")
INSTANCE = os.getenv("OPEN_SOURCE_INSTANCE_ID", "open-source-local")
PREFIXES = ("e2e_rule_", "e2e_del_", "e2e_channel_", "E2ETest", "E2EDlq")

session = requests.Session()
login = session.post(f"{BASE}/api/auth/login", json={
    "username": os.getenv("STUDIO_USERNAME", ""),
    "password": os.getenv("STUDIO_PASSWORD", ""),
}, timeout=30).json()
if login.get("code") != 200:
    sys.exit(f"login failed: {login.get('message')}")


def listing(path, params=None):
    body = session.get(f"{BASE}{path}", params=params or {}, timeout=30).json()
    return body.get("data") or []


removed = []

rules = listing("/api/alert-rules")
for rule in rules if isinstance(rules, list) else []:
    name = str(rule.get("name", ""))
    if name.startswith(PREFIXES):
        body = session.post(f"{BASE}/api/alert-rules/delete", json={"id": rule.get("id")}, timeout=30).json()
        removed.append(f"alert-rule {name} -> {body.get('code')}")

for path, key, payload_key in (("/api/topics", "name", "name"), ("/api/groups", "name", "name")):
    items = listing(path, {"instanceId": INSTANCE})
    records = items.get("records") if isinstance(items, dict) else items
    for item in records or []:
        name = str(item.get(key, ""))
        if name.startswith(PREFIXES):
            body = session.post(f"{BASE}{path}/delete", json={
                "instanceId": INSTANCE, payload_key: name,
            }, timeout=30).json()
            removed.append(f"{path.strip('/')} {name} -> {body.get('code')}")

print("\n".join(removed) if removed else "no leftovers")
