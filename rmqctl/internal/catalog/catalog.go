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
