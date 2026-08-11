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

import { useState } from 'react';
import { Alert, Button, Checkbox, Descriptions, Modal, Space, Table, Upload, message } from 'antd';
import { InboxOutlined } from '@ant-design/icons';
import type { InstanceImportItem, InstanceImportResult } from '../../api/instance';
import { importInstances } from '../../services/instanceService';
import { parseInstanceBundle } from './instanceImport';

interface Props {
  open: boolean;
  onCancel: () => void;
  onImported: () => Promise<void> | void;
}

const ResultSummary = ({ result }: { result: InstanceImportResult }) => (
  <>
    <Descriptions size="small" column={4} bordered style={{ marginTop: 16 }}>
      <Descriptions.Item label="新增">{result.createdIds.length}</Descriptions.Item>
      <Descriptions.Item label="更新">{result.updatedIds.length}</Descriptions.Item>
      <Descriptions.Item label="跳过">{result.skippedIds.length}</Descriptions.Item>
      <Descriptions.Item label="失败">{Object.keys(result.errors).length}</Descriptions.Item>
    </Descriptions>
    {Object.keys(result.errors).length > 0 && (
      <Table
        size="small"
        rowKey="id"
        pagination={{ pageSize: 5, hideOnSinglePage: true }}
        style={{ marginTop: 12 }}
        dataSource={Object.entries(result.errors).map(([id, reason]) => ({ id, reason }))}
        columns={[
          { title: '记录', dataIndex: 'id', width: 160 },
          { title: '错误原因', dataIndex: 'reason' },
        ]}
      />
    )}
  </>
);

const InstanceImportDialog = ({ open, onCancel, onImported }: Props) => {
  const [fileName, setFileName] = useState('');
  const [instances, setInstances] = useState<InstanceImportItem[]>([]);
  const [overwrite, setOverwrite] = useState(false);
  const [preview, setPreview] = useState<InstanceImportResult | null>(null);
  const [previewing, setPreviewing] = useState(false);
  const [importing, setImporting] = useState(false);

  const close = () => {
    setFileName('');
    setInstances([]);
    setOverwrite(false);
    setPreview(null);
    onCancel();
  };

  const readFile = async (file: File) => {
    try {
      const bundle = parseInstanceBundle(await file.text());
      setFileName(file.name);
      setInstances(bundle.instances);
      setPreview(null);
      message.success(`已读取 ${bundle.instances.length} 个实例`);
    } catch (error) {
      setFileName('');
      setInstances([]);
      setPreview(null);
      message.error(error instanceof Error ? error.message : 'JSON 文件解析失败');
    }
  };

  const previewImport = async () => {
    setPreviewing(true);
    try {
      setPreview(await importInstances({ instances, overwrite, dryRun: true }));
    } catch {
      message.error('预检失败，请检查导入内容');
    } finally {
      setPreviewing(false);
    }
  };

  const executeImport = async () => {
    setImporting(true);
    try {
      const result = await importInstances({ instances, overwrite, dryRun: false });
      setPreview(result);
      await onImported();
      const changed = result.createdIds.length + result.updatedIds.length;
      if (Object.keys(result.errors).length > 0) {
        message.warning(`已导入 ${changed} 个实例，${Object.keys(result.errors).length} 个失败`);
      } else {
        message.success(`成功导入 ${changed} 个实例`);
        close();
      }
    } catch {
      message.error('导入失败，请稍后重试');
    } finally {
      setImporting(false);
    }
  };

  return (
    <Modal
      title="导入实例配置"
      open={open}
      onCancel={close}
      width={680}
      footer={
        <Space>
          <Button onClick={close}>取消</Button>
          <Button
            disabled={!instances.length}
            loading={previewing}
            onClick={() => void previewImport()}
          >
            预检
          </Button>
          <Button
            type="primary"
            disabled={!instances.length}
            loading={importing}
            onClick={() => void executeImport()}
          >
            确认导入
          </Button>
        </Space>
      }
    >
      <Alert
        type="info"
        showIcon
        message="先预检，再导入"
        description="支持由控制台导出的 schemaVersion 1 JSON，也支持实例数组。预检不会修改任何数据。"
        style={{ marginBottom: 16 }}
      />
      <Upload.Dragger
        accept="application/json,.json"
        maxCount={1}
        fileList={[]}
        beforeUpload={(file) => {
          void readFile(file);
          return Upload.LIST_IGNORE;
        }}
      >
        <p className="ant-upload-drag-icon">
          <InboxOutlined />
        </p>
        <p className="ant-upload-text">点击或拖放实例 JSON 文件到此处</p>
        <p className="ant-upload-hint">最多 200 条记录，凭据字段仅包含服务端引用</p>
      </Upload.Dragger>
      {fileName && (
        <Space direction="vertical" size={8} style={{ width: '100%', marginTop: 16 }}>
          <span>
            文件：{fileName}（{instances.length} 个实例）
          </span>
          <Checkbox
            checked={overwrite}
            onChange={(event) => {
              setOverwrite(event.target.checked);
              setPreview(null);
            }}
          >
            覆盖 ID 相同的已有实例
          </Checkbox>
        </Space>
      )}
      {preview && <ResultSummary result={preview} />}
    </Modal>
  );
};

export default InstanceImportDialog;
