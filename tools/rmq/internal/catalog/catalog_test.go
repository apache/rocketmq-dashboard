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

package catalog

import (
	"encoding/json"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"runtime"
	"slices"
	"strings"
	"testing"
)

func TestGeneratedCatalogMatchesCanonicalMetadata(t *testing.T) {
	if Version != "1.0.0" {
		t.Fatalf("Version = %q", Version)
	}
	if MinimumClientVersion != "1.0.0" {
		t.Fatalf("MinimumClientVersion = %q", MinimumClientVersion)
	}
	if !regexp.MustCompile(`^[0-9a-f]{64}$`).MatchString(SourceSHA256) {
		t.Fatalf("invalid digest %q", SourceSHA256)
	}
	if got := Names(); !slices.Equal(got, []string{
		"rmq.capabilities",
		"rmq.cluster.list",
	}) {
		t.Fatalf("Names() = %v", got)
	}
}

func TestFindUsesExactToolName(t *testing.T) {
	tool, ok := Find("rmq.capabilities")
	if !ok {
		t.Fatal("Find(rmq.capabilities) did not find the tool")
	}
	if tool.Name != "rmq.capabilities" {
		t.Fatalf("Find(rmq.capabilities).Name = %q", tool.Name)
	}
	if _, ok := Find("capabilities"); ok {
		t.Fatal("Find(capabilities) unexpectedly matched a partial name")
	}
}

func TestFindByResourceReturnsMatchingTools(t *testing.T) {
	got := FindByResource("cluster")
	if len(got) != 1 || got[0].Name != "rmq.cluster.list" {
		t.Fatalf("FindByResource(cluster) = %#v", got)
	}
	if got := FindByResource("missing"); len(got) != 0 {
		t.Fatalf("FindByResource(missing) = %#v", got)
	}
}

func TestCatalogLookupsReturnDeepDefensiveCopies(t *testing.T) {
	all := All()
	if len(all) == 0 {
		t.Fatal("All() returned no tools")
	}
	all[0].Name = "mutated"
	all[0].RequiredCapabilities = append(all[0].RequiredCapabilities, "MUTATED")
	all[0].InputSchema["type"] = "mutated"
	required := all[0].InputSchema["required"].([]any)
	required[0] = "mutated"
	nestedOutput := all[0].OutputSchema["properties"].(map[string]any)
	nestedOutput["mutated"] = true

	found, ok := Find("rmq.capabilities")
	if !ok {
		t.Fatal("Find(rmq.capabilities) did not find the tool after mutation")
	}
	if found.Name != "rmq.capabilities" {
		t.Fatalf("Find returned mutated name %q", found.Name)
	}
	if found.InputSchema["type"] != "object" {
		t.Fatalf("Find returned mutated input schema %#v", found.InputSchema)
	}
	if found.InputSchema["required"].([]any)[0] != "cluster" {
		t.Fatalf("Find returned mutated nested input schema %#v", found.InputSchema)
	}
	if _, exists := found.OutputSchema["properties"].(map[string]any)["mutated"]; exists {
		t.Fatalf("Find returned mutated nested output schema %#v", found.OutputSchema)
	}

	found.InputSchema["type"] = "mutated again"
	foundByResource := FindByResource("capabilities")
	if len(foundByResource) != 1 {
		t.Fatalf("FindByResource(capabilities) = %#v", foundByResource)
	}
	if foundByResource[0].InputSchema["type"] != "object" {
		t.Fatalf("FindByResource returned shared schema %#v", foundByResource[0].InputSchema)
	}

	names := Names()
	names[0] = "mutated"
	if Names()[0] != "rmq.capabilities" {
		t.Fatalf("Names returned a shared backing slice: %v", Names())
	}
}

func TestCatalogLookupsUseStableAlphabeticalOrder(t *testing.T) {
	all := All()
	if !slices.IsSortedFunc(all, func(a, b Tool) int {
		switch {
		case a.Name < b.Name:
			return -1
		case a.Name > b.Name:
			return 1
		default:
			return 0
		}
	}) {
		t.Fatalf("All() is not sorted by name: %#v", all)
	}

	for _, tool := range all {
		if !slices.IsSorted(tool.RequiredCapabilities) {
			t.Fatalf("%s capabilities are not sorted: %v", tool.Name, tool.RequiredCapabilities)
		}
	}
}

func TestCatalogLookupsPreserveEmptyCapabilitiesAsJSONArray(t *testing.T) {
	all := All()
	if len(all) == 0 {
		t.Fatal("All() returned no tools")
	}
	assertJSONArray(t, "All", all[0].RequiredCapabilities)

	found, ok := Find(all[0].Name)
	if !ok {
		t.Fatalf("Find(%q) did not find the tool", all[0].Name)
	}
	assertJSONArray(t, "Find", found.RequiredCapabilities)

	byResource := FindByResource(all[0].CLI.Resource)
	if len(byResource) == 0 {
		t.Fatalf("FindByResource(%q) returned no tools", all[0].CLI.Resource)
	}
	assertJSONArray(t, "FindByResource", byResource[0].RequiredCapabilities)
}

func TestGeneratedFilesArePinnedToLF(t *testing.T) {
	paths := []string{
		"/server/src/main/resources/tool-catalog/rmq-tools.yaml",
		"/tools/rmq/internal/catalog/generated.go",
		"/docs/rmqctl-reference.md",
	}
	repositoryRoot := filepath.Clean(filepath.Join(moduleRoot(t), "..", ".."))
	content, err := os.ReadFile(filepath.Join(repositoryRoot, ".gitattributes"))
	if err != nil {
		t.Fatalf("read .gitattributes: %v", err)
	}

	matches := make(map[string]int, len(paths))
	expected := make(map[string]struct{}, len(paths))
	for _, path := range paths {
		expected[path] = struct{}{}
	}
	for lineNumber, line := range strings.Split(string(content), "\n") {
		fields := strings.Fields(line)
		if len(fields) == 0 || strings.HasPrefix(fields[0], "#") {
			continue
		}
		if _, ok := expected[fields[0]]; !ok {
			continue
		}
		if !slices.Equal(fields, []string{fields[0], "text", "eol=lf"}) {
			t.Fatalf(".gitattributes line %d has invalid LF rule: %q", lineNumber+1, line)
		}
		matches[fields[0]]++
	}
	for _, path := range paths {
		if matches[path] != 1 {
			t.Fatalf(".gitattributes has %d exact LF rules for %s, want 1", matches[path], path)
		}
	}
}

func TestGeneratedFilesHaveNoDrift(t *testing.T) {
	cmd := exec.Command("go", "run", "./cmd/cataloggen", "-check")
	cmd.Dir = moduleRoot(t)
	output, err := cmd.CombinedOutput()
	if err != nil {
		t.Fatalf("catalog drift: %v\n%s", err, output)
	}
}

func assertJSONArray(t *testing.T, lookup string, capabilities []string) {
	t.Helper()

	encoded, err := json.Marshal(capabilities)
	if err != nil {
		t.Fatal(err)
	}
	if string(encoded) != "[]" {
		t.Fatalf("%s capabilities JSON = %s, want []", lookup, encoded)
	}
}

func moduleRoot(t *testing.T) string {
	t.Helper()

	_, filename, _, ok := runtime.Caller(0)
	if !ok {
		t.Fatal("could not locate catalog test source")
	}
	return filepath.Clean(filepath.Join(filepath.Dir(filename), "..", ".."))
}
