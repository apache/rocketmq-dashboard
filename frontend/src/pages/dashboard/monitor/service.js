import { request } from 'umi';
export async function queryTags() {
  return request('/api/tags');
}
