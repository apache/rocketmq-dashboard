import React from 'react';

function computeHeight(node) {
  const { style } = node;
  style.height = '100%';
  const totalHeight = parseInt(`${getComputedStyle(node).height}`, 10);
  const padding =
    parseInt(`${getComputedStyle(node).paddingTop}`, 10) +
    parseInt(`${getComputedStyle(node).paddingBottom}`, 10);
  return totalHeight - padding;
}

function getAutoHeight(n) {
  if (!n) {
    return 0;
  }

  const node = n;
  let height = computeHeight(node);
  const parentNode = node.parentNode;

  if (parentNode) {
    height = computeHeight(parentNode);
  }

  return height;
}

function autoHeight() {
  return (WrappedComponent) => {
    class AutoHeightComponent extends React.Component {
      state = {
        computedHeight: 0,
      };
      root = undefined;

      componentDidMount() {
        const { height } = this.props;

        if (!height) {
          let h = getAutoHeight(this.root);
          this.setState({
            computedHeight: h,
          });

          if (h < 1) {
            h = getAutoHeight(this.root);
            this.setState({
              computedHeight: h,
            });
          }
        }
      }

      handleRoot = (node) => {
        this.root = node;
      };

      render() {
        const { height } = this.props;
        const { computedHeight } = this.state;
        const h = height || computedHeight;
        return (
          <div ref={this.handleRoot}>
            {h > 0 && <WrappedComponent {...this.props} height={h} />}
          </div>
        );
      }
    }

    return AutoHeightComponent;
  };
}

export default autoHeight;
