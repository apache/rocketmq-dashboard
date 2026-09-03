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

import apacheFeatherLogo from '../../assets/logos/apache-feather.svg';
import alibabaCloudLogo from '../../assets/logos/alibabacloud.svg';
import tencentCloudLogo from '../../assets/logos/tencentcloud.svg';

export type InstanceVendor = 'APACHE' | 'ALIYUN' | 'TENCENT';

export interface VendorOption {
  key: InstanceVendor;
  label: string;
  labelKey?: string;
  logo: string;
  description: string;
  descKey?: string;
}

export const VENDOR_OPTIONS: VendorOption[] = [
  {
    key: 'APACHE',
    label: '开源版',
    labelKey: 'instance.vendorApache',
    logo: apacheFeatherLogo,
    description: '接入自建 Apache RocketMQ 开源集群，支持 Proxy / Direct 两种接入方式',
    descKey: 'instance.vendorApacheDesc',
  },
  {
    key: 'ALIYUN',
    label: 'Aliyun 版',
    labelKey: 'instance.vendorAliyun',
    logo: alibabaCloudLogo,
    description: '选择已录入的云凭据与云上实例完成接入，接入点自动解析',
    descKey: 'instance.vendorAliyunDesc',
  },
  {
    key: 'TENCENT',
    label: 'Tencent 版',
    labelKey: 'instance.vendorTencent',
    logo: tencentCloudLogo,
    description: '接入腾讯云 TDMQ RocketMQ 版实例，接入地址填写实例的接入点',
    descKey: 'instance.vendorTencentDesc',
  },
];

export const DEFAULT_VENDOR: InstanceVendor = 'APACHE';
