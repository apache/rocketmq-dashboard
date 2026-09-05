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
  Alert,
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
  Progress,
  Statistic,
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
import InfoBanner from '../../components/InfoBanner';
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
  pageAclUsers,
  updateAclRule,
  updateAclUser,
} from '../../services/aclService';
import type { AclRule, AclUser, AclClusterConfig, PlainAccessConfig } from '../../api/acl';
import { useInstanceFilter } from '../../hooks/useInstanceFilter';
import { tableScrollX } from '../../utils/table';
import { analyzeAclRisk, type AclRiskIssue } from '../../utils/aclRiskDiagnostics';

type AclEntityId = AclRule['id'];
type AclRuleFormValues = Pick<
  AclRule,
  'principal' | 'resource' | 'resourceType' | 'resourcePattern' | 'actions' | 'decision' | 'scope'
>;
type AclUserFormValues = Pick<AclUser, 'username' | 'admin' | 'clusters'>;

const normalizeRule = (rule: AclRule): AclRule => ({
  ...rule,
  id: rule.id ?? rule.principal,
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
  gmtCreate: rule.gmtCreate ?? null,
});

type NormalizedAclUser = AclUser & { accessKey: string; secretKey: string };

const normalizeUser = (user: AclUser): NormalizedAclUser => ({
  ...user,
  id: user.id ?? user.username,
  username: user.username ?? '',
  accessKey: user.accessKey ?? '',
  secretKey: user.secretKey ?? '',
  admin: user.admin ?? false,
  clusters: user.clusters ?? [],
  gmtCreate: user.gmtCreate ?? null,
});

const isFormValidationError = (error: unknown) =>
  typeof error === 'object' && error !== null && 'errorFields' in error;

/* ═══════════════════════════════════════════
   ACL Management Page
   ═══════════════════════════════════════════ */
type AclPageContentProps = Pick<
  ReturnType<typeof useInstanceFilter>,
  'selectedInstanceId' | 'selectInstance' | 'instanceOptions' | 'instances'
>;

