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

	"go.yaml.in/yaml/v3"
)

type ciWorkflow struct {
	Jobs map[string]ciJob `yaml:"jobs"`
}

type ciJob struct {
	Defaults        ciDefaults `yaml:"defaults"`
	Steps           []ciStep   `yaml:"steps"`
	If              any        `yaml:"if"`
	ContinueOnError any        `yaml:"continue-on-error"`
}

type ciDefaults struct {
	Run ciRunDefaults `yaml:"run"`
}

type ciRunDefaults struct {
	WorkingDirectory string `yaml:"working-directory"`
}

type ciStep struct {
	Uses             string         `yaml:"uses"`
	Run              string         `yaml:"run"`
	With             map[string]any `yaml:"with"`
	WorkingDirectory string         `yaml:"working-directory"`
	If               any            `yaml:"if"`
	ContinueOnError  any            `yaml:"continue-on-error"`
}

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

func TestCIHasGoCLIFoundationJob(t *testing.T) {
	repositoryRoot := filepath.Clean(filepath.Join(moduleRoot(t), "..", ".."))
	content, err := os.ReadFile(filepath.Join(repositoryRoot, ".github", "workflows", "ci.yml"))
	if err != nil {
		t.Fatalf("read CI workflow: %v", err)
	}

	var workflow ciWorkflow
	if err := yaml.Unmarshal(content, &workflow); err != nil {
		t.Fatalf("parse CI workflow: %v", err)
	}

	for _, job := range workflow.Jobs {
		if supportsGoCLIFoundation(job) {
			return
		}
	}

	t.Fatal("CI workflow has no single Go job with the required actions, module working directory, and verification commands")
}

func TestGoCLIFoundationJobRejectsRunStepOutsideModule(t *testing.T) {
	job := validGoCLIFoundationJob()
	job.Steps[len(job.Steps)-1].WorkingDirectory = "server"

	if supportsGoCLIFoundation(job) {
		t.Fatal("Go CLI foundation job accepted a run step that overrides the module working directory")
	}
}

func TestGoCLIFoundationJobRequiresExecutableIndependentSteps(t *testing.T) {
	tests := []struct {
		name   string
		mutate func(*ciJob)
		want   bool
	}{
		{
			name: "current valid job",
			want: true,
		},
		{
			name: "checkout if false",
			mutate: func(job *ciJob) {
				job.Steps[0].If = false
			},
		},
		{
			name: "setup continue-on-error true",
			mutate: func(job *ciJob) {
				job.Steps[1].ContinueOnError = true
			},
		},
		{
			name: "command continue-on-error expression",
			mutate: func(job *ciJob) {
				job.Steps[2].ContinueOnError = "${{ matrix.experimental }}"
			},
		},
		{
			name: "command in dead shell block",
			mutate: func(job *ciJob) {
				job.Steps[2].Run = "if false; then\n  go test -race ./...\nfi"
			},
		},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			job := validGoCLIFoundationJob()
			if test.mutate != nil {
				test.mutate(&job)
			}
			if got := supportsGoCLIFoundation(job); got != test.want {
				t.Fatalf("supportsGoCLIFoundation() = %t, want %t", got, test.want)
			}
		})
	}
}

func TestGoCLIFoundationJobMustRunAndEnforceFailures(t *testing.T) {
	tests := []struct {
		name   string
		mutate func(*ciJob)
		want   bool
	}{
		{
			name: "current valid job",
			want: true,
		},
		{
			name: "explicit continue-on-error false",
			mutate: func(job *ciJob) {
				job.ContinueOnError = false
			},
			want: true,
		},
		{
			name: "job if false",
			mutate: func(job *ciJob) {
				job.If = false
			},
		},
		{
			name: "job continue-on-error true",
			mutate: func(job *ciJob) {
				job.ContinueOnError = true
			},
		},
		{
			name: "job continue-on-error expression",
			mutate: func(job *ciJob) {
				job.ContinueOnError = "${{ matrix.experimental }}"
			},
		},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			job := validGoCLIFoundationJob()
			if test.mutate != nil {
				test.mutate(&job)
			}
			if got := supportsGoCLIFoundation(job); got != test.want {
				t.Fatalf("supportsGoCLIFoundation() = %t, want %t", got, test.want)
			}
		})
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

func supportsGoCLIFoundation(job ciJob) bool {
	if job.If != nil || !enforcesCIFailures(job.ContinueOnError) {
		return false
	}

	requiredCommands := []string{
		"go test -race ./...",
		"go vet ./...",
		"go run ./cmd/cataloggen -check",
		"go build ./cmd/rmqctl",
	}
	hasCheckout := false
	hasSetupGo := false
	commands := make(map[string]bool, len(requiredCommands))
	runStepsUseModule := true

	for _, step := range job.Steps {
		switch {
		case step.Uses == "actions/checkout@v4" && isRequiredCIStep(step):
			hasCheckout = true
		case step.Uses == "actions/setup-go@v6" && isRequiredCIStep(step):
			hasSetupGo = hasSetupGo ||
				step.With["go-version"] == "1.26.x" &&
					step.With["cache-dependency-path"] == "tools/rmq/go.sum"
		}

		if step.Run == "" {
			continue
		}
		effectiveWorkingDirectory := step.WorkingDirectory
		if effectiveWorkingDirectory == "" {
			effectiveWorkingDirectory = job.Defaults.Run.WorkingDirectory
		}
		if effectiveWorkingDirectory != "tools/rmq" {
			runStepsUseModule = false
		}
		if isRequiredCIStep(step) {
			commands[strings.TrimSpace(step.Run)] = true
		}
	}

	hasRequiredCommands := true
	for _, command := range requiredCommands {
		hasRequiredCommands = hasRequiredCommands && commands[command]
	}
	return hasCheckout && hasSetupGo && runStepsUseModule && hasRequiredCommands
}

func isRequiredCIStep(step ciStep) bool {
	return step.If == nil && enforcesCIFailures(step.ContinueOnError)
}

func enforcesCIFailures(continueOnError any) bool {
	if continueOnError == nil {
		return true
	}
	value, ok := continueOnError.(bool)
	return ok && !value
}

func validGoCLIFoundationJob() ciJob {
	return ciJob{
		Defaults: ciDefaults{
			Run: ciRunDefaults{WorkingDirectory: "tools/rmq"},
		},
		Steps: []ciStep{
			{Uses: "actions/checkout@v4"},
			{
				Uses: "actions/setup-go@v6",
				With: map[string]any{
					"go-version":            "1.26.x",
					"cache-dependency-path": "tools/rmq/go.sum",
				},
			},
			{Run: "go test -race ./..."},
			{Run: "go vet ./..."},
			{Run: "go run ./cmd/cataloggen -check"},
			{Run: "go build ./cmd/rmqctl"},
		},
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
