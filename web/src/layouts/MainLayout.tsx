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

import { useEffect, useMemo, useRef, useState, type CSSProperties } from 'react';
import { Layout, Menu, Breadcrumb, Avatar, Drawer, Dropdown, Empty, Modal, message } from 'antd';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import {
  House,
  List,
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
import {
  filterNavigationEntries,
  isNavigationSearchShortcut,
  type NavigationSearchEntry,
} from './navigationSearch';
import { useDataModeStore } from '../stores/dataModeStore';
import { getInstanceCapabilities } from '../services/instanceService';
import type { InstanceCapability } from '../api/instance';
import './MainLayout.css';

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
  const [mobileNavOpen, setMobileNavOpen] = useState(false);
  const searchInputRef = useRef<HTMLInputElement>(null);
  const { lang, setLang, t } = useLang();
  const clearAuth = useAuthStore((state) => state.logout);
  const admin = useAuthStore((state) => state.admin);
  const username = useAuthStore((state) => state.user);
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
    if (key === 'dataMode') {
      handleDataModeToggle();
      return;
    }
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
      message.warning(t('user.logoutFailed'));
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
      '/studio/users': t('userMgmt.title'),
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
          // Same for the AI conversation path (/ai/c/<id>): everything after /ai is
          // conversation identity, not a navigation level.
          const isAiConversationSegment = index >= 1 && pathSnippets[0] === 'ai';
          if (isAiConversationSegment) {
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
      ...(admin
        ? [{ key: 'users', icon: <UserGear size={14} />, label: t('userMgmt.title') }]
        : []),
      {
        key: 'dataMode',
        icon: (
          <span
            aria-hidden
            style={{
              width: 8,
              height: 8,
              borderRadius: '50%',
              background: useMock ? '#faad14' : '#52c41a',
              display: 'inline-block',
            }}
          />
        ),
        label: `${t('layout.dataMode')}: ${useMock ? 'Mock' : 'Real'}`,
        title: useMock ? t('layout.switchToRealData') : t('layout.switchToMockData'),
      },
      { type: 'divider' as const },
      { key: 'logout', label: t('user.logout'), danger: true },
    ],
  };

  const borderColor = darkMode ? '#3a3a3e' : '#f0f0f0';
  const siderBg = darkMode ? '#2a2a2e' : '#ffffff';
  const topBarBg = darkMode ? 'rgba(42,42,46,0.85)' : 'rgba(255,255,255,0.7)';
  const logoColor = darkMode ? '#e5e5e5' : '#1b1b1a';
  const kbdStyle: CSSProperties = {
    fontSize: 12,
    lineHeight: '18px',
    padding: '0 6px',
    borderRadius: 6,
    background: darkMode ? '#333' : '#f5f5f5',
    border: `1px solid ${borderColor}`,
    color: darkMode ? '#a1a1aa' : '#9CA3AF',
    fontFamily: 'inherit',
    whiteSpace: 'nowrap',
  };
  const navigationEntries: NavigationSearchEntry[] = useMemo(
    () =>
      menuItems
        .flatMap((item) => ('children' in item && item.children ? item.children : [item]))
        .map((item) => ({ key: String(item.key), label: String(item.label), icon: item.icon })),
    [menuItems],
  );
  const filteredEntries = filterNavigationEntries(navigationEntries, searchText);
  // Group filtered results by their navigation section for the grid layout; the
  // top-level pages (home / audit / AI / settings) share a single "general" bucket.
  const resultSections = useMemo(() => {
    const matched = new Set(filteredEntries.map((entry) => entry.key));
    const sections: { title: string; entries: NavigationSearchEntry[] }[] = [];
    const general: NavigationSearchEntry[] = [];
    for (const item of menuItems) {
      if ('children' in item && item.children) {
        const entries = item.children
          .map((child) => ({
            key: String(child.key),
            label: String(child.label),
            icon: child.icon,
          }))
          .filter((entry) => matched.has(entry.key));
        if (entries.length > 0) sections.push({ title: String(item.label), entries });
      } else {
        const entry = { key: String(item.key), label: String(item.label), icon: item.icon };
        if (matched.has(entry.key)) general.push(entry);
      }
    }
    if (general.length > 0)
      sections.unshift({ title: t('layout.searchGeneral'), entries: general });
    return sections;
  }, [menuItems, filteredEntries, t]);
  // Keyboard navigation follows the visual (grouped) order.
  const searchResults = useMemo(
    () => resultSections.flatMap((section) => section.entries),
    [resultSections],
  );
  const isAiRoute = location.pathname === '/ai';
  const navigationMenu = (onSelect: (key: string) => void) => (
    <Menu
      theme={darkMode ? 'dark' : 'light'}
      mode="inline"
      selectedKeys={[selectedMenuKey]}
      defaultOpenKeys={['instance-group', 'cluster-ops-group']}
      items={menuItems}
      onClick={({ key }) => onSelect(key)}
      style={{ borderRight: 'none', background: 'transparent' }}
    />
  );

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
          className="studio-desktop-sidebar"
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
          {navigationMenu(navigate)}
        </Sider>

        <Layout
          className="studio-main-column"
          style={{ background: 'transparent', height: '100vh', overflow: 'hidden' }}
        >
          {/* Top bar */}
          <div
            className="studio-topbar"
            style={{
              height: 48,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              background: topBarBg,
              backdropFilter: 'blur(8px)',
              borderBottom: `1px solid ${borderColor}`,
            }}
          >
            <button
              type="button"
              className="studio-mobile-menu-button"
              aria-label={t('layout.openNavigation')}
              aria-expanded={mobileNavOpen}
              onClick={() => setMobileNavOpen(true)}
              style={{ color: logoColor }}
            >
              <List size={22} />
            </button>
            {/* Left: Breadcrumb */}
            <Breadcrumb
              className="studio-breadcrumb"
              items={breadcrumbItems}
              style={{ fontSize: 14 }}
            />

            {/* Right: Search + Lang + Theme + User */}
            <div
              className="studio-topbar-actions"
              style={{ display: 'flex', alignItems: 'center' }}
            >
              {/* Search button */}
              <button
                type="button"
                className="studio-search-button"
                aria-label={t('layout.openSearch')}
                onClick={() => setSearchOpen(true)}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 8,
                  borderRadius: 999,
                  border: `1px solid ${borderColor}`,
                  cursor: 'pointer',
                  fontSize: 14,
                  color: '#9CA3AF',
                  background: darkMode ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.03)',
                  font: 'inherit',
                  transition: 'all 0.2s',
                }}
              >
                <MagnifyingGlass size={15} />
                <span className="studio-search-label" style={{ flex: 1, textAlign: 'left' }}>
                  {t('common.search')}
                </span>
                <kbd className="studio-search-shortcut" style={kbdStyle}>
                  ⌘K
                </kbd>
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
                    style={{
                      background: 'linear-gradient(135deg, #7c3aed, #d946ef)',
                      fontSize: 13,
                      fontWeight: 600,
                      color: '#ffffff',
                    }}
                  >
                    {(username ?? 'U').charAt(0).toUpperCase()}
                  </Avatar>
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

      <Drawer
        title="🚀 RocketMQ Studio"
        placement="left"
        width="min(280px, 85vw)"
        open={mobileNavOpen}
        onClose={() => setMobileNavOpen(false)}
        destroyOnHidden
        styles={{
          header: {
            background: siderBg,
            color: logoColor,
            borderBottom: `1px solid ${borderColor}`,
          },
          body: { padding: 0, background: siderBg },
        }}
      >
        {navigationMenu((key) => {
          setMobileNavOpen(false);
          navigate(key);
        })}
      </Drawer>

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
          content: {
            padding: 0,
            overflow: 'hidden',
            borderRadius: 16,
            border: `1px solid ${borderColor}`,
            boxShadow: '0 24px 80px -24px rgba(0, 0, 0, 0.35)',
          },
        }}
        width={640}
        style={{ top: '14vh' }}
        afterOpenChange={(open) => {
          if (open) searchInputRef.current?.focus();
        }}
      >
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 12,
            padding: '16px 20px',
            borderBottom: `1px solid ${borderColor}`,
          }}
        >
          <MagnifyingGlass size={18} color="#9CA3AF" />
          <input
            ref={searchInputRef}
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
              fontSize: 15,
              background: 'transparent',
              color: 'inherit',
            }}
          />
          <kbd style={kbdStyle}>ESC</kbd>
        </div>
        <div style={{ maxHeight: 420, overflow: 'auto', padding: '12px 16px' }}>
          {resultSections.length ? (
            resultSections.map((section) => (
              <div key={section.title} style={{ marginBottom: 12 }}>
                <div
                  style={{
                    fontSize: 12,
                    fontWeight: 600,
                    color: '#9CA3AF',
                    padding: '0 4px',
                    marginBottom: 6,
                  }}
                >
                  {section.title}
                </div>
                <div
                  style={{
                    display: 'grid',
                    gridTemplateColumns: 'repeat(4, 1fr)',
                    gap: 8,
                  }}
                >
                  {section.entries.map((item) => {
                    const active = searchResults[activeIndex]?.key === item.key;
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
                        onMouseEnter={() => {
                          const index = searchResults.findIndex((r) => r.key === item.key);
                          if (index >= 0) setActiveIndex(index);
                        }}
                        style={{
                          display: 'flex',
                          alignItems: 'center',
                          gap: 8,
                          padding: '8px 10px',
                          borderRadius: 10,
                          cursor: 'pointer',
                          fontSize: 13,
                          border: `1px solid ${active ? 'rgba(124, 58, 237, 0.35)' : 'transparent'}`,
                          background: active
                            ? darkMode
                              ? 'rgba(124, 58, 237, 0.18)'
                              : 'rgba(124, 58, 237, 0.08)'
                            : darkMode
                              ? 'rgba(255, 255, 255, 0.04)'
                              : 'rgba(0, 0, 0, 0.025)',
                          color: 'inherit',
                          textAlign: 'left',
                          font: 'inherit',
                          transition: 'background 0.15s, border-color 0.15s',
                        }}
                      >
                        <span
                          style={{
                            color: active ? '#7c3aed' : '#9CA3AF',
                            display: 'flex',
                            flexShrink: 0,
                          }}
                        >
                          {item.icon}
                        </span>
                        <span
                          style={{
                            fontWeight: active ? 600 : 500,
                            overflow: 'hidden',
                            textOverflow: 'ellipsis',
                            whiteSpace: 'nowrap',
                            minWidth: 0,
                          }}
                        >
                          {item.label}
                        </span>
                      </button>
                    );
                  })}
                </div>
              </div>
            ))
          ) : (
            <Empty
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              description={t('layout.noMatchingPage')}
              style={{ padding: '24px 0' }}
            />
          )}
        </div>
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 16,
            padding: '10px 20px',
            borderTop: `1px solid ${borderColor}`,
            fontSize: 12,
            color: '#9CA3AF',
            background: darkMode ? '#26262a' : '#fafafa',
          }}
        >
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
            <kbd style={kbdStyle}>↑</kbd>
            <kbd style={kbdStyle}>↓</kbd>
            {t('layout.shortcutNavigate')}
          </span>
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
            <kbd style={kbdStyle}>↵</kbd>
            {t('common.open')}
          </span>
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
            <kbd style={kbdStyle}>ESC</kbd>
            {t('common.close')}
          </span>
        </div>
      </Modal>
    </>
  );
};

export default MainLayout;
