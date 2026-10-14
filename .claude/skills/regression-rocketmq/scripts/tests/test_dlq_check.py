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

"""Exercise the DLQ script's verdict and cleanup without network or Broker access."""

import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import textwrap
import unittest


SCRIPT = Path(__file__).resolve().parents[1] / "dlq_check.sh"
COMPLETE = {
    "matched": 3,
    "resent": 3,
    "failed": 0,
    "outcome": "SUCCESS",
    "scanIncomplete": False,
    "failedQueueCount": 0,
}


class DLQCheckTest(unittest.TestCase):
    def run_script(self, result, *, progress=True, optimize=False):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            binary = root / "bin"
            fake = root / "fake"
            e2e = root / "e2e"
            for path in (binary, fake, e2e):
                path.mkdir()
            (e2e / ".env").touch()
            (binary / "python3").symlink_to(sys.executable)
            (fake / "dotenv.py").write_text("def load_dotenv(*args): return False\n")
            (fake / "requests.py").write_text(textwrap.dedent("""\
                import json
                import os
                from pathlib import Path

                class Response:
                    def __init__(self, body):
                        self.body = body
                    def json(self):
                        return self.body

                class Session:
                    def post(self, url, json=None, timeout=None):
                        prefix = 'http://127.0.0.1:9/api/'
                        assert url.startswith(prefix), url
                        path = url[len(prefix):]
                        with open(os.environ['HTTP_LOG'], 'a') as log:
                            log.write(path + '\\n')
                        if path == 'auth/login':
                            return Response({'code': 200})
                        if path == 'dlq/resend':
                            return Response({'code': 200, 'data': __import__('json').loads(os.environ['RESEND_RESULT'])})
                        if path == 'topics/send':
                            return Response({'code': 200, 'data': {'msgId': 'synthetic-' + json['key']}})
                        if path in ('topics/create', 'groups/create', 'groups/delete', 'topics/delete'):
                            return Response({'code': 200})
                        raise RuntimeError('Unexpected fixture request: ' + path)

                    def get(self, url, params=None, timeout=None):
                        assert url == 'http://127.0.0.1:9/api/dlq', url
                        return Response({'code': 200, 'data': {'items': [
                            {'groupName': os.environ['GROUP'], 'messageCount': 3, 'retryCount': 2}
                        ]}})
                """))
            (binary / "ssh").write_text(textwrap.dedent("""\
                #!/usr/bin/env python3
                import os
                from pathlib import Path
                import sys

                if 'fixture-target' not in sys.argv[1:]:
                    raise RuntimeError('Unexpected SSH target')
                command = sys.argv[-1]
                root = Path(os.environ['FIXTURE_ROOT'])
                with (root / 'ssh.log').open('a') as log:
                    log.write(command + '\\n')
                if 'grep -c fail-consume' in command:
                    counter = root / 'counter'
                    count = 2 if counter.exists() and os.environ['CONSUMER_PROGRESS'] == '1' else 1
                    counter.touch()
                    print(count)
                elif 'docker logs' in command:
                    print('fail-consume synthetic')
                elif 'docker run' in command:
                    print('synthetic-container')
                elif 'mkdir -p' not in command and 'docker rm -f' not in command:
                    raise RuntimeError('Unexpected SSH command')
                """))
            (binary / "scp").write_text("#!/bin/sh\nexit 0\n")
            (binary / "sleep").write_text("#!/bin/sh\nexit 0\n")
            for path in (binary / "ssh", binary / "scp", binary / "sleep"):
                path.chmod(0o755)
            env = dict(
                os.environ,
                PATH=str(binary) + os.pathsep + os.environ.get("PATH", os.defpath),
                PYTHONPATH=str(fake),
                PYTHONNOUSERSITE="1",
                PYTHONOPTIMIZE="1" if optimize else "0",
                FIXTURE_ROOT=str(root),
                HTTP_LOG=str(root / "http.log"),
                CONSUMER_PROGRESS="1" if progress else "0",
                E2E_DIR=str(e2e),
                SSH_TARGET="fixture-target",
                STUDIO_URL="http://127.0.0.1:9",
                STUDIO_USERNAME="synthetic",
                STUDIO_PASSWORD="synthetic",
                OPEN_SOURCE_INSTANCE_ID="synthetic-instance",
                RESEND_RESULT=json.dumps(result),
            )
            completed = subprocess.run(
                ["bash", str(SCRIPT)], env=env, text=True,
                capture_output=True, timeout=15,
            )
            output = completed.stdout + completed.stderr
            # Cleanup must still run on rejection, in group-before-topic order.
            self.assertEqual(
                (root / "http.log").read_text().splitlines()[-2:],
                ["groups/delete", "topics/delete"], output,
            )
            self.assertIn("docker rm -f", (root / "ssh.log").read_text(), output)
            return completed.returncode, output

    def assert_rejected(self, result, **options):
        code, output = self.run_script(result, **options)
        self.assertNotEqual(code, 0, output)
        self.assertNotIn("PASS 死信链路", output)

    def test_complete_resend_passes(self):
        code, output = self.run_script(COMPLETE)
        self.assertEqual(code, 0, output)
        self.assertIn("PASS 死信链路", output)

    def test_partial_resend_fails(self):
        self.assert_rejected(dict(COMPLETE, resent=1, failed=2, outcome="PARTIAL"))

    def test_incomplete_scan_fails(self):
        self.assert_rejected(dict(COMPLETE, outcome="PARTIAL", scanIncomplete=True, failedQueueCount=1))

    def test_short_success_fails(self):
        self.assert_rejected(dict(COMPLETE, matched=2, resent=2))

    def test_missing_result_fails(self):
        self.assert_rejected(None)

    def test_complete_without_reconsumption_fails(self):
        self.assert_rejected(COMPLETE, progress=False)

    def test_partial_resend_with_optimized_python_fails(self):
        self.assert_rejected(dict(COMPLETE, resent=1, failed=2, outcome="PARTIAL"), optimize=True)


if __name__ == "__main__":
    unittest.main()
