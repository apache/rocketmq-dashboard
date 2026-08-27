/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 * You may obtain a copy of the License at
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
 * Sentinel the backend (ConsumerLagResolver.UNKNOWN) reports when a consumer lag
 * cannot be determined, e.g. RocketMQ 5.0 gRPC consumers without proxy stats.
 */
export const UNKNOWN_LAG = -1;

export const isLagAvailable = (lag: number | null | undefined): lag is number =>
  typeof lag === 'number' && Number.isFinite(lag) && lag >= 0;

export const formatLag = (
  lag: number | null | undefined,
  unavailableLabel: string = String(UNKNOWN_LAG),
): string => (isLagAvailable(lag) ? lag.toLocaleString() : unavailableLabel);

/** Sort key that pushes unknown lags to the end of an ascending list. */
export const lagSortValue = (lag: number | null | undefined): number =>
  isLagAvailable(lag) ? lag : Number.MAX_SAFE_INTEGER;
