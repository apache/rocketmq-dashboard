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

"""Exercise the Dockerfile's real download RUN commands without network or Docker."""

import hashlib
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import unittest


DOCKERFILE = Path(__file__).resolve().parents[1] / "Dockerfile"
ARTIFACTS = (
    ("node-v20.19.2-linux-x64.tar.gz", "sha256", None),
    ("apache-maven-3.9.9-bin.tar.gz", "sha512", None),
    ("mysql-connector-j-9.7.0.jar", "sha256", "app/lib/mysql-connector-j-9.7.0.jar"),
)
TRUSTED = b"trusted fixture bytes; never executed or extracted\n"


class DockerDownloadsTest(unittest.TestCase):
    def run_download(self, artifact, algorithm, content=TRUSTED,
                     curl_status=0, checksum_failure=False, wrong_digest=False):
        source = DOCKERFILE.read_text()
        runs = re.findall(r"^RUN (.*)$", source.replace("\\\n", " "), re.MULTILINE)
        matches = [run for run in runs if artifact in run and "curl " in run]
        self.assertEqual(len(matches), 1, artifact)
        command = matches[0]
        # The expected digest must be a committed literal, never a fetched sidecar
        # or an overridable build argument. Only the fixture hash changes in tests.
        digest = re.search(r'echo "([a-f0-9]+)  [^"\n]+" \| ' + algorithm + r'sum -c -', command)
        self.assertIsNotNone(digest, artifact + " has no committed checksum check")
        self.assertEqual(len(digest[1]), hashlib.new(algorithm).digest_size * 2)
        self.assertEqual(command.count("curl "), 1)
        expected = hashlib.new(algorithm, TRUSTED).hexdigest()
        if wrong_digest:
            expected = "0" * len(expected)
        command = command.replace(digest[1], expected)

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            bin_dir = root / "bin"
            bin_dir.mkdir()
            (root / "tmp").mkdir()
            fixture = root / "fixture"
            fixture.write_bytes(content)
            log = root / "actions"
            # Map all Docker absolute paths into the test directory. curl, tar,
            # npm and ln are stand-ins; the checksum programs are the real ones.
            command = re.sub(r"/(?:tmp|opt|usr/local/bin|app/lib)(?=[/\s\"])",
                             lambda match: str(root) + match[0], command)
            curl = bin_dir / "curl"
            curl.write_text("#!" + sys.executable + "\n" + """
import os
from pathlib import Path
import sys
args = sys.argv[1:]
data = Path(os.environ['FIXTURE']).read_bytes()
if '-o' in args:
    Path(args[args.index('-o') + 1]).write_bytes(data)
else:
    sys.stdout.buffer.write(data)
sys.exit(int(os.environ['CURL_STATUS']))
""")
            curl.chmod(0o755)
            for name in ("tar", "npm", "ln"):
                stub = bin_dir / name
                stub.write_text('#!/bin/sh\nprintf "%s\\n" "' + name + '" >> "$ACTION_LOG"\n')
                stub.chmod(0o755)
            if checksum_failure:
                stub = bin_dir / (algorithm + "sum")
                stub.write_text("#!/bin/sh\nexit 2\n")
                stub.chmod(0o755)
            env = dict(os.environ, PATH=str(bin_dir) + os.pathsep + os.environ["PATH"],
                       FIXTURE=str(fixture), ACTION_LOG=str(log), CURL_STATUS=str(curl_status))
            result = subprocess.run(["/bin/sh", "-c", command], env=env,
                                    capture_output=True, text=True, timeout=10)
            actions = log.read_text().splitlines() if log.exists() else []
            installed = root / "app/lib/mysql-connector-j-9.7.0.jar"
            installed_bytes = installed.read_bytes() if installed.exists() else None
            return result, actions, installed_bytes

    def test_verified_downloads_are_used(self):
        for artifact, algorithm, jar in ARTIFACTS:
            with self.subTest(artifact=artifact):
                result, actions, installed = self.run_download(artifact, algorithm)
                self.assertEqual(result.returncode, 0, result.stderr)
                if jar:
                    self.assertEqual(installed, TRUSTED)
                else:
                    self.assertEqual(actions[0], "tar")
                    self.assertIn("ln", actions)

    def test_changed_or_empty_bytes_are_rejected_before_use(self):
        for artifact, algorithm, _ in ARTIFACTS:
            for content in (TRUSTED + b"changed", b""):
                with self.subTest(artifact=artifact, content=content):
                    result, actions, installed = self.run_download(artifact, algorithm, content)
                    self.assertNotEqual(result.returncode, 0)
                    self.assertEqual(actions, [])
                    self.assertIsNone(installed)

    def test_failed_download_cannot_use_even_matching_bytes(self):
        for artifact, algorithm, _ in ARTIFACTS:
            with self.subTest(artifact=artifact):
                result, actions, installed = self.run_download(artifact, algorithm, curl_status=22)
                self.assertNotEqual(result.returncode, 0)
                self.assertEqual(actions, [])
                self.assertIsNone(installed)

    def test_checksum_tool_failure_is_fatal(self):
        for artifact, algorithm, _ in ARTIFACTS:
            with self.subTest(artifact=artifact):
                result, actions, installed = self.run_download(artifact, algorithm, checksum_failure=True)
                self.assertNotEqual(result.returncode, 0)
                self.assertEqual(actions, [])
                self.assertIsNone(installed)

    def test_changed_expected_digest_is_rejected(self):
        for artifact, algorithm, _ in ARTIFACTS:
            with self.subTest(artifact=artifact):
                result, actions, installed = self.run_download(artifact, algorithm, wrong_digest=True)
                self.assertNotEqual(result.returncode, 0)
                self.assertEqual(actions, [])
                self.assertIsNone(installed)


if __name__ == "__main__":
    unittest.main()
