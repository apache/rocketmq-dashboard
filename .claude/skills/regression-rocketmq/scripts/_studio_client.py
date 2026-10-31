#!/usr/bin/env python3
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

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

BASE = os.getenv("STUDIO_URL", "").rstrip("/")
if not BASE:
    sys.exit("STUDIO_URL is not set; export STUDIO_URL=http://<host>:6789 first")
INSTANCE = os.getenv("OPEN_SOURCE_INSTANCE_ID", "open-source-local")

session = requests.Session()
login = session.post(f"{BASE}/api/auth/login", json={
    "username": os.getenv("STUDIO_USERNAME", ""),
    "password": os.getenv("STUDIO_PASSWORD", ""),
}, timeout=30).json()
if login.get("code") != 200:
    sys.exit(f"login failed: {login.get('message')}")

exec(sys.stdin.read())
