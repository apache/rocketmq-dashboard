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

/*
 * Compile-time configuration.
 */

/**
 * @deprecated Use `isMockMode()` from `./services/dataMode` instead.
 * The data mode is now a runtime toggle persisted in localStorage via Zustand.
 * This export is kept temporarily for backward compatibility and will be removed.
 */
export const USE_MOCK = false;

/** API prefix for browser requests. Defaults to the reverse-proxy friendly `/api`. */
export const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/$/, '');
