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
 * The brand logo in front of a model name, so the model selector reads at a glance.
 *
 * Model ids arrive as free text from the configured provider, so the mapping is a prefix match
 * on the lowercased id. Logos are the official brand marks from lobehub/lobe-icons (MIT, see
 * `assets/model-logos/LICENSE`), vendored as static SVGs so an offline deployment keeps them.
 * Unknown models fall back to a neutral letter badge.
 */

import baichuanLogo from '../../../assets/model-logos/baichuan-color.svg';
import chatglmLogo from '../../../assets/model-logos/chatglm-color.svg';
import claudeLogo from '../../../assets/model-logos/claude-color.svg';
import deepseekLogo from '../../../assets/model-logos/deepseek-color.svg';
import doubaoLogo from '../../../assets/model-logos/doubao-color.svg';
import geminiLogo from '../../../assets/model-logos/gemini-color.svg';
import grokLogo from '../../../assets/model-logos/grok.svg';
import hunyuanLogo from '../../../assets/model-logos/hunyuan-color.svg';
import kimiLogo from '../../../assets/model-logos/kimi-color.svg';
import metaLogo from '../../../assets/model-logos/metaai-color.svg';
import minimaxLogo from '../../../assets/model-logos/minimax-color.svg';
import mistralLogo from '../../../assets/model-logos/mistral-color.svg';
import moonshotLogo from '../../../assets/model-logos/moonshot.svg';
import ollamaLogo from '../../../assets/model-logos/ollama.svg';
import openaiLogo from '../../../assets/model-logos/openai.svg';
import qwenLogo from '../../../assets/model-logos/qwen-color.svg';
import sparkLogo from '../../../assets/model-logos/spark-color.svg';

interface ModelBrand {
  prefix: string;
  logo: string;
}

/** Longest prefix wins, so `gpt-4o` hits `gpt` but `glm-4` hits `glm` before `g`. */
const MODEL_BRANDS: ModelBrand[] = [
  { prefix: 'qwen', logo: qwenLogo },
  { prefix: 'chatgpt', logo: openaiLogo },
  { prefix: 'gpt', logo: openaiLogo },
  { prefix: 'o1', logo: openaiLogo },
  { prefix: 'o3', logo: openaiLogo },
  { prefix: 'o4', logo: openaiLogo },
  { prefix: 'claude', logo: claudeLogo },
  { prefix: 'gemini', logo: geminiLogo },
  { prefix: 'deepseek', logo: deepseekLogo },
  { prefix: 'kimi', logo: kimiLogo },
  { prefix: 'moonshot', logo: moonshotLogo },
  { prefix: 'llama', logo: metaLogo },
  { prefix: 'meta', logo: metaLogo },
  { prefix: 'ollama', logo: ollamaLogo },
  { prefix: 'glm', logo: chatglmLogo },
  { prefix: 'chatglm', logo: chatglmLogo },
  { prefix: 'doubao', logo: doubaoLogo },
  { prefix: 'hunyuan', logo: hunyuanLogo },
  { prefix: 'grok', logo: grokLogo },
  { prefix: 'mistral', logo: mistralLogo },
  { prefix: 'minimax', logo: minimaxLogo },
  { prefix: 'baichuan', logo: baichuanLogo },
  { prefix: 'spark', logo: sparkLogo },
];

const FALLBACK_COLOR = '#9CA3AF';

/** Exported so the assistant avatar in the transcript can reuse the exact same brand mapping. */
export function modelBrandLogo(model: string): string | null {
  const lower = model.toLowerCase();
  return MODEL_BRANDS.find((brand) => lower.startsWith(brand.prefix))?.logo ?? null;
}

export interface ModelBadgeProps {
  model: string;
  /** Badge edge length; defaults to 16px to sit inline with the 0.893rem selector text. */
  size?: number;
}

const ModelBadge = ({ model, size = 16 }: ModelBadgeProps) => {
  const logo = modelBrandLogo(model);
  if (logo) {
    return (
      <img
        src={logo}
        alt=""
        aria-hidden
        data-testid="ai-model-badge"
        width={size}
        height={size}
        style={{ flexShrink: 0, objectFit: 'contain', userSelect: 'none' }}
      />
    );
  }
  const letter = model.trim().charAt(0).toUpperCase() || '?';
  return (
    <span
      aria-hidden
      data-testid="ai-model-badge"
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        width: size,
        height: size,
        borderRadius: 4,
        background: FALLBACK_COLOR,
        color: '#fff',
        fontSize: Math.max(8, Math.round(size * 0.64)),
        fontWeight: 700,
        lineHeight: 1,
        flexShrink: 0,
        userSelect: 'none',
      }}
    >
      {letter}
    </span>
  );
};

export default ModelBadge;
