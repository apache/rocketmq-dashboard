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

import { FetchEventSource, remoteApi } from './remoteApi';

// ---- Mock helpers -----------------------------------------------------------

/**
 * Create a mock stream (plain object with getReader) that yields SSE chunks.
 * Uses TextEncoder lazily (available via setupTests.js polyfill).
 */
function createMockStream(chunks) {
    let index = 0;
    const encoder = new TextEncoder();
    return {
        getReader: () => ({
            read: () => {
                if (index < chunks.length) {
                    const value = encoder.encode(chunks[index++]);
                    return Promise.resolve({ done: false, value });
                }
                return Promise.resolve({ done: true, value: undefined });
            }
        })
    };
}

/**
 * Create a mock fetch that returns a successful response with the given SSE chunks.
 */
function mockFetchSuccess(chunks, status = 200) {
    return jest.fn(() => Promise.resolve({
        ok: status >= 200 && status < 300,
        status,
        statusText: status === 200 ? 'OK' : 'Error',
        body: createMockStream(chunks)
    }));
}

/**
 * Create a mock fetch that rejects with an error.
 */
function mockFetchError(errorMessage) {
    return jest.fn(() => Promise.reject(new Error(errorMessage)));
}

/**
 * Wait for a condition to become true, polling at intervals.
 */
function waitFor(conditionFn, timeoutMs = 3000, intervalMs = 50) {
    return new Promise((resolve, reject) => {
        const start = Date.now();
        const check = () => {
            try {
                if (conditionFn()) return resolve();
            } catch (e) { /* ignore */ }
            if (Date.now() - start > timeoutMs) return reject(new Error('waitFor timeout'));
            setTimeout(check, intervalMs);
        };
        check();
    });
}

/**
 * Flush microtasks and allow async callbacks to run.
 */
function flush(ms = 150) {
    return new Promise(r => setTimeout(r, ms));
}

// ---- Tests ------------------------------------------------------------------

