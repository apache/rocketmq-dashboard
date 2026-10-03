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
 * Sentinel the backend reports when the consumer connection inventory could not be
 * established (instance discovery or proxy lookup failure), as opposed to a confirmed
 * zero online clients.
 */
export const UNKNOWN_ONLINE_INSTANCES = -1;

export const isOnlineInstancesAvailable = (value: number | null | undefined): value is number =>
  typeof value === 'number' && Number.isFinite(value) && value >= 0;

export const formatOnlineInstances = (
  value: number | null | undefined,
  unavailableLabel: string = String(UNKNOWN_ONLINE_INSTANCES),
): string => (isOnlineInstancesAvailable(value) ? value.toLocaleString() : unavailableLabel);

/** Sort key that pushes unavailable connection counts to the end of an ascending list. */
export const onlineInstancesSortValue = (value: number | null | undefined): number =>
  isOnlineInstancesAvailable(value) ? value : Number.MAX_SAFE_INTEGER;
