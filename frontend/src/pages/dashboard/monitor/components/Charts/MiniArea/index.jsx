import { Axis, Chart, Geom, Tooltip } from 'bizcharts';
import React from 'react';
import autoHeight from '../autoHeight';
import styles from '../index.less';

const MiniArea = (props) => {
  const {
    height = 1,
    data = [],
    forceFit = true,
    color = 'rgba(24, 144, 255, 0.2)',
    borderColor = '#1089ff',
    scale = {
      x: {},
      y: {},
    },
    borderWidth = 2,
    line,
    xAxis,
    yAxis,
    animate = true,
  } = props;
  const padding = [36, 5, 30, 5];
  const scaleProps = {
    x: {
      type: 'cat',
      range: [0, 1],
      ...scale.x,
    },
    y: {
      min: 0,
      ...scale.y,
    },
  };
  const tooltip = [
    'x*y',
    (x, y) => ({
      name: x,
      value: y,
    }),
  ];
  const chartHeight = height + 54;
  return (
    <div
      className={styles.miniChart}
      style={{
        height,
      }}
    >
      <div className={styles.chartContent}>
        {height > 0 && (
          <Chart
            animate={animate}
            scale={scaleProps}
            height={chartHeight}
            forceFit={forceFit}
            data={data}
            padding={padding}
          >
            <Axis
              key="axis-x"
              name="x"
              label={null}
              line={null}
              tickLine={null}
              grid={null}
              {...xAxis}
            />
            <Axis
              key="axis-y"
              name="y"
              label={null}
              line={null}
              tickLine={null}
              grid={null}
              {...yAxis}
            />
            <Tooltip showTitle={false} crosshairs={false} />
            <Geom
              type="area"
              position="x*y"
              color={color}
              tooltip={tooltip}
              shape="smooth"
              style={{
                fillOpacity: 1,
              }}
            />
            {line ? (
              <Geom
                type="line"
                position="x*y"
                shape="smooth"
                color={borderColor}
                size={borderWidth}
                tooltip={false}
              />
            ) : (
              <span
                style={{
                  display: 'none',
                }}
              />
            )}
          </Chart>
        )}
      </div>
    </div>
  );
};

export default autoHeight()(MiniArea);
