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

import { useState, useEffect, useCallback } from 'react';
import { Layout, Menu, Avatar, Dropdown, Input, ConfigProvider, theme, Modal, Tooltip } from 'antd';
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
  Notebook,
  SidebarSimple,
} from '@phosphor-icons/react';
import { useLang } from '../i18n/LangContext';

const { Sider, Content } = Layout;
const iconSize = 18;

/* ─── Breadcrumb route map ─── */
const breadcrumbRoutes: Record<string, { parent?: string; label: string }> = {
  '/': { label: '' },
  '/instance': { parent: 'nav.instanceList', label: 'nav.instanceList' },
  '/instance/topic': { parent: 'nav.instanceList', label: 'nav.topic' },
  '/instance/consumer': { parent: 'nav.instanceList', label: 'nav.group' },
  '/instance/acl': { parent: 'nav.instanceList', label: 'nav.acl' },
  '/instance/message': { parent: 'nav.instanceList', label: 'nav.message' },
  '/instance/dlq': { parent: 'nav.instanceList', label: 'nav.dlq' },
  '/cluster': { label: 'nav.rocketmqCluster' },
  '/cluster/certs': { parent: 'nav.clusterOps', label: 'nav.certs' },
  '/cluster/clients': { parent: 'nav.clusterOps', label: 'nav.clients' },
  '/ops/dashboard': { parent: 'nav.clusterOps', label: 'nav.dashboard' },
  '/ops/alerts': { parent: 'nav.clusterOps', label: 'nav.alertRules' },
  '/ops/system-alerts': { parent: 'nav.clusterOps', label: 'nav.alertEvents' },
  '/ops/audit': { parent: 'nav.clusterOps', label: 'nav.audit' },
  '/ai': { label: 'nav.ai' },
  '/settings': { label: 'nav.settings' },
};

const Breadcrumb = () => {
  const loc = useLocation();
  const { t } = useLang();
  const route = breadcrumbRoutes[loc.pathname];
  if (!route) return null;
  // Home page: show only house icon
  if (loc.pathname === '/') {
    return <House size={16} weight="duotone" style={{ color: '#1677ff' }} />;
  }
  const items: string[] = [];
  if (route.parent) items.push(t(route.parent));
  items.push(t(route.label));
  return (
    <span style={{ fontSize: 13, color: '#8c8c8c' }}>
      {items.map((item, i) => (
        <span key={i}>
          {i > 0 && <span style={{ margin: '0 6px', color: '#d9d9d9' }}>/</span>}
          <span style={i === items.length - 1 ? { color: '#262626', fontWeight: 500 } : undefined}>
            {item}
          </span>
        </span>
      ))}
    </span>
  );
};

