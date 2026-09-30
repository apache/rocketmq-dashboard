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
import { Alert, Flex, Tag, theme } from 'antd';
import type { ErrorBlock as ErrorBlockData } from '../../render/blocks';

/**
 * A run failure, rendered inline in the transcript.
 *
 * This is the one place on the AI page where a coloured antd `Alert` is correct: an error IS a
 * semantic state, which is exactly what the project rule reserves `Alert` for (persistent
 * explanatory notices go through `InfoBanner` instead, see `NoticeBlock`).
 *
 * The `code` is kept visible rather than logged away — `llm.provider.error_max_turns`,
 * `llm.stream.unexpected_content_type` and `ai.run.busy` are the strings an operator has to
 * search for, and the provider messages that accompany them are frequently vague. `hint`, when the
 * server sent one, becomes the description.
 */

export interface ErrorBlockProps {
  block: ErrorBlockData;
}

const ErrorBlock = ({ block }: ErrorBlockProps) => {
  const { token } = theme.useToken();

  return (
    <div data-testid="ai-error-block">
      <Alert
        type="error"
        showIcon
        style={{ borderRadius: 8, fontSize: 14 }}
        message={
          <Flex align="center" gap={8} wrap="wrap">
            <span style={{ fontSize: 14 }}>{block.message}</span>
            <Tag style={{ marginInlineEnd: 0, fontSize: 14, color: token.colorTextSecondary }}>
              {block.code}
            </Tag>
          </Flex>
        }
        description={
          block.hint ? (
            <span style={{ fontSize: 14, whiteSpace: 'pre-wrap' }}>{block.hint}</span>
          ) : undefined
        }
      />
    </div>
  );
};

export default memo(ErrorBlock);
