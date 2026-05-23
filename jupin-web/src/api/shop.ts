import { get, post, put, del } from '../utils/request'
import type { ApiResult, PageResult } from '../utils/request'
import type { PoolListItem, PoolDetail, OrderItem, ScriptItem, ChatMessage } from './player'

export interface ShopInfo {
  id: number
  name: string
  city: string
  address: string
  phone: string
  logo: string | null
  cover: string | null
  description: string | null
  openingHours: string | null
  role: number
}

export interface ShopMember {
  id: number
  userId: number
  nickname: string
  role: number
  roleText: string
  status: number
  createTime: string
}

export interface CreateShopPoolRequest {
  scriptId: number
  city: string
  address: string
  startTime: string
  endTime: string
  maxMembers: number
  price: number
  dmId: number
}

// === Shop Current ===
export function getCurrentShop(): Promise<ApiResult<ShopInfo>> {
  return get('/shop/current')
}

// === Shop Update ===
export function updateShop(data: Partial<ShopInfo>): Promise<ApiResult<void>> {
  return put('/shop/update', data)
}

// === Members ===
export function getShopMembers(shopId: number): Promise<ApiResult<ShopMember[]>> {
  return get(`/shop/${shopId}/members`)
}

export function addShopMember(shopId: number, data: { userId: number }): Promise<ApiResult<void>> {
  return post(`/shop/${shopId}/members/add`, data)
}

export function removeShopMember(shopId: number, data: { userId: number }): Promise<ApiResult<void>> {
  return post(`/shop/${shopId}/members/remove`, data)
}

export function setMemberRole(shopId: number, data: { userId: number; role: number }): Promise<ApiResult<void>> {
  return put(`/shop/${shopId}/members/role`, data)
}

// === Shop Scripts ===
export function getShopScripts(shopId: number, params?: any): Promise<ApiResult<PageResult<ScriptItem>>> {
  return get(`/shop/${shopId}/scripts`, params)
}

export function addShopScript(shopId: number, scriptId: number): Promise<ApiResult<void>> {
  return post(`/shop/${shopId}/scripts/add`, { scriptId })
}

export function removeShopScript(shopId: number, scriptId: number): Promise<ApiResult<void>> {
  return del(`/shop/${shopId}/scripts/${scriptId}`)
}

// === System Scripts ===
export function getSystemScripts(params?: any): Promise<ApiResult<PageResult<ScriptItem>>> {
  return get('/shop/script/list', params)
}

// === Shop Pool ===
export function createShopPool(data: CreateShopPoolRequest): Promise<ApiResult<{ id: number; status: number }>> {
  return post('/shop/pool/create', data)
}

export function getShopPoolList(params?: any): Promise<ApiResult<PageResult<PoolListItem>>> {
  return get('/shop/pool/list', params)
}

export function getShopPoolDetail(id: number): Promise<ApiResult<PoolDetail>> {
  return get(`/shop/pool/${id}`)
}

export function assignDm(poolId: number, dmId: number): Promise<ApiResult<void>> {
  return post(`/shop/pool/${poolId}/assign-dm`, { dmId })
}

export function startCompleteShop(poolId: number): Promise<ApiResult<any>> {
  return post(`/shop/pool/${poolId}/complete`)
}

export function startFinishShop(poolId: number): Promise<ApiResult<any>> {
  return post(`/shop/pool/${poolId}/finish`)
}

export function confirmShopPool(poolId: number, confirmed: boolean): Promise<ApiResult<any>> {
  return post(`/shop/pool/${poolId}/confirm`, { confirmed })
}

export function cancelShopPool(poolId: number): Promise<ApiResult<any>> {
  return post(`/shop/pool/${poolId}/cancel`)
}

// === Order ===
export function getShopOrders(params?: any): Promise<ApiResult<PageResult<OrderItem>>> {
  return get('/shop/order/list', params)
}

// === Review ===
export function getShopReviews(): Promise<ApiResult<any[]>> {
  return get('/shop/review/my')
}

// === Chat ===
export function getShopChatHistory(params: { poolId: number; page?: number; size?: number }): Promise<ApiResult<PageResult<ChatMessage>>> {
  return get('/shop/chat/history', params)
}

export function sendShopChatMessage(data: { poolId: number; content: string }): Promise<ApiResult<void>> {
  return post('/shop/chat/send', data)
}
