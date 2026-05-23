import { get, post, put, del } from '../utils/request'
import type { ApiResult, PageResult } from '../utils/request'
import type { ScriptItem } from './player'

export interface AdminUser {
  id: number
  phone: string
  nickname: string
  role: number
  status: number
  creditScore: number
  createTime: string
}

export function getAdminScriptList(params?: any): Promise<ApiResult<PageResult<ScriptItem>>> {
  return get('/admin/script/list', params)
}

export function createScript(data: Partial<ScriptItem>): Promise<ApiResult<any>> {
  return post('/admin/script/create', data)
}

export function updateScript(id: number, data: Partial<ScriptItem>): Promise<ApiResult<void>> {
  return put(`/admin/script/${id}`, data)
}

export function deleteScript(id: number): Promise<ApiResult<void>> {
  return del(`/admin/script/${id}`)
}

export function getUserList(params?: any): Promise<ApiResult<PageResult<AdminUser>>> {
  return get('/admin/user/list', params)
}

export function setUserStatus(id: number, status: number): Promise<ApiResult<void>> {
  return put(`/admin/user/${id}/status`, { status })
}
