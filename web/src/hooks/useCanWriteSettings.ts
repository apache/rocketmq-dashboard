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

import useAuthStore from '../stores/authStore';

/**
 * 设置类写操作的角色闸门：当前登录者能否写设置。
 *
 * Mirrors the server policy: the settings writes (POST /api/settings/general/save, the LLM
 * configuration POST, and the data source / cloud credential mutations) are admin-only, and the
 * server also hands redacted secret values to readers. Without a signed-in studio user (auth
 * disabled) the caller counts as system, so writes stay allowed — that fallback is deliberate:
 * dropping it would lock out every deployment that runs without studio authentication.
 *
 * 四个设置 tab 共用这一份派生，避免权限口径在复制中分叉。
 */
export function useCanWriteSettings(): boolean {
  const authUserId = useAuthStore((state) => state.userId);
  const authAdmin = useAuthStore((state) => state.admin);
  return !authUserId || authAdmin === true;
}
