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
  LinearProgress,
  Alert,
  Grid,
  Card,
  CardContent,
  CircularProgress
} from '@mui/material';
import axios from 'axios';

const ConsumerThreadPoolSaturationModal = ({ open, onClose, consumerGroup }) => {
  const [loading, setLoading] = useState(false);
  const [report, setReport] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    if (open) {
      fetchThreadPoolSaturation();
    }
  }, [open, consumerGroup]);

  const fetchThreadPoolSaturation = async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await axios.get('/consumer/threadPoolSaturation.query', {
        params: { consumerGroup }
      });
      setReport(response.data);
    } catch (err) {
      setError(err.message || 'Failed to fetch thread pool saturation metrics');
    } finally {
      setLoading(false);
    }
  };

  const getStatusColor = (status) => {
    if (status === 'HEALTHY') return 'success';
    if (status === 'WARNING') return 'warning';
    return 'error';
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>
        <Box display="flex" justifyContent="space-between" alignItems="center">
          <Typography variant="h6">
            Consumer Client Thread Pool Saturation: {consumerGroup}
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
                      Online Consumer Clients
                    </Typography>
                    <Typography variant="h6">{report.totalClients}</Typography>
                  </CardContent>
                </Card>
              </Grid>
              <Grid item xs={4}>
                <Card variant="outlined">
                  <CardContent>
                    <Typography color="textSecondary" variant="caption">
                      Avg Thread Saturation
                    </Typography>
                    <Typography variant="h6">{report.overallSaturationPercent} %</Typography>
                  </CardContent>
                </Card>
              </Grid>
              <Grid item xs={4}>
                <Card variant="outlined">
                  <CardContent>
                    <Typography color="textSecondary" variant="caption">
                      Health Evaluation
                    </Typography>
                    <Typography variant="h6" color={getStatusColor(report.healthStatus)}>
                      {report.healthStatus}
                    </Typography>
                  </CardContent>
                </Card>
              </Grid>
            </Grid>

            <Typography variant="subtitle1" sx={{ mt: 2, mb: 1, fontWeight: 'bold' }}>
              Client Instance Thread Pool Breakdown
            </Typography>
            <Table size="small" sx={{ mb: 3 }}>
              <TableHead>
                <TableRow>
                  <TableCell>Client ID</TableCell>
                  <TableCell>Client IP</TableCell>
                  <TableCell>Active / Max</TableCell>
                  <TableCell>Queue Depth</TableCell>
                  <TableCell>Utilization</TableCell>
                  <TableCell>Status</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {report.clientThreadPoolStats.map((client, idx) => (
                  <TableRow key={idx}>
                    <TableCell sx={{ fontFamily: 'monospace', fontSize: '0.85rem' }}>
                      {client.clientId}
                    </TableCell>
                    <TableCell>{client.clientAddr}</TableCell>
                    <TableCell>
                      {client.activeThreadCount} / {client.maximumPoolSize}
                    </TableCell>
                    <TableCell>
                      {client.queueDepth} / {client.queueCapacity}
                    </TableCell>
                    <TableCell sx={{ width: '25%' }}>
                      <Box display="flex" alignItems="center">
                        <Box width="100%" mr={1}>
                          <LinearProgress
                            variant="determinate"
                            value={Math.min(client.poolUtilizationPercent, 100)}
                            color={client.isSaturated ? 'error' : 'primary'}
                          />
                        </Box>
                        <Box minWidth={35}>
                          <Typography variant="body2" color="textSecondary">
                            {client.poolUtilizationPercent}%
                          </Typography>
                        </Box>
                      </Box>
                    </TableCell>
                    <TableCell>
                      <Chip
                        label={client.isSaturated ? 'SATURATED' : 'OK'}
                        color={client.isSaturated ? 'error' : 'success'}
                        size="small"
                      />
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>

            {report.warnings && report.warnings.length > 0 && (
              <Box sx={{ mb: 2 }}>
                <Typography variant="subtitle2" color="error" sx={{ mb: 1 }}>
                  Capacity Warnings:
                </Typography>
                {report.warnings.map((warn, i) => (
                  <Alert severity="warning" key={i} sx={{ mb: 1 }}>
                    {warn}
                  </Alert>
                ))}
              </Box>
            )}

            {report.optimizationRecommendations && report.optimizationRecommendations.length > 0 && (
              <Box sx={{ mt: 2 }}>
                <Typography variant="subtitle2" color="textSecondary" sx={{ mb: 1 }}>
                  Optimization Recommendations:
                </Typography>
                {report.optimizationRecommendations.map((rec, i) => (
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
        <Button onClick={fetchThreadPoolSaturation} color="secondary">
          Refresh
        </Button>
        <Button onClick={onClose} color="primary" variant="contained">
          Close
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default ConsumerThreadPoolSaturationModal;
