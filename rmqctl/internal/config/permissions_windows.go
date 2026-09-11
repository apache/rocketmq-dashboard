//go:build windows

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
)

// Windows does not expose POSIX group/other permission bits through os.FileMode.
// The config lives below the user's profile by default and inherits its ACL.
func ensureConfigDirectory(path string) error {
	if err := os.MkdirAll(path, 0o700); err != nil {
		return err
	}
	info, err := os.Stat(path)
	if err != nil {
		return fmt.Errorf("inspect config directory: %w", err)
	}
	if !info.IsDir() {
		return fmt.Errorf("config parent is not a directory: %s", path)
	}
	return nil
}

func secureConfigFile(path string) error {
	// On Windows os.Chmod supports only the owner write bit. Keep the file
	// writable and rely on the inherited user-profile ACL for access control.
	return os.Chmod(path, 0o600)
}

func checkFilePermissions(path string) error {
	info, err := os.Stat(path)
	if err != nil {
		return fmt.Errorf("inspect config file: %w", err)
	}
	if !info.Mode().IsRegular() {
		return fmt.Errorf("config path is not a regular file: %s", path)
	}
	return nil
}
