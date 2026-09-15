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

const ConsumerRebalanceHistoryModal = ({ open, onClose, consumerGroup }) => {
  const [loading, setLoading] = useState(false);
  const [report, setReport] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    if (open) {
      fetchRebalanceHistory();
    }
  }, [open, consumerGroup]);

  const fetchRebalanceHistory = async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await axios.get('/consumer/rebalanceHistory.query', {
        params: { consumerGroup, lookbackHours: 24 }
      });
      setReport(response.data);
    } catch (err) {
      setError(err.message || 'Failed to fetch consumer rebalance history');
    } finally {
      setLoading(false);
    }
  };

  const getStabilityColor = (level) => {
    if (level === 'STABLE') return 'success';
    if (level === 'MODERATE') return 'warning';
    return 'error';
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="lg" fullWidth>
      <DialogTitle>
        <Box display="flex" justifyContent="space-between" alignItems="center">
          <Typography variant="h6">
            Consumer Group Rebalance History & Flapping Audit: {consumerGroup}
          </Typography>
          {report && (
            <Chip
              label={report.stabilityLevel}
              color={getStabilityColor(report.stabilityLevel)}
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
                      Total Rebalance Events (24h)
                    </Typography>
                    <Typography variant="h6">{report.totalRebalanceEvents}</Typography>
                  </CardContent>
                </Card>
              </Grid>
              <Grid item xs={4}>
                <Card variant="outlined">
                  <CardContent>
                    <Typography color="textSecondary" variant="caption">
                      Flapping Instability Index
                    </Typography>
                    <Typography variant="h6" color={getStabilityColor(report.stabilityLevel)}>
                      {report.flappingScore} / 100
                    </Typography>
                  </CardContent>
                </Card>
              </Grid>
              <Grid item xs={4}>
                <Card variant="outlined">
                  <CardContent>
                    <Typography color="textSecondary" variant="caption">
                      Churning / Unstable Clients
                    </Typography>
                    <Typography variant="h6" color="error">
                      {report.churnClients.length}
                    </Typography>
                  </CardContent>
                </Card>
              </Grid>
            </Grid>

            <Typography variant="subtitle1" sx={{ mt: 2, mb: 1, fontWeight: 'bold' }}>
              Historical Rebalance Event Timeline
            </Typography>
            <Table size="small" sx={{ mb: 3 }}>
              <TableHead>
                <TableRow>
                  <TableCell>Event ID</TableCell>
                  <TableCell>Timestamp</TableCell>
                  <TableCell>Trigger Cause</TableCell>
                  <TableCell>Clients (Before → After)</TableCell>
                  <TableCell>Reassigned Queues</TableCell>
                  <TableCell>Target Topic</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {report.rebalanceEvents.map((evt, idx) => (
                  <TableRow key={idx}>
                    <TableCell sx={{ fontFamily: 'monospace' }}>{evt.eventId}</TableCell>
                    <TableCell>{new Date(evt.timestamp).toLocaleString()}</TableCell>
                    <TableCell>
                      <Chip label={evt.triggerReason} size="small" variant="outlined" />
                    </TableCell>
                    <TableCell>
                      {evt.clientCountBefore} → {evt.clientCountAfter}
                    </TableCell>
                    <TableCell>{evt.reassignedQueueCount}</TableCell>
                    <TableCell>{evt.impactedTopic}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>

            <Typography variant="subtitle1" sx={{ mt: 2, mb: 1, fontWeight: 'bold' }}>
              Partition Flapping Distribution (Frequent Queue Transfers)
            </Typography>
            <Table size="small" sx={{ mb: 3 }}>
              <TableHead>
                <TableRow>
                  <TableCell>Topic</TableCell>
                  <TableCell>Queue ID</TableCell>
                  <TableCell>Broker</TableCell>
                  <TableCell>Transfer Count</TableCell>
                  <TableCell>Current Owner</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {report.flappingQueues.map((q, idx) => (
                  <TableRow key={idx}>
                    <TableCell>{q.topic}</TableCell>
                    <TableCell>Queue #{q.queueId}</TableCell>
                    <TableCell>{q.brokerName}</TableCell>
                    <TableCell sx={{ color: q.reassignmentCount > 4 ? 'red' : 'inherit' }}>
                      {q.reassignmentCount} times
                    </TableCell>
                    <TableCell sx={{ fontFamily: 'monospace' }}>{q.lastAssignedClientId}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>

            {report.stabilityRecommendations && report.stabilityRecommendations.length > 0 && (
              <Box sx={{ mt: 2 }}>
                <Typography variant="subtitle2" color="textSecondary" sx={{ mb: 1 }}>
                  Stability Tuning Guidance:
                </Typography>
                {report.stabilityRecommendations.map((rec, i) => (
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
        <Button onClick={fetchRebalanceHistory} color="secondary">
          Refresh
        </Button>
        <Button onClick={onClose} color="primary" variant="contained">
          Close
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default ConsumerRebalanceHistoryModal;
