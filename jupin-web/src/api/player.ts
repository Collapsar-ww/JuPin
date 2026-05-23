import { get, post, put } from '../utils/request'
import type { ApiResult } from '../utils/request'
import type { UserInfo } from './auth'

export interface PoolListItem {
  id: number
  type: number
  ownerId: number
  ownerNickname: string
  shopId: number | null
  shopName: string | null
  scriptId: number
  scriptName: string
  scriptType: string
  city: string
  address: string
  startTime: string
  endTime: string | null
  maxMembers: number
  currentMembers: number
  price: number
  deposit: number
  dmId: number | null
  dmNickname: string | null
  joinType: number
  status: number
  createTime: string
}

export interface PoolDetail extends PoolListItem {
  members: PoolMemberItem[]
  roles: string | null
}

export interface PoolMemberItem {
  id: number
  userId: number
  nickname: string
  avatar: string | null
  role: number
  status: number
}

export interface ShopListItem {
  id: number
  name: string
  city: string
  address: string
  phone: string
  logo: string | null
  cover: string | null
  description: string
  openingHours: string | null
  rating: number | null
  ratingText: string
}

export interface ShopDetail extends ShopListItem {
  pools?: PoolListItem[]
}

export interface ScriptItem {
  id: number
  name: string
  type: string
  difficulty: number
  minPlayers: number
  maxPlayers: number
  duration: number | null
  cover: string | null
  priceRef: number | null
  description: string | null
}

export interface OrderItem {
  id: number
  orderNo: string
  poolId: number
  type: number
  amount: number
  status: number
  releaseStatus: number
  refundReason: string | null
  createTime: string
  payTime: string | null
  refundTime: string | null
}

export interface Preference {
  city: string
  scriptType: string
  priceMin: number
  priceMax: number
  timeSlot: string
  minMembers: number
  maxMembers: number
}

export interface CreatePoolRequest {
  scriptId: number
  scriptName: string
  scriptType: string
  city: string
  address: string
  startTime: string
  endTime?: string
  maxMembers: number
  price: number
  joinType: number
}

export interface ChatMessage {
  id: number
  poolId: number
  senderId: number
  senderName: string
  senderRole: string
  content: string
  createTime: string
}

export interface MessageItem {
  id: number
  type: number
  title: string
  content: string
  relatedId: number | null
  isRead: number
  createTime: string
}

// === Player Pool ===
export function getPlayerPoolList(params?: any): Promise<ApiResult<PoolListItem[]>> {
  return get('/player/pool/list', params)
}

export function getPlayerPoolDetail(id: number): Promise<ApiResult<PoolDetail>> {
  return get(`/player/pool/${id}`)
}

export function createPlayerPool(data: CreatePoolRequest): Promise<ApiResult<{ id: number; status: number }>> {
  return post('/player/pool/create', data)
}

export function joinPool(poolId: number): Promise<ApiResult<any>> {
  return post(`/player/pool/${poolId}/join`)
}

export function leavePool(poolId: number): Promise<ApiResult<any>> {
  return post(`/player/pool/${poolId}/leave`)
}

export function cancelPool(poolId: number): Promise<ApiResult<any>> {
  return post(`/player/pool/${poolId}/cancel`)
}

export function startComplete(poolId: number): Promise<ApiResult<any>> {
  return post(`/player/pool/${poolId}/complete`)
}

export function startFinish(poolId: number): Promise<ApiResult<any>> {
  return post(`/player/pool/${poolId}/finish`)
}

export function confirmPool(poolId: number, confirmed: boolean): Promise<ApiResult<any>> {
  return post(`/player/pool/${poolId}/confirm`, { confirmed })
}

export function transferDm(poolId: number, targetUserId: number): Promise<ApiResult<any>> {
  return put(`/player/pool/${poolId}/transfer-dm`, { targetUserId })
}

// === Script ===
export function getScriptList(params?: any): Promise<ApiResult<ScriptItem[]>> {
  return get('/player/script/list', params)
}

// === Shop browsing (player) ===
export function getShopList(params?: any): Promise<ApiResult<ShopListItem[]>> {
  return get('/player/shop/list', params)
}

export function getShopDetail(id: number): Promise<ApiResult<ShopDetail>> {
  return get(`/player/shop/${id}`)
}

export function getShopScripts(id: number, params?: any): Promise<ApiResult<ScriptItem[]>> {
  return get(`/player/shop/${id}/scripts`, params)
}

export function getShopPools(id: number, params?: any): Promise<ApiResult<PoolListItem[]>> {
  return get(`/player/shop/${id}/pools`, params)
}

// === Order ===
export function createOrder(data: { poolId: number; type: number }): Promise<ApiResult<OrderItem>> {
  return post('/player/order/create', data)
}

export function payOrder(orderNo: string): Promise<ApiResult<OrderItem>> {
  return post(`/player/order/pay/${orderNo}`)
}

export function getMyOrders(params?: any): Promise<ApiResult<OrderItem[]>> {
  return get('/player/order/my', params)
}

// === Credit ===
export function getCreditScore(): Promise<ApiResult<number>> {
  return get('/player/credit/score')
}

export function getCreditLog(params?: any): Promise<ApiResult<any[]>> {
  return get('/player/credit/log', params)
}

// === Review ===
export function createReview(data: {
  poolId: number
  targetId: number
  type: number
  score: number
  content?: string
}): Promise<ApiResult<any>> {
  return post('/player/review/create', data)
}

export function getMyDmReviews(): Promise<ApiResult<any[]>> {
  return get('/player/review/my-dm')
}

// === User ===
export function getUserProfile(): Promise<ApiResult<UserInfo>> {
  return get('/player/user/me')
}

export function updateUserProfile(data: Partial<UserInfo>): Promise<ApiResult<void>> {
  return put('/player/user/me', data)
}

// === Preference ===
export function getPreference(): Promise<ApiResult<Preference>> {
  return get('/player/preference')
}

export function savePreference(data: Preference): Promise<ApiResult<void>> {
  return put('/player/preference', data)
}

// === Message ===
export function getMessageList(params?: any): Promise<ApiResult<MessageItem[]>> {
  return get('/player/message/list', params)
}

export function getUnreadCount(): Promise<ApiResult<number>> {
  return get('/player/message/unread-count')
}

export function markMessageRead(id: number): Promise<ApiResult<void>> {
  return put(`/player/message/read/${id}`)
}

export function markAllRead(): Promise<ApiResult<void>> {
  return put('/player/message/read-all')
}

// === Chat ===
export function getChatHistory(params: { poolId: number; page?: number; size?: number }): Promise<ApiResult<ChatMessage[]>> {
  return get('/player/chat/history', params)
}

export function sendChatMessage(data: { poolId: number; content: string }): Promise<ApiResult<void>> {
  return post('/player/chat/send', data)
}
