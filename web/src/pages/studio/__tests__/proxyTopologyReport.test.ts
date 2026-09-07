/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */

import { describe, expect, it } from 'vitest';
import type { ProxyNode, ProxyTopologyNode } from '../../../api/proxy';
import {
  buildProxyTopologyReport,
  filterProxyTopologyRows,
  parseProxyAddress,
  proxyTopologyCsvRows,
} from '../proxyTopologyReport';

const registered = (address: string, selected = false): ProxyNode => ({
  key: address,
  address,
  status: 'unknown',
  version: null,
  connections: null,
  tps: null,
  memory: null,
  cpu: null,
  uptime: null,
  isSelected: selected,
});

const probed = (
  proxyAddr: string,
  overrides: Partial<ProxyTopologyNode> = {},
): ProxyTopologyNode => ({
  proxyAddr,
  status: 'UP',
  grpcPort: 8081,
  remotingPort: 8080,
  grpcReachable: true,
  remotingReachable: true,
  latencyMs: 5,
  ...overrides,
});

describe('proxy topology report', () => {
  it('parses host names, IPv4, and bracketed IPv6 addresses', () => {
    expect(parseProxyAddress('proxy.example.com:8081')).toEqual({
      host: 'proxy.example.com',
      port: 8081,
    });
    expect(parseProxyAddress('10.0.0.1:8081')).toEqual({ host: '10.0.0.1', port: 8081 });
    expect(parseProxyAddress('[2001:db8::1]:8081')).toEqual({ host: '2001:db8::1', port: 8081 });
  });

  it('does not interpret unbracketed IPv6 or invalid ports as a host-port pair', () => {
    expect(parseProxyAddress('2001:db8::1')).toEqual({ host: '2001:db8::1', port: null });
    expect(parseProxyAddress('proxy:70000')).toEqual({ host: 'proxy', port: null });
    expect(parseProxyAddress('proxy')).toEqual({ host: 'proxy', port: null });
  });

  it('correlates registration and probe details', () => {
    const report = buildProxyTopologyReport(
      [registered('proxy-a:8081', true)],
      [probed('proxy-a:8081')],
    );
    expect(report.rows[0]).toMatchObject({
      address: 'proxy-a:8081',
      host: 'proxy-a',
      registered: true,
      selected: true,
      status: 'UP',
      grpcReachable: true,
      remotingReachable: true,
    });
    expect(report.summary).toMatchObject({ registeredNodes: 1, probedNodes: 1, fullyReachable: 1 });
  });

  it('keeps registration-only and discovery-only nodes', () => {
    const report = buildProxyTopologyReport(
      [registered('registered:8081')],
      [probed('discovered:8081')],
    );
    expect(report.rows.map((row) => row.status)).toEqual(['REGISTERED_ONLY', 'DISCOVERED_ONLY']);
    expect(report.summary.registrationGaps).toBe(2);
  });

  it('sorts failed and partial nodes before healthy nodes', () => {
    const nodes = ['up:1', 'partial:2', 'down:3'].map((address) => registered(address));
    const report = buildProxyTopologyReport(nodes, [
      probed('up:1'),
      probed('partial:2', { status: 'PARTIAL', remotingReachable: false }),
      probed('down:3', { status: 'DOWN', grpcReachable: false, remotingReachable: false }),
    ]);
    expect(report.rows.map((row) => row.status)).toEqual(['DOWN', 'PARTIAL', 'UP']);
    expect(report.summary.degradedOrDown).toBe(2);
  });

  it('groups nodes by host and aggregates unique ports', () => {
    const report = buildProxyTopologyReport(
      [registered('proxy-a:8081'), registered('proxy-a:9081'), registered('proxy-b:8081')],
      [
        probed('proxy-a:8081'),
        probed('proxy-a:9081', { grpcPort: 9081, remotingPort: 9080 }),
        probed('proxy-b:8081'),
      ],
    );
    expect(report.hosts[0]).toMatchObject({ host: 'proxy-a', nodes: 2, reachableNodes: 2 });
    expect(report.hosts[0].ports).toEqual([8080, 8081, 9080, 9081]);
    expect(report.summary).toMatchObject({ uniqueHosts: 2, busiestHostNodes: 2 });
  });

  it('filters by status, registration gaps, and case-insensitive text', () => {
    const report = buildProxyTopologyReport(
      [registered('PROXY-A:8081'), registered('proxy-b:8081')],
      [probed('PROXY-A:8081')],
    );
    expect(filterProxyTopologyRows(report.rows, { status: 'UP' })).toHaveLength(1);
    expect(filterProxyTopologyRows(report.rows, { registrationGapOnly: true })).toHaveLength(1);
    expect(filterProxyTopologyRows(report.rows, { search: 'proxy-a' })).toHaveLength(1);
  });

  it('creates flat export rows with explicit empty unavailable metrics', () => {
    const report = buildProxyTopologyReport([registered('proxy-a:8081')], []);
    expect(proxyTopologyCsvRows(report.rows)).toEqual([
      expect.objectContaining({
        address: 'proxy-a:8081',
        registeredPort: 8081,
        status: 'REGISTERED_ONLY',
        grpcPort: '',
        grpcReachable: '',
      }),
    ]);
  });

  it('returns an empty, stable report', () => {
    expect(buildProxyTopologyReport([], [])).toEqual({
      rows: [],
      hosts: [],
      summary: {
        registeredNodes: 0,
        probedNodes: 0,
        fullyReachable: 0,
        degradedOrDown: 0,
        registrationGaps: 0,
        uniqueHosts: 0,
        busiestHostNodes: 0,
      },
    });
  });
});
