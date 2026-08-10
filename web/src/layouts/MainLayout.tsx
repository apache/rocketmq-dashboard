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
import { Layout, Menu, Breadcrumb, Avatar, Dropdown, Empty, Input, Modal, message } from 'antd';
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
  ChartLine,
  Sun,
  Moon,
  ShieldCheck,
  TrashSimple,
  PlugsConnected,
  BellRinging,
  Notebook,
  Warning,
  GitDiff,
} from '@phosphor-icons/react';
import { useLang } from '../i18n/LangContext';
import { useTheme } from '../theme/useTheme';
import { logout as requestLogout } from '../api/auth';
import useAuthStore from '../stores/authStore';
import {
  filterNavigationEntries,
  isNavigationSearchShortcut,
  type NavigationSearchEntry,
} from './navigationSearch';
import { useDataModeStore } from '../stores/dataModeStore';

const { Sider, Content } = Layout;

const iconSize = 18;

const MainLayout = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { darkMode, toggleTheme } = useTheme();
  const [searchOpen, setSearchOpen] = useState(false);
  const [searchText, setSearchText] = useState('');
  const { lang, setLang, t } = useLang();
  const clearAuth = useAuthStore((state) => state.logout);
  const useMock = useDataModeStore((state) => state.useMock);
  const toggleDataMode = useDataModeStore((state) => state.toggle);

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
    if (key !== 'logout') return;

    try {
      await requestLogout();
    } catch {
      message.warning('服务端退出失败，已清除本地登录状态');
    } finally {
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

  const instanceScopedMatch = location.pathname.match(
    /^\/instance\/[^/]+\/(topic|consumer|message|acl|dlq|resource-plan)$/,
  );
  const selectedMenuKey = instanceScopedMatch
    ? `/instance/${instanceScopedMatch[1]}`
    : location.pathname;

  const menuItems = [
    { key: '/', icon: <House size={iconSize} weight="duotone" />, label: t('nav.home') },
    {
      key: 'instance-group',
      icon: <Database size={iconSize} weight="duotone" />,
      label: t('nav.instance'),
      children: [
        { key: '/instance', icon: <Database size={16} />, label: t('nav.instanceList') },
        { key: '/instance/topic', icon: <ListDashes size={16} />, label: t('nav.topic') },
        { key: '/instance/consumer', icon: <ChatCircleText size={16} />, label: t('nav.group') },
        { key: '/instance/acl', icon: <Key size={16} />, label: t('nav.acl') },
        { key: '/instance/message', icon: <MagnifyingGlass size={16} />, label: t('nav.message') },
        { key: '/instance/dlq', icon: <TrashSimple size={16} />, label: t('nav.dlq') },
        {
          key: '/instance/resource-plan',
          icon: <Notebook size={16} />,
          label: t('nav.resourcePlan'),
        },
      ],
    },
    {
      key: 'cluster-ops-group',
      icon: <Monitor size={iconSize} weight="duotone" />,
      label: t('nav.clusterOps'),
      children: [
        { key: '/ops/dashboard', icon: <ChartBar size={16} />, label: t('nav.dashboard') },
        { key: '/ops/grafana', icon: <ChartLine size={16} />, label: t('nav.grafanaDashboards') },
        { key: '/cluster/certs', icon: <ShieldCheck size={16} />, label: t('nav.certs') },
        { key: '/cluster', icon: <Database size={16} />, label: t('nav.rocketmqCluster') },
        { key: '/cluster/clients', icon: <PlugsConnected size={16} />, label: t('nav.clients') },
        { key: '/ops/alerts', icon: <BellRinging size={16} />, label: t('nav.alertRules') },
        { key: '/ops/system-alerts', icon: <BellRinging size={16} />, label: t('nav.alertEvents') },
        { key: '/ops/audit', icon: <Notebook size={16} />, label: t('nav.audit') },
        {
          key: '/ops/nameserver-config-drift',
          icon: <GitDiff size={16} />,
          label: t('nav.nameServerConfigDrift'),
        },
        {
          key: '/ops/alert-rule-templates',
          icon: <Warning size={16} />,
          label: t('nav.alertRuleAssets'),
        },
      ],
    },
    { key: '/ai', icon: <Sparkle size={iconSize} weight="duotone" />, label: t('nav.ai') },
    {
      key: '/settings',
      icon: <GearSix size={iconSize} weight="duotone" />,
      label: t('nav.settings'),
    },
  ];

  const breadcrumbMap: Record<string, string> = {
    '/': t('nav.home'),
    '/ops': t('nav.clusterOps'),
    '/instance': t('nav.instanceList'),
    '/instance/topic': t('nav.topic'),
    '/instance/consumer': t('nav.group'),
    '/instance/message': t('nav.message'),
    '/instance/acl': t('nav.acl'),
    '/instance/dlq': t('nav.dlq'),
    '/instance/resource-plan': t('nav.resourcePlan'),
    '/cluster': t('nav.rocketmqCluster'),
    '/cluster/certs': t('nav.certs'),
    '/cluster/clients': t('nav.clients'),
    '/ops/dashboard': t('nav.dashboard'),
    '/ops/grafana': t('nav.grafanaDashboards'),
    '/ops/system-alerts': t('nav.alertEvents'),
    '/ops/alerts': t('nav.alertRules'),
    '/ops/audit': t('nav.audit'),
    '/ops/nameserver-config-drift': t('nav.nameServerConfigDrift'),
    '/ops/alert-rule-templates': t('nav.alertRuleAssets'),
    '/ai': t('nav.ai'),
    '/settings': t('nav.settings'),
  };

  const pathSnippets = location.pathname.split('/').filter((i) => i);
  const breadcrumbItems = [
    {
      title: (
        <span onClick={() => navigate('/')} style={{ cursor: 'pointer' }}>
          🏠
        </span>
      ),
      key: 'home',
    },
    ...pathSnippets.map((_, index) => {
      const path = '/' + pathSnippets.slice(0, index + 1).join('/');
      const isSectionLeaf = instanceScopedMatch && index === pathSnippets.length - 1;
      const leafTitle = isSectionLeaf
        ? breadcrumbMap[`/instance/${instanceScopedMatch[1]}`]
        : undefined;
      return {
        title: breadcrumbMap[path] || leafTitle || path,
        key: path,
      };
    }),
  ];

  const userMenu = {
    onClick: handleUserMenuClick,
    items: [
      { key: 'profile', icon: <UserGear size={14} />, label: t('user.profile') },
      { type: 'divider' as const },
      { key: 'logout', label: t('user.logout'), danger: true },
    ],
  };

  const borderColor = darkMode ? '#3a3a3e' : '#f0f0f0';
  const siderBg = darkMode ? '#2a2a2e' : '#ffffff';
  const topBarBg = darkMode ? 'rgba(42,42,46,0.85)' : 'rgba(255,255,255,0.7)';
  const logoColor = darkMode ? '#e5e5e5' : '#1b1b1a';
  const navigationEntries: NavigationSearchEntry[] = menuItems
    .flatMap((item) => ('children' in item && item.children ? item.children : [item]))
    .map((item) => ({ key: String(item.key), label: String(item.label), icon: item.icon }));
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
        跳到主要内容
      </a>
      <Layout style={{ height: '100vh', minHeight: 0, overflow: 'hidden' }}>
        <Sider
          theme={darkMode ? 'dark' : 'light'}
          collapsible
          width={220}
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
            }}
          >
            <span style={{ fontSize: 20 }}>🚀</span>
            <span>RocketMQ Studio</span>
          </div>

          {/* Navigation Menu */}
          <Menu
            theme={darkMode ? 'dark' : 'light'}
            mode="inline"
            selectedKeys={[selectedMenuKey]}
            defaultOpenKeys={['instance-group', 'cluster-ops-group']}
            items={menuItems}
            onClick={({ key }) => navigate(key)}
            style={{ borderRight: 'none', paddingTop: 8, background: 'transparent' }}
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
            <Breadcrumb items={breadcrumbItems} style={{ fontSize: 13 }} />

            {/* Right: Search + Lang + Theme + User */}
            <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
              {/* Search button */}
              <div
                onClick={() => setSearchOpen(true)}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 6,
                  padding: '4px 12px',
                  borderRadius: 6,
                  border: `1px solid ${borderColor}`,
                  cursor: 'pointer',
                  fontSize: 13,
                  color: '#9CA3AF',
                  minWidth: 160,
                }}
              >
                <MagnifyingGlass size={14} />
                <span>{t('common.search')}</span>
                <span
                  style={{
                    marginLeft: 'auto',
                    fontSize: 11,
                    padding: '1px 6px',
                    borderRadius: 4,
                    background: darkMode ? '#333' : '#f5f5f5',
                    border: `1px solid ${borderColor}`,
                  }}
                >
                  ⌘K
                </span>
              </div>

              {/* Data mode toggle */}
              <div
                onClick={handleDataModeToggle}
                style={{
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  gap: 6,
                  padding: '4px 10px',
                  borderRadius: 6,
                  border: `1px solid ${borderColor}`,
                  fontSize: 12,
                  fontWeight: 500,
                  color: useMock ? '#d48806' : '#389e0d',
                  transition: 'all 0.2s',
                }}
                title={
                  useMock
                    ? 'Data Mode: Mock (click to switch to Real)'
                    : 'Data Mode: Real (click to switch to Mock)'
                }
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
              </div>

              {/* Language toggle */}
              <div
                onClick={() => setLang(lang === 'zh' ? 'en' : 'zh')}
                style={{
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  width: 28,
                  height: 28,
                  borderRadius: 6,
                  fontSize: 13,
                  fontWeight: 600,
                  color: '#1677ff',
                  transition: 'background 0.2s',
                }}
                title={lang === 'zh' ? 'Switch to English' : '切换到中文'}
              >
                {lang === 'zh' ? 'En' : '中'}
              </div>

              {/* Theme toggle */}
              <div
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
                }}
                title={darkMode ? 'Light mode' : 'Dark mode'}
              >
                {darkMode ? (
                  <Sun size={18} color="#9CA3AF" weight="fill" />
                ) : (
                  <Moon size={18} color="#9CA3AF" weight="fill" />
                )}
              </div>

              {/* User avatar */}
              <Dropdown menu={userMenu} trigger={['click']}>
                <Avatar
                  size={28}
                  style={{ backgroundColor: '#1677ff', cursor: 'pointer' }}
                  icon={<UserGear size={16} />}
                />
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

      {/* Search Modal */}
      <Modal
        open={searchOpen}
        onCancel={() => {
          setSearchOpen(false);
          setSearchText('');
        }}
        footer={null}
        closable={false}
        styles={{ body: { padding: 0 } }}
        width={520}
        centered
      >
        <div style={{ padding: '16px 20px 8px' }}>
          <Input
            size="large"
            placeholder={t('common.searchPlaceholder')}
            prefix={<MagnifyingGlass size={18} color="#9CA3AF" />}
            value={searchText}
            onChange={(e) => setSearchText(e.target.value)}
            onPressEnter={() => {
              const firstResult = searchResults[0];
              if (!firstResult) return;
              navigate(firstResult.key);
              setSearchOpen(false);
              setSearchText('');
            }}
            autoFocus
            allowClear
            style={{ fontSize: 16 }}
          />
        </div>
        <div style={{ maxHeight: 360, overflow: 'auto', padding: '8px 12px 12px' }}>
          {searchResults.length ? (
            searchResults.map((item) => (
              <div
                key={item.key}
                onClick={() => {
                  navigate(item.key as string);
                  setSearchOpen(false);
                  setSearchText('');
                }}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 10,
                  padding: '10px 12px',
                  borderRadius: 8,
                  cursor: 'pointer',
                  fontSize: 14,
                  transition: 'background 0.15s',
                }}
                onMouseEnter={(e) => {
                  e.currentTarget.style.background = darkMode ? '#1a1a1a' : '#f5f5f5';
                }}
                onMouseLeave={(e) => {
                  e.currentTarget.style.background = 'transparent';
                }}
              >
                {item.icon}
                <span>{item.label}</span>
              </div>
            ))
          ) : (
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="未找到匹配页面" />
          )}
        </div>
      </Modal>
    </>
  );
};

export default MainLayout;
