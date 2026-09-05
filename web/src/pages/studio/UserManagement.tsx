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
  Select,
  Space,
  Switch,
  Table,
  Tag,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { DownloadSimple, Key, Plus } from '@phosphor-icons/react';
import { useNavigate } from 'react-router-dom';
import PageHeader from '../../components/PageHeader';
import InfoBanner from '../../components/InfoBanner';
import { changePassword } from '../../api/auth';
import {
  createStudioUser,
  listAllStudioUsers as exportStudioUsers,
  listStudioUsers,
  resetStudioUserPassword,
  setStudioUserEnabled,
  type StudioUser,
} from '../../api/studioUsers';
import useAuthStore from '../../stores/authStore';
import { buildCsv, downloadCsv, type CsvColumn } from '../../utils/download';
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

const dateTime = (value?: string) => (value ? new Date(value).toLocaleString() : '-');
const PAGE_SIZE_OPTIONS = [20, 50, 100];

type RoleFilter = 'admin' | 'reader';
type StatusFilter = 'enabled' | 'disabled';

const STUDIO_USER_EXPORT_COLUMNS: CsvColumn<StudioUser>[] = [
  { header: 'User ID', value: (user) => user.id },
  { header: 'Username', value: (user) => user.username },
  { header: 'Role', value: (user) => (user.admin ? 'Admin' : 'User') },
  { header: 'Status', value: (user) => (user.enabled ? 'Enabled' : 'Disabled') },
  { header: 'Password Changed At', value: (user) => dateTime(user.passwordChangedAt) },
  { header: 'Created At', value: (user) => dateTime(user.gmtCreate) },
  { header: 'Modified At', value: (user) => dateTime(user.gmtModified) },
];
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
      return;
    }
    const requestId = ++requestSeqRef.current;
    Promise.resolve().then(() => {
      if (requestId === requestSeqRef.current) setLoading(true);
    });
    try {
      const result = await listStudioUsers({
        search: debouncedSearch || undefined,
        admin: roleFilter === undefined ? undefined : roleFilter === 'admin',
        enabled: statusFilter === undefined ? undefined : statusFilter === 'enabled',
        page,
        pageSize,
      });
      if (requestId !== requestSeqRef.current) return;
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
      if (requestId === requestSeqRef.current) message.error(t('users.loadFailed'));
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
    },
    [],
  );

  const createUser = async () => {
    const values = await createForm.validateFields();
    try {
      await createStudioUser(values);
      message.success(t('users.created'));
      setCreateOpen(false);
      createForm.resetFields();
      if (page === 1) await loadUsers();
      else setPage(1);
    } catch {
      message.error(t('users.createFailed'));
    }
  };

  const setEnabled = async (record: StudioUser, enabled: boolean) => {
    if (mutatingUserIdsRef.current.has(record.id)) return;
    mutatingUserIdsRef.current.add(record.id);
    setMutatingUserIds(new Set(mutatingUserIdsRef.current));
    try {
      await setStudioUserEnabled(record.id, enabled);
      message.success(enabled ? t('users.enabledToast') : t('users.disabledToast'));
      await loadUsers();
    } catch {
      message.error(t('users.updateFailed'));
    } finally {
      mutatingUserIdsRef.current.delete(record.id);
      setMutatingUserIds(new Set(mutatingUserIdsRef.current));
    }
  };

  const updatePassword = async () => {
    if (!passwordTarget) return;
    const values = await passwordForm.validateFields();
    try {
      if (passwordTarget.id === userId) {
        await changePassword(values.currentPassword ?? '', values.newPassword);
        clearAuth();
        navigate('/login', { replace: true });
        message.success(t('users.passwordChangedToast'));
      } else {
        await resetStudioUserPassword(passwordTarget.id, values.newPassword);
        message.success(t('users.passwordResetToast'));
      }
      setPasswordTarget(null);
      passwordForm.resetFields();
    } catch {
      message.error(t('users.passwordChangeFailed'));
    }
  };
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
      message.success(t('users.exportedToast', { count: String(exportedUsers.length) }));
    } catch {
      message.error(t('users.exportFailed'));
    }
    setUserExporting(false);
  }, [admin, roleFilter, search, statusFilter, t]);
  const columns: ColumnsType<StudioUser> = [
    { title: t('users.username'), dataIndex: 'username' },
    { title: t('users.userId'), dataIndex: 'id', width: 100 },
    {
      title: t('users.role'),
      dataIndex: 'admin',
      render: (value: boolean) =>
        value ? <Tag color="blue">{t('users.roleAdmin')}</Tag> : <Tag>{t('users.roleReader')}</Tag>,
    },
    {
      title: t('common.status'),
      dataIndex: 'enabled',
      render: (value: boolean) =>
        value ? <Tag color="green">{t('users.enabled')}</Tag> : <Tag color="default">{t('users.disabled')}</Tag>,
    },
    { title: t('users.createdAt'), dataIndex: 'gmtCreate', render: dateTime },
    {
      title: t('common.actions'),
      key: 'actions',
      render: (_, record) => (
        <Space>
          <Button size="small" icon={<Key size={14} />} onClick={() => setPasswordTarget(record)}>
            {t('users.changePwdShort')}
          </Button>
          <Switch
            checked={record.enabled}
            loading={mutatingUserIds.has(record.id)}
            checkedChildren={t('users.switchEnable')}
            unCheckedChildren={t('users.switchDisable')}
            onChange={(enabled) => void setEnabled(record, enabled)}
          />
        </Space>
      ),
    },
  ];

  return (
    <div style={{ padding: 24 }}>
      <PageHeader
        title={t('users.pageTitle')}
        subtitle={t('users.pageSubtitle')}
        extra={
          admin ? (
            <Space>
              <Button
                icon={<DownloadSimple size={16} />}
                disabled={loading || total === 0}
                loading={userExporting}
                onClick={() => void handleExportUsers()}
              >
                {t('users.export')}
              </Button>
              <Button type="primary" icon={<Plus size={16} />} onClick={openCreateUserModal}>
                {t('users.newUser')}
              </Button>
            </Space>
          ) : undefined
        }
      />
      {!admin && (
        <InfoBanner title={t('users.notAdminTitle')} description={t('users.notAdminDesc')} />
      )}
      <Card title={t('users.myAccount')} style={{ marginBottom: 16 }}>
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
          {t('users.changeMyPassword')}
        </Button>
      </Card>
      {admin && (
        <Card>
          <Flex gap={12} wrap style={{ marginBottom: 16 }}>
            <Input.Search
              allowClear
              placeholder={t('users.searchPlaceholder')}
              style={{ width: 240 }}
              value={search}
              onChange={(event) => {
                setSearch(event.target.value);
                setPage(1);
              }}
            />
            <Select<RoleFilter>
              allowClear
              aria-label={t('users.filterByRole')}
              placeholder={t('users.allRoles')}
              style={{ width: 140 }}
              value={roleFilter}
              onChange={(value) => {
                setRoleFilter(value);
                setPage(1);
              }}
              options={[
                { label: t('users.roleAdmin'), value: 'admin' },
                { label: t('users.roleReader'), value: 'reader' },
              ]}
            />
            <Select<StatusFilter>
              allowClear
              aria-label={t('users.filterByStatus')}
              placeholder={t('users.allStatuses')}
              style={{ width: 140 }}
              value={statusFilter}
              onChange={(value) => {
                setStatusFilter(value);
                setPage(1);
              }}
              options={[
                { label: t('users.enabled'), value: 'enabled' },
                { label: t('users.disabled'), value: 'disabled' },
              ]}
            />
          </Flex>
          <Table
            rowKey="id"
            loading={loading}
            columns={columns}
            dataSource={users}
            pagination={{
              current: page,
              pageSize,
              total,
              showSizeChanger: true,
              pageSizeOptions: PAGE_SIZE_OPTIONS.map(String),
              showTotal: (count) => `${t('common.total')} ${count} ${t('users.unit')}`,
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
        title={t('users.createTitle')}
        open={createOpen}
        onOk={() => void createUser()}
        onCancel={() => setCreateOpen(false)}
      >
        <Form form={createForm} layout="vertical" initialValues={{ admin: false }}>
          <Form.Item
            name="username"
            label={t('users.username')}
            rules={[{ required: true }, { max: 128 }]}
          >
            <Input autoComplete="username" />
          </Form.Item>
          <Form.Item
            name="password"
            label={t('users.initialPassword')}
            rules={[{ required: true }, { min: 8, message: t('users.passwordMin') }]}
          >
            <Input.Password autoComplete="new-password" />
          </Form.Item>
          <Form.Item name="admin" label={t('users.adminRoleLabel')} valuePropName="checked">
            <Switch
              checkedChildren={t('users.roleAdmin')}
              unCheckedChildren={t('users.roleReader')}
            />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={
          passwordTarget?.id === userId
            ? t('users.changeMyPassword')
            : t('users.resetTitle', { name: passwordTarget?.username ?? '' })
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
            <Form.Item name="currentPassword" label={t('users.currentPassword')} rules={[{ required: true }]}>
              <Input.Password autoComplete="current-password" />
            </Form.Item>
          )}
          <Form.Item
            name="newPassword"
            label={t('users.newPassword')}
            rules={[{ required: true }, { min: 8, message: t('users.passwordMin') }]}
          >
            <Input.Password autoComplete="new-password" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default UserManagementPage;
