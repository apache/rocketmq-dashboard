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
  LinearProgress,
} from '@mui/material';
import { get } from '../../api/remoteApi/remoteApi';

const TopicTrafficSkewModal = ({ open, onClose, topic }) => {
  const [loading, setLoading] = useState(false);
  const [report, setReport] = useState(null);
  const [error, setError] = useState(null);

  const fetchSkewReport = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const url = `/topic/skewInspection.query?topic=${encodeURIComponent(topic)}`;
      const res = await get(url);
      setReport(res);
    } catch (err) {
      setError(err.message || 'Failed to analyze topic traffic skew');
    } finally {
      setLoading(false);
    }
  }, [topic]);

  useEffect(() => {
    if (open && topic) {
      fetchSkewReport();
    }
  }, [open, topic, fetchSkewReport]);

  const getSkewLevelChip = (level) => {
    switch (level) {
      case 'SEVERE_SKEW':
        return <Chip label="SEVERE SKEW" color="error" size="small" />;
      case 'SLIGHT_SKEW':
        return <Chip label="SLIGHT SKEW" color="warning" size="small" />;
      case 'BALANCED':
        return <Chip label="BALANCED" color="success" size="small" />;
      default:
        return <Chip label={level || 'UNKNOWN'} size="small" />;
    }
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>Topic Partition Hotspot & Traffic Skew Diagnostic [{topic}]</DialogTitle>
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
                <Typography variant="caption" color="textSecondary">Total Queues</Typography>
                <Typography variant="body2" fontWeight="bold">{report.totalQueues}</Typography>
              </Paper>
              <Paper sx={{ p: 1.5, flex: '1 1 140px' }} variant="outlined">
                <Typography variant="caption" color="textSecondary">Total Messages</Typography>
                <Typography variant="body2" fontWeight="bold">{report.totalMessages.toLocaleString()}</Typography>
              </Paper>
              <Paper sx={{ p: 1.5, flex: '1 1 140px' }} variant="outlined">
                <Typography variant="caption" color="textSecondary">Gini Index</Typography>
                <Typography variant="body2" fontWeight="bold">{report.giniCoefficient}</Typography>
              </Paper>
              <Paper sx={{ p: 1.5, flex: '1 1 140px' }} variant="outlined">
                <Typography variant="caption" color="textSecondary">Status</Typography>
                <Box mt={0.5}>{getSkewLevelChip(report.skewLevel)}</Box>
              </Paper>
            </Box>

            {report.suggestion && (
              <Box mb={2}>
                <Alert severity={report.skewLevel === 'SEVERE_SKEW' ? 'warning' : 'info'}>
                  {report.suggestion}
                </Alert>
              </Box>
            )}

            <Typography variant="subtitle1" gutterBottom fontWeight="bold">
              Queue Message Distribution Breakdown
            </Typography>
            <TableContainer component={Paper} variant="outlined">
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Broker</TableCell>
                    <TableCell>Queue ID</TableCell>
                    <TableCell>Offset Range</TableCell>
                    <TableCell>Messages</TableCell>
                    <TableCell>Traffic Share</TableCell>
                    <TableCell>Status</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {report.queueDetails && report.queueDetails.length > 0 ? (
                    report.queueDetails.map((q, idx) => (
                      <TableRow key={idx} sx={{ bgcolor: q.isHotspot ? 'error.lighter' : 'inherit' }}>
                        <TableCell>{q.brokerName}</TableCell>
                        <TableCell>Queue #{q.queueId}</TableCell>
                        <TableCell>
                          {q.minOffset} ~ {q.maxOffset}
                        </TableCell>
                        <TableCell sx={{ fontWeight: 'bold' }}>{q.messageCount.toLocaleString()}</TableCell>
                        <TableCell sx={{ width: 160 }}>
                          <Box display="flex" alignItems="center">
                            <Box sx={{ width: '100%', mr: 1 }}>
                              <LinearProgress
                                variant="determinate"
                                value={Math.min(100, q.ratioPercent)}
                                color={q.isHotspot ? 'error' : 'primary'}
                              />
                            </Box>
                            <Typography variant="caption" color="textSecondary">
                              {q.ratioPercent}%
                            </Typography>
                          </Box>
                        </TableCell>
                        <TableCell>
                          {q.isHotspot ? (
                            <Chip label="HOTSPOT" color="error" size="small" />
                          ) : (
                            <Chip label="NORMAL" color="default" size="small" />
                          )}
                        </TableCell>
                      </TableRow>
                    ))
                  ) : (
                    <TableRow>
                      <TableCell colSpan={6} align="center">
                        No queue details available
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

export default TopicTrafficSkewModal;
