#!/usr/bin/env python3
"""Login to Studio and exec the payload received on stdin with `session` / BASE / INSTANCE in scope."""

import os
import sys

import requests
from dotenv import load_dotenv

E2E_DIR = os.getenv(
    "E2E_DIR",
    os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "..", "..",
                 "..", "..", "project", "rocketmq-studio-docs", "e2e-tests"),
)
load_dotenv(os.path.join(E2E_DIR, ".env"))

BASE = os.getenv("STUDIO_URL", "http://47.98.43.243:6789").rstrip("/")
INSTANCE = os.getenv("OPEN_SOURCE_INSTANCE_ID", "open-source-local")

session = requests.Session()
login = session.post(f"{BASE}/api/auth/login", json={
    "username": os.getenv("STUDIO_USERNAME", ""),
    "password": os.getenv("STUDIO_PASSWORD", ""),
}, timeout=30).json()
if login.get("code") != 200:
    sys.exit(f"login failed: {login.get('message')}")

exec(sys.stdin.read())
