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

const BrokerConfigDriftModal = ({ open, onClose }) => {
  const [loading, setLoading] = useState(false);
  const [report, setReport] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    if (open) {
      fetchConfigDriftReport();
    }
  }, [open]);

  const fetchConfigDriftReport = async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await get('/broker-config-drift/inspect.query');
      setReport(res);
    } catch (err) {
      setError(err.message || 'Failed to fetch broker configuration drift report');
    } finally {
      setLoading(false);
    }
  };

  const getSeverityChip = (severity) => {
    switch (severity) {
      case 'HIGH':
        return <Chip label="HIGH" color="error" size="small" />;
      case 'MEDIUM':
        return <Chip label="MEDIUM" color="warning" size="small" />;
      default:
        return <Chip label="LOW" color="info" size="small" />;
    }
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>Broker Dynamic Configuration Drift Console</DialogTitle>
      <DialogContent dividers>
        {loading && (
          <Box display="flex" justifyContent="center" my={4}>
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
            {/* Overview Summary */}
            <Box display="flex" gap={2} mb={3}>
              <Paper sx={{ p: 2, flex: 1, textAlign: 'center' }}>
                <Typography variant="caption" color="textSecondary">
                  Inspected Master Brokers
                </Typography>
                <Typography variant="h5">{report.totalBrokersInspected}</Typography>
              </Paper>
              <Paper sx={{ p: 2, flex: 1, textAlign: 'center' }}>
                <Typography variant="caption" color="textSecondary">
                  Drifted Brokers
                </Typography>
                <Typography variant="h5" color={report.driftedBrokerCount > 0 ? 'error.main' : 'success.main'}>
                  {report.driftedBrokerCount}
                </Typography>
              </Paper>
              <Paper sx={{ p: 2, flex: 1, textAlign: 'center' }}>
                <Typography variant="caption" color="textSecondary">
                  Critical Parameter Drift
                </Typography>
                <Box mt={0.5}>
                  <Chip
                    label={report.hasCriticalDrift ? 'YES (RISK)' : 'NO'}
                    color={report.hasCriticalDrift ? 'error' : 'success'}
                  />
                </Box>
              </Paper>
            </Box>

            <Divider sx={{ my: 2 }} />

            {/* Config Drift Table */}
            <Typography variant="h6" gutterBottom>
              Detected Cross-Broker Configuration Drifts ({report.driftItems?.length || 0})
            </Typography>
            <TableContainer component={Paper}>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Broker Address</TableCell>
                    <TableCell>Configuration Key</TableCell>
                    <TableCell>Expected Baseline</TableCell>
                    <TableCell>Actual Broker Value</TableCell>
                    <TableCell>Drift Severity</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {report.driftItems && report.driftItems.length > 0 ? (
                    report.driftItems.map((item, idx) => (
                      <TableRow key={idx}>
                        <TableCell><b>{item.brokerAddr}</b></TableCell>
                        <TableCell><code>{item.configKey}</code></TableCell>
                        <TableCell>{item.expectedBaselineValue}</TableCell>
                        <TableCell sx={{ color: 'error.main', fontWeight: 'bold' }}>
                          {item.actualBrokerValue}
                        </TableCell>
                        <TableCell>{getSeverityChip(item.driftSeverity)}</TableCell>
                      </TableRow>
                    ))
                  ) : (
                    <TableRow>
                      <TableCell colSpan={5} align="center">
                        All inspected broker dynamic configurations match the cluster baseline perfectly.
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
        <Button onClick={fetchConfigDriftReport} color="primary">
          Re-inspect
        </Button>
        <Button onClick={onClose}>Close</Button>
      </DialogActions>
    </Dialog>
  );
};

export default BrokerConfigDriftModal;
