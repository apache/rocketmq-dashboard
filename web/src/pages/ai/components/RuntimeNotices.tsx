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

import { Alert } from 'antd';
import { useLang } from '../../../i18n/LangContext';
import InfoBanner from '../../../components/InfoBanner';

/**
 * The page-level notices between the transcript and the composer.
 *
 * Severity discipline per the project UI rules: the mock-mode explanation and the missing-rmqctl
 * notice are persistent, non-semantic statements about the runtime — one is an antd `Alert
 * type="info"` only because it replaces the identical legacy banner, the other is the neutral
 * `InfoBanner` (never a yellow warning Alert: nothing is wrong, a capability is simply absent).
 * The run error IS semantic, so it gets `Alert type="error"`.
 */

const NOTICE_STYLE = { margin: '0 24px 12px' } as const;

export interface RuntimeNoticesProps {
  /** Mock data mode: the AI page does not participate and the composer is disabled. */
  mock: boolean;
  /** `getAgentCapabilities().rmqctlAvailable`; without rmqctl the agent has no RocketMQ tools. */
  rmqctlAvailable: boolean;
  /** `useAgentRun().error`; empty while nothing failed. */
  runError: string;
}

const RuntimeNotices = ({ mock, rmqctlAvailable, runError }: RuntimeNoticesProps) => {
  const { t } = useLang();

  return (
    <>
      {mock && (
        <Alert
          type="info"
          showIcon
          data-testid="ai-mock-disabled"
          style={NOTICE_STYLE}
          message={t('ai.mockProviderDisabled')}
          description={t('ai.mockProviderDisabledDescription')}
        />
      )}
      {!mock && !rmqctlAvailable && (
        <InfoBanner
          data-testid="ai-rmqctl-unavailable-banner"
          title={t('ai.rmqctlUnavailable')}
          description={t('ai.rmqctlUnavailableDescription')}
          style={NOTICE_STYLE}
        />
      )}
      {runError && (
        <Alert
          type="error"
          showIcon
          data-testid="ai-run-error"
          style={NOTICE_STYLE}
          message={runError}
        />
      )}
    </>
  );
};

export default RuntimeNotices;
