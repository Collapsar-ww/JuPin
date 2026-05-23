import axios from 'axios'
import type { AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'

export interface ApiResult<T = any> {
  code: number
  msg: string
  data: T
}

export interface PageResult<T = any> {
  records: T[]
  total: number
  page: number
  size: number
}

const request = axios.create({
  baseURL: '/api',
  timeout: 15000,
})

request.interceptors.request.use((config) => {
  const token = localStorage.getItem('accessToken')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

request.interceptors.response.use(
  (response: AxiosResponse<ApiResult>) => {
    const res = response.data
    if (res.code === 401) {
      localStorage.removeItem('accessToken')
      localStorage.removeItem('refreshToken')
      localStorage.removeItem('userInfo')
      router.push('/login')
      return Promise.reject(new Error(res.msg || '登录已过期'))
    }
    if (res.code !== 200 && res.code !== undefined) {
      ElMessage.error(res.msg || '请求失败')
      return Promise.reject(new Error(res.msg))
    }
    return response
  },
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('accessToken')
      localStorage.removeItem('refreshToken')
      localStorage.removeItem('userInfo')
      router.push('/login')
    }
    ElMessage.error(error.response?.data?.msg || error.message || '网络错误')
    return Promise.reject(error)
  },
)

export default request

export function get<T>(url: string, params?: any): Promise<ApiResult<T>> {
  return request.get(url, { params }).then((r) => r.data)
}

export function post<T>(url: string, data?: any): Promise<ApiResult<T>> {
  return request.post(url, data).then((r) => r.data)
}

export function put<T>(url: string, data?: any): Promise<ApiResult<T>> {
  return request.put(url, data).then((r) => r.data)
}

export function del<T>(url: string): Promise<ApiResult<T>> {
  return request.delete(url).then((r) => r.data)
}
