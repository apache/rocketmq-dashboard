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

import { useState } from 'react';
import { Button, Modal, Space, Tooltip, Typography, theme } from 'antd';
import { QuestionCircleOutlined } from '@ant-design/icons';
import { useLang } from '../i18n/LangContext';

const { Text } = Typography;

const FONT_SIZE = 14;

/**
 * 创建实例 / Topic / Group 表单上的规则入口：问号点开弹窗，讲清名字的唯一性边界与
 * 创建时的三种结果。文案与「资源模型」页共用 `resourceModel.*` 一套口径。
 */
const ResourceRuleHelp = () => {
  const { t } = useLang();
  const { token } = theme.useToken();
  const [open, setOpen] = useState(false);

  const outcomes = [
    {
      color: token.colorSuccess,
      title: t('resourceModel.outcomeFreeTitle'),
      desc: t('resourceModel.outcomeFreeDesc'),
    },
    {
      color: token.colorPrimary,
      title: t('resourceModel.outcomeOwnTitle'),
      desc: t('resourceModel.outcomeOwnDesc'),
    },
    {
      color: token.colorError,
      title: t('resourceModel.outcomeOtherTitle'),
      desc: t('resourceModel.outcomeOtherDesc'),
    },
  ];

  return (
    <>
      <Tooltip title={t('resourceModel.helpTooltip')}>
        <span
          role="button"
          tabIndex={0}
          aria-label={t('resourceModel.helpAria')}
          onClick={(event) => {
            // The icon sits inside a Form.Item label, whose default click behaviour focuses the
            // field - on a Select that would pop the dropdown open behind the modal.
            event.preventDefault();
            setOpen(true);
          }}
          onKeyDown={(event) => {
            if (event.key !== 'Enter' && event.key !== ' ') return;
            event.preventDefault();
            setOpen(true);
          }}
          style={{ display: 'inline-flex', cursor: 'pointer' }}
        >
          <QuestionCircleOutlined style={{ color: token.colorTextTertiary, fontSize: FONT_SIZE }} />
        </span>
      </Tooltip>

      <Modal
        title={t('resourceModel.modalTitle')}
        open={open}
        width={520}
        onCancel={() => setOpen(false)}
        footer={<Button onClick={() => setOpen(false)}>{t('common.close')}</Button>}
      >
        <Space direction="vertical" size={14} style={{ width: '100%', marginTop: 8 }}>
          <Text style={{ fontSize: FONT_SIZE, lineHeight: 1.7 }}>
            {t('resourceModel.modalIntro')}
          </Text>
          {outcomes.map((outcome) => (
            <Space key={outcome.title} align="start" size={10}>
              <span
                aria-hidden
                style={{
                  width: 8,
                  height: 8,
                  marginTop: 7,
                  borderRadius: '50%',
                  background: outcome.color,
                  flexShrink: 0,
                }}
              />
              <div>
                <Text strong style={{ fontSize: FONT_SIZE }}>
                  {outcome.title}
                </Text>
                <div>
                  <Text type="secondary" style={{ fontSize: FONT_SIZE, lineHeight: 1.7 }}>
                    {outcome.desc}
                  </Text>
                </div>
              </div>
            </Space>
          ))}
        </Space>
      </Modal>
    </>
  );
};

export default ResourceRuleHelp;
