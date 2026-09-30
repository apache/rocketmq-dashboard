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
import InfoBanner from '../../../../components/InfoBanner';
import { useLang } from '../../../../i18n/LangContext';
import type { NoticeBlock as NoticeBlockData } from '../../render/blocks';

/**
 * Advisory the provider or Studio itself emitted alongside an answer — typically
 * `rmqctl 不可用，本次会话已禁用 RocketMQ 工具`, or "unhandled agent message type: X".
 *
 * Rendered with the neutral `InfoBanner` treatment (#fafafa on #f0f0f0, radius 8, 14px), NOT a
 * yellow antd `Alert`: a notice is part of the transcript's furniture, and the project reserves
 * semantic colours for real states. A `warn` notice gets a title so it can be spotted while
 * scanning, but keeps the same neutral chrome. A genuine failure arrives as an `error` block and is
 * rendered by `ErrorBlock`, which does use `Alert type="error"` — that one IS semantic.
 */

export interface NoticeBlockProps {
  block: NoticeBlockData;
}

const NoticeBlock = ({ block }: NoticeBlockProps) => {
  const { t } = useLang();

  return (
    <InfoBanner
      data-testid="ai-notice-block"
      title={block.level === 'warn' ? t('ai.notice.warn') : undefined}
      description={block.message}
      style={{ padding: '8px 12px' }}
    />
  );
};

export default memo(NoticeBlock);
