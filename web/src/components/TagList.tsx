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

import type { CSSProperties } from 'react';
import { Flex, Tag, Tooltip } from 'antd';

export interface TagListItem {
  key: string;
  label: string;
  color?: string;
}

interface TagListProps {
  items: TagListItem[];
  /**
   * How many tags to render before collapsing the rest into a `+N` tag that lists them on hover.
   * Leave unset only when the value set is bounded and the column is wide enough for all of it.
   */
  max?: number;
  /** Truncates each tag to this width; the full label stays reachable via the native title. */
  tagMaxWidth?: number;
  gap?: number;
  style?: CSSProperties;
}

// antd gives `.ant-tag` an 8px `margin-inline-end`, which stacks on top of the container's own gap
// and makes the real spacing 12px when 4px was asked for. The container is the single source of
// truth for spacing here, so the tag margin is cancelled. 14px is the project-wide font floor;
// antd's own default for Tag is 12px.
const TAG_STYLE: CSSProperties = { marginInlineEnd: 0, fontSize: 14 };

const TagList = ({ items, max, tagMaxWidth, gap = 4, style }: TagListProps) => {
  const visible = max === undefined ? items : items.slice(0, max);
  const hidden = max === undefined ? [] : items.slice(max);
  const clip: CSSProperties | undefined = tagMaxWidth
    ? {
        maxWidth: tagMaxWidth,
        overflow: 'hidden',
        textOverflow: 'ellipsis',
        whiteSpace: 'nowrap',
      }
    : undefined;

  return (
    <Flex gap={gap} wrap={false} align="center" style={{ minWidth: 0, ...style }}>
      {visible.map((item) => (
        <Tag
          key={item.key}
          color={item.color}
          style={{ ...TAG_STYLE, ...clip }}
          title={tagMaxWidth ? item.label : undefined}
        >
          {item.label}
        </Tag>
      ))}
      {hidden.length > 0 && (
        <Tooltip
          trigger={['hover', 'focus']}
          title={hidden.map((item) => (
            <div key={item.key}>{item.label}</div>
          ))}
        >
          <Tag
            style={TAG_STYLE}
            tabIndex={0}
            aria-label={`+${hidden.length}: ${hidden.map((item) => item.label).join(', ')}`}
          >
            +{hidden.length}
          </Tag>
        </Tooltip>
      )}
    </Flex>
  );
};

export default TagList;
