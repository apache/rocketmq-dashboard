/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package config

import (
	"context"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"time"

	"github.com/gofrs/flock"
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

	lock := flock.New(lockPath, flock.SetPermissions(0o600))
	defer func() {
		if closeErr := lock.Close(); closeErr != nil {
			err = errors.Join(err, fmt.Errorf("release config lock: %w", closeErr))
		}
	}()
	waitCtx, cancel := context.WithTimeout(ctx, configLockTimeout)
	defer cancel()
	locked, err := lock.TryLockContext(waitCtx, configLockRetry)
	if err != nil {
		return fmt.Errorf("acquire config lock %s: %w", lockPath, err)
	}
	if !locked {
		return fmt.Errorf("could not acquire config lock %s", lockPath)
	}
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
