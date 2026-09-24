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
  Descriptions,
  Drawer,
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
import {
  ArrowClockwise,
  DownloadSimple,
  Key,
  ListBullets,
  Plus,
  SignOut,
} from '@phosphor-icons/react';
import { useNavigate } from 'react-router-dom';
import PageHeader from '../../components/PageHeader';
import InfoBanner from '../../components/InfoBanner';
import { changePassword } from '../../api/auth';
import {
  createStudioUser,
  getStudioUserSessionOverview,
  listAllStudioUsers as exportStudioUsers,
  listStudioUserSessions,
  listStudioUsers,
  resetStudioUserPassword,
  revokeStudioUserSessions,
  setStudioUserEnabled,
  type StudioUser,
  type StudioUserSessionDetail,
  type StudioUserSessionOverview,
} from '../../api/studioUsers';
import useAuthStore from '../../stores/authStore';
import { buildCsv, downloadCsv, type CsvColumn } from '../../utils/download';
import { formatDelay, formatUtcDateTime } from '../../utils/format';
import { tableScrollX } from '../../utils/table';
import { useLang } from '../../i18n/LangContext';

interface CreateFormValues {
  username: string;
  password: string;
  admin: boolean;
}

interface PasswordFormValues {
  currentPassword?: string;
  newPassword: string;
}

// Studio user and session APIs serialize UTC LocalDateTime values without an offset,
// so the timestamps have to be parsed as UTC before rendering in the viewer's zone.
const dateTime = (value?: string | null) => formatUtcDateTime(value);
const durationText = (value?: number | null) => (value == null ? '-' : formatDelay(value));
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

const SessionStatusTags = ({ session }: { session: StudioUserSessionDetail }) => {
  const { t } = useLang();
  return (
    <Space size={4} wrap>
      <Tag color="processing">{t('userMgmt.tagActive')}</Tag>
      {session.expiringSoon && <Tag color="gold">{t('userMgmt.tagExpiringSoon')}</Tag>}
      {session.stale && <Tag color="orange">{t('userMgmt.tagStale')}</Tag>}
    </Space>
  );
};

