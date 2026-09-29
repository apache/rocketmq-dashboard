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
	"slices"
	"strings"
	"unicode"

	"github.com/spf13/cobra"
)

// valueCompletion returns data candidates, never filesystem suggestions. Keep a
// separate result slice: catalog enum slices are shared and must stay read-only.
func valueCompletion(values []string) cobra.CompletionFunc {
	return func(_ *cobra.Command, _ []string, prefix string) ([]string, cobra.ShellCompDirective) {
		candidates := make([]string, 0, len(values))
		for _, value := range values {
			// A tab introduces a description and a newline starts another record
			// in Cobra's completion protocol; neither belongs in a value.
			if strings.HasPrefix(value, prefix) && strings.IndexFunc(value, unicode.IsControl) < 0 {
				candidates = append(candidates, value)
			}
		}
		slices.Sort(candidates)
		return slices.Compact(candidates), cobra.ShellCompDirectiveNoFileComp
	}
}
