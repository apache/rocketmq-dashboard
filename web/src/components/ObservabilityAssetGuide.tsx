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

import { Collapse, Space, Typography } from 'antd';
import { useLang } from '../i18n/LangContext';

const { Paragraph, Text } = Typography;

type GuideKind = 'grafana' | 'prometheus';

interface ObservabilityAssetGuideProps {
  kind: GuideKind;
}

const prometheusConfig = `rule_files:
  - /etc/prometheus/rules/rocketmq-*.yml`;

const copy = {
  en: {
    grafana: {
      title: 'How to import these dashboards',
      steps: [
        'Download a dashboard JSON file. If you download the bundle, extract it first.',
        'In Grafana, open Dashboards > New > Import and upload the JSON file.',
        'Choose your Prometheus data source, review dashboard variables, and select Import.',
      ],
    },
    prometheus: {
      title: 'How to deploy these alert rules',
      steps: [
        'Download the YAML rules and place them in a directory readable by Prometheus.',
        'Reference the downloaded files from the rule_files section of prometheus.yml.',
        'Validate each file with promtool check rules <file>, then reload Prometheus.',
      ],
    },
  },
  zh: {
    grafana: {
      title: '如何导入这些仪表盘',
      steps: [
        '下载仪表盘 JSON 文件；如果下载的是资源包，请先解压。',
        '在 Grafana 中打开“仪表盘 > 新建 > 导入”，然后上传 JSON 文件。',
        '选择 Prometheus 数据源，检查仪表盘变量后执行导入。',
      ],
    },
    prometheus: {
      title: '如何部署这些告警规则',
      steps: [
        '下载 YAML 规则，并将其放入 Prometheus 可读取的目录。',
        '在 prometheus.yml 的 rule_files 配置中引用下载的文件。',
        '使用 promtool check rules <文件> 校验每个文件，然后重载 Prometheus。',
      ],
    },
  },
} as const;

const ObservabilityAssetGuide = ({ kind }: ObservabilityAssetGuideProps) => {
  const { lang } = useLang();
  const content = copy[lang][kind];

  return (
    <Collapse
      size="small"
      items={[
        {
          key: kind,
          label: content.title,
          children: (
            <Space direction="vertical" size={8} style={{ width: '100%' }}>
              <ol style={{ margin: 0, paddingInlineStart: 20 }}>
                {content.steps.map((step) => (
                  <li key={step} style={{ marginBottom: 6 }}>
                    <Text>{step}</Text>
                  </li>
                ))}
              </ol>
              {kind === 'prometheus' && (
                <Paragraph
                  copyable={{ text: prometheusConfig }}
                  style={{ margin: 0, whiteSpace: 'pre-wrap' }}
                >
                  <Text code>{prometheusConfig}</Text>
                </Paragraph>
              )}
            </Space>
          ),
        },
      ]}
    />
  );
};

export default ObservabilityAssetGuide;