describe('FetchEventSource', () => {
    let originalFetch;

    beforeEach(() => {
        originalFetch = global.fetch;
    });

    afterEach(() => {
        global.fetch = originalFetch;
        jest.restoreAllMocks();
    });

    // ---- Constructor & basic interface ----------------------------------------

    test('calls fetch with POST method and provided body', async () => {
        const mockFetch = mockFetchSuccess(['event: done\ndata: {}\n\n']);
        global.fetch = mockFetch;

        const body = JSON.stringify({ message: 'hello' });
        const es = new FetchEventSource('http://test/api', {
            headers: { 'Content-Type': 'application/json' },
            body
        });

        await flush();

        expect(mockFetch).toHaveBeenCalledTimes(1);
        const [url, options] = mockFetch.mock.calls[0];
        expect(url).toBe('http://test/api');
        expect(options.method).toBe('POST');
        expect(options.body).toBe(body);
        expect(options.headers['Content-Type']).toBe('application/json');
        es.close();
    });

    test('exposes addEventListener, removeEventListener, close, and onerror', () => {
        global.fetch = mockFetchSuccess(['event: done\ndata: {}\n\n']);
        const es = new FetchEventSource('http://test/api', { body: '{}' });

        expect(typeof es.addEventListener).toBe('function');
        expect(typeof es.removeEventListener).toBe('function');
        expect(typeof es.close).toBe('function');
        expect(es.onerror).toBeNull();
        es.close();
    });

    test('readyState starts at 0 (CONNECTING)', () => {
        global.fetch = mockFetchSuccess(['event: done\ndata: {}\n\n']);
        const es = new FetchEventSource('http://test/api', { body: '{}' });
        expect(es.readyState).toBe(0);
        es.close();
    });

    // ---- SSE event parsing ----------------------------------------------------

    test('dispatches named events from SSE stream', async () => {
        const sseData = [
            'event: token\ndata: {"content":"Hello"}\n\n',
            'event: token\ndata: {"content":" World"}\n\n',
            'event: done\ndata: {}\n\n'
        ];
        global.fetch = mockFetchSuccess(sseData);

        const events = [];
        const es = new FetchEventSource('http://test/api', { body: '{}' });
        es.addEventListener('token', (e) => events.push({ name: 'token', data: e.data }));
        es.addEventListener('done', (e) => events.push({ name: 'done', data: e.data }));

        await waitFor(() => events.length >= 3);

        expect(events.length).toBe(3);
        expect(events[0]).toEqual({ name: 'token', data: '{"content":"Hello"}' });
        expect(events[1]).toEqual({ name: 'token', data: '{"content":" World"}' });
        expect(events[2]).toEqual({ name: 'done', data: '{}' });
    });

    test('dispatches tool_call events from SSE stream', async () => {
        const sseData = [
            'event: token\ndata: {"content":"Let me check"}\n\n',
            'event: tool_call\ndata: {"id":"call_1","name":"listTopics","arguments":"{}"}\n\n',
            'event: done\ndata: {}\n\n'
        ];
        global.fetch = mockFetchSuccess(sseData);

        const events = [];
        const es = new FetchEventSource('http://test/api', { body: '{}' });
        es.addEventListener('tool_call', (e) => events.push(e.data));
        es.addEventListener('done', () => events.push('done'));

        await waitFor(() => events.length >= 2);

        expect(events.length).toBe(2);
        expect(events[0]).toBe('{"id":"call_1","name":"listTopics","arguments":"{}"}');
        expect(events[1]).toBe('done');
    });

    test('handles multi-line data fields by joining with newline', async () => {
        const sseData = [
            'event: token\ndata: line1\ndata: line2\n\n',
            'event: done\ndata: {}\n\n'
        ];
        global.fetch = mockFetchSuccess(sseData);

        const events = [];
        const es = new FetchEventSource('http://test/api', { body: '{}' });
        es.addEventListener('token', (e) => events.push(e.data));

        await waitFor(() => events.length >= 1);

        expect(events.length).toBe(1);
        expect(events[0]).toBe('line1\nline2');
    });

    test('ignores comment lines starting with colon', async () => {
        const sseData = [
            ': this is a comment\nevent: token\ndata: {"content":"hi"}\n\n',
            'event: done\ndata: {}\n\n'
        ];
        global.fetch = mockFetchSuccess(sseData);

        const events = [];
        const es = new FetchEventSource('http://test/api', { body: '{}' });
        es.addEventListener('token', (e) => events.push(e.data));

        await waitFor(() => events.length >= 1);

        expect(events.length).toBe(1);
        expect(events[0]).toBe('{"content":"hi"}');
    });

    test('handles events split across multiple chunks', async () => {
        const chunks = [
            'event: to',
            'ken\ndata: {"content":"split"}\n\nevent: done\ndata: {}\n\n'
        ];
        global.fetch = mockFetchSuccess(chunks);

        const events = [];
        const es = new FetchEventSource('http://test/api', { body: '{}' });
        es.addEventListener('token', (e) => events.push(e.data));
        es.addEventListener('done', () => events.push('done'));

        await waitFor(() => events.length >= 2);

        expect(events.length).toBe(2);
        expect(events[0]).toBe('{"content":"split"}');
        expect(events[1]).toBe('done');
    });

    // ---- done event tracking --------------------------------------------------

    test('does not dispatch error when done event is received before stream ends', async () => {
        const sseData = [
            'event: token\ndata: {"content":"hi"}\n\n',
            'event: done\ndata: {}\n\n'
        ];
        global.fetch = mockFetchSuccess(sseData);

        let errorDispatched = false;
        let doneReceived = false;
        const es = new FetchEventSource('http://test/api', { body: '{}' });
        es.addEventListener('error', () => { errorDispatched = true; });
        es.addEventListener('done', () => { doneReceived = true; });

        await waitFor(() => doneReceived);
        await flush(200);

        expect(errorDispatched).toBe(false);
    });

    test('dispatches error when stream ends without done event', async () => {
        const sseData = [
            'event: token\ndata: {"content":"incomplete"}\n\n'
        ];
        global.fetch = mockFetchSuccess(sseData);

        let errorDispatched = false;
        const es = new FetchEventSource('http://test/api', { body: '{}' });
        es.addEventListener('error', () => { errorDispatched = true; });

        await waitFor(() => errorDispatched);

        expect(errorDispatched).toBe(true);
    });

    // ---- HTTP error handling --------------------------------------------------

    test('dispatches error event on HTTP error response', async () => {
        const mockFetch = jest.fn(() => Promise.resolve({
            ok: false,
            status: 400,
            statusText: 'Bad Request',
            body: null
        }));
        global.fetch = mockFetch;

        let errorData = null;
        const es = new FetchEventSource('http://test/api', { body: '{}' });
        es.addEventListener('error', (e) => { errorData = e.data; });

        await waitFor(() => errorData !== null);

        expect(errorData).toBeTruthy();
        const parsed = JSON.parse(errorData);
        expect(parsed.message).toContain('400');
    });

    test('dispatches error event on fetch network failure', async () => {
        global.fetch = mockFetchError('Network error');

        let errorData = null;
        const es = new FetchEventSource('http://test/api', { body: '{}' });
        es.addEventListener('error', (e) => { errorData = e.data; });

        await waitFor(() => errorData !== null);

        expect(errorData).toBeTruthy();
        const parsed = JSON.parse(errorData);
        expect(parsed.message).toContain('Network error');
    });

    // ---- onerror callback -----------------------------------------------------

    test('calls onerror callback when error occurs', async () => {
        global.fetch = mockFetchError('Connection refused');

        let onerrorCalled = false;
        const es = new FetchEventSource('http://test/api', { body: '{}' });
        es.onerror = () => { onerrorCalled = true; };

        await waitFor(() => onerrorCalled);

        expect(onerrorCalled).toBe(true);
    });

    // ---- close / abort --------------------------------------------------------

    test('close() sets readyState to 2 (CLOSED) and aborts fetch', () => {
        let abortSignal = null;
        const mockFetch = jest.fn((url, options) => {
            abortSignal = options.signal;
            return new Promise(() => {});
        });
        global.fetch = mockFetch;

        const es = new FetchEventSource('http://test/api', { body: '{}' });

        expect(es.readyState).toBe(0);
        es.close();
        expect(es.readyState).toBe(2);
        expect(abortSignal.aborted).toBe(true);
    });

    test('close() before stream ends does not dispatch error', async () => {
        // Create a stream that delivers one chunk, then stalls (never resolves second read)
        let readCallCount = 0;
        const stallStream = {
            getReader: () => ({
                read: () => {
                    readCallCount++;
                    if (readCallCount === 1) {
                        const encoder = new TextEncoder();
                        return Promise.resolve({
                            done: false,
                            value: encoder.encode('event: token\ndata: {"content":"start"}\n\n')
                        });
                    }
                    // Stall: return a promise that never resolves
                    return new Promise(() => {});
                }
            })
        };

        global.fetch = jest.fn(() => Promise.resolve({
            ok: true,
            status: 200,
            body: stallStream
        }));

        let errorDispatched = false;
        const es = new FetchEventSource('http://test/api', { body: '{}' });
        es.addEventListener('error', () => { errorDispatched = true; });

        // Wait for the first chunk to be processed
        await flush(200);

        // Close before stream ends — _closed flag should prevent error dispatch
        es.close();
        await flush(200);

        expect(errorDispatched).toBe(false);
    });

    // ---- removeEventListener --------------------------------------------------

    test('removeEventListener stops dispatching to removed listener', async () => {
        const sseData = ['event: done\ndata: {}\n\n'];
        global.fetch = mockFetchSuccess(sseData);

        const calls = [];
        const listener = () => calls.push('called');

        const es = new FetchEventSource('http://test/api', { body: '{}' });
        es.addEventListener('done', listener);
        es.removeEventListener('done', listener);

        await flush();

        expect(calls.length).toBe(0);
    });

    // ---- readyState transitions ------------------------------------------------

    test('readyState transitions to 2 (CLOSED) after done event', async () => {
        const sseData = ['event: done\ndata: {}\n\n'];
        global.fetch = mockFetchSuccess(sseData);

        let doneReceived = false;
        const es = new FetchEventSource('http://test/api', { body: '{}' });
        es.addEventListener('done', () => { doneReceived = true; });

        await waitFor(() => doneReceived);
        await flush(200);

        expect(es.readyState).toBe(2);
    });

    test('readyState is 0 on HTTP error response', async () => {
        global.fetch = jest.fn(() => Promise.resolve({
            ok: false,
            status: 500,
            statusText: 'Internal Server Error',
            body: null
        }));

        let errorReceived = false;
        const es = new FetchEventSource('http://test/api', { body: '{}' });
        es.addEventListener('error', () => { errorReceived = true; });

        await waitFor(() => errorReceived);

        expect(es.readyState).toBe(0);
    });
});

