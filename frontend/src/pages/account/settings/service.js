import { request } from 'umi';
export async function queryCurrent() {
  return request('/api/accountSettingCurrentUser');
}
export async function queryProvince() {
  return request('/api/geographic/province');
}
export async function queryCity(province) {
  return request(`/api/geographic/city/${province}`);
}
export async function query() {
  return request('/api/users');
}
