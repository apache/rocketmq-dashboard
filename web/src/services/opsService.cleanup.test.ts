import { afterEach, describe, expect, it, vi } from 'vitest';

import { cleanupAuditLogs as apiCleanupAuditLogs } from '../api/ops';
import { cleanupAuditLogs } from './opsService';

vi.mock('./dataMode', () => ({ isMockMode: () => false }));
vi.mock('../config', () => ({
  API_BASE_URL: '/api',
}));
vi.mock('../api/ops', () => ({
  cleanupAuditLogs: vi.fn(),
}));

describe('audit cleanup API tolerance', () => {
  afterEach(() => {
    vi.clearAllMocks();
  });

  it('treats a null cleanup result as zero deleted records', async () => {
    vi.mocked(apiCleanupAuditLogs).mockResolvedValueOnce(null as never);

    await expect(cleanupAuditLogs(7)).resolves.toBe(0);
  });

  it('forwards a finite deleted count', async () => {
    vi.mocked(apiCleanupAuditLogs).mockResolvedValueOnce({ deleted: 5 });

    await expect(cleanupAuditLogs(7)).resolves.toBe(5);
  });
});
