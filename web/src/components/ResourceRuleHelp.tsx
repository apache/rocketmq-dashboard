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
import ResourceOwnershipTopology from './ResourceOwnershipTopology';

const { Text } = Typography;

const FONT_SIZE = 14;

/**
 * 创建实例 / Topic / Group 表单上的规则入口：问号点开弹窗，讲清名字的唯一性边界、
 * 归属拓扑与创建时的三种结果。2026-10-26 起这套说明没有独立页面，只在这里出现。
 */
const ResourceRuleHelp = () => {
  const { t } = useLang();
  const { token } = theme.useToken();
  const [open, setOpen] = useState(false);

  return (
    <>
      <Tooltip title={t('resourceModel.helpAria')}>
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
        width={860}
        onCancel={() => setOpen(false)}
        styles={{
          body: { maxHeight: 'calc(100vh - 260px)', overflowY: 'auto', paddingRight: 8 },
        }}
        footer={<Button onClick={() => setOpen(false)}>{t('common.close')}</Button>}
      >
        <Space direction="vertical" size={16} style={{ width: '100%', marginTop: 8 }}>
          <Text style={{ fontSize: FONT_SIZE, lineHeight: 1.7 }}>
            {t('resourceModel.modalIntro')}
          </Text>

          <ResourceOwnershipTopology />

          <div>
            <Text strong style={{ fontSize: FONT_SIZE }}>
              {t('resourceModel.rulesTitle')}
            </Text>
            <Space direction="vertical" size={4} style={{ display: 'flex', marginTop: 6 }}>
              <Text style={{ fontSize: FONT_SIZE, lineHeight: 1.7 }}>
                {t('resourceModel.rule1')}
              </Text>
              <Text style={{ fontSize: FONT_SIZE, lineHeight: 1.7 }}>
                {t('resourceModel.rule2')}
              </Text>
              <Text style={{ fontSize: FONT_SIZE, lineHeight: 1.7 }}>
                {t('resourceModel.rule3')}
              </Text>
              <Text style={{ fontSize: FONT_SIZE, lineHeight: 1.7 }}>
                {t('resourceModel.rule4')}
              </Text>
            </Space>
          </div>
        </Space>
      </Modal>
    </>
  );
};

export default ResourceRuleHelp;
