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

import { Form, Input, Modal, Select } from 'antd';
import { useEffect } from 'react';
import type { K8sCertInfo } from '../../api/cluster';

export interface K8sCertFormValue {
  name: string;
  namespace: string;
  cluster: string;
  type: string;
  issuer: string;
  sanText?: string;
}

interface Props {
  open: boolean;
  certificate: K8sCertInfo | null;
  loading: boolean;
  onCancel: () => void;
  onSubmit: (value: Partial<K8sCertInfo>) => Promise<void> | void;
}

const parseSubjectAlternativeNames = (value?: string): string[] =>
  Array.from(
    new Set(
      (value || '')
        .split(/[\n,]/)
        .map((item) => item.trim())
        .filter(Boolean),
    ),
  );

const K8sCertEditor = ({ open, certificate, loading, onCancel, onSubmit }: Props) => {
  const [form] = Form.useForm<K8sCertFormValue>();

  useEffect(() => {
    if (!open) return;
    form.setFieldsValue(
      certificate
        ? {
            name: certificate.name,
            namespace: certificate.namespace,
            cluster: certificate.cluster,
            type: certificate.type,
            issuer: certificate.issuer,
            sanText: certificate.san?.join('\n'),
          }
        : {
            namespace: 'rocketmq',
            type: 'TLS',
            issuer: 'kubernetes-ca',
          },
    );
  }, [certificate, form, open]);

  const submit = async () => {
    const values = await form.validateFields();
    await onSubmit({
      ...(certificate ? { id: certificate.id } : {}),
      name: values.name.trim(),
      namespace: values.namespace.trim(),
      cluster: values.cluster.trim(),
      type: values.type,
      issuer: values.issuer.trim(),
      san: parseSubjectAlternativeNames(values.sanText),
    });
  };

  return (
    <Modal
      title={certificate ? `编辑证书 — ${certificate.name}` : '添加证书'}
      open={open}
      onCancel={() => {
        form.resetFields();
        onCancel();
      }}
      onOk={() => void submit()}
      confirmLoading={loading}
      okText={certificate ? '保存' : '创建'}
      destroyOnClose
      width={560}
    >
      <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
        <Form.Item
          name="name"
          label="证书名称"
          rules={[
            { required: true, whitespace: true, message: '请输入证书名称' },
            { max: 128, message: '证书名称不能超过 128 个字符' },
          ]}
        >
          <Input placeholder="rocketmq-prod-tls" />
        </Form.Item>
        <Form.Item
          name="cluster"
          label="K8s 集群名称"
          rules={[{ required: true, whitespace: true, message: '请输入集群名称' }]}
        >
          <Input placeholder="production-cluster" />
        </Form.Item>
        <Form.Item
          name="namespace"
          label="命名空间"
          rules={[{ required: true, whitespace: true, message: '请输入命名空间' }]}
        >
          <Input placeholder="rocketmq" />
        </Form.Item>
        <Form.Item name="type" label="证书类型" rules={[{ required: true }]}>
          <Select
            options={[
              { value: 'TLS', label: 'TLS' },
              { value: 'mTLS', label: 'mTLS' },
              { value: 'ServiceAccount', label: 'ServiceAccount' },
            ]}
          />
        </Form.Item>
        <Form.Item
          name="issuer"
          label="签发者"
          rules={[{ required: true, whitespace: true, message: '请输入签发者' }]}
        >
          <Input placeholder="kubernetes-ca" />
        </Form.Item>
        <Form.Item
          name="sanText"
          label="Subject Alternative Names"
          extra="每行一个或使用逗号分隔；重复项会自动去除"
        >
          <Input.TextArea rows={4} placeholder={'broker.example.com\nproxy.example.com'} />
        </Form.Item>
      </Form>
    </Modal>
  );
};

export default K8sCertEditor;
