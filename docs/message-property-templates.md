<!--
Licensed to the Apache Software Foundation (ASF) under one or more
contributor license agreements.  See the NOTICE file distributed with
this work for additional information regarding copyright ownership.
The ASF licenses this file to You under the Apache License, Version 2.0
(the "License"); you may not use this file except in compliance with
the License.  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

# 消息属性模板

Topic 发送弹窗支持在自定义属性区域套用常见属性模板。模板用于减少手动发送测试消息、
回放消息或排查业务链路时的重复录入工作，同时避免把已有的操作者输入覆盖掉。

## 使用位置

1. 进入 **Topic 管理** 页面。
2. 在目标 Topic 的操作列点击 **发送**。
3. 在 **自定义属性（可选）** 区域选择 **属性模板**。
4. 点击 **套用模板**。

模板同时支持两种录入方式：

- **逐条录入**：模板会追加为多行 `key` / `value` 表单项。
- **批量粘贴**：模板会追加为多行 `key=value` 文本。

如果当前输入中已经存在同名属性，模板会跳过该属性，只追加缺失项。这一规则对表单模式
和文本模式一致。

## 模板列表

### Trace context

用于给手动发送的测试消息附加链路排查上下文。

| 属性名    | 默认值               | 用途                               |
| --------- | -------------------- | ---------------------------------- |
| `traceId` | `trace-${timestamp}` | 跨服务日志或追踪系统的关联标识     |
| `spanId`  | `studio-send`        | 标识消息来自 Studio 的手动发送动作 |
| `source`  | `rocketmq-studio`    | 标识消息生产来源                   |

适合场景：

- 验证消费者日志是否能按 trace id 检索。
- 排查一次手动发送是否被下游链路完整消费。
- 在测试环境中区分 Studio 手动流量与应用真实流量。

### Tenant routing

用于多租户或多地域消费链路的冒烟测试。

| 属性名        | 默认值        | 用途     |
| ------------- | ------------- | -------- |
| `tenantId`    | `tenant-demo` | 租户标识 |
| `region`      | `cn-hangzhou` | 地域提示 |
| `environment` | `staging`     | 环境标记 |

适合场景：

- 消费端根据租户或地域分流。
- 验证测试消息不会混入生产流量。
- 排查跨地域路由规则是否按预期读取消息属性。

### Retry audit

用于手动重试、补偿或回放消息时保留操作上下文。

| 属性名        | 默认值            | 用途                       |
| ------------- | ----------------- | -------------------------- |
| `retryReason` | `manual-replay`   | 重试或回放原因             |
| `operator`    | `rocketmq-studio` | 发起重试的工具或操作者来源 |
| `requestedAt` | `${isoTimestamp}` | 套用模板时的 ISO 时间      |

适合场景：

- 标记某条消息是人工补偿而非业务系统自动产生。
- 消费端需要区分正常消息与重放消息。
- 审计人工重试操作时需要请求时间。

### Order diagnostics

用于订单类消息链路排查，提供稳定的业务键和分片提示。

| 属性名         | 默认值               | 用途               |
| -------------- | -------------------- | ------------------ |
| `orderId`      | `order-${timestamp}` | 订单业务标识       |
| `businessType` | `order-created`      | 业务事件类型       |
| `shardHint`    | `order`              | 诊断或消费分片提示 |

适合场景：

- 验证订单创建事件的消费者处理链路。
- 使用业务键在日志、审计或消息查询中关联一次测试。
- 检查消费者侧分片或分组逻辑。

## 占位符

模板值支持以下时间占位符：

| 占位符            | 示例                       | 说明                  |
| ----------------- | -------------------------- | --------------------- |
| `${timestamp}`    | `1790253296789`            | 当前时间的毫秒时间戳  |
| `${isoTimestamp}` | `2026-09-16T12:34:56.789Z` | 当前时间的 ISO 字符串 |

占位符在点击 **套用模板** 时展开。再次点击同一模板时，因为同名属性已经存在，不会重新
生成新的时间值，也不会覆盖之前生成的值。

## 去重规则

模板去重以属性名为准：

- 表单模式会忽略空行，再检查已有 `key`。
- 文本模式会复用批量属性解析逻辑，只把可解析的 `key=value` 行纳入去重。
- 无法解析的已有文本会原样保留，模板仍会追加其它缺失属性。
- 属性名大小写保持用户输入，不做大小写折叠。

示例：

```text
tenantId=tenant-a
region=cn-shanghai
```

在批量粘贴模式套用 `Tenant routing` 后，结果为：

```text
tenantId=tenant-a
region=cn-shanghai
environment=staging
```

`tenantId` 和 `region` 被保留，只有缺失的 `environment` 被追加。

## 发送前预检

套用模板只负责生成属性输入，不绕过现有发送前预检。发送前仍会检查：

- 消息体是否为空。
- 属性名是否重复。
- 属性名和属性值是否符合当前解析规则。
- 属性大小是否进入预警范围。

因此，模板不会改变 Topic 发送接口的请求结构。最终发送到后端的 `properties` 仍是普通
键值对象，由现有预检和归一化逻辑生成。
