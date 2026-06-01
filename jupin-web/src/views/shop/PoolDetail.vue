<template>
  <div class="shop-pool-detail" v-loading="loading">
    <el-button text @click="$router.back()" style="margin-bottom: 12px">← 返回</el-button>

    <el-card v-if="pool" shadow="never">
      <div class="detail-header">
        <div>
          <h3>{{ pool.scriptName }}</h3>
          <div class="detail-tags">
            <StatusTag :status="pool.status" />
            <el-tag size="small" effect="plain">{{ pool.scriptType }}</el-tag>
            <el-tag size="small" effect="plain">{{ pool.city }}</el-tag>
          </div>
        </div>
        <div class="detail-actions">
          <el-button v-if="canConfirmComplete" type="success" @click="handleComplete">发起完成确认</el-button>
          <el-button v-if="canStartFinish" type="success" @click="handleFinish">发起结束确认</el-button>
          <el-button v-if="canCancel" type="danger" plain @click="handleCancel">取消拼车</el-button>
        </div>
      </div>

      <el-descriptions :column="2" border style="margin-top: 16px">
        <el-descriptions-item label="剧本类型">{{ pool.scriptType }}</el-descriptions-item>
        <el-descriptions-item label="城市">{{ pool.city }}</el-descriptions-item>
        <el-descriptions-item label="地址">{{ pool.address || '-' }}</el-descriptions-item>
        <el-descriptions-item label="开始时间">{{ formatDateTime(pool.startTime) }}</el-descriptions-item>
        <el-descriptions-item label="人数进度">{{ pool.currentMembers }}/{{ pool.maxMembers }}</el-descriptions-item>
        <el-descriptions-item label="人均费用">¥{{ formatPrice(pool.price) }}</el-descriptions-item>
        <el-descriptions-item label="押金">¥{{ formatPrice(pool.deposit) }}</el-descriptions-item>
        <el-descriptions-item label="DM">{{ pool.dmNickname || '待指定' }}</el-descriptions-item>
        <el-descriptions-item label="成团确认">
          <el-tag size="small" :type="completeConfirmStarted ? 'warning' : 'info'">
            {{ completeConfirmStarted ? '进行中' : '未开始' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="结束确认">
          <el-tag size="small" :type="finishConfirmStarted ? 'warning' : 'info'">
            {{ finishConfirmStarted ? '进行中' : '未开始' }}
          </el-tag>
        </el-descriptions-item>
      </el-descriptions>

      <h4 style="margin: 20px 0 8px">成员列表</h4>
      <el-table :data="pool.members" size="small" border>
        <el-table-column prop="nickname" label="昵称" />
        <el-table-column label="角色" width="80">
          <template #default="{ row }">
            <el-tag v-if="row.role === 1" size="small" type="warning">发布人</el-tag>
            <el-tag v-else size="small">玩家</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.status === 2" size="small" type="success">已加入</el-tag>
            <el-tag v-else-if="row.status === 1" size="small" type="warning">待支付</el-tag>
            <el-tag v-else-if="row.status === 3" size="small" type="danger">已退出</el-tag>
            <el-tag v-else size="small" type="info">其他</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="押金" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="orderTagType(row.depositOrderStatus, row.status === MEMBER_STATUS.PENDING_PAYMENT || row.status === MEMBER_STATUS.JOINED)">
              {{ orderText(row.depositOrderStatus, row.status === MEMBER_STATUS.PENDING_PAYMENT || row.status === MEMBER_STATUS.JOINED) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="成团确认" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="confirmTagType(row.completedConfirmed, completeConfirmStarted)">
              {{ confirmText(row.completedConfirmed, completeConfirmStarted) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="尾款" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="orderTagType(row.remainingOrderStatus, pool.status >= POOL_STATUS.COMPLETED)">
              {{ orderText(row.remainingOrderStatus, pool.status >= POOL_STATUS.COMPLETED) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="结束确认" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="confirmTagType(row.finishedConfirmed, finishConfirmStarted)">
              {{ confirmText(row.finishedConfirmed, finishConfirmStarted) }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getShopPoolDetail, startCompleteShop, startFinishShop, cancelShopPool } from '../../api/shop'
import type { PoolDetail } from '../../api/player'
import { useAuthStore } from '../../stores/auth'
import { MEMBER_STATUS, ORDER_STATUS, POOL_STATUS } from '../../constants'
import StatusTag from '../../components/StatusTag.vue'
import { formatDateTime, formatPrice } from '../../utils/format'
import { subscribePool } from '../../utils/poolSocket'

const route = useRoute()
const auth = useAuthStore()

const loading = ref(false)
const pool = ref<PoolDetail | null>(null)
let refreshTimer: number | undefined
let unsubscribePool: (() => void) | undefined

const isOwner = computed(() => pool.value?.ownerId === auth.user?.id)
const joinedMembers = computed(() => pool.value?.members.filter(m => m.status === MEMBER_STATUS.JOINED) ?? [])
const completeRejected = computed(() => joinedMembers.value.some(m => m.completedConfirmed === 2))
const finishRejected = computed(() => joinedMembers.value.some(m => m.finishedConfirmed === 2))
const completeConfirmStarted = computed(() => !!pool.value?.completedConfirmStarted || (joinedMembers.value.some(m => !!m.completedConfirmTime) && !completeRejected.value))
const finishConfirmStarted = computed(() => !!pool.value?.finishedConfirmStarted || (joinedMembers.value.some(m => !!m.finishedConfirmTime) && !finishRejected.value))
const isFullByCount = computed(() => !!pool.value && pool.value.currentMembers >= pool.value.maxMembers)
const canConfirmComplete = computed(() => isOwner.value && pool.value?.status === POOL_STATUS.FULL && isFullByCount.value && !completeConfirmStarted.value)
const canStartFinish = computed(() => isOwner.value && pool.value?.status === POOL_STATUS.COMPLETED && !finishConfirmStarted.value)
const canCancel = computed(() => isOwner.value && (pool.value?.status === POOL_STATUS.OPEN || pool.value?.status === POOL_STATUS.FULL))

function orderText(status: number | null | undefined, expected: boolean) {
  if (status === ORDER_STATUS.PAID) return '已支付'
  if (status === ORDER_STATUS.PENDING) return '待支付'
  if (status === ORDER_STATUS.REFUNDED) return '已退款'
  if (status === ORDER_STATUS.OVERDUE) return '逾期'
  return expected ? '未创建' : '未到阶段'
}

function orderTagType(status: number | null | undefined, expected: boolean) {
  if (status === ORDER_STATUS.PAID) return 'success'
  if (status === ORDER_STATUS.PENDING) return 'warning'
  if (status === ORDER_STATUS.REFUNDED) return 'info'
  if (status === ORDER_STATUS.OVERDUE) return 'danger'
  return expected ? 'warning' : 'info'
}

function confirmText(status: number | null | undefined, started: boolean) {
  if (status === 1) return '已确认'
  if (status === 2) return '已拒绝'
  return started ? '待确认' : '未开始'
}

function confirmTagType(status: number | null | undefined, started: boolean) {
  if (status === 1) return 'success'
  if (status === 2) return 'danger'
  return started ? 'warning' : 'info'
}

async function loadDetail() {
  loading.value = true
  try {
    const res = await getShopPoolDetail(Number(route.params.id))
    pool.value = res.data
  } finally {
    loading.value = false
  }
}

async function handleComplete() {
  try {
    await startCompleteShop(pool.value!.id)
    if (pool.value) {
      pool.value.completedConfirmStarted = true
      pool.value.members = pool.value.members.map((member) => member.status === MEMBER_STATUS.JOINED
        ? { ...member, completedConfirmTime: member.completedConfirmTime || new Date().toISOString(), completedConfirmed: 0 }
        : member)
    }
    ElMessage.success('已发起完成确认')
    await loadDetail()
  } catch { /* handled */ }
}

async function handleFinish() {
  try {
    await startFinishShop(pool.value!.id)
    if (pool.value) {
      pool.value.finishedConfirmStarted = true
      pool.value.members = pool.value.members.map((member) => member.status === MEMBER_STATUS.JOINED
        ? { ...member, finishedConfirmTime: member.finishedConfirmTime || new Date().toISOString(), finishedConfirmed: 0 }
        : member)
    }
    ElMessage.success('已发起结束确认')
    await loadDetail()
  } catch { /* handled */ }
}

async function handleCancel() {
  try {
    await ElMessageBox.confirm('确定取消该拼车吗？')
    await cancelShopPool(pool.value!.id)
    ElMessage.success('已取消')
    loadDetail()
  } catch { /* cancelled */ }
}

onMounted(() => {
  loadDetail()
  unsubscribePool = subscribePool(Number(route.params.id), () => {
    loadDetail()
  })
  refreshTimer = window.setInterval(() => {
    if (pool.value?.status === POOL_STATUS.FULL || pool.value?.status === POOL_STATUS.COMPLETED) {
      loadDetail()
    }
  }, 5000)
})

onUnmounted(() => {
  if (refreshTimer) window.clearInterval(refreshTimer)
  if (unsubscribePool) unsubscribePool()
})
</script>

<style scoped>
.shop-pool-detail { max-width: 800px; margin: 0 auto; }
.detail-header { display: flex; justify-content: space-between; align-items: flex-start; }
.detail-header h3 { margin: 0 0 8px; font-size: 20px; }
.detail-tags { display: flex; gap: 6px; }
.detail-actions { display: flex; gap: 8px; flex-wrap: wrap; }
</style>
