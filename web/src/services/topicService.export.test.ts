import { afterEach, describe, expect, it, vi } from 'vitest';

import { listTopicsPage } from '../api/metadata';
import { listAllTopics } from './topicService';

vi.mock('./dataMode', () => ({ isMockMode: () => false }));
vi.mock('../config', () => ({
  API_BASE_URL: '/api',
}));
vi.mock('../api/metadata', () => ({
  listTopicsPage: vi.fn(),
}));

describe('topic export pagination tolerance', () => {
  afterEach(() => {
    vi.clearAllMocks();
  });

  it('treats a null page payload as an empty page', async () => {
    vi.mocked(listTopicsPage).mockResolvedValueOnce(null as never);

    const topics = await listAllTopics();

    expect(topics).toEqual([]);
    expect(listTopicsPage).toHaveBeenCalledTimes(1);
  });

  it('treats a page payload without items as an empty page', async () => {
    vi.mocked(listTopicsPage).mockResolvedValueOnce({
      total: 3,
      page: 1,
      size: 100,
    } as never);

    const topics = await listAllTopics();

    expect(topics).toEqual([]);
  });

  it('collects every page and stops at the reported total', async () => {
    const pageOne = Array.from({ length: 100 }, (_, index) => ({ name: `topic-${index}` }));
    const pageTwo = Array.from(
      { length: 40 },
      (_, index) => ({ name: `topic-100-${index}` }),
    );
    vi.mocked(listTopicsPage)
      .mockResolvedValueOnce({ items: pageOne, total: 140, page: 1, size: 100 })
      .mockResolvedValueOnce({ items: pageTwo, total: 140, page: 2, size: 100 });

    const topics = await listAllTopics();

    expect(topics).toHaveLength(140);
    expect(listTopicsPage).toHaveBeenCalledTimes(2);
  });
});
