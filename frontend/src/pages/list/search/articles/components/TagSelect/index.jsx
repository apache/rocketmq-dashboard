import { DownOutlined, UpOutlined } from '@ant-design/icons';
import { useBoolean, useControllableValue } from 'ahooks';
import { Tag } from 'antd';
import classNames from 'classnames';
import React from 'react';
import styles from './index.less';
const { CheckableTag } = Tag;

const TagSelectOption = ({ children, checked, onChange, value }) => (
  <CheckableTag
    checked={!!checked}
    key={value}
    onChange={(state) => onChange && onChange(value, state)}
  >
    {children}
  </CheckableTag>
);

TagSelectOption.isTagSelectOption = true;

const TagSelect = (props) => {
  const { children, hideCheckAll = false, className, style, expandable, actionsText = {} } = props;
  const [expand, { toggle }] = useBoolean();
  const [value, setValue] = useControllableValue(props);

  const isTagSelectOption = (node) =>
    node &&
    node.type &&
    (node.type.isTagSelectOption || node.type.displayName === 'TagSelectOption');

  const getAllTags = () => {
    const childrenArray = React.Children.toArray(children);
    const checkedTags = childrenArray
      .filter((child) => isTagSelectOption(child))
      .map((child) => child.props.value);
    return checkedTags || [];
  };

  const onSelectAll = (checked) => {
    let checkedTags = [];

    if (checked) {
      checkedTags = getAllTags();
    }

    setValue(checkedTags);
  };

  const handleTagChange = (tag, checked) => {
    const checkedTags = [...(value || [])];
    const index = checkedTags.indexOf(tag);

    if (checked && index === -1) {
      checkedTags.push(tag);
    } else if (!checked && index > -1) {
      checkedTags.splice(index, 1);
    }

    setValue(checkedTags);
  };

  const checkedAll = getAllTags().length === value?.length;
  const { expandText = '展开', collapseText = '收起', selectAllText = '全部' } = actionsText;
  const cls = classNames(styles.tagSelect, className, {
    [styles.hasExpandTag]: expandable,
    [styles.expanded]: expand,
  });
  return (
    <div className={cls} style={style}>
      {hideCheckAll ? null : (
        <CheckableTag checked={checkedAll} key="tag-select-__all__" onChange={onSelectAll}>
          {selectAllText}
        </CheckableTag>
      )}
      {children &&
        React.Children.map(children, (child) => {
          if (isTagSelectOption(child)) {
            return React.cloneElement(child, {
              key: `tag-select-${child.props.value}`,
              value: child.props.value,
              checked: value && value.indexOf(child.props.value) > -1,
              onChange: handleTagChange,
            });
          }

          return child;
        })}
      {expandable && (
        <a
          className={styles.trigger}
          onClick={() => {
            toggle();
          }}
        >
          {expand ? (
            <>
              {collapseText} <UpOutlined />
            </>
          ) : (
            <>
              {expandText}
              <DownOutlined />
            </>
          )}
        </a>
      )}
    </div>
  );
};

TagSelect.Option = TagSelectOption;
export default TagSelect;
