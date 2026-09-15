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

const TopicDeduplicationHealthModal = ({ open, onClose, topic }) => {
  const [loading, setLoading] = useState(false);
  const [report, setReport] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    if (open) {
      fetchDeduplicationHealth();
    }
  }, [open, topic]);

  const fetchDeduplicationHealth = async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await axios.get('/topic/dedupHealth.query', {
        params: { topic, sampleSize: 1000 }
      });
      setReport(response.data);
    } catch (err) {
      setError(err.message || 'Failed to fetch topic message deduplication report');
    } finally {
      setLoading(false);
    }
  };

  const getScoreColor = (score) => {
    if (score === 'HEALTHY') return 'success';
    if (score === 'MODERATE_RISK') return 'warning';
    return 'error';
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>
        <Box display="flex" justifyContent="space-between" alignItems="center">
          <Typography variant="h6">
            Message Key Deduplication & Idempotency Health: {topic}
          </Typography>
          {report && (
            <Chip
              label={report.idempotencyHealthScore}
              color={getScoreColor(report.idempotencyHealthScore)}
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
                      Sampled Messages
                    </Typography>
                    <Typography variant="h6">{report.sampledMessageCount}</Typography>
                  </CardContent>
                </Card>
              </Grid>
              <Grid item xs={3}>
                <Card variant="outlined">
                  <CardContent>
                    <Typography color="textSecondary" variant="caption">
                      Unique Keys
                    </Typography>
                    <Typography variant="h6">{report.uniqueKeyCount}</Typography>
                  </CardContent>
                </Card>
              </Grid>
              <Grid item xs={3}>
                <Card variant="outlined">
                  <CardContent>
                    <Typography color="textSecondary" variant="caption">
                      Duplicate Keys
                    </Typography>
                    <Typography variant="h6" color="error">
                      {report.duplicateKeyCount}
                    </Typography>
                  </CardContent>
                </Card>
              </Grid>
              <Grid item xs={3}>
                <Card variant="outlined">
                  <CardContent>
                    <Typography color="textSecondary" variant="caption">
                      Duplication Ratio
                    </Typography>
                    <Typography variant="h6">{report.duplicateRatioPercent} %</Typography>
                  </CardContent>
                </Card>
              </Grid>
            </Grid>

            <Typography variant="subtitle1" sx={{ mt: 2, mb: 1, fontWeight: 'bold' }}>
              Duplicate Time Interval Distribution
            </Typography>
            <Table size="small" sx={{ mb: 3 }}>
              <TableHead>
                <TableRow>
                  <TableCell>Time Delta Window</TableCell>
                  <TableCell>Duplicate Count</TableCell>
                  <TableCell>Ratio</TableCell>
                  <TableCell>Percentage Distribution</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {report.timeWindowDistribution.map((w, idx) => (
                  <TableRow key={idx}>
                    <TableCell>{w.intervalLabel}</TableCell>
                    <TableCell>{w.duplicateCount}</TableCell>
                    <TableCell>{w.percentage}%</TableCell>
                    <TableCell sx={{ width: '35%' }}>
                      <LinearProgress
                        variant="determinate"
                        value={w.percentage}
                        color={w.intervalLabel.includes('< 1s') ? 'error' : 'primary'}
                      />
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>

            <Typography variant="subtitle1" sx={{ mt: 2, mb: 1, fontWeight: 'bold' }}>
              Top Duplicate Business Keys
            </Typography>
            <Table size="small" sx={{ mb: 3 }}>
              <TableHead>
                <TableRow>
                  <TableCell>Message Key</TableCell>
                  <TableCell>Occurrences</TableCell>
                  <TableCell>Min Interval</TableCell>
                  <TableCell>Sample MsgIDs</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {report.topDuplicateKeys.map((k, idx) => (
                  <TableRow key={idx}>
                    <TableCell sx={{ fontFamily: 'monospace', fontWeight: 'bold' }}>
                      {k.messageKey}
                    </TableCell>
                    <TableCell sx={{ color: 'red' }}>{k.occurrences}x</TableCell>
                    <TableCell>{k.minIntervalMs} ms</TableCell>
                    <TableCell sx={{ fontFamily: 'monospace', fontSize: '0.8rem' }}>
                      {k.sampleMsgIds}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>

            {report.idempotencyRecommendations && report.idempotencyRecommendations.length > 0 && (
              <Box sx={{ mt: 2 }}>
                <Typography variant="subtitle2" color="textSecondary" sx={{ mb: 1 }}>
                  Idempotency Tuning Recommendations:
                </Typography>
                {report.idempotencyRecommendations.map((rec, i) => (
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
        <Button onClick={fetchDeduplicationHealth} color="secondary">
          Refresh
        </Button>
        <Button onClick={onClose} color="primary" variant="contained">
          Close
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default TopicDeduplicationHealthModal;
