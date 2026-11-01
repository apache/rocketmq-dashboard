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

"""Preserve the license texts of the actually distributed files using only the standard library; complete materials do not imply redistribution rights have been granted."""
import argparse
import hashlib
import io
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile

SERVER = Path(__file__).resolve().parents[1]
LEGAL_NAME = re.compile(r"^(licen[sc]e|notice|copying|copyright|patents|authors|dependencies|third[-_]party)([._-].*)?$", re.I)
LICENSE_NAME = re.compile(r"^(licen[sc]e|copying)([._-].*)?$", re.I)
NOTICE_NAME = re.compile(r"^notice([._-].*)?$", re.I)
PERMISSIVE = {"MIT", "Apache-2.0", "BSD-2-Clause", "BSD-3-Clause", "ISC", "0BSD"}
DETERMINATIONS = "scripts/legal-determinations.json"
COLLECTED = "legal/licenses"
# ASF Category B: weak copyleft. Allowed in a binary distribution only with a recorded decision,
# never silently — so an entry has to carry `review` or the gate stays red.
CATEGORY_B = {"EPL-1.0", "EPL-2.0", "MPL-1.1", "MPL-2.0", "CDDL-1.0", "CDDL-1.1", "LGPL-2.1", "LGPL-3.0"}


def artifact_id(jar_name):
    """`netty-transport-native-epoll-4.2.15.Final-linux-x86_64.jar` -> `netty-transport-native-epoll`.

    Determinations are recorded per artifact, not per build: the leftmost dash-separated part that
    starts with a digit begins the version, and anything after it is a version suffix or classifier.
    """
    base = jar_name[:-4] if jar_name.endswith(".jar") else jar_name
    parts = base.split("-")
    for index in range(1, len(parts)):
        if parts[index][:1].isdigit():
            return "-".join(parts[:index])
    return base


def load_determinations(base):
    """The checked-in license determination for artifacts whose JAR carries no license text.

    Most Maven artifacts ship no LICENSE inside the JAR at all, so "the JAR must contain its own
    upstream license" is not satisfiable in principle. What ASF actually requires of a binary
    distribution is that the terms travel with it, which this file plus `legal/licenses/` provides:
    an explicit, reviewable determination per artifact instead of an impossible self-contained one.
    """
    path = Path(base) / DETERMINATIONS
    if not path.exists():
        return {}
    return json.loads(path.read_text(encoding="utf-8"))


def determination_for(name, determinations):
    """-> {"kind": "apache"|"collected"|"categoryB", ...} or None when nothing is recorded."""
    if not determinations:
        return None
    key = artifact_id(name)
    if key in determinations.get("apache", {}):
        entry = determinations["apache"][key] or {}
        return {"kind": "apache", "license": "Apache-2.0",
                "basis": entry.get("basis") or determinations.get("apacheBasis", "")}
    for kind in ("collected", "categoryB"):
        if key in determinations.get(kind, {}):
            entry = dict(determinations[kind][key])
            entry["kind"] = "categoryB" if kind == "categoryB" else "collected"
            if entry.get("license") in CATEGORY_B:
                entry["kind"] = "categoryB"
            return entry
    return None


def sha(data):
    return hashlib.sha256(data).hexdigest()


def read(path):
    data = Path(path).read_bytes()
    if not data.strip():
        raise ValueError(f"empty license material: {path}")
    return data


def safe(name):
    p = PurePosixPath(name)
    if p.is_absolute() or ".." in p.parts or "\\" in name:
        raise ValueError(f"unsafe material path: {name}")
    return name


def is_legal(name):
    p = PurePosixPath(name)
    if p.suffix.lower() in {".class", ".java", ".js", ".ts", ".go", ".py", ".so", ".dll", ".exe"}:
        return False
    return bool(LEGAL_NAME.match(p.name) or p.name.lower() == "about.html"
                or any(part.lower() in {"licenses", "legal", "license"} for part in p.parts[:-1]))


def texts_from_zip(data):
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        if len(archive.namelist()) != len(set(archive.namelist())):
            raise ValueError("dependency JAR contains duplicate ZIP entries")
        return {safe(name): archive.read(name) for name in sorted(archive.namelist())
                if not name.endswith("/") and is_legal(name)}


