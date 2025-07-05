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

import React, {useEffect} from 'react';
import {HashRouter as Router, Navigate, Route, Routes, useLocation, useNavigate} from 'react-router-dom';
import {Layout} from 'antd';
import {AnimatePresence, motion} from 'framer-motion';
import Login from '../pages/Login/login';
import Ops from '../pages/Ops/ops';
import Proxy from '../pages/Proxy/proxy';
import Cluster from '../pages/Cluster/cluster';
import Topic from '../pages/Topic/topic';
import Consumer from '../pages/Consumer/consumer';
import Producer from '../pages/Producer/producer';
import Message from '../pages/Message/message';
import DlqMessage from '../pages/DlqMessage/dlqmessage';
import MessageTrace from '../pages/MessageTrace/messagetrace';
import Acl from '../pages/Acl/acl';

import Navbar from '../components/Navbar';
import DashboardPage from "../pages/Dashboard/DashboardPage";
import {remoteApi} from "../api/remoteApi/remoteApi";

const {Header, Content} = Layout;

const pageVariants = {
    initial: {
        opacity: 0,
        x: "-100vw"
    },
    in: {
        opacity: 1,
        x: 0
    },
    out: {
        opacity: 0,
        x: "100vw"
    }
};

const pageTransition = {
    type: "tween",
    ease: "anticipate",
    duration: 0.2
};

const AppRouter = () => {
    const navigate = useNavigate();
    const location = useLocation();

    useEffect(() => {
        remoteApi.setRedirectHandler(() => {
            navigate('/login', {replace: true});
        });
    }, [navigate]);

    return (
        <Layout style={{minHeight: '100vh'}}>
            <Header style={{padding: 0, height: 'auto', lineHeight: 'normal'}}>
                <Navbar/>
            </Header>

            <Content style={{padding: '24px'}}>
                <AnimatePresence mode="wait">
                    <Routes location={location} key={location.pathname}>
                        <Route
                            path="/login"
                            element={
                                <motion.div
                                    variants={pageVariants}
                                    initial="initial"
                                    animate="in"
                                    exit="out"
                                    transition={pageTransition}
                                >
                                    <Login/>
                                </motion.div>
                            }
                        />
                        <Route
                            path="/"
                            element={
                                <motion.div
                                    variants={pageVariants}
                                    initial="initial"
                                    animate="in"
                                    exit="out"
                                    transition={pageTransition}
                                >
                                    <DashboardPage/>
                                </motion.div>
                            }
                        />
                        <Route
                            path="/ops"
                            element={
                                <motion.div
                                    variants={pageVariants}
                                    initial="initial"
                                    animate="in"
                                    exit="out"
                                    transition={pageTransition}
                                >
                                    <Ops/>
                                </motion.div>
                            }
                        />
                        <Route
                            path="/proxy"
                            element={
                                <motion.div
                                    variants={pageVariants}
                                    initial="initial"
                                    animate="in"
                                    exit="out"
                                    transition={pageTransition}
                                >
                                    <Proxy/>
                                </motion.div>
                            }
                        />
                        <Route
                            path="/cluster"
                            element={
                                <motion.div
                                    variants={pageVariants}
                                    initial="initial"
                                    animate="in"
                                    exit="out"
                                    transition={pageTransition}
                                >
                                    <Cluster/>
                                </motion.div>
                            }
                        />
                        <Route
                            path="/topic"
                            element={
                                <motion.div
                                    variants={pageVariants}
                                    initial="initial"
                                    animate="in"
                                    exit="out"
                                    transition={pageTransition}
                                >
                                    <Topic/>
                                </motion.div>
                            }
                        />
                        <Route
                            path="/consumer"
                            element={
                                <motion.div
                                    variants={pageVariants}
                                    initial="initial"
                                    animate="in"
                                    exit="out"
                                    transition={pageTransition}
                                >
                                    <Consumer/>
                                </motion.div>
                            }
                        />
                        <Route
                            path="/producer"
                            element={
                                <motion.div
                                    variants={pageVariants}
                                    initial="initial"
                                    animate="in"
                                    exit="out"
                                    transition={pageTransition}
                                >
                                    <Producer/>
                                </motion.div>
                            }
                        />
                        <Route
                            path="/message"
                            element={
                                <motion.div
                                    variants={pageVariants}
                                    initial="initial"
                                    animate="in"
                                    exit="out"
                                    transition={pageTransition}
                                >
                                    <Message/>
                                </motion.div>
                            }
                        />
                        <Route
                            path="/dlqMessage"
                            element={
                                <motion.div
                                    variants={pageVariants}
                                    initial="initial"
                                    animate="in"
                                    exit="out"
                                    transition={pageTransition}
                                >
                                    <DlqMessage/>
                                </motion.div>
                            }
                        />
                        <Route
                            path="/messageTrace"
                            element={
                                <motion.div
                                    variants={pageVariants}
                                    initial="initial"
                                    animate="in"
                                    exit="out"
                                    transition={pageTransition}
                                >
                                    <MessageTrace/>
                                </motion.div>
                            }
                        />
                        <Route
                            path="/acl"
                            element={
                                <motion.div
                                    variants={pageVariants}
                                    initial="initial"
                                    animate="in"
                                    exit="out"
                                    transition={pageTransition}
                                >
                                    <Acl/>
                                </motion.div>
                            }
                        />
                        <Route path="*" element={<Navigate to="/"/>}/>
                    </Routes>
                </AnimatePresence>
            </Content>
        </Layout>
    );
};

const AppWrapper = () => (
    <Router>
        <AppRouter/>
    </Router>
);

export default AppWrapper;
