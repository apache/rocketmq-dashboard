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

import React from 'react';
import { theme } from 'antd';
import { useLang } from '../i18n/LangContext';
import { useTheme } from '../theme/useTheme';

const FONT_SIZE = 14;

/**
 * 页面专用色板（用户指定 2026-10-23）。颜色只用于描边、浅色填充、圆点与标题下划线，
 * 正文文字仍走 antd token 保证对比度；深色模式换一组提亮变体。
 */
const PALETTE = {
  light: {
    red: '#D4042D',
    blue: '#5A92E5',
    olive: '#5E8034',
    teal: '#41B5C2',
    purple: '#722169',
  },
  dark: {
    red: '#FF7A8C',
    blue: '#8FB9F2',
    olive: '#A9C47E',
    teal: '#7FD4DE',
    purple: '#C993C0',
  },
};

type PaletteKey = keyof typeof PALETTE.light;

interface SvgBoxProps {
  x: number;
  y: number;
  width: number;
  height: number;
  stroke: string;
  fill?: string;
  radius?: number;
}

const SvgBox: React.FC<SvgBoxProps> = ({ x, y, width, height, stroke, fill, radius = 10 }) => (
  <rect
    x={x}
    y={y}
    width={width}
    height={height}
    rx={radius}
    fill={fill ?? 'transparent'}
    stroke={stroke}
    strokeWidth={1}
  />
);

interface SvgLabelProps {
  x: number;
  y: number;
  fill: string;
  children: React.ReactNode;
  bold?: boolean;
  muted?: boolean;
  anchor?: 'start' | 'middle';
}

const SvgLabel: React.FC<SvgLabelProps> = ({
  x,
  y,
  fill,
  children,
  bold,
  muted,
  anchor = 'start',
}) => (
  <text
    x={x}
    y={y}
    fill={fill}
    fontSize={FONT_SIZE}
    fontWeight={bold ? 600 : 400}
    fontStyle={muted ? 'italic' : undefined}
    textAnchor={anchor}
  >
    {children}
  </text>
);

const SvgDot: React.FC<{ cx: number; cy: number; fill: string }> = ({ cx, cy, fill }) => (
  <circle cx={cx} cy={cy} r={3.5} fill={fill} />
);

/**
 * 资源命名与归属的拓扑示意：两套 nameserver 环境，含同名共存正例与归属冲突反例。
 * 原为独立「资源模型」页的主体，2026-10-26 起只出现在创建表单的规则弹窗里。
 */
