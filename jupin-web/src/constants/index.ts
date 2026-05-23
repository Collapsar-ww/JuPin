export const USER_ROLE = {
  PLAYER: 'player',
  SHOP: 'shop',
  ADMIN: 'admin',
} as const

export type UserRole = (typeof USER_ROLE)[keyof typeof USER_ROLE]

export const POOL_STATUS = {
  OPEN: 0,
  FULL: 1,
  COMPLETED: 2,
  FINISHED: 3,
  CANCELLED: 4,
} as const

export const POOL_STATUS_TEXT: Record<number, string> = {
  [POOL_STATUS.OPEN]: '开放招募',
  [POOL_STATUS.FULL]: '已满员',
  [POOL_STATUS.COMPLETED]: '拼车成功',
  [POOL_STATUS.FINISHED]: '已完成',
  [POOL_STATUS.CANCELLED]: '已取消',
}

export const POOL_STATUS_TAG = {
  [POOL_STATUS.OPEN]: 'success',
  [POOL_STATUS.FULL]: 'warning',
  [POOL_STATUS.COMPLETED]: 'primary',
  [POOL_STATUS.FINISHED]: 'info',
  [POOL_STATUS.CANCELLED]: 'danger',
} as const

export const POOL_TYPE = {
  PLAYER: 0,
  SHOP: 1,
} as const

export const POOL_TYPE_TEXT: Record<number, string> = {
  [POOL_TYPE.PLAYER]: '玩家局',
  [POOL_TYPE.SHOP]: '店家局',
}

export const MEMBER_STATUS = {
  PENDING_REVIEW: 0,
  PENDING_PAYMENT: 1,
  JOINED: 2,
  LEFT: 3,
  REJECTED: 4,
} as const

export const ORDER_TYPE = {
  DEPOSIT: 0,
  REMAINING: 1,
} as const

export const ORDER_STATUS = {
  PENDING: 0,
  PAID: 1,
  REFUNDED: 2,
  DETAINED: 3,
  OVERDUE: 4,
} as const

export const ORDER_STATUS_TEXT: Record<number, string> = {
  [ORDER_STATUS.PENDING]: '待支付',
  [ORDER_STATUS.PAID]: '已支付',
  [ORDER_STATUS.REFUNDED]: '已退款',
  [ORDER_STATUS.DETAINED]: '已扣留',
  [ORDER_STATUS.OVERDUE]: '逾期',
}

export const SCRIPT_TYPES = ['硬核', '情感', '欢乐', '恐怖', '机制'] as const

export const JOIN_TYPE = {
  AUTO: 1,
  REVIEW: 0,
} as const

export const JOIN_TYPE_TEXT: Record<number, string> = {
  [JOIN_TYPE.AUTO]: '自动通过',
  [JOIN_TYPE.REVIEW]: '审核制',
}

export const GENDER = {
  UNKNOWN: 0,
  MALE: 1,
  FEMALE: 2,
} as const

export const GENDER_TEXT: Record<number, string> = {
  [GENDER.UNKNOWN]: '未知',
  [GENDER.MALE]: '男',
  [GENDER.FEMALE]: '女',
}
