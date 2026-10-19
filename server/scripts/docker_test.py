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

import os
from pathlib import Path
import subprocess
import tempfile
import unittest


class DockerNodeTest(unittest.TestCase):
    def run_node_install(self, architecture):
        docker = (Path(__file__).resolve().parents[1] / "Dockerfile").read_text()
        instructions = docker.replace("\\\n", "").splitlines()
        install = next(line[4:] for line in instructions
                       if line.startswith("RUN ") and "node-v20.19.2-linux-" in line)
        # Execute the Dockerfile's real shell logic without downloading packages or touching /opt.
        stubs = """
curl() { printf '%s\\n' "$@" > "$TEST_DIR/download"; }
tar() { cat >/dev/null; }
ln() { printf '%s\\n' "$@" >> "$TEST_DIR/links"; }
npm() { :; }
"""
        with tempfile.TemporaryDirectory() as directory:
            result = subprocess.run(["sh", "-eu", "-c", stubs + install],
                                    env={**os.environ, "TARGETARCH": architecture, "TEST_DIR": directory},
                                    capture_output=True, text=True)
            download = Path(directory, "download")
            links = Path(directory, "links")
            return result, download.read_text() if download.exists() else "", \
                links.read_text() if links.exists() else ""

    def amd64UsesX64NodeTest(self):
        result, download, links = self.run_node_install("amd64")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("node-v20.19.2-linux-x64.tar.gz", download)
        for executable in ["node", "npm", "npx", "claude", "qodercli"]:
            self.assertIn(f"/opt/node-v20.19.2-linux-x64/bin/{executable}\n", links)

    def arm64UsesArm64NodeTest(self):
        result, download, links = self.run_node_install("arm64")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("node-v20.19.2-linux-arm64.tar.gz", download)
        for executable in ["node", "npm", "npx", "claude", "qodercli"]:
            self.assertIn(f"/opt/node-v20.19.2-linux-arm64/bin/{executable}\n", links)

    def unsupportedArchitectureFailsBeforeDownloadTest(self):
        result, download, links = self.run_node_install("unsupported")
        self.assertNotEqual(0, result.returncode)
        self.assertEqual("", download)
        self.assertEqual("", links)


def load_tests(_loader, _tests, _pattern):
    return unittest.TestSuite(DockerNodeTest(name) for name in DockerNodeTest.__dict__ if name.endswith("Test"))


if __name__ == "__main__":
    unittest.main(verbosity=2)
