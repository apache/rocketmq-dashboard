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

import { afterEach, describe, expect, it, vi } from 'vitest';
import { chatStream } from './ai';

const encoder = new TextEncoder();

function streamResponse(chunks: string[]): Response {
  const body = new ReadableStream<Uint8Array>({
    start(controller) {
      chunks.forEach((chunk) => controller.enqueue(encoder.encode(chunk)));
      controller.close();
    },
  });
  return new Response(body, { status: 200 });
}

describe('AI chat SSE stream', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('reassembles an event split across network chunks', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(
          streamResponse([
            'event: message\r\ndata: {"text":"hel',
            'lo"}\r\n\r\nevent: done\r\ndata: [DONE]\r\n\r\n',
          ]),
        ),
    );
    vi.stubGlobal('localStorage', { getItem: vi.fn().mockReturnValue('token') });
    const chunks: string[] = [];

    await chatStream({ message: 'hello', mode: 'chat', model: 'stub' }, (text) =>
      chunks.push(text),
    );

    expect(chunks).toEqual(['hello']);
  });

  it('dispatches multiple events delivered in one network chunk', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(
          streamResponse([
            'data: {"content":"first"}\n\ndata: {"content":"second"}\n\ndata: [DONE]\n\n',
          ]),
        ),
    );
    vi.stubGlobal('localStorage', { getItem: vi.fn().mockReturnValue(null) });
    const chunks: string[] = [];

    await chatStream({ message: 'hello', mode: 'chat', model: 'stub' }, (text) =>
      chunks.push(text),
    );

    expect(chunks).toEqual(['first', 'second']);
  });

  it('supports multiline and raw SSE data at end of stream', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(streamResponse(['data: {"content":\ndata: "hello"}\n\ndata: raw text'])),
    );
    vi.stubGlobal('localStorage', { getItem: vi.fn().mockReturnValue(null) });
    const chunks: string[] = [];

    await chatStream({ message: 'hello', mode: 'chat', model: 'stub' }, (text) =>
      chunks.push(text),
    );

    expect(chunks).toEqual(['hello', 'raw text']);
  });
});
