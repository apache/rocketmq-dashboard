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
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.rocketmq.dashboard.service.impl;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import org.apache.rocketmq.dashboard.config.RMQConfigure;
import org.apache.rocketmq.srvutil.FileWatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractFileStore {
    public final Logger log = LoggerFactory.getLogger(this.getClass());

    public String filePath;

    public AbstractFileStore(RMQConfigure configure, String fileName) {
        filePath = configure.getRocketMqDashboardDataPath() + File.separator + fileName;
        if (!new File(filePath).exists()) {
            // Use the default path
            InputStream inputStream = getClass().getResourceAsStream("/" + fileName);
            if (inputStream == null) {
                log.error(String.format("Can not found the file %s in Spring Boot jar", fileName));
                System.exit(1);
            } else {
                try {
                    load(inputStream);
                } catch (Exception e) {
                    log.error("fail to load file {}", filePath, e);
                } finally {
                    try {
                        inputStream.close();
                    } catch (IOException e) {
                        log.error("inputStream close exception", e);
                    }
                }
            }
        } else {
            log.info(String.format("configure file is %s", filePath));
            load();
            watch();
        }
    }

    abstract void load(InputStream inputStream);

    private void load() {
        load(null);
    }

    private boolean watch() {
        try {
            FileWatchService fileWatchService = new FileWatchService(new String[] {filePath}, new FileWatchService.Listener() {
                @Override
                public void onChanged(String path) {
                    log.info("The file changed, reload the context");
                    load();
                }
            });
            fileWatchService.start();
            log.info("Succeed to start FileWatcherService");
            return true;
        } catch (Exception e) {
            log.error("Failed to start FileWatcherService", e);
        }
        return false;
    }
}
