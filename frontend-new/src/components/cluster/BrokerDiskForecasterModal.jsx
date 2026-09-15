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

const BrokerDiskForecasterModal = ({ open, onClose, clusterName }) => {
  const [loading, setLoading] = useState(false);
  const [report, setReport] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    if (open) {
      fetchDiskForecast();
    }
  }, [open, clusterName]);

  const fetchDiskForecast = async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await axios.get('/cluster/diskForecast.query', {
        params: { clusterName }
      });
      setReport(response.data);
    } catch (err) {
      setError(err.message || 'Failed to fetch broker disk watermark forecast');
    } finally {
      setLoading(false);
    }
  };

  const getRiskColor = (level) => {
    if (level === 'HEALTHY') return 'success';
    if (level === 'WARNING') return 'warning';
    return 'error';
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="lg" fullWidth>
      <DialogTitle>
        <Box display="flex" justifyContent="space-between" alignItems="center">
          <Typography variant="h6">
            Broker Disk Capacity & Watermark Forecaster: {clusterName || 'DefaultCluster'}
          </Typography>
          {report && (
            <Chip
              label={report.riskLevel}
              color={getRiskColor(report.riskLevel)}
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
                      Total Monitored Brokers
                    </Typography>
                    <Typography variant="h6">{report.totalBrokers}</Typography>
                  </CardContent>
                </Card>
              </Grid>
              <Grid item xs={4}>
                <Card variant="outlined">
                  <CardContent>
                    <Typography color="textSecondary" variant="caption">
                      Highest Disk Watermark
                    </Typography>
                    <Typography variant="h6" color={getRiskColor(report.riskLevel)}>
                      {report.highestDiskUsagePercent} %
                    </Typography>
                  </CardContent>
                </Card>
              </Grid>
              <Grid item xs={4}>
                <Card variant="outlined">
                  <CardContent>
                    <Typography color="textSecondary" variant="caption">
                      Cluster Risk Assessment
                    </Typography>
                    <Typography variant="h6" color={getRiskColor(report.riskLevel)}>
                      {report.riskLevel}
                    </Typography>
                  </CardContent>
                </Card>
              </Grid>
            </Grid>

            <Typography variant="subtitle1" sx={{ mt: 2, mb: 1, fontWeight: 'bold' }}>
              Broker Storage Watermark & Exhaustion Projection
            </Typography>
            <Table size="small" sx={{ mb: 3 }}>
              <TableHead>
                <TableRow>
                  <TableCell>Broker</TableCell>
                  <TableCell>Address</TableCell>
                  <TableCell>Mount Path</TableCell>
                  <TableCell>Growth Rate</TableCell>
                  <TableCell>Hours to Exhaust</TableCell>
                  <TableCell>Watermark Usage</TableCell>
                  <TableCell>Status</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {report.brokerDisks.map((d, idx) => (
                  <TableRow key={idx}>
                    <TableCell sx={{ fontWeight: 'bold' }}>{d.brokerName}</TableCell>
                    <TableCell>{d.brokerAddr}</TableCell>
                    <TableCell>{d.mountPath}</TableCell>
                    <TableCell>{d.growthVelocityMbPerHour} MB/h</TableCell>
                    <TableCell sx={{ color: d.hoursUntilExhaustion < 72 ? 'red' : 'inherit' }}>
                      {d.hoursUntilExhaustion < 999 ? `${d.hoursUntilExhaustion} hrs` : '> 1000 hrs'}
                    </TableCell>
                    <TableCell sx={{ width: '22%' }}>
                      <Box display="flex" alignItems="center">
                        <Box width="100%" mr={1}>
                          <LinearProgress
                            variant="determinate"
                            value={d.usedPercent}
                            color={d.usedPercent >= 85 ? 'error' : d.usedPercent >= 75 ? 'warning' : 'primary'}
                          />
                        </Box>
                        <Box minWidth={45}>
                          <Typography variant="body2">{d.usedPercent}%</Typography>
                        </Box>
                      </Box>
                    </TableCell>
                    <TableCell>
                      <Chip
                        label={d.status}
                        color={d.status === 'CRITICAL' ? 'error' : d.status === 'WARNING' ? 'warning' : 'success'}
                        size="small"
                      />
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>

            {report.criticalAlerts && report.criticalAlerts.length > 0 && (
              <Box sx={{ mb: 2 }}>
                <Typography variant="subtitle2" color="error" sx={{ mb: 1 }}>
                  Capacity Threshold Alerts:
                </Typography>
                {report.criticalAlerts.map((alert, i) => (
                  <Alert severity="warning" key={i} sx={{ mb: 1 }}>
                    {alert}
                  </Alert>
                ))}
              </Box>
            )}

            {report.capacityRecommendations && report.capacityRecommendations.length > 0 && (
              <Box sx={{ mt: 2 }}>
                <Typography variant="subtitle2" color="textSecondary" sx={{ mb: 1 }}>
                  Mitigation Guidance:
                </Typography>
                {report.capacityRecommendations.map((rec, i) => (
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
        <Button onClick={fetchDiskForecast} color="secondary">
          Refresh
        </Button>
        <Button onClick={onClose} color="primary" variant="contained">
          Close
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default BrokerDiskForecasterModal;
