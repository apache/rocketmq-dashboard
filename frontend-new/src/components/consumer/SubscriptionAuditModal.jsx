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

const SubscriptionAuditModal = ({ open, onClose, consumerGroup }) => {
  const [loading, setLoading] = useState(false);
  const [report, setReport] = useState(null);
  const [error, setError] = useState(null);

  const fetchAuditReport = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const url = `/consumer/subscriptionAudit.query?consumerGroup=${encodeURIComponent(consumerGroup)}`;
      const res = await get(url);
      setReport(res);
    } catch (err) {
      setError(err.message || 'Failed to audit subscription consistency');
    } finally {
      setLoading(false);
    }
  }, [consumerGroup]);

  useEffect(() => {
    if (open && consumerGroup) {
      fetchAuditReport();
    }
  }, [open, consumerGroup, fetchAuditReport]);

  const getStatusChip = (consistent) => {
    if (consistent) {
      return <Chip label="HOMOGENEOUS" color="success" size="small" />;
    }
    return <Chip label="INCONSISTENT / CONFLICT" color="error" size="small" />;
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>Consumer Group Subscription Consistency Audit [{consumerGroup}]</DialogTitle>
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
              <Paper sx={{ p: 1.5, flex: '1 1 140px' }} variant="outlined">
                <Typography variant="caption" color="textSecondary">Conflict Rules</Typography>
                <Typography variant="body2" color={report.conflictItemCount > 0 ? 'error.main' : 'textPrimary'} fontWeight="bold">
                  {report.conflictItemCount}
                </Typography>
              </Paper>
              <Paper sx={{ p: 1.5, flex: '1 1 180px' }} variant="outlined">
                <Typography variant="caption" color="textSecondary">Consistency Audit</Typography>
                <Box mt={0.5}>{getStatusChip(report.consistent)}</Box>
              </Paper>
            </Box>

            {report.recommendation && (
              <Box mb={2}>
                <Alert severity={report.consistent ? 'success' : 'error'}>
                  {report.recommendation}
                </Alert>
              </Box>
            )}

            <Typography variant="subtitle1" gutterBottom fontWeight="bold">
              Detected Subscription Discrepancies
            </Typography>
            <TableContainer component={Paper} variant="outlined" sx={{ mb: 3 }}>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Topic</TableCell>
                    <TableCell>Conflict Type</TableCell>
                    <TableCell>Impact Description</TableCell>
                    <TableCell>Expression Breakdown</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {report.conflictItems && report.conflictItems.length > 0 ? (
                    report.conflictItems.map((item, idx) => (
                      <TableRow key={idx} sx={{ bgcolor: 'error.lighter' }}>
                        <TableCell sx={{ fontWeight: 'bold' }}>{item.topic}</TableCell>
                        <TableCell>
                          <Chip label={item.conflictType} color="warning" size="small" />
                        </TableCell>
                        <TableCell>{item.description}</TableCell>
                        <TableCell>
                          {item.clientExpressions &&
                            Object.entries(item.clientExpressions).map(([client, expr], i) => (
                              <Typography variant="caption" display="block" key={i}>
                                <code>{client}</code>: <strong>{expr}</strong>
                              </Typography>
                            ))}
                        </TableCell>
                      </TableRow>
                    ))
                  ) : (
                    <TableRow>
                      <TableCell colSpan={4} align="center">
                        No subscription conflicts detected across group clients
                      </TableCell>
                    </TableRow>
                  )}
                </TableBody>
              </Table>
            </TableContainer>

            <Typography variant="subtitle1" gutterBottom fontWeight="bold">
              Online Client Instance Inventory
            </Typography>
            <TableContainer component={Paper} variant="outlined">
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Client ID</TableCell>
                    <TableCell>Client Address</TableCell>
                    <TableCell>Language / Ver</TableCell>
                    <TableCell>Subscribed Topics</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {report.clientSummaries && report.clientSummaries.length > 0 ? (
                    report.clientSummaries.map((c, idx) => (
                      <TableRow key={idx}>
                        <TableCell sx={{ wordBreak: 'break-all' }}>{c.clientId}</TableCell>
                        <TableCell>{c.clientAddr}</TableCell>
                        <TableCell>{c.language} / {c.version}</TableCell>
                        <TableCell>
                          {c.subscribedTopics ? c.subscribedTopics.join(', ') : '-'}
                        </TableCell>
                      </TableRow>
                    ))
                  ) : (
                    <TableRow>
                      <TableCell colSpan={4} align="center">
                        No active instances online
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

export default SubscriptionAuditModal;
