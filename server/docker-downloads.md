# Server Docker download integrity

`Dockerfile` verifies the following downloaded files against committed checksums
before extracting them or adding them to the runtime classpath. A failed download,
missing checksum tool, or mismatch fails the image build. Expected checksums are
not downloaded alongside the artifact during the build and cannot be replaced by
build arguments.

The versions, download mirrors, and existing licensing separation are unchanged:
these third-party binaries are obtained by the image builder, not bundled in the
ASF source release. The Node archive remains Linux x64 only.

## Checksum provenance

Verified on 2026-10-02 by downloading each artifact from its upstream location and
independently computing its digest, then comparing with the published checksum:

- Node.js `node-v20.19.2-linux-x64.tar.gz`, SHA-256:
  `eec2c7b9c6ac72e42885a42edfc0503c0e4ee455f855c4a17a6cbcf026656dd5`
  - [Upstream checksums](https://nodejs.org/dist/v20.19.2/SHASUMS256.txt)
  - [Upstream archive](https://nodejs.org/dist/v20.19.2/node-v20.19.2-linux-x64.tar.gz)
  - The Dockerfile downloads from npmmirror but trusts the committed **upstream**
    checksum, not a checksum supplied by that mirror at build time.
- Apache Maven `apache-maven-3.9.9-bin.tar.gz`, SHA-512:
  `a555254d6b53d267965a3404ecb14e53c3827c09c3b94b5678835887ab404556bfaf78dcfe03ba76fa2508649dca8531c74bca4d5846513522404d48e8c4ac8b`
  - [Upstream checksum](https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.tar.gz.sha512)
  - [Upstream archive](https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.tar.gz)
- MySQL Connector/J `mysql-connector-j-9.7.0.jar`, SHA-256:
  `0353648eaa1c91e0f4020c959abf756bc866ffd583df22ae6b6f6e0cbd43eb44`
  - [Maven Central checksum](https://repo.maven.apache.org/maven2/com/mysql/mysql-connector-j/9.7.0/mysql-connector-j-9.7.0.jar.sha256)
  - [Maven Central artifact](https://repo.maven.apache.org/maven2/com/mysql/mysql-connector-j/9.7.0/mysql-connector-j-9.7.0.jar)

These pins detect bytes that differ from the reviewed artifacts. They are not a
replacement for publisher-signature verification or proof that an upstream
artifact is free of vulnerabilities. The initial checksum sources were retrieved
via HTTPS; no independent publisher-signing-key verification is claimed here.

## Updating a download

Before changing any pinned digest or download command, you must manually run
`python3 server/scripts/docker_downloads_test.py` from the repository root.
Run it again after the change to check the updated commands. CI does not currently
execute this suite; CI wiring is maintained by the project committers.

1. Choose the intended exact version and check its runtime compatibility.
2. Obtain the artifact and its published checksum from the upstream project,
   independently of any deployment mirror. Compute the artifact's digest locally
   and compare it before changing the committed checksum.
3. Update the URL, digest, extraction paths, regression fixture names, and this
   provenance record together. Do not make the expected checksum a build argument,
   read it from the download mirror during the build, or skip a failed check.
4. Run `python3 server/scripts/docker_downloads_test.py` from the repository root.
   The offline suite executes the actual Dockerfile shell commands with fake
   downloads and real checksum tools; extraction and CLI execution are mocked.
   It covers valid, changed, empty, download-failure, checksum-tool-failure, and
   changed-expected-digest cases. Build both server image targets separately when
   validating a dependency update in the supported deployment environment.

## Remaining build inputs

This is a bounded download-integrity change, not a fully reproducible image.
The `alibabadragonwell/dragonwell:21` tag remains rolling because the Dockerfile
records deployment-registry mirror reachability constraints. Base-image digests
and operating-system package repositories require their own update policy.
The globally installed Claude Code and Qoder packages also remain unversioned;
selecting supported exact versions and locking their transitive/optional packages
and install-time behavior is a separate change. A direct npm version pin alone
does not lock all of those inputs.
