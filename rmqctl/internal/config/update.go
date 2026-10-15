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
	"context"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"time"
)

const configLockTimeout = 30 * time.Second
const configLockRetry = 25 * time.Millisecond

// Update serializes a local read-modify-save transaction with other Update
// callers. Save remains a whole-document replacement and does not take this
// lock. The persistent sidecar must not be removed: renaming the config or
// deleting a held lock file would split cooperating writers across inodes.
func (s Store) Update(ctx context.Context, path string, mutate func(*Config) error) (err error) {
	if err := ctx.Err(); err != nil {
		return err
	}
	absolute, err := filepath.Abs(path)
	if err != nil {
		return err
	}
	directory := filepath.Dir(absolute)
	if err := ensureConfigDirectory(directory); err != nil {
		return err
	}
	directory, err = filepath.EvalSymlinks(directory)
	if err != nil {
		return fmt.Errorf("resolve config directory: %w", err)
	}
	path = filepath.Join(directory, filepath.Base(absolute))
	lockPath := path + ".lock"
	// A sidecar is a regular, private file, never a link to another document.
	if info, statErr := os.Lstat(lockPath); statErr == nil {
		if !info.Mode().IsRegular() {
			return fmt.Errorf("config lock is not a regular file: %s", lockPath)
		}
		if err := checkFilePermissions(lockPath); err != nil {
			return err
		}
	} else if !errors.Is(statErr, os.ErrNotExist) {
		return statErr
	}

	waitCtx, cancel := context.WithTimeout(ctx, configLockTimeout)
	defer cancel()
	lock, err := acquireConfigLock(waitCtx, lockPath)
	if err != nil {
		return fmt.Errorf("acquire config lock %s: %w", lockPath, err)
	}
	defer func() {
		if closeErr := lock.Close(); closeErr != nil {
			err = errors.Join(err, fmt.Errorf("release config lock: %w", closeErr))
		}
	}()
	if err := checkFilePermissions(lockPath); err != nil {
		return err
	}
	if err := ctx.Err(); err != nil {
		return err
	}
	cfg, err := s.Load(path)
	if err != nil {
		return err
	}
	if err := mutate(&cfg); err != nil {
		return err
	}
	if err := ctx.Err(); err != nil {
		return err
	}
	return s.Save(path, cfg)
}
