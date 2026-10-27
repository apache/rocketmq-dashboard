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
	"container/list"
	"context"
	"errors"
	"sync"
)

var errMCPClientSessionClosed = errors.New("MCP client session is closed")

// sendGate admits concurrent ordinary sends, but grants reconnects exclusively.
// A FIFO queue prevents new sends from starving a reconnect. Its zero value is
// ready to use; waiting needs neither a polling timer nor a helper goroutine.
type sendGate struct {
	mu      sync.Mutex
	readers int
	writer  bool
	waiters list.List
}

type sendGateWaiter struct {
	exclusive bool
	ready     chan struct{}
	granted   bool
}

func sendGateWaitError(ctx context.Context, closed <-chan struct{}) error {
	if err := ctx.Err(); err != nil {
		return err
	}
	select {
	case <-closed:
		return errMCPClientSessionClosed
	default:
		return nil
	}
}

func (gate *sendGate) acquire(ctx context.Context, closed <-chan struct{}, exclusive bool) error {
	gate.mu.Lock()
	if err := sendGateWaitError(ctx, closed); err != nil {
		gate.mu.Unlock()
		return err
	}
	if gate.waiters.Len() == 0 && !gate.writer && (!exclusive || gate.readers == 0) {
		if exclusive {
			gate.writer = true
		} else {
			gate.readers++
		}
		gate.mu.Unlock()
		return nil
	}
	waiter := &sendGateWaiter{exclusive: exclusive, ready: make(chan struct{})}
	element := gate.waiters.PushBack(waiter)
	gate.mu.Unlock()

	select {
	case <-waiter.ready:
	case <-ctx.Done():
	case <-closed:
	}
	gate.mu.Lock()
	defer gate.mu.Unlock()
	// Cancellation can race with a grant. Return the permit in that case rather
	// than leaving a reader or writer behind that blocks every subsequent call.
	if err := sendGateWaitError(ctx, closed); err != nil {
		if waiter.granted {
			gate.releaseLocked(exclusive)
		} else {
			gate.waiters.Remove(element)
			gate.grantLocked()
		}
		return err
	}
	return nil
}

func (gate *sendGate) release(exclusive bool) {
	gate.mu.Lock()
	defer gate.mu.Unlock()
	gate.releaseLocked(exclusive)
}

func (gate *sendGate) releaseLocked(exclusive bool) {
	if exclusive {
		gate.writer = false
	} else {
		gate.readers--
	}
	gate.grantLocked()
}

// Grant an exclusive waiter only after existing readers finish, or admit the
// consecutive readers at the head of the queue as one concurrent batch.
func (gate *sendGate) grantLocked() {
	if gate.writer {
		return
	}
	for element := gate.waiters.Front(); element != nil; element = gate.waiters.Front() {
		waiter := element.Value.(*sendGateWaiter)
		if waiter.exclusive && gate.readers != 0 {
			return
		}
		gate.waiters.Remove(element)
		waiter.granted = true
		if waiter.exclusive {
			gate.writer = true
		} else {
			gate.readers++
		}
		close(waiter.ready)
		if waiter.exclusive {
			return
		}
	}
}
