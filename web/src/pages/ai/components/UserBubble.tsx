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

import { memo } from 'react';
import { Flex, theme } from 'antd';
import useAuthStore from '../../../stores/authStore';
import { formatUtcDateTime } from '../../../utils/format';

/**
 * The operator's own message, right-aligned, with the operator's avatar on the right — the same
 * initial-on-gradient mark the top bar shows, so the transcript reads as a two-sided dialogue.
 *
 * Plain text on purpose: what was sent is what the run was admitted with, so re-interpreting it as
 * Markdown would let a prompt containing `#` or `*` render as something the user never typed.
 *
 * `createdAt` is the persisted `rmq_ai_event.gmt_create`, which the backend writes from
 * `Clock.systemUTC()` as a `LocalDateTime` with NO offset — so it goes through `formatUtcDateTime`,
 * the same helper every other timestamp in Studio uses. Parsing it as a plain `Date` would silently
 * shift every history entry by the viewer's UTC offset.
 */

export interface UserBubbleProps {
  text: string;
  createdAt?: string;
}

const UserBubble = ({ text, createdAt }: UserBubbleProps) => {
  const { token } = theme.useToken();
  const username = useAuthStore((state) => state.user);

  return (
    <Flex justify="flex-end" align="flex-start" gap={12} style={{ marginBottom: 16 }}>
      <div
        className="ai-user-bubble"
        style={{
          maxWidth: '70%',
          padding: '10px 16px',
          background: token.colorPrimaryBg,
          color: token.colorText,
          border: `1px solid ${token.colorPrimaryBorder}`,
          borderRadius: 16,
          borderTopRightRadius: 4,
          lineHeight: 1.5,
          fontSize: 14,
          whiteSpace: 'pre-wrap',
          overflowWrap: 'anywhere',
        }}
      >
        {text}
        {createdAt && (
          <div
            style={{
              marginTop: 4,
              color: token.colorTextTertiary,
              fontSize: 14,
              textAlign: 'right',
            }}
          >
            {formatUtcDateTime(createdAt)}
          </div>
        )}
      </div>
      <div
        data-testid="ai-bubble-user-avatar"
        title={username ?? undefined}
        aria-hidden
        style={{
          width: 36,
          height: 36,
          borderRadius: '50%',
          background: 'linear-gradient(135deg, #7c3aed, #d946ef)',
          flexShrink: 0,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          color: '#ffffff',
          fontSize: 16,
          fontWeight: 600,
          userSelect: 'none',
        }}
      >
        {(username ?? 'U').charAt(0).toUpperCase()}
      </div>
    </Flex>
  );
};

export default memo(UserBubble);
