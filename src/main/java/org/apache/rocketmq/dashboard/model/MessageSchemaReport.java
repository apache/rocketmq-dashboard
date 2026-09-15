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

public class MessageSchemaReport {
    private String topic;
    private String schemaName;
    private String schemaType;
    private int currentVersion;
    private String compatibilityMode;
    private String currentSchemaDefinition;
    private boolean isEvolutionCompatible;
    private List<SchemaVersionItem> versionHistory = new ArrayList<>();
    private List<String> compatibilityDiffs = new ArrayList<>();
    private List<String> validationErrors = new ArrayList<>();

    public static class SchemaVersionItem {
        private int version;
        private String schemaType;
        private long createTime;
        private String author;
        private String comment;
        private String schemaContent;

        public SchemaVersionItem() {
        }

        public SchemaVersionItem(int version, String schemaType, long createTime, String author, String comment,
            String schemaContent) {
            this.version = version;
            this.schemaType = schemaType;
            this.createTime = createTime;
            this.author = author;
            this.comment = comment;
            this.schemaContent = schemaContent;
        }

        public int getVersion() {
            return version;
        }

        public void setVersion(int version) {
            this.version = version;
        }

        public String getSchemaType() {
            return schemaType;
        }

        public void setSchemaType(String schemaType) {
            this.schemaType = schemaType;
        }

        public long getCreateTime() {
            return createTime;
        }

        public void setCreateTime(long createTime) {
            this.createTime = createTime;
        }

        public String getAuthor() {
            return author;
        }

        public void setAuthor(String author) {
            this.author = author;
        }

        public String getComment() {
            return comment;
        }

        public void setComment(String comment) {
            this.comment = comment;
        }

        public String getSchemaContent() {
            return schemaContent;
        }

        public void setSchemaContent(String schemaContent) {
            this.schemaContent = schemaContent;
        }
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getSchemaName() {
        return schemaName;
    }

    public void setSchemaName(String schemaName) {
        this.schemaName = schemaName;
    }

    public String getSchemaType() {
        return schemaType;
    }

    public void setSchemaType(String schemaType) {
        this.schemaType = schemaType;
    }

    public int getCurrentVersion() {
        return currentVersion;
    }

    public void setCurrentVersion(int currentVersion) {
        this.currentVersion = currentVersion;
    }

    public String getCompatibilityMode() {
        return compatibilityMode;
    }

    public void setCompatibilityMode(String compatibilityMode) {
        this.compatibilityMode = compatibilityMode;
    }

    public String getCurrentSchemaDefinition() {
        return currentSchemaDefinition;
    }

    public void setCurrentSchemaDefinition(String currentSchemaDefinition) {
        this.currentSchemaDefinition = currentSchemaDefinition;
    }

    public boolean isEvolutionCompatible() {
        return isEvolutionCompatible;
    }

    public void setEvolutionCompatible(boolean evolutionCompatible) {
        isEvolutionCompatible = evolutionCompatible;
    }

    public List<SchemaVersionItem> getVersionHistory() {
        return versionHistory;
    }

    public void setVersionHistory(List<SchemaVersionItem> versionHistory) {
        this.versionHistory = versionHistory;
    }

    public List<String> getCompatibilityDiffs() {
        return compatibilityDiffs;
    }

    public void setCompatibilityDiffs(List<String> compatibilityDiffs) {
        this.compatibilityDiffs = compatibilityDiffs;
    }

    public List<String> getValidationErrors() {
        return validationErrors;
    }

    public void setValidationErrors(List<String> validationErrors) {
        this.validationErrors = validationErrors;
    }
}
