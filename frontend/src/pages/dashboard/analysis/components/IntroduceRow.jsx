import { InfoCircleOutlined } from '@ant-design/icons';
import { TinyArea, TinyColumn, Line } from '@ant-design/charts';
import { Col, Row, Tooltip } from 'antd';
import numeral from 'numeral';
import { ChartCard, Field } from './Charts';
import Trend from './Trend';
import Yuan from '../utils/Yuan';
import styles from '../style.less';
const topColResponsiveProps = {
  xs: 24,
  sm: 12,
  md: 12,
  lg: 12,
  xl: 12,
  style: {
    marginBottom: 24,
  },
};

const IntroduceRow = ({ loading, visitData, offlineChartData }) => (
  <Row gutter={24}>
    <Col {...topColResponsiveProps}>
      <ChartCard
        bordered={false}
        loading={loading}
        title="Broker TOP 10"
        contentHeight={300}
      >
        <TinyArea
          color="#975FE4"
          xField="x"
          height={300}
          forceFit
          yField="y"
          smooth
          data={visitData}
        />
      </ChartCard>
    </Col>

    <Col {...topColResponsiveProps}>
      <Line
        forceFit
        height={400}
        data={offlineChartData}
        responsive
        xField="date"
        yField="value"
        seriesField="type"
        interactions={[
          {
            type: 'slider',
            cfg: {},
          },
        ]}
        legend={{
          position: 'top-center',
        }}
      />
    </Col>

    <Col {...topColResponsiveProps}>
      <ChartCard
        bordered={false}
        loading={loading}
        title="Topic TOP 10"
        contentHeight={300}
      >
        <TinyArea
          color="#975FE4"
          xField="x"
          height={300}
          forceFit
          yField="y"
          smooth
          data={visitData}
        />
      </ChartCard>
    </Col>

    <Col {...topColResponsiveProps}>
      <Line
        forceFit
        height={400}
        data={offlineChartData}
        responsive
        xField="date"
        yField="value"
        seriesField="type"
        interactions={[
          {
            type: 'slider',
            cfg: {},
          },
        ]}
        legend={{
          position: 'top-center',
        }}
      />
    </Col>

  </Row>
);

export default IntroduceRow;
