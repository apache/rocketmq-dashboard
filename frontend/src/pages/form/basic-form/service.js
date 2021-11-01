import { request } from 'umi';
export async function fakeSubmitForm(params) {
  return request('/api/basicForm', {
    method: 'POST',
    data: params,
  });
}
