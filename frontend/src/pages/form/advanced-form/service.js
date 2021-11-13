import { request } from 'umi';
export async function fakeSubmitForm(params) {
  return request('/api/advancedForm', {
    method: 'POST',
    data: params,
  });
}
