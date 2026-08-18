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
import { useCallback, useEffect, useState } from 'react';
import { Button, Card, Form, Input, Modal, Space, Switch, Table, Tag, message } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { Key, Plus } from '@phosphor-icons/react';
import PageHeader from '../../components/PageHeader';
import InfoBanner from '../../components/InfoBanner';
import { changePassword } from '../../api/auth';
import {
  createStudioUser,
  listStudioUsers,
  resetStudioUserPassword,
  setStudioUserEnabled,
  type StudioUser,
} from '../../api/studioUsers';
import useAuthStore from '../../stores/authStore';

interface CreateFormValues {
  username: string;
  password: string;
  admin: boolean;
}

interface PasswordFormValues {
  currentPassword?: string;
  newPassword: string;
}

const dateTime = (value?: string) => (value ? new Date(value).toLocaleString() : '-');

const UserManagementPage = () => {
  const admin = useAuthStore((state) => state.admin);
  const userId = useAuthStore((state) => state.userId);
  const [users, setUsers] = useState<StudioUser[]>([]);
  const [loading, setLoading] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [passwordTarget, setPasswordTarget] = useState<StudioUser | null>(null);
  const [createForm] = Form.useForm<CreateFormValues>();
  const [passwordForm] = Form.useForm<PasswordFormValues>();

  const loadUsers = useCallback(async () => {
    if (!admin) return;
    setLoading(true);
    try {
      setUsers(await listStudioUsers());
    } catch {
      message.error('加载用户列表失败');
    } finally {
      setLoading(false);
    }
  }, [admin]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      void loadUsers();
    }, 0);
    return () => window.clearTimeout(timer);
  }, [loadUsers]);

  const createUser = async () => {
    const values = await createForm.validateFields();
    try {
      await createStudioUser(values);
      message.success('用户已创建');
      setCreateOpen(false);
      createForm.resetFields();
      await loadUsers();
    } catch {
      message.error('创建用户失败');
    }
  };

  const setEnabled = async (record: StudioUser, enabled: boolean) => {
    try {
      await setStudioUserEnabled(record.id, enabled);
      message.success(enabled ? '用户已启用' : '用户已禁用，全部会话已注销');
      await loadUsers();
    } catch {
      message.error('更新用户状态失败');
    }
  };

  const updatePassword = async () => {
    if (!passwordTarget) return;
    const values = await passwordForm.validateFields();
    try {
      if (passwordTarget.id === userId) {
        await changePassword(values.currentPassword ?? '', values.newPassword);
        message.success('密码已修改，请使用新密码重新登录');
      } else {
        await resetStudioUserPassword(passwordTarget.id, values.newPassword);
        message.success('密码已重置，用户的现有会话已注销');
      }
      setPasswordTarget(null);
      passwordForm.resetFields();
    } catch {
      message.error('修改密码失败');
    }
  };

  const columns: ColumnsType<StudioUser> = [
    { title: '用户名', dataIndex: 'username' },
    { title: '用户 ID', dataIndex: 'id', width: 100 },
    {
      title: '权限',
      dataIndex: 'admin',
      render: (value: boolean) => (value ? <Tag color="blue">管理员</Tag> : <Tag>普通用户</Tag>),
    },
    {
      title: '状态',
      dataIndex: 'enabled',
      render: (value: boolean) => (value ? <Tag color="green">已启用</Tag> : <Tag color="default">已禁用</Tag>),
    },
    { title: '创建时间', dataIndex: 'gmtCreate', render: dateTime },
    {
      title: '操作',
      key: 'actions',
      render: (_, record) => (
        <Space>
          <Button size="small" icon={<Key size={14} />} onClick={() => setPasswordTarget(record)}>
            改密
          </Button>
          <Switch
            checked={record.enabled}
            checkedChildren="启用"
            unCheckedChildren="禁用"
            onChange={(enabled) => void setEnabled(record, enabled)}
          />
        </Space>
      ),
    },
  ];

  return (
    <div style={{ padding: 24 }}>
      <PageHeader
        title="用户管理"
        subtitle="Studio 本地账号、会话与密码管理"
        extra={
          admin ? (
            <Button type="primary" icon={<Plus size={16} />} onClick={() => setCreateOpen(true)}>
              新建用户
            </Button>
          ) : undefined
        }
      />
      {!admin && (
        <InfoBanner
          title="当前账号不是管理员"
          description="你可以修改自己的密码；用户列表和账号状态仅对管理员开放。"
        />
      )}
      <Card title="我的账号" style={{ marginBottom: 16 }}>
        <Button
          icon={<Key size={16} />}
          disabled={!userId}
          onClick={() =>
            setPasswordTarget({
              id: userId ?? 0,
              username: '',
              admin: !!admin,
              enabled: true,
              passwordChangedAt: '',
              gmtCreate: '',
              gmtModified: '',
            })
          }
        >
          修改我的密码
        </Button>
      </Card>
      {admin && <Table rowKey="id" loading={loading} columns={columns} dataSource={users} />}

      <Modal title="新建 Studio 用户" open={createOpen} onOk={() => void createUser()} onCancel={() => setCreateOpen(false)}>
        <Form form={createForm} layout="vertical" initialValues={{ admin: false }}>
          <Form.Item name="username" label="用户名" rules={[{ required: true }, { max: 128 }]}>
            <Input autoComplete="username" />
          </Form.Item>
          <Form.Item name="password" label="初始密码" rules={[{ required: true }, { min: 8, message: '密码至少 8 位' }]}>
            <Input.Password autoComplete="new-password" />
          </Form.Item>
          <Form.Item name="admin" label="管理员权限" valuePropName="checked">
            <Switch checkedChildren="管理员" unCheckedChildren="普通用户" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={passwordTarget?.id === userId ? '修改我的密码' : `重置 ${passwordTarget?.username ?? ''} 的密码`}
        open={passwordTarget !== null}
        onOk={() => void updatePassword()}
        onCancel={() => {
          setPasswordTarget(null);
          passwordForm.resetFields();
        }}
      >
        <Form form={passwordForm} layout="vertical">
          {passwordTarget?.id === userId && (
            <Form.Item name="currentPassword" label="当前密码" rules={[{ required: true }]}>
              <Input.Password autoComplete="current-password" />
            </Form.Item>
          )}
          <Form.Item name="newPassword" label="新密码" rules={[{ required: true }, { min: 8, message: '密码至少 8 位' }]}>
            <Input.Password autoComplete="new-password" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default UserManagementPage;
