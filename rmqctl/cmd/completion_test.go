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
package cmd

import (
	"bytes"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"reflect"
	"slices"
	"strconv"
	"strings"
	"testing"

	toolcatalog "github.com/apache/rocketmq-dashboard/rmqctl/internal/catalog"
	"github.com/apache/rocketmq-dashboard/rmqctl/internal/config"
	"github.com/apache/rocketmq-dashboard/rmqctl/internal/output"
	"github.com/spf13/cobra"
)

type completionTransport struct{ t *testing.T }

func (transport completionTransport) RoundTrip(*http.Request) (*http.Response, error) {
	transport.t.Fatal("completion must not contact Studio")
	return nil, nil
}

type completionInput struct{ t *testing.T }

func (input completionInput) Read([]byte) (int, error) {
	input.t.Fatal("completion must not read stdin")
	return 0, io.EOF
}

func completeTestApp(t *testing.T, setup func(*App), args ...string) ([]string, cobra.ShellCompDirective) {
	t.Helper()
	stdout, stderr := &bytes.Buffer{}, &bytes.Buffer{}
	app := NewApp(stdout, stderr)
	app.In = completionInput{t}
	app.HTTP = &http.Client{Transport: completionTransport{t}}
	app.Store.Getenv = func(name string) string { t.Fatalf("unexpected environment lookup: %s", name); return "" }
	app.Store.HomeDir = func() (string, error) { t.Fatal("unexpected home directory lookup"); return "", nil }
	app.confirm = func(io.Reader, io.Writer, string, string, string) error {
		t.Fatal("completion must not prompt")
		return nil
	}
	if setup != nil {
		setup(app)
	}
	if code := app.Execute(append([]string{"__complete"}, args...)); code != 0 {
		t.Fatalf("completion exit %d: %s", code, stderr.String())
	}
	return parseCompletion(t, stdout.String())
}

func parseCompletion(t *testing.T, text string) ([]string, cobra.ShellCompDirective) {
	t.Helper()
	lines := strings.Split(strings.TrimSuffix(text, "\n"), "\n")
	value, err := strconv.Atoi(strings.TrimPrefix(lines[len(lines)-1], ":"))
	if err != nil || !strings.HasPrefix(lines[len(lines)-1], ":") {
		t.Fatalf("invalid completion response: %q", text)
	}
	return lines[:len(lines)-1], cobra.ShellCompDirective(value)
}

func TestCatalogEnumCompletionTest(t *testing.T) {
	for _, tool := range toolcatalog.Default().Tools {
		var visit func(toolcatalog.InputSchema)
		visit = func(schema toolcatalog.InputSchema) {
			for _, field := range schema.Fields {
				if field.Object != nil {
					visit(*field.Object)
					continue
				}
				if field.Kind != toolcatalog.StringField || len(field.Enum) == 0 {
					continue
				}
				t.Run(tool.CommandPath()+"/"+field.Flag, func(t *testing.T) {
					original := slices.Clone(field.Enum)
					got, directive := completeTestApp(t, nil, tool.CLI.Resource, tool.CLI.Verb, "--"+field.Flag, "")
					want := slices.Clone(original)
					slices.Sort(want)
					if !slices.Equal(got, want) || directive != cobra.ShellCompDirectiveNoFileComp {
						t.Fatalf("got %v / %v; want %v / NoFileComp", got, directive, want)
					}
					if !slices.Equal(field.Enum, original) {
						t.Fatal("completion mutated the shared catalog")
					}
				})
			}
		}
		visit(tool.InputSchema)
	}
	for _, example := range []struct {
		prefix string
		want   []string
	}{{"F", []string{"FIFO"}}, {"not-a-type", nil}} {
		got, directive := completeTestApp(t, nil, "topic", "list", "--type", example.prefix)
		if !slices.Equal(got, example.want) || directive != cobra.ShellCompDirectiveNoFileComp {
			t.Fatalf("prefix %q: %v / %v", example.prefix, got, directive)
		}
	}
}

