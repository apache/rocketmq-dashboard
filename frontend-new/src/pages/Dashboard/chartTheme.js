/*
 * 统一图表视觉主题 —— 用于监控面板所有 ECharts 图表
 * 目标：替换原先杂乱、刺眼的默认配色（黑轴标签、#FF0000 等），
 *      提供一套现代、和谐、与 RocketMQ Studio 品牌色一致的视觉规范。
 */

// 主色板：蓝(品牌) → 青 → 绿 → 金 → 品红 → 紫 → 橙 → 深蓝
export const CHART_PALETTE = [
    '#1677ff', // 主蓝
    '#13c2c2', // 青
    '#52c41a', // 绿
    '#faad14', // 金
    '#eb2f96', // 品红
    '#722ed1', // 紫
    '#fa8c16', // 橙
    '#2f54eb', // 深蓝
];

// 柱状图渐变（主蓝，自上而下的清爽蓝）
const BAR_GRADIENT = {
    type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
    colorStops: [
        { offset: 0, color: '#4096ff' },
        { offset: 1, color: '#1677ff' },
    ],
};
const BAR_GRADIENT_HOVER = {
    type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
    colorStops: [
        { offset: 0, color: '#1677ff' },
        { offset: 1, color: '#0958d9' },
    ],
};

// #RRGGBB -> rgba()
export const hexToRgba = (hex, alpha = 1) => {
    const h = hex.replace('#', '');
    const r = parseInt(h.substring(0, 2), 16);
    const g = parseInt(h.substring(2, 4), 16);
    const b = parseInt(h.substring(4, 6), 16);
    return `rgba(${r}, ${g}, ${b}, ${alpha})`;
};

// 数字格式化（大数用 k / w 缩写，避免轴标签拥挤）
export const formatAxisNum = (v) => {
    const n = Math.abs(Number(v));
    if (n >= 1e8) return (v / 1e8).toFixed(1) + '亿';
    if (n >= 1e4) return (v / 1e4).toFixed(1) + 'w';
    if (n >= 1e3) return (v / 1e3).toFixed(1) + 'k';
    return v;
};

// 通用网格（留出边距，标签不被裁切）
export const baseGrid = (extra = {}) => ({
    left: '2.5%',
    right: '3.5%',
    bottom: '4%',
    top: 56,
    containLabel: true,
    ...extra,
});

// 通用 tooltip（白底、圆角、柔和阴影）
export const baseTooltip = (extra = {}) => ({
    trigger: 'axis',
    axisPointer: {
        type: 'line',
        lineStyle: { color: '#d9d9d9', width: 1, type: 'dashed' },
        z: 0,
    },
    backgroundColor: 'rgba(255,255,255,0.96)',
    borderColor: '#f0f0f0',
    borderWidth: 1,
    padding: [8, 12],
    textStyle: { color: '#595959', fontSize: 12, lineHeight: 18 },
    extraCssText: 'box-shadow:0 6px 20px rgba(0,0,0,0.08);border-radius:8px;',
    ...extra,
});

// 类目轴（X 轴）通用样式
export const categoryAxis = (data, extra = {}) => ({
    type: 'category',
    data,
    boundaryGap: true,
    axisLabel: { color: '#8c8c8c', fontSize: 11, hideOverlap: true, ...(extra.axisLabel || {}) },
    axisLine: { lineStyle: { color: '#eaeaea' } },
    axisTick: { show: false },
    ...extra,
});

// 数值轴（Y 轴）通用样式
export const valueAxis = (extra = {}) => ({
    type: 'value',
    axisLabel: { color: '#8c8c8c', fontSize: 11, formatter: formatAxisNum, ...(extra.axisLabel || {}) },
    axisLine: { show: false },
    axisTick: { show: false },
    splitLine: { lineStyle: { color: '#f5f5f5', type: 'solid' } },
    ...extra,
});

// 折线类目轴（时间类，boundaryGap=false，标签更密一点没关系）
export const lineCategoryAxis = (data, extra = {}) => ({
    type: 'category',
    boundaryGap: false,
    data,
    axisLabel: { color: '#8c8c8c', fontSize: 11, hideOverlap: true, ...(extra.axisLabel || {}) },
    axisLine: { lineStyle: { color: '#eaeaea' } },
    axisTick: { show: false },
    ...extra,
});

// 给一组折线 series 套上「平滑 + 细线 + 面积渐变」的统一风格
export const withAreaStyle = (series, { showSymbol = false } = {}) =>
    series.map((s, i) => {
        const c = CHART_PALETTE[i % CHART_PALETTE.length];
        return {
            ...s,
            smooth: true,
            symbol: showSymbol ? 'circle' : 'none',
            symbolSize: 5,
            showSymbol,
            sampling: 'average',
            lineStyle: { width: 2, color: c, ...(s.lineStyle || {}) },
            itemStyle: { color: c, ...(s.itemStyle || {}) },
            areaStyle: {
                opacity: 1,
                color: {
                    type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
                    colorStops: [
                        { offset: 0, color: hexToRgba(c, 0.28) },
                        { offset: 1, color: hexToRgba(c, 0.02) },
                    ],
                },
                ...(s.areaStyle || {}),
            },
        };
    });

// 柱状图单系列（带圆角 + 渐变 + 高亮态）
export const barSeries = (data, name = 'TotalMsg', color = BAR_GRADIENT) => ({
    name,
    type: 'bar',
    data,
    barMaxWidth: 38,
    itemStyle: {
        borderRadius: [6, 6, 0, 0],
        color,
    },
    emphasis: {
        itemStyle: { color: BAR_GRADIENT_HOVER },
    },
});

// 组合柱状图系列（消费并发 / JVM GC），按索引取色
export const groupedBarSeries = (name, data, index = 0, extra = {}) => ({
    name,
    type: 'bar',
    data,
    barMaxWidth: 28,
    itemStyle: {
        borderRadius: [4, 4, 0, 0],
        color: CHART_PALETTE[index % CHART_PALETTE.length],
        ...extra,
    },
});

// 标题统一样式（与卡片风格对齐）
export const titleStyle = (text, extra = {}) => ({
    text,
    left: 4,
    top: 8,
    textStyle: {
        color: '#262626',
        fontSize: 14,
        fontWeight: 600,
        fontFamily: 'inherit',
        ...(extra.textStyle || {}),
    },
    ...extra,
});
