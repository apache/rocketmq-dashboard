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
import {useEffect} from 'react';
import {useDispatch, useSelector} from 'react-redux';
import {defaultTheme, themes} from '../../assets/styles/theme';
import {setTheme} from '../actions/themeActions';

export const useTheme = () => {
    const currentThemeName = useSelector(state => state.theme.currentThemeName);
    const dispatch = useDispatch();


    const currentTheme = themes[currentThemeName] || defaultTheme;

    useEffect(() => {
        localStorage.setItem('appTheme', currentThemeName);
    }, [currentThemeName]);

    return {
        currentTheme,
        currentThemeName,
        setCurrentThemeName: (themeName) => dispatch(setTheme(themeName)),
    };
};
