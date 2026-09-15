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

import React, { useState, useEffect } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Table,
  TableHead,
  TableBody,
  TableRow,
  TableCell,
  Typography,
  Chip,
  Box,
  Alert,
  Grid,
  Card,
  CardContent,
  CircularProgress
} from '@mui/material';
import axios from 'axios';

const TransactionHalfMessageAuditModal = ({ open, onClose, topic, producerGroup }) => {
  const [loading, setLoading] = useState(false);
  const [resolving, setResolving] = useState(false);
  const [report, setReport] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    if (open) {
      fetchHalfAudit();
    }
  }, [open, topic, producerGroup]);

  const fetchHalfAudit = async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await axios.get('/transaction/halfAudit.query', {
        params: { topic, producerGroup }
      });
      setReport(response.data);
    } catch (err) {
      setError(err.message || 'Failed to fetch transaction half-message audit report');
    } finally {
      setLoading(false);
    }
  };

  const handleResolve = async (msgId, transactionId, action) => {
    setResolving(true);
    try {
      await axios.post(
        `/transaction/halfResolve.do?msgId=${encodeURIComponent(msgId)}&transactionId=${encodeURIComponent(transactionId || '')}&action=${action}`
      );
      fetchHalfAudit();
    } catch (err) {
      setError(err.message || `Failed to ${action} transaction`);
    } finally {
      setResolving(false);
    }
  };

  const getStatusColor = (status) => {
    if (status === 'HEALTHY') return 'success';
    if (status === 'WARNING') return 'warning';
    return 'error';
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="lg" fullWidth>
      <DialogTitle>
        <Box display="flex" justifyContent="space-between" alignItems="center">
          <Typography variant="h6">
            Transaction Half Message Audit & Hang Resolution: {topic || 'All Topics'}
          </Typography>
          {report && (
            <Chip
              label={report.healthStatus}
              color={getStatusColor(report.healthStatus)}
              size="small"
            />
          )}
        </Box>
      </DialogTitle>
      <DialogContent dividers>
        {loading && (
          <Box display="flex" justifyContent="center" p={4}>
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
            <Grid container spacing={2} sx={{ mb: 3 }}>
              <Grid item xs={4}>
                <Card variant="outlined">
                  <CardContent>
                    <Typography color="textSecondary" variant="caption">
                      Pending Half Messages
                    </Typography>
                    <Typography variant="h6">{report.totalPendingHalfMessages}</Typography>
                  </CardContent>
                </Card>
              </Grid>
              <Grid item xs={4}>
                <Card variant="outlined">
                  <CardContent>
                    <Typography color="textSecondary" variant="caption">
                      Severe Hanging Messages
                    </Typography>
                    <Typography variant="h6" color="error">
                      {report.severeHangingMessages}
                    </Typography>
                  </CardContent>
                </Card>
              </Grid>
              <Grid item xs={4}>
                <Card variant="outlined">
                  <CardContent>
                    <Typography color="textSecondary" variant="caption">
                      Timeout Risk Rate
                    </Typography>
                    <Typography variant="h6">{report.timeoutRatePercent} %</Typography>
                  </CardContent>
                </Card>
              </Grid>
            </Grid>

            <Typography variant="subtitle1" sx={{ mt: 2, mb: 1, fontWeight: 'bold' }}>
              Pending Uncommitted Half Message Inventory
            </Typography>
            <Table size="small" sx={{ mb: 3 }}>
              <TableHead>
                <TableRow>
                  <TableCell>Message ID</TableCell>
                  <TableCell>Tx ID</TableCell>
                  <TableCell>Hanging Time</TableCell>
                  <TableCell>Check Retries</TableCell>
                  <TableCell>State</TableCell>
                  <TableCell align="right">Manual Compensation</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {report.pendingHalfMessages.map((msg, idx) => (
                  <TableRow key={idx}>
                    <TableCell sx={{ fontFamily: 'monospace', fontSize: '0.85rem' }}>
                      {msg.msgId}
                    </TableCell>
                    <TableCell sx={{ fontFamily: 'monospace' }}>{msg.transactionId}</TableCell>
                    <TableCell>{msg.hangingDurationMinutes} mins</TableCell>
                    <TableCell sx={{ color: msg.checkCount >= 10 ? 'red' : 'inherit' }}>
                      {msg.checkCount} / {msg.maxCheckRetries}
                    </TableCell>
                    <TableCell>
                      <Chip
                        label={msg.state}
                        color={msg.state.includes('APPROACHING') ? 'error' : 'default'}
                        size="small"
                      />
                    </TableCell>
                    <TableCell align="right">
                      <Button
                        size="small"
                        color="success"
                        disabled={resolving}
                        onClick={() => handleResolve(msg.msgId, msg.transactionId, 'COMMIT')}
                        sx={{ mr: 1 }}
                      >
                        Commit
                      </Button>
                      <Button
                        size="small"
                        color="error"
                        disabled={resolving}
                        onClick={() => handleResolve(msg.msgId, msg.transactionId, 'ROLLBACK')}
                      >
                        Rollback
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>

            {report.resolutionRecommendations && report.resolutionRecommendations.length > 0 && (
              <Box sx={{ mt: 2 }}>
                <Typography variant="subtitle2" color="textSecondary" sx={{ mb: 1 }}>
                  Resolution Guidance:
                </Typography>
                {report.resolutionRecommendations.map((rec, i) => (
                  <Alert severity="info" key={i} sx={{ mb: 1 }}>
                    {rec}
                  </Alert>
                ))}
              </Box>
            )}
          </Box>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={fetchHalfAudit} color="secondary">
          Refresh
        </Button>
        <Button onClick={onClose} color="primary" variant="contained">
          Close
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default TransactionHalfMessageAuditModal;
