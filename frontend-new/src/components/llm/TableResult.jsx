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
import { Table } from 'antd';

function TableResult({ columns, dataSource, title }) {
    const resolvedColumns = columns && columns.length > 0
        ? columns.map((col, index) => ({
            title: typeof col === 'string' ? col : (col.title || col.key || `Column ${index + 1}`),
            dataIndex: typeof col === 'string' ? col : (col.dataIndex || col.key),
            key: typeof col === 'string' ? col : (col.key || col.dataIndex || `col_${index}`),
            sorter: (a, b) => {
                const key = typeof col === 'string' ? col : (col.dataIndex || col.key);
                const valA = a[key];
                const valB = b[key];
                if (typeof valA === 'number' && typeof valB === 'number') {
                    return valA - valB;
                }
                return String(valA || '').localeCompare(String(valB || ''));
            },
            ...(typeof col === 'object' ? col : {}),
        }))
        : [];

    const resolvedDataSource = dataSource && dataSource.length > 0
        ? dataSource.map((row, index) => ({ ...row, _key: row.key || index }))
        : [];

    if (!resolvedColumns.length || !resolvedDataSource.length) {
        return <div style={{ color: '#999', padding: '8px' }}>No data available</div>;
    }

    return (
        <div>
            {title && <h4 style={{ margin: '0 0 8px 0' }}>{title}</h4>}
            <Table
                columns={resolvedColumns}
                dataSource={resolvedDataSource}
                rowKey="_key"
                size="small"
                pagination={{ pageSize: 10, showSizeChanger: true, showTotal: (total) => `Total ${total} items` }}
                scroll={{ x: 'max-content' }}
            />
        </div>
    );
}

export default TableResult;
