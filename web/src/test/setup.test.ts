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

import { act, renderHook } from '@testing-library/react';
import { message, notification } from 'antd';
import { createElement, useEffect } from 'react';
import { afterAll, beforeAll, describe, expect, it, vi } from 'vitest';

const realSetTimeout = globalThis.setTimeout;

// 故意跨用例检查全局 afterEach 的结果，不能在断言前主动清理或用 act 冲掉遗留任务。
describe.sequential('全局提示清理生命周期', () => {
  const errors = vi.spyOn(console, 'error');
  const warnings = vi.spyOn(console, 'warn');
  const fragments = vi.spyOn(document, 'createDocumentFragment');
  const onClose = vi.fn();
  const onUnmount = vi.fn();

  function Notice({ text }: { text: string }) {
    useEffect(() => () => onUnmount(), []);
    return text;
  }

  beforeAll(() => {
    // 保留真实微任务与 React 调度器，仅记录提示定时器和动画帧。
    vi.useFakeTimers({
      toFake: [
        'setTimeout',
        'clearTimeout',
        'setInterval',
        'clearInterval',
        'requestAnimationFrame',
        'cancelAnimationFrame',
      ],
    });
  });

  function expectClean() {
    expect(document.querySelector('.ant-message, .ant-notification')).toBeNull();
    expect(vi.getTimerCount()).toBe(0);
    // spy 默认透传日志；任何警告仍会打印并导致回归失败。
    expect(errors).not.toHaveBeenCalled();
    expect(warnings).not.toHaveBeenCalled();
    expect(onClose).not.toHaveBeenCalled();
  }

  async function expectSettled() {
    expectClean();
    // 给微任务和真实事件循环一次运行机会，确认不是仅在清理返回瞬间为空。
    await new Promise<void>((resolve) => realSetTimeout(resolve, 0));
    await vi.advanceTimersByTimeAsync(10_000);
    expectClean();
  }

  afterAll(async () => {
    try {
      await expectSettled();
    } finally {
      vi.useRealTimers();
      errors.mockRestore();
      warnings.mockRestore();
      fragments.mockRestore();
    }
  });

  it('coldCleanupWithoutOpeningNoticesTest', () => {
    expectClean();
  });

  it('coldCleanupLeavesNoAsyncWorkTest', async () => {
    await expectSettled();
    expect(fragments).not.toHaveBeenCalled();
  });

  it('opensTimedNoticesForGlobalCleanupTest', async () => {
    await act(async () => {
      message.info({
        content: createElement(Notice, { text: '定时消息' }),
        duration: 3,
        onClose,
      });
      notification.open({
        message: createElement(Notice, { text: '定时通知' }),
        duration: 3,
        onClose,
      });
    });
    expect(document.querySelector('.ant-message')).not.toBeNull();
    expect(document.querySelector('.ant-notification')).not.toBeNull();
    expect(vi.getTimerCount()).toBeGreaterThan(0);
  });

  it('timedCleanupLeavesNoAsyncWorkAndSupportsReuseTest', async () => {
    await expectSettled();
    expect(onUnmount).toHaveBeenCalledTimes(2);
    await act(async () => {
      message.loading({ content: '常驻消息', duration: 0, onClose });
      notification.open({ message: '常驻通知', duration: 0, onClose });
    });
    expect(document.body).toHaveTextContent('常驻消息');
    expect(document.body).toHaveTextContent('常驻通知');
  });

  it('cleansNoticesCreatedDuringComponentUnmountTest', async () => {
    await expectSettled();
    renderHook(() =>
      useEffect(
        () => () => {
          message.info({ content: '卸载消息', duration: 3, onClose });
          notification.open({ message: '卸载通知', duration: 3, onClose });
        },
        [],
      ),
    );
  });

  it('unmountCleanupLeavesNoAsyncWorkTest', async () => {
    await expectSettled();
  });
});
