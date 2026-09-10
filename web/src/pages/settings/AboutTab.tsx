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

import { useMemo } from 'react';
import { Alert, Button, Descriptions, Divider, Flex, Space, Typography, message } from 'antd';
import {
  BookOutlined,
  CopyOutlined,
  DownloadOutlined,
  GithubOutlined,
  GlobalOutlined,
} from '@ant-design/icons';
import { isMockMode } from '../../services/dataMode';
import { downloadBlob } from '../../utils/download';
import {
  buildSupportBundleFilename,
  buildSupportBundleSummary,
  collectSupportBundle,
  formatSupportBundle,
  type SupportBundleProduct,
} from '../../utils/supportBundle';

const { Title, Text, Link: TypoLink } = Typography;

const PRODUCT_INFO: SupportBundleProduct = {
  name: 'RocketMQ Studio',
  version: '0.1.0',
  buildCommit: __BUILD_COMMIT__,
  buildTime: __BUILD_TIME__,
  rocketmqVersions: '4.x / 5.x',
  frontendFramework: 'React 18 + Ant Design 5',
  backendFramework: 'Spring Boot 3 + RocketMQ MCP Server',
  license: 'Apache 2.0',
};

const createBundle = () =>
  collectSupportBundle(PRODUCT_INFO, {
    effectiveMockMode: isMockMode(),
  });

const copyText = async (text: string) => {
  if (!window.navigator.clipboard?.writeText) {
    throw new Error('Clipboard API is unavailable');
  }
  await window.navigator.clipboard.writeText(text);
};

export const AboutTab = () => {
  const supportBundle = useMemo(() => createBundle(), []);
  const supportSummary = useMemo(() => buildSupportBundleSummary(supportBundle), [supportBundle]);

  const handleCopySupportBundle = async () => {
    try {
      await copyText(formatSupportBundle(createBundle()));
      message.success('支持信息包已复制');
    } catch {
      message.error('复制支持信息包失败');
    }
  };

  const handleDownloadSupportBundle = () => {
    const bundle = createBundle();
    downloadBlob(
      new Blob([formatSupportBundle(bundle)], { type: 'application/json;charset=utf-8' }),
      buildSupportBundleFilename(bundle),
    );
    message.success('支持信息包已下载');
  };

  return (
    <div style={{ maxWidth: 920 }}>
      <Descriptions column={1} bordered size="small">
        <Descriptions.Item label="版本">{PRODUCT_INFO.version}</Descriptions.Item>
        <Descriptions.Item label="构建提交">{PRODUCT_INFO.buildCommit}</Descriptions.Item>
        <Descriptions.Item label="构建时间">{PRODUCT_INFO.buildTime}</Descriptions.Item>
        <Descriptions.Item label="RocketMQ 支持版本">
          {PRODUCT_INFO.rocketmqVersions}
        </Descriptions.Item>
        <Descriptions.Item label="前端框架">{PRODUCT_INFO.frontendFramework}</Descriptions.Item>
        <Descriptions.Item label="后端框架">{PRODUCT_INFO.backendFramework}</Descriptions.Item>
        <Descriptions.Item label="License">{PRODUCT_INFO.license}</Descriptions.Item>
      </Descriptions>

      <Divider />

      <Flex justify="space-between" align="center" gap={12} wrap style={{ marginBottom: 12 }}>
        <Title level={5} style={{ margin: 0 }}>
          支持信息包
        </Title>
        <Space>
          <Button icon={<CopyOutlined />} onClick={() => void handleCopySupportBundle()}>
            复制 JSON
          </Button>
          <Button icon={<DownloadOutlined />} onClick={handleDownloadSupportBundle}>
            下载 JSON
          </Button>
        </Space>
      </Flex>

      <Alert
        showIcon
        type="info"
        message="已脱敏：Token、密码、密钥、URL 查询参数和 sessionStorage 内容不会写入支持信息包。"
        style={{ marginBottom: 12 }}
      />

      <Descriptions
        column={{ xs: 1, sm: 1, md: 2 }}
        bordered
        size="small"
        items={supportSummary.map((item) => ({
          key: item.label,
          label: item.label,
          children: item.value,
        }))}
      />

      <Divider />

      <Title level={5}>相关链接</Title>
      <Space size="middle" style={{ marginBottom: 24 }}>
        <TypoLink
          href="https://github.com/apache/rocketmq"
          target="_blank"
          rel="noopener noreferrer"
        >
          <GithubOutlined /> GitHub
        </TypoLink>
        <TypoLink
          href="https://rocketmq.apache.org/docs/"
          target="_blank"
          rel="noopener noreferrer"
        >
          <BookOutlined /> 文档中心
        </TypoLink>
        <TypoLink href="https://rocketmq.apache.org/" target="_blank" rel="noopener noreferrer">
          <GlobalOutlined /> RocketMQ 社区
        </TypoLink>
      </Space>

      <Divider />

      <Text type="secondary">
        Copyright © {PRODUCT_INFO.buildTime.slice(0, 4)} Apache Software Foundation. Licensed under
        the Apache License, Version 2.0.
      </Text>
    </div>
  );
};

export default AboutTab;
