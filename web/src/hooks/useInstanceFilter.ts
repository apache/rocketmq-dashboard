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

import { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { listInstances } from '../services/instanceService';
import type { Instance } from '../api/instance';

const INSTANCE_SCOPED_PATH = /^\/instance\/([^/]+)\/(topic|consumer|message|acl|dlq)$/;
const STATIC_SECTION_PATH = /^\/instance\/(topic|consumer|message|acl|dlq)$/;

/**
 * 实例维度页面的公共筛选逻辑：从 /instance/:instanceId/<section> 路由解析当前实例，
 * 无实例参数时重定向到第一个实例；实例列表加载失败时降级为不过滤。
 */
export function useInstanceFilter() {
  const navigate = useNavigate();
  const { pathname } = useLocation();

  const scopedMatch = pathname.match(INSTANCE_SCOPED_PATH);
  const staticMatch = pathname.match(STATIC_SECTION_PATH);
  const routeInstanceId = scopedMatch?.[1];
  const section = scopedMatch?.[2] ?? staticMatch?.[1] ?? 'topic';

  const [instances, setInstances] = useState<Instance[]>([]);

  useEffect(() => {
    let cancelled = false;
    void listInstances()
      .then((nextInstances) => {
        if (cancelled) return;
        setInstances(nextInstances);
        if (!routeInstanceId && nextInstances.length > 0) {
          navigate(`/instance/${nextInstances[0].id}/${section}`, { replace: true });
        }
      })
      .catch(() => {
        // 实例列表加载失败时不做实例过滤，保持页面数据可用
      });
    return () => {
      cancelled = true;
    };
  }, [navigate, routeInstanceId, section]);

  const selectedInstanceId =
    routeInstanceId && instances.some((instance) => instance.id === routeInstanceId)
      ? routeInstanceId
      : (instances[0]?.id ?? '');
  const selectedInstance = instances.find((instance) => instance.id === selectedInstanceId);

  const selectInstance = (id: string) => {
    navigate(`/instance/${id}/${section}`);
  };

  const instanceOptions = instances.map((instance) => ({
    value: instance.id,
    label: instance.name,
  }));

  return {
    instances,
    selectedInstanceId,
    selectedInstance,
    selectInstance,
    instanceOptions,
  };
}
