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

import type { AlertRuleTransfer } from '../api/ops';

// Mirrors AlertRuleTransferService.MAX_RULES_PER_IMPORT; each file uses the existing import API.
export const ALERT_RULE_BACKUP_PART_LIMIT = 200;

export interface AlertRuleBackupPart {
  document: AlertRuleTransfer;
  filename: string;
}

export const buildAlertRuleBackupParts = (transfer: AlertRuleTransfer): AlertRuleBackupPart[] => {
  const total = Math.max(1, Math.ceil(transfer.rules.length / ALERT_RULE_BACKUP_PART_LIMIT));
  return Array.from({ length: total }, (_, index) => ({
    document: {
      ...transfer,
      rules: transfer.rules.slice(
        index * ALERT_RULE_BACKUP_PART_LIMIT,
        (index + 1) * ALERT_RULE_BACKUP_PART_LIMIT,
      ),
    },
    filename: `rocketmq-studio-${transfer.domain.toLowerCase()}-alert-rules${total > 1 ? `-part-${index + 1}-of-${total}` : ''}.json`,
  }));
};
