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
	"slices"
	"strings"
	"unicode/utf8"

	toolcatalog "github.com/apache/rocketmq-dashboard/rmqctl/internal/catalog"
)

func validateSchemaArguments(tool toolcatalog.Tool, schema toolcatalog.InputSchema, arguments map[string]any) error {
	for _, field := range schema.Fields {
		if field.Required && !argumentPresent(arguments, field.Name) {
			return invalidArgument(fmt.Sprintf("%s requires %s", tool.CommandPath(), fieldArgumentHint(field)))
		}
	}
	for name, value := range arguments {
		field, exists := schema.Field(name)
		if !exists {
			continue
		}
		if field.Kind == toolcatalog.ObjectField {
			object, ok := value.(map[string]any)
			if !ok || field.Object == nil {
				return invalidArgument(fmt.Sprintf("%s %s must be an object", tool.CommandPath(), name))
			}
			if err := validateSchemaArguments(tool, *field.Object, object); err != nil {
				return err
			}
			continue
		}
		if err := validateValue(tool, field, value); err != nil {
			return err
		}
	}
	if err := validateAnyOf(tool, schema, arguments); err != nil {
		return err
	}
	return nil
}

func validateAnyOf(tool toolcatalog.Tool, schema toolcatalog.InputSchema, arguments map[string]any) error {
	if len(schema.AnyOf) == 0 {
		return nil
	}
	for _, group := range schema.AnyOf {
		satisfied := true
		for _, name := range group.Required {
			if !argumentPresent(arguments, name) {
				satisfied = false
				break
			}
		}
		if satisfied {
			return nil
		}
	}
	flags := make([]string, 0, len(schema.AnyOf))
	for _, group := range schema.AnyOf {
		groupFlags := make([]string, 0, len(group.Required))
		for _, name := range group.Required {
			if field, ok := schema.Field(name); ok {
				groupFlags = append(groupFlags, fieldArgumentHint(field))
			} else {
				groupFlags = append(groupFlags, "--"+name)
			}
		}
		flags = append(flags, "["+strings.Join(groupFlags, " + ")+"]")
	}
	return invalidArgument(fmt.Sprintf(
		"%s requires at least one of: %s", tool.CommandPath(), strings.Join(flags, " OR ")))
}

func fieldArgumentHint(field toolcatalog.Field) string {
	if field.Object == nil {
		return "--" + field.Flag
	}
	flags := make([]string, 0, len(field.Object.Fields))
	for _, child := range field.Object.Fields {
		flags = append(flags, fieldArgumentHint(child))
	}
	return fmt.Sprintf("object %q (flags: %s)", field.Name, strings.Join(flags, ", "))
}

func validateValue(tool toolcatalog.Tool, field toolcatalog.Field, value any) error {
	if text, ok := value.(string); ok {
		if field.MinLength > 0 && utf8.RuneCountInString(text) < field.MinLength {
			return invalidArgument(fmt.Sprintf("%s --%s must not be empty", tool.CommandPath(), field.Flag))
		}
		if len(field.Enum) > 0 && !slices.Contains(field.Enum, text) {
			return invalidArgument(fmt.Sprintf(
				"%s --%s must be one of: %s",
				tool.CommandPath(), field.Flag, strings.Join(field.Enum, ", ")))
		}
	}
	if !field.HasMinimum {
		return nil
	}
	var number float64
	switch typed := value.(type) {
	case int64:
		number = float64(typed)
	case float64:
		number = typed
	default:
		return nil
	}
	if number < field.Minimum {
		return invalidArgument(fmt.Sprintf(
			"%s --%s must be at least %g", tool.CommandPath(), field.Flag, field.Minimum))
	}
	return nil
}

func argumentPresent(arguments map[string]any, name string) bool {
	value, exists := arguments[name]
	if !exists || value == nil {
		return false
	}
	if text, ok := value.(string); ok {
		return strings.TrimSpace(text) != ""
	}
	return true
}
