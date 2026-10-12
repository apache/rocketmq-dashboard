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
	"crypto/sha256"
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

// Single-file mode (-input) must feed the same source into the digest and the
// SDK contract that it parses: a := shadow of `source` in the read branch used
// to leave both consuming the zero value, baking sha256("") into the generated
// catalog and rendering the SDK contract as literal null.
func TestSingleFileModeDigestAndSdkContract(t *testing.T) {
	input, output := writeCatalogSource(t, instanceScopedToolSource)
	sdkPath := filepath.Join(filepath.Dir(input), "rmq-tools.json")

	if err := run([]string{"-input", input, "-output", output, "-sdk", sdkPath}); err != nil {
		t.Fatalf("generate catalog: %v", err)
	}

	source, err := os.ReadFile(input)
	if err != nil {
		t.Fatal(err)
	}
	digest := fmt.Sprintf("%x", sha256.Sum256(source))

	generated, err := os.ReadFile(output)
	if err != nil {
		t.Fatal(err)
	}
	// The digest line is struct-aligned in the generated source; match the
	// quoted digest itself rather than the "Digest:" prefix spacing.
	if !strings.Contains(string(generated), fmt.Sprintf("%q", digest)) {
		t.Fatalf("generated catalog digest does not match sha256 of the input source")
	}
	empty := fmt.Sprintf("%q", fmt.Sprintf("%x", sha256.Sum256(nil)))
	if strings.Contains(string(generated), empty) {
		t.Fatalf("generated catalog digest is sha256 of the empty string")
	}

	sdk, err := os.ReadFile(sdkPath)
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(string(sdk), "tools") {
		t.Fatalf("SDK contract lost the catalog content: %s", sdk)
	}
}
