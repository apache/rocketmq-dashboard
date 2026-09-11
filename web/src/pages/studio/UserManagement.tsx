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
import { useCallback, useEffect, useRef, useState } from 'react';
import {
  Button,
  Card,
  Flex,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Statistic,
  Switch,
  Table,
  Tag,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { DownloadSimple, Key, Plus, SignOut } from '@phosphor-icons/react';
import { useNavigate } from 'react-router-dom';
import PageHeader from '../../components/PageHeader';
import InfoBanner from '../../components/InfoBanner';
import { changePassword } from '../../api/auth';
import {
  createStudioUser,
  getStudioUserSessionOverview,
  listAllStudioUsers as exportStudioUsers,
  listStudioUsers,
  resetStudioUserPassword,
  revokeStudioUserSessions,
  setStudioUserEnabled,
  type StudioUser,
  type StudioUserSessionOverview,
} from '../../api/studioUsers';
import useAuthStore from '../../stores/authStore';
import { buildCsv, downloadCsv, type CsvColumn } from '../../utils/download';
import { tableScrollX } from '../../utils/table';

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
const PAGE_SIZE_OPTIONS = [20, 50, 100];

type RoleFilter = 'admin' | 'reader';
type StatusFilter = 'enabled' | 'disabled';

const STUDIO_USER_EXPORT_COLUMNS: CsvColumn<StudioUser>[] = [
  { header: 'User ID', value: (user) => user.id },
  { header: 'Username', value: (user) => user.username },
  { header: 'Role', value: (user) => (user.admin ? 'Admin' : 'User') },
  { header: 'Status', value: (user) => (user.enabled ? 'Enabled' : 'Disabled') },
  { header: 'Active Sessions', value: (user) => user.activeSessionCount ?? 0 },
  {
    header: 'Last Session Seen At',
    value: (user) => dateTime(user.lastSessionSeenAt ?? undefined),
  },
  {
    header: 'Nearest Session Expires At',
    value: (user) => dateTime(user.nearestSessionExpiresAt ?? undefined),
  },
  { header: 'Password Changed At', value: (user) => dateTime(user.passwordChangedAt) },
  { header: 'Created At', value: (user) => dateTime(user.gmtCreate) },
  { header: 'Modified At', value: (user) => dateTime(user.gmtModified) },
];
const UserManagementPage = () => {
  const navigate = useNavigate();
  const admin = useAuthStore((state) => state.admin);
  const userId = useAuthStore((state) => state.userId);
  const clearAuth = useAuthStore((state) => state.logout);
  const [users, setUsers] = useState<StudioUser[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [search, setSearch] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const [roleFilter, setRoleFilter] = useState<RoleFilter>();
  const [statusFilter, setStatusFilter] = useState<StatusFilter>();
  const [loading, setLoading] = useState(false);
  const [sessionOverview, setSessionOverview] = useState<StudioUserSessionOverview | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [passwordTarget, setPasswordTarget] = useState<StudioUser | null>(null);
  const [userExporting, setUserExporting] = useState(false);
  const [mutatingUserIds, setMutatingUserIds] = useState<Set<number>>(() => new Set());
  const [createForm] = Form.useForm<CreateFormValues>();
  const [passwordForm] = Form.useForm<PasswordFormValues>();
  const requestSeqRef = useRef(0);
  const mutatingUserIdsRef = useRef(new Set<number>());

  useEffect(() => {
    const timer = window.setTimeout(() => setDebouncedSearch(search.trim()), 300);
    return () => window.clearTimeout(timer);
  }, [search]);

  const loadUsers = useCallback(async () => {
    if (!admin) {
      requestSeqRef.current += 1;
      setUsers([]);
      setTotal(0);
      setSessionOverview(null);
      return;
    }
    const requestId = ++requestSeqRef.current;
    Promise.resolve().then(() => {
      if (requestId === requestSeqRef.current) setLoading(true);
    });
    try {
      const [result, overview] = await Promise.all([
        listStudioUsers({
          search: debouncedSearch || undefined,
          admin: roleFilter === undefined ? undefined : roleFilter === 'admin',
          enabled: statusFilter === undefined ? undefined : statusFilter === 'enabled',
          page,
          pageSize,
        }),
        getStudioUserSessionOverview().catch(() => null),
      ]);
      if (requestId !== requestSeqRef.current) return;
      setSessionOverview(overview);
      if (result.items.length === 0 && result.total > 0 && page > 1) {
        const lastPage = Math.max(1, Math.ceil(result.total / result.size));
        if (page > lastPage) {
          setPage(lastPage);
          return;
        }
      }
      setUsers(result.items);
      setTotal(result.total);
    } catch {
      if (requestId === requestSeqRef.current) message.error('加载用户列表失败');
    } finally {
      if (requestId === requestSeqRef.current) setLoading(false);
    }
  }, [admin, debouncedSearch, page, pageSize, roleFilter, statusFilter]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      void loadUsers();
    }, 0);
    return () => window.clearTimeout(timer);
  }, [loadUsers]);

  useEffect(
    () => () => {
      requestSeqRef.current += 1;
    },
    [],
  );

  const createUser = async () => {
    const values = await createForm.validateFields();
    try {
      await createStudioUser(values);
      message.success('用户已创建');
      setCreateOpen(false);
      createForm.resetFields();
      if (page === 1) await loadUsers();
      else setPage(1);
    } catch {
      message.error('创建用户失败');
    }
  };

  /**
   * Runs one mutation for a single user behind the shared in-flight guard, so a row can never
   * have a status update and a session revocation overlapping. The guard is released on both the
   * success and the failure path.
   */
  const runUserMutation = async (
    targetUserId: number,
    action: () => Promise<void>,
    errorMessage: string,
  ) => {
    if (mutatingUserIdsRef.current.has(targetUserId)) return;
    mutatingUserIdsRef.current.add(targetUserId);
    setMutatingUserIds(new Set(mutatingUserIdsRef.current));
    try {
      await action();
    } catch {
      message.error(errorMessage);
    } finally {
      mutatingUserIdsRef.current.delete(targetUserId);
      setMutatingUserIds(new Set(mutatingUserIdsRef.current));
    }
  };

  const setEnabled = (record: StudioUser, enabled: boolean) =>
    runUserMutation(
      record.id,
      async () => {
        await setStudioUserEnabled(record.id, enabled);
        message.success(enabled ? '用户已启用' : '用户已禁用，全部会话已注销');
        await loadUsers();
      },
      '更新用户状态失败',
    );

  const updatePassword = async () => {
    if (!passwordTarget) return;
    const values = await passwordForm.validateFields();
    try {
      if (passwordTarget.id === userId) {
        await changePassword(values.currentPassword ?? '', values.newPassword);
        clearAuth();
        navigate('/login', { replace: true });
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

  const revokeSessions = (record: StudioUser) =>
    runUserMutation(
      record.id,
      async () => {
        const result = await revokeStudioUserSessions(record.id);
        if (result.revokedSessionCount > 0) {
          message.success(`已注销 ${result.revokedSessionCount} 个活跃会话`);
        } else {
          message.success('没有可注销的活跃会话');
        }
        if (record.id === userId && result.revokedSessionCount > 0) {
          clearAuth();
          navigate('/login', { replace: true });
          return;
        }
        await loadUsers();
      },
      '注销用户会话失败',
    );

  const openCreateUserModal = () => setCreateOpen(true);
  const handleExportUsers = useCallback(async () => {
    if (!admin) return;
    setUserExporting(true);
    try {
      const exportedUsers = await exportStudioUsers({
        search: search.trim() || undefined,
        admin: roleFilter === undefined ? undefined : roleFilter === 'admin',
        enabled: statusFilter === undefined ? undefined : statusFilter === 'enabled',
      });
      const today = new Date().toISOString().slice(0, 10);
      downloadCsv(
        `rocketmq-studio-users-${today}.csv`,
        buildCsv(STUDIO_USER_EXPORT_COLUMNS, exportedUsers),
      );
      message.success(`已导出 ${exportedUsers.length} 个用户`);
    } catch {
      message.error('导出用户列表失败，请稍后重试');
    }
    setUserExporting(false);
  }, [admin, roleFilter, search, statusFilter]);
  // Declared widths total 1116px, which stays inside the usable content width of a normal
  // 1440px viewport (220px Sider plus page and Card padding), so the table does not show a
  // horizontal scrollbar by default. Columns whose text can be longer than that truncate with
  // the full value on hover instead of wrapping.
  const columns: ColumnsType<StudioUser> = [
    { title: '用户名', dataIndex: 'username', width: 120, ellipsis: true },
    { title: '用户 ID', dataIndex: 'id', width: 88 },
    {
      title: '权限',
      dataIndex: 'admin',
      width: 92,
      render: (value: boolean) => (value ? <Tag color="blue">管理员</Tag> : <Tag>普通用户</Tag>),
    },
    {
      title: '状态',
      dataIndex: 'enabled',
      width: 92,
      render: (value: boolean) =>
        value ? <Tag color="green">已启用</Tag> : <Tag color="default">已禁用</Tag>,
    },
    {
      title: '活跃会话',
      dataIndex: 'activeSessionCount',
      width: 84,
      render: (value?: number) => {
        const count = value ?? 0;
        return <Tag color={count > 0 ? 'processing' : 'default'}>{count}</Tag>;
      },
    },
    {
      title: '最近活跃',
      dataIndex: 'lastSessionSeenAt',
      width: 140,
      ellipsis: true,
      render: dateTime,
    },
    {
      title: '最近过期',
      dataIndex: 'nearestSessionExpiresAt',
      width: 140,
      ellipsis: true,
      render: dateTime,
    },
    {
      title: '创建时间',
      dataIndex: 'gmtCreate',
      width: 140,
      ellipsis: true,
      render: dateTime,
    },
    {
      title: '操作',
      key: 'actions',
      width: 220,
      render: (_, record) => (
        <Space>
          <Button size="small" icon={<Key size={14} />} onClick={() => setPasswordTarget(record)}>
            改密
          </Button>
          <Popconfirm
            title={`注销 ${record.username} 的活跃会话？`}
            description="用户需要重新登录，账号状态不会改变。"
            okText="注销"
            cancelText="取消"
            okButtonProps={{ danger: true }}
            disabled={(record.activeSessionCount ?? 0) === 0}
            onConfirm={() => void revokeSessions(record)}
          >
            <Button
              size="small"
              danger
              icon={<SignOut size={14} />}
              disabled={(record.activeSessionCount ?? 0) === 0}
              loading={mutatingUserIds.has(record.id)}
            >
              会话
            </Button>
          </Popconfirm>
          <Switch
            checked={record.enabled}
            loading={mutatingUserIds.has(record.id)}
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
            <Space>
              <Button
                icon={<DownloadSimple size={16} />}
                disabled={loading || total === 0}
                loading={userExporting}
                onClick={() => void handleExportUsers()}
              >
                导出
              </Button>
              <Button type="primary" icon={<Plus size={16} />} onClick={openCreateUserModal}>
                新建用户
              </Button>
            </Space>
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
              activeSessionCount: 0,
              passwordChangedAt: '',
              gmtCreate: '',
              gmtModified: '',
            })
          }
        >
          修改我的密码
        </Button>
      </Card>
      {admin && sessionOverview && (
        <Card title="会话概览" style={{ marginBottom: 16 }}>
          <Flex gap={32} wrap>
            <Statistic title="活跃会话" value={sessionOverview.activeSessionCount} />
            <Statistic title="活跃用户" value={sessionOverview.activeUserCount} />
            <Statistic
              title={`未来 ${sessionOverview.expiringSoonWindowMinutes} 分钟过期`}
              value={sessionOverview.expiringSoonSessionCount}
            />
            <Statistic
              title={`${sessionOverview.staleSessionThresholdMinutes} 分钟未活跃`}
              value={sessionOverview.staleSessionCount}
            />
          </Flex>
        </Card>
      )}
      {admin && (
        <Card>
          <Flex gap={12} wrap style={{ marginBottom: 16 }}>
            <Input.Search
              allowClear
              placeholder="搜索用户名"
              style={{ width: 240 }}
              value={search}
              onChange={(event) => {
                setSearch(event.target.value);
                setPage(1);
              }}
            />
            <Select<RoleFilter>
              allowClear
              aria-label="按权限筛选"
              placeholder="全部权限"
              style={{ width: 140 }}
              value={roleFilter}
              onChange={(value) => {
                setRoleFilter(value);
                setPage(1);
              }}
              options={[
                { label: '管理员', value: 'admin' },
                { label: '普通用户', value: 'reader' },
              ]}
            />
            <Select<StatusFilter>
              allowClear
              aria-label="按状态筛选"
              placeholder="全部状态"
              style={{ width: 140 }}
              value={statusFilter}
              onChange={(value) => {
                setStatusFilter(value);
                setPage(1);
              }}
              options={[
                { label: '已启用', value: 'enabled' },
                { label: '已禁用', value: 'disabled' },
              ]}
            />
          </Flex>
          <Table
            rowKey="id"
            loading={loading}
            columns={columns}
            dataSource={users}
            tableLayout="fixed"
            scroll={{ x: tableScrollX(columns) }}
            pagination={{
              current: page,
              pageSize,
              total,
              showSizeChanger: true,
              pageSizeOptions: PAGE_SIZE_OPTIONS.map(String),
              showTotal: (count) => `共 ${count} 个用户`,
              onChange: (nextPage, nextPageSize) => {
                if (nextPageSize !== pageSize) {
                  setPage(1);
                  setPageSize(nextPageSize);
                } else {
                  setPage(nextPage);
                }
              },
            }}
          />
        </Card>
      )}

      <Modal
        title="新建 Studio 用户"
        open={createOpen}
        onOk={() => void createUser()}
        onCancel={() => setCreateOpen(false)}
      >
        <Form form={createForm} layout="vertical" initialValues={{ admin: false }}>
          <Form.Item name="username" label="用户名" rules={[{ required: true }, { max: 128 }]}>
            <Input autoComplete="username" />
          </Form.Item>
          <Form.Item
            name="password"
            label="初始密码"
            rules={[{ required: true }, { min: 8, message: '密码至少 8 位' }]}
          >
            <Input.Password autoComplete="new-password" />
          </Form.Item>
          <Form.Item name="admin" label="管理员权限" valuePropName="checked">
            <Switch checkedChildren="管理员" unCheckedChildren="普通用户" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={
          passwordTarget?.id === userId
            ? '修改我的密码'
            : `重置 ${passwordTarget?.username ?? ''} 的密码`
        }
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
          <Form.Item
            name="newPassword"
            label="新密码"
            rules={[{ required: true }, { min: 8, message: '密码至少 8 位' }]}
          >
            <Input.Password autoComplete="new-password" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default UserManagementPage;
