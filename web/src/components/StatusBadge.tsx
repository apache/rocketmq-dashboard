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

import { Badge, Space, Typography } from 'antd';
import { STATUS_MAP } from '../constants/theme';
import { useLang } from '../i18n/LangContext';

const { Text } = Typography;

interface StatusBadgeProps {
  status: string;
  text?: string;
  showDot?: boolean;
}

const StatusBadge = ({ status, text, showDot = true }: StatusBadgeProps) => {
  const { t } = useLang();
  const config = STATUS_MAP[status];
  const label = text || (config ? t(config.labelKey) : status);
  const color = config?.color ?? '#8c8c8c';
  return (
    <Space size={4} role="status" aria-label={label}>
      {showDot && <Badge color={config?.dot ?? color} aria-hidden="true" />}
      <Text style={{ color, fontSize: 14 }}>{label}</Text>
    </Space>
  );
};

export default StatusBadge;
