/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package main

import (
	"bytes"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

// verify is byte-exact on purpose, so a catalog whose line endings drifted
// (for example a CRLF copy written outside git) must be reported stale.
// Working-tree EOL consistency is .gitattributes' job, not the gate's: a
// catalog committed with CRLF must not pass -check undetected.
func TestCheckRejectsLineEndingDrift(t *testing.T) {
	input, output := writeCatalogSource(t, instanceScopedToolSource)

	if err := run([]string{"-input", input, "-output", output}); err != nil {
		t.Fatalf("generate catalog: %v", err)
	}
	generated, err := os.ReadFile(output)
	if err != nil {
		t.Fatal(err)
	}
	// A CRLF copy of an otherwise current catalog, as produced by tools
	// that bypass git's checkout filters.
	crlf := bytes.ReplaceAll(generated, []byte("\n"), []byte("\r\n"))
	if err := os.WriteFile(output, crlf, 0o600); err != nil {
		t.Fatal(err)
	}

	if err := run([]string{"-input", input, "-output", output, "-check"}); err == nil {
		t.Fatal("cataloggen -check accepted a catalog whose line endings drifted from the LF output go/format emits")
	}
}

// The byte-exact gate must not swallow real staleness: a genuinely different
// catalog is still rejected.
func TestCheckStillDetectsRealStaleness(t *testing.T) {
	input, output := writeCatalogSource(t, instanceScopedToolSource)

	if err := run([]string{"-input", input, "-output", output}); err != nil {
		t.Fatalf("generate catalog: %v", err)
	}
	generated, err := os.ReadFile(output)
	if err != nil {
		t.Fatal(err)
	}
	// Corrupt one version-like token so the content genuinely differs.
	corrupted := bytes.Replace(generated, []byte("1.0.0"), []byte("2.0.0"), 1)
	if bytes.Equal(corrupted, generated) {
		t.Fatal("fixture has no mutable token; strengthen the test")
	}
	if err := os.WriteFile(output, corrupted, 0o600); err != nil {
		t.Fatal(err)
	}

	if err := run([]string{"-input", input, "-output", output, "-check"}); err == nil {
		t.Fatal("cataloggen -check accepted a genuinely stale catalog")
	}
}

// The EOL half of the fix lives in .gitattributes: it pins the generated Go
// catalog (and every other .go file gofmt touches) to LF, so a
// core.autocrlf=true checkout cannot smudge it and trip the byte-exact
// verify gate. CI checks out on ubuntu-latest where a dropped rule would
// never show, so guard it here.
func TestGitAttributesPinsGoFilesToLF(t *testing.T) {
	const repositoryRoot = "../../../.."
	attributes, err := os.ReadFile(filepath.Join(repositoryRoot, ".gitattributes"))
	if err != nil {
		t.Fatalf("read .gitattributes: %v (the byte-exact catalog verify gate relies on it to keep working-tree copies LF on core.autocrlf=true checkouts)", err)
	}
	for _, line := range strings.Split(string(attributes), "\n") {
		fields := strings.Fields(line)
		if len(fields) == 0 || fields[0] != "*.go" {
			continue
		}
		text := false
		eolLF := false
		for _, attribute := range fields[1:] {
			switch attribute {
			case "text", "text=auto":
				text = true
			case "eol=lf":
				eolLF = true
			}
		}
		if !text || !eolLF {
			t.Fatalf(".gitattributes pins *.go with %v; expected \"text eol=lf\"", fields[1:])
		}
		return
	}
	t.Fatal(".gitattributes has no *.go entry; the byte-exact catalog verify gate needs \"*.go text eol=lf\" to keep core.autocrlf=true working copies LF")
}
