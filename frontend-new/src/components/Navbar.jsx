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

import React, {useEffect, useState} from 'react';
import {Button, Drawer, Dropdown, Grid, Layout, Menu, Space, Tooltip} from 'antd';
import {BgColorsOutlined, DownOutlined, GlobalOutlined, MenuOutlined, RobotOutlined, SearchOutlined, UserOutlined} from '@ant-design/icons';
import {useLocation, useNavigate} from 'react-router-dom';
import {useLanguage} from '../i18n/LanguageContext';
import {useTheme} from "../store/context/ThemeContext";
import {useClusterCapabilities} from '../store/context/ClusterCapabilitiesContext';
import {useLlm} from '../store/context/LlmContext';
import {remoteApi} from "../api/remoteApi/remoteApi";

const {Header} = Layout;
const {useBreakpoint} = Grid; // Used to determine screen breakpoints

const Navbar = ({rmqVersion = true, showAcl = true}) => {
    const location = useLocation();
    const navigate = useNavigate();
    const {lang, setLang, t} = useLanguage();
    const screens = useBreakpoint(); // Get current screen size breakpoints
    const {currentThemeName, setCurrentThemeName} = useTheme();
    const {capabilities, loading} = useClusterCapabilities();
    const {setIsOpen: setCommandBarOpen} = useLlm();
    const [userName, setUserName] = useState(null);
    const [drawerVisible, setDrawerVisible] = useState(false); // Controls drawer visibility

    // Get selected menu item key based on current route path
    const getPath = () => location.pathname.replace('/', '');

    const handleMenuClick = ({key}) => {
        navigate(`/${key}`);
        setDrawerVisible(false); // Close drawer after clicking a menu item
    };

    const onLogout = () => {
        remoteApi.logout().then(res => {
            if (res.status === 0) {
                window.localStorage.removeItem("username");
                window.localStorage.removeItem("userRole");
                window.localStorage.removeItem("token");
                window.localStorage.removeItem("rmqVersion");
                navigate('/login');
            } else {
                console.error('Logout failed:', res.message)
                navigate('/login');
            }
        })

    };

    useEffect(() => {
        const storedUsername = window.localStorage.getItem("username");
        if (storedUsername) {
            setUserName(storedUsername);
        } else {
            setUserName(null);
        }
    }, []);

    const langMenu = (
        <Menu onClick={({key}) => setLang(key)}>
            <Menu.Item key="en">{t.ENGLISH}</Menu.Item>
            <Menu.Item key="zh">{t.CHINESE}</Menu.Item>
        </Menu>
    );

    const userMenu = (
        <Menu>
            <Menu.Item key="logout" onClick={onLogout}>{t.LOGOUT}</Menu.Item>
        </Menu>
    );

    const themeMenu = (
        <Menu onClick={({key}) => setCurrentThemeName(key)}>
            <Menu.Item key="default">{t.BLUE} ({t.DEFAULT})</Menu.Item>
            <Menu.Item key="pink">{t.PINK}</Menu.Item>
            <Menu.Item key="green">{t.GREEN}</Menu.Item>
        </Menu>
    );


    // Menu item configuration - dynamically based on cluster capabilities
    const menuItems = [
        {key: 'ops', label: t.OPS},
        ...(capabilities.isV5Architecture ? [{key: 'proxy', label: t.PROXY}] : []),
        {key: '', label: t.DASHBOARD}, // Dashboard corresponds to root path
        {key: 'cluster', label: t.CLUSTER},
        {key: 'topic', label: t.TOPIC},
        ...(capabilities.supportsLiteTopic ? [{key: 'lite-topic', label: t.LITE_TOPIC}] : []),
        {key: 'consumer', label: t.CONSUMER},
        ...(capabilities.supportsPopConsumption ? [{key: 'pop-consumer', label: t.POP_CONSUMER}] : []),
        {key: 'producer', label: t.PRODUCER},
        {key: 'message', label: t.MESSAGE},
        {key: 'dlqMessage', label: t.DLQ_MESSAGE},
        {key: 'messageTrace', label: t.MESSAGETRACE},
        ...(capabilities.supportsAcl2 && showAcl ? [{key: 'acl', label: t.ACL_MANAGEMENT}] : []),
    ];

    // Determine if it's a small screen (e.g., less than md)
    const isSmallScreen = !screens.md;
    // Determine if it's an extra small screen (e.g., less than sm)
    const isExtraSmallScreen = !screens.sm;

    return (
        <Header
            className="navbar"
            style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                padding: isExtraSmallScreen ? '0 16px' : '0 24px', // Smaller padding on extra small screens
            }}
        >
            <div className="navbar-left" style={{display: 'flex', alignItems: 'center'}}>
                <div
                    style={{
                        fontWeight: 'bold',
                        marginRight: isSmallScreen ? '16px' : '24px', // Adjust margin on small screens
                        whiteSpace: 'nowrap', // Prevent text wrapping
                        flexShrink: 0, // Prevent shrinking in flex container
                        color: 'white', // Title text color also set to white
                        fontSize: isSmallScreen ? '14px' : '18px',
                    }}
                >
                    {t.TITLE}
                </div>

                {!isSmallScreen && ( // Display full menu on large screens
                    <Menu
                        onClick={handleMenuClick}
                        selectedKeys={[getPath()]}
                        mode="horizontal"
                        items={menuItems}
                        theme="dark" // Use dark theme to match Header background
                        style={{flex: 1, minWidth: 0}} // Allow menu items to adapt width
                    />
                )}
            </div>

            <Space size={isExtraSmallScreen ? 8 : 16}> {/* Adjust spacing for buttons */}
                {/* Command Bar trigger button */}
                <Tooltip title="搜索 / AI 助手 (⌘K)">
                    <Button
                        icon={<SearchOutlined />}
                        size="small"
                        type="text"
                        style={{ color: 'white' }}
                        onClick={() => setCommandBarOpen(true)}
                    >
                        {!isExtraSmallScreen && (
                            <span style={{ opacity: 0.85, fontSize: '12px' }}>⌘K</span>
                        )}
                    </Button>
                </Tooltip>

                {/* AI助手设置按钮 */}
                <Tooltip title="AI助手配置">
                    <Button
                        icon={<RobotOutlined />}
                        size="small"
                        type="text"
                        style={{ color: 'white' }}
                        onClick={() => navigate('/llm-settings')}
                    >
                        {!isExtraSmallScreen && 'AI配置'}
                    </Button>
                </Tooltip>

                {/* Theme switch button */}
                <Dropdown overlay={themeMenu}>
                    <Button icon={<BgColorsOutlined/>} size="small">
                        {!isExtraSmallScreen && `${t.TOPIC}: ${currentThemeName}`}
                        <DownOutlined/>
                    </Button>
                </Dropdown>
                <Dropdown overlay={langMenu}>
                    <Button icon={<GlobalOutlined/>} size="small">
                        {!isExtraSmallScreen && t.CHANGE_LANG} {/* Hide text on extra small screens */}
                        <DownOutlined/>
                    </Button>
                </Dropdown>

                {userName && (
                    <Dropdown overlay={userMenu}>
                        {/* 使用一个可点击的元素作为 Dropdown 的唯一子元素 */}
                        <a onClick={e => e.preventDefault()} style={{display: 'flex', alignItems: 'center'}}>
                            <UserOutlined style={{marginRight: 8}}/> {/* 添加一些间距 */}
                            {userName}
                            <DownOutlined style={{marginLeft: 8}}/>
                        </a>
                    </Dropdown>
                )}

                {isSmallScreen && ( // Display hamburger icon on small screens
                    <Button
                        type="primary"
                        icon={<MenuOutlined/>}
                        onClick={() => setDrawerVisible(true)}
                        style={{marginLeft: isExtraSmallScreen ? 8 : 16}} // Adjust margin for hamburger icon
                    />
                )}
            </Space>

            {/* Modify Drawer and Menu components here */}
            <Drawer
                // Default Drawer background color is white. If you need to change the Drawer's own background color, set it additionally
                // or set a dark background in bodyStyle, then let Menu override it
                title={t.MENU} // Drawer title
                placement="left" // Drawer pops out from the left
                onClose={() => setDrawerVisible(false)}
                open={drawerVisible}
                // If you want the Drawer's background to match the Menu's background color, you can set bodyStyle like this
                // or set components.Drawer.colorBgElevated in theme.js, etc.
                bodyStyle={{padding: 0, backgroundColor: '#1c324a'}} // Set Drawer body background to dark
                width={200} // Set drawer width
            >
                <Menu
                    onClick={handleMenuClick}
                    selectedKeys={[getPath()]}
                    mode="inline" // Use vertical menu in drawer
                    items={menuItems}
                    theme="dark"
                    style={{height: '100%', borderRight: 0}} // Ensure menu fills the drawer
                />
            </Drawer>
        </Header>
    );
};

export default Navbar;
