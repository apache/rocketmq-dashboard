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

import React, {createContext, useContext, useState, useEffect} from 'react';
import {remoteApi} from '../../api/remoteApi/remoteApi';

const ClusterCapabilitiesContext = createContext();

export const useClusterCapabilities = () => {
    const context = useContext(ClusterCapabilitiesContext);
    if (!context) {
        throw new Error('useClusterCapabilities must be used within a ClusterCapabilitiesProvider');
    }
    return context;
};

// Map the backend ClusterCapability DTO (returned by /api/architecture/info)
// into the UI capability flags consumed across the app.
const mapCapabilities = (info) => {
    const cap = (info && info.capabilities) || {};
    const accessType = (info && info.accessType) || 'V4_NAMESRV';
    const isV5 = accessType.indexOf('V5') >= 0 || String(cap.architectureVersion || '').startsWith('5');
    return {
        hasNamespace: !!cap.namespaceSupported,
        supportsLiteTopic: !!cap.liteTopicSupported,
        supportsPopConsumption: !!cap.popConsumeSupported,
        supportsGrpc: !!cap.grpcClientSupported,
        supportsAcl2: !!cap.aclV2Supported,
        supportsRouteEvents: !!cap.routeEventsSupported,
        isV5Architecture: isV5,
        accessType: accessType,
        architectureVersion: cap.architectureVersion || '4.0'
    };
};

export const ClusterCapabilitiesProvider = ({children}) => {
    const [capabilities, setCapabilities] = useState({
        hasNamespace: false,
        supportsLiteTopic: false,
        supportsPopConsumption: false,
        supportsGrpc: false,
        supportsAcl2: false,
        supportsRouteEvents: false,
        isV5Architecture: false,
        accessType: 'V4_NAMESRV' // default
    });
    const [selectedCluster, setSelectedCluster] = useState(null);
    const [loading, setLoading] = useState(false);

    // Fetch cluster capabilities from the authoritative architecture endpoint.
    // Architecture is a cluster-wide (global) setting in this dashboard, so the
    // optional clusterName argument is accepted for API compatibility but the
    // global /api/architecture/info is the real source of truth.
    const fetchCapabilities = async () => {
        setLoading(true);
        try {
            const info = await remoteApi.getArchitectureInfo();
            if (info) {
                setCapabilities(mapCapabilities(info));
            } else {
                throw new Error('empty architecture info');
            }
        } catch (error) {
            console.error('Failed to fetch cluster capabilities:', error);
            // Fallback to default capabilities for v4
            setCapabilities({
                hasNamespace: false,
                supportsLiteTopic: false,
                supportsPopConsumption: false,
                supportsGrpc: false,
                supportsAcl2: false,
                supportsRouteEvents: false,
                isV5Architecture: false,
                accessType: 'V4_NAMESRV'
            });
        } finally {
            setLoading(false);
        }
    };

    // Refresh on mount so the navbar reflects the current architecture even
    // before a cluster is explicitly selected.
    useEffect(() => {
        fetchCapabilities();
    }, []);

    // Update cluster selection and refresh capabilities
    const selectCluster = async (clusterName) => {
        setSelectedCluster(clusterName);
        await fetchCapabilities();
    };

    // Switch architecture type and refresh capabilities
    const switchArchitecture = async (request) => {
        try {
            const result = await remoteApi.switchArchitecture(request);
            if (result && result.success) {
                // Refresh capabilities after successful switch
                await fetchCapabilities();
            }
            return result;
        } catch (error) {
            console.error('Failed to switch architecture:', error);
            return { success: false, error: error.message };
        }
    };

    const value = {
        capabilities,
        selectedCluster,
        loading,
        selectCluster,
        refreshCapabilities: () => fetchCapabilities(),
        switchArchitecture
    };

    return (
        <ClusterCapabilitiesContext.Provider value={value}>
            {children}
        </ClusterCapabilitiesContext.Provider>
    );
};