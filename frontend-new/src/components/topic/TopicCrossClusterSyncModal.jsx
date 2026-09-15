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

const TopicCrossClusterSyncModal = ({ open, onClose, topic }) => {
  const [loading, setLoading] = useState(false);
  const [syncing, setSyncing] = useState(false);
  const [report, setReport] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    if (open) {
      fetchComparison();
    }
  }, [open, topic]);

  const fetchComparison = async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await axios.get('/topic/crossClusterSync/compare.query', {
        params: { topic, sourceCluster: 'Cluster-East', targetCluster: 'Cluster-West' }
      });
      setReport(response.data);
    } catch (err) {
      setError(err.message || 'Failed to compare topic configurations across clusters');
    } finally {
      setLoading(false);
    }
  };

  const handleSync = async () => {
    setSyncing(true);
    setError(null);
    try {
      const response = await axios.post(
        `/topic/crossClusterSync/sync.do?topic=${encodeURIComponent(topic)}&sourceCluster=Cluster-East&targetCluster=Cluster-West`
      );
      setReport(response.data);
    } catch (err) {
      setError(err.message || 'Failed to synchronize topic configuration');
    } finally {
      setSyncing(false);
    }
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>
        <Box display="flex" justifyContent="space-between" alignItems="center">
          <Typography variant="h6">
            Cross-Cluster Topic Config Drift & Sync: {topic}
          </Typography>
          {report && (
            <Chip
              label={report.syncStatus}
              color={report.configurationInSync ? 'success' : 'error'}
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
                      Source Primary Cluster
                    </Typography>
                    <Typography variant="h6">{report.sourceCluster}</Typography>
                  </CardContent>
                </Card>
              </Grid>
              <Grid item xs={4}>
                <Card variant="outlined">
                  <CardContent>
                    <Typography color="textSecondary" variant="caption">
                      Target Replica Cluster
                    </Typography>
                    <Typography variant="h6">{report.targetCluster}</Typography>
                  </CardContent>
                </Card>
              </Grid>
              <Grid item xs={4}>
                <Card variant="outlined">
                  <CardContent>
                    <Typography color="textSecondary" variant="caption">
                      Drifted Attributes
                    </Typography>
                    <Typography variant="h6" color={report.totalDiscrepancies > 0 ? 'error' : 'success'}>
                      {report.totalDiscrepancies}
                    </Typography>
                  </CardContent>
                </Card>
              </Grid>
            </Grid>

            <Typography variant="subtitle1" sx={{ mt: 2, mb: 1, fontWeight: 'bold' }}>
              Attribute Configuration Drift Matrix
            </Typography>
            <Table size="small" sx={{ mb: 3 }}>
              <TableHead>
                <TableRow>
                  <TableCell>Topic Attribute</TableCell>
                  <TableCell>Source ({report.sourceCluster})</TableCell>
                  <TableCell>Target ({report.targetCluster})</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell>Remediation Plan</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {report.attributeDiffs.map((diff, idx) => (
                  <TableRow key={idx}>
                    <TableCell sx={{ fontWeight: 'bold' }}>{diff.attributeName}</TableCell>
                    <TableCell>{diff.sourceValue}</TableCell>
                    <TableCell sx={{ color: diff.drifted ? 'red' : 'inherit' }}>
                      {diff.targetValue}
                    </TableCell>
                    <TableCell>
                      <Chip
                        label={diff.drifted ? 'DRIFTED' : 'IN SYNC'}
                        color={diff.drifted ? 'error' : 'success'}
                        size="small"
                      />
                    </TableCell>
                    <TableCell>{diff.remediationRecommendation}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>

            {report.syncActionLog && report.syncActionLog.length > 0 && (
              <Box sx={{ mt: 2 }}>
                <Typography variant="subtitle2" color="textSecondary" sx={{ mb: 1 }}>
                  Synchronization Audit Trail:
                </Typography>
                {report.syncActionLog.map((logItem, i) => (
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
        <Button onClick={fetchComparison} color="secondary">
          Re-Check Drift
        </Button>
        <Button
          onClick={handleSync}
          color="primary"
          variant="contained"
          disabled={syncing || (report && report.configurationInSync)}
        >
          {syncing ? 'Synchronizing...' : 'Align Target Configuration'}
        </Button>
        <Button onClick={onClose} color="inherit">
          Close
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default TopicCrossClusterSyncModal;
