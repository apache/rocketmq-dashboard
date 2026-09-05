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
import { get } from '../../api/remoteApi/remoteApi';

const ConsumerDiagnosticModal = ({ open, onClose, consumerGroup, topic }) => {
  const [loading, setLoading] = useState(false);
  const [report, setReport] = useState(null);
  const [error, setError] = useState(null);

  const fetchDiagnosticReport = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const url = `/consumer-diagnostic/diagnose.query?consumerGroup=${encodeURIComponent(consumerGroup)}${
        topic ? `&topic=${encodeURIComponent(topic)}` : ''
      }`;
      const res = await get(url);
      setReport(res);
    } catch (err) {
      setError(err.message || 'Failed to fetch diagnostic report');
    } finally {
      setLoading(false);
    }
  }, [consumerGroup, topic]);

  useEffect(() => {
    if (open && consumerGroup) {
      fetchDiagnosticReport();
    }
  }, [open, consumerGroup, fetchDiagnosticReport]);

  const getSkewChipColor = (level) => {
    switch (level) {
      case 'CRITICAL':
        return 'error';
      case 'MODERATE':
        return 'warning';
      case 'NORMAL':
        return 'success';
      default:
        return 'default';
    }
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>
        Consumer Group Latency & Imbalance Diagnostic [{consumerGroup}]
      </DialogTitle>
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
            {/* Metric Overview */}
            <Box display="flex" gap={2} mb={3}>
              <Paper sx={{ p: 2, flex: 1, textAlign: 'center' }}>
                <Typography variant="caption" color="textSecondary">
                  Total Backlog (Diff)
                </Typography>
                <Typography variant="h5" color="primary">
                  {report.totalDiff?.toLocaleString()}
                </Typography>
              </Paper>
              <Paper sx={{ p: 2, flex: 1, textAlign: 'center' }}>
                <Typography variant="caption" color="textSecondary">
                  Client Skew Variance
                </Typography>
                <Typography variant="h5">
                  {report.clientSkewVariance}
                </Typography>
              </Paper>
              <Paper sx={{ p: 2, flex: 1, textAlign: 'center' }}>
                <Typography variant="caption" color="textSecondary">
                  Skew Level
                </Typography>
                <Box mt={0.5}>
                  <Chip
                    label={report.accumulationSkewLevel || 'UNKNOWN'}
                    color={getSkewChipColor(report.accumulationSkewLevel)}
                  />
                </Box>
              </Paper>
            </Box>

            {/* Diagnostic Suggestions */}
            <Typography variant="h6" gutterBottom sx={{ mt: 2 }}>
              Diagnostic Recommendations
            </Typography>
            {report.diagnosticSuggestions?.map((item, idx) => (
              <Alert key={idx} severity={report.accumulationSkewLevel === 'CRITICAL' ? 'warning' : 'info'} sx={{ mb: 1 }}>
                {item}
              </Alert>
            ))}

            <Divider sx={{ my: 3 }} />

            {/* Bottleneck Queues */}
            <Typography variant="h6" gutterBottom>
              Top Bottleneck Queues (Diff &ge; 500)
            </Typography>
            <TableContainer component={Paper} sx={{ mb: 3 }}>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Broker</TableCell>
                    <TableCell align="right">Queue ID</TableCell>
                    <TableCell align="right">Broker Offset</TableCell>
                    <TableCell align="right">Consumer Offset</TableCell>
                    <TableCell align="right">Diff</TableCell>
                    <TableCell>Assigned Client</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {report.bottleneckQueues && report.bottleneckQueues.length > 0 ? (
                    report.bottleneckQueues.map((row, index) => (
                      <TableRow key={index}>
                        <TableCell>{row.brokerName}</TableCell>
                        <TableCell align="right">{row.queueId}</TableCell>
                        <TableCell align="right">{row.brokerOffset}</TableCell>
                        <TableCell align="right">{row.consumerOffset}</TableCell>
                        <TableCell align="right" sx={{ fontWeight: 'bold', color: 'error.main' }}>
                          {row.diff}
                        </TableCell>
                        <TableCell>{row.clientAddr}</TableCell>
                      </TableRow>
                    ))
                  ) : (
                    <TableRow>
                      <TableCell colSpan={6} align="center">
                        No bottleneck queues found.
                      </TableCell>
                    </TableRow>
                  )}
                </TableBody>
              </Table>
            </TableContainer>

            {/* Client Statuses */}
            <Typography variant="h6" gutterBottom>
              Consumer Clients Load Breakdown
            </Typography>
            <TableContainer component={Paper}>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Client Address</TableCell>
                    <TableCell align="right">Assigned Queues</TableCell>
                    <TableCell align="right">Total Client Backlog</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {report.clientStatuses && report.clientStatuses.length > 0 ? (
                    report.clientStatuses.map((client, index) => (
                      <TableRow key={index}>
                        <TableCell>{client.clientAddr}</TableCell>
                        <TableCell align="right">{client.assignedQueueCount}</TableCell>
                        <TableCell align="right">{client.totalClientDiff}</TableCell>
                      </TableRow>
                    ))
                  ) : (
                    <TableRow>
                      <TableCell colSpan={3} align="center">
                        No active clients registered.
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
        <Button onClick={fetchDiagnosticReport} color="primary">
          Refresh
        </Button>
        <Button onClick={onClose}>Close</Button>
      </DialogActions>
    </Dialog>
  );
};

export default ConsumerDiagnosticModal;
