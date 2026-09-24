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

import type { CSSProperties } from 'react';
import type { GlobalToken } from 'antd';

/**
 * The theme bridge of the AI page: exposes the antd tokens as the `--ai-*` custom properties the
 * `.ai-page` scoped rules in `index.css` consume, so the CSS follows the active theme (dark mode
 * included) without duplicating the palette. Extracted from the page shell to keep it a shell.
 */
export function aiPageStyle(token: GlobalToken): CSSProperties {
  return {
    height: '100%',
    minHeight: 0,
    padding: 24,
    overflow: 'hidden',
    '--ai-surface': token.colorBgContainer,
    '--ai-surface-elevated': token.colorBgElevated,
    '--ai-border': token.colorBorderSecondary,
    '--ai-text': token.colorText,
    '--ai-text-secondary': token.colorTextSecondary,
    '--ai-text-tertiary': token.colorTextTertiary,
    '--ai-primary': token.colorPrimary,
    '--ai-primary-bg': token.colorPrimaryBg,
    '--ai-primary-hover': token.colorPrimaryHover,
    '--ai-fill-secondary': token.colorFillSecondary,
    '--ai-code-bg': token.colorBgSpotlight,
    '--ai-code-text': token.colorTextLightSolid,
  } as CSSProperties;
}
