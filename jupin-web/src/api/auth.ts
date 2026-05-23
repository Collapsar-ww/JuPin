import { post } from '../utils/request'
import type { ApiResult } from '../utils/request'

export interface RegisterRequest {
  phone: string
  password: string
  nickname: string
  role: string
}

export interface LoginRequest {
  phone: string
  password: string
}

export interface UserInfo {
  id: number
  phone: string
  nickname: string
  avatar: string | null
  gender: number
  role: number
  creditScore: number
  createTime: string
}

export interface LoginResult {
  accessToken: string
  refreshToken: string
  user: UserInfo
}

export function register(data: RegisterRequest): Promise<ApiResult<UserInfo>> {
  return post('/auth/register', data)
}

export function login(data: LoginRequest): Promise<ApiResult<LoginResult>> {
  return post('/auth/login', data)
}

export function refreshToken(): Promise<ApiResult<{ accessToken: string; refreshToken: string }>> {
  return post('/auth/refresh')
}

export function logout(): Promise<ApiResult<void>> {
  return post('/auth/logout')
}
