/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 */
import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import ModelBadge, { modelBrandLogo } from '../ModelBadge';

describe('ModelBadge', () => {
  it('renders the brand logo for known model families', () => {
    for (const model of [
      'qwen-max',
      'claude-3.5-sonnet',
      'deepseek-r1',
      'gemini-2.0-flash',
      'gpt-4o',
    ]) {
      const { unmount } = render(<ModelBadge model={model} />);
      const badge = screen.getByTestId('ai-model-badge');
      expect(badge.tagName).toBe('IMG');
      expect(badge.getAttribute('alt')).toBe('');
      unmount();
    }
  });

  it('matches the brand prefix case-insensitively', () => {
    render(<ModelBadge model="QWEN-Max" />);
    expect(screen.getByTestId('ai-model-badge').tagName).toBe('IMG');
  });

  it('falls back to a neutral letter badge for unknown models', () => {
    render(<ModelBadge model="zephyr-7b" />);
    const badge = screen.getByTestId('ai-model-badge');
    expect(badge.tagName).toBe('SPAN');
    expect(badge.textContent).toBe('Z');
  });

  it('falls back to a question mark when the model is empty', () => {
    render(<ModelBadge model="" />);
    expect(screen.getByTestId('ai-model-badge').textContent).toBe('?');
  });

  it('honours the requested badge size', () => {
    const { unmount } = render(<ModelBadge model="qwen-max" size={24} />);
    const img = screen.getByTestId('ai-model-badge');
    expect(img.getAttribute('width')).toBe('24');
    expect(img.getAttribute('height')).toBe('24');
    unmount();

    render(<ModelBadge model="zephyr-7b" size={24} />);
    const span = screen.getByTestId('ai-model-badge') as HTMLElement;
    expect(span.style.width).toBe('24px');
    expect(span.style.height).toBe('24px');
  });

  it('defaults to the inline 16px size', () => {
    render(<ModelBadge model="qwen-max" />);
    expect(screen.getByTestId('ai-model-badge').getAttribute('width')).toBe('16');
  });
});

describe('modelBrandLogo', () => {
  it('exposes the same brand mapping the transcript avatar reuses', () => {
    expect(modelBrandLogo('kimi-k2')).not.toBeNull();
    expect(modelBrandLogo('glm-4-air')).not.toBeNull();
    expect(modelBrandLogo('llama-3.1-405b')).not.toBeNull();
  });

  it('returns null for unknown models so the caller can fall back', () => {
    expect(modelBrandLogo('totally-unknown-model')).toBeNull();
  });
});
