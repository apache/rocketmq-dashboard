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

export const ClusterCapabilitiesProvider = ({children}) => {
    const [capabilities, setCapabilities] = useState({
        hasNamespace: false,
        supportsLiteTopic: false,
        supportsPopConsumption: false,
        supportsGrpc: false,
        supportsAcl2: false,
        isV5Architecture: false,
        accessType: 'v4-namesrv' // default
    });
    const [selectedCluster, setSelectedCluster] = useState(null);
    const [loading, setLoading] = useState(false);

    // Fetch cluster capabilities from backend
    const fetchCapabilities = async (clusterName) => {
        if (!clusterName) return;

        setLoading(true);
        try {
            const response = await remoteApi.getClusterCapabilities(clusterName);
            if (response.status === 0 && response.data) {
                setCapabilities({
                    hasNamespace: response.data.hasNamespace || false,
                    supportsLiteTopic: response.data.supportsLiteTopic || false,
                    supportsPopConsumption: response.data.supportsPopConsumption || false,
                    supportsGrpc: response.data.supportsGrpc || false,
                    supportsAcl2: response.data.supportsAcl2 || false,
                    isV5Architecture: response.data.isV5Architecture || false,
                    accessType: response.data.accessType || 'v4-namesrv'
                });
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
                isV5Architecture: false,
                accessType: 'v4-namesrv'
            });
        } finally {
            setLoading(false);
        }
    };

    // Update cluster selection and fetch capabilities
    const selectCluster = async (clusterName) => {
        setSelectedCluster(clusterName);
        await fetchCapabilities(clusterName);
    };

    const value = {
        capabilities,
        selectedCluster,
        loading,
        selectCluster,
        refreshCapabilities: () => selectedCluster && fetchCapabilities(selectedCluster)
    };

    return (
        <ClusterCapabilitiesContext.Provider value={value}>
            {children}
        </ClusterCapabilitiesContext.Provider>
    );
};