def add_component(files, components, errors, name, payload, texts, notice, source,
                  determinations=None, warnings=None, base=SERVER):
    prefix = f"licenses/{safe(name)}/"
    component = {"name": name, "sha256": sha(payload), "source": source, "files": []}
    license_texts = [data for filename, data in texts.items() if LICENSE_NAME.match(PurePosixPath(filename).name)]
    if any(b"GNU GENERAL PUBLIC LICENSE" in data or b"GNU LESSER GENERAL PUBLIC LICENSE" in data
           or b"GNU AFFERO GENERAL PUBLIC LICENSE" in data for data in license_texts):
        errors.append(f"{name}: contains GPL-family terms; dual-license choice/exception and redistribution obligations require manual review")
    if not license_texts or not any(len(data.strip()) >= 300 for data in license_texts):
        determination = determination_for(name, determinations)
        if determination is None:
            errors.append(f"{name}: missing complete upstream LICENSE/COPYING and no determination in "
                          f"{DETERMINATIONS}; a POM name or link is not a substitute")
        elif determination["kind"] == "apache":
            # ASF does not ask a binary distribution to repeat the Apache-2.0 text once per component:
            # the distribution's own LICENSE already is that text. Their NOTICE, if the JAR carries
            # one, is still merged into ours by the loop below.
            component["license"] = "Apache-2.0"
            component["coveredBy"] = "distribution LICENSE (Apache-2.0)"
            component["basis"] = determination["basis"]
        else:
            source_path = Path(base) / COLLECTED / safe(determination["text"])
            try:
                collected = read(source_path)
            except (OSError, ValueError) as failure:
                errors.append(f"{name}: collected license text is unusable ({source_path}): {failure}")
            else:
                texts = dict(texts)
                texts["LICENSE"] = collected
                component["license"] = determination["license"]
                component["basis"] = determination.get("basis", "")
                component["textSource"] = f"server/{COLLECTED}/{determination['text']}"
                # Any determination may carry an open question. Surfacing it on every build beats
                # burying it in a JSON file nobody re-reads.
                if determination.get("review") and determination["kind"] != "categoryB" and warnings is not None:
                    warnings.append(f"{name}: {determination['license']} — {determination['review']}")
                    component["review"] = determination["review"]
                if determination["kind"] == "categoryB":
                    review = determination.get("review")
                    if not review:
                        errors.append(f"{name}: ASF Category B ({determination['license']}) is bundled "
                                      f"without a recorded review decision")
                    else:
                        component["categoryBReview"] = review
                        if warnings is not None:
                            warnings.append(f"{name}: ASF Category B ({determination['license']}) bundled — {review}")
    for filename, data in sorted(texts.items()):
        if not data.strip():
            errors.append(f"{name}: empty license file {filename}")
            continue
        target = prefix + safe(filename) + ".txt"
        files[target] = data
        component["files"].append({"path": target, "source": filename, "sha256": sha(data)})
        if NOTICE_NAME.match(PurePosixPath(filename).name):
            notice += f"\n--- {name} / {filename} ---\n".encode() + data + b"\n"
    components.append(component)
    return notice


def finish(files, components, errors, license_text, notice, payload, warnings=None):
    for component in components:
        if component.get("coveredBy"):
            license_text += (f"\n{component['name']}: {component['license']}, covered by the "
                             f"META-INF/LICENSE text of this distribution\n").encode()
        else:
            license_text += f"\n{component['name']}: complete upstream license, copyright and additional terms under META-INF/legal/licenses/{component['name']}/\n".encode()
    files["LICENSE"] = license_text
    files["NOTICE"] = notice
    manifest = {"components": components, "payload": payload, "manualReview": sorted(set(errors)),
                "warnings": sorted(set(warnings or [])),
                "categoryB": sorted(c["name"] for c in components if c.get("categoryBReview")),
                "files": {name: sha(data) for name, data in sorted(files.items())}}
    files["manifest.json"] = (json.dumps(manifest, ensure_ascii=False, indent=2) + "\n").encode()
    return files, manifest


