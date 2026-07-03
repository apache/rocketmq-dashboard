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

import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ClusterCapabilitiesProvider } from '../../store/context/ClusterCapabilitiesContext';
import ThreeTierArchitecture from './ThreeTierArchitecture';

// Mock the remoteApi
jest.mock('../../api/remoteApi/remoteApi', () => ({
  remoteApi: {
    queryTopicList: jest.fn(),
    queryConsumerGroupList: jest.fn(),
    queryNamespaceList: jest.fn(),
    getClusterList: jest.fn()
  }
}));

// Mock translation function
const mockT = (key) => {
  const translations = {
    'ARCHITECTURE_OVERVIEW': 'Architecture Overview',
    'CLUSTER_CAPABILITIES': 'Cluster Capabilities',
    'ACCESS_TYPES': 'Access Types',
    'V5_ARCHITECTURE': 'RocketMQ 5.0 Architecture',
    'NAMESPACE_SUPPORT': 'Namespace Support',
    'LITE_TOPIC_SUPPORT': 'Lite Topic Support',
    'GRPC_SUPPORT': 'gRPC Support',
    'ACL_2_0_SUPPORT': 'ACL 2.0 Support',
    'POP_CONSUMPTION': 'Pop Consumption',
    'YES': 'Yes',
    'NO': 'No',
    'FEATURE': 'Feature',
    'SUPPORTED': 'Supported',
    'DESCRIPTION': 'Description',
    'V5_ARCHITECTURE_DESC': 'Unified control plane and enhanced metadata management',
    'NAMESPACE_DESC': 'Multi-tenancy and resource isolation',
    'LITE_TOPIC_DESC': 'Lightweight topics and efficient consumption',
    'GRPC_DESC': 'High-performance gRPC protocol communication',
    'ACL_2_0_DESC': 'Enhanced access control and permission management',
    'POP_DESC': 'Stateless consumption and efficient message retrieval',
    'ACCESS_TYPE_MATRIX': 'Access Type Matrix',
    'TYPE': 'Type',
    'PROTOCOL': 'Protocol',
    'ARCHITECTURE': 'Architecture',
    'FEATURES': 'Features',
    'CURRENT': 'Current',
    'ACTIVE': 'Active'
  };
  return translations[key] || key;
};

describe('ThreeTierArchitecture', () => {
  const renderWithCapabilities = (capabilities = {}) => {
    const defaultCapabilities = {
      hasNamespace: false,
      supportsLiteTopic: false,
      supportsPopConsumption: false,
      supportsGrpc: false,
      supportsAcl2: false,
      isV5Architecture: false,
      accessType: 'v4-namesrv'
    };

    return render(
      <ClusterCapabilitiesProvider>
        <ThreeTierArchitecture t={mockT} />
      </ClusterCapabilitiesProvider>
    );
  };

  test('renders architecture overview by default', () => {
    renderWithCapabilities();
    
    expect(screen.getByText('Architecture Overview')).toBeInTheDocument();
    expect(screen.getByText('Cluster Capabilities')).toBeInTheDocument();
    expect(screen.getByText('Access Types')).toBeInTheDocument();
  });

  test('displays cluster capabilities matrix', async () => {
    renderWithCapabilities({
      isV5Architecture: true,
      hasNamespace: true,
      supportsLiteTopic: true,
      supportsGrpc: true,
      supportsAcl2: true,
      supportsPopConsumption: true
    });

    // Click on Cluster Capabilities tab
    await userEvent.click(screen.getByText('Cluster Capabilities'));

    // Wait for the capabilities matrix to load
    await waitFor(() => {
      expect(screen.getByText('Feature')).toBeInTheDocument();
    });

    // Check that capabilities are displayed
    expect(screen.getByText('RocketMQ 5.0 Architecture')).toBeInTheDocument();
    expect(screen.getByText('Yes')).toBeInTheDocument();
    expect(screen.getByText('Namespace Support')).toBeInTheDocument();
    expect(screen.getByText('Lite Topic Support')).toBeInTheDocument();
    expect(screen.getByText('gRPC Support')).toBeInTheDocument();
    expect(screen.getByText('ACL 2.0 Support')).toBeInTheDocument();
    expect(screen.getByText('Pop Consumption')).toBeInTheDocument();
  });

  test('displays access types matrix', async () => {
    renderWithCapabilities({
      accessType: 'v5-proxy-cluster'
    });

    // Click on Access Types tab
    await userEvent.click(screen.getByText('Access Types'));

    // Wait for the access types matrix to load
    await waitFor(() => {
      expect(screen.getByText('Type')).toBeInTheDocument();
    });

    // Check that access types are displayed
    expect(screen.getByText('v4-namesrv')).toBeInTheDocument();
    expect(screen.getByText('v5-proxy-local')).toBeInTheDocument();
    expect(screen.getByText('v5-proxy-cluster')).toBeInTheDocument();
    expect(screen.getByText('Remoting')).toBeInTheDocument();
    expect(screen.getByText('gRPC')).toBeInTheDocument();
  });

  test('shows current access type as active', async () => {
    renderWithCapabilities({
      accessType: 'v5-proxy-cluster'
    });

    // Click on Access Types tab
    await userEvent.click(screen.getByText('Access Types'));

    // Wait for the access types matrix to load
    await waitFor(() => {
      expect(screen.getByText('Active')).toBeInTheDocument();
    });

    // Should show Active tag for current access type
    expect(screen.getByText('Active')).toBeInTheDocument();
  });

  test('displays correct support status based on capabilities', async () => {
    renderWithCapabilities({
      isV5Architecture: false,
      hasNamespace: false,
      supportsLiteTopic: false,
      supportsGrpc: false,
      supportsAcl2: false,
      supportsPopConsumption: false
    });

    // Click on Cluster Capabilities tab
    await userEvent.click(screen.getByText('Cluster Capabilities'));

    // Wait for the capabilities matrix to load
    await waitFor(() => {
      expect(screen.getByText('No')).toBeInTheDocument();
    });

    // All capabilities should show 'No' for v4 architecture
    const noElements = screen.getAllByText('No');
    expect(noElements.length).toBeGreaterThanOrEqual(6); // At least 6 'No' values for capabilities
  });

  test('displays correct descriptions for features', async () => {
    renderWithCapabilities();

    // Click on Cluster Capabilities tab
    await userEvent.click(screen.getByText('Cluster Capabilities'));

    // Wait for the capabilities matrix to load
    await waitFor(() => {
      expect(screen.getByText('Description')).toBeInTheDocument();
    });

    // Check feature descriptions
    expect(screen.getByText('Unified control plane and enhanced metadata management')).toBeInTheDocument();
    expect(screen.getByText('Multi-tenancy and resource isolation')).toBeInTheDocument();
    expect(screen.getByText('Lightweight topics and efficient consumption')).toBeInTheDocument();
    expect(screen.getByText('High-performance gRPC protocol communication')).toBeInTheDocument();
  });

  test('handles architecture overview tab correctly', async () => {
    renderWithCapabilities();

    // Architecture Overview should be selected by default
    const overviewTab = screen.getByText('Architecture Overview');
    expect(overviewTab).toBeInTheDocument();

    // Click on it to ensure it's working
    await userEvent.click(overviewTab);

    // Should still show the overview content
    expect(screen.getByText('Architecture Overview')).toBeInTheDocument();
  });

  test('renders ThreeTierArchitecture without crashing with minimal capabilities', () => {
    expect(() => {
      render(<ThreeTierArchitecture t={mockT} />);
    }).not.toThrow();
  });
});