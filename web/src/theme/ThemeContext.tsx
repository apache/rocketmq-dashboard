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

import { createContext, useContext, useState, useEffect, type ReactNode } from 'react';
import {
  getStoredThemePreference,
  getSystemDarkMode,
  persistThemePreference,
} from './themePreference';

interface ThemeContextType {
  darkMode: boolean;
  setDarkMode: (dark: boolean) => void;
  toggleTheme: () => void;
}

const ThemeContext = createContext<ThemeContextType>({
  darkMode: false,
  setDarkMode: () => {},
  toggleTheme: () => {},
});

export const ThemeProvider = ({ children }: { children: ReactNode }) => {
  const [themeState, setThemeState] = useState(() => {
    const preference = getStoredThemePreference();
    return {
      darkMode: preference ? preference === 'dark' : getSystemDarkMode(),
      followsSystem: preference === null,
    };
  });

  const setDarkMode = (darkMode: boolean) => {
    persistThemePreference(darkMode);
    setThemeState({ darkMode, followsSystem: false });
  };

  const toggleTheme = () => setDarkMode(!themeState.darkMode);

  useEffect(() => {
    if (!themeState.followsSystem || !window.matchMedia) return;

    const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
    const updateTheme = (event: MediaQueryListEvent) => {
      setThemeState((current) =>
        current.followsSystem ? { ...current, darkMode: event.matches } : current,
      );
    };
    mediaQuery.addEventListener('change', updateTheme);
    return () => mediaQuery.removeEventListener('change', updateTheme);
  }, [themeState.followsSystem]);

  return (
    <ThemeContext.Provider
      value={{ darkMode: themeState.darkMode, setDarkMode, toggleTheme }}
    >
      {children}
    </ThemeContext.Provider>
  );
};

export const useTheme = () => useContext(ThemeContext);

export default ThemeContext;
