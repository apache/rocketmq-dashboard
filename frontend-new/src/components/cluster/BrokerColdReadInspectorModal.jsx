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

const BrokerColdReadInspectorModal = ({ open, onClose, clusterName }) => {
  const [loading, setLoading] = useState(false);
  const [report, setReport] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    if (open) {
      fetchColdReadMetrics();
    }
  }, [open, clusterName]);

  const fetchColdReadMetrics = async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await axios.get('/cluster/coldRead.query', {
        params: { clusterName }
      });
      setReport(response.data);
    } catch (err) {
      setError(err.message || 'Failed to fetch broker cold read metrics');
    } finally {
      setLoading(false);
    }
  };

  const getPressureColor = (status) => {
    if (status === 'NORMAL') return 'success';
    if (status === 'MODERATE') return 'warning';
    return 'error';
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="lg" fullWidth>
      <DialogTitle>
        <Box display="flex" justifyContent="space-between" alignItems="center">
          <Typography variant="h6">
            PageCache Hit Rate & Cold Data Read Inspector: {clusterName || 'DefaultCluster'}
          </Typography>
          {report && (
            <Chip
              label={report.diskPressureStatus}
              color={getPressureColor(report.diskPressureStatus)}
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
                      Overall PageCache Hit Rate
                    </Typography>
                    <Typography variant="h6" color={report.overallPageCacheHitRatePercent < 90 ? 'error' : 'primary'}>
                      {report.overallPageCacheHitRatePercent} %
                    </Typography>
                  </CardContent>
                </Card>
              </Grid>
              <Grid item xs={4}>
                <Card variant="outlined">
                  <CardContent>
                    <Typography color="textSecondary" variant="caption">
                      Total Cold Read Throughput
                    </Typography>
                    <Typography variant="h6">{report.totalColdReadTps} TPS</Typography>
                  </CardContent>
                </Card>
              </Grid>
              <Grid item xs={4}>
                <Card variant="outlined">
                  <CardContent>
                    <Typography color="textSecondary" variant="caption">
                      Disk I/O Pressure State
                    </Typography>
                    <Typography variant="h6" color={getPressureColor(report.diskPressureStatus)}>
                      {report.diskPressureStatus}
                    </Typography>
                  </CardContent>
                </Card>
              </Grid>
            </Grid>

            <Typography variant="subtitle1" sx={{ mt: 2, mb: 1, fontWeight: 'bold' }}>
              Broker Storage PageCache Performance Matrix
            </Typography>
            <Table size="small" sx={{ mb: 3 }}>
              <TableHead>
                <TableRow>
                  <TableCell>Broker Name</TableCell>
                  <TableCell>Address</TableCell>
                  <TableCell>Total Read TPS</TableCell>
                  <TableCell>Cold Read TPS</TableCell>
                  <TableCell>Physical Disk Read</TableCell>
                  <TableCell>PageCache Hit Rate</TableCell>
                  <TableCell>Pressure</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {report.brokerStats.map((b, idx) => (
                  <TableRow key={idx}>
                    <TableCell sx={{ fontWeight: 'bold' }}>{b.brokerName}</TableCell>
                    <TableCell>{b.brokerAddr}</TableCell>
                    <TableCell>{b.totalReadTps}</TableCell>
                    <TableCell sx={{ color: b.coldReadTps > 100 ? 'red' : 'inherit' }}>
                      {b.coldReadTps}
                    </TableCell>
                    <TableCell>{b.physicalDiskReadMbSec} MB/s</TableCell>
                    <TableCell sx={{ width: '25%' }}>
                      <Box display="flex" alignItems="center">
                        <Box width="100%" mr={1}>
                          <LinearProgress
                            variant="determinate"
                            value={b.pageCacheHitRatePercent}
                            color={b.pageCacheHitRatePercent < 90 ? 'error' : 'primary'}
                          />
                        </Box>
                        <Box minWidth={35}>
                          <Typography variant="body2">{b.pageCacheHitRatePercent}%</Typography>
                        </Box>
                      </Box>
                    </TableCell>
                    <TableCell>
                      <Chip
                        label={b.highDiskPressure ? 'HIGH' : 'NORMAL'}
                        color={b.highDiskPressure ? 'error' : 'success'}
                        size="small"
                      />
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>

            <Typography variant="subtitle1" sx={{ mt: 2, mb: 1, fontWeight: 'bold' }}>
              Top Consumer Groups Triggering Cold Reads
            </Typography>
            <Table size="small" sx={{ mb: 3 }}>
              <TableHead>
                <TableRow>
                  <TableCell>Consumer Group</TableCell>
                  <TableCell>Target Topic</TableCell>
                  <TableCell>Current Message Lag</TableCell>
                  <TableCell>Cold Read TPS</TableCell>
                  <TableCell>Offset Distance</TableCell>
                  <TableCell>Impact Level</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {report.topColdConsumerGroups.map((g, idx) => (
                  <TableRow key={idx}>
                    <TableCell sx={{ fontFamily: 'monospace', fontWeight: 'bold' }}>
                      {g.consumerGroup}
                    </TableCell>
                    <TableCell>{g.targetTopic}</TableCell>
                    <TableCell sx={{ color: 'red' }}>{g.messageLag.toLocaleString()}</TableCell>
                    <TableCell>{g.coldReadTps} TPS</TableCell>
                    <TableCell>{(g.readOffsetDistance / 1024 / 1024).toFixed(1)} MB</TableCell>
                    <TableCell>
                      <Chip
                        label={g.impactLevel}
                        color={g.impactLevel.includes('HIGH') ? 'error' : 'warning'}
                        size="small"
                      />
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>

            {report.cacheTuningSuggestions && report.cacheTuningSuggestions.length > 0 && (
              <Box sx={{ mt: 2 }}>
                <Typography variant="subtitle2" color="textSecondary" sx={{ mb: 1 }}>
                  Cache & Cold Read Optimization Suggestions:
                </Typography>
                {report.cacheTuningSuggestions.map((sug, i) => (
                  <Alert severity="info" key={i} sx={{ mb: 1 }}>
                    {sug}
                  </Alert>
                ))}
              </Box>
            )}
          </Box>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={fetchColdReadMetrics} color="secondary">
          Refresh
        </Button>
        <Button onClick={onClose} color="primary" variant="contained">
          Close
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default BrokerColdReadInspectorModal;
