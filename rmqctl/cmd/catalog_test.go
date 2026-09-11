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
	"fmt"
	"strings"
	"testing"

	toolcatalog "github.com/apache/rocketmq-dashboard/rmqctl/internal/catalog"
	"github.com/spf13/cobra"
)

func TestCatalogRegistersEveryToolAndSchemaFlag(t *testing.T) {
	root := &cobra.Command{Use: "rmqctl"}
	commands, err := newCatalogCommands(commandRuntime{})
	if err != nil {
		t.Fatal(err)
	}
	root.AddCommand(commands...)

	for _, tool := range toolcatalog.Default().Tools {
		command := childCommand(root, tool.CLI.Resource, tool.CLI.Verb)
		if command == nil {
			t.Errorf("tool %q command %q is not registered", tool.Name, tool.CommandPath())
			continue
		}
		if command.Short != tool.Description {
			t.Errorf("tool %q help = %q, want %q", tool.Name, command.Short, tool.Description)
		}
		var assertSchemaFlags func(toolcatalog.InputSchema)
		assertSchemaFlags = func(schema toolcatalog.InputSchema) {
			for _, field := range schema.Fields {
				if field.Object != nil {
					assertSchemaFlags(*field.Object)
					continue
				}
				flag := command.Flags().Lookup(field.Flag)
				if flag == nil {
					t.Errorf("tool %q does not expose --%s", tool.Name, field.Flag)
					continue
				}
				if actual, expected := flag.Value.Type(), cobraFlagType(field.Kind); actual != expected {
					t.Errorf("tool %q --%s type = %q, want %q", tool.Name, field.Flag, actual, expected)
				}
				if field.Required && field.Name != "cluster" && !strings.Contains(flag.Usage, "(required)") {
					t.Errorf("tool %q --%s help does not mark the flag as required", tool.Name, field.Flag)
				}
			}
		}
		assertSchemaFlags(tool.InputSchema)
	}
	if command := childCommand(root, "message", "query-by-group"); command != nil {
		t.Fatal("removed message query-by-group command is still registered")
	}
}

func childCommand(root *cobra.Command, path ...string) *cobra.Command {
	current := root
	for _, name := range path {
		var next *cobra.Command
		for _, command := range current.Commands() {
			if command.Name() == name {
				next = command
				break
			}
		}
		if next == nil {
			return nil
		}
		current = next
	}
	return current
}

func cobraFlagType(kind toolcatalog.FieldKind) string {
	switch kind {
	case toolcatalog.StringField:
		return "string"
	case toolcatalog.IntegerField:
		return "int64"
	case toolcatalog.NumberField:
		return "float64"
	case toolcatalog.BooleanField:
		return "bool"
	case toolcatalog.StringSliceField:
		return "stringSlice"
	default:
		return fmt.Sprint(kind)
	}
}
