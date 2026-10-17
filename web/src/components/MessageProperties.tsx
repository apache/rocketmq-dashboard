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

import { Alert, theme, Typography } from 'antd';
import type { MessageRecord } from '../api/message';
import { useLang } from '../i18n/LangContext';

type MessagePropertiesProps = Pick<MessageRecord, 'properties' | 'propertiesTruncated'>;

export default function MessageProperties({ properties, propertiesTruncated }: MessagePropertiesProps) {
  const { t } = useLang();
  const { token } = theme.useToken();
  const entries = Object.entries(properties ?? {});

  if (entries.length === 0 && !propertiesTruncated) return null;

  return (
    <section aria-label={t('message.properties')} style={{ marginTop: 16 }}>
      <Typography.Title level={5} style={{ marginBottom: 8 }}>
        {t('message.properties')}
      </Typography.Title>
      {propertiesTruncated && (
        <Alert
          type="warning"
          showIcon
          message={t('message.propertiesTruncated')}
          style={{ marginBottom: entries.length > 0 ? 8 : 0 }}
        />
      )}
      {entries.length > 0 && (
        <div style={{ maxHeight: 240, overflowY: 'auto' }}>
          <dl style={{ margin: 0 }}>
            {entries.map(([key, value]) => (
              <div
                key={key}
                style={{
                  display: 'grid',
                  gridTemplateColumns: 'minmax(0, 35%) minmax(0, 1fr)',
                  gap: 12,
                  padding: '8px 0',
                  borderBottom: `1px solid ${token.colorBorderSecondary}`,
                }}
              >
                <dt style={{ fontFamily: token.fontFamilyCode, overflowWrap: 'anywhere' }}>{key}</dt>
                <dd style={{ margin: 0, whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{value}</dd>
              </div>
            ))}
          </dl>
        </div>
      )}
    </section>
  );
}
