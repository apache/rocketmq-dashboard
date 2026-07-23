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
	"os/exec"
	"path/filepath"
	"regexp"
	"runtime"
	"slices"
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

func TestGeneratedFilesHaveNoDrift(t *testing.T) {
	cmd := exec.Command("go", "run", "./cmd/cataloggen", "-check")
	cmd.Dir = moduleRoot(t)
	output, err := cmd.CombinedOutput()
	if err != nil {
		t.Fatalf("catalog drift: %v\n%s", err, output)
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
