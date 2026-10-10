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

import { useEffect } from 'react';
import { App as AntdApp, ConfigProvider, message, notification, theme } from 'antd';
import enUS from 'antd/locale/en_US';
import zhCN from 'antd/locale/zh_CN';
import { BrowserRouter } from 'react-router-dom';
import App from './App';
import { useLang } from './i18n/LangContext';
import { useTheme } from './theme/useTheme';
import { getMessageLiveRegion, getNotificationLiveRegion } from './utils/toastLiveRegions';

const StudioApp = () => {
  const { lang } = useLang();
  const { darkMode, compact } = useTheme();

  // The static toast singletons default to mounting into document.body without any ARIA
  // semantics, which leaves every async outcome silent for screen-reader users. Both the
  // singletons (here) and the App.useApp() instances (below) mount into live-region
  // containers instead, so each notice is announced as it appears.
  useEffect(() => {
    message.config({ getContainer: getMessageLiveRegion });
    notification.config({ getContainer: getNotificationLiveRegion });
  }, []);

  const algorithms = [darkMode ? theme.darkAlgorithm : theme.defaultAlgorithm];
  if (compact) {
    algorithms.push(theme.compactAlgorithm);
  }

  return (
    <ConfigProvider
      locale={lang === 'zh' ? zhCN : enUS}
      theme={{
        algorithm: algorithms,
        token: {
          colorPrimary: '#1677ff',
          borderRadius: 8,
          fontFamily:
            '-apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", sans-serif',
          ...(darkMode
            ? {
                colorBgBase: '#2a2a2e',
                colorBgContainer: '#323236',
                colorBgElevated: '#3a3a3e',
                colorBorder: '#3a3a3e',
                colorBorderSecondary: '#333337',
              }
            : {}),
        },
        components: {
          Card: { borderRadiusLG: 12 },
          Table: { borderRadius: 8 },
          Button: { borderRadius: 8 },
          Tag: { borderRadiusSM: 6 },
        },
      }}
    >
      <AntdApp
        message={{ getContainer: getMessageLiveRegion }}
        notification={{ getContainer: getNotificationLiveRegion }}
      >
        <BrowserRouter>
          <App />
        </BrowserRouter>
      </AntdApp>
    </ConfigProvider>
  );
};

export default StudioApp;
