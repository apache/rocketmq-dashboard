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

import React from 'react';
import {
    CheckCircleOutlined,
    CloseCircleOutlined,
    WarningOutlined,
    StopOutlined,
    ThunderboltOutlined,
} from '@ant-design/icons';

/**
 * DryRunCard - 干运行预览卡片
 *
 * L2 (受控变更): 显示操作预览 + "确认执行"(绿色) / "取消"(灰色)
 * L3 (危险操作): 显示"已拒绝"红色卡片，不提供确认按钮
 */
function DryRunCard({ risk, operationName, targetResource, params, warnings, onConfirm, onCancel }) {
    // L3 — 已拒绝红色卡片
    if (risk === 'L3') {
        return (
            <div className="dryrun-card dryrun-card-rejected">
                <div className="dryrun-card-header">
                    <div className="dryrun-card-title">
                        <StopOutlined className="dryrun-card-icon-rejected" />
                        <span className="dryrun-card-op-name">已拒绝</span>
                        <span className="dryrun-card-risk-tag dryrun-risk-L3">危险操作</span>
                    </div>
                </div>
                <div className="dryrun-card-body">
                    <div className="dryrun-card-reason">
                        <WarningOutlined style={{ marginRight: 6, color: '#ff4d4f' }} />
                        此操作风险等级过高，已被安全策略自动拒绝
                    </div>
                    {operationName && (
                        <div className="dryrun-card-field">
                            <span className="dryrun-card-label">操作名称：</span>
                            <span className="dryrun-card-value dryrun-card-value-mono">{operationName}</span>
                        </div>
                    )}
                    {targetResource && (
                        <div className="dryrun-card-field">
                            <span className="dryrun-card-label">目标资源：</span>
                            <span className="dryrun-card-value">{targetResource}</span>
                        </div>
                    )}
                </div>
            </div>
        );
    }

    // L2 — 干运行预览卡片（默认）
    return (
        <div className="dryrun-card dryrun-card-preview">
            <div className="dryrun-card-header">
                <div className="dryrun-card-title">
                    <ThunderboltOutlined className="dryrun-card-icon-preview" />
                    <span className="dryrun-card-op-name">干运行预览</span>
                    <span className="dryrun-card-risk-tag dryrun-risk-L2">受控变更</span>
                </div>
            </div>

            <div className="dryrun-card-body">
                {operationName && (
                    <div className="dryrun-card-field">
                        <span className="dryrun-card-label">操作名称：</span>
                        <span className="dryrun-card-value dryrun-card-value-mono">{operationName}</span>
                    </div>
                )}
                {targetResource && (
                    <div className="dryrun-card-field">
                        <span className="dryrun-card-label">目标资源：</span>
                        <span className="dryrun-card-value">{targetResource}</span>
                    </div>
                )}
                {params && Object.keys(params).length > 0 && (
                    <div className="dryrun-card-field">
                        <span className="dryrun-card-label">参数列表：</span>
                        <pre className="dryrun-card-params">{typeof params === 'string' ? params : JSON.stringify(params, null, 2)}</pre>
                    </div>
                )}
                {warnings && warnings.length > 0 && (
                    <div className="dryrun-card-warnings">
                        <WarningOutlined style={{ marginRight: 4 }} />
                        {warnings.map((w, i) => (
                            <div key={i} className="dryrun-card-warning-item">
                                {typeof w === 'string' ? w : (w.message || JSON.stringify(w))}
                            </div>
                        ))}
                    </div>
                )}
            </div>

            <div className="dryrun-card-footer">
                <button className="dryrun-btn dryrun-btn-confirm" onClick={onConfirm}>
                    <CheckCircleOutlined style={{ marginRight: 4 }} />
                    确认执行
                </button>
                <button className="dryrun-btn dryrun-btn-cancel" onClick={onCancel}>
                    <CloseCircleOutlined style={{ marginRight: 4 }} />
                    取消
                </button>
            </div>
        </div>
    );
}

export default DryRunCard;