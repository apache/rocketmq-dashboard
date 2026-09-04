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

import React, { useState } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  TextField,
  Typography,
  Box,
  CircularProgress,
  Alert,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  Chip,
} from '@mui/material';
import { post } from '../api/remoteApi/remoteApi';

const MessageBatchOperationDialog = ({ open, onClose, selectedMsgIds = [], topic }) => {
  const [targetTopic, setTargetTopic] = useState(topic || '');
  const [tagRegex, setTagRegex] = useState('');
  const [concurrency, setConcurrency] = useState(5);
  const [delayMs, setDelayMs] = useState(10);
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);

  const handleExecuteBatchResend = async () => {
    setLoading(true);
    setError(null);
    setResult(null);

    try {
      const payload = {
        topic,
        targetTopic: targetTopic || topic,
        msgIds: selectedMsgIds,
        tagFilterRegex: tagRegex || null,
        maxConcurrency: Number(concurrency),
        rateLimitDelayMs: Number(delayMs),
      };

      const res = await post('/message-batch/resend.do', payload);
      setResult(res);
    } catch (err) {
      setError(err.message || 'Batch resend failed');
    } finally {
      setLoading(false);
    }
  };

  const handleExportCsv = async () => {
    try {
      const payload = {
        topic,
        msgIds: selectedMsgIds,
      };

      const response = await fetch('/message-batch/export.do', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(payload),
      });

      const blob = await response.blob();
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `message_export_${topic || 'batch'}.csv`;
      document.body.appendChild(a);
      a.click();
      a.remove();
    } catch (err) {
      setError('Export CSV failed: ' + err.message);
    }
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>
        Batch Resend & Export Pipeline ({selectedMsgIds.length} Messages Selected)
      </DialogTitle>
      <DialogContent dividers>
        {error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {error}
          </Alert>
        )}

        <Box display="flex" flexDirection="column" gap={2} mb={3}>
          <TextField
            label="Source Topic"
            value={topic || ''}
            disabled
            fullWidth
            size="small"
          />
          <TextField
            label="Target Resend Topic"
            value={targetTopic}
            onChange={(e) => setTargetTopic(e.target.value)}
            helperText="Leave empty to resend to original topic"
            fullWidth
            size="small"
          />
          <TextField
            label="Tag Regex Filter"
            value={tagRegex}
            onChange={(e) => setTagRegex(e.target.value)}
            placeholder="e.g. ^ORDER_.*"
            helperText="Only messages with matching tags will be dispatched"
            fullWidth
            size="small"
          />
          <Box display="flex" gap={2}>
            <TextField
              label="Max Concurrency"
              type="number"
              value={concurrency}
              onChange={(e) => setConcurrency(e.target.value)}
              size="small"
              sx={{ flex: 1 }}
            />
            <TextField
              label="Rate Limit Delay (ms)"
              type="number"
              value={delayMs}
              onChange={(e) => setDelayMs(e.target.value)}
              size="small"
              sx={{ flex: 1 }}
            />
          </Box>
        </Box>

        {loading && (
          <Box display="flex" justifyContent="center" my={3}>
            <CircularProgress />
          </Box>
        )}

        {result && (
          <Box mt={2}>
            <Typography variant="h6" gutterBottom>
              Batch Execution Summary
            </Typography>
            <Box display="flex" gap={2} mb={2}>
              <Chip label={`Total: ${result.totalProcessed}`} color="default" />
              <Chip label={`Success: ${result.successCount}`} color="success" />
              <Chip label={`Failed: ${result.failedCount}`} color="error" />
              <Chip label={`Filtered: ${result.filteredOutCount}`} color="warning" />
            </Box>

            <TableContainer component={Paper} sx={{ maxHeight: 250 }}>
              <Table size="small" stickyHeader>
                <TableHead>
                  <TableRow>
                    <TableCell>Message ID</TableCell>
                    <TableCell>Status</TableCell>
                    <TableCell>Detail / Remark</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {result.itemResults?.map((item, idx) => (
                    <TableRow key={idx}>
                      <TableCell>{item.msgId}</TableCell>
                      <TableCell>
                        <Chip
                          label={item.success ? 'SUCCESS' : 'FAILED'}
                          color={item.success ? 'success' : 'error'}
                          size="small"
                        />
                      </TableCell>
                      <TableCell>{item.remark}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          </Box>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={handleExportCsv} color="secondary">
          Export CSV Audit
        </Button>
        <Button
          onClick={handleExecuteBatchResend}
          color="primary"
          variant="contained"
          disabled={loading || selectedMsgIds.length === 0}
        >
          Execute Batch Resend
        </Button>
        <Button onClick={onClose}>Close</Button>
      </DialogActions>
    </Dialog>
  );
};

export default MessageBatchOperationDialog;
