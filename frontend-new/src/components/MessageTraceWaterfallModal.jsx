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
  LinearProgress,
} from '@mui/material';
import { get } from '../api/remoteApi/remoteApi';

const MessageTraceWaterfallModal = ({ open, onClose, msgId, traceTopic }) => {
  const [loading, setLoading] = useState(false);
  const [report, setReport] = useState(null);
  const [error, setError] = useState(null);

  const fetchWaterfallReport = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const url = `/messageTrace/waterfall.query?msgId=${encodeURIComponent(msgId)}${
        traceTopic ? `&traceTopic=${encodeURIComponent(traceTopic)}` : ''
      }`;
      const res = await get(url);
      setReport(res);
    } catch (err) {
      setError(err.message || 'Failed to analyze message trace waterfall');
    } finally {
      setLoading(false);
    }
  }, [msgId, traceTopic]);

  useEffect(() => {
    if (open && msgId) {
      fetchWaterfallReport();
    }
  }, [open, msgId, fetchWaterfallReport]);

  const getStageChip = (stage) => {
    switch (stage) {
      case 'PRODUCER_SEND':
        return <Chip label="PRODUCER SEND" color="primary" size="small" />;
      case 'BROKER_STORE_QUEUE':
        return <Chip label="BROKER QUEUE" color="secondary" size="small" />;
      case 'CONSUMER_EXECUTION':
        return <Chip label="CONSUMER EXEC" color="warning" size="small" />;
      default:
        return <Chip label={stage} size="small" />;
    }
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>Message End-to-End Latency & Trace Waterfall</DialogTitle>
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
              <Paper sx={{ p: 1.5, flex: '1 1 200px' }} variant="outlined">
                <Typography variant="caption" color="textSecondary">Message ID</Typography>
                <Typography variant="body2" sx={{ wordBreak: 'break-all', fontWeight: 'bold' }}>
                  {report.msgId}
                </Typography>
              </Paper>
              <Paper sx={{ p: 1.5, flex: '1 1 120px' }} variant="outlined">
                <Typography variant="caption" color="textSecondary">Topic</Typography>
                <Typography variant="body2" fontWeight="bold">{report.topic || '-'}</Typography>
              </Paper>
              <Paper sx={{ p: 1.5, flex: '1 1 120px' }} variant="outlined">
                <Typography variant="caption" color="textSecondary">Total Latency</Typography>
                <Typography variant="body2" color={report.isTimeout ? 'error.main' : 'textPrimary'} fontWeight="bold">
                  {report.totalE2eLatencyMs} ms
                </Typography>
              </Paper>
              <Paper sx={{ p: 1.5, flex: '1 1 140px' }} variant="outlined">
                <Typography variant="caption" color="textSecondary">Bottleneck Stage</Typography>
                <Typography variant="body2" color="warning.main" fontWeight="bold">
                  {report.bottleneckPhase}
                </Typography>
              </Paper>
            </Box>

            <Typography variant="subtitle1" gutterBottom fontWeight="bold">
              Execution Spans Waterfall
            </Typography>
            <TableContainer component={Paper} variant="outlined">
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Stage</TableCell>
                    <TableCell>Client / Target Host</TableCell>
                    <TableCell>Duration</TableCell>
                    <TableCell>Relative Waterfall Ratio</TableCell>
                    <TableCell>Status</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {report.spanNodes && report.spanNodes.length > 0 ? (
                    report.spanNodes.map((span, idx) => {
                      const ratio = report.totalE2eLatencyMs > 0
                        ? Math.min(100, (span.durationMs / report.totalE2eLatencyMs) * 100)
                        : 0;
                      return (
                        <TableRow key={idx} sx={{ bgcolor: span.isBottleneck ? 'action.hover' : 'inherit' }}>
                          <TableCell>
                            {getStageChip(span.stage)}
                            {span.isBottleneck && (
                              <Chip label="BOTTLENECK" color="error" size="small" sx={{ ml: 1, height: 20 }} />
                            )}
                          </TableCell>
                          <TableCell>
                            <Typography variant="caption" display="block">
                              From: {span.clientHost || '-'}
                            </Typography>
                            <Typography variant="caption" color="textSecondary" display="block">
                              To/Group: {span.targetHost || span.groupName || '-'}
                            </Typography>
                          </TableCell>
                          <TableCell sx={{ fontWeight: 'bold' }}>{span.durationMs} ms</TableCell>
                          <TableCell sx={{ width: 160 }}>
                            <Box display="flex" alignItems="center">
                              <Box sx={{ width: '100%', mr: 1 }}>
                                <LinearProgress
                                  variant="determinate"
                                  value={ratio}
                                  color={span.isBottleneck ? 'error' : 'primary'}
                                />
                              </Box>
                              <Typography variant="caption" color="textSecondary">
                                {Math.round(ratio)}%
                              </Typography>
                            </Box>
                          </TableCell>
                          <TableCell>
                            <Chip
                              label={span.status || 'OK'}
                              color={span.status === 'SUCCESS' ? 'success' : 'default'}
                              size="small"
                            />
                          </TableCell>
                        </TableRow>
                      );
                    })
                  ) : (
                    <TableRow>
                      <TableCell colSpan={5} align="center">
                        No trace span details recorded for this message
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

export default MessageTraceWaterfallModal;
