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
	"context"
	"errors"
	"io"
	"os"
	"strings"
	"testing"
	"time"

	"github.com/apache/rocketmq-dashboard/rmqctl/internal/types"
)

// charDeviceReader wraps a pipe with a character-device mode bit so defaultConfirm treats it as
// an interactive prompt without touching the test process's real stdin.
type charDeviceReader struct{ r io.Reader }

func (c charDeviceReader) Read(p []byte) (int, error) { return c.r.Read(p) }

// Stat pretends to be a character device so the TTY gate passes.
func (c charDeviceReader) Stat() (os.FileInfo, error) {
	return charDeviceFileInfo{}, nil
}

type charDeviceFileInfo struct{}

func (charDeviceFileInfo) Name() string       { return "stdin" }
func (charDeviceFileInfo) Size() int64        { return 0 }
func (charDeviceFileInfo) Mode() os.FileMode  { return os.ModeCharDevice | 0o600 }
func (charDeviceFileInfo) ModTime() time.Time { return time.Time{} }
func (charDeviceFileInfo) IsDir() bool        { return false }
func (charDeviceFileInfo) Sys() any           { return nil }

func TestDefaultConfirmCanceledContextWinsOverTypedYes(t *testing.T) {
	pipeR, pipeW, err := os.Pipe()
	if err != nil {
		t.Fatal(err)
	}
	defer pipeR.Close()
	defer pipeW.Close()
	// The operator's "yes" is already in the pipe: an interrupt must still abort the prompt,
	// because running the mutation with a canceled context would only fail later and mislead.
	if _, err := pipeW.WriteString("yes\n"); err != nil {
		t.Fatal(err)
	}
	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	err = defaultConfirm(ctx, charDeviceReader{r: pipeR}, io.Discard, "rmq.consumer.delete", "L3", "http://studio")
	var cliErr *types.CLIError
	if !errors.As(err, &cliErr) || cliErr.Code != types.CodeCanceled {
		t.Fatalf("defaultConfirm with canceled context: got %v, want CANCELED", err)
	}
}

func TestDefaultConfirmInterruptDuringReadAborts(t *testing.T) {
	pipeR, pipeW, err := os.Pipe()
	if err != nil {
		t.Fatal(err)
	}
	defer pipeR.Close()
	defer pipeW.Close()
	ctx, cancel := context.WithCancel(context.Background())
	go func() {
		// Simulate Ctrl-C arriving while the prompt waits on an empty pipe.
		time.Sleep(50 * time.Millisecond)
		cancel()
	}()
	err = defaultConfirm(ctx, charDeviceReader{r: pipeR}, io.Discard, "rmq.consumer.delete", "L3", "http://studio")
	cancel()
	var cliErr *types.CLIError
	if !errors.As(err, &cliErr) || cliErr.Code != types.CodeCanceled {
		t.Fatalf("defaultConfirm interrupted mid-read: got %v, want CANCELED", err)
	}
}

func TestDefaultConfirmAffirmativeProceeds(t *testing.T) {
	pipeR, pipeW, err := os.Pipe()
	if err != nil {
		t.Fatal(err)
	}
	defer pipeR.Close()
	defer pipeW.Close()
	if _, err := pipeW.WriteString("yes\n"); err != nil {
		t.Fatal(err)
	}
	if err := defaultConfirm(context.Background(), charDeviceReader{r: pipeR}, io.Discard, "rmq.consumer.delete", "L3", "http://studio"); err != nil {
		t.Fatalf("defaultConfirm with yes: got %v, want nil", err)
	}
}

func TestDefaultConfirmNonTTYRejected(t *testing.T) {
	err := defaultConfirm(context.Background(), strings.NewReader("yes\n"), io.Discard, "rmq.consumer.delete", "L3", "http://studio")
	var cliErr *types.CLIError
	if !errors.As(err, &cliErr) || cliErr.Code != types.CodeCommandFailed {
		t.Fatalf("defaultConfirm on non-chardevice stdin: got %v, want COMMAND_FAILED rejection", err)
	}
}
