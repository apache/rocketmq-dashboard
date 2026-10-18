//go:build !windows

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package config

import (
	"fmt"
	"os"
	"path/filepath"
)

func ensureConfigDirectory(path string) error {
	if err := os.MkdirAll(path, 0o700); err != nil {
		return err
	}
	return checkConfigDirectory(path)
}

func checkConfigDirectory(path string) error {
	info, err := os.Stat(path)
	if err != nil {
		return fmt.Errorf("inspect config directory: %w", err)
	}
	if !info.IsDir() {
		return fmt.Errorf("config parent is not a directory: %s", path)
	}
	if info.Mode().Perm()&0o022 != 0 {
		return fmt.Errorf("config directory %s must not be writable by group or others (found %04o)",
			path, info.Mode().Perm())
	}
	return nil
}

func secureConfigFile(path string) error {
	return os.Chmod(path, 0o600)
}

func checkFilePermissions(path string) error {
	if err := checkConfigDirectory(filepath.Dir(path)); err != nil {
		return err
	}
	info, err := os.Stat(path)
	if err != nil {
		return fmt.Errorf("inspect config file: %w", err)
	}
	if !info.Mode().IsRegular() {
		return fmt.Errorf("config path is not a regular file: %s", path)
	}
	if info.Mode().Perm()&0o077 != 0 {
		return fmt.Errorf("config file %s must have permissions no wider than 0600 (found %04o)",
			path, info.Mode().Perm())
	}
	return nil
}
