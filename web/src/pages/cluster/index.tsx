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
import { useSearchParams } from 'react-router-dom';
import {
  Table,
  Tabs,
  Tag,
  Button,
  Input,
  Select,
  Modal,
  Form,
  Radio,
  Switch,
  InputNumber,
  Progress,
  Descriptions,
  Flex,
  Space,
  Typography,
  Card,
  Alert,
  Spin,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  ReloadOutlined,
  SettingOutlined,
  EyeOutlined,
  DeleteOutlined,
  EditOutlined,
  PlusOutlined,
  CloudSyncOutlined,
} from '@ant-design/icons';
import { Cpu, HardDrives, Globe } from '@phosphor-icons/react';
import PageHeader from '../../components/PageHeader';
import { useLang } from '../../i18n/LangContext';
import { countClusterComponents } from './clusterStats';
import type {
  BrokerConfigDiffResult,
  BrokerConfigDifference,
  BrokerInfo,
  ProxyInfo,
  NameserverRegistryEntry,
  ClusterConfig,
  ClusterConfigPreviewChange,
  ClusterConfigPreviewResult,
  ClusterInfo,
  ClusterProbeResult,
  NameServerConfigDiffResult,
  NameServerConfigDifference,
  KubernetesNameServerCandidate,
} from '../../api/cluster';
import {
  createNameserverRegistry,
  deleteNameserverRegistry,
  discoverKubernetesNameServers,
  getBrokerConfigDiff,
  getNameServerConfigDiff,
  listClusters,
  listK8sCerts,
  listNameserverRegistry,
  listRegistryClusters,
  previewClusterConfig,
  restartProxy,
  testClusterConnection,
  updateClusterConfig,
  updateNameserverRegistry,
} from '../../services/clusterService';
import { listInstances } from '../../services/instanceService';
import { supportsApacheRuntime } from '../../api/instance';
import { isMockMode } from '../../services/dataMode';
import { tableScrollX } from '../../utils/table';

const { Text } = Typography;

const REFRESH_INTERVAL_MS = 2000;

type RefreshSource = 'initial' | 'manual' | 'operation' | 'background';

type ProxyDetail = ProxyInfo & { clusterId: string; clusterName: string; nsClusterName: string };
type ClusterConfigFormValues = Partial<ClusterConfig> & { maxMessageSizeMB: number };
type ClusterConfigRequest = { id: string; instanceId?: string } & Partial<ClusterConfig>;
type NameServerConfigDiffNode = NameServerConfigDiffResult['nodes'][number];
type BrokerConfigDiffBroker = BrokerConfigDiffResult['brokers'][number];

const safeText = (value: string | null | undefined) => value ?? '';
const searchText = (value: string | null | undefined) => safeText(value).toLowerCase();
const compareText = (left: string | null | undefined, right: string | null | undefined) =>
  safeText(left).localeCompare(safeText(right));

const CONFIG_FIELD_LABEL_KEYS: Record<string, string> = {
  flushDiskType: 'cluster.flushDiskType',
  autoCreateTopicEnable: 'cluster.autoCreateTopic',
  autoCreateSubscriptionGroup: 'cluster.autoCreateSubGroup',
  maxMessageSize: 'cluster.maxMessageSize',
  fileReservedTime: 'cluster.fileReservedTime',
  writeQueueNums: 'cluster.writeQueues',
  readQueueNums: 'cluster.readQueues',
  brokerPermission: 'cluster.brokerPermission',
  deleteWhen: 'cluster.deleteWhen',
  msgTraceTopicName: 'cluster.msgTraceTopicName',
};

// ─── Page ─────────────────────────────────────────────────────────────────────

