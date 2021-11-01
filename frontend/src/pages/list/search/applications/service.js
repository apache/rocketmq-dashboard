import { request } from 'umi';
export async function queryFakeList(params) {
  return request('/api/fake_list', {
    params,
  });
}
