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
 * Without `scroll.x` antd keeps the table squeezed into the viewport, so on a narrow window (or
 * after zooming in) the declared column widths are ignored and cells wrap. Passing a numeric
 * `scroll.x` sets `width` plus `min-width: 100%` on the table, which keeps the columns readable
 * and shows a horizontal scrollbar when the window is narrower, while still stretching to fill a
 * wide window.
 *
 * `scroll.x` on its own is NOT enough to make the declared widths authoritative: rc-table only
 * switches to `table-layout: fixed` when `scroll.y` is set, a column is `fixed`, or a column is
 * `ellipsis`. Otherwise the table stays on `table-layout: auto`, where the widths are merely
 * advisory and a single long cell value (a long topic or group name) stretches its column and
 * pushes the real table width past the value computed here. Every table therefore also passes
 * `tableLayout="fixed"` explicitly.
 *
 * Under `table-layout: fixed` the table still carries `min-width: 100%`, so on a window wider
 * than this value the surplus is shared out across every column in proportion to its width —
 * inflating narrow columns (a checkbox, an expand arrow, a `Push`/`Pull` tag) far beyond what
 * their content needs. To keep the surplus in one place, give the primary text column
 * `minWidth` instead of `width`: a column with no fixed `width` is the only one that grows,
 * every other column keeps its declared width exactly, and `minWidth` remains its floor on
 * narrow windows. That is why `minWidth` counts towards the total below.
 *
 * The value is derived from the column definitions instead of being hard-coded so it stays
 * correct when columns are added, removed or resized.
 */

/**
 * Width antd reserves for the leading checkbox column (not part of `columns`).
 *
 * antd renders this column at 32px for `size="small"` tables, which is what every list page
 * here uses. Reserving the 40px of a default-size table over-estimates the total by 8px and
 * that alone is enough to force a horizontal scrollbar on a table that would otherwise fit.
 */
const SELECTION_COLUMN_WIDTH = 32;

/** Width antd reserves for the leading expand-icon column (not part of `columns`). */
const EXPAND_COLUMN_WIDTH = 48;

/** Fallback share for columns that declare no width, so they never collapse to nothing. */
const UNSIZED_COLUMN_WIDTH = 120;

interface ColumnLike {
  width?: number | string;
  /** Floor for a flexible column that carries no fixed `width`. */
  minWidth?: number;
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
  if (typeof column.minWidth === 'number') {
    return column.minWidth;
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
