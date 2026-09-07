import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { App as AntdApp } from 'antd';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

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

describe('LoginPage', () => {
  beforeEach(() => {
    navigateMock.mockClear();
    loginApiMock.mockClear();
    loginStoreMock.mockClear();
  });

  const renderPage = () =>
    render(
      <MemoryRouter>
        <AntdApp>
          <LoginPage />
        </AntdApp>
      </MemoryRouter>,
    );

  const fillCredentials = () => {
    fireEvent.change(screen.getByPlaceholderText('login.usernamePlaceholder'), {
      target: { value: 'alice' },
    });
    fireEvent.change(screen.getByPlaceholderText('login.passwordPlaceholder'), {
      target: { value: 'secret' },
    });
  };

  it('renders the brand and an accessible theme toggle', () => {
    renderPage();
    expect(screen.getByText('RocketMQ Studio')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'login.switchToDark' })).toBeTruthy();
  });

  it('submits login with username, numeric userId and admin, then navigates to /', async () => {
    loginApiMock.mockResolvedValue({
      user: { username: 'alice', userId: 42, admin: true },
    });
    renderPage();
    fillCredentials();
    fireEvent.click(screen.getByRole('button', { name: 'login.title' }));

    await waitFor(() => expect(loginStoreMock).toHaveBeenCalledWith('alice', 42, true));
    expect(loginApiMock).toHaveBeenCalledWith('alice', 'secret');
    expect(navigateMock).toHaveBeenCalledWith('/', { replace: true });
  });

  it('owns an in-flight login request synchronously and allows retry after failure', async () => {
    let rejectFirst: ((reason?: unknown) => void) | undefined;
    loginApiMock
      .mockReturnValueOnce(
        new Promise((_, reject) => {
          rejectFirst = reject;
        }),
      )
      .mockResolvedValueOnce({
        user: { username: 'alice', userId: 42, admin: true },
      });
    renderPage();
    fillCredentials();

    const form = document.querySelector('form');
    expect(form).not.toBeNull();
    fireEvent.submit(form!);
    fireEvent.submit(form!);

    await waitFor(() => expect(loginApiMock).toHaveBeenCalledTimes(1));
    rejectFirst?.(new Error('temporary failure'));
    const submitButton = document.querySelector<HTMLButtonElement>('button[type="submit"]');
    expect(submitButton).not.toBeNull();
    await waitFor(() => expect(submitButton!.disabled).toBe(false));

    fireEvent.submit(form!);

    await waitFor(() => expect(loginApiMock).toHaveBeenCalledTimes(2));
    await waitFor(() => expect(loginStoreMock).toHaveBeenCalledWith('alice', 42, true));
  });

  it('keeps required-field validation and does not call the API on empty submit', async () => {
    renderPage();
    fireEvent.click(screen.getByRole('button', { name: 'login.title' }));

    await waitFor(() => expect(screen.getByText('login.usernameRequired')).toBeTruthy());
    expect(screen.getByText('login.passwordRequired')).toBeTruthy();
    expect(loginApiMock).not.toHaveBeenCalled();
  });
});
