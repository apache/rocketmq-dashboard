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
  TextField,
  CircularProgress,
  MenuItem,
  Select,
  FormControl,
  InputLabel
} from '@mui/material';
import axios from 'axios';

const MessageSchemaRegistryModal = ({ open, onClose, topic }) => {
  const [loading, setLoading] = useState(false);
  const [testing, setTesting] = useState(false);
  const [report, setReport] = useState(null);
  const [error, setError] = useState(null);
  const [newSchema, setNewSchema] = useState('');
  const [compatMode, setCompatMode] = useState('BACKWARD');
  const [testPayload, setTestPayload] = useState('');
  const [payloadValid, setPayloadValid] = useState(null);

  useEffect(() => {
    if (open) {
      fetchSchemaReport();
    }
  }, [open, topic]);

  const fetchSchemaReport = async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await axios.get('/schema/overview.query', {
        params: { topic }
      });
      setReport(response.data);
      if (response.data) {
        setCompatMode(response.data.compatibilityMode || 'BACKWARD');
        setNewSchema(response.data.currentSchemaDefinition || '');
      }
    } catch (err) {
      setError(err.message || 'Failed to fetch topic schema registry details');
    } finally {
      setLoading(false);
    }
  };

  const handleTestCompatibility = async () => {
    setTesting(true);
    setError(null);
    try {
      const response = await axios.post(
        `/schema/compatibility/test.do?topic=${encodeURIComponent(topic)}&compatibilityMode=${compatMode}`,
        newSchema,
        { headers: { 'Content-Type': 'text/plain' } }
      );
      setReport(response.data);
    } catch (err) {
      setError(err.message || 'Compatibility check failed');
    } finally {
      setTesting(false);
    }
  };

  const handleValidatePayload = async () => {
    try {
      const res = await axios.post(
        `/schema/payload/validate.do?topic=${encodeURIComponent(topic)}`,
        testPayload,
        { headers: { 'Content-Type': 'text/plain' } }
      );
      setPayloadValid(res.data.valid);
    } catch (err) {
      setPayloadValid(false);
    }
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>
        <Box display="flex" justifyContent="space-between" alignItems="center">
          <Typography variant="h6">Message Schema Registry: {topic}</Typography>
          {report && (
            <Chip
              label={`v${report.currentVersion} • ${report.compatibilityMode}`}
              color="primary"
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
              <Grid item xs={6}>
                <Typography variant="subtitle2" sx={{ mb: 1, fontWeight: 'bold' }}>
                  Current Schema Definition ({report.schemaType})
                </Typography>
                <TextField
                  multiline
                  rows={8}
                  fullWidth
                  variant="outlined"
                  value={report.currentSchemaDefinition}
                  InputProps={{ readOnly: true, style: { fontFamily: 'monospace', fontSize: 12 } }}
                />
              </Grid>
              <Grid item xs={6}>
                <Box display="flex" justifyContent="space-between" alignItems="center" mb={1}>
                  <Typography variant="subtitle2" sx={{ fontWeight: 'bold' }}>
                    Candidate Schema (Evolution)
                  </Typography>
                  <FormControl size="small" sx={{ width: 140 }}>
                    <InputLabel>Mode</InputLabel>
                    <Select
                      value={compatMode}
                      label="Mode"
                      onChange={(e) => setCompatMode(e.target.value)}
                    >
                      <MenuItem value="BACKWARD">BACKWARD</MenuItem>
                      <MenuItem value="FORWARD">FORWARD</MenuItem>
                      <MenuItem value="FULL">FULL</MenuItem>
                      <MenuItem value="NONE">NONE</MenuItem>
                    </Select>
                  </FormControl>
                </Box>
                <TextField
                  multiline
                  rows={8}
                  fullWidth
                  variant="outlined"
                  value={newSchema}
                  onChange={(e) => setNewSchema(e.target.value)}
                  InputProps={{ style: { fontFamily: 'monospace', fontSize: 12 } }}
                />
              </Grid>
            </Grid>

            <Box display="flex" justifyContent="flex-end" mb={2}>
              <Button
                variant="contained"
                color="secondary"
                onClick={handleTestCompatibility}
                disabled={testing}
              >
                {testing ? 'Testing...' : 'Inspect Compatibility'}
              </Button>
            </Box>

            {report.evolutionCompatible !== undefined && (
              <Alert
                severity={report.evolutionCompatible ? 'success' : 'error'}
                sx={{ mb: 2 }}
              >
                Compatibility Result:{' '}
                {report.evolutionCompatible
                  ? 'Schema evolution is COMPATIBLE'
                  : 'Schema evolution is INCOMPATIBLE (Breaking Change)'}
              </Alert>
            )}

            {report.compatibilityDiffs && report.compatibilityDiffs.length > 0 && (
              <Box sx={{ mb: 2 }}>
                <Typography variant="caption" color="textSecondary">
                  Diff Analysis:
                </Typography>
                {report.compatibilityDiffs.map((d, i) => (
                  <Typography key={i} variant="body2" sx={{ fontFamily: 'monospace', color: '#555' }}>
                    • {d}
                  </Typography>
                ))}
              </Box>
            )}

            <Typography variant="subtitle1" sx={{ mt: 3, mb: 1, fontWeight: 'bold' }}>
              Version History
            </Typography>
            <Table size="small" sx={{ mb: 3 }}>
              <TableHead>
                <TableRow>
                  <TableCell>Version</TableCell>
                  <TableCell>Type</TableCell>
                  <TableCell>Created</TableCell>
                  <TableCell>Author</TableCell>
                  <TableCell>Comments</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {report.versionHistory.map((v, idx) => (
                  <TableRow key={idx}>
                    <TableCell>v{v.version}</TableCell>
                    <TableCell>{v.schemaType}</TableCell>
                    <TableCell>{new Date(v.createTime).toLocaleDateString()}</TableCell>
                    <TableCell>{v.author}</TableCell>
                    <TableCell>{v.comment}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>

            <Typography variant="subtitle1" sx={{ mt: 2, mb: 1, fontWeight: 'bold' }}>
              Sample Message Payload Preflight Validation
            </Typography>
            <Grid container spacing={2} alignItems="center">
              <Grid item xs={9}>
                <TextField
                  fullWidth
                  size="small"
                  placeholder='{"orderId": "10001", "amount": 99.5}'
                  value={testPayload}
                  onChange={(e) => setTestPayload(e.target.value)}
                  InputProps={{ style: { fontFamily: 'monospace' } }}
                />
              </Grid>
              <Grid item xs={3}>
                <Button variant="outlined" fullWidth onClick={handleValidatePayload}>
                  Validate Payload
                </Button>
              </Grid>
            </Grid>
            {payloadValid !== null && (
              <Alert severity={payloadValid ? 'success' : 'error'} sx={{ mt: 1 }}>
                Payload is {payloadValid ? 'Valid according to schema' : 'Invalid syntax/schema'}
              </Alert>
            )}
          </Box>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} color="primary">
          Close
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default MessageSchemaRegistryModal;
