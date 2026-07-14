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

import React, { useState } from 'react';
import { Layout, Menu, Button, Tooltip, Dropdown } from 'antd';
import {
    HomeOutlined,
    CloudServerOutlined,
    AppstoreOutlined,
    TeamOutlined,
    SafetyCertificateOutlined,
    SearchOutlined,
    StopOutlined,
    DashboardOutlined,
    KeyOutlined,
    ApiOutlined,
    AlertOutlined,
    BellOutlined,
    AuditOutlined,
    RobotOutlined,
    SettingOutlined,
    GlobalOutlined,
    BulbOutlined,
    QuestionCircleOutlined,
    MenuFoldOutlined,
    MenuUnfoldOutlined,
    LockOutlined,
    TagOutlined,
} from '@ant-design/icons';
import { useLocation, useNavigate } from 'react-router-dom';
import { useLanguage } from '../../i18n/LanguageContext';
import { useTheme } from '../../store/context/ThemeContext';
import { useLlm } from '../../store/context/LlmContext';
import './StudioLayout.css';

const { Sider, Content } = Layout;

const StudioLayout = ({ children }) => {
    const location = useLocation();
    const navigate = useNavigate();
    const { setLang } = useLanguage();
    const { currentThemeName, setCurrentThemeName } = useTheme();
    const { setIsOpen: setCommandBarOpen } = useLlm();
    const [collapsed, setCollapsed] = useState(false);
    const [openKeys, setOpenKeys] = useState(['instance-group', 'ops-group']);

    const getPath = () => location.pathname.replace('/', '') || 'home';

    const handleMenuClick = ({ key }) => {
        if (key === 'home') {
            navigate('/');
        } else {
            navigate(`/${key}`);
        }
    };

    // 实例管理菜单组
    const instanceMenuItems = [
        { key: 'ops', icon: <CloudServerOutlined />, label: '实例列表' },
        { key: 'topic', icon: <AppstoreOutlined />, label: 'Topic管理' },
        { key: 'liteTopic', icon: <TagOutlined />, label: 'LiteTopic管理' },
        { key: 'consumer', icon: <TeamOutlined />, label: 'Group管理' },
        { key: 'acl', icon: <SafetyCertificateOutlined />, label: 'ACL管理' },
        { key: 'message', icon: <SearchOutlined />, label: '消息查询' },
        { key: 'dlqMessage', icon: <StopOutlined />, label: '死信队列' },
    ];

    // 集群运维菜单组
    const opsMenuItems = [
        { key: 'dashboard', icon: <DashboardOutlined />, label: '监控面板' },
        { key: 'k8s-cert', icon: <KeyOutlined />, label: 'K8s证书管理' },
        { key: 'cluster', icon: <CloudServerOutlined />, label: 'RocketMQ集群' },
        { key: 'proxy', icon: <ApiOutlined />, label: '客户端连接' },
        { key: 'alert-rule', icon: <AlertOutlined />, label: '告警规则' },
        { key: 'alert', icon: <BellOutlined />, label: '告警事件' },
        { key: 'audit', icon: <AuditOutlined />, label: '审计日志' },
    ];

    // 侧边栏菜单
    const sidebarMenuItems = [
        { key: 'home', icon: <HomeOutlined />, label: '首页' },
        {
            key: 'instance-group',
            label: '实例管理',
            type: 'group',
            children: instanceMenuItems,
        },
        {
            key: 'ops-group',
            label: '集群运维',
            type: 'group',
            children: opsMenuItems,
        },
    ];

    // 语言切换菜单
    const langMenu = {
        items: [
            { key: 'zh', label: '中文' },
            { key: 'en', label: 'English' },
        ],
        onClick: ({ key }) => setLang(key),
    };

    // 主题切换
    const handleThemeToggle = (checked) => {
        setCurrentThemeName(checked ? 'dark' : 'default');
    };

    return (
        <div className="studio-browser-frame">
            {/* Mac Chrome 浏览器外壳 - 顶部标签栏 */}
            <div className="browser-chrome-top">
                <div className="browser-tab-bar">
                    <div className="browser-traffic-lights">
                        <span className="light red"></span>
                        <span className="light yellow"></span>
                        <span className="light green"></span>
                    </div>
                    <div className="browser-tabs">
                        <div className="browser-tab active">
                            <span className="tab-icon">🚀</span>
                            <span className="tab-title">RocketMQ Studio</span>
                        </div>
                    </div>
                </div>
                <div className="browser-address-bar">
                    <div className="address-bar-left">
                        <Button
                            type="text"
                            size="small"
                            className="nav-btn"
                            icon={<span style={{ fontSize: 12 }}>←</span>}
                        />
                        <Button
                            type="text"
                            size="small"
                            className="nav-btn"
                            icon={<span style={{ fontSize: 12 }}>→</span>}
                        />
                        <Button
                            type="text"
                            size="small"
                            className="nav-btn"
                            icon={<span style={{ fontSize: 12 }}>⟳</span>}
                        />
                    </div>
                    <div className="address-input">
                        <LockOutlined style={{ fontSize: 12, marginRight: 6, color: '#52c41a' }} />
                        <span>rocketmq-studio.local</span>
                    </div>
                    <div className="address-bar-right">
                        <Tooltip title="全局搜索 (⌘K)">
                            <Button
                                type="text"
                                size="small"
                                className="toolbar-btn"
                                icon={<SearchOutlined />}
                                onClick={() => setCommandBarOpen(true)}
                            >
                                <span className="shortcut-hint">⌘K</span>
                            </Button>
                        </Tooltip>
                        <Dropdown menu={langMenu} placement="bottomRight">
                            <Button type="text" size="small" className="toolbar-btn">
                                <GlobalOutlined /> <span className="lang-label">En</span>
                            </Button>
                        </Dropdown>
                        <Tooltip title="明暗模式切换">
                            <Button
                                type="text"
                                size="small"
                                className="toolbar-btn"
                                icon={<BulbOutlined />}
                                onClick={() => handleThemeToggle(currentThemeName !== 'default')}
                            />
                        </Tooltip>
                        <Tooltip title="帮助">
                            <Button
                                type="text"
                                size="small"
                                className="toolbar-btn"
                                icon={<QuestionCircleOutlined />}
                            />
                        </Tooltip>
                    </div>
                </div>
            </div>

            {/* 主体内容区 */}
            <div className="browser-body">
                <Layout className="studio-layout">
                    {/* 左侧侧边栏 */}
                    <Sider
                        width={220}
                        collapsedWidth={64}
                        collapsed={collapsed}
                        className="studio-sider"
                        trigger={null}
                    >
                        <div className="sider-logo">
                            <span className="logo-icon">🚀</span>
                            {!collapsed && (
                                <span className="logo-text">
                                    <span className="logo-rocket">Rocket</span>
                                    <span className="logo-mq">MQ</span>
                                    <span className="logo-studio"> Studio</span>
                                </span>
                            )}
                        </div>
                        <Menu
                            mode="inline"
                            selectedKeys={[getPath()]}
                            openKeys={openKeys}
                            onOpenChange={setOpenKeys}
                            onClick={handleMenuClick}
                            items={sidebarMenuItems}
                            className="studio-menu"
                        />
                        {/* 底部独立菜单 */}
                        <div className="sider-bottom-menu">
                            <div
                                className={`bottom-menu-item ${getPath() === 'llm-settings' ? 'active' : ''}`}
                                onClick={() => navigate('/llm-settings')}
                            >
                                <RobotOutlined />
                                {!collapsed && <span className="bottom-menu-label">AI交互</span>}
                            </div>
                            <div
                                className={`bottom-menu-item ${getPath() === 'settings' ? 'active' : ''}`}
                                onClick={() => navigate('/settings')}
                            >
                                <SettingOutlined />
                                {!collapsed && <span className="bottom-menu-label">设置</span>}
                            </div>
                        </div>
                        {/* 折叠按钮 */}
                        <div className="sider-collapse-btn" onClick={() => setCollapsed(!collapsed)}>
                            {collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
                        </div>
                    </Sider>

                    {/* 右侧主内容区 */}
                    <Layout className="studio-content-layout">
                        <Content className="studio-content">
                            {children}
                        </Content>
                    </Layout>
                </Layout>
            </div>
        </div>
    );
};

export default StudioLayout;