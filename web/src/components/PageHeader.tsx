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

import { Flex, Typography } from 'antd';
import type { ReactNode } from 'react';

const { Title, Text } = Typography;

interface PageHeaderProps {
  title: string;
  subtitle?: ReactNode;
  extra?: ReactNode;
}

const PageHeader = ({ title, subtitle, extra }: PageHeaderProps) => (
  <Flex justify="space-between" align="center" style={{ marginBottom: 24 }}>
    <Flex align="center" gap={12}>
      <Title level={4} style={{ margin: 0, fontWeight: 600 }}>
        {title}
      </Title>
      {subtitle && (
        <Text type="secondary" style={{ fontSize: 13 }}>
          {subtitle}
        </Text>
      )}
    </Flex>
    {extra && <Flex gap={8}>{extra}</Flex>}
  </Flex>
);

export default PageHeader;
