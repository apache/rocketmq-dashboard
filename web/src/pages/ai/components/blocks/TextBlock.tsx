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

import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import type { TextBlock as TextBlockData } from '../../render/blocks';
import { normalizeAiMarkdown } from '../../render/markdown';

/**
 * Assistant prose, rendered as GitHub-flavoured Markdown.
 *
 * The `.ai-markdown` class carries the whole typographic contract (sizes, spacing, code and table
 * colours) and is themed through the `--ai-*` custom properties the page sets, so this component has
 * no styles of its own beyond opting into that class.
 *
 * The same component renders a live `text_delta` accumulation and a replayed persisted `text` event:
 * both reducers coalesce adjacent text into ONE block, so a streamed answer and the same answer
 * reloaded from the database produce identical DOM.
 */

export interface TextBlockProps {
  block: TextBlockData;
}

const TextBlock = ({ block }: TextBlockProps) => (
  <div className="ai-markdown" data-testid="ai-text-block">
    <ReactMarkdown remarkPlugins={[remarkGfm]}>{normalizeAiMarkdown(block.text)}</ReactMarkdown>
  </div>
);

export default TextBlock;
