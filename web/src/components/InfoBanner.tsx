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

import type { CSSProperties, ReactNode } from 'react';
import { theme } from 'antd';

interface InfoBannerProps {
  title?: ReactNode;
  description?: ReactNode;
  children?: ReactNode;
  style?: CSSProperties;
  'data-testid'?: string;
}

// 页面级常驻说明统一使用中性灰色 banner，区别于带语义色的告警/错误 Alert；
// 颜色取 antd 主题 token，深色模式下自动适配（浅色模式观感等同 #fafafa/#f0f0f0）
const InfoBanner = ({ title, description, children, style, ...rest }: InfoBannerProps) => {
  const { token } = theme.useToken();
  return (
    <div
      {...rest}
      style={{
        marginBottom: 16,
        padding: '12px 16px',
        borderRadius: 8,
        border: `1px solid ${token.colorBorderSecondary}`,
        background: token.colorFillQuaternary,
        ...style,
      }}
    >
      {title && <div style={{ fontSize: 14, fontWeight: 500 }}>{title}</div>}
      {description && (
        <div
          style={{
            fontSize: 14,
            lineHeight: 1.6,
            color: token.colorTextSecondary,
            marginTop: title ? 6 : 0,
          }}
        >
          {description}
        </div>
      )}
      {children}
    </div>
  );
};

export default InfoBanner;