def generated_entry(name):
    return name in {"META-INF/LICENSE", "META-INF/NOTICE"} or name.startswith("META-INF/legal/")


def collect_jar(jar, base=SERVER):
    files, components, errors, payload = {}, [], [], {}
    warnings, determinations = [], load_determinations(base)
    license_text, notice = read(base / "LICENSE"), read(base / "NOTICE")
    with zipfile.ZipFile(jar) as archive:
        if len(archive.namelist()) != len(set(archive.namelist())):
            raise ValueError("distributed JAR contains duplicate ZIP entries")
        dependencies = sorted(name for name in archive.namelist() if name.startswith("BOOT-INF/lib/") and name.endswith(".jar"))
        if not dependencies:
            raise ValueError("not a Spring Boot JAR containing actual dependencies; refuse to generate an empty license manifest")
        for name in archive.namelist():
            if not name.endswith("/") and not generated_entry(name):
                payload[safe(name)] = sha(archive.read(name))
        for name in dependencies:
            data = archive.read(name)
            filename = PurePosixPath(name).name
            texts = texts_from_zip(data)
            notice = add_component(files, components, errors, filename, data, texts, notice, name,
                                   determinations, warnings, base)
            if filename.startswith("mysql-connector-j-"):
                errors.append(f"{filename}: applicability of GPLv2 + Universal FOSS Exception and ASF redistribution licensing pending PMC/legal review; this is not the Classpath Exception")
        # The Spring Boot loader itself is also copied into the JAR, not only the dependencies under
        # BOOT-INF/lib, so its own license and NOTICE have to travel with the distribution too.
        if any(name.startswith("org/springframework/boot/loader/") and name.endswith(".class") for name in archive.namelist()):
            spring_license, spring_notice = "META-INF/LICENSE.txt", "META-INF/NOTICE.txt"
            version = re.search(rb"(?m)^Spring-Boot-Version: ([^\r\n]+)", archive.read("META-INF/MANIFEST.MF"))
            if not version:
                errors.append("Spring Boot loader: missing version source")
            loader_version = version.group(1).decode() if version else "unknown"
            if spring_license in archive.namelist() and spring_notice in archive.namelist():
                # Up to Boot 3.x the repackage goal copied the loader artifact's own materials here.
                texts = {name: archive.read(name) for name in (spring_license, spring_notice)}
                source = "JAR root META-INF"
            else:
                # Boot 4.x stopped copying them, and the loader classes arrive embedded in
                # spring-boot-loader-tools rather than as a dependency JAR, so there is nothing left
                # in the archive to read. Take the checked-in copies of that artifact's own
                # META-INF/LICENSE.txt and META-INF/NOTICE.txt instead — and refuse them if they were
                # collected for a different Boot version, which is how a stale NOTICE would sneak in.
                texts, source = {}, f"server/{COLLECTED} (spring-boot-loader-tools {loader_version})"
                for collected_name, embedded_name in (("spring-boot-loader.LICENSE.txt", spring_license),
                                                      ("spring-boot-loader.NOTICE.txt", spring_notice)):
                    path = Path(base) / COLLECTED / collected_name
                    if not path.exists():
                        errors.append(f"Spring Boot loader: collected {collected_name} is missing ({path})")
                        continue
                    texts[embedded_name] = read(path)
                if loader_version != "unknown" and loader_version.encode() not in texts.get(spring_notice, b""):
                    errors.append(f"Spring Boot loader: the collected NOTICE is not for {loader_version}; "
                                  f"re-collect it from spring-boot-loader-tools {loader_version}")
            if texts:
                loader = "spring-boot-loader@" + loader_version
                notice = add_component(files, components, errors, loader, b"".join(texts.values()), texts,
                                       notice, source, determinations, warnings, base)
    return finish(files, components, errors, license_text, notice, payload, warnings)


def write_bundle(directory, files):
    directory = Path(directory)
    for name, data in files.items():
        target = directory / safe(name)
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)


def embedded_path(name):
    return f"META-INF/{name}" if name in {"LICENSE", "NOTICE"} else f"META-INF/legal/{name}"


def require_complete(manifest):
    if manifest["manualReview"]:
        raise ValueError("release license gate failed:\n" + "\n".join(manifest["manualReview"]))


