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

import { Alert, Flex, Progress, Space, Statistic, Tag, Typography, theme } from 'antd';
import { CheckCircle, WarningCircle, XCircle } from '@phosphor-icons/react';
import type { StudioUser } from '../api/studioUsers';
import {
  analyzeStudioUsers,
  evaluateStudioPassword,
  type StudioPasswordCheck,
} from '../utils/studioUserSecurity';

const { Text } = Typography;

interface StudioPasswordStrengthPanelProps {
  password?: string;
  username?: string | null;
  currentPassword?: string | null;
}

interface StudioUserSecurityOverviewProps {
  users: StudioUser[];
  total: number;
  loading?: boolean;
}

const checkIcon = (check: StudioPasswordCheck) => {
  if (check.passed) return <CheckCircle size={16} color="#52c41a" weight="fill" />;
  if (check.severity === 'error') return <XCircle size={16} color="#ff4d4f" weight="fill" />;
  return <WarningCircle size={16} color="#faad14" weight="fill" />;
};

const checkColor = (check: StudioPasswordCheck) => {
  if (check.passed) return 'success';
  return check.severity === 'error' ? 'error' : 'warning';
};

export const StudioPasswordStrengthPanel = ({
  password,
  username,
  currentPassword,
}: StudioPasswordStrengthPanelProps) => {
  const { token } = theme.useToken();
  const evaluation = evaluateStudioPassword(password, { username, currentPassword });
  const visibleChecks = evaluation.checks.filter(
    (check) =>
      !check.passed ||
      ['MIN_LENGTH', 'LONG_LENGTH', 'MIXED_CASE', 'DIGIT', 'SYMBOL'].includes(check.code),
  );

  return (
    <div
      data-testid="studio-password-strength"
      style={{
        border: `1px solid ${token.colorBorderSecondary}`,
        background: token.colorFillQuaternary,
        borderRadius: 8,
        padding: 12,
        marginTop: 8,
      }}
    >
      <Flex justify="space-between" align="center" gap={12} wrap>
        <Space size={8}>
          <Text strong>密码强度</Text>
          <Tag color={evaluation.color}>{evaluation.label}</Tag>
        </Space>
        <Text type="secondary">{evaluation.score}/100</Text>
      </Flex>
      <Progress
        percent={evaluation.score}
        showInfo={false}
        strokeColor={evaluation.level === 'empty' ? token.colorFillSecondary : evaluation.color}
        trailColor={token.colorFillSecondary}
        style={{ margin: '8px 0 10px' }}
      />
      {evaluation.level === 'empty' ? (
        <Text type="secondary">输入密码后显示强度检查结果。</Text>
      ) : (
        <Flex gap="8px 10px" wrap>
          {visibleChecks.map((check) => (
            <Tag
              key={check.code}
              color={checkColor(check)}
              icon={checkIcon(check)}
              style={{ display: 'inline-flex', alignItems: 'center', gap: 4, marginInlineEnd: 0 }}
            >
              {check.label}
            </Tag>
          ))}
        </Flex>
      )}
      {evaluation.suggestions.length > 0 && (
        <ul style={{ margin: '10px 0 0', paddingInlineStart: 20, color: token.colorTextSecondary }}>
          {evaluation.suggestions.map((suggestion) => (
            <li key={suggestion}>{suggestion}</li>
          ))}
        </ul>
      )}
    </div>
  );
};

const statusAlertType = (statusColor: 'success' | 'warning' | 'error') =>
  statusColor === 'success' ? 'success' : statusColor === 'error' ? 'error' : 'warning';

const riskColor = (severity: string) => {
  if (severity === 'critical') return 'error';
  if (severity === 'warning') return 'warning';
  return 'default';
};

export const StudioUserSecurityOverview = ({
  users,
  total,
  loading = false,
}: StudioUserSecurityOverviewProps) => {
  const { token } = theme.useToken();
  const summary = analyzeStudioUsers(users, total);
  const scopeText = summary.totalMayExceedInspected
    ? `当前页 ${summary.inspectedCount} / 总计 ${summary.totalCount}`
    : `共 ${summary.inspectedCount} 个账号`;

  return (
    <div
      data-testid="studio-user-security-overview"
      style={{
        border: `1px solid ${token.colorBorderSecondary}`,
        borderRadius: 8,
        padding: 16,
        marginBottom: 16,
        background: token.colorBgContainer,
      }}
    >
      <Flex justify="space-between" align="flex-start" gap={16} wrap>
        <div style={{ minWidth: 220 }}>
          <Space size={8} wrap>
            <Text strong>当前页账号安全摘要</Text>
            <Tag color={summary.statusColor}>{summary.statusText}</Tag>
            <Text type="secondary">{scopeText}</Text>
          </Space>
          <Progress
            percent={summary.score}
            size="small"
            status={summary.status === 'critical' ? 'exception' : 'normal'}
            strokeColor={
              summary.status === 'healthy'
                ? token.colorSuccess
                : summary.status === 'warning'
                  ? token.colorWarning
                  : token.colorError
            }
            style={{ maxWidth: 360, marginTop: 10 }}
          />
        </div>
        <Flex gap={20} wrap>
          <Statistic title="启用账号" value={summary.enabledCount} loading={loading} />
          <Statistic title="管理员" value={summary.adminCount} loading={loading} />
          <Statistic title="可用管理员" value={summary.activeAdminCount} loading={loading} />
          <Statistic title="密码需轮换" value={summary.stalePasswordCount} loading={loading} />
          <Statistic
            title="改密时间未知"
            value={summary.unknownPasswordAgeCount}
            loading={loading}
          />
        </Flex>
      </Flex>
      <Alert
        showIcon
        type={statusAlertType(summary.statusColor)}
        message={
          summary.risks.length === 0
            ? '当前页未发现账号安全风险'
            : summary.risks.map((risk) => risk.title).join('；')
        }
        description={
          summary.risks.length === 0 ? (
            summary.recommendations[0]
          ) : (
            <Space direction="vertical" size={6} style={{ width: '100%' }}>
              {summary.risks.slice(0, 4).map((risk) => (
                <Flex key={risk.code} gap={8} wrap>
                  <Tag color={riskColor(risk.severity)} style={{ marginInlineEnd: 0 }}>
                    {risk.count}
                  </Tag>
                  <Text>{risk.description}</Text>
                  {risk.users.length > 0 && (
                    <Text type="secondary">涉及账号：{risk.users.join(', ')}</Text>
                  )}
                </Flex>
              ))}
            </Space>
          )
        }
        style={{ marginTop: 12 }}
      />
    </div>
  );
};
