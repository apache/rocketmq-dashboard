/// <reference types="vitest/globals" />
import '@testing-library/jest-dom/vitest';
import { configure } from '@testing-library/react';
import { message, notification } from 'antd';

// findBy*/waitFor default to 1s, which antd's async rendering exceeds once the whole
// suite runs in parallel.
configure({ asyncUtilTimeout: 5000 });

// Clean up localStorage between tests
beforeEach(() => {
  localStorage.clear();
});

// The static `message`/`notification` APIs render into body-level holders that RTL's cleanup
// does not own: their notices (3s duration plus a leave animation) survive into later tests,
// and their rc-notification timers fire mid-test on an orphaned React root — the source of
// cross-test DOM pollution and "window is not defined" teardown errors under parallel load.
afterEach(() => {
  message.destroy();
  notification.destroy();
});
