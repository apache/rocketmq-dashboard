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
package studio

import (
	"context"
	"errors"
	"testing"
	"time"
)

func waitSendGateQueue(t *testing.T, gate *sendGate, count int) {
	t.Helper()
	deadline := time.After(2 * time.Second)
	ticker := time.NewTicker(time.Millisecond)
	defer ticker.Stop()
	for {
		gate.mu.Lock()
		size := gate.waiters.Len()
		gate.mu.Unlock()
		if size == count {
			return
		}
		select {
		case <-deadline:
			t.Fatalf("queued waiters = %d, want %d", size, count)
		case <-ticker.C:
		}
	}
}

func acquireSendGateAsync(gate *sendGate, ctx context.Context, closed <-chan struct{}, exclusive bool) <-chan error {
	result := make(chan error, 1)
	go func() { result <- gate.acquire(ctx, closed, exclusive) }()
	return result
}

func receiveSendGateResult(t *testing.T, result <-chan error, want error) {
	t.Helper()
	select {
	case err := <-result:
		if !errors.Is(err, want) {
			t.Fatalf("acquire error = %v, want %v", err, want)
		}
	case <-time.After(2 * time.Second):
		t.Fatal("gate waiter did not return")
	}
}

func TestSendGateConcurrentReadersAndFIFOReconnects(t *testing.T) {
	var gate sendGate
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	closed := make(chan struct{})
	if err := gate.acquire(ctx, closed, false); err != nil {
		t.Fatal(err)
	}
	// A second ordinary send can overlap the first one.
	if err := gate.acquire(ctx, closed, false); err != nil {
		t.Fatal(err)
	}
	writer1 := acquireSendGateAsync(&gate, ctx, closed, true)
	waitSendGateQueue(t, &gate, 1)
	reader1 := acquireSendGateAsync(&gate, ctx, closed, false)
	waitSendGateQueue(t, &gate, 2)
	reader2 := acquireSendGateAsync(&gate, ctx, closed, false)
	waitSendGateQueue(t, &gate, 3)
	writer2 := acquireSendGateAsync(&gate, ctx, closed, true)
	waitSendGateQueue(t, &gate, 4)
	lateReader := acquireSendGateAsync(&gate, ctx, closed, false)
	waitSendGateQueue(t, &gate, 5)
	gate.release(false)
	select {
	case <-writer1:
		t.Fatal("writer overlapped a remaining reader")
	default:
	}
	gate.release(false)
	receiveSendGateResult(t, writer1, nil)
	select {
	case <-reader1:
		t.Fatal("reader overtook reconnect")
	default:
	}
	gate.release(true)
	receiveSendGateResult(t, reader1, nil)
	receiveSendGateResult(t, reader2, nil)
	gate.release(false)
	select {
	case <-writer2:
		t.Fatal("writer overlapped reader batch")
	default:
	}
	gate.release(false)
	receiveSendGateResult(t, writer2, nil)
	select {
	case <-lateReader:
		t.Fatal("late reader overtook queued reconnect")
	default:
	}
	gate.release(true)
	receiveSendGateResult(t, lateReader, nil)
	gate.release(false)
}

func TestSendGateCancelledWriterUnblocksQueuedReaders(t *testing.T) {
	var gate sendGate
	closed := make(chan struct{})
	if err := gate.acquire(context.Background(), closed, false); err != nil {
		t.Fatal(err)
	}
	writerCtx, cancel := context.WithCancel(context.Background())
	writer := acquireSendGateAsync(&gate, writerCtx, closed, true)
	waitSendGateQueue(t, &gate, 1)
	readerCtx, readerCancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer readerCancel()
	reader := acquireSendGateAsync(&gate, readerCtx, closed, false)
	waitSendGateQueue(t, &gate, 2)
	cancel()
	receiveSendGateResult(t, writer, context.Canceled)
	// The original reader is still active: removal must wake compatible waiters.
	receiveSendGateResult(t, reader, nil)
	gate.release(false)
	gate.release(false)
	waitSendGateQueue(t, &gate, 0)
}

func TestSendGateCancelledGrantReturnsPermit(t *testing.T) {
	for _, exclusive := range []bool{false, true} {
		name := "reader"
		if exclusive {
			name = "writer"
		}
		t.Run(name, func(t *testing.T) {
			for i := 0; i < 100; i++ {
				var gate sendGate
				closed := make(chan struct{})
				if err := gate.acquire(context.Background(), closed, true); err != nil {
					t.Fatal(err)
				}
				ctx, cancel := context.WithCancel(context.Background())
				result := acquireSendGateAsync(&gate, ctx, closed, exclusive)
				waitSendGateQueue(t, &gate, 1)
				// Grant the permit, but cancel before the waiter can finish acquiring it.
				gate.mu.Lock()
				gate.releaseLocked(true)
				cancel()
				gate.mu.Unlock()
				receiveSendGateResult(t, result, context.Canceled)
				nextCtx, nextCancel := context.WithTimeout(context.Background(), time.Second)
				err := gate.acquire(nextCtx, closed, true)
				nextCancel()
				if err != nil {
					t.Fatalf("iteration %d leaked a granted permit: %v", i, err)
				}
				gate.release(true)
			}
		})
	}
}

func TestSendGateCloseWakesAllWaiters(t *testing.T) {
	var gate sendGate
	closed := make(chan struct{})
	if err := gate.acquire(context.Background(), closed, true); err != nil {
		t.Fatal(err)
	}
	reader := acquireSendGateAsync(&gate, context.Background(), closed, false)
	waitSendGateQueue(t, &gate, 1)
	writer := acquireSendGateAsync(&gate, context.Background(), closed, true)
	waitSendGateQueue(t, &gate, 2)
	close(closed)
	receiveSendGateResult(t, reader, errMCPClientSessionClosed)
	receiveSendGateResult(t, writer, errMCPClientSessionClosed)
	waitSendGateQueue(t, &gate, 0)
	gate.release(true)
	if err := gate.acquire(context.Background(), closed, false); !errors.Is(err, errMCPClientSessionClosed) {
		t.Fatalf("post-close acquire = %v", err)
	}
}

func TestSendGatePreCancelledContextNeverAcquires(t *testing.T) {
	var gate sendGate
	closed := make(chan struct{})
	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	for _, exclusive := range []bool{false, true} {
		if err := gate.acquire(ctx, closed, exclusive); !errors.Is(err, context.Canceled) {
			t.Fatalf("acquire = %v", err)
		}
	}
	if gate.readers != 0 || gate.writer || gate.waiters.Len() != 0 {
		t.Fatal("cancelled acquisition changed gate state")
	}
}
