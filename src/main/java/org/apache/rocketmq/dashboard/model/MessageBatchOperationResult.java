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

package org.apache.rocketmq.dashboard.model;

import java.util.ArrayList;
import java.util.List;

public class MessageBatchOperationResult {

    private int totalProcessed;
    private int successCount;
    private int failedCount;
    private int filteredOutCount;
    private List<ItemResult> itemResults = new ArrayList<>();

    public int getTotalProcessed() {
        return totalProcessed;
    }

    public void setTotalProcessed(int totalProcessed) {
        this.totalProcessed = totalProcessed;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(int successCount) {
        this.successCount = successCount;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(int failedCount) {
        this.failedCount = failedCount;
    }

    public int getFilteredOutCount() {
        return filteredOutCount;
    }

    public void setFilteredOutCount(int filteredOutCount) {
        this.filteredOutCount = filteredOutCount;
    }

    public List<ItemResult> getItemResults() {
        return itemResults;
    }

    public void setItemResults(List<ItemResult> itemResults) {
        this.itemResults = itemResults;
    }

    public static class ItemResult {
        private String msgId;
        private boolean success;
        private String remark;

        public ItemResult() {}

        public ItemResult(String msgId, boolean success, String remark) {
            this.msgId = msgId;
            this.success = success;
            this.remark = remark;
        }

        public String getMsgId() {
            return msgId;
        }

        public void setMsgId(String msgId) {
            this.msgId = msgId;
        }

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public String getRemark() {
            return remark;
        }

        public void setRemark(String remark) {
            this.remark = remark;
        }
    }
}
