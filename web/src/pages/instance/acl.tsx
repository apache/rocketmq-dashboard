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

import { useEffect, useState } from 'react';
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
  message,
} from 'antd';
import { Plus, MagnifyingGlass, ShieldCheck, User, Eye, EyeSlash } from '@phosphor-icons/react';
import { EditOutlined, DeleteOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import PageHeader from '../../components/PageHeader';
import { useLang } from '../../i18n/LangContext';
import {
  createAclRule,
  createAclUser,
  deleteAclRule,
  deleteAclUser,
  listAclRules,
  listAclUsers,
  updateAclRule,
  updateAclUser,
} from '../../services/aclService';
import type { AclRule, AclUser } from '../../api/acl';

type AclRuleFormValues = Pick<
  AclRule,
  'principal' | 'resource' | 'resourceType' | 'resourcePattern' | 'actions' | 'decision' | 'scope'
>;
type AclUserFormValues = Pick<AclUser, 'username' | 'accessKey' | 'secretKey' | 'admin'>;

const normalizeRule = (rule: AclRule): AclRule => ({
  ...rule,
  principal: rule.principal ?? '',
  resource: rule.resource ?? '',
  resourceType: rule.resourceType ?? '',
  resourcePattern: rule.resourcePattern ?? '',
  actions: rule.actions ?? [],
  decision: rule.decision ?? '',
  scope: rule.scope ?? '',
  aclVersion: rule.aclVersion ?? '2.0',
  createdAt: rule.createdAt ?? new Date().toISOString(),
});

const normalizeUser = (user: AclUser): AclUser => ({
  ...user,
  username: user.username ?? '',
  accessKey: user.accessKey ?? '',
  secretKey: user.secretKey ?? '',
  admin: user.admin ?? false,
  clusters: user.clusters ?? [],
  createdAt: user.createdAt ?? new Date().toISOString(),
});

const isFormValidationError = (error: unknown) =>
  typeof error === 'object' && error !== null && 'errorFields' in error;

/* ═══════════════════════════════════════════
   ACL Management Page
   ═══════════════════════════════════════════ */
const AclPage = () => {
  const { t } = useLang();

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

  useEffect(() => {
    let mounted = true;

    Promise.all([listAclRules(), listAclUsers()])
      .then(([nextRules, nextUsers]) => {
        if (!mounted) return;
        setRules(nextRules.map(normalizeRule));
        setUsers(nextUsers.map(normalizeUser));
      })
      .catch(() => {
        if (mounted) message.error(t('common.fetchDataFailed'));
      })
      .finally(() => {
        if (!mounted) return;
        setRulesLoading(false);
        setUsersLoading(false);
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
          aclVersion: 2,
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
  const toggleRevealKey = (userId: string) => {
    setRevealedKeys((prev) => {
      const next = new Set(prev);
      if (next.has(userId)) {
        next.delete(userId);
      } else {
        next.add(userId);
      }
      return next;
    });
  };

  const openAddUserModal = () => {
    setEditingUser(null);
    userForm.resetFields();
    userForm.setFieldsValue({ admin: false });
    setUserModalOpen(true);
  };

  const openEditUserModal = (user: AclUser) => {
    setEditingUser(user);
    userForm.setFieldsValue({
      username: user.username,
      accessKey: user.accessKey,
      secretKey: user.secretKey,
      admin: user.admin,
    });
    setUserModalOpen(true);
  };

  const handleUserSubmit = async () => {
    try {
      const values = (await userForm.validateFields()) as AclUserFormValues;
      setUserSubmitting(true);
      if (editingUser) {
        const updated = await updateAclUser({ ...editingUser, ...values });
        const normalized = normalizeUser(updated);
        setUsers((prev) => prev.map((u) => (u.id === editingUser.id ? normalized : u)));
        message.success(t('acl.userUpdated'));
      } else {
        const created = await createAclUser({
          username: values.username,
          accessKey: values.accessKey,
          secretKey: values.secretKey,
          admin: values.admin ?? false,
          clusters: ['rmq-cn-v5-prod-01'],
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
    try {
      const updated = await updateAclUser({ ...user, admin: checked });
      const normalized = normalizeUser(updated);
      setUsers((prev) => prev.map((u) => (u.id === user.id ? normalized : u)));
      message.success(checked ? t('acl.adminSet') : t('acl.adminRemoved'));
    } catch {
      message.error(t('common.operationFailed'));
    }
  };

  const formatDate = (iso: string) => {
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
      sorter: (a, b) => a.createdAt.localeCompare(b.createdAt),
      render: (iso: string) => (
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
        return (
          <Space size={8}>
            <Typography.Text copyable={{ text }} style={{ fontFamily: 'monospace', fontSize: 13 }}>
              {revealed ? text : text.replace(/(?<=\*{4}).+/, '••••')}
            </Typography.Text>
            <Button
              type="text"
              size="small"
              icon={revealed ? <EyeSlash size={14} /> : <Eye size={14} />}
              onClick={() => toggleRevealKey(record.id)}
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
      sorter: (a, b) => a.createdAt.localeCompare(b.createdAt),
      render: (iso: string) => (
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

      <Card bordered={false} bodyStyle={{ padding: 0 }}>
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
        destroyOnClose
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
              options={[
                { value: 'cluster', label: t('acl.clusterScope') },
                { value: 'namespace', label: t('acl.namespaceScope') },
              ]}
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
        destroyOnClose
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

          <Form.Item name="accessKey" label="Access Key">
            <Input placeholder={t('acl.autoOrManual')} style={{ fontFamily: 'monospace' }} />
          </Form.Item>

          <Form.Item name="secretKey" label="Secret Key">
            <Input.Password
              placeholder={t('acl.autoOrManual')}
              style={{ fontFamily: 'monospace' }}
            />
          </Form.Item>

          <Form.Item name="admin" label={t('acl.admin')} valuePropName="checked">
            <Switch checkedChildren={t('common.yes')} unCheckedChildren={t('common.no')} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default AclPage;