func TestNestedCatalogEnumCompletionTest(t *testing.T) {
	schema := toolcatalog.InputSchema{Fields: []toolcatalog.Field{{Name: "settings", Kind: toolcatalog.ObjectField, Object: &toolcatalog.InputSchema{Fields: []toolcatalog.Field{{Name: "mode", Flag: "mode", Kind: toolcatalog.StringField, Enum: []string{"SECOND", "FIRST"}}}}}}}
	cmd := &cobra.Command{Use: "nested"}
	if _, err := bindSchemaArguments(cmd, "fixture", schema); err != nil {
		t.Fatal(err)
	}
	stdout := &bytes.Buffer{}
	cmd.SetOut(stdout)
	cmd.SetErr(io.Discard)
	cmd.SetArgs([]string{"__complete", "--mode", "F"})
	if err := cmd.Execute(); err != nil {
		t.Fatal(err)
	}
	got, directive := parseCompletion(t, stdout.String())
	if !reflect.DeepEqual(got, []string{"FIRST"}) || directive != cobra.ShellCompDirectiveNoFileComp {
		t.Fatalf("nested enum: %v / %v", got, directive)
	}
}

func TestOutputFormatCompletionTest(t *testing.T) {
	for _, flag := range []string{"--output", "-o"} {
		got, directive := completeTestApp(t, nil, "topic", "list", flag, "")
		want := output.SupportedFormats()
		slices.Sort(want)
		if !slices.Equal(got, want) || directive != cobra.ShellCompDirectiveNoFileComp {
			t.Fatalf("%s: %v / %v", flag, got, directive)
		}
		for _, format := range got {
			if err := output.RequireFormat(format); err != nil {
				t.Fatal(err)
			}
		}
	}
	got, directive := completeTestApp(t, nil, "--output", "j")
	if !slices.Equal(got, []string{"json"}) || directive != cobra.ShellCompDirectiveNoFileComp {
		t.Fatalf("output prefix: %v / %v", got, directive)
	}
}

func writeCompletionConfig(t *testing.T, path string, names ...string) []byte {
	t.Helper()
	cfg := config.EmptyConfig()
	for _, name := range names {
		cfg.Contexts[name] = newTestConfig("https://studio.example.invalid").Contexts["test"]
	}
	if err := config.NewStore().Save(path, cfg); err != nil {
		t.Fatal(err)
	}
	data, err := os.ReadFile(path)
	if err != nil {
		t.Fatal(err)
	}
	return data
}

func TestContextCompletionIsLocalReadOnlyTest(t *testing.T) {
	path := newTestConfigPath(t)
	names := []string{"staging", "production", "prod east", "生产", "bad\tannotation", "bad\n:0", "bad\rrecord", "bad\x1bescape"}
	original := writeCompletionConfig(t, path, names...)
	// No current context, credentials or instance ID are needed. The fixture's
	// environment/HTTP/stdin/confirmation traps must remain untouched.
	for _, command := range [][]string{{"--context"}, {"topic", "list", "--context"}, {"config", "use-context"}, {"config", "use"}, {"config", "delete-context"}, {"config", "delete"}, {"config", "set-context"}} {
		t.Run(strings.Join(command, "/"), func(t *testing.T) {
			args := append([]string{"--config", path}, command...)
			got, directive := completeTestApp(t, nil, append(args, "")...)
			want := []string{"prod east", "production", "staging", "生产"}
			if !slices.Equal(got, want) || directive != cobra.ShellCompDirectiveNoFileComp {
				t.Fatalf("context completion %v / %v", got, directive)
			}
		})
	}
	for _, example := range []struct {
		prefix string
		want   []string
	}{{"prod", []string{"prod east", "production"}}, {"生", []string{"生产"}}, {"absent", nil}} {
		got, directive := completeTestApp(t, nil, "--config", path, "--context", example.prefix)
		if !slices.Equal(got, example.want) || directive != cobra.ShellCompDirectiveNoFileComp {
			t.Fatalf("prefix %q: %v / %v", example.prefix, got, directive)
		}
	}
	for _, command := range []string{"use-context", "delete-context", "set-context"} {
		got, directive := completeTestApp(t, nil, "--config", path, "config", command, "production", "")
		if len(got) != 0 || directive != cobra.ShellCompDirectiveNoFileComp {
			t.Fatalf("extra positional: %v / %v", got, directive)
		}
	}
	after, err := os.ReadFile(path)
	if err != nil {
		t.Fatal(err)
	}
	if !bytes.Equal(after, original) {
		t.Fatal("completion changed the config")
	}
}

