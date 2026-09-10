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

import { useEffect, useMemo, useState } from 'react';
import { Layout, Menu, Breadcrumb, Avatar, Dropdown, Empty, Modal, message } from 'antd';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import {
  House,
  Database,
  Monitor,
  Sparkle,
  GearSix,
  ChatCircleText,
  Key,
  MagnifyingGlass,
  ListDashes,
  UserGear,
  ChartBar,
  Sun,
  Moon,
  ShieldCheck,
  TrashSimple,
  PlugsConnected,
  BellRinging,
  Siren,
  PaperPlaneTilt,
  Notebook,
  Warning,
} from '@phosphor-icons/react';
import { useLang } from '../i18n/LangContext';
import { useTheme } from '../theme/useTheme';
import { logout as requestLogout } from '../api/auth';
import useAuthStore from '../stores/authStore';
import { clearAiChatHistories } from '../stores/aiChatHistoryStore';
import {
  filterNavigationEntries,
  isNavigationSearchShortcut,
  type NavigationSearchEntry,
} from './navigationSearch';
import { useDataModeStore } from '../stores/dataModeStore';
import { getInstanceCapabilities } from '../services/instanceService';
import type { InstanceCapability } from '../api/instance';

const { Sider, Content } = Layout;

const iconSize = 18;

function hasInstanceCapability(
  capabilities: Set<InstanceCapability> | null,
  capability: InstanceCapability,
) {
  return capabilities === null || capabilities.has(capability);
}