def check_jar(jar, base=SERVER):
    files, manifest = collect_jar(jar, base)
    with zipfile.ZipFile(jar) as archive:
        for name, expected in files.items():
            target = embedded_path(name)
            if target not in archive.namelist() or archive.read(target) != expected:
                raise ValueError(f"JAR is missing legal materials or they do not match the actual dependencies: {target}")
        actual = {name for name in archive.namelist() if generated_entry(name) and not name.endswith("/")}
        if actual != {embedded_path(name) for name in files}:
            raise ValueError("JAR has stale or unregistered legal materials")
    for warning in manifest["warnings"]:
        print(f"WARNING {warning}")
    require_complete(manifest)
    covered = sum(1 for c in manifest["components"] if c.get("coveredBy"))
    collected = sum(1 for c in manifest["components"] if c.get("textSource"))
    print(f"JAR license check passed: {len(manifest['components'])} actual components "
          f"({covered} Apache-2.0 covered by the distribution LICENSE, {collected} from collected text, "
          f"{len(manifest['categoryB'])} Category B)")


def package_jar(jar, output, base=SERVER):
    jar = Path(jar)
    files, manifest = collect_jar(jar, base)
    write_bundle(output, files)
    # Warnings are printed by the check_jar pass at the end of this function; printing them here too
    # would double every line.
    # Emit the review materials first for inspection, but do not produce a seemingly compliant distribution package when materials are missing or licensing is unresolved.
    require_complete(manifest)
    with tempfile.NamedTemporaryFile(dir=jar.parent, suffix=".legal.jar", delete=False) as temporary:
        temporary_path = Path(temporary.name)
    try:
        with zipfile.ZipFile(jar) as source, zipfile.ZipFile(temporary_path, "w") as target:
            target.comment = source.comment
            for entry in source.infolist():
                if not generated_entry(entry.filename):
                    # Preserve attributes such as ZIP_STORED for nested JARs; Boot's random-access loading must not break.
                    target.writestr(entry, source.read(entry.filename))
            for name, data in sorted(files.items()):
                entry = zipfile.ZipInfo(embedded_path(name), (1980, 1, 1, 0, 0, 0))
                entry.compress_type = zipfile.ZIP_DEFLATED
                entry.external_attr = 0o100644 << 16
                target.writestr(entry, data)
        check_jar(temporary_path, base)
        # NamedTemporaryFile starts at 0600; retain the original JAR's deployment permissions.
        shutil.copymode(jar, temporary_path)
        os.replace(temporary_path, jar)
    finally:
        if temporary_path.exists():
            temporary_path.unlink()


def collect_npm(directory, node_license, base=SERVER):
    files, components, errors = {}, [], []
    license_text, notice = read(base / "LICENSE"), read(base / "NOTICE")
    directory = Path(directory)
    package_files = sorted(directory.rglob("package.json"))
    if not package_files:
        raise ValueError("global npm directory is empty; cannot verify the default AI CLI")
    seen_agents = set()
    for package_file in package_files:
        pkg = json.loads(read(package_file))
        if not pkg.get("name") or not pkg.get("version"):
            continue
        name = pkg["name"] + "@" + pkg["version"]
        package_root = package_file.parent
        texts = {}
        for current, dirs, names in os.walk(package_root):
            # Handle each actually installed sub-package separately; a parent package's license must not stand in for a sub-package's.
            dirs[:] = sorted(d for d in dirs if d != "node_modules" and not (Path(current) / d / "package.json").exists())
            for filename in names:
                relative = (Path(current) / filename).relative_to(package_root).as_posix()
                if is_legal(relative):
                    texts[relative] = read(Path(current) / filename)
        # A package name may be installed more than once; the manifest path keeps the physical install location to avoid overwriting each other.
        identity = package_file.parent.relative_to(directory).as_posix() + "@" + pkg["version"]
        notice = add_component(files, components, errors, identity, read(package_file), texts, notice,
                               package_file.relative_to(directory).as_posix())
        components[-1]["package"] = name
        components[-1]["declaredLicense"] = pkg.get("license")
        if not isinstance(pkg.get("license"), str) or pkg["license"] not in PERMISSIVE:
            errors.append(f"{name}: declared license {pkg.get('license')!r} requires manual review")
        if pkg["name"] in {"@anthropic-ai/claude-code", "@qoder-ai/qodercli"}:
            seen_agents.add(pkg["name"])
            errors.append(f"{name}: redistribution licensing for the default in-image AI CLI and its native payload pending manual review; being installable via npm is not treated as licensed")
    expected_agents = {"@anthropic-ai/claude-code", "@qoder-ai/qodercli"}
    if seen_agents != expected_agents:
        errors.append("the default AI CLI is not fully installed; the license check must not be bypassed by omitting components")
    files["licenses/node/LICENSE.txt"] = read(node_license)
    license_text += b"\nNode.js and bundled components: legal/licenses/node/LICENSE.txt\n"
    return finish(files, components, errors, license_text, notice, {})