const AclPageContent = ({
  selectedInstanceId,
  selectInstance,
  instanceOptions,
  instances,
}: AclPageContentProps) => {
  const { t } = useLang();
  const hasSelectedInstance = Boolean(selectedInstanceId);
  const selectedInstance = instances.find((instance) => instance.name === selectedInstanceId);
  const tencentRoleMode = selectedInstance?.vendor === 'TENCENT';

  /* ─── State ─── */
  const [rules, setRules] = useState<AclRule[]>([]);
  const [users, setUsers] = useState<NormalizedAclUser[]>([]);
  const [rulesLoading, setRulesLoading] = useState(hasSelectedInstance);
  const [usersLoading, setUsersLoading] = useState(hasSelectedInstance);
  const [userPage, setUserPage] = useState(1);
  const [userPageSize, setUserPageSize] = useState(20);
  const [userTotal, setUserTotal] = useState(0);
  const [userKeyword, setUserKeyword] = useState('');
  const [ruleSubmitting, setRuleSubmitting] = useState(false);
  const [userSubmitting, setUserSubmitting] = useState(false);
  const [activeTab, setActiveTab] = useState('rules');
  const [ruleRefreshKey, setRuleRefreshKey] = useState(0);
  const [userRefreshKey, setUserRefreshKey] = useState(0);
  const [ruleTotal, setRuleTotal] = useState(0);
  const [rulePage, setRulePage] = useState(1);
  const [rulePageSize, setRulePageSize] = useState(20);

  // Rule filters
  const [rulePrincipalFilter, setRulePrincipalFilter] = useState('');
  const [ruleResourceFilter, setRuleResourceFilter] = useState('');
  const [ruleScopeFilter, setRuleScopeFilter] = useState<string>('all');
  const [ruleVersionFilter, setRuleVersionFilter] = useState<string>('all');
  const [ruleDecisionFilter, setRuleDecisionFilter] = useState<string>('all');

  // Rule modal
  const [ruleModalOpen, setRuleModalOpen] = useState(false);
  const [editingRule, setEditingRule] = useState<AclRule | null>(null);
  const [ruleForm] = Form.useForm();

  // User modal
  const [userModalOpen, setUserModalOpen] = useState(false);
  const [editingUser, setEditingUser] = useState<NormalizedAclUser | null>(null);
  const [userForm] = Form.useForm();

  // Secret key reveal
  const [revealedKeys, setRevealedKeys] = useState<Set<AclEntityId>>(new Set());
  const [adminUpdatingIds, setAdminUpdatingIds] = useState<Set<AclEntityId>>(() => new Set());
  const adminUpdateInFlightRef = useRef<Set<AclEntityId>>(new Set());
  const revealRequestGenerationRef = useRef<Record<string, number>>({});
  const [credentialsByUser, setCredentialsByUser] = useState<
    Record<string, { accessKey: string; secretKey: string }>
  >({});

  // Cluster ACL config (examineBrokerClusterAclConfig)
  const [clusterConfig, setClusterConfig] = useState<AclClusterConfig | null>(null);
  const [configLoading, setConfigLoading] = useState(false);
  const [clusterIdInput, setClusterIdInput] = useState('DefaultCluster');
  const examineRequestGenerationRef = useRef(0);

  // Plain access config modal
  const [plainModalOpen, setPlainModalOpen] = useState(false);
  const [editingPlain, setEditingPlain] = useState<PlainAccessConfig | null>(null);
  const [plainSubmitting, setPlainSubmitting] = useState(false);
  const [plainForm] = Form.useForm();

  useEffect(() => {
    let mounted = true;

    void listAclRules({
      instanceId: selectedInstanceId,
      principal: rulePrincipalFilter || undefined,
      resource: ruleResourceFilter || undefined,
      scope: ruleScopeFilter === 'all' ? undefined : ruleScopeFilter,
      aclVersion: ruleVersionFilter === 'all' ? undefined : ruleVersionFilter,
      decision: ruleDecisionFilter === 'all' ? undefined : ruleDecisionFilter,
      page: rulePage,
      pageSize: rulePageSize,
    })
      .then((nextRules) => {
        if (!mounted) return;
        setRules(nextRules.items.map(normalizeRule));
        setRuleTotal(nextRules.total);
        if (nextRules.items.length === 0 && nextRules.total > 0 && rulePage > 1) {
          setRulePage(Math.max(1, Math.ceil(nextRules.total / rulePageSize)));
        }
      })
      .catch(() => {
        if (mounted) message.error(t('common.fetchDataFailed'));
      })
      .finally(() => {
        if (mounted) setRulesLoading(false);
      });

    void pageAclUsers({
      instanceId: selectedInstanceId,
      page: userPage,
      pageSize: userPageSize,
      keyword: userKeyword || undefined,
    })
      .then((result) => {
        if (mounted) {
          setUsers(result.items.map(normalizeUser));
          setUserTotal(result.total);
          if (result.items.length === 0 && result.total > 0 && userPage > 1) {
            setUserPage(Math.max(1, Math.ceil(result.total / userPageSize)));
          }
        }
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
  }, [
    t,
    selectedInstanceId,
    rulePrincipalFilter,
    ruleResourceFilter,
    ruleScopeFilter,
    ruleVersionFilter,
    ruleDecisionFilter,
    rulePage,
    rulePageSize,
    ruleRefreshKey,
    userPage,
    userPageSize,
    userKeyword,
    userRefreshKey,
  ]);

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
      resourceType: tencentRoleMode ? 'Cluster' : undefined,
      resource: tencentRoleMode ? '*' : undefined,
      resourcePattern: tencentRoleMode ? 'LITERAL' : 'PREFIX',
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
      const normalizedValues = tencentRoleMode
        ? {
            ...values,
            resourceType: 'Cluster',
            resource: '*',
            resourcePattern: 'LITERAL',
            decision: 'ALLOW',
            scope: 'cluster',
          }
        : values;
      setRuleSubmitting(true);
      if (editingRule) {
        await updateAclRule({
          ...editingRule,
          ...normalizedValues,
          instanceId: selectedInstanceId,
        });
        message.success(t('acl.ruleUpdated'));
      } else {
        await createAclRule({
          ...normalizedValues,
          aclVersion: tencentRoleMode ? '1.0' : '2.0',
          instanceId: selectedInstanceId,
        });
        setRulePage(1);
        message.success(t('acl.ruleAdded'));
      }
      setRuleRefreshKey((prev) => prev + 1);
      setRuleModalOpen(false);
    } catch (error) {
      if (isFormValidationError(error)) return;
      message.error(t('common.operationFailed'));
    } finally {
      setRuleSubmitting(false);
    }
  };

  const handleDeleteRule = async (id: AclEntityId) => {
    try {
      await deleteAclRule(id, selectedInstanceId);
      setRuleRefreshKey((prev) => prev + 1);
      message.success(t('acl.ruleDeleted'));
    } catch {
      message.error(t('common.operationFailed'));
    }
  };

  /* ─── User helpers ─── */
  const toggleRevealKey = async (userId: AclEntityId) => {
    const userKey = String(userId);
    const revealing = !revealedKeys.has(userId);
    const revealGeneration = (revealRequestGenerationRef.current[userKey] ?? 0) + 1;
    revealRequestGenerationRef.current[userKey] = revealGeneration;
    setRevealedKeys((prev) => {
      const next = new Set(prev);
      if (next.has(userId)) {
        next.delete(userId);
      } else {
        next.add(userId);
      }
      return next;
    });
    if (!revealing || credentialsByUser[userKey]) return;
    try {
      const credentials = await getAclUserCredentials(userId, selectedInstanceId);
      if (revealRequestGenerationRef.current[userKey] !== revealGeneration) return;
      setCredentialsByUser((prev) => ({
        ...prev,
        [userKey]: {
          accessKey: credentials.accessKey ?? '',
          secretKey: credentials.secretKey ?? '',
        },
      }));
    } catch {
      if (revealRequestGenerationRef.current[userKey] !== revealGeneration) return;
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

  const openEditUserModal = (user: NormalizedAclUser) => {
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
          instanceId: selectedInstanceId,
        });
        const normalized = normalizeUser(updated);
        setUsers((prev) => prev.map((u) => (u.id === editingUser.id ? normalized : u)));
        message.success(t('acl.userUpdated'));
      } else {
        await createAclUser({
          username: values.username,
          admin: values.admin ?? false,
          clusters: values.clusters ?? [],
          instanceId: selectedInstanceId,
        });
        // Reload the authoritative server page so the pagination total and page count
        // stay consistent with the created row (same refresh pattern as the rules tab).
        setUserRefreshKey((prev) => prev + 1);
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

  const handleDeleteUser = async (id: AclEntityId) => {
    try {
      await deleteAclUser(id, selectedInstanceId);
      // Reload the authoritative server page so the pagination total follows the delete
      // and an emptied last page falls back to the previous one (same as the rules tab).
      setUserRefreshKey((prev) => prev + 1);
      message.success(t('acl.userDeleted'));
    } catch {
      message.error(t('common.operationFailed'));
    }
  };

  const handleToggleAdmin = async (user: AclUser, checked: boolean) => {
    if (tencentRoleMode) return;
    if (adminUpdateInFlightRef.current.has(user.id)) return;
    adminUpdateInFlightRef.current.add(user.id);
    setAdminUpdatingIds((current) => new Set(current).add(user.id));
    try {
      const updated = await updateAclUser({
        id: user.id,
        username: user.username,
        admin: checked,
        clusters: user.clusters,
        instanceId: selectedInstanceId,
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
    const requestGeneration = examineRequestGenerationRef.current + 1;
    examineRequestGenerationRef.current = requestGeneration;
    try {
      setConfigLoading(true);
      const config = await examineBrokerClusterAclConfig(clusterId);
      if (examineRequestGenerationRef.current !== requestGeneration) return;
      setClusterConfig(config);
      message.success(t('acl.configExamined'));
    } catch {
      if (examineRequestGenerationRef.current !== requestGeneration) return;
      message.error(t('common.operationFailed'));
    } finally {
      if (examineRequestGenerationRef.current === requestGeneration) {
        setConfigLoading(false);
      }
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
              style={{ backgroundColor: '#722ed1', fontSize: 14 }}
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
            style={{ fontSize: 14, lineHeight: '18px' }}
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
            <Tag key={action} color={actionTagColor[action]} style={{ fontSize: 14 }}>
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
        <span style={{ fontSize: 14 }}>
          {scope === 'cluster' ? t('acl.cluster') : t('acl.namespace')}
        </span>
      ),
    },
    {
      title: t('acl.createdAt'),
      dataIndex: 'gmtCreate',
      key: 'gmtCreate',
      width: 160,
      sorter: (a, b) => (a.gmtCreate ?? '').localeCompare(b.gmtCreate ?? ''),
      render: (iso?: string | null) => (
        <span style={{ fontSize: 14, color: '#8c8c8c' }}>{formatDate(iso)}</span>
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
  const userColumns: ColumnsType<NormalizedAclUser> = [
    {
      title: t('acl.username'),
      dataIndex: 'username',
      key: 'username',
      width: 200,
      sorter: (a, b) => a.username.localeCompare(b.username),
      render: (text: string, record: NormalizedAclUser) => (
        <Space size={6}>
          <User size={14} color="#8c8c8c" weight="fill" />
          <span style={{ fontWeight: 500 }}>{text}</span>
          {record.admin && (
            <Badge
              count={t('acl.adminBadge')}
              style={{ backgroundColor: '#722ed1', fontSize: 14 }}
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
      render: (text: string, record: NormalizedAclUser) => {
        const revealed = revealedKeys.has(record.id);
        const fullAccessKey = credentialsByUser[String(record.id)]?.accessKey ?? text;
        const displayedAccessKey = revealed && fullAccessKey ? fullAccessKey : text || '-';
        return (
          <Space size={8}>
            <Typography.Text
              copyable={fullAccessKey ? { text: fullAccessKey } : false}
              style={{ fontFamily: 'monospace', fontSize: 14 }}
            >
              {displayedAccessKey}
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
      render: (_: string, record: NormalizedAclUser) => {
        const revealed = revealedKeys.has(record.id);
        const secret = credentialsByUser[String(record.id)]?.secretKey;
        return (
          <Space size={8}>
            <Typography.Text
              copyable={revealed && secret ? { text: secret } : false}
              style={{ fontFamily: 'monospace', fontSize: 14 }}
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
          disabled={tencentRoleMode || adminUpdatingIds.has(record.id)}
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
            <Tag key={c} color="processing" style={{ fontSize: 14 }}>
              {c}
            </Tag>
          ))}
        </Space>
      ),
    },
    {
      title: t('acl.createdAt'),
      dataIndex: 'gmtCreate',
      key: 'gmtCreate',
      width: 160,
      sorter: (a, b) => (a.gmtCreate ?? '').localeCompare(b.gmtCreate ?? ''),
      render: (iso?: string | null) => (
        <span style={{ fontSize: 14, color: '#8c8c8c' }}>{formatDate(iso)}</span>
      ),
    },
    {
      title: t('common.actions'),
      key: 'userActions',
      width: 160,
      render: (_: unknown, record: NormalizedAclUser) => (
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

  const riskSeverityColor: Record<AclRiskIssue['severity'], string> = {
    critical: 'red',
    warning: 'gold',
    info: 'blue',
  };

  const riskSeverityText: Record<AclRiskIssue['severity'], string> = {
    critical: t('acl.riskCritical'),
    warning: t('acl.riskWarning'),
    info: t('acl.riskInfo'),
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
            <Tag key={p} color="blue" style={{ fontSize: 14 }}>
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
            <Tag key={p} color="green" style={{ fontSize: 14 }}>
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

  const aclRiskDiagnostics = clusterConfig ? analyzeAclRisk(clusterConfig) : null;

  const aclRiskProgressStatus =
    aclRiskDiagnostics?.status === 'critical'
      ? 'exception'
      : aclRiskDiagnostics?.status === 'healthy'
        ? 'success'
        : 'normal';

  const aclRiskStrokeColor =
    aclRiskDiagnostics?.status === 'critical'
      ? '#ff4d4f'
      : aclRiskDiagnostics?.status === 'warning'
        ? '#faad14'
        : '#52c41a';

  const aclRiskSummaryItems = aclRiskDiagnostics
    ? [
        {
          key: 'adminAccountCount',
          label: t('acl.riskAdminAccounts'),
          value: aclRiskDiagnostics.summary.adminAccountCount,
        },
        {
          key: 'defaultAllowAccountCount',
          label: t('acl.riskDefaultAllows'),
          value: aclRiskDiagnostics.summary.defaultAllowAccountCount,
        },
        {
          key: 'wildcardPermissionAccountCount',
          label: t('acl.riskWildcardAccounts'),
          value: aclRiskDiagnostics.summary.wildcardPermissionAccountCount,
        },
        {
          key: 'broadWhitelistCount',
          label: t('acl.riskBroadWhitelists'),
          value: aclRiskDiagnostics.summary.broadWhitelistCount,
        },
      ]
    : [];

  const aclRiskColumns: ColumnsType<AclRiskIssue> = [
    {
      title: t('acl.riskSeverity'),
      dataIndex: 'severity',
      key: 'severity',
      width: 100,
      render: (severity: AclRiskIssue['severity']) => (
        <Tag color={riskSeverityColor[severity]}>{riskSeverityText[severity]}</Tag>
      ),
    },
    {
      title: t('acl.riskItem'),
      key: 'item',
      width: 260,
      render: (_: unknown, record) => (
        <Space direction="vertical" size={2}>
          <Typography.Text strong>{record.title}</Typography.Text>
          <Typography.Text type="secondary">{record.description}</Typography.Text>
        </Space>
      ),
    },
    {
      title: t('acl.accessKey'),
      dataIndex: 'account',
      key: 'account',
      width: 160,
      render: (account?: string) =>
        account ? (
          <Typography.Text style={{ fontFamily: 'monospace' }}>{account}</Typography.Text>
        ) : (
          <span style={{ color: '#8c8c8c' }}>-</span>
        ),
    },
    {
      title: t('acl.riskEvidence'),
      dataIndex: 'evidence',
      key: 'evidence',
      width: 220,
      render: (evidence: string[]) => (
        <Space size={4} wrap>
          {evidence.length === 0 ? (
            <span style={{ color: '#8c8c8c' }}>-</span>
          ) : (
            evidence.map((item) => (
              <Typography.Text key={item} code>
                {item}
              </Typography.Text>
            ))
          )}
        </Space>
      ),
    },
    {
      title: t('acl.riskRecommendation'),
      dataIndex: 'recommendation',
      key: 'recommendation',
      width: 280,
      render: (text: string) => <Typography.Text>{text}</Typography.Text>,
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

      <InfoBanner
        data-testid="acl-local-metadata-notice"
        title={t(tencentRoleMode ? 'acl.tencentRoleNotice' : 'acl.localMetadataNotice')}
        description={t(
          tencentRoleMode ? 'acl.tencentRoleDescription' : 'acl.localMetadataDescription',
        )}
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
                      padding: '0 0 16px',
                      flexWrap: 'wrap',
                    }}
                  >
                    <InstanceSelect
                      value={selectedInstanceId || undefined}
                      onChange={selectInstance}
                      options={instanceOptions}
                      style={{ width: 220 }}
                    />
                    <Input
                      placeholder={t('acl.searchPrincipal')}
                      prefix={<MagnifyingGlass size={14} color="#9CA3AF" />}
                      value={rulePrincipalFilter}
                      onChange={(e) => {
                        setRulePage(1);
                        setRulePrincipalFilter(e.target.value);
                      }}
                      allowClear
                      style={{ width: 220 }}
                    />
                    <Input
                      placeholder={t('acl.searchResource')}
                      prefix={<MagnifyingGlass size={14} color="#9CA3AF" />}
                      value={ruleResourceFilter}
                      onChange={(e) => {
                        setRulePage(1);
                        setRuleResourceFilter(e.target.value);
                      }}
                      allowClear
                      style={{ width: 220 }}
                    />
                    <Select
                      value={ruleScopeFilter}
                      onChange={(value) => {
                        setRulePage(1);
                        setRuleScopeFilter(value);
                      }}
                      style={{ width: 180 }}
                      options={[
                        { value: 'all', label: t('acl.allScopes') },
                        { value: 'cluster', label: t('acl.clusterScope') },
                        { value: 'namespace', label: t('acl.namespaceScope') },
                      ]}
                    />
                    <Select
                      value={ruleVersionFilter}
                      onChange={(value) => {
                        setRulePage(1);
                        setRuleVersionFilter(value);
                      }}
                      style={{ width: 140 }}
                      options={[
                        { value: 'all', label: t('acl.allVersions') },
                        { value: '1.0', label: 'ACL 1.0' },
                        { value: '2.0', label: 'ACL 2.0' },
                      ]}
                    />
                    <Select
                      value={ruleDecisionFilter}
                      onChange={(value) => {
                        setRulePage(1);
                        setRuleDecisionFilter(value);
                      }}
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
                    dataSource={rules}
                    rowKey="id"
                    loading={rulesLoading}
                    pagination={{
                      current: rulePage,
                      pageSize: rulePageSize,
                      total: ruleTotal,
                      showSizeChanger: true,
                      showTotal: (total) => t('acl.totalRules', { n: total }),
                      onChange: (page, pageSize) => {
                        if (pageSize !== rulePageSize) {
                          setRulePage(1);
                          setRulePageSize(pageSize);
                          return;
                        }
                        setRulePage(page);
                      },
                    }}
                    size="small"
                    scroll={{ x: tableScrollX(ruleColumns) }}
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
                  <div style={{ padding: '0 0 16px' }}>
                    <Space>
                      <Button
                        type="primary"
                        icon={<Plus size={14} weight="bold" />}
                        onClick={openAddUserModal}
                      >
                        {t('acl.addUser')}
                      </Button>
                      <Input.Search
                        value={userKeyword}
                        onChange={(event) => {
                          setUserPage(1);
                          setUserKeyword(event.target.value);
                        }}
                        allowClear
                        style={{ width: 240 }}
                      />
                    </Space>
                  </div>

                  <Table
                    columns={userColumns}
                    dataSource={users}
                    rowKey="id"
                    loading={usersLoading}
                    pagination={{
                      current: userPage,
                      pageSize: userPageSize,
                      total: userTotal,
                      showSizeChanger: true,
                      showTotal: (total) => t('acl.totalUsers', { n: total }),
                      onChange: (page, pageSize) => {
                        setUserPage(page);
                        setUserPageSize(pageSize);
                      },
                    }}
                    size="small"
                    scroll={{ x: tableScrollX(userColumns) }}
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
                      padding: '0 0 16px',
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
                          style={{ fontSize: 14, padding: '4px 10px' }}
                        >
                          {clusterConfig.aclEnabled ? t('acl.aclEnabled') : t('acl.aclDisabled')}
                        </Tag>
                        <Tag color="geekblue" style={{ fontSize: 14, padding: '4px 10px' }}>
                          {clusterConfig.aclVersion}
                        </Tag>
                        <Tag style={{ fontSize: 14, padding: '4px 10px' }}>
                          {t('acl.accountCount')}: {clusterConfig.accountCount}
                        </Tag>
                      </div>

                      <div style={{ marginBottom: 12, color: '#8c8c8c', fontSize: 14 }}>
                        {t('acl.globalWhitelist')}:{' '}
                        {clusterConfig.globalWhiteRemoteAddresses.length === 0 ? (
                          <span>-</span>
                        ) : (
                          clusterConfig.globalWhiteRemoteAddresses.map((ip) => (
                            <Tag key={ip} color="cyan" style={{ fontSize: 14 }}>
                              {ip}
                            </Tag>
                          ))
                        )}
                      </div>

                      {aclRiskDiagnostics && (
                        <div
                          data-testid="acl-risk-diagnostics"
                          style={{
                            border: '1px solid #f0f0f0',
                            borderRadius: 8,
                            padding: 16,
                            marginBottom: 16,
                          }}
                        >
                          <Flex gap={20} align="center" wrap="wrap" style={{ marginBottom: 16 }}>
                            <Progress
                              type="circle"
                              percent={aclRiskDiagnostics.score}
                              size={96}
                              status={aclRiskProgressStatus}
                              strokeColor={aclRiskStrokeColor}
                              format={(percent) => `${percent}`}
                            />
                            <div style={{ minWidth: 220, flex: '1 1 260px' }}>
                              <Typography.Title level={5} style={{ margin: 0 }}>
                                {t('acl.riskDiagnostics')}
                              </Typography.Title>
                              <Typography.Text type="secondary">
                                {aclRiskDiagnostics.statusText}
                              </Typography.Text>
                              <div style={{ marginTop: 8 }}>
                                <Tag color={aclRiskDiagnostics.statusColor}>
                                  {t('acl.riskIssues')}: {aclRiskDiagnostics.issues.length}
                                </Tag>
                                <Tag>
                                  {t('acl.accountCount')}: {aclRiskDiagnostics.summary.accountCount}
                                </Tag>
                              </div>
                            </div>
                            <Flex gap={16} wrap="wrap" style={{ flex: '2 1 420px' }}>
                              {aclRiskSummaryItems.map((item) => (
                                <div key={item.key} style={{ minWidth: 118 }}>
                                  <Statistic
                                    title={item.label}
                                    value={item.value}
                                    valueStyle={{ fontSize: 22 }}
                                  />
                                </div>
                              ))}
                            </Flex>
                          </Flex>

                          {aclRiskDiagnostics.issues.length === 0 ? (
                            <Alert type="success" showIcon message={t('acl.riskHealthyMessage')} />
                          ) : (
                            <Table<AclRiskIssue>
                              columns={aclRiskColumns}
                              dataSource={aclRiskDiagnostics.issues}
                              rowKey="id"
                              pagination={false}
                              size="small"
                              scroll={{ x: tableScrollX(aclRiskColumns) }}
                              style={{ marginBottom: 12 }}
                            />
                          )}

                          <div>
                            <Typography.Text strong>{t('acl.riskRecommendations')}</Typography.Text>
                            <ul style={{ margin: '8px 0 0', paddingLeft: 20 }}>
                              {aclRiskDiagnostics.recommendations.map((item) => (
                                <li key={item}>
                                  <Typography.Text>{item}</Typography.Text>
                                </li>
                              ))}
                            </ul>
                          </div>
                        </div>
                      )}

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
              disabled={tencentRoleMode}
              options={
                tencentRoleMode
                  ? [{ value: 'Cluster', label: 'Cluster' }]
                  : [
                      { value: 'Topic', label: 'Topic' },
                      { value: 'Group', label: 'Group' },
                      { value: 'Cluster', label: 'Cluster' },
                    ]
              }
            />
          </Form.Item>

          <Form.Item
            name="resource"
            label={t('acl.resourceName')}
            rules={[
              { required: true, message: t('acl.inputRequired', { field: t('acl.resourceName') }) },
            ]}
          >
            <Input placeholder={t('acl.resourceNamePlaceholder')} disabled={tencentRoleMode} />
          </Form.Item>

          <Form.Item name="resourcePattern" label={t('acl.matchPattern')}>
            <Radio.Group disabled={tencentRoleMode}>
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
            <Radio.Group disabled={tencentRoleMode}>
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
              disabled={tencentRoleMode}
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
            <Switch
              checkedChildren={t('common.yes')}
              unCheckedChildren={t('common.no')}
              disabled={tencentRoleMode}
            />
          </Form.Item>

          <Form.Item name="clusters" label={t('acl.associatedClusters')}>
            <Select
              mode="tags"
              tokenSeparators={[',']}
              allowClear
              disabled={tencentRoleMode}
              options={instances.map((instance) => ({
                value: instance.cloudInstanceId ?? instance.name,
                label: instance.name,
              }))}
            />
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

const AclPage = () => {
  const instanceFilter = useInstanceFilter();
  return (
    <AclPageContent
      key={instanceFilter.selectedInstanceId || 'no-selected-instance'}
      {...instanceFilter}
    />
  );
};

export default AclPage;
