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

const ConsumerSlowRootCauseModal = ({ open, onClose, consumerGroup }) => {
  const [loading, setLoading] = useState(false);
  const [report, setReport] = useState(null);
  const [error, setError] = useState(null);

  const runRootCauseDiagnosis = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const url = `/consumer/slowRootCause.query?consumerGroup=${encodeURIComponent(consumerGroup)}`;
      const res = await get(url);
      setReport(res);
    } catch (err) {
      setError(err.message || 'Failed to analyze consumer slow root cause');
    } finally {
      setLoading(false);
    }
  }, [consumerGroup]);

  useEffect(() => {
    if (open && consumerGroup) {
      runRootCauseDiagnosis();
    }
  }, [open, consumerGroup, runRootCauseDiagnosis]);

  const getSeverityChip = (severity) => {
    switch (severity) {
      case 'CRITICAL':
        return <Chip label="CRITICAL" color="error" size="small" />;
      case 'WARNING':
        return <Chip label="WARNING" color="warning" size="small" />;
      case 'NORMAL':
        return <Chip label="NORMAL" color="success" size="small" />;
      default:
        return <Chip label={severity || 'UNKNOWN'} size="small" />;
    }
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>Slow Consumer & Rebalance Hang Root-Cause Diagnostic [{consumerGroup}]</DialogTitle>
      <DialogContent dividers>
        {loading && (
          <Box display="flex" justifyContent="center" my={4}>
            <CircularProgress />
          </Box>
        )}

        {error && (
          <Box mb={2}>
            <Alert severity="error">{error}</Alert>
          </Box>
        )}

        {report && !loading && (
          <Box>
            <Box display="flex" flexWrap="wrap" gap={2} mb={3}>
              <Paper sx={{ p: 1.5, flex: '1 1 120px' }} variant="outlined">
                <Typography variant="caption" color="textSecondary">Active Clients</Typography>
                <Typography variant="body2" fontWeight="bold">{report.totalClients}</Typography>
              </Paper>
              <Paper sx={{ p: 1.5, flex: '1 1 180px' }} variant="outlined">
                <Typography variant="caption" color="textSecondary">Primary Root Cause</Typography>
                <Typography variant="body2" color="warning.main" fontWeight="bold">
                  {report.primaryRootCause}
                </Typography>
              </Paper>
              <Paper sx={{ p: 1.5, flex: '1 1 120px' }} variant="outlined">
                <Typography variant="caption" color="textSecondary">Health Severity</Typography>
                <Box mt={0.5}>{getSeverityChip(report.severity)}</Box>
              </Paper>
            </Box>

            {report.rootCauseDescription && (
              <Box mb={2}>
                <Alert severity={report.severity === 'CRITICAL' ? 'error' : 'warning'}>
                  <Typography variant="body2" fontWeight="bold">
                    Diagnostic Conclusion:
                  </Typography>
                  <Typography variant="body2">{report.rootCauseDescription}</Typography>
                  {report.actionableRemedy && (
                    <Typography variant="caption" display="block" sx={{ mt: 1 }}>
                      <strong>Remedy Action:</strong> {report.actionableRemedy}
                    </Typography>
                  )}
                </Alert>
              </Box>
            )}

            <Typography variant="subtitle1" gutterBottom fontWeight="bold">
              Client Instance Metric Profiling
            </Typography>
            <TableContainer component={Paper} variant="outlined">
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Client ID</TableCell>
                    <TableCell>Cached Msgs</TableCell>
                    <TableCell>Consume / Pull TPS</TableCell>
                    <TableCell>Stall Indicator</TableCell>
                    <TableCell>Diagnosis Summary</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {report.findings && report.findings.length > 0 ? (
                    report.findings.map((f, idx) => (
                      <TableRow key={idx} sx={{ bgcolor: f.isBlockedThreadDetected ? 'error.lighter' : 'inherit' }}>
                        <TableCell sx={{ wordBreak: 'break-all' }}>
                          <Typography variant="body2">{f.clientId}</Typography>
                          <Typography variant="caption" color="textSecondary">{f.clientAddr}</Typography>
                        </TableCell>
                        <TableCell sx={{ fontWeight: 'bold' }}>
                          {f.cachedMessageCount.toLocaleString()} ({f.cachedMessageSizeMb} MB)
                        </TableCell>
                        <TableCell>
                          {f.consumeTps} / {f.pullTps}
                        </TableCell>
                        <TableCell>
                          {f.isBlockedThreadDetected && (
                            <Chip label="THREAD BLOCKED" color="error" size="small" sx={{ mr: 0.5 }} />
                          )}
                          {f.isFlowControlTriggered && (
                            <Chip label="FLOW CONTROL" color="warning" size="small" />
                          )}
                          {!f.isBlockedThreadDetected && !f.isFlowControlTriggered && (
                            <Chip label="NOMINAL" color="success" size="small" />
                          )}
                        </TableCell>
                        <TableCell sx={{ fontSize: '0.8rem' }}>{f.diagnosisSummary}</TableCell>
                      </TableRow>
                    ))
                  ) : (
                    <TableRow>
                      <TableCell colSpan={5} align="center">
                        No client metrics available
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
        <Button onClick={onClose} color="primary" variant="contained">
          Close
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default ConsumerSlowRootCauseModal;