def scan_headers(root):
    # Only report pre-existing missing headers in other scopes; do not modify Java/SQL, tests, or workflow files owned by concurrent agents.
    names = subprocess.check_output(["git", "-C", str(root), "ls-files", "-z"]).decode().split("\0")
    extensions = {".sh", ".py", ".go", ".ts", ".tsx", ".mjs", ".cjs", ".js", ".css", ".html", ".xml", ".yml", ".yaml"}
    missing = []
    for name in names:
        file = root / name
        if not name or not file.is_file() or (file.suffix not in extensions and file.name not in {"Dockerfile", "Makefile"}):
            continue
        if name.startswith("server/src/") or name.startswith("web/src/test/") or name == "rmqctl/internal/catalog/catalog_gen.go":
            continue
        text = file.read_text(errors="replace")
        if "Licensed to the Apache Software Foundation" not in text[:5000]:
            missing.append(name)
    print(json.dumps({"scope": "tracked non-Java/SQL files outside concurrent-agent resources/tests", "missing": missing}, ensure_ascii=False, indent=2))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command")
    headers = sub.add_parser("headers")
    headers.add_argument("root", type=Path)
    package = sub.add_parser("jar")
    package.add_argument("jar", type=Path)
    package.add_argument("--output", type=Path, required=True)
    check = sub.add_parser("check-jar")
    check.add_argument("jar", type=Path)
    check.add_argument("--output", type=Path)
    inspect = sub.add_parser("inspect")
    inspect.add_argument("jar", type=Path)
    inspect.add_argument("--summary", action="store_true")
    npm = sub.add_parser("npm")
    npm.add_argument("directory", type=Path)
    npm.add_argument("--node-license", type=Path, required=True)
    npm.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if args.command is None:
        parser.error("missing subcommand")
    if args.command == "headers":
        scan_headers(args.root)
    elif args.command == "jar":
        package_jar(args.jar, args.output)
    elif args.command == "check-jar":
        check_jar(args.jar)
        if args.output:
            files, _ = collect_jar(args.jar)
            write_bundle(args.output / "META-INF/legal", files)
            write_bundle(args.output, {name: files[name] for name in ["LICENSE", "NOTICE"]})
    elif args.command == "inspect":
        texts = texts_from_zip(read(args.jar))
        print(json.dumps({"jar": args.jar.name, "sha256": sha(read(args.jar)),
                          "legalFiles": {name: {"sha256": sha(data), "bytes": len(data), "text": data[:1200].decode('utf-8', errors='replace') if args.summary else data.decode('utf-8', errors='replace')}
                                         for name, data in texts.items()}}, ensure_ascii=False, indent=2))
    elif args.command == "npm":
        files, manifest = collect_npm(args.directory, args.node_license)
        # Relative paths of in-image materials do not use the JAR's META-INF prefix.
        files["LICENSE"] = files["LICENSE"].replace(b"META-INF/legal/", b"legal/")
        manifest["files"]["LICENSE"] = sha(files["LICENSE"])
        files["manifest.json"] = (json.dumps(manifest, ensure_ascii=False, indent=2) + "\n").encode()
        write_bundle(args.output, files)
        require_complete(manifest)


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, zipfile.BadZipFile, KeyError) as error:
        print(f"license gate: {error}", file=sys.stderr)
        sys.exit(1)