func TestContextCompletionConfigPrecedenceTest(t *testing.T) {
	explicit := newTestConfigPath(t)
	envPath := newTestConfigPath(t)
	home := t.TempDir()
	defaultPath := filepath.Join(home, ".rmqctl", "config.yaml")
	writeCompletionConfig(t, explicit, "explicit")
	writeCompletionConfig(t, envPath, "environment")
	writeCompletionConfig(t, defaultPath, "default")
	for _, example := range []struct {
		name, explicit, envPath string
		want                    []string
	}{
		{"explicit", explicit, envPath, []string{"explicit"}},
		{"environment", "", envPath, []string{"environment"}},
		{"default", "", "", []string{"default"}},
	} {
		t.Run(example.name, func(t *testing.T) {
			setup := func(app *App) {
				app.Store.Getenv = func(name string) string {
					if name != "RMQCTL_CONFIG" {
						t.Fatalf("credential environment read: %s", name)
					}
					return example.envPath
				}
				app.Store.HomeDir = func() (string, error) { return home, nil }
			}
			args := []string{"--context", ""}
			if example.explicit != "" {
				args = append([]string{"--config", example.explicit}, args...)
			}
			got, directive := completeTestApp(t, setup, args...)
			if !slices.Equal(got, example.want) || directive != cobra.ShellCompDirectiveNoFileComp {
				t.Fatalf("got %v / %v", got, directive)
			}
		})
	}
}

func TestContextCompletionMissingAndInvalidConfigTest(t *testing.T) {
	for _, example := range []struct {
		name, content      string
		missing, directory bool
	}{
		{name: "missing", missing: true}, {name: "empty", content: ""},
		{name: "invalid-yaml", content: "contexts: ["},
		{name: "unknown-current", content: "currentContext: missing\ncontexts: {}\n"},
		{name: "invalid-reference", content: "contexts:\n  secret-marker:\n    server: https://studio.example.invalid\n    credential:\n      accessKeyRef: plaintext-marker\n      secretKeyRef: env:SK\n"},
		{name: "unreadable-file", directory: true},
	} {
		t.Run(example.name, func(t *testing.T) {
			path := newTestConfigPath(t)
			if example.directory {
				if err := os.Mkdir(path, 0o700); err != nil {
					t.Fatal(err)
				}
			} else if !example.missing {
				if err := os.WriteFile(path, []byte(example.content), 0o600); err != nil {
					t.Fatal(err)
				}
			}
			want := cobra.ShellCompDirectiveNoFileComp
			if example.name != "missing" && example.name != "empty" {
				want |= cobra.ShellCompDirectiveError
			}
			got, directive := completeTestApp(t, nil, "--config", path, "--context", "")
			if len(got) != 0 || directive != want {
				t.Fatalf("got %v / %v, want empty / %v", got, directive, want)
			}
			if example.missing {
				if _, err := os.Stat(path); !os.IsNotExist(err) {
					t.Fatalf("completion created missing config: %v", err)
				}
			}
		})
	}
	// A real path flag still allows shell filesystem completion.
	got, directive := completeTestApp(t, nil, "--config", "")
	if len(got) != 0 || directive != cobra.ShellCompDirectiveDefault {
		t.Fatalf("config path completion changed: %v / %v", got, directive)
	}
}

func TestCompletionScriptsExposeShellHooksTest(t *testing.T) {
	for _, example := range []struct{ shell, hook string }{
		{"bash", "__start_rmqctl"}, {"zsh", "#compdef rmqctl"},
		{"fish", "complete -c rmqctl"}, {"powershell", "Register-ArgumentCompleter"},
	} {
		t.Run(example.shell, func(t *testing.T) {
			stdout, stderr := &bytes.Buffer{}, &bytes.Buffer{}
			app := NewApp(stdout, stderr)
			app.HTTP = &http.Client{Transport: completionTransport{t}}
			app.In = completionInput{t}
			app.Store.Getenv = func(name string) string { t.Fatalf("script generation read environment %s", name); return "" }
			app.Store.HomeDir = func() (string, error) { t.Fatal("script generation read config"); return "", nil }
			if code := app.Execute([]string{"completion", example.shell}); code != 0 {
				t.Fatalf("script generation exit %d: %s", code, stderr)
			}
			script := stdout.String()
			if !strings.Contains(script, example.hook) || !strings.Contains(script, "__complete") {
				t.Fatalf("missing %s completion hook or protocol entry point", example.shell)
			}
		})
	}
}
