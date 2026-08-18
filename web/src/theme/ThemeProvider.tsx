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

import { useState, useSyncExternalStore, type ReactNode } from 'react';
import ThemeContext from './ThemeContext';
import {
  getStoredCompact,
  getStoredThemeMode,
  getSystemDarkMode,
  persistCompact,
  persistThemeMode,
  type ThemeMode,
} from './themePreference';

const DARK_MEDIA_QUERY = '(prefers-color-scheme: dark)';

const subscribeSystemDark = (callback: () => void) => {
  if (!window.matchMedia) return () => {};
  const mediaQuery = window.matchMedia(DARK_MEDIA_QUERY);
  mediaQuery.addEventListener('change', callback);
  return () => mediaQuery.removeEventListener('change', callback);
};

export const ThemeProvider = ({ children }: { children: ReactNode }) => {
  const [themeMode, setThemeModeState] = useState<ThemeMode>(getStoredThemeMode);
  const [compact, setCompactState] = useState(getStoredCompact);
  const systemDark = useSyncExternalStore(subscribeSystemDark, getSystemDarkMode, () => false);

  const setThemeMode = (mode: ThemeMode) => {
    persistThemeMode(mode);
    setThemeModeState(mode);
  };

  const setCompact = (nextCompact: boolean) => {
    persistCompact(nextCompact);
    setCompactState(nextCompact);
  };

  const darkMode = themeMode === 'dark' || (themeMode === 'system' && systemDark);
  const setDarkMode = (dark: boolean) => setThemeMode(dark ? 'dark' : 'light');
  const toggleTheme = () => setDarkMode(!darkMode);

  return (
    <ThemeContext.Provider
      value={{ themeMode, darkMode, compact, setThemeMode, setCompact, setDarkMode, toggleTheme }}
    >
      {children}
    </ThemeContext.Provider>
  );
};
