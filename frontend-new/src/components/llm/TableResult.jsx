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

import React, { useMemo } from 'react';
import { Table, Empty } from 'antd';

/**
 * TableResult — 使用 Ant Design Table 渲染表格结果
 * - 自动解析 columns / dataSource
 * - 中文分页标签
 * - 紧凑样式适配聊天面板
 */
function TableResult({ columns, dataSource, title }) {
    const resolvedColumns = useMemo(() => {
        if (!columns || columns.length === 0) return [];
        return columns.map((col, index) => {
            if (typeof col === 'string') {
                return {
                    title: col,
                    dataIndex: col,
                    key: col,
                    ellipsis: true,
                };
            }
            return {
                title: col.title || col.label || col.key || `列${index + 1}`,
                dataIndex: col.dataIndex || col.key,
                key: col.key || col.dataIndex || `col_${index}`,
                ellipsis: col.ellipsis !== false,
                width: col.width,
                render: col.render,
                sorter: col.sorter !== false ? (a, b) => {
                    const key = col.dataIndex || col.key;
                    const valA = a[key];
                    const valB = b[key];
                    if (typeof valA === 'number' && typeof valB === 'number') return valA - valB;
                    return String(valA || '').localeCompare(String(valB || ''));
                } : undefined,
            };
        });
    }, [columns]);

    const resolvedDataSource = useMemo(() => {
        if (!dataSource || dataSource.length === 0) return [];
        return dataSource.map((row, index) => ({
            ...row,
            _key: row.key || row.id || index,
        }));
    }, [dataSource]);

    if (!resolvedColumns.length) {
        return <Empty description="无列定义" image={Empty.PRESENTED_IMAGE_SIMPLE} />;
    }

    if (!resolvedDataSource.length) {
        return <Empty description="暂无数据" image={Empty.PRESENTED_IMAGE_SIMPLE} />;
    }

    return (
        <div className="table-result">
            {title && <div className="table-result-title">{title}</div>}
            <Table
                columns={resolvedColumns}
                dataSource={resolvedDataSource}
                rowKey="_key"
                size="small"
                bordered
                pagination={{
                    pageSize: 5,
                    size: 'small',
                    showSizeChanger: true,
                    pageSizeOptions: ['5', '10', '20'],
                    showTotal: (total, range) => `${range[0]}-${range[1]} / 共 ${total} 条`,
                }}
                scroll={{ x: 'max-content' }}
                className="table-result-ant"
            />
        </div>
    );
}

export default TableResult;