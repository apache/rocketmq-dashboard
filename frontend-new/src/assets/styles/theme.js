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
export const defaultTheme = {
    token: {
        colorPrimary: '#1677ff',
        borderRadius: 8,
        colorBgContainer: '#ffffff',
        colorBgLayout: '#f5f7fa',
        colorBorder: '#e8e8e8',
        colorBorderSecondary: '#f0f0f0',
        fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif',
    },
    components: {
        Button: {
            colorPrimary: '#1677ff',
            borderRadius: 6,
        },
        Layout: {
            headerBg: '#ffffff',
            headerColor: '#1a1a1a',
            backgroundColor: '#ffffff',
            colorBgLayout: '#f5f7fa',
        },
        Menu: {
            itemBg: 'transparent',
            itemSelectedBg: '#e6f4ff',
            itemSelectedColor: '#1677ff',
            itemHoverBg: '#e6f4ff',
            itemHoverColor: '#1677ff',
            itemColor: '#595959',
            subMenuItemBg: 'transparent',
            activeBarBorderWidth: 0,
        },
        Card: {
            borderRadius: 8,
        },
        Table: {
            borderRadius: 8,
            headerBg: '#fafafa',
            headerColor: '#1a1a1a',
            rowHoverBg: '#f5f7fa',
        },
        Modal: {
            borderRadius: 12,
        },
        Tabs: {
            inkBarColor: '#1677ff',
            itemSelectedColor: '#1677ff',
            itemHoverColor: '#1677ff',
        },
        Tag: {
            borderRadiusSM: 4,
        },
        Input: {
            borderRadius: 6,
        },
        Select: {
            borderRadius: 6,
        },
    },
};

export const pinkTheme = {
    token: {
        colorPrimary: '#FF69B4',
        borderRadius: 8,
    },
    components: {
        Button: {
            colorPrimary: '#FF69B4',
        },
        Layout: {
            headerBg: '#ffffff',
            headerColor: '#1a1a1a',
            backgroundColor: '#ffffff',
            colorBgLayout: '#faf4f4',
        },
        Menu: {
            itemBg: 'transparent',
            itemSelectedBg: '#fff0f6',
            itemSelectedColor: '#FF69B4',
            itemHoverBg: '#fff0f6',
            itemHoverColor: '#FF69B4',
            itemColor: '#595959',
            activeBarBorderWidth: 0,
        },
    },
};

export const greenTheme = {
    token: {
        colorPrimary: '#52c41a',
        borderRadius: 8,
    },
    components: {
        Button: {
            colorPrimary: '#52c41a',
        },
        Layout: {
            headerBg: '#ffffff',
            headerColor: '#1a1a1a',
            backgroundColor: '#ffffff',
            colorBgLayout: '#f6ffed',
        },
        Menu: {
            itemBg: 'transparent',
            itemSelectedBg: '#f6ffed',
            itemSelectedColor: '#52c41a',
            itemHoverBg: '#f6ffed',
            itemHoverColor: '#52c41a',
            itemColor: '#595959',
            activeBarBorderWidth: 0,
        },
    },
};

export const themes = {
    default: defaultTheme,
    pink: pinkTheme,
    green: greenTheme,
};