// ---- sendLlmMessageStream tests ---------------------------------------------

describe('sendLlmMessageStream', () => {
    let originalFetch;

    beforeEach(() => {
        originalFetch = global.fetch;
    });

    afterEach(() => {
        global.fetch = originalFetch;
        jest.restoreAllMocks();
    });

    test('constructs POST request with message in body', async () => {
        const mockFetch = mockFetchSuccess(['event: done\ndata: {}\n\n']);
        global.fetch = mockFetch;

        remoteApi.sendLlmMessageStream('List all topics');

        await flush();

        expect(mockFetch).toHaveBeenCalledTimes(1);
        const [url, options] = mockFetch.mock.calls[0];
        expect(options.method).toBe('POST');
        const body = JSON.parse(options.body);
        expect(body.message).toBe('List all topics');
    });

    test('includes cluster in body when provided', async () => {
        const mockFetch = mockFetchSuccess(['event: done\ndata: {}\n\n']);
        global.fetch = mockFetch;

        remoteApi.sendLlmMessageStream('List topics', 'production-cluster');

        await flush();

        const body = JSON.parse(mockFetch.mock.calls[0][1].body);
        expect(body.message).toBe('List topics');
        expect(body.cluster).toBe('production-cluster');
    });

    test('includes history in body when provided and non-empty', async () => {
        const mockFetch = mockFetchSuccess(['event: done\ndata: {}\n\n']);
        global.fetch = mockFetch;

        const history = [
            { role: 'assistant', content: 'I found 3 topics.' },
            { role: 'user', content: 'Show me more details' }
        ];
        remoteApi.sendLlmMessageStream('What about topicA?', 'cluster1', history);

        await flush();

        const body = JSON.parse(mockFetch.mock.calls[0][1].body);
        expect(body.message).toBe('What about topicA?');
        expect(body.cluster).toBe('cluster1');
        expect(body.history).toEqual(history);
    });

    test('omits history when empty array', async () => {
        const mockFetch = mockFetchSuccess(['event: done\ndata: {}\n\n']);
        global.fetch = mockFetch;

        remoteApi.sendLlmMessageStream('Hello', 'cluster1', []);

        await flush();

        const body = JSON.parse(mockFetch.mock.calls[0][1].body);
        expect(body.message).toBe('Hello');
        expect(body).not.toHaveProperty('history');
    });

    test('omits history when null or undefined', async () => {
        const mockFetch = mockFetchSuccess(['event: done\ndata: {}\n\n']);
        global.fetch = mockFetch;

        remoteApi.sendLlmMessageStream('Hello', 'cluster1', null);

        await flush();

        const body = JSON.parse(mockFetch.mock.calls[0][1].body);
        expect(body).not.toHaveProperty('history');
    });

    test('includes model in body when provided', async () => {
        const mockFetch = mockFetchSuccess(['event: done\ndata: {}\n\n']);
        global.fetch = mockFetch;

        remoteApi.sendLlmMessageStream('Hello', 'cluster1', [], 'deepseek-chat');

        await flush();

        const body = JSON.parse(mockFetch.mock.calls[0][1].body);
        expect(body.model).toBe('deepseek-chat');
    });

    test('omits model when not provided', async () => {
        const mockFetch = mockFetchSuccess(['event: done\ndata: {}\n\n']);
        global.fetch = mockFetch;

        remoteApi.sendLlmMessageStream('Hello', 'cluster1', []);

        await flush();

        const body = JSON.parse(mockFetch.mock.calls[0][1].body);
        expect(body).not.toHaveProperty('model');
    });

    test('omits cluster when not provided', async () => {
        const mockFetch = mockFetchSuccess(['event: done\ndata: {}\n\n']);
        global.fetch = mockFetch;

        remoteApi.sendLlmMessageStream('Hello');

        await flush();

        const body = JSON.parse(mockFetch.mock.calls[0][1].body);
        expect(body).not.toHaveProperty('cluster');
    });

    test('sets Content-Type header to application/json', async () => {
        const mockFetch = mockFetchSuccess(['event: done\ndata: {}\n\n']);
        global.fetch = mockFetch;

        remoteApi.sendLlmMessageStream('Hello');

        await flush();

        const options = mockFetch.mock.calls[0][1];
        expect(options.headers['Content-Type']).toBe('application/json');
    });

    test('returns FetchEventSource instance', () => {
        global.fetch = mockFetchSuccess(['event: done\ndata: {}\n\n']);

        const result = remoteApi.sendLlmMessageStream('Hello');

        expect(result).toBeInstanceOf(FetchEventSource);
        expect(typeof result.addEventListener).toBe('function');
        expect(typeof result.close).toBe('function');
    });

    test('uses POST method (not GET) to avoid URL length limits', async () => {
        const mockFetch = mockFetchSuccess(['event: done\ndata: {}\n\n']);
        global.fetch = mockFetch;

        const largeHistory = Array.from({ length: 50 }, (_, i) => ({
            role: i % 2 === 0 ? 'assistant' : 'user',
            content: `Message ${i} with some content to make it longer`
        }));
        remoteApi.sendLlmMessageStream('Follow up', 'cluster1', largeHistory);

        await flush();

        const options = mockFetch.mock.calls[0][1];
        expect(options.method).toBe('POST');
        const body = JSON.parse(options.body);
        expect(body.history.length).toBe(50);
        const url = mockFetch.mock.calls[0][0];
        expect(url).not.toContain('history=');
    });
});