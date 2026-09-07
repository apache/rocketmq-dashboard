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

/**
 * Minimum table width helpers.
 *
 * Without `scroll.x` antd keeps `table-layout: auto` and squeezes every column into the
 * viewport, so on a narrow window (or after zooming in) the declared column widths are
 * ignored and cells wrap. Passing a numeric `scroll.x` switches the table to
 * `table-layout: fixed` with `min-width: 100%`, which keeps the columns readable and shows
 * a horizontal scrollbar when the window is narrower, while still stretching to fill a
 * wide window.
 *
 * The value is derived from the column definitions instead of being hard-coded so it stays
 * correct when columns are added, removed or resized.
 */

/** Width antd reserves for the leading checkbox column (not part of `columns`). */
const SELECTION_COLUMN_WIDTH = 40;

/** Width antd reserves for the leading expand-icon column (not part of `columns`). */
const EXPAND_COLUMN_WIDTH = 48;

/** Fallback share for columns that declare no width, so they never collapse to nothing. */
const UNSIZED_COLUMN_WIDTH = 120;

interface ColumnLike {
  width?: number | string;
  children?: readonly ColumnLike[];
  hidden?: boolean;
}

export interface TableScrollXOptions {
  /** The table renders a `rowSelection` checkbox column. */
  selection?: boolean;
  /** The table renders an expandable row icon column. */
  expandable?: boolean;
  /** Extra pixels to reserve on top of the declared columns. */
  extra?: number;
}

function columnWidth(column: ColumnLike): number {
  if (column.hidden) {
    return 0;
  }
  if (column.children?.length) {
    return column.children.reduce((total, child) => total + columnWidth(child), 0);
  }
  if (typeof column.width === 'number') {
    return column.width;
  }
  if (typeof column.width === 'string') {
    const parsed = Number.parseFloat(column.width);
    return Number.isFinite(parsed) && column.width.endsWith('px') ? parsed : UNSIZED_COLUMN_WIDTH;
  }
  return UNSIZED_COLUMN_WIDTH;
}

/** Sums the declared column widths into the `scroll.x` a table needs to stay readable. */
export function tableScrollX(
  columns: readonly ColumnLike[] | undefined,
  options: TableScrollXOptions = {},
): number {
  const declared = (columns ?? []).reduce((total, column) => total + columnWidth(column), 0);
  return (
    declared +
    (options.selection ? SELECTION_COLUMN_WIDTH : 0) +
    (options.expandable ? EXPAND_COLUMN_WIDTH : 0) +
    (options.extra ?? 0)
  );
}
