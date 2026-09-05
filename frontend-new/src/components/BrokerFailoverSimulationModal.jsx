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

const BrokerFailoverSimulationModal = ({ open, onClose, brokerName }) => {
  const [loading, setLoading] = useState(false);
  const [report, setReport] = useState(null);
  const [error, setError] = useState(null);

  const runSimulation = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const url = `/cluster/simulateBrokerDown.query?brokerName=${encodeURIComponent(brokerName)}`;
      const res = await get(url);
      setReport(res);
    } catch (err) {
      setError(err.message || 'Failed to simulate broker failover');
    } finally {
      setLoading(false);
    }
  }, [brokerName]);

  useEffect(() => {
    if (open && brokerName) {
      runSimulation();
    }
  }, [open, brokerName, runSimulation]);

  const getHazardChip = (level) => {
    switch (level) {
      case 'CRITICAL':
        return <Chip label="CRITICAL HAZARD" color="error" size="small" />;
      case 'MEDIUM':
        return <Chip label="MEDIUM HAZARD" color="warning" size="small" />;
      case 'LOW':
        return <Chip label="SAFE" color="success" size="small" />;
      default:
        return <Chip label={level || 'UNKNOWN'} size="small" />;
    }
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>Broker Offline & Failover Impact Simulation [{brokerName}]</DialogTitle>
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
                <Typography variant="caption" color="textSecondary">Target Broker</Typography>
                <Typography variant="body2" fontWeight="bold">{report.targetBrokerName}</Typography>
              </Paper>
              <Paper sx={{ p: 1.5, flex: '1 1 120px' }} variant="outlined">
                <Typography variant="caption" color="textSecondary">Total Outage Topics</Typography>
                <Typography variant="body2" color={report.totalLossTopicCount > 0 ? 'error.main' : 'textPrimary'} fontWeight="bold">
                  {report.totalLossTopicCount}
                </Typography>
              </Paper>
              <Paper sx={{ p: 1.5, flex: '1 1 120px' }} variant="outlined">
                <Typography variant="caption" color="textSecondary">Degraded Topics</Typography>
                <Typography variant="body2" fontWeight="bold">{report.degradedTopicCount}</Typography>
              </Paper>
              <Paper sx={{ p: 1.5, flex: '1 1 120px' }} variant="outlined">
                <Typography variant="caption" color="textSecondary">SLA Score</Typography>
                <Typography variant="body2" fontWeight="bold">{report.availabilityScore} / 100</Typography>
              </Paper>
              <Paper sx={{ p: 1.5, flex: '1 1 140px' }} variant="outlined">
                <Typography variant="caption" color="textSecondary">Risk Level</Typography>
                <Box mt={0.5}>{getHazardChip(report.hazardLevel)}</Box>
              </Paper>
            </Box>

            {report.actionPlan && (
              <Box mb={2}>
                <Alert severity={report.hazardLevel === 'CRITICAL' ? 'error' : 'info'}>
                  {report.actionPlan}
                </Alert>
              </Box>
            )}

            <Typography variant="subtitle1" gutterBottom fontWeight="bold">
              Impacted Topic Routing Breakdown
            </Typography>
            <TableContainer component={Paper} variant="outlined">
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Topic</TableCell>
                    <TableCell>Queues (Orig / Lost / Left)</TableCell>
                    <TableCell>Capacity Drop</TableCell>
                    <TableCell>Severity Status</TableCell>
                    <TableCell>Impact Diagnosis</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {report.impactedTopics && report.impactedTopics.length > 0 ? (
                    report.impactedTopics.map((item, idx) => (
                      <TableRow key={idx} sx={{ bgcolor: item.isCompleteLoss ? 'error.lighter' : 'inherit' }}>
                        <TableCell sx={{ fontWeight: 'bold' }}>{item.topic}</TableCell>
                        <TableCell>
                          {item.originalQueueCount} / -{item.lostQueueCount} / {item.remainingQueueCount}
                        </TableCell>
                        <TableCell>{item.capacityLossRatio}%</TableCell>
                        <TableCell>
                          {item.isCompleteLoss ? (
                            <Chip label="100% OUTAGE" color="error" size="small" />
                          ) : (
                            <Chip label="CAPACITY DEGRADED" color="warning" size="small" />
                          )}
                        </TableCell>
                        <TableCell sx={{ fontSize: '0.8rem' }}>{item.riskExplanation}</TableCell>
                      </TableRow>
                    ))
                  ) : (
                    <TableRow>
                      <TableCell colSpan={5} align="center">
                        No topics will be impacted by offline operation on this broker
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

export default BrokerFailoverSimulationModal;
