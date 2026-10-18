# Proposal: signed rmqctl releases and installer verification

Status: **proposed**, pending the release-policy decisions in #6026. Existing
releases and the installer currently provide archive SHA-256 verification only.
This proposal does not claim signatures are already produced or checked.

## Current producer/consumer contract

`rmqctl/scripts/package-release.sh` emits platform-specific archives with adjacent
`.sha256` files. `rmqctl/install.sh` downloads both from the selected GitHub release,
checks their bytes and then extracts the archive. This detects corruption but does
not establish publisher identity independently of that release asset source.

## Proposed producer-first rollout

1. Maintainers decide whether these prebuilt assets use the RocketMQ ASF release
   signing process, and identify the first release that guarantees every supported
   platform is signed.
2. Sign the exact final archives and publish detached signatures alongside the
   existing checksum files. If one signed checksum manifest is chosen instead,
   define its exact archive-name/version/platform binding before implementation.
3. Publish the authorized full signer fingerprints and rotation/revocation process
   through project-controlled release documentation and the agreed KEYS process.
4. Verify every supported platform's produced assets before enabling mandatory
   installer verification. A missing platform signature must not result in an
   unannounced downgrade for that platform.
5. Only then require both byte-integrity and publisher verification before
   extraction or replacement of an installed binary.

Private signing keys stay with the authorized release process. They do not belong
in source, images, ordinary build configuration or installer test fixtures.

## Installer trust boundary

A signature is useful only when its signer is already trusted. Downloading a key
next to an archive and accepting any valid signature from it does not authenticate
the publisher. Verify against a pinned authorized fingerprint or an explicitly
supplied trusted keyring, according to the project-approved trust policy.

Use an isolated verification keyring; never import keys into the operator's
personal trust database. Check the full authorized fingerprint, not a short key ID.
Define how authorized key updates, revocation and expired keys reach operators.

Verification must fail clearly for changed bytes, a wrong signer, a missing
signature, an unavailable verifier and an unsupported release. Never extract first
or replace a working binary before verification succeeds.

The installer script itself is a separate input. Publish instructions for fetching
the versioned installer through the agreed trusted distribution and verifying it;
archive verification does not authenticate an arbitrary script piped into a shell.

## Decisions needed before implementation

- ASF release signing/KEYS or another project-approved signing mechanism, with its
  exact artifact naming and verification commands.
- The first signed version and supported platform matrix.
- Require signatures for signed versions onward; choose whether older releases are
  rejected or supported only through a separately documented explicit legacy path.
- Trusted fingerprint delivery, key rollover/revocation and offline verification.
- The installer's own authenticated distribution and minimum verifier requirements.

No silent fallback is proposed when a signed release fails verification. Whether a
legacy path exists is a compatibility decision, not an automatic recovery step.

## Verification required for a runtime change

Use disposable test keys, never production signing keys. Package and install the
real archive names offline for each supported platform. Cover a valid archive,
tampered archive, wrong signer, missing/malformed sidecars, renamed version or
platform, unavailable verifier, key rotation, interrupted download and verification
failure while an existing installation remains intact. Tests must prove that no
unverified executable is extracted into or moved to the install location.

References: [ASF release signing](https://infra.apache.org/release-signing) and
[ASF download verification](https://www.apache.org/info/verification).
