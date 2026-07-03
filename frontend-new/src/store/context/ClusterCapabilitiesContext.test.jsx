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
    getClusterCapabilities: jest.fn()
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
      <button onClick={() => selectCluster('test-cluster')}>Select Cluster</button>
    </div>
  );
};

describe('ClusterCapabilitiesContext', () => {
  const mockCapabilities = {
    hasNamespace: true,
    supportsLiteTopic: true,
    supportsPopConsumption: true,
    supportsGrpc: true,
    supportsAcl2: true,
    isV5Architecture: true,
    accessType: 'v5-proxy-cluster'
  };

  beforeEach(() => {
    jest.clearAllMocks();
  });

  test('should provide default capabilities when no cluster is selected', () => {
    render(
      <ClusterCapabilitiesProvider>
        <TestComponent />
      </ClusterCapabilitiesProvider>
    );

    expect(screen.getByTestId('selected-cluster')).toHaveTextContent('none');
    expect(screen.getByTestId('has-namespace')).toHaveTextContent('false');
    expect(screen.getByTestId('supports-grpc')).toHaveTextContent('false');
    expect(screen.getByTestId('is-v5')).toHaveTextContent('false');
    expect(screen.getByTestId('access-type')).toHaveTextContent('v4-namesrv');
  });

  test('should render loading state when fetching capabilities', async () => {
    remoteApi.getClusterCapabilities.mockImplementation(() => 
      new Promise(resolve => setTimeout(() => resolve({ status: 0, data: mockCapabilities }), 100))
    );

    render(
      <ClusterCapabilitiesProvider>
        <TestComponent />
      </ClusterCapabilitiesProvider>
    );

    // Initially should be loading
    expect(screen.getByTestId('loading')).toHaveTextContent('true');

    // Click select cluster
    await userEvent.click(screen.getByText('Select Cluster'));

    // Wait for loading to complete
    await waitFor(() => {
      expect(screen.getByTestId('loading')).toHaveTextContent('false');
    });
  });

  test('should update capabilities when cluster is selected', async () => {
    remoteApi.getClusterCapabilities.mockResolvedValue({ 
      status: 0, 
      data: mockCapabilities 
    });

    render(
      <ClusterCapabilitiesProvider>
        <TestComponent />
      </ClusterCapabilitiesProvider>
    );

    // Click select cluster
    await userEvent.click(screen.getByText('Select Cluster'));

    // Wait for loading to complete and capabilities to be updated
    await waitFor(() => {
      expect(screen.getByTestId('loading')).toHaveTextContent('false');
    });

    expect(screen.getByTestId('has-namespace')).toHaveTextContent('true');
    expect(screen.getByTestId('supports-grpc')).toHaveTextContent('true');
    expect(screen.getByTestId('is-v5')).toHaveTextContent('true');
    expect(screen.getByTestId('access-type')).toHaveTextContent('v5-proxy-cluster');
    
    // Verify API was called with correct cluster name
    expect(remoteApi.getClusterCapabilities).toHaveBeenCalledWith('test-cluster');
  });

  test('should fallback to default capabilities when API fails', async () => {
    remoteApi.getClusterCapabilities.mockRejectedValue(new Error('API Error'));

    render(
      <ClusterCapabilitiesProvider>
        <TestComponent />
      </ClusterCapabilitiesProvider>
    );

    // Click select cluster
    await userEvent.click(screen.getByText('Select Cluster'));

    // Wait for error handling
    await waitFor(() => {
      expect(screen.getByTestId('loading')).toHaveTextContent('false');
    });

    // Should have default capabilities
    expect(screen.getByTestId('has-namespace')).toHaveTextContent('false');
    expect(screen.getByTestId('supports-grpc')).toHaveTextContent('false');
    expect(screen.getByTestId('is-v5')).toHaveTextContent('false');
    expect(screen.getByTestId('access-type')).toHaveTextContent('v4-namesrv');
  });

  test('should handle API error with status code', async () => {
    remoteApi.getClusterCapabilities.mockResolvedValue({ 
      status: 1, 
      errMsg: 'Cluster not found'
    });

    render(
      <ClusterCapabilitiesProvider>
        <TestComponent />
      </ClusterCapabilitiesProvider>
    );

    // Click select cluster
    await userEvent.click(screen.getByText('Select Cluster'));

    // Wait for error handling
    await waitFor(() => {
      expect(screen.getByTestId('selected-cluster')).toHaveTextContent('test-cluster');
    });

    // Should have default capabilities
    expect(screen.getByTestId('has-namespace')).toHaveTextContent('false');
    expect(screen.getByTestId('supports-grpc')).toHaveTextContent('false');
    expect(screen.getByTestId('is-v5')).toHaveTextContent('false');
    expect(screen.getByTestId('access-type')).toHaveTextContent('v4-namesrv');
  });

  test('should throw error when useClusterCapabilities is used outside provider', () => {
    // Suppress console error for this test
    const originalError = console.error;
    console.error = jest.fn();

    expect(() => {
      render(<TestComponent />);
    }).toThrow('useClusterCapabilities must be used within a ClusterCapabilitiesProvider');

    console.error = originalError;
  });
});