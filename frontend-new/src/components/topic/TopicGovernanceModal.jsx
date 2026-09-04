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
import { get } from '../../api/remoteApi/remoteApi';

const TopicGovernanceModal = ({ open, onClose }) => {
  const [loading, setLoading] = useState(false);
  const [report, setReport] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    if (open) {
      fetchGovernanceAudit();
    }
  }, [open]);

  const fetchGovernanceAudit = async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await get('/topic-governance/audit.query');
      setReport(res);
    } catch (err) {
      setError(err.message || 'Failed to fetch topic governance report');
    } finally {
      setLoading(false);
    }
  };

  const getAnomalyChip = (type) => {
    switch (type) {
      case 'ZOMBIE_TOPIC':
        return <Chip label="ZOMBIE TOPIC" color="error" size="small" />;
      case 'ORPHAN_TOPIC':
        return <Chip label="ORPHAN TOPIC" color="warning" size="small" />;
      case 'OVERSIZED_QUEUES':
        return <Chip label="OVERSIZED QUEUES" color="info" size="small" />;
      default:
        return <Chip label={type} size="small" />;
    }
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>Topic Lifecycle Governance & Zombie Audit Console</DialogTitle>
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
                  Audited Topics
                </Typography>
                <Typography variant="h5">{report.totalTopicsCount}</Typography>
              </Paper>
              <Paper sx={{ p: 2, flex: 1, textAlign: 'center' }}>
                <Typography variant="caption" color="textSecondary">
                  Orphan Topics (No Subscribers)
                </Typography>
                <Typography variant="h5" color="warning.main">
                  {report.orphanTopicsCount}
                </Typography>
              </Paper>
              <Paper sx={{ p: 2, flex: 1, textAlign: 'center' }}>
                <Typography variant="caption" color="textSecondary">
                  Zombie Topics
                </Typography>
                <Typography variant="h5" color="error.main">
                  {report.zombieTopicsCount}
                </Typography>
              </Paper>
              <Paper sx={{ p: 2, flex: 1, textAlign: 'center' }}>
                <Typography variant="caption" color="textSecondary">
                  Oversized Queue Topics
                </Typography>
                <Typography variant="h5" color="info.main">
                  {report.oversizedQueueTopicsCount}
                </Typography>
              </Paper>
            </Box>

            <Divider sx={{ my: 2 }} />

            {/* Anomalous Topics Table */}
            <Typography variant="h6" gutterBottom>
              Governance Findings & Action Items ({report.governanceItems?.length || 0})
            </Typography>
            <TableContainer component={Paper}>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Topic Name</TableCell>
                    <TableCell>Anomaly Category</TableCell>
                    <TableCell align="right">Read/Write Queues</TableCell>
                    <TableCell align="right">Consumers</TableCell>
                    <TableCell>Risk Context</TableCell>
                    <TableCell>Suggested Remediation</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {report.governanceItems && report.governanceItems.length > 0 ? (
                    report.governanceItems.map((item, idx) => (
                      <TableRow key={idx}>
                        <TableCell><b>{item.topicName}</b></TableCell>
                        <TableCell>{getAnomalyChip(item.anomalyType)}</TableCell>
                        <TableCell align="right">{item.readQueueNums} / {item.writeQueueNums}</TableCell>
                        <TableCell align="right">{item.consumerGroupCount}</TableCell>
                        <TableCell>{item.riskReason}</TableCell>
                        <TableCell sx={{ color: 'text.secondary', fontSize: '0.85rem' }}>
                          {item.suggestedAction}
                        </TableCell>
                      </TableRow>
                    ))
                  ) : (
                    <TableRow>
                      <TableCell colSpan={6} align="center">
                        All topics match healthy governance standards. No zombie or orphan topics detected.
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
        <Button onClick={fetchGovernanceAudit} color="primary">
          Re-audit
        </Button>
        <Button onClick={onClose}>Close</Button>
      </DialogActions>
    </Dialog>
  );
};

export default TopicGovernanceModal;
