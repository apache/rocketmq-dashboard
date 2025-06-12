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
        colorPrimary: '#0cb5fb', // 主题色
        borderRadius: 1.5,         // 组件圆角
    },
    components: {
        Button: {
            colorPrimary: '#1c324a', // 普通按钮主题色
        },
        Layout: {
            headerBg: '#1c324a', // 设置 Header 的背景色为 #1c324a
            headerColor: '#ffffff', // 设置 Header 内文本颜色为白色
            backgroundColor: '#ffffff', // 设置 Layout 的背景色为白色
            colorBgLayout: '#f9fcfe',
        },
        Menu: {
            darkItemBg: '#1c324a',
            horizontalItemSelectedBg: '#0cb5fb',
            itemSelectedColor: '#ffffff',
            itemColor: '#ffffff',
            colorText: 'rgba(255, 255, 255, 0.88)', // Adjust for dark theme menu
            activeBarBorderWidth: 0,
        },
        Drawer: {
            colorBgElevated: '#1c324a', // Drawer 背景色
        },
    },
};

export const pinkTheme = {
    token: {
        colorPrimary: '#FF69B4', // 热粉色
        borderRadius: 1.5,
    },
    components: {
        Button: {
            colorPrimary: '#FFC0CB', // 深粉色
        },
        Layout: {
            headerBg: '#FFC0CB', // 粉色
            headerColor: '#000000', // 黑色文本
            backgroundColor: '#F8F8FF', // 幽灵白
            colorBgLayout: '#faf4f4',
        },
        Menu: {
            darkItemBg: '#FFC0CB', // 粉色
            horizontalItemSelectedBg: '#FF69B4',
            itemSelectedColor: '#ffffff',
            itemColor: '#000000', // 黑色文本
            colorText: 'rgba(0, 0, 0, 0.88)',
            activeBarBorderWidth: 0,
        },
        Drawer: {
            colorBgElevated: '#FFC0CB', // Drawer 背景色
        },
    },
};

export const greenTheme = {
    token: {
        colorPrimary: '#52c41a', // 绿色
        borderRadius: 1.5,
    },
    components: {
        Button: {
            colorPrimary: '#7cb305', // 橄榄绿
        },
        Layout: {
            headerBg: '#3f673f', // 深绿色
            headerColor: '#ffffff', // 白色文本
            backgroundColor: '#f6ffed',
            colorBgLayout: '#ebf8eb',
        },
        Menu: {
            darkItemBg: '#3f673f', // 深绿色
            horizontalItemSelectedBg: '#52c41a',
            itemSelectedColor: '#ffffff',
            itemColor: '#ffffff',
            colorText: 'rgba(255, 255, 255, 0.88)',
            activeBarBorderWidth: 0,
        },
        Drawer: {
            colorBgElevated: '#3f673f', // Drawer 背景色
        },
    },
};

export const themes = {
    default: defaultTheme,
    pink: pinkTheme,
    green: greenTheme,
};
