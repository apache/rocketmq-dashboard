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

/// <reference types="vitest/globals" />
import '@testing-library/jest-dom/vitest';
import { act, cleanup, configure } from '@testing-library/react';
import { unstableSetRender } from 'antd';
import { actDestroy as resetMessage } from 'antd/lib/message';
import { actDestroy as resetNotification } from 'antd/lib/notification';

// 透传 antd 原始渲染，仅保存它返回的卸载函数，让静态 holder 也遵循测试生命周期。
const render = unstableSetRender();
const unmounts = new Set<() => Promise<void>>();
unstableSetRender((node, container) => {
  const unmount = render(node, container);
  const dispose = async () => {
    await unmount();
    unmounts.delete(dispose);
  };
  unmounts.add(dispose);
  return dispose;
});

// 全量并行时 antd 异步渲染可能超过 findBy*/waitFor 默认的 1 秒。
configure({ asyncUtilTimeout: 5000 });

// 测试之间清理本地存储。
beforeEach(() => {
  localStorage.clear();
});

// destroy 冷调用也会创建 root，热调用仅开始退场动画；不能用它代替卸载。
// 先等待组件卸载及其提示队列初始化，再卸载静态 root，取消动画与自动关闭定时器。
afterEach(async () => {
  await act(async () => {
    cleanup();
  });
  await act(async () => {
    await Promise.all([...unmounts].map((unmount) => unmount()));
  });
  // 使用 antd 的测试专用重置入口；lib 与 antd 主入口共用同一组静态实例。
  resetMessage();
  resetNotification();
});
