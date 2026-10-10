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

/**
 * Normalize a single numeric sample: a missing or non-finite value (null, undefined,
 * NaN, +/-Infinity) reads as 0.
 *
 * Shared by the traffic-insights aggregation and the sparkline chart family
 * (`MiniBar`, `MiniLine`), so a bad sample can never poison a `Math.max`/`Math.min`
 * scale computed over a series.
 */
export const finiteNumber = (value: number | null | undefined): number =>
  typeof value === 'number' && Number.isFinite(value) ? value : 0;

/**
 * Clamp a single numeric sample to the non-negative range the charts render, after
 * normalizing it with `finiteNumber`.
 */
export const nonNegativeNumber = (value: number | null | undefined): number =>
  Math.max(0, finiteNumber(value));
