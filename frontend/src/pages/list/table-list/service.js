// @ts-ignore

/* eslint-disable */
import { request } from 'umi';

const formatClusterRes = (brokerInfo = {}) => {
  const result = [];

  Object.keys(brokerInfo).forEach(brokerName => {
    const singleBroker = brokerInfo[brokerName];

    Object.keys(singleBroker).forEach(brokerIndex => {
      result.push({
        index: `${brokerName}_${brokerIndex}`,
        brokerName,
        brokerIndex,
        ...singleBroker[brokerIndex]
      })
    })
  })

  return result;
}

/** get cluster list GET /cluster/list.query */
export async function queryClusterList(params, options) {
  return request('/api/cluster/list.query', {
    method: 'GET',
    params: { ...params },
    ...(options || {}),
  }).then(res => {
    const {
      data = {}
    } = res;
    const { brokerServer = {} } = data;

    return {
      data: formatClusterRes(brokerServer),
      success: true
    };
  });
}
/** 新建规则 PUT /api/rule */

export async function updateRule(data, options) {
  return request('/api/rule', {
    data,
    method: 'PUT',
    ...(options || {}),
  });
}
/** 新建规则 POST /api/rule */

export async function addRule(data, options) {
  return request('/api/rule', {
    data,
    method: 'POST',
    ...(options || {}),
  });
}
/** 删除规则 DELETE /api/rule */

export async function removeRule(data, options) {
  return request('/api/rule', {
    data,
    method: 'DELETE',
    ...(options || {}),
  });
}
