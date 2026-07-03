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
import { render, screen, fireEvent } from '@testing-library/react';
import { ClusterCapabilitiesProvider } from '../../store/context/ClusterCapabilitiesContext';
import { TopicTypeFilter, TopicOperations } from './CapabilityAwareTopic';

// Mock translation function
const mockT = (key) => {
  const translations = {
    'NORMAL': 'Normal',
    'RETRY': 'Retry',
    'DLQ': 'DLQ',
    'SYSTEM': 'System',
    'DELAY': 'Delay',
    'FIFO': 'FIFO',
    'TRANSACTION': 'Transaction',
    'LITE_TOPIC': 'Lite Topic',
    'STATUS': 'Status',
    'ROUTER': 'Router',
    'MANAGE': 'Manage',
    'CONFIG': 'Config',
    'SEND_MSG': 'Send Message',
    'RESET_CUS_OFFSET': 'Reset Offset',
    'SKIP_MESSAGE_ACCUMULATE': 'Skip Accumulate',
    'LITE_CONFIG': 'Lite Config',
    'GRPC_METRICS': 'gRPC Metrics',
    'DELETE': 'Delete'
  };
  return translations[key] || key;
};

// Test wrapper with capabilities provider
const TestWrapper = ({ children, capabilities = {} }) => {
  const defaultCapabilities = {
    hasNamespace: false,
    supportsLiteTopic: false,
    supportsPopConsumption: false,
    supportsGrpc: false,
    supportsAcl2: false,
    isV5Architecture: false,
    accessType: 'v4-namesrv',
    ...capabilities
  };

  return (
    <ClusterCapabilitiesProvider>
      {React.cloneElement(children, { capabilities: defaultCapabilities })}
    </ClusterCapabilitiesProvider>
  );
};

