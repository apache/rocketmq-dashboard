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

import React, { useEffect } from 'react';
import { Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { AnimatePresence, motion } from 'framer-motion';
import Login from '../pages/Login/login';
import Ops from '../pages/Ops/ops';
import ProxyCluster from '../pages/Proxy/ProxyCluster';
import Cluster from '../pages/Cluster/cluster';
import Topic from '../pages/Topic/topic';
import Consumer from '../pages/Consumer/consumer';
import Producer from '../pages/Producer/producer';
import Message from '../pages/Message/message';
import DlqMessage from '../pages/DlqMessage/dlqmessage';
import MessageTrace from '../pages/MessageTrace/messagetrace';
import Acl from '../pages/Acl/acl';
import AlertManagement from '../pages/Alert/AlertManagement';
import LlmSettings from '../pages/LlmSettings/LlmSettings';
import DashboardPage from '../pages/Dashboard/DashboardPage';

// 新页面
import HomePage from '../pages/Home/HomePage';
import GroupManagement from '../pages/GroupManagement/GroupManagement';
import BrokerCluster from '../pages/BrokerCluster/BrokerCluster';
import LiteTopic from '../pages/LiteTopic/LiteTopic';
import SslSettings from '../pages/SslSettings/SslSettings';

import StudioLayout from '../components/StudioLayout/StudioLayout';
import { remoteApi } from '../api/remoteApi/remoteApi';

const pageVariants = {
    initial: { opacity: 0, y: 8 },
    in: { opacity: 1, y: 0 },
    out: { opacity: 0, y: -8 },
};

const pageTransition = {
    type: 'tween',
    ease: 'easeInOut',
    duration: 0.2,
};

const AppRouter = () => {
    const navigate = useNavigate();
    const location = useLocation();

    useEffect(() => {
        remoteApi.setRedirectHandler(() => {
            navigate('/login', { replace: true });
        });
    }, [navigate]);

    // 登录页不使用 StudioLayout
    if (location.pathname === '/login') {
        return (
            <Routes>
                <Route path="/login" element={<Login />} />
            </Routes>
        );
    }

    return (
        <StudioLayout>
            <AnimatePresence mode="wait">
                <Routes location={location} key={location.pathname}>
                    <Route
                        path="/"
                        element={
                            <motion.div variants={pageVariants} initial="initial" animate="in" exit="out" transition={pageTransition}>
                                <HomePage />
                            </motion.div>
                        }
                    />
                    <Route
                        path="/home"
                        element={
                            <motion.div variants={pageVariants} initial="initial" animate="in" exit="out" transition={pageTransition}>
                                <HomePage />
                            </motion.div>
                        }
                    />
                    <Route
                        path="/consumer"
                        element={
                            <motion.div variants={pageVariants} initial="initial" animate="in" exit="out" transition={pageTransition}>
                                <GroupManagement />
                            </motion.div>
                        }
                    />
                    <Route
                        path="/cluster"
                        element={
                            <motion.div variants={pageVariants} initial="initial" animate="in" exit="out" transition={pageTransition}>
                                <BrokerCluster />
                            </motion.div>
                        }
                    />
                    <Route
                        path="/ops"
                        element={
                            <motion.div variants={pageVariants} initial="initial" animate="in" exit="out" transition={pageTransition}>
                                <Ops />
                            </motion.div>
                        }
                    />
                    <Route
                        path="/proxy"
                        element={
                            <motion.div variants={pageVariants} initial="initial" animate="in" exit="out" transition={pageTransition}>
                                <ProxyCluster />
                            </motion.div>
                        }
                    />
                    <Route
                        path="/topic"
                        element={
                            <motion.div variants={pageVariants} initial="initial" animate="in" exit="out" transition={pageTransition}>
                                <Topic />
                            </motion.div>
                        }
                    />
                    <Route
                        path="/liteTopic"
                        element={
                            <motion.div variants={pageVariants} initial="initial" animate="in" exit="out" transition={pageTransition}>
                                <LiteTopic />
                            </motion.div>
                        }
                    />
                    <Route
                        path="/producer"
                        element={
                            <motion.div variants={pageVariants} initial="initial" animate="in" exit="out" transition={pageTransition}>
                                <Producer />
                            </motion.div>
                        }
                    />
                    <Route
                        path="/message"
                        element={
                            <motion.div variants={pageVariants} initial="initial" animate="in" exit="out" transition={pageTransition}>
                                <Message />
                            </motion.div>
                        }
                    />
                    <Route
                        path="/dlqMessage"
                        element={
                            <motion.div variants={pageVariants} initial="initial" animate="in" exit="out" transition={pageTransition}>
                                <DlqMessage />
                            </motion.div>
                        }
                    />
                    <Route
                        path="/messageTrace"
                        element={
                            <motion.div variants={pageVariants} initial="initial" animate="in" exit="out" transition={pageTransition}>
                                <MessageTrace />
                            </motion.div>
                        }
                    />
                    <Route
                        path="/acl"
                        element={
                            <motion.div variants={pageVariants} initial="initial" animate="in" exit="out" transition={pageTransition}>
                                <Acl />
                            </motion.div>
                        }
                    />
                    <Route
                        path="/alert"
                        element={
                            <motion.div variants={pageVariants} initial="initial" animate="in" exit="out" transition={pageTransition}>
                                <AlertManagement />
                            </motion.div>
                        }
                    />
                    <Route
                        path="/dashboard"
                        element={
                            <motion.div variants={pageVariants} initial="initial" animate="in" exit="out" transition={pageTransition}>
                                <DashboardPage />
                            </motion.div>
                        }
                    />
                    <Route
                        path="/llm-settings"
                        element={
                            <motion.div variants={pageVariants} initial="initial" animate="in" exit="out" transition={pageTransition}>
                                <LlmSettings />
                            </motion.div>
                        }
                    />
                    <Route
                        path="/ssl-settings"
                        element={
                            <motion.div variants={pageVariants} initial="initial" animate="in" exit="out" transition={pageTransition}>
                                <SslSettings />
                            </motion.div>
                        }
                    />
                    <Route path="*" element={<Navigate to="/" />} />
                </Routes>
            </AnimatePresence>
        </StudioLayout>
    );
};

export default AppRouter;