/// <reference types="vitest/globals" />
import '@testing-library/jest-dom/vitest';
import { configure } from '@testing-library/react';

// findBy*/waitFor default to 1s, which antd's async rendering exceeds once the whole
// suite runs in parallel.
configure({ asyncUtilTimeout: 5000 });

// Clean up localStorage between tests
beforeEach(() => {
  localStorage.clear();
});
