# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements. See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License. You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Run with python3 -m unittest discover -s deploy -p test_deploy_paths.py."""

import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tarfile
import tempfile
import unittest


@unittest.skipUnless(shutil.which("bash"), "requires bash")
class DeployPathsTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.remote = self.root / "RocketMQ Studio's $literal"
        self.remote.mkdir()
        (self.remote / ".env").write_text("", encoding="utf-8")
        self.log = self.root / "commands.jsonl"
        self.bin = self.root / "bin"
        self.bin.mkdir()
        self.write_stub("ssh", """
import subprocess
import sys
sys.exit(subprocess.run(["bash", "-c", sys.argv[-1]]).returncode)
""")
        self.write_stub("docker", """
import json
import os
import sys
with open(os.environ["DEPLOY_COMMAND_LOG"], "a", encoding="utf-8") as log:
    log.write(json.dumps({"cwd": os.getcwd(), "args": sys.argv[1:]}) + "\\n")
""")
        # scp before OpenSSH 9 hands the remote target to the remote shell, so resolve it
        # through bash the way the ssh stub does: an unquoted path breaks here exactly as it
        # would on the target host.
        self.write_stub("scp", """
import json
import os
import subprocess
import sys
target = sys.argv[-1].partition(":")[2]
resolved = subprocess.run(["bash", "-c", "cd " + target + " && pwd"],
                          capture_output=True, text=True)
with open(os.environ["DEPLOY_COMMAND_LOG"], "a", encoding="utf-8") as log:
    log.write(json.dumps({"cwd": resolved.stdout.strip(), "args": sys.argv[1:]}) + "\\n")
sys.exit(resolved.returncode)
""")
        source = Path(__file__).with_name("deploy.sh").read_text(encoding="utf-8")
        self.assertTrue(source.rstrip().endswith("\nmain"))
        source = source.rstrip().removesuffix("\nmain") + (
            '\nSRC_TAR="$DEPLOY_TEST_ARCHIVE"\n"$DEPLOY_TEST_FUNCTION"\n'
        )
        self.script = self.root / "checkout" / "deploy" / "deploy.sh"
        self.script.parent.mkdir(parents=True)
        self.script.write_text(source, encoding="utf-8")
        self.env = os.environ.copy()
        self.env.update({
            "PATH": str(self.bin) + os.pathsep + self.env["PATH"],
            "REMOTE_HOST": "fixture.invalid",
            "REMOTE_PATH": str(self.remote),
            "DEPLOY_COMMAND_LOG": str(self.log),
            "DEPLOY_TEST_ARCHIVE": str(self.root / "source.tar.gz"),
            "MAVEN_IMAGE": "maven-fixture",
            "MAVEN_ARGS": "",
            "LEGAL_GATE": "1",
        })

    def write_stub(self, name, body):
        path = self.bin / name
        path.write_text(f"#!{sys.executable}\n{body}", encoding="utf-8")
        path.chmod(0o755)

    def run_function(self, function):
        result = subprocess.run(
            ["bash", str(self.script)],
            env={**self.env, "DEPLOY_TEST_FUNCTION": function},
            text=True, capture_output=True, timeout=15,
        )
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        return [json.loads(line) for line in self.log.read_text().splitlines()]

    def testPrerequisitesAcceptLiteralRemotePathTest(self):
        self.run_function("check_prereqs")

    def testSourceUploadQuotesScpRemotePathTest(self):
        # The remote side untars `basename $SRC_TAR` out of the installation directory, so
        # park a real archive there for this run.
        payload = self.root / "payload.txt"
        payload.write_text("", encoding="utf-8")
        archive = self.remote / Path(self.env["DEPLOY_TEST_ARCHIVE"]).name
        with tarfile.open(archive, "w:gz") as tar:
            tar.add(payload, arcname=payload.name)
        commands = self.run_function("upload_source")
        upload = next(command for command in commands if command["args"][0] == "-q")
        self.assertEqual(upload["cwd"], str(self.remote))

    def testServerBuildPreservesBindMountPathTest(self):
        commands = self.run_function("build_server")
        build = next(command for command in commands if command["args"][0] == "run")
        self.assertEqual(build["cwd"], str(self.remote))
        self.assertIn(str(self.remote / "server") + ":/app", build["args"])

    def testWebBuildAndComposePreserveRemotePathTest(self):
        for function in ("build_web", "start_services"):
            with self.subTest(function=function):
                commands = self.run_function(function)
                self.assertEqual(commands[-1]["cwd"], str(self.remote))


if __name__ == "__main__":
    unittest.main()
