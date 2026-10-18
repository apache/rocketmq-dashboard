/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import { useEffect, useState } from 'react';
import { Alert, Button, Flex, Select, Typography } from 'antd';
import { getConversation } from '../../../api/aiConversations';
import type { Instance } from '../../../api/instance';
import { listInstances } from '../../../services/instanceService';
import { useLang } from '../../../i18n/LangContext';

interface Props {
  conversationId?: number | null;
  value: string;
  onChange: (value: string) => void;
  disabled?: boolean;
}

export default function ConversationTarget({
  conversationId = null,
  value,
  onChange,
  disabled = false,
}: Props) {
  const { t } = useLang();
  const [revision, setRevision] = useState(0);
  const [loaded, setLoaded] = useState<{
    id: number | null;
    revision: number;
    instances: Instance[];
    binding: string | null;
    error: boolean;
  } | null>(null);
  useEffect(() => {
    if (disabled) return;
    let cancelled = false;
    void (async () => {
      try {
        const detail = conversationId === null ? null : await getConversation(conversationId);
        const instances = conversationId === null ? await listInstances() : [];
        if (!cancelled)
          setLoaded({
            id: conversationId,
            revision,
            instances,
            binding: detail?.instanceId ?? null,
            error: false,
          });
      } catch {
        if (!cancelled)
          setLoaded({ id: conversationId, revision, instances: [], binding: null, error: true });
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [conversationId, disabled, revision]);

  const current =
    !disabled && loaded?.id === conversationId && loaded.revision === revision ? loaded : null;
  return (
    <Flex vertical gap={8} style={{ marginTop: 12 }}>
      <Flex gap={8} align="center" wrap>
        <Typography.Text>{t('ai.target.label')}</Typography.Text>
        {conversationId === null ? (
          <Select
            aria-label={t('ai.target.label')}
            value={value}
            onChange={onChange}
            disabled={disabled}
            loading={!disabled && current === null}
            style={{ minWidth: 220, maxWidth: '100%' }}
            options={[
              { value: '', label: t('ai.target.plainChat') },
              ...(current?.instances ?? []).map((instance) => ({
                value: instance.name,
                label: instance.name,
              })),
            ]}
          />
        ) : (
          <Typography.Text data-testid="ai-conversation-target">
            {current === null
              ? t('common.loading')
              : current.error
                ? t('ai.target.unknown')
                : current.binding || t('ai.target.plainChat')}
          </Typography.Text>
        )}
      </Flex>
      {current?.error && (
        <Alert
          type="error"
          showIcon
          message={t('ai.target.loadFailed')}
          action={
            <Button size="small" onClick={() => setRevision((previous) => previous + 1)}>
              {t('common.retry')}
            </Button>
          }
        />
      )}
    </Flex>
  );
}
