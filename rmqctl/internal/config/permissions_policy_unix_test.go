//go:build !windows

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
package config

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestConfigRoundTripInNonWritableSharedDirectories(t *testing.T) {
	for _, mode := range []os.FileMode{0o700, 0o750, 0o755} {
		t.Run(fmt.Sprintf("%04o", mode), func(t *testing.T) {
			directory := t.TempDir()
			if err := os.Chmod(directory, mode); err != nil {
				t.Fatal(err)
			}
			path := filepath.Join(directory, "config.yaml")
			store := NewStore()
			if err := store.Save(path, EmptyConfig()); err != nil {
				t.Fatal(err)
			}
			if _, err := store.Load(path); err != nil {
				t.Fatal(err)
			}
			info, err := os.Stat(directory)
			if err != nil {
				t.Fatal(err)
			}
			if info.Mode().Perm() != mode {
				t.Fatalf("operator directory permissions changed: %04o", info.Mode().Perm())
			}
			file, err := os.Stat(path)
			if err != nil {
				t.Fatal(err)
			}
			if file.Mode().Perm() != 0o600 {
				t.Fatalf("file permissions = %04o", file.Mode().Perm())
			}
		})
	}
}

func TestConfigRejectsGroupOrOtherWritableParentsOnReadsAndWrites(t *testing.T) {
	for _, mode := range []os.FileMode{0o770, 0o777} {
		t.Run(fmt.Sprintf("%04o", mode), func(t *testing.T) {
			directory := t.TempDir()
			if err := os.Chmod(directory, 0o700); err != nil {
				t.Fatal(err)
			}
			path := filepath.Join(directory, "config.yaml")
			store := NewStore()
			if err := store.Save(path, EmptyConfig()); err != nil {
				t.Fatal(err)
			}
			if err := os.Chmod(directory, mode); err != nil {
				t.Fatal(err)
			}
			if _, err := store.Load(path); err == nil || !strings.Contains(err.Error(), "must not be writable by group or others") {
				t.Fatalf("Load error = %v", err)
			}
			if err := store.Save(path, EmptyConfig()); err == nil || !strings.Contains(err.Error(), "must not be writable by group or others") {
				t.Fatalf("Save error = %v", err)
			}
		})
	}
}