const ResourceOwnershipTopology = () => {
  const { t } = useLang();
  const { darkMode } = useTheme();
  const { token } = theme.useToken();
  const c = darkMode ? PALETTE.dark : PALETTE.light;
  const tint = (key: PaletteKey, alpha: string) => `${c[key]}${alpha}`;

  return (
    <svg
      role="img"
      aria-label={t('resourceModel.svgTopologyAria')}
      viewBox="0 0 760 332"
      style={{
        width: '100%',
        maxWidth: 760,
        height: 'auto',
        display: 'block',
        margin: '0 auto',
      }}
      data-testid="resource-ownership-topology"
    >
      <SvgBox
        x={8}
        y={8}
        width={430}
        height={316}
        radius={14}
        stroke={token.colorBorderSecondary}
        fill={token.colorFillQuaternary}
      />
      <SvgLabel x={24} y={34} fill={token.colorText} bold>
        {t('resourceModel.svgDomainProd')}
      </SvgLabel>
      <rect x={24} y={42} width={48} height={3} rx={1.5} fill={c.blue} />
      <SvgLabel x={24} y={62} fill={token.colorTextSecondary} muted>
        10.0.0.1:9876
      </SvgLabel>

      <SvgBox
        x={24}
        y={74}
        width={193}
        height={96}
        stroke={tint('blue', '59')}
        fill={token.colorBgContainer}
      />
      <SvgDot cx={40} cy={96} fill={c.blue} />
      <SvgLabel x={50} y={101} fill={token.colorText} bold>
        broker-a
      </SvgLabel>
      <SvgBox
        x={36}
        y={110}
        width={169}
        height={26}
        radius={6}
        stroke={tint('olive', '73')}
        fill={tint('olive', '1F')}
      />
      <SvgLabel x={46} y={128} fill={token.colorText}>
        {t('resourceModel.svgChipOrdersTrade')}
      </SvgLabel>
      <SvgBox
        x={36}
        y={142}
        width={169}
        height={26}
        radius={6}
        stroke={tint('olive', '73')}
        fill={tint('olive', '1F')}
      />
      <SvgLabel x={46} y={160} fill={token.colorText}>
        {t('resourceModel.svgChipPaymentsTrade')}
      </SvgLabel>

      <SvgBox
        x={229}
        y={74}
        width={193}
        height={96}
        stroke={tint('blue', '59')}
        fill={token.colorBgContainer}
      />
      <SvgDot cx={245} cy={96} fill={c.blue} />
      <SvgLabel x={255} y={101} fill={token.colorText} bold>
        broker-b
      </SvgLabel>
      <SvgBox
        x={241}
        y={110}
        width={169}
        height={26}
        radius={6}
        stroke={tint('olive', '73')}
        fill={tint('olive', '1F')}
      />
      <SvgLabel x={251} y={128} fill={token.colorText}>
        {t('resourceModel.svgChipOrdersLogistics')}
      </SvgLabel>

      <SvgLabel x={24} y={190} fill={token.colorTextSecondary} muted>
        {t('resourceModel.svgMergeNote1')}
      </SvgLabel>
      <SvgLabel x={24} y={208} fill={token.colorTextSecondary} muted>
        {t('resourceModel.svgMergeNote2')}
      </SvgLabel>

      <SvgBox
        x={24}
        y={220}
        width={193}
        height={44}
        stroke={token.colorBorder}
        fill={token.colorBgContainer}
      />
      <SvgDot cx={40} cy={236} fill={c.teal} />
      <SvgLabel x={50} y={241} fill={token.colorText} bold>
        {t('resourceModel.svgInstTrade')}
      </SvgLabel>
      <SvgLabel x={50} y={257} fill={token.colorTextSecondary} muted>
        {t('resourceModel.svgBoundNote')}
      </SvgLabel>

      <SvgBox
        x={229}
        y={220}
        width={193}
        height={44}
        stroke={token.colorBorder}
        fill={token.colorBgContainer}
      />
      <SvgDot cx={245} cy={236} fill={c.teal} />
      <SvgLabel x={255} y={241} fill={token.colorText} bold>
        {t('resourceModel.svgInstLogistics')}
      </SvgLabel>
      <SvgLabel x={255} y={257} fill={token.colorTextSecondary} muted>
        {t('resourceModel.svgBoundNote')}
      </SvgLabel>

      <SvgBox
        x={24}
        y={276}
        width={398}
        height={40}
        stroke={tint('red', '66')}
        fill={tint('red', '14')}
      />
      <SvgLabel x={38} y={293} fill={c.red} bold>
        {t('resourceModel.svgConflictLine1')}
      </SvgLabel>
      <SvgLabel x={38} y={309} fill={token.colorText}>
        {t('resourceModel.svgConflictLine2')}
      </SvgLabel>

      <SvgBox
        x={454}
        y={8}
        width={298}
        height={316}
        radius={14}
        stroke={token.colorBorderSecondary}
        fill={token.colorFillQuaternary}
      />
      <SvgLabel x={470} y={34} fill={token.colorText} bold>
        {t('resourceModel.svgDomainStaging')}
      </SvgLabel>
      <rect x={470} y={42} width={48} height={3} rx={1.5} fill={c.purple} />
      <SvgLabel x={470} y={62} fill={token.colorTextSecondary} muted>
        10.9.0.1:9876
      </SvgLabel>

      <SvgBox
        x={470}
        y={74}
        width={266}
        height={96}
        stroke={tint('purple', '59')}
        fill={token.colorBgContainer}
      />
      <SvgDot cx={486} cy={96} fill={c.purple} />
      <SvgLabel x={496} y={101} fill={token.colorText} bold>
        broker-s
      </SvgLabel>
      <SvgBox
        x={482}
        y={110}
        width={242}
        height={26}
        radius={6}
        stroke={tint('olive', '73')}
        fill={tint('olive', '1F')}
      />
      <SvgLabel x={492} y={128} fill={token.colorText}>
        {t('resourceModel.svgChipOrdersStaging')}
      </SvgLabel>

      <SvgLabel x={470} y={190} fill={token.colorTextSecondary} muted>
        {t('resourceModel.svgCrossDomainNote')}
      </SvgLabel>
      <SvgLabel x={470} y={208} fill={token.colorTextSecondary} muted>
        {t('resourceModel.svgCrossDomainNote2')}
      </SvgLabel>

      <SvgBox
        x={470}
        y={220}
        width={266}
        height={44}
        stroke={token.colorBorder}
        fill={token.colorBgContainer}
      />
      <SvgDot cx={486} cy={236} fill={c.teal} />
      <SvgLabel x={496} y={241} fill={token.colorText} bold>
        {t('resourceModel.svgInstStaging')}
      </SvgLabel>
      <SvgLabel x={496} y={257} fill={token.colorTextSecondary} muted>
        {t('resourceModel.svgBoundNote')}
      </SvgLabel>

      <SvgBox x={470} y={276} width={266} height={40} stroke={token.colorBorderSecondary} />
      <SvgLabel x={484} y={293} fill={token.colorText}>
        {t('resourceModel.svgCrossLine1')}
      </SvgLabel>
      <SvgLabel x={484} y={309} fill={token.colorTextSecondary} muted>
        {t('resourceModel.svgCrossLine2')}
      </SvgLabel>
    </svg>
  );
};

export default ResourceOwnershipTopology;
