import { request } from 'umi';
export async function fakeSubmitForm(params) {
  return request('/api/stepForm', {
    method: 'POST',
    data: params,
  });
}