const MainLayout = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const [darkMode, setDarkMode] = useState(false);
  const [collapsed, setCollapsed] = useState(false);
  const [searchOpen, setSearchOpen] = useState(false);
  const [searchText, setSearchText] = useState('');
  const { lang, setLang, t } = useLang();

  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
        e.preventDefault();
        setSearchOpen(true);
      }
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, []);

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
      ],
    },
    {
      key: 'cluster-ops-group',
      icon: <Monitor size={iconSize} weight="duotone" />,
      label: t('nav.clusterOps'),
      children: [
        { key: '/ops/dashboard', icon: <ChartBar size={16} />, label: t('nav.dashboard') },
        { key: '/cluster/certs', icon: <ShieldCheck size={16} />, label: t('nav.certs') },
        { key: '/cluster', icon: <Database size={16} />, label: t('nav.rocketmqCluster') },
        { key: '/cluster/clients', icon: <PlugsConnected size={16} />, label: t('nav.clients') },
        { key: '/ops/alerts', icon: <BellRinging size={16} />, label: t('nav.alertRules') },
        { key: '/ops/system-alerts', icon: <BellRinging size={16} />, label: t('nav.alertEvents') },
        { key: '/ops/audit', icon: <Notebook size={16} />, label: t('nav.audit') },
      ],
    },
  ];

  const bottomMenuItems = [
    { key: '/ai', icon: <Sparkle size={iconSize} weight="duotone" />, label: t('nav.ai') },
    {
      key: '/settings',
      icon: <GearSix size={iconSize} weight="duotone" />,
      label: t('nav.settings'),
    },
  ];

  const userMenu = {
    items: [
      { key: 'profile', icon: <UserGear size={14} />, label: t('user.profile') },
      { type: 'divider' as const },
      { key: 'logout', label: t('user.logout'), danger: true },
    ],
  };

  const allMenuItems = [
    ...menuItems.flatMap((item) => ('children' in item && item.children ? item.children : [item])),
    ...bottomMenuItems,
  ];

  const handleNavigate = useCallback((key: string) => navigate(key), [navigate]);

  const isDark = darkMode;
  const siderBg = isDark ? '#1e1e22' : '#f7f8fa';
  const borderColor = isDark ? '#2e2e32' : '#e8e8ed';
  const topBarBg = isDark ? 'rgba(30,30,34,0.92)' : 'rgba(255,255,255,0.82)';
  const logoColor = isDark ? '#e5e5e5' : '#1b1b1a';
  const contentBg = isDark ? '#141416' : '#f0f1f3';

  return (
    <ConfigProvider
      theme={{
        algorithm: isDark ? theme.darkAlgorithm : theme.defaultAlgorithm,
        token: isDark
          ? {
              colorBgBase: '#141416',
              colorBgContainer: '#1e1e22',
              colorBgElevated: '#2a2a2e',
              colorBorder: '#2e2e32',
              colorBorderSecondary: '#252528',
            }
          : undefined,
      }}
    >
      {/* ── macOS Chrome Shell ── */}
      <div className="chrome-shell">
        {/* ── Title Bar ── */}
        <div className="chrome-titlebar">
          <div className="chrome-traffic-lights">
            <span className="traffic-dot traffic-close" />
            <span className="traffic-dot traffic-minimize" />
            <span className="traffic-dot traffic-maximize" />
          </div>
          <div className="chrome-tabs">
            <div className="chrome-tab chrome-tab-active">
              <span className="chrome-tab-icon">🚀</span>
              <span className="chrome-tab-title">RocketMQ Studio</span>
            </div>
          </div>
          <div className="chrome-titlebar-actions">
            <Tooltip title={lang === 'zh' ? 'Switch to English' : '切换到中文'}>
              <button
                className="chrome-action-btn"
                onClick={() => setLang(lang === 'zh' ? 'en' : 'zh')}
              >
                {lang === 'zh' ? 'En' : '中'}
              </button>
            </Tooltip>
            <Tooltip title={isDark ? 'Light mode' : 'Dark mode'}>
              <button className="chrome-action-btn" onClick={() => setDarkMode(!darkMode)}>
                {isDark ? <Sun size={14} /> : <Moon size={14} />}
              </button>
            </Tooltip>
            <Dropdown menu={userMenu} trigger={['click']}>
              <Avatar
                size={26}
                style={{ backgroundColor: '#1677ff', cursor: 'pointer', fontSize: 12 }}
                icon={<UserGear size={14} />}
              />
            </Dropdown>
          </div>
        </div>

        {/* ── Address Bar ── */}
        <div className="chrome-addressbar">
          <div className="chrome-url-bar" onClick={() => setSearchOpen(true)}>
            <MagnifyingGlass size={12} className="chrome-url-icon" />
            <span className="chrome-url-text">rocketmq-studio.local</span>
            <kbd className="chrome-url-kbd">⌘K</kbd>
          </div>
        </div>

        {/* ── Main Layout: Sidebar + Content ── */}
        <Layout className="chrome-body">
          <Sider
            theme={isDark ? 'dark' : 'light'}
            collapsible
            collapsed={collapsed}
            onCollapse={setCollapsed}
            width={220}
            collapsedWidth={64}
            trigger={null}
            className="studio-sidebar"
            style={{ background: siderBg, borderRight: `1px solid ${borderColor}` }}
          >
            {/* Logo */}
            <div className="sidebar-logo" style={{ color: logoColor, borderColor }}>
              <span className="sidebar-logo-icon">🚀</span>
              {!collapsed && (
                <span className="sidebar-logo-text gradient-text">RocketMQ Studio</span>
              )}
            </div>

            {/* Main nav */}
            <div className="sidebar-menu-area">
              <Menu
                theme={isDark ? 'dark' : 'light'}
                mode="inline"
                selectedKeys={[location.pathname]}
                defaultOpenKeys={collapsed ? [] : ['instance-group', 'cluster-ops-group']}
                items={menuItems}
                onClick={({ key }) => handleNavigate(key)}
                className="studio-menu"
                inlineCollapsed={collapsed}
              />
            </div>

            {/* Bottom: AI + Settings + Collapse */}
            <div className="sidebar-bottom" style={{ borderColor }}>
              <Menu
                theme={isDark ? 'dark' : 'light'}
                mode="inline"
                selectedKeys={[location.pathname]}
                items={bottomMenuItems}
                onClick={({ key }) => handleNavigate(key)}
                className="studio-menu studio-menu-bottom"
                inlineCollapsed={collapsed}
              />
              <div className="sidebar-collapse-btn" style={{ borderColor }}>
                <button
                  className="collapse-toggle"
                  onClick={() => setCollapsed(!collapsed)}
                  title={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
                >
                  <SidebarSimple
                    size={16}
                    weight="bold"
                    style={{
                      transform: collapsed ? 'rotate(180deg)' : 'none',
                      transition: 'transform 0.2s',
                    }}
                  />
                </button>
              </div>
            </div>
          </Sider>

          <Layout style={{ background: 'transparent' }}>
            <div
              className="studio-topbar"
              style={{ background: topBarBg, borderBottom: `1px solid ${borderColor}` }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: 16, flex: 1 }}>
                <Breadcrumb />
                <div
                  className="studio-search-trigger"
                  onClick={() => setSearchOpen(true)}
                  style={{ borderColor }}
                >
                  <MagnifyingGlass size={14} />
                  <span>{t('common.search')}</span>
                  <kbd className="studio-search-kbd">⌘K</kbd>
                </div>
              </div>
            </div>
            <Content
              style={{ padding: 0, background: contentBg, minHeight: 0, overflow: 'auto', flex: 1 }}
            >
              <Outlet />
            </Content>
          </Layout>
        </Layout>
      </div>

      {/* ── Search Modal ── */}
      <Modal
        open={searchOpen}
        onCancel={() => {
          setSearchOpen(false);
          setSearchText('');
        }}
        footer={null}
        closable={false}
        styles={{ body: { padding: 0 } }}
        width={560}
        centered
        className="studio-search-modal"
      >
        <div style={{ padding: '16px 20px 8px' }}>
          <Input
            size="large"
            placeholder={t('common.searchPlaceholder')}
            prefix={<MagnifyingGlass size={18} color="#9CA3AF" />}
            value={searchText}
            onChange={(e) => setSearchText(e.target.value)}
            autoFocus
            allowClear
            style={{ fontSize: 16 }}
          />
        </div>
        <div style={{ maxHeight: 360, overflow: 'auto', padding: '8px 12px 12px' }}>
          {allMenuItems
            .filter((item) => {
              if (!searchText) return true;
              return String(item.label).toLowerCase().includes(searchText.toLowerCase());
            })
            .map((item) => (
              <div
                key={item.key}
                onClick={() => {
                  navigate(item.key as string);
                  setSearchOpen(false);
                  setSearchText('');
                }}
                className="studio-search-item"
              >
                <span className="studio-search-item-icon">{item.icon}</span>
                <span>{item.label}</span>
              </div>
            ))}
        </div>
      </Modal>
    </ConfigProvider>
  );
};

export default MainLayout;