const MainLayout = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { darkMode, toggleTheme } = useTheme();
  const [searchOpen, setSearchOpen] = useState(false);
  const [searchText, setSearchText] = useState('');
  const [activeIndex, setActiveIndex] = useState(0);
  const [collapsed, setCollapsed] = useState(false);
  const { lang, setLang, t } = useLang();
  const clearAuth = useAuthStore((state) => state.logout);
  const admin = useAuthStore((state) => state.admin);
  const useMock = useDataModeStore((state) => state.useMock);
  const toggleDataMode = useDataModeStore((state) => state.toggle);
  const [capabilityState, setCapabilityState] = useState<{
    instanceId: string;
    capabilities: Set<InstanceCapability>;
  } | null>(null);

  // Pages fetch on mount, so reload to re-request everything from the new data source.
  const handleDataModeToggle = () => {
    toggleDataMode();
    window.location.reload();
  };

  const handleUserMenuClick = async ({ key }: { key: string }) => {
    if (key === 'profile') {
      navigate('/settings');
      return;
    }
    if (key === 'users') {
      navigate('/studio/users');
      return;
    }
    if (key !== 'logout') return;

    try {
      await requestLogout();
    } catch {
      message.warning(t('layout.logoutServerFailed'));
    } finally {
      clearAiChatHistories();
      clearAuth();
      navigate('/login', { replace: true });
    }
  };

  useEffect(() => {
    const openSearchWithShortcut = (event: KeyboardEvent) => {
      if (!isNavigationSearchShortcut(event)) return;
      event.preventDefault();
      setSearchOpen(true);
    };
    window.addEventListener('keydown', openSearchWithShortcut);
    return () => window.removeEventListener('keydown', openSearchWithShortcut);
  }, []);

  const instanceScopedMatch = useMemo(
    () => location.pathname.match(/^\/instance\/[^/]+\/(topic|consumer|message|acl|dlq)$/),
    [location.pathname],
  );
  const selectedInstanceId = useMemo(() => {
    const match = location.pathname.match(/^\/instance\/([^/]+)\//);
    if (!match) return null;
    try {
      const value = decodeURIComponent(match[1]);
      return value || null;
    } catch {
      return null;
    }
  }, [location.pathname]);

  useEffect(() => {
    if (!selectedInstanceId) return;
    let active = true;
    void getInstanceCapabilities(selectedInstanceId)
      .then((result) => {
        if (active) {
          setCapabilityState({
            instanceId: selectedInstanceId,
            capabilities: new Set(result.capabilities),
          });
        }
      })
      .catch(() => {
        // Preserve existing navigation when capability discovery is unavailable.
      });
    return () => {
      active = false;
    };
  }, [selectedInstanceId]);

  const instanceCapabilities =
    capabilityState?.instanceId === selectedInstanceId ? capabilityState.capabilities : null;

  const selectedMenuKey = instanceScopedMatch
    ? `/instance/${instanceScopedMatch[1]}`
    : location.pathname;

  const menuItems = useMemo(
    () => [
      { key: '/', icon: <House size={iconSize} weight="duotone" />, label: t('nav.home') },
      {
        key: 'instance-group',
        icon: <Database size={iconSize} weight="duotone" />,
        label: t('nav.instance'),
        children: [
          { key: '/instance', icon: <Database size={16} />, label: t('nav.instanceList') },
          ...(hasInstanceCapability(instanceCapabilities, 'TOPIC_MANAGEMENT')
            ? [{ key: '/instance/topic', icon: <ListDashes size={16} />, label: t('nav.topic') }]
            : []),
          ...(hasInstanceCapability(instanceCapabilities, 'CONSUMER_GROUP_MANAGEMENT')
            ? [
                {
                  key: '/instance/consumer',
                  icon: <ChatCircleText size={16} />,
                  label: t('nav.group'),
                },
              ]
            : []),
          ...(hasInstanceCapability(instanceCapabilities, 'ACL_MANAGEMENT')
            ? [{ key: '/instance/acl', icon: <Key size={16} />, label: t('nav.acl') }]
            : []),
          ...(hasInstanceCapability(instanceCapabilities, 'MESSAGE_QUERY')
            ? [
                {
                  key: '/instance/message',
                  icon: <MagnifyingGlass size={16} />,
                  label: t('nav.message'),
                },
              ]
            : []),
          ...(hasInstanceCapability(instanceCapabilities, 'DLQ_MANAGEMENT')
            ? [{ key: '/instance/dlq', icon: <TrashSimple size={16} />, label: t('nav.dlq') }]
            : []),
          {
            key: '/ops/business-alerts',
            icon: <Warning size={16} />,
            label: t('nav.alertRuleAssets'),
          },
        ],
      },
      {
        key: 'cluster-ops-group',
        icon: <Monitor size={iconSize} weight="duotone" />,
        label: t('nav.clusterOps'),
        children: [
          { key: '/cluster/certs', icon: <ShieldCheck size={16} />, label: t('nav.certs') },
          { key: '/cluster', icon: <Database size={16} />, label: t('nav.rocketmqCluster') },
          { key: '/cluster/clients', icon: <PlugsConnected size={16} />, label: t('nav.clients') },
          { key: '/ops/alerts', icon: <BellRinging size={16} />, label: t('nav.alertRules') },
          {
            key: '/ops/system-alerts',
            icon: <Siren size={16} />,
            label: t('nav.alertEvents'),
          },
          {
            key: '/ops/alert-deliveries',
            icon: <PaperPlaneTilt size={16} />,
            label: t('nav.alertDeliveries'),
          },
          { key: '/ops/dashboard', icon: <ChartBar size={16} />, label: t('nav.dashboard') },
        ],
      },
      {
        key: '/ops/audit',
        icon: <Notebook size={iconSize} weight="duotone" />,
        label: t('nav.audit'),
      },
      { key: '/ai', icon: <Sparkle size={iconSize} weight="duotone" />, label: t('nav.ai') },
      {
        key: '/settings',
        icon: <GearSix size={iconSize} weight="duotone" />,
        label: t('nav.settings'),
      },
    ],
    [t, instanceCapabilities],
  );

  const breadcrumbMap: Record<string, string> = useMemo(
    () => ({
      '/': t('nav.home'),
      '/ops': t('nav.clusterOps'),
      '/instance': t('nav.instanceList'),
      '/instance/topic': t('nav.topic'),
      '/instance/consumer': t('nav.group'),
      '/instance/message': t('nav.message'),
      '/instance/acl': t('nav.acl'),
      '/instance/dlq': t('nav.dlq'),
      '/instance/alerts': t('nav.alertRuleAssets'),
      '/cluster': t('nav.rocketmqCluster'),
      '/cluster/certs': t('nav.certs'),
      '/cluster/clients': t('nav.clients'),
      '/ops/dashboard': t('nav.dashboard'),
      '/ops/grafana': t('nav.grafanaDashboards'),
      '/ops/system-alerts': t('nav.alertEvents'),
      '/ops/alert-deliveries': t('nav.alertDeliveries'),
      '/ops/business-alerts': t('nav.alertRuleAssets'),
      '/ops/alerts': t('nav.alertRules'),
      '/ops/audit': t('nav.audit'),
      '/ai': t('nav.ai'),
      '/settings': t('nav.settings'),
      '/studio/users': t('nav.studioUsers'),
    }),
    [t],
  );

  const breadcrumbItems = useMemo(() => {
    const pathSnippets = location.pathname.split('/').filter((segment) => segment);
    return [
      {
        title: (
          <button
            type="button"
            aria-label={t('layout.goHome')}
            onClick={() => navigate('/')}
            style={{
              cursor: 'pointer',
              border: 0,
              padding: 0,
              background: 'transparent',
              font: 'inherit',
            }}
          >
            🏠
          </button>
        ),
        key: 'home',
      },
      ...pathSnippets
        .map((_, index) => {
          const path = '/' + pathSnippets.slice(0, index + 1).join('/');
          // The instance ID path segment (/instance/<id>) is an identifier, not a
          // navigation level — keep it out of the breadcrumb trail.
          const isInstanceIdSegment =
            index === 1 && pathSnippets[0] === 'instance' && !breadcrumbMap[path];
          if (isInstanceIdSegment) {
            return null;
          }
          const isSectionLeaf = instanceScopedMatch && index === pathSnippets.length - 1;
          const leafTitle = isSectionLeaf
            ? breadcrumbMap[`/instance/${instanceScopedMatch[1]}`]
            : undefined;
          return {
            title: breadcrumbMap[path] || leafTitle || path,
            key: path,
          };
        })
        .filter((item): item is NonNullable<typeof item> => item !== null),
    ];
  }, [location.pathname, navigate, breadcrumbMap, instanceScopedMatch, t]);

  const userMenu = {
    onClick: handleUserMenuClick,
    items: [
      { key: 'profile', icon: <UserGear size={14} />, label: t('user.profile') },
      ...(admin ? [{ key: 'users', icon: <UserGear size={14} />, label: t('nav.studioUsers') }] : []),
      { type: 'divider' as const },
      { key: 'logout', label: t('user.logout'), danger: true },
    ],
  };

  const borderColor = darkMode ? '#3a3a3e' : '#f0f0f0';
  const siderBg = darkMode ? '#2a2a2e' : '#ffffff';
  const topBarBg = darkMode ? 'rgba(42,42,46,0.85)' : 'rgba(255,255,255,0.7)';
  const logoColor = darkMode ? '#e5e5e5' : '#1b1b1a';
  const navigationEntries: NavigationSearchEntry[] = useMemo(
    () =>
      menuItems
        .flatMap((item) => ('children' in item && item.children ? item.children : [item]))
        .map((item) => ({ key: String(item.key), label: String(item.label), icon: item.icon })),
    [menuItems],
  );
  const searchResults = filterNavigationEntries(navigationEntries, searchText);
  const isAiRoute = location.pathname === '/ai';

  return (
    <>
      <a
        href="#main-content"
        style={{
          position: 'fixed',
          top: 8,
          left: 8,
          zIndex: 1000,
          padding: '8px 12px',
          background: '#1677ff',
          color: '#fff',
          borderRadius: 6,
          transform: 'translateY(-150%)',
        }}
        onFocus={(event) => {
          event.currentTarget.style.transform = 'translateY(0)';
        }}
        onBlur={(event) => {
          event.currentTarget.style.transform = 'translateY(-150%)';
        }}
      >
        {t('layout.skipToMain')}
      </a>
      <Layout style={{ height: '100vh', minHeight: 0, overflow: 'hidden' }}>
        <Sider
          theme={darkMode ? 'dark' : 'light'}
          collapsible
          collapsed={collapsed}
          onCollapse={setCollapsed}
          width={220}
          collapsedWidth={64}
          style={{
            background: siderBg,
            borderRight: `1px solid ${borderColor}`,
            boxShadow: darkMode ? '2px 0 8px rgba(0,0,0,0.2)' : '2px 0 8px rgba(0,0,0,0.03)',
            height: '100vh',
            overflow: 'hidden',
          }}
        >
          {/* Logo */}
          <div
            style={{
              height: 48,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              gap: 8,
              borderBottom: `1px solid ${borderColor}`,
              fontSize: 15,
              fontWeight: 600,
              color: logoColor,
              letterSpacing: '-0.01em',
              whiteSpace: 'nowrap',
              overflow: 'hidden',
            }}
          >
            <span style={{ fontSize: 20 }}>🚀</span>
            {!collapsed && <span>RocketMQ Studio</span>}
          </div>

          {/* Navigation Menu */}
          <Menu
            theme={darkMode ? 'dark' : 'light'}
            mode="inline"
            selectedKeys={[selectedMenuKey]}
            defaultOpenKeys={['instance-group', 'cluster-ops-group']}
            items={menuItems}
            onClick={({ key }) => navigate(key)}
            style={{ borderRight: 'none', background: 'transparent' }}
          />
        </Sider>

        <Layout style={{ background: 'transparent', height: '100vh', overflow: 'hidden' }}>
          {/* Top bar */}
          <div
            style={{
              height: 48,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              padding: '0 24px',
              background: topBarBg,
              backdropFilter: 'blur(8px)',
              borderBottom: `1px solid ${borderColor}`,
            }}
          >
            {/* Left: Breadcrumb */}
            <Breadcrumb items={breadcrumbItems} style={{ fontSize: 14 }} />

            {/* Right: Search + Lang + Theme + User */}
            <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
              {/* Search button */}
              <button
                type="button"
                aria-label={t('layout.openSearch')}
                onClick={() => setSearchOpen(true)}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 6,
                  padding: '4px 12px',
                  borderRadius: 6,
                  border: `1px solid ${borderColor}`,
                  cursor: 'pointer',
                  fontSize: 14,
                  color: '#9CA3AF',
                  minWidth: 160,
                  background: 'transparent',
                  font: 'inherit',
                }}
              >
                <MagnifyingGlass size={14} />
                <span>{t('common.search')}</span>
                <span
                  style={{
                    marginLeft: 'auto',
                    fontSize: 14,
                    padding: '1px 6px',
                    borderRadius: 4,
                    background: darkMode ? '#333' : '#f5f5f5',
                    border: `1px solid ${borderColor}`,
                  }}
                >
                  ⌘K
                </span>
              </button>

              {/* Data mode toggle */}
              <button
                type="button"
                aria-label={useMock ? t('layout.switchToRealData') : t('layout.switchToMockData')}
                aria-pressed={useMock}
                onClick={handleDataModeToggle}
                style={{
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  gap: 6,
                  padding: '4px 10px',
                  borderRadius: 6,
                  border: `1px solid ${borderColor}`,
                  fontSize: 14,
                  fontWeight: 500,
                  color: useMock ? '#d48806' : '#389e0d',
                  transition: 'all 0.2s',
                  background: 'transparent',
                  font: 'inherit',
                }}
                title={useMock ? t('layout.switchToRealData') : t('layout.switchToMockData')}
              >
                <span
                  style={{
                    width: 8,
                    height: 8,
                    borderRadius: '50%',
                    background: useMock ? '#faad14' : '#52c41a',
                    display: 'inline-block',
                  }}
                />
                {useMock ? 'Mock' : 'Real'}
              </button>

              {/* Language toggle */}
              <button
                type="button"
                aria-label={
                  lang === 'zh' ? t('layout.switchToEnglish') : t('layout.switchToChinese')
                }
                onClick={() => setLang(lang === 'zh' ? 'en' : 'zh')}
                style={{
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  width: 28,
                  height: 28,
                  borderRadius: 6,
                  fontSize: 14,
                  fontWeight: 600,
                  color: '#1677ff',
                  transition: 'background 0.2s',
                  border: 0,
                  padding: 0,
                  background: 'transparent',
                  font: 'inherit',
                }}
                title={lang === 'zh' ? t('layout.switchToEnglish') : t('layout.switchToChinese')}
              >
                {lang === 'zh' ? 'En' : '中'}
              </button>

              {/* Theme toggle */}
              <button
                type="button"
                aria-label={
                  darkMode ? t('layout.switchToLightTheme') : t('layout.switchToDarkTheme')
                }
                aria-pressed={darkMode}
                onClick={toggleTheme}
                style={{
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  width: 28,
                  height: 28,
                  borderRadius: 6,
                  transition: 'background 0.2s',
                  border: 0,
                  padding: 0,
                  background: 'transparent',
                  font: 'inherit',
                }}
                title={darkMode ? t('layout.switchToLightTheme') : t('layout.switchToDarkTheme')}
              >
                {darkMode ? (
                  <Sun size={18} color="#9CA3AF" weight="fill" />
                ) : (
                  <Moon size={18} color="#9CA3AF" weight="fill" />
                )}
              </button>

              {/* User avatar */}
              <Dropdown menu={userMenu} trigger={['click']}>
                <button
                  type="button"
                  aria-label={t('layout.openUserMenu')}
                  style={{
                    cursor: 'pointer',
                    border: 0,
                    padding: 0,
                    background: 'transparent',
                    font: 'inherit',
                  }}
                >
                  <Avatar
                    size={28}
                    style={{ backgroundColor: '#1677ff' }}
                    icon={<UserGear size={16} />}
                  />
                </button>
              </Dropdown>
            </div>
          </div>

          <Content
            id="main-content"
            tabIndex={-1}
            style={{
              padding: 0,
              background: 'transparent',
              minHeight: 0,
              height: 'calc(100vh - 48px)',
              overflow: isAiRoute ? 'hidden' : 'auto',
            }}
          >
            <Outlet />
          </Content>
        </Layout>
      </Layout>

      {/* Search Modal (command palette) */}
      <Modal
        open={searchOpen}
        onCancel={() => {
          setSearchOpen(false);
          setSearchText('');
          setActiveIndex(0);
        }}
        footer={null}
        closable={false}
        styles={{
          body: { padding: 0 },
          content: { padding: 0, overflow: 'hidden', borderRadius: 12 },
        }}
        width={600}
        style={{ top: '12vh' }}
      >
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 10,
            padding: '14px 20px',
            borderBottom: `1px solid ${borderColor}`,
          }}
        >
          <MagnifyingGlass size={18} color="#9CA3AF" />
          <input
            placeholder={t('common.searchPlaceholder')}
            value={searchText}
            onChange={(e) => {
              setSearchText(e.target.value);
              setActiveIndex(0);
            }}
            onKeyDown={(e) => {
              if (e.key === 'ArrowDown') {
                e.preventDefault();
                setActiveIndex((i) => Math.min(i + 1, Math.max(searchResults.length - 1, 0)));
              } else if (e.key === 'ArrowUp') {
                e.preventDefault();
                setActiveIndex((i) => Math.max(i - 1, 0));
              } else if (e.key === 'Enter') {
                const target = searchResults[Math.min(activeIndex, searchResults.length - 1)];
                if (!target) return;
                navigate(target.key as string);
                setSearchOpen(false);
                setSearchText('');
                setActiveIndex(0);
              }
            }}
            autoFocus
            style={{
              flex: 1,
              border: 'none',
              outline: 'none',
              fontSize: 16,
              background: 'transparent',
              color: 'inherit',
            }}
          />
          <span
            style={{
              fontSize: 14,
              color: '#9CA3AF',
              padding: '1px 6px',
              borderRadius: 4,
              background: darkMode ? '#333' : '#f5f5f5',
              border: `1px solid ${borderColor}`,
              whiteSpace: 'nowrap',
            }}
          >
            ESC
          </span>
        </div>
        <div style={{ maxHeight: 380, overflow: 'auto', padding: 8 }}>
          {searchResults.length ? (
            searchResults.map((item, index) => {
              const active = index === activeIndex;
              return (
                <button
                  type="button"
                  key={item.key}
                  ref={(el) => {
                    if (el && active) el.scrollIntoView?.({ block: 'nearest' });
                  }}
                  onClick={() => {
                    navigate(item.key as string);
                    setSearchOpen(false);
                    setSearchText('');
                    setActiveIndex(0);
                  }}
                  onMouseEnter={() => setActiveIndex(index)}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 12,
                    padding: '10px 14px',
                    borderRadius: 8,
                    cursor: 'pointer',
                    fontSize: 14,
                    width: '100%',
                    border: 0,
                    background: active ? (darkMode ? '#1f2937' : '#eff6ff') : 'transparent',
                    color: 'inherit',
                    textAlign: 'left',
                    font: 'inherit',
                  }}
                >
                  <span style={{ color: active ? '#1677ff' : '#9CA3AF', display: 'flex' }}>
                    {item.icon}
                  </span>
                  <span style={{ flex: 1 }}>{item.label}</span>
                  {active && (
                    <span
                      aria-hidden
                      style={{
                        fontSize: 14,
                        color: '#9CA3AF',
                        padding: '0 6px',
                        borderRadius: 4,
                        background: darkMode ? '#333' : '#f5f5f5',
                        border: `1px solid ${borderColor}`,
                      }}
                    >
                      ↵
                    </span>
                  )}
                </button>
              );
            })
          ) : (
            <Empty
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              description={t('layout.searchNotFound')}
              style={{ padding: '24px 0' }}
            />
          )}
        </div>
        <div
          style={{
            display: 'flex',
            gap: 16,
            padding: '8px 20px',
            borderTop: `1px solid ${borderColor}`,
            fontSize: 14,
            color: '#9CA3AF',
            background: darkMode ? '#26262a' : '#fafafa',
          }}
        >
          <span>{t('layout.searchNavHint')}</span>
          <span>{t('layout.searchOpenHint')}</span>
          <span>{t('layout.searchCloseHint')}</span>
        </div>
      </Modal>
    </>
  );
};

export default MainLayout;
