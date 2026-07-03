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
import CapabilityDemo from './CapabilityDemo';

// Mock the ThreeTierArchitecture component since it's complex
jest.mock('../../components/architecture/ThreeTierArchitecture', () => {
  return function MockThreeTierArchitecture({ t }) {
    return (
      <div data-testid="three-tier-architecture">
        <h3>Mock Three Tier Architecture</h3>
        <p>Business Layer → MetadataProvider → AdminClient</p>
      </div>
    );
  };
});

// Mock the CapabilityAwareTopic component
jest.mock('../../components/topic/CapabilityAwareTopic', () => {
  return function MockCapabilityAwareTopic({ children, t }) {
    return (
      <div data-testid="capability-aware-topic">
        <h3>Mock CapabilityAwareTopic</h3>
        {children}
      </div>
    );
  };
});

describe('CapabilityDemo', () => {
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
        <CapabilityDemo />
      </ClusterCapabilitiesProvider>
    );
  };

  test('renders demo page title and description', () => {
    renderWithCapabilities();
    
    expect(screen.getByText('RIP-1 UI重构演示')).toBeInTheDocument();
    expect(screen.getByText(/此页面展示了RIP-1/)).toBeInTheDocument();
  });

  test('displays cluster information section', () => {
    renderWithCapabilities({
      selectedCluster: 'test-cluster',
      isV5Architecture: true,
      accessType: 'v5-proxy-cluster',
      hasNamespace: true,
      supportsLiteTopic: true,
      supportsGrpc: true,
      supportsAcl2: true,
      supportsPopConsumption: true
    });

    expect(screen.getByText('集群信息')).toBeInTheDocument();
    expect(screen.getByText('当前集群')).toBeInTheDocument();
    expect(screen.getByText('test-cluster')).toBeInTheDocument();
    expect(screen.getByText('架构版本')).toBeInTheDocument();
    expect(screen.getByText('RocketMQ 5.0')).toBeInTheDocument();
    expect(screen.getByText('访问类型')).toBeInTheDocument();
    expect(screen.getByText('Proxy集群模式')).toBeInTheDocument();
    expect(screen.getByText('支持特性')).toBeInTheDocument();
  });

  test('shows supported features correctly for v4 architecture', () => {
    renderWithCapabilities({
      isV5Architecture: false,
      accessType: 'v4-namesrv'
    });

    // Should show 架构版本 as RocketMQ 4.0
    expect(screen.getByText('RocketMQ 4.0')).toBeInTheDocument();
    
    // Should show 访问类型 as Remoting协议
    expect(screen.getByText('Remoting协议')).toBeInTheDocument();
    
    // Should NOT show 5.0 specific features
    expect(screen.queryByText('✓ 命名空间支持')).not.toBeInTheDocument();
    expect(screen.queryByText('✓ Lite Topic支持')).not.toBeInTheDocument();
  });

  test('shows supported features correctly for v5 architecture', () => {
    renderWithCapabilities({
      isV5Architecture: true,
      hasNamespace: true,
      supportsLiteTopic: true,
      supportsGrpc: true,
      supportsAcl2: true,
      supportsPopConsumption: true
    });

    // Should show all supported 5.0 features
    expect(screen.getByText('✓ 命名空间支持')).toBeInTheDocument();
    expect(screen.getByText('✓ Lite Topic支持')).toBeInTheDocument();
    expect(screen.getByText('✓ gRPC协议支持')).toBeInTheDocument();
    expect(screen.getByText('✓ ACL 2.0支持')).toBeInTheDocument();
    expect(screen.getByText('✓ Pop消费模式')).toBeInTheDocument();
  });

  test('displays feature matrix with correct support status', () => {
    renderWithCapabilities({
      isV5Architecture: true,
      hasNamespace: true,
      supportsLiteTopic: true,
      supportsGrpc: true,
      supportsAcl2: true,
      supportsPopConsumption: true
    });

    expect(screen.getByText('能力矩阵')).toBeInTheDocument();
    
    // Check feature names in matrix
    expect(screen.getByText('RocketMQ 5.0架构')).toBeInTheDocument();
    expect(screen.getByText('命名空间支持')).toBeInTheDocument();
    expect(screen.getByText('Lite Topic支持')).toBeInTheDocument();
    expect(screen.getByText('gRPC协议支持')).toBeInTheDocument();
    expect(screen.getByText('ACL 2.0支持')).toBeInTheDocument();
    expect(screen.getByText('Pop消费模式')).toBeInTheDocument();
    
    // Check support status
    expect(screen.getByText('是')).toBeInTheDocument();
  });

  test('displays feature matrix with correct unsupported status for v4', () => {
    renderWithCapabilities({
      isV5Architecture: false,
      hasNamespace: false,
      supportsLiteTopic: false,
      supportsGrpc: false,
      supportsAcl2: false,
      supportsPopConsumption: false
    });

    expect(screen.getByText('能力矩阵')).toBeInTheDocument();
    
    // Check that shows 'No' for unsupported features
    expect(screen.getByText('否')).toBeInTheDocument();
  });

  test('displays interactive demo section', () => {
    renderWithCapabilities();
    
    expect(screen.getByText('交互演示')).toBeInTheDocument();
    expect(screen.getByText('三层抽象架构前端适配')).toBeInTheDocument();
    expect(screen.getByText('能力集驱动UI')).toBeInTheDocument();
    expect(screen.getByTestId('three-tier-architecture')).toBeInTheDocument();
    expect(screen.getByTestId('capability-aware-topic')).toBeInTheDocument();
  });

  test('shows feature buttons with correct enabled/disabled states', () => {
    renderWithCapabilities({
      supportsLiteTopic: true,
      supportsGrpc: true,
      hasNamespace: false
    });

    // Should have buttons for supported features
    const liteTopicButton = screen.getByText('Lite Topic功能 ✓');
    const grpcButton = screen.getByText('gRPC监控 ✓');
    const namespaceButton = screen.getByText('Namespace管理 ✗');
    
    expect(liteTopicButton).toBeEnabled();
    expect(grpcButton).toBeEnabled();
    expect(namespaceButton).toBeDisabled();
  });

  test('shows feature buttons with all disabled for v4 architecture', () => {
    renderWithCapabilities({
      isV5Architecture: false,
      supportsLiteTopic: false,
      supportsGrpc: false,
      hasNamespace: false
    });

    // All advanced buttons should be disabled
    const liteTopicButton = screen.getByText('Lite Topic功能 ✗');
    const grpcButton = screen.getByText('gRPC监控 ✗');
    const namespaceButton = screen.getByText('Namespace管理 ✗');
    
    expect(liteTopicButton).toBeDisabled();
    expect(grpcButton).toBeDisabled();
    expect(namespaceButton).toBeDisabled();
  });

  test('shows tooltips for disabled buttons', () => {
    renderWithCapabilities({
      supportsLiteTopic: false
    });

    const liteTopicButton = screen.getByText('Lite Topic功能 ✗');
    expect(liteTopicButton).toBeDisabled();
    // Note: Tooltip content testing would require more complex setup
  });

  test('has refresh button for cluster information', () => {
    renderWithCapabilities();
    
    const refreshButton = screen.getByText('刷新能力信息');
    expect(refreshButton).toBeInTheDocument();
    expect(refreshButton).toHaveAttribute('type', 'primary');
  });

  test('displays feature descriptions in matrix', () => {
    renderWithCapabilities();
    
    // Check that feature descriptions are present
    expect(screen.getByText(/统一控制面和增强的元数据管理/)).toBeInTheDocument();
    expect(screen.getByText(/多租户和资源隔离/)).toBeInTheDocument();
    expect(screen.getByText(/轻量级Topic和高效消费/)).toBeInTheDocument();
    expect(screen.getByText(/高性能gRPC协议通信/)).toBeInTheDocument();
    expect(screen.getByText(/增强的访问控制和权限管理/)).toBeInTheDocument();
    expect(screen.getByText(/无状态消费和高效消息获取/)).toBeInTheDocument();
  });

  test('renders without crashing with minimal props', () => {
    expect(() => {
      render(<CapabilityDemo />);
    }).not.toThrow();
  });
});