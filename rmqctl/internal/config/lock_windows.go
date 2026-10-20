//go:build windows

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
	"os"
	"syscall"
	"unsafe"
)

var lockFileEx = syscall.NewLazyDLL("kernel32.dll").NewProc("LockFileEx")

func tryConfigLock(file *os.File) (bool, error) {
	// The standard syscall package does not wrap LockFileEx. Call the Windows
	// API directly without adding a module. Lock one byte, even on an empty
	// sidecar; every writer uses this same exclusive, nonblocking range.
	var overlapped syscall.Overlapped
	const lockfileExclusiveLock = 0x2
	const lockfileFailImmediately = 0x1
	result, _, callErr := lockFileEx.Call(file.Fd(), lockfileExclusiveLock|lockfileFailImmediately,
		0, 1, 0, uintptr(unsafe.Pointer(&overlapped)))
	if result != 0 {
		return true, nil
	}
	const errorLockViolation syscall.Errno = 33
	if callErr == errorLockViolation {
		return false, nil
	}
	return false, callErr
}
