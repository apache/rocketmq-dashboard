import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { App as AntdApp } from 'antd';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const navigateMock = vi.hoisted(() => vi.fn());
const loginApiMock = vi.hoisted(() => vi.fn());
const loginStoreMock = vi.hoisted(() => vi.fn());

vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router-dom')>();
  return { ...actual, useNavigate: () => navigateMock };
});

vi.mock('../../api/auth', () => ({ login: loginApiMock }));

vi.mock('../../stores/authStore', () => ({
  default: (selector: (state: { login: typeof loginStoreMock }) => unknown) =>
    selector({ login: loginStoreMock }),
}));

vi.mock('../../i18n/LangContext', () => ({
  useLang: () => ({ t: (key: string) => key }),
}));

vi.mock('../../theme/useTheme', () => ({
  useTheme: () => ({ darkMode: false, toggleTheme: vi.fn() }),
}));

import LoginPage from './index';

// antd's responsive observer calls window.matchMedia; jsdom does not implement it.
Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: vi.fn().mockImplementation((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })),
});

const fillCredentials = () => {
  fireEvent.change(screen.getByPlaceholderText('login.usernamePlaceholder'), {
    target: { value: 'alice' },
  });
  fireEvent.change(screen.getByPlaceholderText('login.passwordPlaceholder'), {
    target: { value: 'secret' },
  });
};

const renderPage = (initialEntry = '/login') =>
  render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <AntdApp>
        <LoginPage />
      </AntdApp>
    </MemoryRouter>,
  );

describe('LoginPage post-login redirect', () => {
  beforeEach(() => {
    navigateMock.mockClear();
    loginApiMock.mockClear();
    loginStoreMock.mockClear();
    loginApiMock.mockResolvedValue({
      user: { username: 'alice', userId: 42, admin: true },
    });
  });

  it('returns to the page carried by ?redirect after a successful login', async () => {
    renderPage('/login?redirect=%2Fops%2Falerts%3Flevel%3Derror');
    fillCredentials();
    fireEvent.click(screen.getByRole('button', { name: 'login.title' }));

    await waitFor(() => expect(loginStoreMock).toHaveBeenCalledWith('alice', 42, true));
    expect(navigateMock).toHaveBeenCalledWith('/ops/alerts?level=error', { replace: true });
  });

  it('falls back to home when no redirect is present', async () => {
    renderPage('/login');
    fillCredentials();
    fireEvent.click(screen.getByRole('button', { name: 'login.title' }));

    await waitFor(() => expect(navigateMock).toHaveBeenCalledWith('/', { replace: true }));
  });

  it.each([
    ['protocol-relative URL', '/login?redirect=%2F%2Fevil.example%2Fphish'],
    ['absolute external URL', '/login?redirect=https%3A%2F%2Fevil.example%2Fphish'],
    ['relative path without a leading slash', '/login?redirect=ops%2Falerts'],
    ['backslash trick', '/login?redirect=%2F%5Cevil.example'],
  ])('rejects a redirect that is a %s', async (_label, entry) => {
    renderPage(entry);
    fillCredentials();
    fireEvent.click(screen.getByRole('button', { name: 'login.title' }));

    await waitFor(() => expect(navigateMock).toHaveBeenCalledWith('/', { replace: true }));
  });
});
