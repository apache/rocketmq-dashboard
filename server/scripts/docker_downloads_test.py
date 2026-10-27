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
# Different fixture bytes make accidental cross-architecture checksum selection fail.
NODE_PINS = {
    "amd64": ("x64", "eec2c7b9c6ac72e42885a42edfc0503c0e4ee455f855c4a17a6cbcf026656dd5"),
    "arm64": ("arm64", "24c3090d4e8c3667cd57482263291ca4f562c2e0773d5e618a0c6ba32d21b39f"),
}
ARTIFACTS = (
    ("node-v20.19.2-linux-x64.tar.gz", "sha256", "amd64"),
    ("node-v20.19.2-linux-arm64.tar.gz", "sha256", "arm64"),
    ("apache-maven-3.9.9-bin.tar.gz", "sha512", None),
    ("mysql-connector-j-9.7.0.jar", "sha256", None),
)


def trusted_bytes(artifact):
    return ("trusted fixture for " + artifact + "; never executed or extracted\n").encode()


class DockerDownloadsTest(unittest.TestCase):
    def run_download(self, artifact, algorithm, architecture=None, content=None,
                     curl_status=0, checksum_failure=False, wrong_digest=False):
        source = DOCKERFILE.read_text()
        runs = re.findall(r"^RUN (.*)$", source.replace("\\\n", " "), re.MULTILINE)
        marker = "node-v20.19.2-linux-" if architecture is not None else artifact
        matches = [run for run in runs if marker in run and "curl " in run]
        self.assertEqual(len(matches), 1, artifact)
        command = matches[0]
        self.assertEqual(command.count("curl "), 1)
        if architecture is not None:
            self.assertIn("ARG TARGETARCH", source)
            self.assertNotRegex(source, r"(?m)^(?:ARG|ENV) NODE_SHA256\b")
            self.assertIn('echo "${NODE_SHA256}  /tmp/node.tar.gz" | sha256sum -c -', command)
            # Assert each target has a committed pin, then substitute only fixture
            # digests. The actual shell case selects URL, checksum and symlinks.
            for target, (node_arch, pin) in NODE_PINS.items():
                self.assertRegex(command, re.escape(target) + r"\)\s+NODE_ARCH=" +
                                 re.escape(node_arch) + r";\s+NODE_SHA256=" + pin + r"\s+;;")
                fixture_artifact = "node-v20.19.2-linux-" + node_arch + ".tar.gz"
                expected = hashlib.sha256(trusted_bytes(fixture_artifact)).hexdigest()
                if wrong_digest and target == architecture:
                    expected = "0" * len(expected)
                command = command.replace(pin, expected)
        else:
            digest = re.search(r'echo "([a-f0-9]+)  [^"\n]+" \| ' + algorithm + r'sum -c -', command)
            self.assertIsNotNone(digest, artifact + " has no committed checksum check")
            self.assertEqual(len(digest[1]), hashlib.new(algorithm).digest_size * 2)
            expected = hashlib.new(algorithm, trusted_bytes(artifact)).hexdigest()
            if wrong_digest:
                expected = "0" * len(expected)
            command = command.replace(digest[1], expected)

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            bin_dir = root / "bin"
            bin_dir.mkdir()
            (root / "tmp").mkdir()
            fixture = root / "fixture"
            fixture.write_bytes(trusted_bytes(artifact) if content is None else content)
            log = root / "actions"
            downloads = root / "downloads"
            command = re.sub(r"/(?:tmp|opt|usr/local/bin|app/lib)(?=[/\s\"])",
                             lambda match: str(root) + match[0], command)
            curl = bin_dir / "curl"
            curl.write_text("#!" + sys.executable + "\n" + """
import os
from pathlib import Path
import sys
args = sys.argv[1:]
url = args[-1]
Path(os.environ['DOWNLOAD_LOG']).write_text(url)
if not url.endswith('/' + os.environ['EXPECTED_ARTIFACT']):
    sys.exit(91)
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
                stub.write_text('#!/bin/sh\nprintf "%s\\n" "' + name + ' $*" >> "$ACTION_LOG"\n')
                stub.chmod(0o755)
            if checksum_failure:
                stub = bin_dir / (algorithm + "sum")
                stub.write_text("#!/bin/sh\nexit 2\n")
                stub.chmod(0o755)
            env = dict(os.environ, PATH=str(bin_dir) + os.pathsep + os.environ["PATH"],
                       FIXTURE=str(fixture), ACTION_LOG=str(log), CURL_STATUS=str(curl_status),
                       DOWNLOAD_LOG=str(downloads), EXPECTED_ARTIFACT=artifact,
                       TARGETARCH="amd64" if architecture is None else architecture, NODE_SHA256="untrusted environment pin")
            result = subprocess.run(["/bin/sh", "-c", command], env=env,
                                    capture_output=True, text=True, timeout=10)
            actions = log.read_text().splitlines() if log.exists() else []
            installed = root / "app/lib/mysql-connector-j-9.7.0.jar"
            installed_bytes = installed.read_bytes() if installed.exists() else None
            download = downloads.read_text() if downloads.exists() else None
            return result, actions, installed_bytes, download

    def test_verified_downloads_are_used(self):
        for artifact, algorithm, architecture in ARTIFACTS:
            with self.subTest(artifact=artifact):
                result, actions, installed, download = self.run_download(artifact, algorithm, architecture)
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertTrue(download.endswith("/" + artifact))
                if artifact.endswith(".jar"):
                    self.assertEqual(installed, trusted_bytes(artifact))
                else:
                    self.assertTrue(actions[0].startswith("tar "))
                    self.assertTrue(any(action.startswith("ln ") for action in actions))
                if architecture:
                    node_dir = artifact.removesuffix(".tar.gz")
                    for executable in ("node", "npm", "npx", "claude", "qodercli"):
                        self.assertTrue(any(action.startswith("ln ") and
                                            "/" + node_dir + "/bin/" + executable + " " in action
                                            for action in actions), actions)

    def test_changed_or_empty_bytes_are_rejected_before_use(self):
        for artifact, algorithm, architecture in ARTIFACTS:
            for content in (trusted_bytes(artifact) + b"changed", b""):
                with self.subTest(artifact=artifact, content=content):
                    result, actions, installed, _ = self.run_download(artifact, algorithm, architecture, content)
                    self.assertNotEqual(result.returncode, 0)
                    self.assertEqual(actions, [])
                    self.assertIsNone(installed)

    def test_failed_download_cannot_use_even_matching_bytes(self):
        for artifact, algorithm, architecture in ARTIFACTS:
            with self.subTest(artifact=artifact):
                result, actions, installed, _ = self.run_download(artifact, algorithm, architecture, curl_status=22)
                self.assertNotEqual(result.returncode, 0)
                self.assertEqual(actions, [])
                self.assertIsNone(installed)

    def test_checksum_tool_failure_is_fatal(self):
        for artifact, algorithm, architecture in ARTIFACTS:
            with self.subTest(artifact=artifact):
                result, actions, installed, _ = self.run_download(artifact, algorithm, architecture, checksum_failure=True)
                self.assertNotEqual(result.returncode, 0)
                self.assertEqual(actions, [])
                self.assertIsNone(installed)

    def test_changed_expected_digest_is_rejected(self):
        for artifact, algorithm, architecture in ARTIFACTS:
            with self.subTest(artifact=artifact):
                result, actions, installed, _ = self.run_download(artifact, algorithm, architecture, wrong_digest=True)
                self.assertNotEqual(result.returncode, 0)
                self.assertEqual(actions, [])
                self.assertIsNone(installed)

    def test_other_architecture_bytes_are_rejected(self):
        for architecture, (node_arch, _) in NODE_PINS.items():
            other = "arm64" if node_arch == "x64" else "x64"
            artifact = "node-v20.19.2-linux-" + node_arch + ".tar.gz"
            with self.subTest(architecture=architecture):
                result, actions, installed, _ = self.run_download(
                    artifact, "sha256", architecture,
                    trusted_bytes("node-v20.19.2-linux-" + other + ".tar.gz"))
                self.assertNotEqual(result.returncode, 0)
                self.assertEqual(actions, [])
                self.assertIsNone(installed)

    def test_unsupported_architecture_fails_before_download(self):
        for architecture in ("s390x", "", "AMD64"):
            with self.subTest(architecture=architecture):
                result, actions, installed, download = self.run_download(
                    "node-v20.19.2-linux-x64.tar.gz", "sha256", architecture)
                self.assertNotEqual(result.returncode, 0)
                self.assertIn("Unsupported Node.js architecture", result.stderr)
                self.assertEqual(actions, [])
                self.assertIsNone(installed)
                self.assertIsNone(download)


if __name__ == "__main__":
    unittest.main()
