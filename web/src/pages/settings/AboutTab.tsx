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

import { Descriptions, Divider, Space, Typography } from 'antd';
import { BookOutlined, GithubOutlined, GlobalOutlined } from '@ant-design/icons';
import { useLanguage } from '../../i18n/LangContext';

const { Title, Text, Link: TypoLink } = Typography;

export const AboutTab = () => {
  const { t } = useLanguage();
  return (
  <div style={{ maxWidth: 800 }}>
    <Descriptions column={1} bordered size="small">
      <Descriptions.Item label={t('settings.about.version')}>0.1.0</Descriptions.Item>
      <Descriptions.Item label={t('settings.about.buildTime')}>2024-01-15 14:30:00</Descriptions.Item>
      <Descriptions.Item label={t('settings.about.supportedVersions')}>4.x / 5.x</Descriptions.Item>
      <Descriptions.Item label={t('settings.about.frontend')}>React 18 + Ant Design 5</Descriptions.Item>
      <Descriptions.Item label={t('settings.about.backend')}>Spring Boot 3 + RocketMQ MCP Server</Descriptions.Item>
      <Descriptions.Item label="License">Apache 2.0</Descriptions.Item>
    </Descriptions>

    <Divider />

    <Title level={5}>{t('settings.about.relatedLinks')}</Title>
    <Space size="middle" style={{ marginBottom: 24 }}>
      <TypoLink href="https://github.com/apache/rocketmq" target="_blank" rel="noopener noreferrer">
        <GithubOutlined /> GitHub
      </TypoLink>
      <TypoLink href="https://rocketmq.apache.org/docs/" target="_blank" rel="noopener noreferrer">
        <BookOutlined /> {t('settings.about.docs')}
      </TypoLink>
      <TypoLink href="https://rocketmq.apache.org/" target="_blank" rel="noopener noreferrer">
        <GlobalOutlined /> {t('settings.about.community')}
      </TypoLink>
    </Space>

    <Divider />

    <Text type="secondary">
      Copyright © 2024 Apache Software Foundation. Licensed under the Apache License, Version 2.0.
    </Text>
  </div>
  );
};

export default AboutTab;
