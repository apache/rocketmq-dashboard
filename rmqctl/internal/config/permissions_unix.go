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
)

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
	if info.Mode().Perm() != 0o700 {
		return fmt.Errorf("config directory %s must have permissions 0700 (found %04o)",
			path, info.Mode().Perm())
	}
	return nil
}

func secureConfigFile(path string) error {
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
	if info.Mode().Perm()&0o077 != 0 {
		return fmt.Errorf("config file %s must have permissions no wider than 0600 (found %04o)",
			path, info.Mode().Perm())
	}
	return nil
}
