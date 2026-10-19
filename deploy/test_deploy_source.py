#!/usr/bin/env python3
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

"""Offline deployment source replacement tests: no SSH or Docker daemon."""

import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


class SourceSnapshotTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.fixture = Path(self.temporary.name)
        self.project = self.fixture / "project"
        self.remote = self.fixture / "remote"
        self.project.mkdir()
        self.remote.mkdir()
        for name in ("server", "web", "deploy", "rmqctl"):
            (self.project / name).mkdir()
            (self.remote / name).mkdir()
            (self.project / name / "current-source").write_text("current\n")
            (self.remote / name / "obsolete-source").write_text("obsolete\n")
        (self.project / "rmqctl" / "cmd").mkdir()
        (self.remote / "rmqctl" / "cmd").mkdir()
        (self.project / "rmqctl" / "cmd" / "current.go").write_text("package cmd\n")
        for name in ("rmqctl/bin", "server/target", "web/node_modules", "web/dist"):
            (self.project / name).mkdir()
            (self.project / name / "local-build-output").write_text("excluded\n")
        for name in ("LICENSE", "NOTICE"):
            (self.project / name).write_text(f"current {name}\n")
        (self.project / "deploy" / ".env").write_text("LOCAL_CONFIG=excluded\n")
        (self.remote / ".env").write_text("ROOT_CONFIG=keep\n")
        (self.remote / "deploy" / ".env").write_text("REMOTE_CONFIG=keep\n")
        (self.remote / "persistent-data").write_text("keep\n")
        self.script = self.project / "deploy" / "deploy.sh"
        shutil.copyfile(Path(__file__).with_name("deploy.sh"), self.script)

    def run_upload(self, failure="", expected_status=0):
        result = subprocess.run(
            ["bash", "-c", r"""
set -euo pipefail
# Load unchanged production functions without invoking main. $0 points at
# the copied deploy.sh, retaining its real project-root calculation.
source <(sed '$d' "$0")
SRC_TAR="$FIXTURE/source.tar.gz"
REMOTE_PATH="$FIXTURE/remote"
run_remote() {
  # Isolate the simulated remote /tmp; no Docker daemon or SSH connection.
  local command="${1//\/tmp\/deploy-env-backup/$FIXTURE/remote-backup}"
  [[ "$command" == docker\ network* ]] && return 0
  bash -c '
    mv() { [[ "$FAILURE" != restore && "$FAILURE" != both ]] || return 73; command mv "$@"; }
    tar() { [[ "$FAILURE" != extract && "$FAILURE" != both ]] || return 74; command tar "$@"; }
    cp() { [[ "$FAILURE" != backup ]] || return 75; command cp "$@"; }
    mkdir() { [[ "$FAILURE" != restore-directory || "$*" != "-p deploy" ]] || return 76; command mkdir "$@"; }
  '"$command"
}
scp() { cp "$2" "${3#*:}"; }
package_source
upload_source
""", str(self.script)],
            env=dict(os.environ, FIXTURE=str(self.fixture), REMOTE_HOST="offline", FAILURE=failure),
            text=True, capture_output=True, check=False,
        )
        self.assertEqual(result.returncode, expected_status, result.stdout + result.stderr)
        return result

    def test_restore_failure_stops_source_upload(self):
        result = self.run_upload(failure="restore", expected_status=73)
        self.assertNotIn("源码就位", result.stdout)
        self.assertEqual((self.fixture / "remote-backup").read_text(), "REMOTE_CONFIG=keep\n")

    def test_restore_directory_failure_stops_source_upload(self):
        result = self.run_upload(failure="restore-directory", expected_status=76)
        self.assertNotIn("源码就位", result.stdout)
        self.assertEqual((self.fixture / "remote-backup").read_text(), "REMOTE_CONFIG=keep\n")

    def test_extraction_failure_restores_environment_before_returning(self):
        result = self.run_upload(failure="extract", expected_status=74)
        self.assertNotIn("源码就位", result.stdout)
        self.assertEqual((self.remote / "deploy" / ".env").read_text(), "REMOTE_CONFIG=keep\n")
        self.assertFalse((self.fixture / "remote-backup").exists())

    def test_extraction_error_takes_precedence_when_restore_also_fails(self):
        self.run_upload(failure="both", expected_status=74)
        self.assertEqual((self.fixture / "remote-backup").read_text(), "REMOTE_CONFIG=keep\n")

    def test_backup_failure_leaves_original_sources_and_environment(self):
        self.run_upload(failure="backup", expected_status=75)
        self.assertEqual((self.remote / "deploy" / ".env").read_text(), "REMOTE_CONFIG=keep\n")
        for name in ("server", "web", "deploy", "rmqctl"):
            self.assertTrue((self.remote / name / "obsolete-source").is_file())

    def test_redeploy_preserves_environment_and_persistent_files(self):
        self.run_upload()
        self.assertEqual((self.remote / ".env").read_text(), "ROOT_CONFIG=keep\n")
        self.assertEqual((self.remote / "deploy" / ".env").read_text(), "REMOTE_CONFIG=keep\n")
        self.assertEqual((self.remote / "persistent-data").read_text(), "keep\n")
        self.assertFalse((self.fixture / "remote-backup").exists())

    def test_package_keeps_sources_and_legal_files_without_build_outputs(self):
        self.run_upload()
        for name in ("server", "web", "deploy", "rmqctl"):
            with self.subTest(directory=name):
                self.assertEqual((self.remote / name / "current-source").read_text(), "current\n")
                if name != "rmqctl":
                    self.assertFalse((self.remote / name / "obsolete-source").exists())
        for name in ("rmqctl/bin", "server/target", "web/node_modules", "web/dist"):
            self.assertFalse((self.remote / name).exists())
        for name in ("LICENSE", "NOTICE"):
            self.assertEqual((self.remote / name).read_text(), f"current {name}\n")

    def test_upload_to_a_fresh_source_tree(self):
        for name in ("server", "web", "deploy", "rmqctl"):
            shutil.rmtree(self.remote / name)
        self.run_upload()
        self.assertTrue((self.remote / "rmqctl" / "cmd" / "current.go").is_file())
        self.assertFalse((self.remote / "deploy" / ".env").exists())
        self.assertEqual((self.remote / ".env").read_text(), "ROOT_CONFIG=keep\n")


if __name__ == "__main__":
    unittest.main()
