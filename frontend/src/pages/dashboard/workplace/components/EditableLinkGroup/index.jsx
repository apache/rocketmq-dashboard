import React, { createElement } from 'react';
import { PlusOutlined } from '@ant-design/icons';
import { Button } from 'antd';
import styles from './index.less';

const EditableLinkGroup = (props) => {
  const { links, linkElement, onAdd } = props;
  return (
    <div className={styles.linkGroup}>
      {links.map((link) =>
        createElement(
          linkElement,
          {
            key: `linkGroup-item-${link.id || link.title}`,
            to: link.href,
            href: link.href,
          },
          link.title,
        ),
      )}
      <Button size="small" type="primary" ghost onClick={onAdd}>
        <PlusOutlined /> 添加
      </Button>
    </div>
  );
};

EditableLinkGroup.defaultProps = {
  links: [],
  onAdd: () => {},
  linkElement: 'a',
};
export default EditableLinkGroup;
