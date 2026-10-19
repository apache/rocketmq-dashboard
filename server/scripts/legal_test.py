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

import io
import json
from pathlib import Path
import tempfile
import unittest
import zipfile
import xml.etree.ElementTree as ET

import legal


class LegalTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(dir=legal.SERVER / "scripts")
        self.addCleanup(self.temporary.cleanup)
        self.directory = Path(self.temporary.name)

    def jar(self, name="fixture-1.0.jar", license_present=True):
        dependency = io.BytesIO()
        with zipfile.ZipFile(dependency, "w") as archive:
            if license_present:
                archive.writestr("META-INF/LICENSE", legal.read(legal.SERVER / "LICENSE"))
            archive.writestr("META-INF/NOTICE", b"fixture required notice\n")
            archive.writestr("META-INF/COPYRIGHT", b"fixture copyright statement\n")
            archive.writestr("fixture/Notice.class", b"not a legal text")
        jar = self.directory / "app.jar"
        with zipfile.ZipFile(jar, "w") as archive:
            archive.writestr("BOOT-INF/lib/" + name, dependency.getvalue(), compress_type=zipfile.ZIP_STORED)
            archive.writestr("BOOT-INF/classes/fixture.txt", b"application")
        return jar

    def jarRoundTripAndCopyrightTest(self):
        jar = self.jar()
        legal.package_jar(jar, self.directory / "legal")
        legal.check_jar(jar)
        first = jar.read_bytes()
        legal.package_jar(jar, self.directory / "legal")
        self.assertEqual(first, jar.read_bytes())
        with zipfile.ZipFile(jar) as archive:
            self.assertIn(b"fixture required notice", archive.read("META-INF/NOTICE"))
            self.assertEqual(b"fixture copyright statement\n", archive.read("META-INF/legal/licenses/fixture-1.0.jar/META-INF/COPYRIGHT.txt"))
            self.assertEqual(zipfile.ZIP_STORED, archive.getinfo("BOOT-INF/lib/fixture-1.0.jar").compress_type)
            self.assertNotIn("META-INF/legal/licenses/fixture-1.0.jar/fixture/Notice.class.txt", archive.namelist())

    def missingLicenseFailsWithoutReplacingJarTest(self):
        jar = self.jar(license_present=False)
        original = jar.read_bytes()
        with self.assertRaisesRegex(ValueError, "missing complete upstream"):
            legal.package_jar(jar, self.directory / "legal")
        self.assertEqual(original, jar.read_bytes())
        self.assertTrue((self.directory / "legal/manifest.json").is_file())

    def mysqlExceptionRequiresReviewTest(self):
        jar = self.jar("mysql-connector-j-9.7.0.jar")
        with self.assertRaisesRegex(ValueError, "Universal FOSS Exception"):
            legal.package_jar(jar, self.directory / "legal")

    def changedPayloadFailsTest(self):
        jar = self.jar()
        legal.package_jar(jar, self.directory / "legal")
        with zipfile.ZipFile(jar, "a") as archive:
            archive.writestr("BOOT-INF/classes/extra.txt", b"new payload")
        with self.assertRaisesRegex(ValueError, "do not match the actual dependencies"):
            legal.check_jar(jar)

    def defaultAiCliRequiresReviewTest(self):
        for name in ["@anthropic-ai/claude-code", "@qoder-ai/qodercli"]:
            directory = self.directory / "npm" / name
            directory.mkdir(parents=True)
            # A test fixture must not be treated as having obtained redistribution rights for a real product just because it declares MIT.
            (directory / "package.json").write_text(json.dumps({"name": name, "version": "fixture", "license": "MIT"}))
            (directory / "LICENSE").write_bytes(legal.read(legal.SERVER / "LICENSE"))
        files, manifest = legal.collect_npm(self.directory / "npm", legal.SERVER / "LICENSE")
        self.assertIn("licenses/node/LICENSE.txt", files)
        with self.assertRaisesRegex(ValueError, "AI CLI"):
            legal.require_complete(manifest)

    def lifecycleAndDockerStaticContractTest(self):
        pom = ET.parse(legal.SERVER / "pom.xml")
        ns = {"m": "http://maven.apache.org/POM/4.0.0"}
        plugins = pom.findall("m:build/m:plugins/m:plugin", ns)
        names = [plugin.findtext("m:artifactId", namespaces=ns) for plugin in plugins]
        self.assertLess(names.index("spring-boot-maven-plugin"), names.index("maven-antrun-plugin"))
        gate = plugins[names.index("maven-antrun-plugin")]
        self.assertEqual("package", gate.findtext("m:executions/m:execution/m:phase", namespaces=ns))
        self.assertEqual("true", gate.find("m:executions/m:execution/m:configuration/m:target/m:exec", ns).get("failonerror"))
        docker = (legal.SERVER / "Dockerfile").read_text()
        self.assertEqual(2, docker.count("legal.py check-jar /app/app.jar"))
        self.assertIn("npm install -g @anthropic-ai/claude-code @qoder-ai/qodercli", docker)
        self.assertIn("COPY --from=rmqctl-build /out/legal", docker)


def load_tests(_loader, _tests, _pattern):
    return unittest.TestSuite(LegalTest(name) for name in LegalTest.__dict__ if name.endswith("Test"))


if __name__ == "__main__":
    unittest.main(verbosity=2)