describe('CapabilityAwareTopic Components', () => {
  describe('TopicTypeFilter', () => {
    test('should render basic topic type filters for v4 architecture', () => {
      const mockCapabilities = {
        isV5Architecture: false
      };

      render(
        <TestWrapper capabilities={mockCapabilities}>
          <TopicTypeFilter t={mockT} />
        </TestWrapper>
      );

      // Should show basic types
      expect(screen.getByLabelText('filterNormal')).toBeInTheDocument();
      expect(screen.getByLabelText('filterRetry')).toBeInTheDocument();
      expect(screen.getByLabelText('filterDLQ')).toBeInTheDocument();
      expect(screen.getByLabelText('filterSystem')).toBeInTheDocument();

      // Should NOT show advanced types for v4
      expect(screen.queryByLabelText('filterDelay')).not.toBeInTheDocument();
      expect(screen.queryByLabelText('filterFifo')).not.toBeInTheDocument();
      expect(screen.queryByLabelText('filterTransaction')).not.toBeInTheDocument();
      expect(screen.queryByLabelText('filterLite')).not.toBeInTheDocument();
    });

    test('should render advanced topic type filters for v5 architecture', () => {
      const mockCapabilities = {
        isV5Architecture: true,
        supportsLiteTopic: true
      };

      render(
        <TestWrapper capabilities={mockCapabilities}>
          <TopicTypeFilter t={mockT} />
        </TestWrapper>
      );

      // Should show basic types
      expect(screen.getByLabelText('filterNormal')).toBeInTheDocument();
      expect(screen.getByLabelText('filterRetry')).toBeInTheDocument();
      expect(screen.getByLabelText('filterDLQ')).toBeInTheDocument();
      expect(screen.getByLabelText('filterSystem')).toBeInTheDocument();

      // Should show advanced types for v5
      expect(screen.getByLabelText('filterDelay')).toBeInTheDocument();
      expect(screen.getByLabelText('filterFifo')).toBeInTheDocument();
      expect(screen.getByLabelText('filterTransaction')).toBeInTheDocument();
      
      // Should show Lite Topic filter when supported
      expect(screen.getByLabelText('filterLite')).toBeInTheDocument();
    });

    test('should NOT render lite topic filter when not supported', () => {
      const mockCapabilities = {
        isV5Architecture: true,
        supportsLiteTopic: false
      };

      render(
        <TestWrapper capabilities={mockCapabilities}>
          <TopicTypeFilter t={mockT} />
        </TestWrapper>
      );

      // Should NOT show Lite Topic filter
      expect(screen.queryByLabelText('filterLite')).not.toBeInTheDocument();
    });
  });

  describe('TopicOperations', () => {
    const mockOnAction = jest.fn();

    beforeEach(() => {
      mockOnAction.mockClear();
    });

    test('should render basic operations for all topics', () => {
      const mockCapabilities = {
        isV5Architecture: false
      };

      render(
        <TestWrapper capabilities={mockCapabilities}>
          <TopicOperations 
            topic="test-topic"
            isSystem={false}
            writeOperationEnabled={true}
            onAction={mockOnAction}
            t={mockT}
          />
        </TestWrapper>
      );

      // Always available operations
      expect(screen.getByText('Status')).toBeInTheDocument();
      expect(screen.getByText('Router')).toBeInTheDocument();
      expect(screen.getByText('Consumer Manage')).toBeInTheDocument();
      expect(screen.getByText('Topic Config')).toBeInTheDocument();
      expect(screen.getByText('Send Message')).toBeInTheDocument();
      expect(screen.getByText('Reset Offset')).toBeInTheDocument();
      expect(screen.getByText('Skip Accumulate')).toBeInTheDocument();
      expect(screen.getByText('Delete')).toBeInTheDocument();
    });

    test('should NOT render send message for system topics', () => {
      const mockCapabilities = {
        isV5Architecture: false
      };

      render(
        <TestWrapper capabilities={mockCapabilities}>
          <TopicOperations 
            topic="%SYS%test-topic"
            isSystem={true}
            writeOperationEnabled={true}
            onAction={mockOnAction}
            t={mockT}
          />
        </TestWrapper>
      );

      // Should NOT show send message for system topics
      expect(screen.queryByText('Send Message')).not.toBeInTheDocument();
    });

    test('should render Lite Topic operations when supported', () => {
      const mockCapabilities = {
        isV5Architecture: true,
        supportsLiteTopic: true
      };

      render(
        <TestWrapper capabilities={mockCapabilities}>
          <TopicOperations 
            topic="test-topic"
            isSystem={false}
            writeOperationEnabled={true}
            onAction={mockOnAction}
            t={mockT}
          />
        </TestWrapper>
      );

      // Should show Lite Topic config button
      expect(screen.getByText('Lite Config')).toBeInTheDocument();
    });

    test('should render gRPC operations when supported', () => {
      const mockCapabilities = {
        isV5Architecture: true,
        supportsGrpc: true
      };

      render(
        <TestWrapper capabilities={mockCapabilities}>
          <TopicOperations 
            topic="test-topic"
            isSystem={false}
            writeOperationEnabled={true}
            onAction={mockOnAction}
            t={mockT}
          />
        </TestWrapper>
      );

      // Should show gRPC metrics button
      expect(screen.getByText('gRPC Metrics')).toBeInTheDocument();
    });

    test('should NOT render advanced operations when write operations disabled', () => {
      const mockCapabilities = {
        isV5Architecture: true,
        supportsLiteTopic: true
      };

      render(
        <TestWrapper capabilities={mockCapabilities}>
          <TopicOperations 
            topic="test-topic"
            isSystem={false}
            writeOperationEnabled={false}
            onAction={mockOnAction}
            t={mockT}
          />
        </TestWrapper>
      );

      // Should NOT show write operations
      expect(screen.queryByText('Reset Offset')).not.toBeInTheDocument();
      expect(screen.queryByText('Skip Accumulate')).not.toBeInTheDocument();
      expect(screen.queryByText('Delete')).not.toBeInTheDocument();
      
      // Should still show read operations
      expect(screen.getByText('Status')).toBeInTheDocument();
      expect(screen.getByText('Router')).toBeInTheDocument();
    });

    test('should call onAction with correct parameters when buttons are clicked', () => {
      const mockCapabilities = {
        isV5Architecture: true,
        supportsLiteTopic: true
      };

      render(
        <TestWrapper capabilities={mockCapabilities}>
          <TopicOperations 
            topic="test-topic"
            isSystem={false}
            writeOperationEnabled={true}
            onAction={mockOnAction}
            t={mockT}
          />
        </TestWrapper>
      );

      // Click various action buttons
      fireEvent.click(screen.getByText('Status'));
      fireEvent.click(screen.getByText('Lite Config'));
      fireEvent.click(screen.getByText('Delete'));

      expect(mockOnAction).toHaveBeenCalledTimes(3);
      expect(mockOnAction).toHaveBeenCalledWith('stats', 'test-topic');
      expect(mockOnAction).toHaveBeenCalledWith('liteConfig', 'test-topic');
      expect(mockOnAction).toHaveBeenCalledWith('delete', 'test-topic');
    });
  });
});