const ClusterPage = () => {
  const { t } = useLang();
  const [searchParams] = useSearchParams();
  const requestedInstanceIdParam = searchParams.get('instanceId');
  const requestedInstanceId = requestedInstanceIdParam ?? undefined;
  const [clusters, setClusters] = useState<ClusterInfo[]>([]);
  const [instanceLoadError, setInstanceLoadError] = useState<string | null>(null);
  const [instanceLoadKey, setInstanceLoadKey] = useState(0);
  const [loading, setLoading] = useState(true);
  const [nsSearch, setNsSearch] = useState('');
  const [brokerSearch, setBrokerSearch] = useState('');
  const [proxySearch, setProxySearch] = useState('');

  const [configModalOpen, setConfigModalOpen] = useState(false);
  const [selectedCluster, setSelectedCluster] = useState<ClusterInfo | null>(null);
  const [configPreview, setConfigPreview] = useState<ClusterConfigPreviewResult | null>(null);
  const [configPreviewLoading, setConfigPreviewLoading] = useState(false);
  const [configSubmitting, setConfigSubmitting] = useState(false);
  const [nsRegistry, setNsRegistry] = useState<NameserverRegistryEntry[]>([]);
  const [selectedProxy, setSelectedProxy] = useState<ProxyDetail | null>(null);
  const [nsConfigDiffState, setNsConfigDiffState] = useState<{
    open: boolean;
    loading: boolean;
    cluster: ClusterInfo | null;
    result: NameServerConfigDiffResult | null;
  }>({
    open: false,
    loading: false,
    cluster: null,
    result: null,
  });
  const [brokerConfigDiffState, setBrokerConfigDiffState] = useState<{
    open: boolean;
    loading: boolean;
    cluster: ClusterInfo | null;
    result: BrokerConfigDiffResult | null;
  }>({
    open: false,
    loading: false,
    cluster: null,
    result: null,
  });
  const [configForm] = Form.useForm();

  const [k8sIdOptions, setK8sIdOptions] = useState<string[]>([]);

  const [registryClusters, setRegistryClusters] = useState<ClusterInfo[]>([]);
  const [registryLoading, setRegistryLoading] = useState(true);
  const nsRegistryRequestRef = useRef(0);
  const registryClustersRequestRef = useRef(0);
  const k8sCertsRequestRef = useRef(0);
  const nsConfigDiffRequestRef = useRef(0);
  const connectionTestRequestRef = useRef(0);
  const kubernetesDiscoveryRequestRef = useRef(0);

  const loadRegistryClusters = useCallback(async () => {
    const requestId = ++registryClustersRequestRef.current;
    void Promise.resolve().then(() => {
      if (registryClustersRequestRef.current === requestId) setRegistryLoading(true);
    });
    try {
      const nextClusters = await listRegistryClusters();
      if (registryClustersRequestRef.current === requestId) {
        setRegistryClusters(nextClusters);
      }
    } catch {
      if (registryClustersRequestRef.current === requestId) {
        setRegistryClusters([]);
      }
    } finally {
      if (registryClustersRequestRef.current === requestId) {
        setRegistryLoading(false);
      }
    }
  }, []);

  useEffect(() => {
    void Promise.resolve().then(loadRegistryClusters);
  }, [loadRegistryClusters]);

  const loadNsRegistry = useCallback(async () => {
    const requestId = ++nsRegistryRequestRef.current;
    try {
      const entries = await listNameserverRegistry();
      if (nsRegistryRequestRef.current === requestId) setNsRegistry(entries);
    } catch {
      if (nsRegistryRequestRef.current === requestId) setNsRegistry([]);
    }
  }, []);

  useEffect(() => {
    void Promise.resolve().then(loadNsRegistry);
  }, [loadNsRegistry]);

  useEffect(() => {
    const requestId = ++k8sCertsRequestRef.current;
    listK8sCerts()
      .then((certs) => {
        if (k8sCertsRequestRef.current === requestId) {
          setK8sIdOptions([...new Set(certs.map((cert) => cert.k8sId).filter(Boolean))]);
        }
      })
      .catch(() => {
        if (k8sCertsRequestRef.current === requestId) setK8sIdOptions([]);
      });
  }, []);

  useEffect(
    () => () => {
      nsRegistryRequestRef.current += 1;
      registryClustersRequestRef.current += 1;
      k8sCertsRequestRef.current += 1;
      nsConfigDiffRequestRef.current += 1;
      connectionTestRequestRef.current += 1;
      kubernetesDiscoveryRequestRef.current += 1;
    },
    [],
  );

  const [nsCreateModalOpen, setNsCreateModalOpen] = useState(false);
  const [nsModalMode, setNsModalMode] = useState<'create' | 'edit'>('create');
  const [nsEditId, setNsEditId] = useState<number | null>(null);
  const [nsCreateForm] = Form.useForm();
  const [kubernetesDiscoveryOpen, setKubernetesDiscoveryOpen] = useState(false);
  const [kubernetesDiscoveryLoading, setKubernetesDiscoveryLoading] = useState(false);
  const [kubernetesDiscoveryCandidates, setKubernetesDiscoveryCandidates] = useState<
    KubernetesNameServerCandidate[]
  >([]);
  const [kubernetesDiscoverySearched, setKubernetesDiscoverySearched] = useState(false);
  const [kubernetesDiscoveryForm] = Form.useForm<{ namespace: string }>();

  const handleNsSubmit = useCallback(async () => {
    let values: Record<string, string>;
    try {
      values = await nsCreateForm.validateFields();
    } catch {
      return;
    }
    const payload = {
      name: values.name,
      namesrvAddr: values.namesrvAddr,
      k8sNamespace: values.k8sNamespace || undefined,
      k8sId: values.k8sId || undefined,
      description: values.description || undefined,
    };
    try {
      if (nsModalMode === 'edit' && nsEditId !== null) {
        await updateNameserverRegistry({ id: nsEditId, ...payload });
        message.success(t('cluster.nsUpdated'));
      } else {
        await createNameserverRegistry(payload);
        message.success(t('cluster.nsCreated'));
      }
      setNsCreateModalOpen(false);
      nsCreateForm.resetFields();
      await loadNsRegistry();
    } catch {
      message.error(t('cluster.nsOperationFailed'));
    }
  }, [loadNsRegistry, nsCreateForm, nsEditId, nsModalMode, t]);

  const openNsCreateModal = useCallback(() => {
    setNsModalMode('create');
    setNsEditId(null);
    nsCreateForm.resetFields();
    setNsCreateModalOpen(true);
  }, [nsCreateForm]);

  const openNsEditModal = useCallback(
    (entry: NameserverRegistryEntry) => {
      setNsModalMode('edit');
      setNsEditId(entry.id);
      nsCreateForm.setFieldsValue({
        name: entry.name,
        namesrvAddr: entry.namesrvAddr,
        k8sNamespace: entry.k8sNamespace ?? '',
        k8sId: entry.k8sId ?? '',
        description: entry.description ?? '',
      });
      setNsCreateModalOpen(true);
    },
    [nsCreateForm],
  );

  const openKubernetesDiscovery = useCallback(() => {
    kubernetesDiscoveryRequestRef.current += 1;
    setKubernetesDiscoveryCandidates([]);
    setKubernetesDiscoverySearched(false);
    setKubernetesDiscoveryLoading(false);
    kubernetesDiscoveryForm.resetFields();
    setKubernetesDiscoveryOpen(true);
  }, [kubernetesDiscoveryForm]);

  const closeKubernetesDiscovery = useCallback(() => {
    kubernetesDiscoveryRequestRef.current += 1;
    setKubernetesDiscoveryOpen(false);
    setKubernetesDiscoveryLoading(false);
  }, []);

  const handleKubernetesDiscovery = useCallback(async () => {
    let namespace: string;
    try {
      ({ namespace } = await kubernetesDiscoveryForm.validateFields());
    } catch {
      return;
    }
    const requestId = ++kubernetesDiscoveryRequestRef.current;
    setKubernetesDiscoveryLoading(true);
    try {
      const result = await discoverKubernetesNameServers(namespace.trim());
      if (requestId !== kubernetesDiscoveryRequestRef.current) return;
      setKubernetesDiscoveryCandidates(result.candidates);
      setKubernetesDiscoverySearched(true);
      if (result.candidates.length === 0) {
        message.info(t('cluster.k8sDiscoveryEmpty'));
      }
    } catch (error) {
      if (requestId !== kubernetesDiscoveryRequestRef.current) return;
      setKubernetesDiscoveryCandidates([]);
      setKubernetesDiscoverySearched(true);
      message.error(
        error instanceof Error && error.message ? error.message : t('cluster.k8sDiscoveryFailed'),
      );
    } finally {
      if (requestId === kubernetesDiscoveryRequestRef.current) {
        setKubernetesDiscoveryLoading(false);
      }
    }
  }, [kubernetesDiscoveryForm, t]);

  const selectKubernetesCandidate = useCallback(
    (candidate: KubernetesNameServerCandidate) => {
      setNsModalMode('create');
      setNsEditId(null);
      nsCreateForm.setFieldsValue({
        name: candidate.resourceName,
        namesrvAddr: candidate.namesrvAddr,
        k8sNamespace: candidate.namespace,
        description: t('cluster.k8sDiscoveredDescription', { source: candidate.source }),
      });
      setNsCreateModalOpen(true);
      closeKubernetesDiscovery();
    },
    [closeKubernetesDiscovery, nsCreateForm, t],
  );

  const handleNsDelete = useCallback(
    (entry: NameserverRegistryEntry) => {
      Modal.confirm({
        title: t('cluster.deleteNsConfirmTitle'),
        content: t('cluster.deleteNsConfirmContent', { name: entry.name }),
        okText: t('common.delete'),
        okType: 'danger',
        cancelText: t('common.cancel'),
        onOk: async () => {
          try {
            await deleteNameserverRegistry(entry.id);
            message.success(t('cluster.nsDeleted'));
            await loadNsRegistry();
          } catch {
            message.error(t('cluster.nsOperationFailed'));
          }
        },
      });
    },
    [loadNsRegistry, t],
  );

  const resolveNameserverRegistryCluster = useCallback(
    (entry: NameserverRegistryEntry) => {
      const namesrvAddr = safeText(entry.namesrvAddr);
      const name = safeText(entry.name);
      return registryClusters.find(
        (cluster) =>
          cluster.endpoint === namesrvAddr ||
          cluster.nameServers.some((nameServer) => nameServer.addr === namesrvAddr) ||
          cluster.name === name ||
          cluster.nsClusterName === name,
      );
    },
    [registryClusters],
  );

  const openNameServerConfigDiff = useCallback(
    async (cluster: ClusterInfo) => {
      const requestId = ++nsConfigDiffRequestRef.current;
      setNsConfigDiffState({
        open: true,
        loading: true,
        cluster,
        result: null,
      });
      try {
        const result = await getNameServerConfigDiff(cluster.id, selectedInstanceIdRef.current);
        if (requestId !== nsConfigDiffRequestRef.current) return;
        setNsConfigDiffState({
          open: true,
          loading: false,
          cluster,
          result,
        });
      } catch {
        if (requestId !== nsConfigDiffRequestRef.current) return;
        setNsConfigDiffState((current) => ({ ...current, loading: false }));
        message.error(t('cluster.nsConfigDiffFailed'));
      }
    },
    [t],
  );

  const openBrokerConfigDiff = useCallback(
    async (cluster: ClusterInfo) => {
      setBrokerConfigDiffState({
        open: true,
        loading: true,
        cluster,
        result: null,
      });
      try {
        const result = await getBrokerConfigDiff(cluster.id, selectedInstanceIdRef.current);
        setBrokerConfigDiffState({
          open: true,
          loading: false,
          cluster,
          result,
        });
      } catch {
        setBrokerConfigDiffState((current) => ({ ...current, loading: false }));
        message.error(t('cluster.brokerConfigDiffFailed'));
      }
    },
    [t],
  );
  const closeNameServerConfigDiff = useCallback(() => {
    nsConfigDiffRequestRef.current += 1;
    setNsConfigDiffState({ open: false, loading: false, cluster: null, result: null });
  }, []);

  // ─── Connection test ──────────────────────────────────────────────────────
  const [connectModalOpen, setConnectModalOpen] = useState(false);
  const [connectTesting, setConnectTesting] = useState(false);
  const [probeResult, setProbeResult] = useState<ClusterProbeResult | null>(null);
  const [connectForm] = Form.useForm();

  const openConnectModal = useCallback(() => {
    connectionTestRequestRef.current += 1;
    setProbeResult(null);
    setConnectTesting(false);
    setConnectModalOpen(true);
  }, []);

  const closeConnectModal = useCallback(() => {
    connectionTestRequestRef.current += 1;
    setConnectModalOpen(false);
    setConnectTesting(false);
    setProbeResult(null);
    connectForm.resetFields();
  }, [connectForm]);

  const handleTestConnection = useCallback(async () => {
    const requestId = ++connectionTestRequestRef.current;
    let namesrvAddr: string;
    try {
      ({ namesrvAddr } = await connectForm.validateFields());
    } catch {
      return;
    }
    if (requestId !== connectionTestRequestRef.current) return;
    setConnectTesting(true);
    setProbeResult(null);
    try {
      const result = await testClusterConnection(namesrvAddr);
      if (requestId !== connectionTestRequestRef.current) return;
      setProbeResult(result);
      message.success(t('cluster.testConnectionSuccess'));
    } catch {
      if (requestId !== connectionTestRequestRef.current) return;
      message.error(t('cluster.testConnectionFailed'));
    } finally {
      if (requestId === connectionTestRequestRef.current) setConnectTesting(false);
    }
  }, [connectForm, t]);

  // ─── Cluster refresh coordinator ──────────────────────────────────────────
  const [autoRefresh, setAutoRefresh] = useState(true);
  const [refreshFailed, setRefreshFailed] = useState(false);
  const mountedRef = useRef(false);
  const autoRefreshRef = useRef(true);
  const refreshTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const inFlightRefreshRef = useRef<Promise<void> | null>(null);
  const queuedForegroundRef = useRef<RefreshSource | null>(null);
  const queuedBackgroundRef = useRef(false);
  const requestRefreshRef = useRef<(source: RefreshSource) => Promise<void>>(() =>
    Promise.resolve(),
  );
  const tRef = useRef(t);
  const selectedInstanceIdRef = useRef<string | undefined>(undefined);
  const instanceLoadRetryRef = useRef(0);

  useEffect(() => {
    let cancelled = false;
    void listInstances()
      .then((nextInstances) => {
        if (cancelled) return;
        const apacheInstances = nextInstances.filter(supportsApacheRuntime);
        const initialInstanceId = apacheInstances.some(
          (instance) => instance.name === requestedInstanceId,
        )
          ? requestedInstanceId
          : apacheInstances[0]?.name;
        selectedInstanceIdRef.current = initialInstanceId;
        instanceLoadRetryRef.current = 0;
        setInstanceLoadError(null);
        if (initialInstanceId) void requestRefreshRef.current('manual');
      })
      .catch(() => {
        if (cancelled) return;
        selectedInstanceIdRef.current = undefined;
        setClusters([]);
        setSelectedProxy(null);
        setLoading(false);
        if (instanceLoadRetryRef.current < 3) {
          instanceLoadRetryRef.current += 1;
          window.setTimeout(() => setInstanceLoadKey((key) => key + 1), 3000);
        } else {
          setInstanceLoadError(tRef.current('common.fetchDataFailed'));
          setAutoRefresh(false);
          autoRefreshRef.current = false;
        }
      });
    return () => {
      cancelled = true;
    };
  }, [instanceLoadKey, requestedInstanceId]);

  const clearRefreshTimer = useCallback(() => {
    if (refreshTimerRef.current !== null) {
      clearTimeout(refreshTimerRef.current);
      refreshTimerRef.current = null;
    }
  }, []);

  const requestRefresh = useCallback(
    (source: RefreshSource): Promise<void> => {
      clearRefreshTimer();
      if (!mountedRef.current) return Promise.resolve();

      if (!selectedInstanceIdRef.current && !isMockMode()) {
        setClusters([]);
        setLoading(false);
        return Promise.resolve();
      }

      if (source !== 'background') setLoading(true);

      if (inFlightRefreshRef.current) {
        if (source === 'background') {
          if (autoRefreshRef.current) queuedBackgroundRef.current = true;
        } else if (source !== 'initial' && !queuedForegroundRef.current) {
          queuedForegroundRef.current = source;
        }
        return inFlightRefreshRef.current;
      }

      const runRefreshes = async () => {
        let currentSource: RefreshSource | null = source;

        while (currentSource && mountedRef.current) {
          try {
            const nextClusters = await listClusters(selectedInstanceIdRef.current);
            if (!mountedRef.current) return;
            setClusters(nextClusters);
            setRefreshFailed(false);
          } catch {
            if (!mountedRef.current) return;
            setRefreshFailed(true);
            if (currentSource !== 'background') {
              message.error(tRef.current('common.fetchDataFailed'));
            }
          }

          if (!mountedRef.current) return;
          if (queuedForegroundRef.current) {
            currentSource = queuedForegroundRef.current;
            queuedForegroundRef.current = null;
            queuedBackgroundRef.current = false;
          } else if (queuedBackgroundRef.current && autoRefreshRef.current) {
            currentSource = 'background';
            queuedBackgroundRef.current = false;
          } else {
            queuedBackgroundRef.current = false;
            currentSource = null;
          }
        }
      };

      const refreshCycle = runRefreshes().finally(() => {
        inFlightRefreshRef.current = null;
        if (!mountedRef.current) return;

        if (queuedForegroundRef.current) {
          const queuedSource = queuedForegroundRef.current;
          queuedForegroundRef.current = null;
          queuedBackgroundRef.current = false;
          return requestRefreshRef.current(queuedSource);
        }
        if (queuedBackgroundRef.current && autoRefreshRef.current) {
          queuedBackgroundRef.current = false;
          return requestRefreshRef.current('background');
        }

        queuedBackgroundRef.current = false;
        setLoading(false);
        if (autoRefreshRef.current) {
          refreshTimerRef.current = setTimeout(() => {
            refreshTimerRef.current = null;
            void requestRefreshRef.current('background');
          }, REFRESH_INTERVAL_MS);
        }
      });
      inFlightRefreshRef.current = refreshCycle;
      return refreshCycle;
    },
    [clearRefreshTimer],
  );

  useEffect(() => {
    tRef.current = t;
  }, [t]);

  useEffect(() => {
    requestRefreshRef.current = requestRefresh;
  }, [requestRefresh]);

  useEffect(() => {
    mountedRef.current = true;
    autoRefreshRef.current = true;
    void requestRefreshRef.current('initial');
    return () => {
      mountedRef.current = false;
      clearRefreshTimer();
      queuedForegroundRef.current = null;
      queuedBackgroundRef.current = false;
    };
  }, [clearRefreshTimer]);

  const handleAutoRefreshChange = (checked: boolean) => {
    autoRefreshRef.current = checked;
    setAutoRefresh(checked);
    clearRefreshTimer();
    if (!checked) {
      queuedBackgroundRef.current = false;
      return;
    }
    void requestRefresh('background');
  };

  // Broker config handler
  const handleConfigOpen = (cluster: ClusterInfo) => {
    const cfg: ClusterConfig = cluster.config ?? ({} as ClusterConfig);
    setSelectedCluster(cluster);
    setConfigPreview(null);
    setConfigPreviewLoading(false);
    setConfigSubmitting(false);
    configForm.setFieldsValue({
      flushDiskType: cfg.flushDiskType ?? 'ASYNC_FLUSH',
      autoCreateTopicEnable: cfg.autoCreateTopicEnable ?? false,
      autoCreateSubscriptionGroup: cfg.autoCreateSubscriptionGroup ?? false,
      maxMessageSizeMB: Math.round((cfg.maxMessageSize ?? 4194304) / 1048576),
      fileReservedTime: cfg.fileReservedTime ?? 72,
      writeQueueNums: cfg.writeQueueNums ?? 8,
      readQueueNums: cfg.readQueueNums ?? 8,
      brokerPermission: cfg.brokerPermission ?? 6,
    });
    setConfigModalOpen(true);
  };

  const buildConfigUpdateRequest = (
    values: ClusterConfigFormValues,
  ): ClusterConfigRequest | null => {
    if (!selectedCluster) return null;
    const { maxMessageSizeMB, ...configValues } = values;
    return {
      id: selectedCluster.id,
      instanceId: selectedInstanceIdRef.current,
      ...(selectedCluster.config ?? {}),
      ...configValues,
      maxMessageSize: maxMessageSizeMB * 1048576,
    };
  };

  const handleConfigPreview = async () => {
    let values: ClusterConfigFormValues;
    try {
      values = await configForm.validateFields();
    } catch {
      return;
    }
    const request = buildConfigUpdateRequest(values);
    if (!request) return;

    setConfigPreviewLoading(true);
    try {
      const preview = await previewClusterConfig(request);
      setConfigPreview(preview);
      message.success(t('cluster.configPreviewGenerated'));
    } catch {
      setConfigPreview(null);
      message.error(t('cluster.configPreviewFailed'));
    } finally {
      setConfigPreviewLoading(false);
    }
  };

  const handleConfigSubmit = async () => {
    let values: ClusterConfigFormValues;
    try {
      values = await configForm.validateFields();
    } catch {
      return;
    }
    const request = buildConfigUpdateRequest(values);
    if (!request) return;

    setConfigSubmitting(true);
    try {
      const result = await updateClusterConfig(request);
      if (result.status === 'SUCCESS') {
        await requestRefresh('operation');
        message.success(t('cluster.configUpdated'));
        setConfigModalOpen(false);
        setConfigPreview(null);
        return;
      }

      const failedAddresses = result.failedBrokers.map((failure) => failure.address).join(', ');
      if (result.status === 'PARTIAL') {
        await requestRefresh('operation');
        message.warning(t('cluster.configPartiallyUpdated', { brokers: failedAddresses }));
        return;
      }
      message.error(t('cluster.configUpdateFailed', { brokers: failedAddresses }));
    } catch {
      message.error(t('cluster.configUpdateFailed', { brokers: '' }));
    } finally {
      setConfigSubmitting(false);
    }
  };

  const configFieldLabel = (field: string) => {
    const key = CONFIG_FIELD_LABEL_KEYS[field];
    return key ? t(key) : field;
  };

  const previewValue = (value: string | null) => value ?? '-';

  const renderConfigPreview = () => {
    if (!configPreview) return null;
    const propertyEntries = Object.entries(configPreview.brokerProperties ?? {});
    const previewColumns: ColumnsType<ClusterConfigPreviewChange> = [
      {
        title: t('cluster.configPreviewField'),
        dataIndex: 'field',
        key: 'field',
        render: (field: string) => configFieldLabel(field),
      },
      {
        title: t('cluster.configPreviewCurrent'),
        dataIndex: 'currentValue',
        key: 'currentValue',
        render: previewValue,
      },
      {
        title: t('cluster.configPreviewProposed'),
        dataIndex: 'proposedValue',
        key: 'proposedValue',
        render: previewValue,
      },
      {
        title: t('cluster.configPreviewProperty'),
        dataIndex: 'brokerProperty',
        key: 'brokerProperty',
      },
    ];

    return (
      <Card size="small" title={t('cluster.configPreview')} style={{ marginTop: 16 }}>
        <Descriptions size="small" column={1}>
          <Descriptions.Item label={t('cluster.configPreviewTargets')}>
            <Space size={[0, 4]} wrap>
              {configPreview.targetBrokers.length > 0 ? (
                configPreview.targetBrokers.map((broker) => (
                  <Tag key={broker.address}>{broker.address}</Tag>
                ))
              ) : (
                <Text type="secondary">-</Text>
              )}
            </Space>
          </Descriptions.Item>
          <Descriptions.Item label={t('cluster.configPreviewBrokerProperties')}>
            <Space size={[0, 4]} wrap>
              {propertyEntries.length > 0 ? (
                propertyEntries.map(([key, value]) => <Tag key={key}>{`${key}=${value}`}</Tag>)
              ) : (
                <Text type="secondary">-</Text>
              )}
            </Space>
          </Descriptions.Item>
        </Descriptions>
        <Table<ClusterConfigPreviewChange>
          columns={previewColumns}
          dataSource={configPreview.changes}
          rowKey="field"
          pagination={false}
          size="small"
          locale={{ emptyText: t('cluster.configPreviewNoChanges') }}
          style={{ marginTop: 12 }}
        />
      </Card>
    );
  };

  const tabItems = [
    {
      key: 'nameserver',
      label: (
        <Flex align="center" gap={4}>
          <Cpu size={16} />
          <span>{t('cluster.nameserver')}</span>
        </Flex>
      ),
      children: renderNameServerTab(),
    },
    {
      key: 'broker',
      label: (
        <Flex align="center" gap={4}>
          <HardDrives size={16} />
          <span>{t('cluster.broker')}</span>
        </Flex>
      ),
      children: renderBrokerTab(),
    },
    {
      key: 'proxy',
      label: (
        <Flex align="center" gap={4}>
          <Globe size={16} />
          <span>{t('cluster.proxy')}</span>
        </Flex>
      ),
      children: renderProxyTab(),
    },
  ];

  // ─── Tab 2: Broker 管理 (flat table) ────────────────────────────────────────

  function renderNameServerConfigDiffModal() {
    const { cluster, loading: diffLoading, open, result } = nsConfigDiffState;
    const titleName = cluster?.nsClusterName ?? cluster?.name ?? result?.cluster ?? '-';
    const nodeColumns: ColumnsType<NameServerConfigDiffNode> = [
      {
        title: t('common.address'),
        dataIndex: 'address',
        key: 'address',
        render: (address: string) => <Text copyable>{address}</Text>,
      },
      {
        title: t('common.status'),
        dataIndex: 'reachable',
        key: 'reachable',
        width: 120,
        render: (reachable: boolean) => (
          <Tag color={reachable ? 'green' : 'red'}>
            {reachable ? t('cluster.nsConfigDiffReachable') : t('cluster.nsConfigDiffUnreachable')}
          </Tag>
        ),
      },
    ];
    const differenceColumns: ColumnsType<NameServerConfigDifference> = [
      {
        title: t('cluster.configPreviewField'),
        dataIndex: 'key',
        key: 'key',
        width: 220,
        render: (key: string) => <Text strong>{key}</Text>,
      },
      {
        title: t('cluster.nsConfigDiffValues'),
        dataIndex: 'values',
        key: 'values',
        render: (values: NameServerConfigDifference['values']) => (
          <Space size={[0, 4]} wrap>
            {values.map((value) => (
              <Tag key={value.address} color={value.configured ? 'blue' : 'default'}>
                {`${value.address}: ${
                  value.configured ? (value.value ?? '-') : t('cluster.nsConfigDiffUnconfigured')
                }`}
              </Tag>
            ))}
          </Space>
        ),
      },
    ];

    return (
      <Modal
        title={t('cluster.nsConfigDiffTitle', { name: titleName })}
        open={open}
        onCancel={closeNameServerConfigDiff}
        footer={<Button onClick={closeNameServerConfigDiff}>{t('common.close')}</Button>}
        width={920}
        destroyOnHidden
      >
        <Spin spinning={diffLoading}>
          {result ? (
            <>
              <Alert
                showIcon
                type={result.driftDetected ? 'warning' : 'success'}
                message={
                  result.driftDetected
                    ? t('cluster.nsConfigDiffDriftDetected')
                    : t('cluster.nsConfigDiffNoDrift')
                }
                style={{ marginBottom: 16 }}
              />
              <Descriptions size="small" column={2} style={{ marginBottom: 16 }}>
                <Descriptions.Item label={t('cluster.configPreviewTargets')}>
                  {`${result.reachableNodeCount}/${result.nodeCount}`}
                </Descriptions.Item>
                <Descriptions.Item label={t('cluster.nsConfigDiffComplete')}>
                  {result.complete ? t('common.yes') : t('common.no')}
                </Descriptions.Item>
                <Descriptions.Item label={t('cluster.nsConfigDiffComparedKeys')} span={2}>
                  <Space size={[0, 4]} wrap>
                    {result.comparedKeys.map((key) => (
                      <Tag key={key}>{key}</Tag>
                    ))}
                  </Space>
                </Descriptions.Item>
              </Descriptions>
              <Table<NameServerConfigDiffNode>
                columns={nodeColumns}
                dataSource={result.nodes}
                rowKey="address"
                pagination={false}
                size="small"
                style={{ marginBottom: 16 }}
              />
              <Table<NameServerConfigDifference>
                columns={differenceColumns}
                dataSource={result.differences}
                rowKey="key"
                pagination={false}
                size="small"
                locale={{ emptyText: t('cluster.configPreviewNoChanges') }}
              />
            </>
          ) : (
            <Alert showIcon type="info" message={t('cluster.nsConfigDiffLoading')} />
          )}
        </Spin>
      </Modal>
    );
  }

  function renderBrokerConfigDiffModal() {
    const { cluster, loading: diffLoading, open, result } = brokerConfigDiffState;
    const titleName = cluster?.nsClusterName ?? cluster?.name ?? result?.cluster ?? '-';
    const brokerColumns: ColumnsType<BrokerConfigDiffBroker> = [
      {
        title: t('cluster.brokerName'),
        dataIndex: 'name',
        key: 'name',
        width: 180,
        render: (name: string) => <Text strong>{name}</Text>,
      },
      {
        title: t('common.address'),
        dataIndex: 'address',
        key: 'address',
        render: (address: string) => <Text copyable>{address}</Text>,
      },
      {
        title: t('common.status'),
        dataIndex: 'reachable',
        key: 'reachable',
        width: 120,
        render: (reachable: boolean) => (
          <Tag color={reachable ? 'green' : 'red'}>
            {reachable
              ? t('cluster.brokerConfigDiffReachable')
              : t('cluster.brokerConfigDiffUnreachable')}
          </Tag>
        ),
      },
      {
        title: t('common.message'),
        dataIndex: 'message',
        key: 'message',
        ellipsis: true,
        render: (value?: string | null) => value || <Text type="secondary">-</Text>,
      },
    ];
    const differenceColumns: ColumnsType<BrokerConfigDifference> = [
      {
        title: t('cluster.configPreviewField'),
        dataIndex: 'field',
        key: 'field',
        width: 180,
        render: (field: string) => <Text strong>{configFieldLabel(field)}</Text>,
      },
      {
        title: t('cluster.configPreviewProperty'),
        dataIndex: 'brokerProperty',
        key: 'brokerProperty',
        width: 190,
        render: (value: string) => <Text code>{value}</Text>,
      },
      {
        title: t('cluster.brokerConfigDiffValues'),
        dataIndex: 'values',
        key: 'values',
        render: (values: BrokerConfigDifference['values']) => (
          <Space size={[0, 4]} wrap>
            {values.map((value) => (
              <Tag
                key={`${value.address}-${value.value ?? 'missing'}`}
                color={value.configured ? 'blue' : 'default'}
              >
                {`${value.brokerName || value.address}: ${
                  value.configured
                    ? (value.value ?? '-')
                    : t('cluster.brokerConfigDiffUnconfigured')
                }`}
              </Tag>
            ))}
          </Space>
        ),
      },
    ];

    return (
      <Modal
        title={t('cluster.brokerConfigDiffTitle', { name: titleName })}
        open={open}
        onCancel={() =>
          setBrokerConfigDiffState({ open: false, loading: false, cluster: null, result: null })
        }
        footer={
          <Button
            onClick={() =>
              setBrokerConfigDiffState({ open: false, loading: false, cluster: null, result: null })
            }
          >
            {t('common.close')}
          </Button>
        }
        width={980}
        destroyOnHidden
      >
        <Spin spinning={diffLoading}>
          {result ? (
            <>
              <Alert
                showIcon
                type={result.driftDetected ? 'warning' : 'success'}
                message={
                  result.driftDetected
                    ? t('cluster.brokerConfigDiffDriftDetected')
                    : t('cluster.brokerConfigDiffNoDrift')
                }
                style={{ marginBottom: 16 }}
              />
              <Descriptions size="small" column={2} style={{ marginBottom: 16 }}>
                <Descriptions.Item label={t('cluster.configPreviewTargets')}>
                  {`${result.reachableBrokerCount}/${result.brokerCount}`}
                </Descriptions.Item>
                <Descriptions.Item label={t('cluster.brokerConfigDiffComplete')}>
                  {result.complete ? t('common.yes') : t('common.no')}
                </Descriptions.Item>
                <Descriptions.Item label={t('cluster.brokerConfigDiffComparedFields')} span={2}>
                  <Space size={[0, 4]} wrap>
                    {result.comparedFields.map((field) => (
                      <Tag key={field}>{configFieldLabel(field)}</Tag>
                    ))}
                  </Space>
                </Descriptions.Item>
              </Descriptions>
              <Table<BrokerConfigDiffBroker>
                columns={brokerColumns}
                dataSource={result.brokers}
                rowKey="address"
                pagination={false}
                size="small"
                style={{ marginBottom: 16 }}
              />
              <Table<BrokerConfigDifference>
                columns={differenceColumns}
                dataSource={result.differences}
                rowKey="field"
                pagination={false}
                size="small"
                locale={{ emptyText: t('cluster.configPreviewNoChanges') }}
              />
            </>
          ) : (
            <Alert showIcon type="info" message={t('cluster.brokerConfigDiffLoading')} />
          )}
        </Spin>
      </Modal>
    );
  }

  function renderBrokerTab() {
    type BrokerWithCluster = BrokerInfo & {
      clusterName: string;
      nsClusterName: string;
      cluster: ClusterInfo;
    };
    const brokerSearchText = searchText(brokerSearch);

    const allBrokers: BrokerWithCluster[] = registryClusters.flatMap((c) =>
      (c.brokers ?? [])
        .filter((b) => {
          const matchSearch =
            !brokerSearchText ||
            searchText(c.nsClusterName).includes(brokerSearchText) ||
            searchText(b.name).includes(brokerSearchText) ||
            searchText(b.addr).includes(brokerSearchText);
          return matchSearch;
        })
        .map((b) => ({
          ...b,
          clusterName: c.name,
          nsClusterName: c.nsClusterName,
          cluster: c,
        })),
    );

    const brokerColumns: ColumnsType<BrokerWithCluster> = [
      {
        title: t('cluster.brokerClusterName'),
        dataIndex: 'nsClusterName',
        key: 'nsClusterName',
        width: 160,
        sorter: (a, b) => a.nsClusterName.localeCompare(b.nsClusterName),
        render: (name: string) => (
          <Text strong style={{ fontSize: 14 }}>
            {name}
          </Text>
        ),
      },
      {
        title: t('cluster.brokerName'),
        dataIndex: 'name',
        key: 'name',
        width: 170,
        sorter: (a, b) => a.name.localeCompare(b.name),
        render: (name: string) => (
          <Text strong style={{ fontSize: 14 }}>
            {name}
          </Text>
        ),
      },
      {
        title: t('common.status'),
        dataIndex: 'status',
        key: 'status',
        width: 90,
        align: 'center',
        sorter: (a, b) => a.status.localeCompare(b.status),
        render: (status: string) => {
          const map: Record<string, { color: string; label: string }> = {
            running: { color: 'green', label: t('cluster.running') },
            readonly: { color: 'gold', label: t('cluster.readonly') },
            maintenance: { color: 'red', label: t('cluster.maintenance') },
          };
          const cfg = map[status] ?? { color: 'default', label: status };
          return <Tag color={cfg.color}>{cfg.label}</Tag>;
        },
      },
      {
        title: t('common.version'),
        dataIndex: 'version',
        key: 'version',
        width: 80,
        align: 'center',
        sorter: (a, b) => (a.version || '').localeCompare(b.version || ''),
        render: (v?: string | null) => <span style={{ fontSize: 14 }}>{v || '-'}</span>,
      },
      {
        title: t('cluster.diskUsage'),
        dataIndex: 'diskUsage',
        key: 'diskUsage',
        width: 150,
        sorter: (a, b) => a.diskUsage - b.diskUsage,
        render: (v: number) => (
          <Progress
            percent={v}
            size="small"
            strokeColor={v > 85 ? '#ff4d4f' : v > 70 ? '#faad14' : '#1677ff'}
          />
        ),
      },
      {
        title: t('common.address'),
        dataIndex: 'addr',
        key: 'addr',
        width: 170,
        align: 'right',
        sorter: (a, b) => compareText(a.addr, b.addr),
        render: (addr: string | null) => <span style={{ fontSize: 14 }}>{safeText(addr)}</span>,
      },
      {
        title: 'TPS In',
        dataIndex: 'tpsIn',
        key: 'tpsIn',
        width: 90,
        align: 'right',
        sorter: (a, b) => a.tpsIn - b.tpsIn,
        render: (v: number) => v.toLocaleString(),
      },
      {
        title: 'TPS Out',
        dataIndex: 'tpsOut',
        key: 'tpsOut',
        width: 90,
        align: 'right',
        sorter: (a, b) => a.tpsOut - b.tpsOut,
        render: (v: number) => v.toLocaleString(),
      },
      {
        title: t('common.actions'),
        key: 'action',
        width: 260,
        render: (_: unknown, record: BrokerWithCluster) => (
          <Flex gap={6}>
            <Button
              size="small"
              icon={<EyeOutlined />}
              aria-label={t('cluster.brokerConfigDiff')}
              loading={
                brokerConfigDiffState.loading &&
                brokerConfigDiffState.cluster?.id === record.cluster.id
              }
              onClick={() => void openBrokerConfigDiff(record.cluster)}
            >
              {t('cluster.brokerConfigDiff')}
            </Button>
            <Button
              size="small"
              icon={<SettingOutlined />}
              aria-label={t('cluster.config')}
              style={{ borderColor: '#1677ff', color: '#1677ff' }}
              onClick={() => handleConfigOpen(record.cluster)}
            >
              {t('cluster.config')}
            </Button>
            <Button
              size="small"
              icon={<ReloadOutlined />}
              aria-label={t('cluster.restart')}
              danger
              style={{ borderColor: '#ff4d4f', color: '#ff4d4f' }}
              onClick={() => message.warning(t('cluster.restartNotSupported'))}
            >
              {t('cluster.restart')}
            </Button>
          </Flex>
        ),
      },
    ];

    return (
      <div>
        <Flex justify="space-between" style={{ marginBottom: 16 }}>
          <Space>
            <Input.Search
              placeholder={t('cluster.searchBroker')}
              allowClear
              onSearch={setBrokerSearch}
              onChange={(e) => !e.target.value && setBrokerSearch('')}
              style={{ width: 240 }}
            />
          </Space>
          <Button type="primary" icon={<PlusOutlined />} onClick={openConnectModal}>
            {t('cluster.createCluster')}
          </Button>
        </Flex>
        <Card styles={{ body: { padding: 0 } }}>
          <Table
            columns={brokerColumns}
            dataSource={allBrokers}
            loading={registryLoading}
            rowKey="addr"
            pagination={{ pageSize: 20 }}
            size="small"
            scroll={{ x: tableScrollX(brokerColumns) }}
          />
        </Card>

        {selectedCluster && (
          <Modal
            title={t('cluster.configTitle', { name: selectedCluster.name })}
            open={configModalOpen}
            onCancel={() => {
              setConfigModalOpen(false);
              setConfigPreview(null);
            }}
            onOk={() => void handleConfigSubmit()}
            confirmLoading={configSubmitting}
            width={720}
          >
            <Space style={{ marginBottom: 16 }}>
              <Button
                icon={<EyeOutlined />}
                loading={configPreviewLoading}
                onClick={() => void handleConfigPreview()}
              >
                {t('cluster.configPreview')}
              </Button>
            </Space>
            <Form form={configForm} layout="vertical" onValuesChange={() => setConfigPreview(null)}>
              <Form.Item label={t('cluster.flushDiskType')} name="flushDiskType">
                <Radio.Group>
                  <Radio value="SYNC_FLUSH">{t('cluster.syncFlush')}</Radio>
                  <Radio value="ASYNC_FLUSH">{t('cluster.asyncFlush')}</Radio>
                </Radio.Group>
              </Form.Item>
              <Form.Item
                label={t('cluster.autoCreateTopic')}
                name="autoCreateTopicEnable"
                valuePropName="checked"
              >
                <Switch />
              </Form.Item>
              <Form.Item
                label={t('cluster.autoCreateSubGroup')}
                name="autoCreateSubscriptionGroup"
                valuePropName="checked"
              >
                <Switch />
              </Form.Item>
              <Form.Item label={t('cluster.maxMessageSize')} name="maxMessageSizeMB">
                <InputNumber min={1} max={128} style={{ width: '100%' }} />
              </Form.Item>
              <Form.Item label={t('cluster.fileReservedTime')} name="fileReservedTime">
                <InputNumber min={1} max={720} style={{ width: '100%' }} />
              </Form.Item>
              <Form.Item label={t('cluster.writeQueues')} name="writeQueueNums">
                <InputNumber min={1} max={256} style={{ width: '100%' }} />
              </Form.Item>
              <Form.Item label={t('cluster.readQueues')} name="readQueueNums">
                <InputNumber min={1} max={256} style={{ width: '100%' }} />
              </Form.Item>
              <Text type="secondary" style={{ display: 'block', marginTop: -16, marginBottom: 16 }}>
                RocketMQ Broker uses one default Topic queue count; read and write values must
                match.
              </Text>
              <Form.Item label={t('cluster.brokerPermission')} name="brokerPermission">
                <InputNumber min={0} max={7} style={{ width: '100%' }} />
              </Form.Item>
            </Form>
            {renderConfigPreview()}
          </Modal>
        )}
      </div>
    );
  }

  // ─── Tab 2: NameServer 管理 (rmq_nameserver 注册表) ────────────────────────

  function renderNameServerTab() {
    const nsSearchText = searchText(nsSearch);
    const filteredRegistry = nsRegistry.filter((entry) => {
      if (!nsSearchText) return true;
      return (
        searchText(entry.name).includes(nsSearchText) ||
        searchText(entry.namesrvAddr).includes(nsSearchText) ||
        searchText(entry.k8sNamespace).includes(nsSearchText) ||
        searchText(entry.k8sId).includes(nsSearchText)
      );
    });

    const registryColumns: ColumnsType<NameserverRegistryEntry> = [
      {
        title: t('common.name'),
        dataIndex: 'name',
        key: 'name',
        width: 180,
        sorter: (a, b) => compareText(a.name, b.name),
        render: (name: string | null) => (
          <Text strong style={{ fontSize: 14 }}>
            {safeText(name)}
          </Text>
        ),
      },
      {
        title: t('cluster.nsAddr'),
        dataIndex: 'namesrvAddr',
        key: 'namesrvAddr',
        sorter: (a, b) => compareText(a.namesrvAddr, b.namesrvAddr),
        render: (addr: string | null) => <Text style={{ fontSize: 14 }}>{safeText(addr)}</Text>,
      },
      {
        title: t('cluster.k8sId'),
        dataIndex: 'k8sId',
        key: 'k8sId',
        width: 180,
        sorter: (a, b) => compareText(a.k8sId, b.k8sId),
        render: (k8sId: string | null) =>
          k8sId ? (
            <Text style={{ fontFamily: 'monospace', fontSize: 14 }}>{k8sId}</Text>
          ) : (
            <Text type="secondary">-</Text>
          ),
      },
      {
        title: t('cluster.k8sNamespace'),
        dataIndex: 'k8sNamespace',
        key: 'k8sNamespace',
        width: 180,
        sorter: (a, b) => compareText(a.k8sNamespace, b.k8sNamespace),
        render: (ns: string | null) =>
          ns ? <Text style={{ fontSize: 14 }}>{ns}</Text> : <Text type="secondary">-</Text>,
      },
      {
        title: t('common.status'),
        dataIndex: 'status',
        key: 'status',
        width: 90,
        sorter: (a, b) => safeText(a.status).localeCompare(safeText(b.status)),
        render: (status: string | null) => {
          const map: Record<string, { color: string; label: string }> = {
            healthy: { color: 'green', label: t('cluster.running') },
            warning: { color: 'gold', label: t('cluster.warning') },
            error: { color: 'red', label: t('cluster.error') },
            offline: { color: 'default', label: t('cluster.offline') },
          };
          const cfg = map[status ?? ''] ?? { color: 'default', label: safeText(status) };
          return <Tag color={cfg.color}>{cfg.label}</Tag>;
        },
      },
      {
        title: t('cluster.nsDescription'),
        dataIndex: 'description',
        key: 'description',
        ellipsis: true,
        render: (desc: string | null) => safeText(desc) || <Text type="secondary">-</Text>,
      },
      {
        title: t('common.actions'),
        key: 'action',
        width: 260,
        render: (_: unknown, record: NameserverRegistryEntry) => {
          const matchedCluster = resolveNameserverRegistryCluster(record);
          return (
            <Flex gap={6}>
              <Button
                size="small"
                icon={<EyeOutlined />}
                disabled={!matchedCluster}
                loading={
                  nsConfigDiffState.loading && nsConfigDiffState.cluster?.id === matchedCluster?.id
                }
                onClick={() => {
                  if (matchedCluster) void openNameServerConfigDiff(matchedCluster);
                }}
              >
                {t('cluster.nsConfigDiff')}
              </Button>
              <Button
                size="small"
                icon={<EditOutlined />}
                style={{ borderColor: '#722ed1', color: '#722ed1' }}
                onClick={() => openNsEditModal(record)}
              >
                {t('common.edit')}
              </Button>
              <Button
                size="small"
                danger
                icon={<DeleteOutlined />}
                onClick={() => handleNsDelete(record)}
              >
                {t('common.delete')}
              </Button>
            </Flex>
          );
        },
      },
    ];

    return (
      <div>
        <Flex justify="space-between" style={{ marginBottom: 16 }}>
          <Space>
            <Input.Search
              placeholder={t('cluster.searchNs')}
              allowClear
              onSearch={setNsSearch}
              onChange={(e) => !e.target.value && setNsSearch('')}
              style={{ width: 240 }}
            />
          </Space>
          <Space>
            <Button icon={<CloudSyncOutlined />} onClick={openKubernetesDiscovery}>
              {t('cluster.k8sDiscoverAction')}
            </Button>
            <Button type="primary" icon={<PlusOutlined />} onClick={openNsCreateModal}>
              {t('cluster.createNameServer')}
            </Button>
          </Space>
        </Flex>
        <Card styles={{ body: { padding: 0 } }}>
          <Table
            columns={registryColumns}
            dataSource={filteredRegistry}
            rowKey="id"
            pagination={{ pageSize: 20 }}
            size="small"
            scroll={{ x: tableScrollX(registryColumns) }}
          />
        </Card>
      </div>
    );
  }

  // ─── Tab 3: Proxy 管理 (flat table) ────────────────────────────────────────

  function renderProxyTab() {
    type ProxyRow = ProxyDetail;
    const proxySearchText = searchText(proxySearch);

    const allProxies: ProxyRow[] = registryClusters
      .filter((c) => (c.proxies?.length ?? 0) > 0)
      .flatMap((c) =>
        (c.proxies ?? [])
          .filter((p) => {
            const matchSearch = !proxySearchText || searchText(p.addr).includes(proxySearchText);
            return matchSearch;
          })
          .map((p) => ({
            ...p,
            clusterId: c.id,
            clusterName: c.name,
            nsClusterName: c.nsClusterName,
          })),
      );

    const proxyColumns: ColumnsType<ProxyRow> = [
      {
        title: t('cluster.brokerClusterName'),
        dataIndex: 'nsClusterName',
        key: 'nsClusterName',
        width: 160,
        sorter: (a, b) => a.nsClusterName.localeCompare(b.nsClusterName),
        render: (name: string) => (
          <Text strong style={{ fontSize: 14 }}>
            {name}
          </Text>
        ),
      },
      {
        title: t('cluster.proxyAddr'),
        dataIndex: 'addr',
        key: 'addr',
        width: 200,
        sorter: (a, b) => compareText(a.addr, b.addr),
        render: (addr: string | null) => <Text style={{ fontSize: 14 }}>{safeText(addr)}</Text>,
      },
      {
        title: t('common.status'),
        dataIndex: 'status',
        key: 'status',
        width: 90,
        sorter: (a, b) => a.status.localeCompare(b.status),
        render: (status: string) => {
          const map: Record<string, { color: string; label: string }> = {
            healthy: { color: 'green', label: t('cluster.running') },
            warning: { color: 'gold', label: t('cluster.warning') },
            error: { color: 'red', label: t('cluster.error') },
            offline: { color: 'default', label: t('cluster.offline') },
          };
          const cfg = map[status] ?? { color: 'default', label: status };
          return <Tag color={cfg.color}>{cfg.label}</Tag>;
        },
      },
      {
        title: t('cluster.connections'),
        dataIndex: 'connections',
        key: 'connections',
        width: 100,
        align: 'right',
        sorter: (a, b) => a.connections - b.connections,
        render: (v: number) => v.toLocaleString(),
      },
      {
        title: t('cluster.remotingPort'),
        dataIndex: 'remotingPort',
        key: 'remotingPort',
        width: 120,
        align: 'center',
        sorter: (a, b) => a.remotingPort - b.remotingPort,
      },
      {
        title: t('cluster.grpcPort'),
        dataIndex: 'grpcPort',
        key: 'grpcPort',
        width: 100,
        align: 'center',
        sorter: (a, b) => a.grpcPort - b.grpcPort,
      },
      {
        title: t('common.actions'),
        key: 'action',
        width: 160,
        render: (_: unknown, record: ProxyRow) => (
          <Flex gap={6}>
            <Button
              size="small"
              icon={<EyeOutlined />}
              style={{ borderColor: '#1677ff', color: '#1677ff' }}
              onClick={() => setSelectedProxy(record)}
            >
              {t('common.detail')}
            </Button>
            <Button
              size="small"
              icon={<ReloadOutlined />}
              style={{ borderColor: '#faad14', color: '#faad14' }}
              onClick={() => {
                Modal.confirm({
                  title: t('cluster.confirmRestart'),
                  content: t('cluster.restartProxyConfirm', { addr: record.addr }),
                  okText: t('common.confirm'),
                  cancelText: t('common.cancel'),
                  onOk: async () => {
                    await restartProxy({ clusterId: record.clusterId, addr: record.addr });
                    await requestRefresh('operation');
                    message.success(t('cluster.restartProxySubmitted', { addr: record.addr }));
                  },
                });
              }}
            >
              {t('cluster.restart')}
            </Button>
          </Flex>
        ),
      },
    ];

    return (
      <div>
        <Flex justify="space-between" style={{ marginBottom: 16 }}>
          <Space>
            <Input.Search
              placeholder={t('cluster.searchProxy')}
              allowClear
              onSearch={setProxySearch}
              onChange={(e) => !e.target.value && setProxySearch('')}
              style={{ width: 240 }}
            />
          </Space>
          <Button type="primary" icon={<PlusOutlined />} onClick={openConnectModal}>
            {t('cluster.createCluster')}
          </Button>
        </Flex>
        <Card styles={{ body: { padding: 0 } }}>
          <Table
            columns={proxyColumns}
            dataSource={allProxies}
            loading={registryLoading}
            rowKey={(r) => `${r.clusterName}-${r.addr}`}
            pagination={{ pageSize: 20 }}
            size="small"
            scroll={{ x: tableScrollX(proxyColumns) }}
          />
        </Card>
      </div>
    );
  }

  // ─── Tab 4: K8s 证书管理 ───────────────────────────────────────────────────

  // ─── Render ─────────────────────────────────────────────────────────────────

  const {
    brokers: totalBrokers,
    nameServers: totalNameServers,
    proxies: totalProxies,
  } = countClusterComponents(clusters);

  return (
    <div style={{ padding: 24 }}>
      <PageHeader
        title={t('cluster.title')}
        subtitle={`${t('common.total')} ${clusters.length} ${t('cluster.title')} · ${totalBrokers} Broker · ${totalNameServers} NameServer · ${totalProxies} Proxy`}
        extra={
          <Flex align="center" gap={8}>
            <Button
              size="small"
              icon={<ReloadOutlined spin={loading} />}
              aria-label={t('common.refresh')}
              onClick={() => {
                void requestRefresh('manual');
                void loadNsRegistry();
                void loadRegistryClusters();
              }}
            >
              {t('common.refresh')}
            </Button>
            <Flex align="center" gap={6}>
              {(autoRefresh || refreshFailed) && (
                <span
                  title={
                    refreshFailed
                      ? t('common.refreshFailed')
                      : autoRefresh
                        ? t('common.liveRefresh')
                        : t('common.autoRefresh')
                  }
                  style={{
                    width: 6,
                    height: 6,
                    borderRadius: '50%',
                    background: refreshFailed ? '#ff4d4f' : '#52c41a',
                    display: 'inline-block',
                    animation: refreshFailed ? undefined : 'livePulse 1.5s ease-in-out infinite',
                  }}
                />
              )}
              <Text type={refreshFailed ? 'danger' : 'secondary'} style={{ fontSize: 14 }}>
                {refreshFailed
                  ? t('common.refreshFailed')
                  : autoRefresh
                    ? t('common.liveRefresh')
                    : t('common.autoRefresh')}
              </Text>
              <Switch
                size="small"
                checked={autoRefresh}
                aria-label={t('common.autoRefresh')}
                onChange={handleAutoRefreshChange}
              />
            </Flex>
          </Flex>
        }
      />
      {instanceLoadError && (
        <Alert
          type="error"
          showIcon
          message={instanceLoadError}
          action={
            <Button size="small" onClick={() => setInstanceLoadKey((key) => key + 1)}>
              {t('common.retry')}
            </Button>
          }
          style={{ marginBottom: 16 }}
        />
      )}
      <style>{`
        @keyframes livePulse {
          0%, 100% { opacity: 1; box-shadow: 0 0 0 0 rgba(82, 196, 26, 0.4); }
          50% { opacity: 0.6; box-shadow: 0 0 0 4px rgba(82, 196, 26, 0); }
        }
      `}</style>
      {/* ─── NameServer 注册表新建/编辑弹窗 ─── */}
      <Modal
        title={t('cluster.k8sDiscoveryTitle')}
        open={kubernetesDiscoveryOpen}
        onCancel={closeKubernetesDiscovery}
        footer={null}
        width={920}
        destroyOnHidden
      >
        <Alert
          type="info"
          showIcon
          message={t('cluster.k8sDiscoveryReadOnly')}
          style={{ marginBottom: 16 }}
        />
        <Form form={kubernetesDiscoveryForm} layout="inline" style={{ marginBottom: 16 }}>
          <Form.Item
            name="namespace"
            label={t('cluster.k8sNamespace')}
            rules={[{ required: true, message: t('cluster.k8sNamespaceRequired') }]}
          >
            <Input placeholder="rocketmq" style={{ width: 260 }} />
          </Form.Item>
          <Form.Item>
            <Button
              type="primary"
              icon={<CloudSyncOutlined />}
              loading={kubernetesDiscoveryLoading}
              onClick={() => void handleKubernetesDiscovery()}
            >
              {t('cluster.k8sDiscoverAction')}
            </Button>
          </Form.Item>
        </Form>
        <Table<KubernetesNameServerCandidate>
          rowKey={(candidate) => `${candidate.source}:${candidate.namesrvAddr}`}
          dataSource={kubernetesDiscoveryCandidates}
          loading={kubernetesDiscoveryLoading}
          pagination={false}
          size="small"
          locale={{
            emptyText: kubernetesDiscoverySearched
              ? t('cluster.k8sDiscoveryEmpty')
              : t('cluster.k8sDiscoveryPending'),
          }}
          columns={[
            {
              title: t('common.name'),
              dataIndex: 'resourceName',
              width: 180,
              ellipsis: true,
            },
            {
              title: t('cluster.nsAddr'),
              dataIndex: 'namesrvAddr',
              ellipsis: true,
            },
            {
              title: t('cluster.k8sDiscoverySource'),
              dataIndex: 'source',
              width: 150,
              render: (source: KubernetesNameServerCandidate['source']) => (
                <Tag>{t(`cluster.k8sDiscoverySource.${source}`)}</Tag>
              ),
            },
            {
              title: t('cluster.k8sDiscoveryConfidence'),
              dataIndex: 'confidence',
              width: 100,
              render: (confidence: KubernetesNameServerCandidate['confidence']) => (
                <Tag
                  color={
                    confidence === 'HIGH' ? 'green' : confidence === 'MEDIUM' ? 'gold' : 'default'
                  }
                >
                  {t(`cluster.k8sDiscoveryConfidence.${confidence}`)}
                </Tag>
              ),
            },
            {
              title: t('cluster.k8sDiscoveryStability'),
              dataIndex: 'stable',
              width: 100,
              render: (stable: boolean) =>
                stable ? t('cluster.k8sDiscoveryStable') : t('cluster.k8sDiscoveryUnstable'),
            },
            {
              title: t('common.actions'),
              key: 'action',
              width: 80,
              render: (_: unknown, candidate: KubernetesNameServerCandidate) => (
                <Button
                  type="link"
                  size="small"
                  onClick={() => selectKubernetesCandidate(candidate)}
                >
                  {t('cluster.k8sDiscoveryUse')}
                </Button>
              ),
            },
          ]}
        />
      </Modal>
      <Modal
        title={
          nsModalMode === 'create' ? t('cluster.createNameServer') : t('cluster.editNameServer')
        }
        open={nsCreateModalOpen}
        onCancel={() => setNsCreateModalOpen(false)}
        onOk={() => void handleNsSubmit()}
        okText={t('common.confirm')}
        cancelText={t('common.cancel')}
        destroyOnHidden
      >
        <Form form={nsCreateForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item
            name="name"
            label={t('common.name')}
            rules={[{ required: true, message: t('common.name') }]}
          >
            <Input placeholder="rocketmq1" />
          </Form.Item>
          <Form.Item
            name="namesrvAddr"
            label={t('cluster.nsAddr')}
            rules={[{ required: true, message: t('cluster.nsAddr') }]}
          >
            <Input placeholder={t('cluster.nsAddrPlaceholder')} />
          </Form.Item>
          <Form.Item name="k8sId" label={t('cluster.k8sId')} extra={t('cluster.k8sIdExtra')}>
            <Select
              allowClear
              placeholder={t('cluster.k8sIdPlaceholder')}
              options={k8sIdOptions.map((k8sId) => ({ value: k8sId, label: k8sId }))}
            />
          </Form.Item>
          <Form.Item name="k8sNamespace" label={t('cluster.k8sNamespace')}>
            <Input placeholder="rocketmq1" />
          </Form.Item>
          <Form.Item name="description" label={t('cluster.nsDescription')}>
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>
      <Tabs items={tabItems} defaultActiveKey="broker" />
      <Modal
        title={t('cluster.proxyDetailTitle', { addr: selectedProxy?.addr ?? '' })}
        open={Boolean(selectedProxy)}
        onCancel={() => setSelectedProxy(null)}
        footer={<Button onClick={() => setSelectedProxy(null)}>{t('common.close')}</Button>}
        width={560}
        destroyOnHidden
      >
        {selectedProxy && (
          <Descriptions column={1} bordered size="small">
            <Descriptions.Item label={t('cluster.k8sName')}>
              {selectedProxy.clusterName}
            </Descriptions.Item>
            <Descriptions.Item label={t('cluster.nsClusterName')}>
              {selectedProxy.nsClusterName}
            </Descriptions.Item>
            <Descriptions.Item label={t('cluster.proxyAddr')}>
              <Text copyable code>
                {selectedProxy.addr}
              </Text>
            </Descriptions.Item>
            <Descriptions.Item label={t('common.status')}>
              <Tag
                color={
                  selectedProxy.status === 'healthy'
                    ? 'green'
                    : selectedProxy.status === 'warning'
                      ? 'gold'
                      : selectedProxy.status === 'error'
                        ? 'red'
                        : 'default'
                }
              >
                {selectedProxy.status}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label={t('cluster.connections')}>
              {selectedProxy.connections.toLocaleString()}
            </Descriptions.Item>
            <Descriptions.Item label={t('cluster.remotingPort')}>
              {selectedProxy.remotingPort}
            </Descriptions.Item>
            <Descriptions.Item label={t('cluster.grpcPort')}>
              {selectedProxy.grpcPort}
            </Descriptions.Item>
          </Descriptions>
        )}
      </Modal>

      {renderNameServerConfigDiffModal()}
      {renderBrokerConfigDiffModal()}

      <Modal
        title={t('cluster.testConnectionTitle')}
        open={connectModalOpen}
        onCancel={closeConnectModal}
        okText={t('cluster.testConnection')}
        cancelText={t('common.close')}
        confirmLoading={connectTesting}
        onOk={handleTestConnection}
        width={560}
        destroyOnHidden
      >
        <Text type="secondary">{t('cluster.testConnectionDesc')}</Text>
        <Form form={connectForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item
            name="namesrvAddr"
            label={t('cluster.nsAddr')}
            rules={[{ required: true, message: t('cluster.nsAddrPlaceholder') }]}
          >
            <Input
              placeholder={t('cluster.nsAddrPlaceholder')}
              onPressEnter={handleTestConnection}
            />
          </Form.Item>
        </Form>
        {probeResult && (
          <Descriptions column={1} bordered size="small">
            <Descriptions.Item label={t('common.status')}>
              <Tag color={probeResult.connected ? 'green' : 'red'}>
                {probeResult.connected
                  ? t('cluster.testConnectionSuccess')
                  : t('cluster.testConnectionFailed')}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label={t('cluster.probeClusterName')}>
              {probeResult.clusterName}
            </Descriptions.Item>
            <Descriptions.Item label={t('cluster.probeBrokerCount')}>
              {probeResult.brokerCount}
            </Descriptions.Item>
            <Descriptions.Item label={t('cluster.probeBrokers')}>
              {probeResult.brokerNames.length > 0 ? probeResult.brokerNames.join(', ') : '-'}
            </Descriptions.Item>
            <Descriptions.Item label={t('cluster.probeElapsed')}>
              {probeResult.elapsedMillis}
            </Descriptions.Item>
          </Descriptions>
        )}
      </Modal>
    </div>
  );
};

export default ClusterPage;
