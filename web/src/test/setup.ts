/// <reference types="vitest/globals" />
import '@testing-library/jest-dom/vitest';

// Clean up localStorage between tests
beforeEach(() => {
  localStorage.clear();
});
