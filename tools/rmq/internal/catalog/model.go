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

// CLI identifies the resource and verb exposed by rmqctl for a tool.
type CLI struct {
	Resource string `json:"resource" yaml:"resource"`
	Verb     string `json:"verb" yaml:"verb"`
}

// Tool describes one operation in the canonical RocketMQ tool catalog.
type Tool struct {
	Name                 string         `json:"name" yaml:"name"`
	CLI                  CLI            `json:"cli" yaml:"cli"`
	Description          string         `json:"description" yaml:"description"`
	RiskLevel            string         `json:"riskLevel" yaml:"riskLevel"`
	Permission           string         `json:"permission" yaml:"permission"`
	RequiredCapabilities []string       `json:"requiredCapabilities" yaml:"requiredCapabilities"`
	InputSchema          map[string]any `json:"inputSchema" yaml:"inputSchema"`
	OutputSchema         map[string]any `json:"outputSchema" yaml:"outputSchema"`
	ViewHint             string         `json:"viewHint" yaml:"viewHint"`
	Deprecated           bool           `json:"deprecated" yaml:"deprecated"`
	Replacement          string         `json:"replacement,omitempty" yaml:"replacement,omitempty"`
}

// All returns every catalog tool in stable name order.
func All() []Tool {
	result := make([]Tool, len(generatedTools))
	for i, tool := range generatedTools {
		result[i] = cloneTool(tool)
	}
	return result
}

// Find returns the tool with the exact canonical name.
func Find(name string) (Tool, bool) {
	for _, tool := range generatedTools {
		if tool.Name == name {
			return cloneTool(tool), true
		}
	}
	return Tool{}, false
}

// FindByResource returns every tool mapped to the exact CLI resource.
func FindByResource(resource string) []Tool {
	var result []Tool
	for _, tool := range generatedTools {
		if tool.CLI.Resource == resource {
			result = append(result, cloneTool(tool))
		}
	}
	return result
}

// Names returns all canonical tool names in stable alphabetical order.
func Names() []string {
	result := make([]string, len(generatedTools))
	for i, tool := range generatedTools {
		result[i] = tool.Name
	}
	return result
}

func cloneTool(tool Tool) Tool {
	cloned := tool
	cloned.RequiredCapabilities = append([]string(nil), tool.RequiredCapabilities...)
	cloned.InputSchema = cloneMap(tool.InputSchema)
	cloned.OutputSchema = cloneMap(tool.OutputSchema)
	return cloned
}

func cloneMap(source map[string]any) map[string]any {
	if source == nil {
		return nil
	}

	cloned := make(map[string]any, len(source))
	for key, value := range source {
		cloned[key] = cloneValue(value)
	}
	return cloned
}

func cloneValue(value any) any {
	switch value := value.(type) {
	case map[string]any:
		return cloneMap(value)
	case []any:
		cloned := make([]any, len(value))
		for i, item := range value {
			cloned[i] = cloneValue(item)
		}
		return cloned
	default:
		return value
	}
}
