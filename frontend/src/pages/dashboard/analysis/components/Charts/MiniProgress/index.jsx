import React from 'react';
import { Tooltip } from 'antd';
import styles from './index.less';

const MiniProgress = ({
  targetLabel,
  target,
  color = 'rgb(19, 194, 194)',
  strokeWidth,
  percent,
}) => (
  <div className={styles.miniProgress}>
    <Tooltip title={targetLabel}>
      <div
        className={styles.target}
        style={{
          left: target ? `${target}%` : undefined,
        }}
      >
        <span
          style={{
            backgroundColor: color || undefined,
          }}
        />
        <span
          style={{
            backgroundColor: color || undefined,
          }}
        />
      </div>
    </Tooltip>
    <div className={styles.progressWrap}>
      <div
        className={styles.progress}
        style={{
          backgroundColor: color || undefined,
          width: percent ? `${percent}%` : undefined,
          height: strokeWidth || undefined,
        }}
      />
    </div>
  </div>
);

export default MiniProgress;
