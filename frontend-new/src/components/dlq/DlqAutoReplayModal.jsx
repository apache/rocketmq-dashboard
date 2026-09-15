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
  TextField,
  CircularProgress,
  Switch,
  FormControlLabel
} from '@mui/material';
import axios from 'axios';

const DlqAutoReplayModal = ({ open, onClose, consumerGroup }) => {
  const [loading, setLoading] = useState(false);
  const [executing, setExecuting] = useState(false);
  const [report, setReport] = useState(null);
  const [error, setError] = useState(null);
  const [rateLimit, setRateLimit] = useState(50);
  const [maxBatch, setMaxBatch] = useState(500);
  const [stopOnFailure, setStopOnFailure] = useState(false);

  useEffect(() => {
    if (open) {
      fetchReplayStatus();
    }
  }, [open, consumerGroup]);

  const fetchReplayStatus = async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await axios.get('/dlq/autoReplay/status.query', {
        params: { consumerGroup }
      });
      setReport(response.data);
      if (response.data && response.data.policy) {
        setRateLimit(response.data.policy.rateLimitPerSecond || 50);
        setMaxBatch(response.data.policy.maxBatchSize || 500);
        setStopOnFailure(response.data.policy.stopOnFailure || false);
      }
    } catch (err) {
      setError(err.message || 'Failed to fetch DLQ auto replay status');
    } finally {
      setLoading(false);
    }
  };

  const handleTriggerReplay = async () => {
    setExecuting(true);
    setError(null);
    try {
      const response = await axios.post(
        `/dlq/autoReplay/execute.do?consumerGroup=${encodeURIComponent(consumerGroup)}`,
        {
          rateLimitPerSecond: rateLimit,
          maxBatchSize: maxBatch,
          stopOnFailure: stopOnFailure
        }
      );
      setReport(response.data);
    } catch (err) {
      setError(err.message || 'Failed to execute DLQ auto replay');
    } finally {
      setExecuting(false);
    }
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>
        <Box display="flex" justifyContent="space-between" alignItems="center">
          <Typography variant="h6">
            DLQ Auto-Replay & Throttle Scheduler: {consumerGroup}
          </Typography>
          {report && (
            <Chip
              label={report.status}
              color={report.status === 'IDLE' ? 'default' : 'primary'}
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
                      Pending DLQ Messages
                    </Typography>
                    <Typography variant="h6">{report.totalDlqMessages}</Typography>
                  </CardContent>
                </Card>
              </Grid>
              <Grid item xs={3}>
                <Card variant="outlined">
                  <CardContent>
                    <Typography color="textSecondary" variant="caption">
                      Replayed Messages
                    </Typography>
                    <Typography variant="h6" color="primary">
                      {report.replayedMessages}
                    </Typography>
                  </CardContent>
                </Card>
              </Grid>
              <Grid item xs={3}>
                <Card variant="outlined">
                  <CardContent>
                    <Typography color="textSecondary" variant="caption">
                      Failed Messages
                    </Typography>
                    <Typography variant="h6" color="error">
                      {report.failedMessages}
                    </Typography>
                  </CardContent>
                </Card>
              </Grid>
              <Grid item xs={3}>
                <Card variant="outlined">
                  <CardContent>
                    <Typography color="textSecondary" variant="caption">
                      Replay Success Rate
                    </Typography>
                    <Typography variant="h6">{report.replaySuccessRate} %</Typography>
                  </CardContent>
                </Card>
              </Grid>
            </Grid>

            <Typography variant="subtitle1" sx={{ mt: 2, mb: 1, fontWeight: 'bold' }}>
              Replay Rate Throttling Policy
            </Typography>
            <Grid container spacing={2} sx={{ mb: 3 }} alignItems="center">
              <Grid item xs={4}>
                <TextField
                  label="Rate Limit (msg/sec)"
                  type="number"
                  size="small"
                  fullWidth
                  value={rateLimit}
                  onChange={(e) => setRateLimit(Number(e.target.value))}
                />
              </Grid>
              <Grid item xs={4}>
                <TextField
                  label="Max Batch Size"
                  type="number"
                  size="small"
                  fullWidth
                  value={maxBatch}
                  onChange={(e) => setMaxBatch(Number(e.target.value))}
                />
              </Grid>
              <Grid item xs={4}>
                <FormControlLabel
                  control={
                    <Switch
                      checked={stopOnFailure}
                      onChange={(e) => setStopOnFailure(e.target.checked)}
                      color="primary"
                    />
                  }
                  label="Stop on First Failure"
                />
              </Grid>
            </Grid>

            <Typography variant="subtitle1" sx={{ mt: 2, mb: 1, fontWeight: 'bold' }}>
              Execution History
            </Typography>
            <Table size="small" sx={{ mb: 3 }}>
              <TableHead>
                <TableRow>
                  <TableCell>Batch ID</TableCell>
                  <TableCell>Processed</TableCell>
                  <TableCell>Success</TableCell>
                  <TableCell>Failed</TableCell>
                  <TableCell>Operator</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {report.executionHistory && report.executionHistory.length > 0 ? (
                  report.executionHistory.map((item, index) => (
                    <TableRow key={index}>
                      <TableCell>{item.batchId}</TableCell>
                      <TableCell>{item.count}</TableCell>
                      <TableCell sx={{ color: 'green' }}>{item.successCount}</TableCell>
                      <TableCell sx={{ color: 'red' }}>{item.failCount}</TableCell>
                      <TableCell>{item.operator}</TableCell>
                    </TableRow>
                  ))
                ) : (
                  <TableRow>
                    <TableCell colSpan={5} align="center">
                      No replay execution history.
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>

            {report.auditLogs && report.auditLogs.length > 0 && (
              <Box sx={{ mt: 2 }}>
                <Typography variant="subtitle2" color="textSecondary" sx={{ mb: 1 }}>
                  Audit Log:
                </Typography>
                {report.auditLogs.map((logItem, i) => (
                  <Typography key={i} variant="body2" sx={{ fontFamily: 'monospace', mb: 0.5 }}>
                    {logItem}
                  </Typography>
                ))}
              </Box>
            )}
          </Box>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={fetchReplayStatus} color="secondary">
          Refresh
        </Button>
        <Button
          onClick={handleTriggerReplay}
          color="primary"
          variant="contained"
          disabled={executing || (report && report.totalDlqMessages === 0)}
        >
          {executing ? 'Executing...' : 'Trigger Replay Now'}
        </Button>
        <Button onClick={onClose} color="inherit">
          Close
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default DlqAutoReplayModal;
