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

export const thresholdUnits: Record<string, string> = {
  磁盘使用率: '%',
  消费堆积量: '条',
  'TPS 异常': 'TPS',
  'Broker 离线': '个',
  'Proxy 连接数': '个',
};

export function attachThresholdUnit<T extends { metric: string }>(
  values: T,
): T & { thresholdUnit: string } {
  return {
    ...values,
    thresholdUnit: thresholdUnits[values.metric] ?? '',
  };
}
