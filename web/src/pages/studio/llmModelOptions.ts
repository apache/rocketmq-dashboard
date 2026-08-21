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

export const FALLBACK_MODELS: Record<string, string[]> = {
  openai: ['gpt-5.6-sol', 'gpt-5.6-terra', 'gpt-5.6-luna'],
  azure: ['gpt-5.6-sol', 'gpt-5.6-terra', 'gpt-5.6-luna'],
  anthropic: [
    'claude-fable-5',
    'claude-opus-5',
    'claude-opus-4-8',
    'claude-opus-4-7',
    'claude-sonnet-5',
    'claude-sonnet-4-6',
    'claude-haiku-4-5',
  ],
  deepseek: ['deepseek-chat', 'deepseek-reasoner'],
  tongyi: [
    'qwen3.8-max',
    'qwen3.7-max',
    'qwen3.7-plus',
    'deepseek-v4-pro',
    'deepseek-v4-flash',
    'MiniMax-M2.5',
    'glm-5.2',
  ],
  ollama: ['llama3', 'mistral', 'gemma2', 'qwen2.5'],
  bedrock: [
    'anthropic.claude-fable-5',
    'anthropic.claude-opus-5',
    'anthropic.claude-opus-4-8',
    'anthropic.claude-sonnet-5',
    'anthropic.claude-haiku-4-5',
    'meta.llama3-70b',
  ],
};

export function fallbackModelOptions(provider: string, model?: string) {
  const fallback = FALLBACK_MODELS[provider] || [model || ''].filter(Boolean);
  return fallback.map((item) => ({ value: item, label: item }));
}
