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

const ProducerLatencyProfilerModal = ({ open, onClose, topic, producerGroup }) => {
  const [loading, setLoading] = useState(false);
  const [report, setReport] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    if (open) {
      fetchLatencyReport();
    }
  }, [open, topic, producerGroup]);

  const fetchLatencyReport = async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await axios.get('/producer/latencyProfile.query', {
        params: { topic, producerGroup, timeWindowMinutes: 60 }
      });
      setReport(response.data);
    } catch (err) {
      setError(err.message || 'Failed to fetch producer latency profiling report');
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
            Producer Latency Profiler: {topic || 'All Topics'}
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
              <Grid item xs={3}>
                <Card variant="outlined">
                  <CardContent>
                    <Typography color="textSecondary" variant="caption">
                      Average Latency
                    </Typography>
                    <Typography variant="h6">
                      {report.avgLatencyMs} ms
                    </Typography>
                  </CardContent>
                </Card>
              </Grid>
              <Grid item xs={3}>
                <Card variant="outlined">
                  <CardContent>
                    <Typography color="textSecondary" variant="caption">
                      P95 Latency
                    </Typography>
                    <Typography variant="h6">
                      {report.p95LatencyMs} ms
                    </Typography>
                  </CardContent>
                </Card>
              </Grid>
              <Grid item xs={3}>
                <Card variant="outlined">
                  <CardContent>
                    <Typography color="textSecondary" variant="caption">
                      P99 Latency
                    </Typography>
                    <Typography variant="h6">
                      {report.p99LatencyMs} ms
                    </Typography>
                  </CardContent>
                </Card>
              </Grid>
              <Grid item xs={3}>
                <Card variant="outlined">
                  <CardContent>
                    <Typography color="textSecondary" variant="caption">
                      Timeout Rate
                    </Typography>
                    <Typography variant="h6">
                      {report.timeoutRatePercent} %
                    </Typography>
                  </CardContent>
                </Card>
              </Grid>
            </Grid>

            <Typography variant="subtitle1" sx={{ mt: 2, mb: 1, fontWeight: 'bold' }}>
              Latency Distribution Histogram
            </Typography>
            <Table size="small" sx={{ mb: 3 }}>
              <TableHead>
                <TableRow>
                  <TableCell>Latency Range</TableCell>
                  <TableCell>Sample Count</TableCell>
                  <TableCell>Percentage</TableCell>
                  <TableCell>Distribution</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {report.latencyHistogram.map((bucket, index) => (
                  <TableRow key={index}>
                    <TableCell>{bucket.rangeLabel}</TableCell>
                    <TableCell>{bucket.count}</TableCell>
                    <TableCell>{bucket.percentage}%</TableCell>
                    <TableCell sx={{ width: '35%' }}>
                      <LinearProgress
                        variant="determinate"
                        value={bucket.percentage}
                        color={bucket.rangeLabel.includes('>') ? 'error' : 'primary'}
                      />
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>

            <Typography variant="subtitle1" sx={{ mt: 2, mb: 1, fontWeight: 'bold' }}>
              Broker Latency Metrics
            </Typography>
            <Table size="small" sx={{ mb: 3 }}>
              <TableHead>
                <TableRow>
                  <TableCell>Broker Name</TableCell>
                  <TableCell>Address</TableCell>
                  <TableCell>Avg RT</TableCell>
                  <TableCell>P95 RT</TableCell>
                  <TableCell>Timeouts</TableCell>
                  <TableCell>Status</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {report.brokerLatencyStats.map((broker, idx) => (
                  <TableRow key={idx}>
                    <TableCell>{broker.brokerName}</TableCell>
                    <TableCell>{broker.brokerAddr}</TableCell>
                    <TableCell>{broker.avgLatencyMs} ms</TableCell>
                    <TableCell>{broker.p95LatencyMs} ms</TableCell>
                    <TableCell>{broker.timeoutCount}</TableCell>
                    <TableCell>
                      <Chip
                        label={broker.slowBroker ? 'SLOW BROKER' : 'NORMAL'}
                        color={broker.slowBroker ? 'error' : 'success'}
                        size="small"
                      />
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>

            {report.diagnosticSuggestions && report.diagnosticSuggestions.length > 0 && (
              <Box sx={{ mt: 2 }}>
                <Typography variant="subtitle2" color="textSecondary" sx={{ mb: 1 }}>
                  Diagnostic Recommendations:
                </Typography>
                {report.diagnosticSuggestions.map((sug, i) => (
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
        <Button onClick={fetchLatencyReport} color="secondary">
          Refresh
        </Button>
        <Button onClick={onClose} color="primary" variant="contained">
          Close
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default ProducerLatencyProfilerModal;
