/* Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. */
import { useMemo, useRef, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Drawer,
  Flex,
  Popconfirm,
  Progress,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type { StudioUser } from '../api/studioUsers';
import { setStudioUserEnabled } from '../api/studioUsers';
import {
  eligibleUserStatusTargets,
  executeUserStatusBatch,
  type UserStatusBatchResult,
} from '../utils/studioUserStatusBatch';

interface Props {
  open: boolean;
  selectedUsers: StudioUser[];
  currentUserId?: number | null;
  onClose: () => void;
  onCompleted: () => Promise<void> | void;
}
type Action = 'ENABLE' | 'DISABLE';

export const UserStatusBatchDrawer = ({
  open,
  selectedUsers,
  currentUserId,
  onClose,
  onCompleted,
}: Props) => {
  const [action, setAction] = useState<Action>();
  const [running, setRunning] = useState(false);
  const [result, setResult] = useState<UserStatusBatchResult>();
  const operationRef = useRef(0);
  const enabled = action === 'ENABLE';
  const targets = useMemo(
    () => (action ? eligibleUserStatusTargets(selectedUsers, currentUserId, enabled) : []),
    [action, currentUserId, enabled, selectedUsers],
  );
  const selfSelected = selectedUsers.some((user) => user.id === currentUserId);

  const begin = async (nextAction: Action, retryUsers?: StudioUser[]) => {
    const operation = operationRef.current + 1;
    operationRef.current = operation;
    setAction(nextAction);
    setRunning(true);
    setResult(undefined);
    const targetEnabled = nextAction === 'ENABLE';
    const source =
      retryUsers ?? eligibleUserStatusTargets(selectedUsers, currentUserId, targetEnabled);
    const next = await executeUserStatusBatch(source, targetEnabled, setStudioUserEnabled, 4);
    if (operationRef.current !== operation) return;
    setResult(next);
    setRunning(false);
    await onCompleted();
  };
  const close = () => {
    if (running) return;
    operationRef.current += 1;
    setAction(undefined);
    setResult(undefined);
    onClose();
  };

  const userColumns: ColumnsType<StudioUser> = [
    { title: '用户名', dataIndex: 'username', key: 'username' },
    { title: '用户 ID', dataIndex: 'id', key: 'id', width: 100 },
    {
      title: '权限',
      dataIndex: 'admin',
      key: 'admin',
      width: 100,
      render: (admin: boolean) => (
        <Tag color={admin ? 'blue' : 'default'}>{admin ? '管理员' : '普通用户'}</Tag>
      ),
    },
    {
      title: '当前状态',
      dataIndex: 'enabled',
      key: 'enabled',
      width: 110,
      render: (value: boolean) => (
        <Tag color={value ? 'green' : 'default'}>{value ? '已启用' : '已禁用'}</Tag>
      ),
    },
  ];
  const failureColumns: ColumnsType<UserStatusBatchResult['failures'][number]> = [
    { title: '用户名', key: 'username', render: (_, row) => row.user.username },
    { title: '用户 ID', key: 'id', render: (_, row) => row.user.id, width: 100 },
    { title: '失败原因', dataIndex: 'error', key: 'error' },
  ];

  const succeeded = result?.successes.length ?? 0;
  const failed = result?.failures.length ?? 0;
  const completed = succeeded + failed;
  const progress = result?.changed
    ? Math.round((completed * 100) / result.changed)
    : running
      ? 0
      : 100;
  return (
    <Drawer
      title="批量管理用户状态"
      open={open}
      onClose={close}
      width={760}
      destroyOnHidden
      closable={!running}
      maskClosable={!running}
    >
      <Alert
        showIcon
        type="info"
        message="每次最多并发更新 4 个账号"
        description="每个账号使用现有状态接口独立提交；部分失败不会回滚已成功项，结果会逐项列出。当前登录账号永远不会加入禁用或启用批次。"
        style={{ marginBottom: 16 }}
      />
      {selfSelected && (
        <Alert
          showIcon
          type="warning"
          message="已从批次中排除当前登录账号"
          style={{ marginBottom: 16 }}
        />
      )}
      <Descriptions
        bordered
        size="small"
        column={3}
        style={{ marginBottom: 16 }}
        items={[
          { key: 'selected', label: '已选择', children: selectedUsers.length },
          {
            key: 'enable',
            label: '可启用',
            children: eligibleUserStatusTargets(selectedUsers, currentUserId, true).length,
          },
          {
            key: 'disable',
            label: '可禁用',
            children: eligibleUserStatusTargets(selectedUsers, currentUserId, false).length,
          },
        ]}
      />
      <Flex gap={12} style={{ marginBottom: 16 }}>
        <Popconfirm
          title={`确认启用 ${eligibleUserStatusTargets(selectedUsers, currentUserId, true).length} 个账号？`}
          disabled={
            running || eligibleUserStatusTargets(selectedUsers, currentUserId, true).length === 0
          }
          onConfirm={() => void begin('ENABLE')}
        >
          <Button
            type="primary"
            disabled={
              running || eligibleUserStatusTargets(selectedUsers, currentUserId, true).length === 0
            }
          >
            批量启用
          </Button>
        </Popconfirm>
        <Popconfirm
          title={`确认禁用 ${eligibleUserStatusTargets(selectedUsers, currentUserId, false).length} 个账号并注销其会话？`}
          disabled={
            running || eligibleUserStatusTargets(selectedUsers, currentUserId, false).length === 0
          }
          onConfirm={() => void begin('DISABLE')}
        >
          <Button
            danger
            disabled={
              running || eligibleUserStatusTargets(selectedUsers, currentUserId, false).length === 0
            }
          >
            批量禁用
          </Button>
        </Popconfirm>
      </Flex>
      {running && (
        <Card size="small" style={{ marginBottom: 16 }}>
          <Typography.Text>
            正在{enabled ? '启用' : '禁用'} {targets.length} 个账号…
          </Typography.Text>
          <Progress percent={progress} status="active" />
        </Card>
      )}
      {result && (
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <Alert
            showIcon
            type={failed ? 'warning' : 'success'}
            message={`批次完成：成功 ${succeeded}，失败 ${failed}，无需变更 ${result.skipped.length}`}
          />
          {result.failures.length > 0 && (
            <Card
              title="失败项"
              size="small"
              extra={
                <Button
                  onClick={() =>
                    void begin(
                      action ?? 'ENABLE',
                      result.failures.map((item) => item.user),
                    )
                  }
                >
                  重试失败项
                </Button>
              }
            >
              <Table
                rowKey={(row) => row.user.id}
                size="small"
                pagination={false}
                columns={failureColumns}
                dataSource={result.failures}
              />
            </Card>
          )}
          {result.successes.length > 0 && (
            <Card title="成功项" size="small">
              <Table
                rowKey="id"
                size="small"
                pagination={{ pageSize: 10 }}
                columns={userColumns}
                dataSource={result.successes.map((item) => item.updated)}
              />
            </Card>
          )}
        </Space>
      )}
      {!running && !result && (
        <Table
          rowKey="id"
          size="small"
          columns={userColumns}
          dataSource={selectedUsers.filter((user) => user.id !== currentUserId)}
          pagination={{ pageSize: 10 }}
        />
      )}
    </Drawer>
  );
};
export default UserStatusBatchDrawer;
