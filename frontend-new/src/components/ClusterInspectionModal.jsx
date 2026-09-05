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

import React, { useState, useEffect, useCallback } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Typography,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  Chip,
  Box,
  CircularProgress,
  Alert,
  Divider,
} from '@mui/material';
import { get } from '../api/remoteApi/remoteApi';

const ClusterInspectionModal = ({ open, onClose }) => {
  const [loading, setLoading] = useState(false);
  const [report, setReport] = useState(null);
  const [error, setError] = useState(null);

  const runClusterInspection = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await get('/cluster-inspection/inspect.query');
      setReport(res);
    } catch (err) {
      setError(err.message || 'Failed to execute cluster inspection');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (open) {
      runClusterInspection();
    }
  }, [open, runClusterInspection]);

  const getSeverityChip = (severity) => {
    switch (severity) {
      case 'CRITICAL':
        return <Chip label="CRITICAL" color="error" size="small" />;
      case 'HIGH':
        return <Chip label="HIGH" color="warning" size="small" />;
      case 'WARNING':
        return <Chip label="WARNING" color="info" size="small" />;
      default:
        return <Chip label={severity} size="small" />;
    }
  };

  const getScoreColor = (score) => {
    if (score >= 90) return 'success.main';
    if (score >= 70) return 'warning.main';
    return 'error.main';
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>Cluster Health & Topology Inspection Console</DialogTitle>
      <DialogContent dividers>
        {loading && (
          <Box display="flex" justifyContent="center" my={4}>
            <CircularProgress />
          </Box>
        )}

        {error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {error}
          </Alert>
        )}

        {report && !loading && (
          <Box>
            {/* Health Score Overview */}
            <Box display="flex" gap={2} mb={3}>
              <Paper sx={{ p: 2, flex: 1, textAlign: 'center' }}>
                <Typography variant="caption" color="textSecondary">
                  Cluster Health Score
                </Typography>
                <Typography variant="h3" sx={{ color: getScoreColor(report.totalScore), fontWeight: 'bold' }}>
                  {report.totalScore} / 100
                </Typography>
              </Paper>
              <Paper sx={{ p: 2, flex: 1, textAlign: 'center' }}>
                <Typography variant="caption" color="textSecondary">
                  Overall Status
                </Typography>
                <Box mt={1}>
                  <Chip
                    label={report.overallHealthStatus}
                    color={
                      report.overallHealthStatus === 'HEALTHY'
                        ? 'success'
                        : report.overallHealthStatus === 'WARNING'
                        ? 'warning'
                        : 'error'
                    }
                  />
                </Box>
              </Paper>
              <Paper sx={{ p: 2, flex: 1, textAlign: 'center' }}>
                <Typography variant="caption" color="textSecondary">
                  Inspected Topology Scope
                </Typography>
                <Typography variant="body1" sx={{ mt: 1 }}>
                  Brokers: <b>{report.inspectedBrokerCount}</b> | Topics: <b>{report.inspectedTopicCount}</b>
                </Typography>
              </Paper>
            </Box>

            <Divider sx={{ my: 2 }} />

            {/* Detected Issues */}
            <Typography variant="h6" gutterBottom>
              Detected Topology & Risk Issues ({report.detectedIssues?.length || 0})
            </Typography>
            <TableContainer component={Paper} sx={{ mb: 3 }}>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Category</TableCell>
                    <TableCell>Severity</TableCell>
                    <TableCell>Target Resource</TableCell>
                    <TableCell>Description</TableCell>
                    <TableCell>Remediation</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {report.detectedIssues && report.detectedIssues.length > 0 ? (
                    report.detectedIssues.map((issue, idx) => (
                      <TableRow key={idx}>
                        <TableCell><b>{issue.category}</b></TableCell>
                        <TableCell>{getSeverityChip(issue.severity)}</TableCell>
                        <TableCell>{issue.targetResource}</TableCell>
                        <TableCell>{issue.description}</TableCell>
                        <TableCell sx={{ color: 'text.secondary', fontSize: '0.85rem' }}>
                          {issue.remediationSuggestion}
                        </TableCell>
                      </TableRow>
                    ))
                  ) : (
                    <TableRow>
                      <TableCell colSpan={5} align="center">
                        No health issues or topology drifts detected. Cluster is in optimal state.
                      </TableCell>
                    </TableRow>
                  )}
                </TableBody>
              </Table>
            </TableContainer>

            {/* Master-Slave Replication Status */}
            <Typography variant="h6" gutterBottom>
              Master-Slave Replication & Sync Status
            </Typography>
            <TableContainer component={Paper}>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Cluster</TableCell>
                    <TableCell>Broker Group</TableCell>
                    <TableCell>Master Addr</TableCell>
                    <TableCell>Slave Addr</TableCell>
                    <TableCell>Sync Status</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {report.masterSlaveSyncStatuses && report.masterSlaveSyncStatuses.length > 0 ? (
                    report.masterSlaveSyncStatuses.map((sync, idx) => (
                      <TableRow key={idx}>
                        <TableCell>{sync.clusterName}</TableCell>
                        <TableCell>{sync.brokerName}</TableCell>
                        <TableCell>{sync.masterAddr}</TableCell>
                        <TableCell>{sync.slaveAddr}</TableCell>
                        <TableCell>
                          <Chip label="SYNCHRONIZED" color="success" size="small" />
                        </TableCell>
                      </TableRow>
                    ))
                  ) : (
                    <TableRow>
                      <TableCell colSpan={5} align="center">
                        No Master-Slave pairs configured in this cluster.
                      </TableCell>
                    </TableRow>
                  )}
                </TableBody>
              </Table>
            </TableContainer>
          </Box>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={runClusterInspection} color="primary">
          Re-run Inspection
        </Button>
        <Button onClick={onClose}>Close</Button>
      </DialogActions>
    </Dialog>
  );
};

export default ClusterInspectionModal;
