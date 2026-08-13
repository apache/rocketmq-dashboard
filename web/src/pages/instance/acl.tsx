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

import { useEffect, useRef, useState } from 'react';
import {
  Table,
  Card,
  Button,
  Tag,
  Space,
  Input,
  Select,
  Tabs,
  Modal,
  Form,
  Switch,
  Checkbox,
  Radio,
  Badge,
  Typography,
  Flex,
  Alert,
  message,
} from 'antd';
import {
  Plus,
  MagnifyingGlass,
  ShieldCheck,
  User,
  Eye,
  EyeSlash,
  Key,
} from '@phosphor-icons/react';
import { EditOutlined, DeleteOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import PageHeader from '../../components/PageHeader';
import { InstanceSelect } from '../../components/InstanceSelect';
import { useLang } from '../../i18n/LangContext';
import {
  createAclRule,
  createAclUser,
  createAndUpdatePlainAccessConfig,
  deleteAclRule,
  deleteAclUser,
  getAclUserCredentials,
  examineBrokerClusterAclConfig,
  listAclRules,
  listAclUsers,
  updateAclRule,
  updateAclUser,
} from '../../services/aclService';
import type { AclRule, AclUser, AclClusterConfig, PlainAccessConfig } from '../../api/acl';
import { useInstanceFilter } from '../../hooks/useInstanceFilter';

type AclRuleFormValues = Pick<
  AclRule,
  'principal' | 'resource' | 'resourceType' | 'resourcePattern' | 'actions' | 'decision' | 'scope'
>;
type AclUserFormValues = Pick<AclUser, 'username' | 'admin' | 'clusters'>;

const normalizeRule = (rule: AclRule): AclRule => ({
  ...rule,
  principal: rule.principal ?? '',
  resource: rule.resource ?? '',
  resourceType: rule.resourceType ?? '',
  resourcePattern: rule.resourcePattern ?? '',
  actions: rule.actions ?? [],
  decision: rule.decision ?? '',
  scope: rule.scope ?? '',
  // Normalize to a string so version filters and tag coloring work whether the backend
  // returns a number (2.0) or a string ("2.0").
  aclVersion: String(rule.aclVersion ?? '2.0'),
  createdAt: rule.createdAt ?? null,
});

const normalizeUser = (user: AclUser): AclUser => ({
  ...user,
  username: user.username ?? '',
  accessKey: user.accessKey ?? '',
  secretKey: user.secretKey ?? '',
  admin: user.admin ?? false,
  clusters: user.clusters ?? [],
  createdAt: user.createdAt ?? null,
});

const isFormValidationError = (error: unknown) =>
  typeof error === 'object' && error !== null && 'errorFields' in error;

/* ═══════════════════════════════════════════
   ACL Management Page
   ═══════════════════════════════════════════ */
const AclPage = () => {
  const { t } = useLang();
  const { selectedInstanceId, selectInstance, instanceOptions } = useInstanceFilter();

  /* ─── State ─── */
  const [rules, setRules] = useState<AclRule[]>([]);
  const [users, setUsers] = useState<AclUser[]>([]);
  const [rulesLoading, setRulesLoading] = useState(true);
  const [usersLoading, setUsersLoading] = useState(true);
  const [ruleSubmitting, setRuleSubmitting] = useState(false);
  const [userSubmitting, setUserSubmitting] = useState(false);
  const [activeTab, setActiveTab] = useState('rules');

  // Rule filters
  const [ruleSearch, setRuleSearch] = useState('');
  const [ruleVersionFilter, setRuleVersionFilter] = useState<string>('all');
  const [ruleDecisionFilter, setRuleDecisionFilter] = useState<string>('all');

  // Rule modal
  const [ruleModalOpen, setRuleModalOpen] = useState(false);
  const [editingRule, setEditingRule] = useState<AclRule | null>(null);
  const [ruleForm] = Form.useForm();

  // User modal
  const [userModalOpen, setUserModalOpen] = useState(false);
  const [editingUser, setEditingUser] = useState<AclUser | null>(null);
  const [userForm] = Form.useForm();

  // Secret key reveal
  const [revealedKeys, setRevealedKeys] = useState<Set<string>>(new Set());
  const [adminUpdatingIds, setAdminUpdatingIds] = useState<Set<string>>(() => new Set());
  const adminUpdateInFlightRef = useRef<Set<string>>(new Set());
  const [credentialsByUser, setCredentialsByUser] = useState<
    Record<string, { accessKey: string; secretKey: string }>
  >({});

  // Cluster ACL config (examineBrokerClusterAclConfig)
  const [clusterConfig, setClusterConfig] = useState<AclClusterConfig | null>(null);
  const [configLoading, setConfigLoading] = useState(false);
  const [clusterIdInput, setClusterIdInput] = useState('DefaultCluster');

  // Plain access config modal
  const [plainModalOpen, setPlainModalOpen] = useState(false);
  const [editingPlain, setEditingPlain] = useState<PlainAccessConfig | null>(null);
  const [plainSubmitting, setPlainSubmitting] = useState(false);
  const [plainForm] = Form.useForm();

  useEffect(() => {
    let mounted = true;

    void listAclRules()
      .then((nextRules) => {
        if (mounted) setRules(nextRules.map(normalizeRule));
      })
      .catch(() => {
        if (mounted) message.error(t('common.fetchDataFailed'));
      })
      .finally(() => {
        if (mounted) setRulesLoading(false);
      });

    void listAclUsers()
      .then((nextUsers) => {
        if (mounted) setUsers(nextUsers.map(normalizeUser));
      })
      .catch(() => {
        if (mounted) message.error(t('common.fetchDataFailed'));
      })
      .finally(() => {
        if (mounted) setUsersLoading(false);
      });

    return () => {
      mounted = false;
    };
  }, [t]);

  /* ─── Filtered rules ─── */
  const filteredRules = rules.filter((r) => {
    const aclVersion = String(r.aclVersion);
    const matchSearch =
      !ruleSearch ||
      r.principal.toLowerCase().includes(ruleSearch.toLowerCase()) ||
      r.resource.toLowerCase().includes(ruleSearch.toLowerCase());
    const matchVersion = ruleVersionFilter === 'all' || aclVersion === ruleVersionFilter;
    const matchDecision = ruleDecisionFilter === 'all' || r.decision === ruleDecisionFilter;
    return matchSearch && matchVersion && matchDecision;
  });

  /* ─── Rule helpers ─── */
  const isAdmin = (principal: string) =>
    users.find((u) => u.username === principal)?.admin ?? false;

  const actionTagColor: Record<string, string> = {
    PUB: 'blue',
    SUB: 'green',
    ALL: 'purple',
  };

  const actionLabel: Record<string, string> = {
    PUB: t('acl.pub'),
    SUB: t('acl.sub'),
    ALL: t('acl.all'),
  };

  const openAddRuleModal = () => {
    setEditingRule(null);
    ruleForm.resetFields();
    ruleForm.setFieldsValue({
      resourcePattern: 'PREFIX',
      actions: ['PUB'],
      decision: 'ALLOW',
      scope: 'cluster',
    });
    setRuleModalOpen(true);
  };

  const openEditRuleModal = (rule: AclRule) => {
    setEditingRule(rule);
    ruleForm.setFieldsValue({
      principal: rule.principal,
      resourceType: rule.resourceType,
      resource: rule.resource,
      resourcePattern: rule.resourcePattern,
      actions: rule.actions,
      decision: rule.decision,
      scope: rule.scope,
    });
    setRuleModalOpen(true);
  };

  const handleRuleSubmit = async () => {
    try {
      const values = (await ruleForm.validateFields()) as AclRuleFormValues;
      setRuleSubmitting(true);
      if (editingRule) {
        const updated = await updateAclRule({ ...editingRule, ...values });
        const normalized = normalizeRule(updated);
        setRules((prev) => prev.map((r) => (r.id === editingRule.id ? normalized : r)));
        message.success(t('acl.ruleUpdated'));
      } else {
        const created = await createAclRule({
          ...values,
          aclVersion: '2.0',
        });
        setRules((prev) => [normalizeRule(created), ...prev]);
        message.success(t('acl.ruleAdded'));
      }
      setRuleModalOpen(false);
    } catch (error) {
      if (isFormValidationError(error)) return;
      message.error(t('common.operationFailed'));
    } finally {
      setRuleSubmitting(false);
    }
  };

  const handleDeleteRule = async (id: string) => {
    try {
      await deleteAclRule(id);
      setRules((prev) => prev.filter((r) => r.id !== id));
      message.success(t('acl.ruleDeleted'));
    } catch {
      message.error(t('common.operationFailed'));
    }
  };

  /* ─── User helpers ─── */
  const toggleRevealKey = async (userId: string) => {
    const revealing = !revealedKeys.has(userId);
    setRevealedKeys((prev) => {
      const next = new Set(prev);
      if (next.has(userId)) {
        next.delete(userId);
      } else {
        next.add(userId);
      }
      return next;
    });
    if (!revealing || credentialsByUser[userId]) return;
    try {
      const credentials = await getAclUserCredentials(userId);
      setCredentialsByUser((prev) => ({
        ...prev,
        [userId]: {
          accessKey: credentials.accessKey,
          secretKey: credentials.secretKey,
        },
      }));
    } catch {
      setRevealedKeys((prev) => {
        const next = new Set(prev);
        next.delete(userId);
        return next;
      });
      message.error(t('common.fetchDataFailed'));
    }
  };

  const openAddUserModal = () => {
    setEditingUser(null);
    userForm.resetFields();
    userForm.setFieldsValue({ admin: false, clusters: [] });
    setUserModalOpen(true);
  };

  const openEditUserModal = (user: AclUser) => {
    setEditingUser(user);
    userForm.setFieldsValue({
      username: user.username,
      admin: user.admin,
      clusters: [...user.clusters],
    });
    setUserModalOpen(true);
  };

  const handleUserSubmit = async () => {
    try {
      const values = (await userForm.validateFields()) as AclUserFormValues;
      setUserSubmitting(true);
      if (editingUser) {
        const updated = await updateAclUser({
          id: editingUser.id,
          username: values.username,
          admin: values.admin ?? false,
          clusters: values.clusters ?? [],
        });
        const normalized = normalizeUser(updated);
        setUsers((prev) => prev.map((u) => (u.id === editingUser.id ? normalized : u)));
        message.success(t('acl.userUpdated'));
      } else {
        const created = await createAclUser({
          username: values.username,
          admin: values.admin ?? false,
          clusters: values.clusters ?? [],
        });
        setUsers((prev) => [normalizeUser(created), ...prev]);
        message.success(t('acl.userAdded'));
      }
      setUserModalOpen(false);
    } catch (error) {
      if (isFormValidationError(error)) return;
      message.error(t('common.operationFailed'));
    } finally {
      setUserSubmitting(false);
    }
  };

  const handleDeleteUser = async (id: string) => {
    try {
      await deleteAclUser(id);
      setUsers((prev) => prev.filter((u) => u.id !== id));
      message.success(t('acl.userDeleted'));
    } catch {
      message.error(t('common.operationFailed'));
    }
  };

  const handleToggleAdmin = async (user: AclUser, checked: boolean) => {
    if (adminUpdateInFlightRef.current.has(user.id)) return;
    adminUpdateInFlightRef.current.add(user.id);
    setAdminUpdatingIds((current) => new Set(current).add(user.id));
    try {
      const updated = await updateAclUser({
        id: user.id,
        username: user.username,
        admin: checked,
        clusters: user.clusters,
      });
      const normalized = normalizeUser(updated);
      setUsers((prev) => prev.map((u) => (u.id === user.id ? normalized : u)));
      message.success(checked ? t('acl.adminSet') : t('acl.adminRemoved'));
    } catch {
      message.error(t('common.operationFailed'));
    } finally {
      adminUpdateInFlightRef.current.delete(user.id);
      setAdminUpdatingIds((current) => {
        const next = new Set(current);
        next.delete(user.id);
        return next;
      });
    }
  };

  /* ─── Cluster ACL config helpers ─── */
  const handleExamine = async () => {
    const clusterId = clusterIdInput.trim();
    if (!clusterId) {
      message.warning(t('acl.inputRequired', { field: t('acl.examineCluster') }));
      return;
    }
    try {
      setConfigLoading(true);
      const config = await examineBrokerClusterAclConfig(clusterId);
      setClusterConfig(config);
      message.success(t('acl.configExamined'));
    } catch {
      message.error(t('common.operationFailed'));
    } finally {
      setConfigLoading(false);
    }
  };

  const openAddPlainModal = () => {
    setEditingPlain(null);
    plainForm.resetFields();
    plainForm.setFieldsValue({
      admin: false,
      defaultTopicPerm: 'DENY',
      defaultGroupPerm: 'DENY',
      topicPerms: [],
      groupPerms: [],
    });
    setPlainModalOpen(true);
  };

  const openEditPlainModal = (account: PlainAccessConfig) => {
    setEditingPlain(account);
    plainForm.setFieldsValue({
      accessKey: account.accessKey,
      // Read-back views only carry a masked secret; leave the field blank so the
      // stored secret is kept unless the user explicitly types a new one.
      secretKey: '',
      whiteRemoteAddress: account.whiteRemoteAddress ?? '',
      admin: account.admin,
      defaultTopicPerm: account.defaultTopicPerm ?? 'DENY',
      defaultGroupPerm: account.defaultGroupPerm ?? 'DENY',
      topicPerms: [...(account.topicPerms ?? [])],
      groupPerms: [...(account.groupPerms ?? [])],
    });
    setPlainModalOpen(true);
  };

  const handlePlainSubmit = async () => {
    try {
      const values = (await plainForm.validateFields()) as Partial<PlainAccessConfig>;
      setPlainSubmitting(true);
      const saved = await createAndUpdatePlainAccessConfig({
        ...values,
        accessKey: (values.accessKey ?? '').trim(),
      });
      const normalized: PlainAccessConfig = {
        ...saved,
        accessKey: saved.accessKey,
        topicPerms: saved.topicPerms ?? [],
        groupPerms: saved.groupPerms ?? [],
      };
      setClusterConfig((prev) => {
        if (!prev) return prev;
        const exists = prev.accounts.some((a) => a.accessKey === normalized.accessKey);
        const accounts = exists
          ? prev.accounts.map((a) => (a.accessKey === normalized.accessKey ? normalized : a))
          : [normalized, ...prev.accounts];
        return { ...prev, accounts, accountCount: accounts.length };
      });
      message.success(t('acl.plainAccessSaved'));
      setPlainModalOpen(false);
    } catch (error) {
      if (isFormValidationError(error)) return;
      message.error(t('common.operationFailed'));
    } finally {
      setPlainSubmitting(false);
    }
  };

  const formatDate = (iso?: string | null) => {
    if (!iso) return '-';
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) return '-';
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
  };

  /* ═══════════════════════════════════════════
     ACL Rules Table
     ═══════════════════════════════════════════ */
  const ruleColumns: ColumnsType<AclRule> = [
    {
      title: t('acl.principal'),
      dataIndex: 'principal',
      key: 'principal',
      width: 200,
      sorter: (a, b) => a.principal.localeCompare(b.principal),
      render: (text: string) => (
        <Space size={6}>
          <User size={14} color="#8c8c8c" weight="fill" />
          <span style={{ fontWeight: 500 }}>{text}</span>
          {isAdmin(text) && (
            <Badge
              count={t('acl.adminBadge')}
              style={{ backgroundColor: '#722ed1', fontSize: 11 }}
            />
          )}
        </Space>
      ),
    },
    {
      title: t('acl.resource'),
      key: 'resource',
      width: 240,
      sorter: (a, b) => a.resource.localeCompare(b.resource),
      render: (_: unknown, record: AclRule) => (
        <Space size={6}>
          <span style={{ fontFamily: 'monospace', fontWeight: 500 }}>{record.resource}</span>
          <Tag
            color={record.resourcePattern === 'LITERAL' ? 'blue' : 'green'}
            style={{ fontSize: 11, lineHeight: '18px' }}
          >
            {record.resourcePattern}
          </Tag>
        </Space>
      ),
    },
    {
      title: t('acl.permissions'),
      dataIndex: 'actions',
      key: 'actions',
      width: 180,
      render: (actions: AclRule['actions']) => (
        <Space size={4} wrap>
          {actions.map((action) => (
            <Tag key={action} color={actionTagColor[action]} style={{ fontSize: 11 }}>
              {actionLabel[action] ?? action}
            </Tag>
          ))}
        </Space>
      ),
    },
    {
      title: t('acl.decision'),
      dataIndex: 'decision',
      key: 'decision',
      width: 80,
      sorter: (a, b) => a.decision.localeCompare(b.decision),
      render: (decision: string) => (
        <Tag color={decision === 'ALLOW' ? 'green' : 'red'} style={{ fontWeight: 600 }}>
          {decision === 'ALLOW' ? t('acl.allow') : t('acl.deny')}
        </Tag>
      ),
    },
    {
      title: t('acl.aclVersion'),
      dataIndex: 'aclVersion',
      key: 'aclVersion',
      width: 100,
      sorter: (a, b) => String(a.aclVersion).localeCompare(String(b.aclVersion)),
      render: (version: AclRule['aclVersion']) => (
        <Tag color={String(version) === '2.0' ? 'geekblue' : 'default'}>{version}</Tag>
      ),
    },
    {
      title: t('acl.scope'),
      dataIndex: 'scope',
      key: 'scope',
      width: 100,
      sorter: (a, b) => a.scope.localeCompare(b.scope),
      render: (scope: string) => (
        <span style={{ fontSize: 13 }}>
          {scope === 'cluster' ? t('acl.cluster') : t('acl.namespace')}
        </span>
      ),
    },
    {
      title: t('acl.createdAt'),
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 160,
      sorter: (a, b) => (a.createdAt ?? '').localeCompare(b.createdAt ?? ''),
      render: (iso?: string | null) => (
        <span style={{ fontSize: 13, color: '#8c8c8c' }}>{formatDate(iso)}</span>
      ),
    },
    {
      title: t('common.actions'),
      key: 'ruleActions',
      width: 160,
      render: (_: unknown, record: AclRule) => (
        <Flex gap={6}>
          <Button
            size="small"
            icon={<EditOutlined />}
            style={{ borderColor: '#1677ff', color: '#1677ff' }}
            onClick={() => openEditRuleModal(record)}
          >
            {t('common.edit')}
          </Button>
          <Button
            size="small"
            icon={<DeleteOutlined />}
            style={{ borderColor: '#ff4d4f', color: '#ff4d4f' }}
            onClick={() =>
              Modal.confirm({
                title: t('acl.confirmDeleteRule'),
                content: t('acl.deleteWarning'),
                okText: t('common.delete'),
                okButtonProps: { danger: true },
                onOk: () => handleDeleteRule(record.id),
              })
            }
          >
            {t('common.delete')}
          </Button>
        </Flex>
      ),
    },
  ];

  /* ═══════════════════════════════════════════
     Users Table
     ═══════════════════════════════════════════ */
  const userColumns: ColumnsType<AclUser> = [
    {
      title: t('acl.username'),
      dataIndex: 'username',
      key: 'username',
      width: 200,
      sorter: (a, b) => a.username.localeCompare(b.username),
      render: (text: string, record: AclUser) => (
        <Space size={6}>
          <User size={14} color="#8c8c8c" weight="fill" />
          <span style={{ fontWeight: 500 }}>{text}</span>
          {record.admin && (
            <Badge
              count={t('acl.adminBadge')}
              style={{ backgroundColor: '#722ed1', fontSize: 11 }}
            />
          )}
        </Space>
      ),
    },
    {
      title: 'Access Key',
      dataIndex: 'accessKey',
      key: 'accessKey',
      width: 220,
      sorter: (a, b) => a.accessKey.localeCompare(b.accessKey),
      render: (text: string, record: AclUser) => {
        const revealed = revealedKeys.has(record.id);
        const fullAccessKey = credentialsByUser[record.id]?.accessKey ?? text;
        return (
          <Space size={8}>
            <Typography.Text
              copyable={{ text: fullAccessKey }}
              style={{ fontFamily: 'monospace', fontSize: 13 }}
            >
              {revealed ? fullAccessKey : text}
            </Typography.Text>
          </Space>
        );
      },
    },
    {
      title: 'Secret Key',
      dataIndex: 'secretKey',
      key: 'secretKey',
      width: 240,
      render: (_: string, record: AclUser) => {
        const revealed = revealedKeys.has(record.id);
        const secret = credentialsByUser[record.id]?.secretKey;
        return (
          <Space size={8}>
            <Typography.Text
              copyable={revealed && secret ? { text: secret } : false}
              style={{ fontFamily: 'monospace', fontSize: 13 }}
            >
              {revealed ? (secret ?? '加载中…') : '••••••••••••'}
            </Typography.Text>
            <Button
              type="text"
              size="small"
              icon={revealed ? <EyeSlash size={14} /> : <Eye size={14} />}
              onClick={() => void toggleRevealKey(record.id)}
            />
          </Space>
        );
      },
    },
    {
      title: t('acl.admin'),
      dataIndex: 'admin',
      key: 'admin',
      width: 100,
      sorter: (a, b) => Number(a.admin) - Number(b.admin),
      render: (val: boolean, record: AclUser) => (
        <Switch
          checked={val}
          size="small"
          loading={adminUpdatingIds.has(record.id)}
          disabled={adminUpdatingIds.has(record.id)}
          onChange={(checked) => handleToggleAdmin(record, checked)}
        />
      ),
    },
    {
      title: t('acl.associatedClusters'),
      dataIndex: 'clusters',
      key: 'clusters',
      width: 280,
      sorter: (a, b) => a.clusters.length - b.clusters.length,
      render: (clusters: string[]) => (
        <Space size={4} wrap>
          {clusters.map((c) => (
            <Tag key={c} color="processing" style={{ fontSize: 11 }}>
              {c}
            </Tag>
          ))}
        </Space>
      ),
    },
    {
      title: t('acl.createdAt'),
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 160,
      sorter: (a, b) => (a.createdAt ?? '').localeCompare(b.createdAt ?? ''),
      render: (iso?: string | null) => (
        <span style={{ fontSize: 13, color: '#8c8c8c' }}>{formatDate(iso)}</span>
      ),
    },
    {
      title: t('common.actions'),
      key: 'userActions',
      width: 160,
      render: (_: unknown, record: AclUser) => (
        <Flex gap={6}>
          <Button
            size="small"
            icon={<EditOutlined />}
            style={{ borderColor: '#1677ff', color: '#1677ff' }}
            onClick={() => openEditUserModal(record)}
          >
            {t('common.edit')}
          </Button>
          <Button
            size="small"
            icon={<DeleteOutlined />}
            style={{ borderColor: '#ff4d4f', color: '#ff4d4f' }}
            onClick={() =>
              Modal.confirm({
                title: t('acl.confirmDeleteUser'),
                content: t('acl.deleteUserWarning'),
                okText: t('common.delete'),
                okButtonProps: { danger: true },
                onOk: () => handleDeleteUser(record.id),
              })
            }
          >
            {t('common.delete')}
          </Button>
        </Flex>
      ),
    },
  ];

  const permTagColor: Record<string, string> = {
    ALL: 'purple',
    PUB: 'blue',
    SUB: 'green',
    DENY: 'red',
  };

  const plainColumns: ColumnsType<PlainAccessConfig> = [
    {
      title: t('acl.accessKey'),
      dataIndex: 'accessKey',
      key: 'accessKey',
      width: 220,
      render: (text: string) => (
        <Space size={6}>
          <Key size={14} color="#8c8c8c" weight="fill" />
          <span style={{ fontFamily: 'monospace', fontWeight: 500 }}>{text}</span>
        </Space>
      ),
    },
    {
      title: t('acl.admin'),
      dataIndex: 'admin',
      key: 'admin',
      width: 90,
      render: (val: boolean) =>
        val ? (
          <Tag color="purple">{t('acl.adminBadge')}</Tag>
        ) : (
          <span style={{ color: '#8c8c8c' }}>-</span>
        ),
    },
    {
      title: t('acl.defaultTopicPerm'),
      dataIndex: 'defaultTopicPerm',
      key: 'defaultTopicPerm',
      width: 140,
      render: (val: string) => <Tag color={permTagColor[val] ?? 'default'}>{val}</Tag>,
    },
    {
      title: t('acl.defaultGroupPerm'),
      dataIndex: 'defaultGroupPerm',
      key: 'defaultGroupPerm',
      width: 140,
      render: (val: string) => <Tag color={permTagColor[val] ?? 'default'}>{val}</Tag>,
    },
    {
      title: t('acl.topicPerms'),
      dataIndex: 'topicPerms',
      key: 'topicPerms',
      width: 220,
      render: (perms: string[]) => (
        <Space size={4} wrap>
          {(perms ?? []).map((p) => (
            <Tag key={p} color="blue" style={{ fontSize: 11 }}>
              {p}
            </Tag>
          ))}
        </Space>
      ),
    },
    {
      title: t('acl.groupPerms'),
      dataIndex: 'groupPerms',
      key: 'groupPerms',
      width: 200,
      render: (perms: string[]) => (
        <Space size={4} wrap>
          {(perms ?? []).map((p) => (
            <Tag key={p} color="green" style={{ fontSize: 11 }}>
              {p}
            </Tag>
          ))}
        </Space>
      ),
    },
    {
      title: t('common.actions'),
      key: 'plainActions',
      width: 100,
      render: (_: unknown, record: PlainAccessConfig) => (
        <Button
          size="small"
          icon={<EditOutlined />}
          style={{ borderColor: '#1677ff', color: '#1677ff' }}
          onClick={() => openEditPlainModal(record)}
        >
          {t('common.edit')}
        </Button>
      ),
    },
  ];

  /* ═══════════════════════════════════════════
     Render
     ═══════════════════════════════════════════ */
  return (
    <div style={{ padding: 24 }}>
      <PageHeader
        title={t('acl.title')}
        subtitle={t('acl.subtitle', { rules: rules.length, users: users.length })}
        extra={
          <Button
            type="primary"
            icon={<Plus size={14} weight="bold" />}
            onClick={activeTab === 'rules' ? openAddRuleModal : openAddUserModal}
          >
            {activeTab === 'rules' ? t('acl.addRule') : t('acl.addUser')}
          </Button>
        }
      />

      <Alert
        data-testid="acl-local-metadata-notice"
        type="warning"
        showIcon
        message={t('acl.localMetadataNotice')}
        description={t('acl.localMetadataDescription')}
        style={{ marginBottom: 16 }}
      />

      <Card variant="borderless" styles={{ body: { padding: 0 } }}>
        <Tabs
          activeKey={activeTab}
          onChange={setActiveTab}
          style={{ padding: '0 16px' }}
          items={[
            {
              key: 'rules',
              label: (
                <Space size={6}>
                  <ShieldCheck size={15} />
                  <span>{t('acl.ruleTab')}</span>
                </Space>
              ),
              children: (
                <div>
                  {/* Filter bar */}
                  <div
                    style={{
                      display: 'flex',
                      gap: 12,
                      padding: '16px 0',
                      flexWrap: 'wrap',
                    }}
                  >
                    <InstanceSelect
                      value={selectedInstanceId || undefined}
                      onChange={selectInstance}
                      options={instanceOptions}
                      style={{ width: 220 }}
                    />
                    <Input.Search
                      placeholder={t('acl.searchPrincipal')}
                      prefix={<MagnifyingGlass size={14} color="#9CA3AF" />}
                      value={ruleSearch}
                      onChange={(e) => setRuleSearch(e.target.value)}
                      onSearch={setRuleSearch}
                      allowClear
                      style={{ width: 260 }}
                    />
                    <Select
                      value={ruleVersionFilter}
                      onChange={setRuleVersionFilter}
                      style={{ width: 140 }}
                      options={[
                        { value: 'all', label: t('acl.allVersions') },
                        { value: '1.0', label: 'ACL 1.0' },
                        { value: '2.0', label: 'ACL 2.0' },
                      ]}
                    />
                    <Select
                      value={ruleDecisionFilter}
                      onChange={setRuleDecisionFilter}
                      style={{ width: 140 }}
                      options={[
                        { value: 'all', label: t('acl.allDecisions') },
                        { value: 'ALLOW', label: t('acl.allow') },
                        { value: 'DENY', label: t('acl.deny') },
                      ]}
                    />
                  </div>

                  {/* Rules table */}
                  <Table
                    columns={ruleColumns}
                    dataSource={filteredRules}
                    rowKey="id"
                    loading={rulesLoading}
                    pagination={{
                      pageSize: 20,
                      showSizeChanger: true,
                      showTotal: (total) => t('acl.totalRules', { n: total }),
                    }}
                    size="small"
                  />
                </div>
              ),
            },
            {
              key: 'users',
              label: (
                <Space size={6}>
                  <User size={15} />
                  <span>{t('acl.userTab')}</span>
                </Space>
              ),
              children: (
                <div>
                  <div style={{ padding: '16px 0' }}>
                    <Space>
                      <Button
                        type="primary"
                        icon={<Plus size={14} weight="bold" />}
                        onClick={openAddUserModal}
                      >
                        {t('acl.addUser')}
                      </Button>
                    </Space>
                  </div>

                  <Table
                    columns={userColumns}
                    dataSource={users}
                    rowKey="id"
                    loading={usersLoading}
                    pagination={{
                      pageSize: 20,
                      showSizeChanger: true,
                      showTotal: (total) => t('acl.totalUsers', { n: total }),
                    }}
                    size="small"
                  />
                </div>
              ),
            },
            {
              key: 'clusterConfig',
              label: (
                <Space size={6}>
                  <ShieldCheck size={15} />
                  <span>{t('acl.clusterConfigTab')}</span>
                </Space>
              ),
              children: (
                <div>
                  {/* Examine cluster ACL config */}
                  <div
                    style={{
                      display: 'flex',
                      gap: 12,
                      padding: '16px 0',
                      flexWrap: 'wrap',
                      alignItems: 'center',
                    }}
                  >
                    <Input
                      placeholder={t('acl.examineClusterPlaceholder')}
                      prefix={<ShieldCheck size={14} color="#9CA3AF" />}
                      value={clusterIdInput}
                      onChange={(e) => setClusterIdInput(e.target.value)}
                      onPressEnter={handleExamine}
                      style={{ width: 260 }}
                    />
                    <Button
                      type="primary"
                      icon={<ShieldCheck size={14} weight="bold" />}
                      loading={configLoading}
                      onClick={handleExamine}
                    >
                      {t('acl.examine')}
                    </Button>
                    <Button icon={<Plus size={14} weight="bold" />} onClick={openAddPlainModal}>
                      {t('acl.addPlainAccess')}
                    </Button>
                  </div>

                  {clusterConfig && (
                    <div style={{ paddingBottom: 16 }}>
                      {/* Summary cards */}
                      <div
                        style={{
                          display: 'flex',
                          gap: 12,
                          flexWrap: 'wrap',
                          marginBottom: 16,
                        }}
                      >
                        <Tag
                          color={clusterConfig.aclEnabled ? 'green' : 'default'}
                          style={{ fontSize: 13, padding: '4px 10px' }}
                        >
                          {clusterConfig.aclEnabled ? t('acl.aclEnabled') : t('acl.aclDisabled')}
                        </Tag>
                        <Tag color="geekblue" style={{ fontSize: 13, padding: '4px 10px' }}>
                          {clusterConfig.aclVersion}
                        </Tag>
                        <Tag style={{ fontSize: 13, padding: '4px 10px' }}>
                          {t('acl.accountCount')}: {clusterConfig.accountCount}
                        </Tag>
                      </div>

                      <div style={{ marginBottom: 12, color: '#8c8c8c', fontSize: 13 }}>
                        {t('acl.globalWhitelist')}:{' '}
                        {clusterConfig.globalWhiteRemoteAddresses.length === 0 ? (
                          <span>-</span>
                        ) : (
                          clusterConfig.globalWhiteRemoteAddresses.map((ip) => (
                            <Tag key={ip} color="cyan" style={{ fontSize: 11 }}>
                              {ip}
                            </Tag>
                          ))
                        )}
                      </div>

                      {/* Accounts table */}
                      <Table<PlainAccessConfig>
                        columns={plainColumns}
                        dataSource={clusterConfig.accounts}
                        rowKey="accessKey"
                        pagination={false}
                        size="small"
                        locale={{
                          emptyText: t('acl.noAccounts'),
                        }}
                      />
                    </div>
                  )}
                </div>
              ),
            },
          ]}
        />
      </Card>

      {/* ─── Add/Edit Rule Modal ─── */}
      <Modal
        title={editingRule ? t('acl.editRule') : t('acl.addRule')}
        open={ruleModalOpen}
        onCancel={() => setRuleModalOpen(false)}
        onOk={handleRuleSubmit}
        okText={editingRule ? t('acl.save') : t('acl.add')}
        cancelText={t('common.cancel')}
        confirmLoading={ruleSubmitting}
        width={560}
        destroyOnHidden
      >
        <Form form={ruleForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item
            name="principal"
            label={t('acl.principal')}
            rules={[{ required: true, message: t('acl.required', { field: t('acl.principal') }) }]}
          >
            <Select
              placeholder={t('acl.selectPrincipal')}
              showSearch
              optionFilterProp="label"
              options={users.map((u) => ({
                value: u.username,
                label: u.username,
              }))}
            />
          </Form.Item>

          <Form.Item
            name="resourceType"
            label={t('acl.resourceType')}
            rules={[
              { required: true, message: t('acl.required', { field: t('acl.resourceType') }) },
            ]}
          >
            <Select
              placeholder={t('acl.selectResourceType')}
              options={[
                { value: 'Topic', label: 'Topic' },
                { value: 'Group', label: 'Group' },
                { value: 'Cluster', label: 'Cluster' },
              ]}
            />
          </Form.Item>

          <Form.Item
            name="resource"
            label={t('acl.resourceName')}
            rules={[
              { required: true, message: t('acl.inputRequired', { field: t('acl.resourceName') }) },
            ]}
          >
            <Input placeholder={t('acl.resourceNamePlaceholder')} />
          </Form.Item>

          <Form.Item name="resourcePattern" label={t('acl.matchPattern')}>
            <Radio.Group>
              <Radio.Button value="LITERAL">{t('acl.literal')}</Radio.Button>
              <Radio.Button value="PREFIX">{t('acl.prefix')}</Radio.Button>
            </Radio.Group>
          </Form.Item>

          <Form.Item
            name="actions"
            label={t('acl.permissions')}
            rules={[
              { required: true, message: t('acl.required', { field: t('acl.permissions') }) },
            ]}
          >
            <Checkbox.Group>
              <Space>
                <Checkbox value="PUB">{t('acl.pub')}</Checkbox>
                <Checkbox value="SUB">{t('acl.sub')}</Checkbox>
                <Checkbox value="ALL">{t('acl.all')}</Checkbox>
              </Space>
            </Checkbox.Group>
          </Form.Item>

          <Form.Item name="decision" label={t('acl.decision')}>
            <Radio.Group>
              <Radio.Button value="ALLOW">
                <span style={{ color: '#52c41a' }}>{t('acl.allowDesc')}</span>
              </Radio.Button>
              <Radio.Button value="DENY">
                <span style={{ color: '#ff4d4f' }}>{t('acl.denyDesc')}</span>
              </Radio.Button>
            </Radio.Group>
          </Form.Item>

          <Form.Item name="scope" label={t('acl.effectScope')}>
            <Select
              placeholder={t('acl.selectEffectScope')}
              options={[{ value: 'cluster', label: t('acl.clusterScope') }]}
            />
          </Form.Item>
        </Form>
      </Modal>

      {/* ─── Add/Edit User Modal ─── */}
      <Modal
        title={editingUser ? t('acl.editUser') : t('acl.addUser')}
        open={userModalOpen}
        onCancel={() => setUserModalOpen(false)}
        onOk={handleUserSubmit}
        okText={editingUser ? t('acl.save') : t('acl.add')}
        cancelText={t('common.cancel')}
        confirmLoading={userSubmitting}
        width={520}
        destroyOnHidden
      >
        <Form form={userForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item
            name="username"
            label={t('acl.username')}
            rules={[
              { required: true, message: t('acl.inputRequired', { field: t('acl.username') }) },
            ]}
          >
            <Input
              placeholder={t('acl.usernamePlaceholder')}
              disabled={!!editingUser}
              prefix={<User size={14} color="#9CA3AF" />}
            />
          </Form.Item>

          <Form.Item name="admin" label={t('acl.admin')} valuePropName="checked">
            <Switch checkedChildren={t('common.yes')} unCheckedChildren={t('common.no')} />
          </Form.Item>

          <Form.Item name="clusters" label={t('acl.associatedClusters')}>
            <Select mode="tags" tokenSeparators={[',']} allowClear />
          </Form.Item>
        </Form>
      </Modal>

      {/* ─── Add/Edit Plain Access Config Modal ─── */}
      <Modal
        title={editingPlain ? t('acl.editPlainAccess') : t('acl.addPlainAccess')}
        open={plainModalOpen}
        onCancel={() => setPlainModalOpen(false)}
        onOk={handlePlainSubmit}
        okText={editingPlain ? t('acl.save') : t('acl.add')}
        cancelText={t('common.cancel')}
        confirmLoading={plainSubmitting}
        width={560}
        destroyOnHidden
      >
        <Form form={plainForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item
            name="accessKey"
            label={t('acl.accessKey')}
            rules={[
              { required: true, message: t('acl.inputRequired', { field: t('acl.accessKey') }) },
            ]}
          >
            <Input
              placeholder="e.g. user-order-service"
              disabled={!!editingPlain}
              prefix={<Key size={14} color="#9CA3AF" />}
            />
          </Form.Item>

          <Form.Item
            name="secretKey"
            label={t('acl.secretKey')}
            rules={[
              {
                required: !editingPlain,
                message: t('acl.inputRequired', { field: t('acl.secretKey') }),
              },
            ]}
          >
            <Input.Password
              placeholder={editingPlain ? t('acl.secretKeepUnchanged') : t('acl.secretCreateHint')}
              prefix={<Key size={14} color="#9CA3AF" />}
            />
          </Form.Item>

          <Form.Item name="whiteRemoteAddress" label={t('acl.whiteRemoteAddress')}>
            <Input placeholder="e.g. 10.0.1.0/24" />
          </Form.Item>

          <Form.Item name="admin" label={t('acl.admin')} valuePropName="checked">
            <Switch checkedChildren={t('common.yes')} unCheckedChildren={t('common.no')} />
          </Form.Item>

          <Form.Item name="defaultTopicPerm" label={t('acl.defaultTopicPerm')}>
            <Select
              options={[
                { value: 'DENY', label: 'DENY' },
                { value: 'PUB', label: 'PUB' },
                { value: 'SUB', label: 'SUB' },
                { value: 'ALL', label: 'ALL' },
              ]}
            />
          </Form.Item>

          <Form.Item name="defaultGroupPerm" label={t('acl.defaultGroupPerm')}>
            <Select
              options={[
                { value: 'DENY', label: 'DENY' },
                { value: 'PUB', label: 'PUB' },
                { value: 'SUB', label: 'SUB' },
                { value: 'ALL', label: 'ALL' },
              ]}
            />
          </Form.Item>

          <Form.Item name="topicPerms" label={t('acl.topicPerms')}>
            <Select
              mode="tags"
              tokenSeparators={[',']}
              placeholder={t('acl.topicPermsPlaceholder')}
            />
          </Form.Item>

          <Form.Item name="groupPerms" label={t('acl.groupPerms')}>
            <Select
              mode="tags"
              tokenSeparators={[',']}
              placeholder={t('acl.groupPermsPlaceholder')}
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default AclPage;
