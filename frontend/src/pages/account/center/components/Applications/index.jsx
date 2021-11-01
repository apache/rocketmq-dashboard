import {
  DownloadOutlined,
  EditOutlined,
  EllipsisOutlined,
  ShareAltOutlined,
} from '@ant-design/icons';
import { useRequest } from 'umi';
import { Avatar, Card, Dropdown, List, Menu, Tooltip } from 'antd';
import React from 'react';
import numeral from 'numeral';
import { queryFakeList } from '../../service';
import stylesApplications from './index.less';
export function formatWan(val) {
  const v = val * 1;
  if (!v || Number.isNaN(v)) return '';
  let result = val;

  if (val > 10000) {
    result = (
      <span>
        {Math.floor(val / 10000)}
        <span
          style={{
            position: 'relative',
            top: -2,
            fontSize: 14,
            fontStyle: 'normal',
            marginLeft: 2,
          }}
        >
          万
        </span>
      </span>
    );
  }

  return result;
}

const Applications = () => {
  // 获取tab列表数据
  const { data: listData } = useRequest(() => {
    return queryFakeList({
      count: 30,
    });
  });
  const itemMenu = (
    <Menu>
      <Menu.Item>
        <a target="_blank" rel="noopener noreferrer" href="https://www.alipay.com/">
          1st menu item
        </a>
      </Menu.Item>
      <Menu.Item>
        <a target="_blank" rel="noopener noreferrer" href="https://www.taobao.com/">
          2nd menu item
        </a>
      </Menu.Item>
      <Menu.Item>
        <a target="_blank" rel="noopener noreferrer" href="https://www.tmall.com/">
          3d menu item
        </a>
      </Menu.Item>
    </Menu>
  );

  const CardInfo = ({ activeUser, newUser }) => (
    <div className={stylesApplications.cardInfo}>
      <div>
        <p>活跃用户</p>
        <p>{activeUser}</p>
      </div>
      <div>
        <p>新增用户</p>
        <p>{newUser}</p>
      </div>
    </div>
  );

  return (
    <List
      rowKey="id"
      className={stylesApplications.filterCardList}
      grid={{
        gutter: 24,
        xxl: 3,
        xl: 2,
        lg: 2,
        md: 2,
        sm: 2,
        xs: 1,
      }}
      dataSource={listData?.list || []}
      renderItem={(item) => (
        <List.Item key={item.id}>
          <Card
            hoverable
            bodyStyle={{
              paddingBottom: 20,
            }}
            actions={[
              <Tooltip key="download" title="下载">
                <DownloadOutlined />
              </Tooltip>,
              <Tooltip title="编辑" key="edit">
                <EditOutlined />
              </Tooltip>,
              <Tooltip title="分享" key="share">
                <ShareAltOutlined />
              </Tooltip>,
              <Dropdown overlay={itemMenu} key="ellipsis">
                <EllipsisOutlined />
              </Dropdown>,
            ]}
          >
            <Card.Meta avatar={<Avatar size="small" src={item.avatar} />} title={item.title} />
            <div className={stylesApplications.cardItemContent}>
              <CardInfo
                activeUser={formatWan(item.activeUser)}
                newUser={numeral(item.newUser).format('0,0')}
              />
            </div>
          </Card>
        </List.Item>
      )}
    />
  );
};

export default Applications;
