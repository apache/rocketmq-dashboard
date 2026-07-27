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
import { ClusterCapabilitiesProvider, useClusterCapabilities } from './ClusterCapabilitiesContext';
import { remoteApi } from '../../api/remoteApi/remoteApi';

// Mock the remoteApi
jest.mock('../../api/remoteApi/remoteApi', () => ({
  remoteApi: {
    getArchitectureInfo: jest.fn()
  }
}));

// Test component to use the context
const TestComponent = () => {
  const { capabilities, selectedCluster, loading, selectCluster } = useClusterCapabilities();

  return (
    <div>
      <div data-testid="selected-cluster">{selectedCluster || 'none'}</div>
      <div data-testid="loading">{loading.toString()}</div>
      <div data-testid="has-namespace">{capabilities.hasNamespace ? 'true' : 'false'}</div>
      <div data-testid="supports-grpc">{capabilities.supportsGrpc ? 'true' : 'false'}</div>
      <div data-testid="is-v5">{capabilities.isV5Architecture ? 'true' : 'false'}</div>
      <div data-testid="access-type">{capabilities.accessType}</div>
      <div data-testid="supports-route">{capabilities.supportsRouteEvents ? 'true' : 'false'}</div>
      <button onClick={() => selectCluster('test-cluster')}>Select Cluster</button>
    </div>
  );
};

// Backend ClusterCapability DTO shape (field names differ from UI flags).
const mockInfoV5 = {
  accessType: 'V5_PROXY_CLUSTER',
  capabilities: {
    architectureVersion: '5.0',
    namespaceSupported: true,
    liteTopicSupported: true,
    popConsumeSupported: true,
    grpcClientSupported: true,
    aclV2Supported: true,
    routeEventsSupported: true,
  }
};

describe('ClusterCapabilitiesContext', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  test('should provide default (V4) capabilities when info is empty', async () => {
    remoteApi.getArchitectureInfo.mockResolvedValue(null);

    render(
      <ClusterCapabilitiesProvider>
        <TestComponent />
      </ClusterCapabilitiesProvider>
    );

    await waitFor(() => {
      expect(screen.getByTestId('loading')).toHaveTextContent('false');
    });
    expect(screen.getByTestId('selected-cluster')).toHaveTextContent('none');
    expect(screen.getByTestId('has-namespace')).toHaveTextContent('false');
    expect(screen.getByTestId('supports-grpc')).toHaveTextContent('false');
    expect(screen.getByTestId('is-v5')).toHaveTextContent('false');
    expect(screen.getByTestId('access-type')).toHaveTextContent('V4_NAMESRV');
    expect(screen.getByTestId('supports-route')).toHaveTextContent('false');
  });

  test('should render loading state when fetching capabilities', async () => {
    remoteApi.getArchitectureInfo.mockImplementation(() =>
      new Promise(resolve => setTimeout(() => resolve(mockInfoV5), 100))
    );

    render(
      <ClusterCapabilitiesProvider>
        <TestComponent />
      </ClusterCapabilitiesProvider>
    );

    // Initially should be loading (fetch kicks off on mount)
    expect(screen.getByTestId('loading')).toHaveTextContent('true');

    await waitFor(() => {
      expect(screen.getByTestId('loading')).toHaveTextContent('false');
    });
  });

  test('should update capabilities from architecture info', async () => {
    remoteApi.getArchitectureInfo.mockResolvedValue(mockInfoV5);

    render(
      <ClusterCapabilitiesProvider>
        <TestComponent />
      </ClusterCapabilitiesProvider>
    );

    await waitFor(() => {
      expect(screen.getByTestId('loading')).toHaveTextContent('false');
    });

    expect(screen.getByTestId('has-namespace')).toHaveTextContent('true');
    expect(screen.getByTestId('supports-grpc')).toHaveTextContent('true');
    expect(screen.getByTestId('is-v5')).toHaveTextContent('true');
    expect(screen.getByTestId('access-type')).toHaveTextContent('V5_PROXY_CLUSTER');
    expect(screen.getByTestId('supports-route')).toHaveTextContent('true');

    // Verify API was called
    expect(remoteApi.getArchitectureInfo).toHaveBeenCalled();
  });

  test('should fallback to default capabilities when API fails', async () => {
    remoteApi.getArchitectureInfo.mockRejectedValue(new Error('API Error'));

    render(
      <ClusterCapabilitiesProvider>
        <TestComponent />
      </ClusterCapabilitiesProvider>
    );

    await waitFor(() => {
      expect(screen.getByTestId('loading')).toHaveTextContent('false');
    });

    expect(screen.getByTestId('has-namespace')).toHaveTextContent('false');
    expect(screen.getByTestId('supports-grpc')).toHaveTextContent('false');
    expect(screen.getByTestId('is-v5')).toHaveTextContent('false');
    expect(screen.getByTestId('access-type')).toHaveTextContent('V4_NAMESRV');
  });

  test('should throw error when useClusterCapabilities is used outside provider', () => {
    const originalError = console.error;
    console.error = jest.fn();

    expect(() => {
      render(<TestComponent />);
    }).toThrow('useClusterCapabilities must be used within a ClusterCapabilitiesProvider');

    console.error = originalError;
  });
});
