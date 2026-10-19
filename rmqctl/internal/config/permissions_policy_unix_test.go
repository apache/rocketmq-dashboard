//go:build !windows

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
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