const UserManagementPage = () => {
  const { t } = useLang();
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
  const [sessionDrawerUser, setSessionDrawerUser] = useState<StudioUser | null>(null);
  const [sessionDetails, setSessionDetails] = useState<StudioUserSessionDetail[]>([]);
  const [sessionDetailsLoading, setSessionDetailsLoading] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [passwordTarget, setPasswordTarget] = useState<StudioUser | null>(null);
  const [userExporting, setUserExporting] = useState(false);
  const [mutatingUserIds, setMutatingUserIds] = useState<Set<number>>(() => new Set());
  const [createForm] = Form.useForm<CreateFormValues>();
  const [passwordForm] = Form.useForm<PasswordFormValues>();
  const requestSeqRef = useRef(0);
  const sessionDetailsRequestSeqRef = useRef(0);
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
      setSessionDrawerUser(null);
      setSessionDetails([]);
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
      if (requestId === requestSeqRef.current) message.error(t('userMgmt.loadFailed'));
    } finally {
      if (requestId === requestSeqRef.current) setLoading(false);
    }
  }, [admin, debouncedSearch, page, pageSize, roleFilter, statusFilter, t]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      void loadUsers();
    }, 0);
    return () => window.clearTimeout(timer);
  }, [loadUsers]);

  useEffect(
    () => () => {
      requestSeqRef.current += 1;
      sessionDetailsRequestSeqRef.current += 1;
    },
    [],
  );

  const loadSessionDetails = useCallback(
    async (record: StudioUser) => {
      const requestId = ++sessionDetailsRequestSeqRef.current;
      setSessionDetailsLoading(true);
      try {
        const details = await listStudioUserSessions(record.id);
        if (requestId !== sessionDetailsRequestSeqRef.current) return;
        setSessionDetails(details);
        setSessionDrawerUser((current) =>
          current?.id === record.id ? { ...current, activeSessionCount: details.length } : current,
        );
      } catch {
        if (requestId === sessionDetailsRequestSeqRef.current) {
          setSessionDetails([]);
          message.error(t('userMgmt.loadSessionsFailed'));
        }
      } finally {
        if (requestId === sessionDetailsRequestSeqRef.current) {
          setSessionDetailsLoading(false);
        }
      }
    },
    [t],
  );

  const openSessionDrawer = (record: StudioUser) => {
    setSessionDrawerUser(record);
    setSessionDetails([]);
    void loadSessionDetails(record);
  };

  const closeSessionDrawer = () => {
    sessionDetailsRequestSeqRef.current += 1;
    setSessionDrawerUser(null);
    setSessionDetails([]);
    setSessionDetailsLoading(false);
  };

  const createUser = async () => {
    const values = await createForm.validateFields();
    try {
      await createStudioUser(values);
      message.success(t('userMgmt.userCreated'));
      setCreateOpen(false);
      createForm.resetFields();
      if (page === 1) await loadUsers();
      else setPage(1);
    } catch {
      message.error(t('userMgmt.createFailed'));
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
        message.success(enabled ? t('userMgmt.userEnabled') : t('userMgmt.userDisabled'));
        await loadUsers();
      },
      t('userMgmt.updateStatusFailed'),
    );

  const updatePassword = async () => {
    if (!passwordTarget) return;
    const values = await passwordForm.validateFields();
    try {
      if (passwordTarget.id === userId) {
        await changePassword(values.currentPassword ?? '', values.newPassword);
        clearAuth();
        navigate('/login', { replace: true });
        message.success(t('userMgmt.passwordChanged'));
      } else {
        await resetStudioUserPassword(passwordTarget.id, values.newPassword);
        message.success(t('userMgmt.passwordReset'));
      }
      setPasswordTarget(null);
      passwordForm.resetFields();
    } catch {
      message.error(t('userMgmt.changePasswordFailed'));
    }
  };

  const revokeSessions = (record: StudioUser) =>
    runUserMutation(
      record.id,
      async () => {
        const result = await revokeStudioUserSessions(record.id);
        if (result.revokedSessionCount > 0) {
          message.success(t('userMgmt.revokedCount', { count: result.revokedSessionCount }));
        } else {
          message.success(t('userMgmt.nothingToRevoke'));
        }
        if (record.id === userId && result.revokedSessionCount > 0) {
          clearAuth();
          navigate('/login', { replace: true });
          return;
        }
        if (sessionDrawerUser?.id === record.id) {
          await loadSessionDetails({ ...record, activeSessionCount: 0 });
        }
        await loadUsers();
      },
      t('userMgmt.revokeFailed'),
    );

  const openCreateUserModal = () => {
    createForm.resetFields();
    setCreateOpen(true);
  };
  const handleExportUsers = useCallback(async () => {
    if (!admin) return;
    setUserExporting(true);
    try {
      const exportedUsers = await exportStudioUsers({
        // The table only ever shows the debounced (committed) search; exporting the live
        // input would produce a CSV for a query the user never saw displayed.
        search: debouncedSearch || undefined,
        admin: roleFilter === undefined ? undefined : roleFilter === 'admin',
        enabled: statusFilter === undefined ? undefined : statusFilter === 'enabled',
      });
      const today = new Date().toISOString().slice(0, 10);
      downloadCsv(
        `rocketmq-studio-users-${today}.csv`,
        buildCsv(STUDIO_USER_EXPORT_COLUMNS, exportedUsers),
      );
      message.success(t('userMgmt.exportedCount', { count: exportedUsers.length }));
    } catch {
      message.error(t('userMgmt.exportFailed'));
    }
    setUserExporting(false);
  }, [admin, debouncedSearch, roleFilter, statusFilter, t]);
  const sessionDetailColumns: ColumnsType<StudioUserSessionDetail> = [
    { title: t('userMgmt.sessionId'), dataIndex: 'id', width: 96 },
    {
      title: t('common.status'),
      key: 'status',
      width: 172,
      render: (_, record) => <SessionStatusTags session={record} />,
    },
    {
      title: t('userMgmt.lastActive'),
      dataIndex: 'lastSeenAt',
      width: 160,
      ellipsis: true,
      render: dateTime,
    },
    {
      title: t('userMgmt.idleFor'),
      dataIndex: 'idleSeconds',
      width: 112,
      render: durationText,
    },
    {
      title: t('userMgmt.expiresAt'),
      dataIndex: 'expiresAt',
      width: 160,
      ellipsis: true,
      render: dateTime,
    },
    {
      title: t('userMgmt.remainingValidity'),
      dataIndex: 'remainingSeconds',
      width: 126,
      render: durationText,
    },
    {
      title: t('userMgmt.createdAt'),
      dataIndex: 'gmtCreate',
      width: 160,
      ellipsis: true,
      render: dateTime,
    },
  ];
  // Declared widths total 1116px, which stays inside the usable content width of a normal
  // 1440px viewport (220px Sider plus page and Card padding), so the table does not show a
  // horizontal scrollbar by default. Columns whose text can be longer than that truncate with
  // the full value on hover instead of wrapping.
  const columns: ColumnsType<StudioUser> = [
    { title: t('userMgmt.username'), dataIndex: 'username', width: 120, ellipsis: true },
    { title: t('userMgmt.userId'), dataIndex: 'id', width: 80 },
    {
      title: t('userMgmt.role'),
      dataIndex: 'admin',
      width: 88,
      render: (value: boolean) =>
        value ? (
          <Tag color="blue">{t('userMgmt.roleAdmin')}</Tag>
        ) : (
          <Tag>{t('userMgmt.roleUser')}</Tag>
        ),
    },
    {
      title: t('common.status'),
      dataIndex: 'enabled',
      width: 88,
      render: (value: boolean) =>
        value ? (
          <Tag color="green">{t('common.enabled')}</Tag>
        ) : (
          <Tag color="default">{t('common.disabled')}</Tag>
        ),
    },
    {
      title: t('userMgmt.activeSessions'),
      dataIndex: 'activeSessionCount',
      width: 80,
      render: (value?: number) => {
        const count = value ?? 0;
        return <Tag color={count > 0 ? 'processing' : 'default'}>{count}</Tag>;
      },
    },
    {
      title: t('userMgmt.lastActive'),
      dataIndex: 'lastSessionSeenAt',
      width: 132,
      ellipsis: true,
      render: dateTime,
    },
    {
      title: t('userMgmt.nearestExpiry'),
      dataIndex: 'nearestSessionExpiresAt',
      width: 132,
      ellipsis: true,
      render: dateTime,
    },
    {
      title: t('userMgmt.createdAt'),
      dataIndex: 'gmtCreate',
      width: 132,
      ellipsis: true,
      render: dateTime,
    },
    {
      title: t('common.actions'),
      key: 'actions',
      width: 264,
      render: (_, record) => (
        <Space size={4}>
          <Button size="small" icon={<Key size={14} />} onClick={() => setPasswordTarget(record)}>
            {t('userMgmt.changePassword')}
          </Button>
          <Button
            size="small"
            icon={<ListBullets size={14} />}
            onClick={() => openSessionDrawer(record)}
          >
            {t('userMgmt.sessions')}
          </Button>
          <Popconfirm
            title={t('userMgmt.revokeConfirm', { username: record.username })}
            description={t('userMgmt.revokeDescription')}
            okText={t('userMgmt.revoke')}
            cancelText={t('common.cancel')}
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
              {t('userMgmt.revoke')}
            </Button>
          </Popconfirm>
          <Switch
            checked={record.enabled}
            loading={mutatingUserIds.has(record.id)}
            checkedChildren={t('userMgmt.enable')}
            unCheckedChildren={t('userMgmt.disable')}
            onChange={(enabled) => void setEnabled(record, enabled)}
          />
        </Space>
      ),
    },
  ];

  return (
    <div style={{ padding: 24 }}>
      <PageHeader
        title={t('userMgmt.title')}
        subtitle={t('userMgmt.subtitle')}
        extra={
          admin ? (
            <Space>
              <Button
                icon={<DownloadSimple size={16} />}
                disabled={loading || total === 0}
                loading={userExporting}
                onClick={() => void handleExportUsers()}
              >
                {t('common.export')}
              </Button>
              <Button type="primary" icon={<Plus size={16} />} onClick={openCreateUserModal}>
                {t('userMgmt.createUser')}
              </Button>
            </Space>
          ) : undefined
        }
      />
      {!admin && (
        <InfoBanner
          title={t('userMgmt.notAdminTitle')}
          description={t('userMgmt.notAdminDescription')}
        />
      )}
      <Card title={t('userMgmt.myAccount')} style={{ marginBottom: 16 }}>
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
          {t('userMgmt.changeMyPassword')}
        </Button>
      </Card>
      {admin && sessionOverview && (
        <Card title={t('userMgmt.sessionOverview')} style={{ marginBottom: 16 }}>
          <Flex gap={32} wrap>
            <Statistic
              title={t('userMgmt.activeSessions')}
              value={sessionOverview.activeSessionCount}
            />
            <Statistic title={t('userMgmt.activeUsers')} value={sessionOverview.activeUserCount} />
            <Statistic
              title={t('userMgmt.expiringWithin', {
                minutes: sessionOverview.expiringSoonWindowMinutes,
              })}
              value={sessionOverview.expiringSoonSessionCount}
            />
            <Statistic
              title={t('userMgmt.idleMinutes', {
                minutes: sessionOverview.staleSessionThresholdMinutes,
              })}
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
              placeholder={t('userMgmt.searchPlaceholder')}
              style={{ width: 240 }}
              value={search}
              onChange={(event) => {
                setSearch(event.target.value);
                setPage(1);
              }}
            />
            <Select<RoleFilter>
              allowClear
              aria-label={t('userMgmt.filterByRole')}
              placeholder={t('userMgmt.allRoles')}
              style={{ width: 140 }}
              value={roleFilter}
              onChange={(value) => {
                setRoleFilter(value);
                setPage(1);
              }}
              options={[
                { label: t('userMgmt.roleAdmin'), value: 'admin' },
                { label: t('userMgmt.roleUser'), value: 'reader' },
              ]}
            />
            <Select<StatusFilter>
              allowClear
              aria-label={t('userMgmt.filterByStatus')}
              placeholder={t('userMgmt.allStatuses')}
              style={{ width: 140 }}
              value={statusFilter}
              onChange={(value) => {
                setStatusFilter(value);
                setPage(1);
              }}
              options={[
                { label: t('common.enabled'), value: 'enabled' },
                { label: t('common.disabled'), value: 'disabled' },
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
              showTotal: (count) => t('userMgmt.totalUsers', { count }),
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

      <Drawer
        title={
          sessionDrawerUser
            ? t('userMgmt.sessionsOf', { username: sessionDrawerUser.username })
            : t('userMgmt.userSessions')
        }
        width={1040}
        open={sessionDrawerUser !== null}
        onClose={closeSessionDrawer}
        destroyOnHidden
        extra={
          sessionDrawerUser ? (
            <Space>
              <Button
                icon={<ArrowClockwise size={16} />}
                loading={sessionDetailsLoading}
                onClick={() => void loadSessionDetails(sessionDrawerUser)}
              >
                {t('common.refresh')}
              </Button>
              <Popconfirm
                title={t('userMgmt.revokeConfirm', { username: sessionDrawerUser.username })}
                description={t('userMgmt.revokeDescription')}
                okText={t('userMgmt.revoke')}
                cancelText={t('common.cancel')}
                okButtonProps={{ danger: true }}
                disabled={sessionDetails.length === 0}
                onConfirm={() => void revokeSessions(sessionDrawerUser)}
              >
                <Button
                  danger
                  icon={<SignOut size={16} />}
                  disabled={sessionDetails.length === 0}
                  loading={mutatingUserIds.has(sessionDrawerUser.id)}
                >
                  {t('userMgmt.revokeAll')}
                </Button>
              </Popconfirm>
            </Space>
          ) : undefined
        }
      >
        {sessionDrawerUser && (
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <Descriptions
              bordered
              size="small"
              column={2}
              items={[
                { key: 'userId', label: t('userMgmt.userId'), children: sessionDrawerUser.id },
                {
                  key: 'role',
                  label: t('userMgmt.role'),
                  children: sessionDrawerUser.admin
                    ? t('userMgmt.roleAdmin')
                    : t('userMgmt.roleUser'),
                },
                {
                  key: 'activeSessionCount',
                  label: t('userMgmt.activeSessions'),
                  children: sessionDetailsLoading ? '-' : sessionDetails.length,
                },
                {
                  key: 'status',
                  label: t('userMgmt.accountStatus'),
                  children: sessionDrawerUser.enabled ? t('common.enabled') : t('common.disabled'),
                },
                {
                  key: 'lastSessionSeenAt',
                  label: t('userMgmt.lastActive'),
                  children: dateTime(sessionDrawerUser.lastSessionSeenAt),
                },
                {
                  key: 'nearestSessionExpiresAt',
                  label: t('userMgmt.nearestExpiry'),
                  children: dateTime(sessionDrawerUser.nearestSessionExpiresAt),
                },
                {
                  key: 'passwordChangedAt',
                  label: t('userMgmt.passwordChangedAt'),
                  children: dateTime(sessionDrawerUser.passwordChangedAt),
                },
              ]}
            />
            <Table
              rowKey="id"
              loading={sessionDetailsLoading}
              columns={sessionDetailColumns}
              dataSource={sessionDetails}
              tableLayout="fixed"
              pagination={false}
              scroll={{ x: tableScrollX(sessionDetailColumns), y: 420 }}
              locale={{ emptyText: t('userMgmt.noActiveSessions') }}
            />
          </Space>
        )}
      </Drawer>

      <Modal
        title={t('userMgmt.createTitle')}
        open={createOpen}
        onOk={() => void createUser()}
        onCancel={() => {
          setCreateOpen(false);
          createForm.resetFields();
        }}
      >
        <Form form={createForm} layout="vertical" initialValues={{ admin: false }}>
          <Form.Item
            name="username"
            label={t('userMgmt.username')}
            rules={[{ required: true }, { max: 128 }]}
          >
            <Input autoComplete="username" />
          </Form.Item>
          <Form.Item
            name="password"
            label={t('userMgmt.initialPassword')}
            rules={[{ required: true }, { min: 8, message: t('userMgmt.passwordMinLength') }]}
          >
            <Input.Password autoComplete="new-password" />
          </Form.Item>
          <Form.Item name="admin" label={t('userMgmt.adminAccess')} valuePropName="checked">
            <Switch
              checkedChildren={t('userMgmt.roleAdmin')}
              unCheckedChildren={t('userMgmt.roleUser')}
            />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={
          passwordTarget?.id === userId
            ? t('userMgmt.changeMyPassword')
            : t('userMgmt.resetPasswordOf', { username: passwordTarget?.username ?? '' })
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
            <Form.Item
              name="currentPassword"
              label={t('userMgmt.currentPassword')}
              rules={[{ required: true }]}
            >
              <Input.Password autoComplete="current-password" />
            </Form.Item>
          )}
          <Form.Item
            name="newPassword"
            label={t('userMgmt.newPassword')}
            rules={[{ required: true }, { min: 8, message: t('userMgmt.passwordMinLength') }]}
          >
            <Input.Password autoComplete="new-password" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default UserManagementPage;
