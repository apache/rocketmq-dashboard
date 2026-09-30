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

//go:generate go run ./generate -input-dir ../../../server/src/main/resources/tool-catalog/tools -output catalog_gen.go -markdown ../../../docs/generated/rmq-tools.md -sdk ../../../docs/generated/rmq-tools.json

type FieldKind string

const (
	StringField      FieldKind = "string"
	IntegerField     FieldKind = "integer"
	NumberField      FieldKind = "number"
	BooleanField     FieldKind = "boolean"
	StringSliceField FieldKind = "stringSlice"
	ObjectField      FieldKind = "object"
)

// ClientDefaultNow marks a field whose value rmqctl fills with the current
// Unix epoch milliseconds when the user did not pass the corresponding flag
// (catalog extension key x-client-default: NOW). The fill happens before
// required validation so a schema-required field with a client default is
// self-consistent.
const ClientDefaultNow = "NOW"

// InstanceIDGlobalFlag is the global CLI flag that supplies the Studio
// Instance identifier. The catalog field it feeds is never registered as a
// per-tool flag; see InstanceFieldName.
const InstanceIDGlobalFlag = "instance-id"

// platformToolNames lists the platform-level tools whose input schemas do not
// declare an instance identifier (design decisions 25/26). rmqctl still signs
// requests and sends the x-rmq-instance-id header for them, but the explicit
// --instance-id value is not added to their tool call arguments.
var platformToolNames = map[string]struct{}{
	"rmq.cluster.list":      {},
	"rmq.dashboard.summary": {},
	"rmq.audit.list":        {},
	"rmq.alert.rule.list":   {},
	"rmq.nameserver.list":   {},
	"rmq.nameserver.config": {},
	"rmq.broker.list":       {},
	"rmq.broker.describe":   {},
	"rmq.broker.config":     {},
	"rmq.proxy.list":        {},
	"rmq.proxy.config":      {},
	"rmq.litetopic.list":    {},
	"rmq.litetopic.session": {},
	"rmq.litetopic.quota":   {},
}

// IsPlatformTool reports whether the named tool is exempt from carrying an
// instanceId argument.
func IsPlatformTool(name string) bool {
	_, ok := platformToolNames[name]
	return ok
}

type Document struct {
	Version              string
	MinimumClientVersion string
	Digest               string
	Tools                []Tool
}

type Tool struct {
	Name                 string
	CLI                  CLI
	Description          string
	RiskLevel            string
	Permission           string
	RequiredCapabilities []string
	InputSchema          InputSchema
	ViewHint             string
	TableDataKey         string
	Deprecated           bool
	Replacement          string
}

type CLI struct {
	Resource string
	Verb     string
}

type InputSchema struct {
	Fields []Field
	AnyOf  []RequiredGroup
}

type RequiredGroup struct {
	Required []string
}

type Field struct {
	Name        string
	Flag        string
	Description string
	Kind        FieldKind
	Required    bool
	Enum        []string
	Minimum     float64
	HasMinimum  bool
	MinLength   int
	// ClientDefault records the x-client-default catalog extension. The only
	// supported value today is ClientDefaultNow ("NOW"): rmqctl fills the
	// current Unix epoch milliseconds when the user did not pass the flag.
	ClientDefault string
	// Object retains the JSON nesting; only its leaf fields have CLI flags.
	Object *InputSchema
}

// Default returns the built-in tool catalog document.
//
// The returned Document shares its nested slices and object schemas with the
// package-level default. Callers MUST treat them as read-only; mutations corrupt
// subsequent Default() calls. Callers needing a private copy must deep-copy both
// the slices and object schemas before mutation.
func Default() Document {
	return defaultDocument
}

// LookupTool returns the catalog tool registered under the given MCP tool name.
func LookupTool(name string) (Tool, bool) {
	for _, tool := range defaultDocument.Tools {
		if tool.Name == name {
			return tool, true
		}
	}
	return Tool{}, false
}

// InstanceFieldName returns the top-level input property that carries the
// Studio instance identifier, which rmqctl feeds from the global --instance-id
// flag instead of a per-tool flag. The canonical property name is instanceId.
func InstanceFieldName(schema InputSchema) (string, bool) {
	if field, ok := schema.Field("instanceId"); ok && field.Kind == StringField {
		return "instanceId", true
	}
	return "", false
}

func (tool Tool) CommandPath() string {
	return tool.CLI.Resource + " " + tool.CLI.Verb
}

func (schema InputSchema) Field(name string) (Field, bool) {
	for _, field := range schema.Fields {
		if field.Name == name {
			return field, true
		}
	}
	return Field{}, false
}
