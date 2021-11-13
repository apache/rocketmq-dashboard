import { request } from 'umi';
export async function queryFakeList(params) {
  return request('/api/card_fake_list', {
    params,
  });
}
