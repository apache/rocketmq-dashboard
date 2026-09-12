/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { useEffect, useRef, useState } from 'react';
import { Alert, Button, Descriptions, Form, Input, Modal, Space, Typography } from 'antd';
import {
  previewMessageRecall,
  recallDelayMessage,
  type RecallRequest,
  type RecallTarget,
  type RecallReceipt,
} from '../api/messageRecall';

interface Props {
  instanceId: string;
  initialTopic?: string;
  onClose: () => void;
}

export default function DelayMessageRecallDialog({ instanceId, initialTopic, onClose }: Props) {
  const [form] = Form.useForm<{ topic: string; recallHandle: string }>();
  const [target, setTarget] = useState<{ request: RecallRequest; detail: RecallTarget } | null>(
    null,
  );
  const [receipt, setReceipt] = useState<RecallReceipt | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState<'preview' | 'recall' | null>(null);
  const busyRef = useRef(false);
  const activeRef = useRef(true);
  const generationRef = useRef(0);
  useEffect(() => {
    activeRef.current = true;
    return () => {
      activeRef.current = false;
      generationRef.current += 1;
    };
  }, []);

  const preview = async () => {
    if (busyRef.current) return;
    busyRef.current = true;
    const generation = ++generationRef.current;
    try {
      const values = await form.validateFields();
      if (!activeRef.current || generation !== generationRef.current) return;
      const request = {
        instanceId,
        topic: values.topic.trim(),
        recallHandle: values.recallHandle.trim(),
      };
      setBusy('preview');
      setError(null);
      setReceipt(null);
      setTarget(null);
      const detail = await previewMessageRecall(request);
      if (activeRef.current && generation === generationRef.current) setTarget({ request, detail });
    } catch (failure) {
      if (activeRef.current && generation === generationRef.current && failure instanceof Error)
        setError(failure.message);
    } finally {
      busyRef.current = false;
      if (activeRef.current) setBusy(null);
    }
  };

  const recall = async () => {
    if (busyRef.current || !target || receipt) return;
    busyRef.current = true;
    setBusy('recall');
    setError(null);
    try {
      const result = await recallDelayMessage(target.request);
      if (activeRef.current) setReceipt(result);
    } catch (failure) {
      if (activeRef.current)
        setError(failure instanceof Error ? failure.message : 'Recall request failed');
    } finally {
      busyRef.current = false;
      if (activeRef.current) setBusy(null);
    }
  };

  return (
    <Modal
      open
      title="Recall a delayed message"
      width={720}
      styles={{ body: { maxHeight: '65vh', overflowY: 'auto', paddingRight: 8 } }}
      onCancel={onClose}
      closable={busy !== 'recall'}
      maskClosable={busy !== 'recall'}
      keyboard={busy !== 'recall'}
      footer={
        <Space>
          <Button disabled={busy === 'recall'} onClick={onClose}>
            Close
          </Button>
          <Button
            loading={busy === 'preview'}
            disabled={busy === 'recall'}
            onClick={() => void preview()}
          >
            Inspect handle
          </Button>
          <Button
            danger
            type="primary"
            disabled={!target || !!receipt || busy === 'preview'}
            loading={busy === 'recall'}
            onClick={() => void recall()}
          >
            Confirm recall
          </Button>
        </Space>
      }
    >
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        <Alert
          showIcon
          type="warning"
          message="Recall applies only to pending timer messages"
          description="Paste the recall handle returned by the original producer send receipt. A message ID alone cannot recall a message. Broker acceptance does not undo a message already delivered."
        />
        <Typography.Text>Instance: {instanceId}</Typography.Text>
        <Form
          form={form}
          layout="vertical"
          initialValues={{ topic: initialTopic }}
          disabled={busy !== null}
          onValuesChange={() => {
            generationRef.current += 1;
            setTarget(null);
            setReceipt(null);
            setError(null);
          }}
        >
          <Form.Item
            label="Topic"
            name="topic"
            rules={[{ required: true, whitespace: true, message: 'Topic is required' }]}
          >
            <Input />
          </Form.Item>
          <Form.Item
            label="Recall handle"
            name="recallHandle"
            rules={[{ required: true, whitespace: true, message: 'Recall handle is required' }]}
          >
            <Input.TextArea rows={3} autoComplete="off" />
          </Form.Item>
        </Form>
        {target && (
          <Descriptions bordered size="small" column={1}>
            <Descriptions.Item label="Target Topic">{target.detail.topic}</Descriptions.Item>
            <Descriptions.Item label="Broker">{target.detail.brokerName}</Descriptions.Item>
            <Descriptions.Item label="Original message ID">
              {target.detail.messageId}
            </Descriptions.Item>
            <Descriptions.Item label="Handle delivery time">
              {new Date(target.detail.deliveryTimestamp).toLocaleString()}
            </Descriptions.Item>
          </Descriptions>
        )}
        {error && <Alert type="error" showIcon message={error} />}
        {receipt && (
          <Alert
            type="success"
            showIcon
            message="Broker accepted the recall request"
            description={
              <Space direction="vertical">
                <Typography.Text copyable>{receipt.messageId}</Typography.Text>
                <Typography.Text>
                  Acceptance does not independently confirm cancellation. Check the message trace or
                  the producing application if delivery status is uncertain.
                </Typography.Text>
              </Space>
            }
          />
        )}
      </Space>
    </Modal>
  );
}
