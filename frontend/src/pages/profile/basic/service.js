import { request } from 'umi';
export async function queryBasicProfile() {
  return request('/api/profile/basic');
}
