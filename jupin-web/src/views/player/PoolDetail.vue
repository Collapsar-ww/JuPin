<template>
  <div class="pool-detail" v-loading="loading">
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
          <el-button v-if="canJoin" type="primary" :loading="joining" @click="handleJoin">
            加入拼车
          </el-button>
          <el-button v-if="canConfirmComplete" type="success" :loading="confirmingComplete" @click="handleStartComplete">
            确认成团
          </el-button>
          <el-button v-if="canStartFinish" type="success" :loading="confirmingFinish" @click="handleStartFinish">
            确认结束
          </el-button>
          <el-button v-if="canPayDeposit" type="warning" :loading="paying" @click="handlePayDeposit">
            支付押金
          </el-button>
          <el-button v-if="canPayRemaining" type="warning" :loading="paying" @click="handlePayRemaining">
            支付尾款
          </el-button>
          <el-button v-if="canCancel" type="danger" plain @click="handleCancel">取消拼车</el-button>
          <el-button v-if="canLeave" type="info" plain @click="handleLeave">退出</el-button>
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
        <el-descriptions-item label="发布人">{{ pool.ownerName }}</el-descriptions-item>
        <el-descriptions-item label="DM">{{ pool.dmName || '待指定' }}</el-descriptions-item>
        <el-descriptions-item label="加入方式">{{ pool.joinType === 1 ? '自动通过' : '审核制' }}</el-descriptions-item>
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
            <el-tag v-else-if="row.status === 0" size="small">待审核</el-tag>
            <el-tag v-else-if="row.status === 3" size="small" type="danger">已退出</el-tag>
            <el-tag v-else size="small" type="info">已拒绝</el-tag>
          </template>
        </el-table-column>
      </el-table>

      <el-collapse v-if="isMember" style="margin-top: 16px">
        <el-collapse-item title="群聊" name="chat">
          <div class="chat-box" ref="chatRef">
            <div v-for="msg in chatMessages" :key="msg.id" class="chat-msg">
              <span class="chat-sender">{{ msg.senderName }}：</span>
              <span>{{ msg.content }}</span>
              <span class="chat-time">{{ formatDateTime(msg.createTime) }}</span>
            </div>
            <el-empty v-if="chatMessages.length === 0" description="暂无消息" />
          </div>
          <div v-if="canChat" class="chat-input">
            <el-input v-model="chatContent" placeholder="发送消息" size="small" @keyup.enter="sendChat" />
            <el-button type="primary" size="small" @click="sendChat">发送</el-button>
          </div>
        </el-collapse-item>
      </el-collapse>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getPlayerPoolDetail, joinPool, leavePool, cancelPool,
  createOrder, payOrder, startComplete, startFinish,
  getChatHistory, sendChatMessage,
} from '../../api/player'
import type { PoolDetail, ChatMessage } from '../../api/player'
import { useAuthStore } from '../../stores/auth'
import { POOL_STATUS } from '../../constants'
import StatusTag from '../../components/StatusTag.vue'
import { formatDateTime, formatPrice } from '../../utils/format'

const route = useRoute()
const auth = useAuthStore()

const loading = ref(true)
const joining = ref(false)
const paying = ref(false)
const confirmingComplete = ref(false)
const confirmingFinish = ref(false)

const pool = ref<PoolDetail | null>(null)
const chatMessages = ref<ChatMessage[]>([])
const chatContent = ref('')
const chatRef = ref<HTMLElement>()

const isOwner = computed(() => pool.value?.ownerId === auth.user?.id)
const isMember = computed(() => pool.value?.members.some(m => m.userId === auth.user?.id && m.status === 2))
const myMember = computed(() => pool.value?.members.find(m => m.userId === auth.user?.id))

const canJoin = computed(() => pool.value?.status === POOL_STATUS.OPEN && !pool.value?.members.some(m => m.userId === auth.user?.id))
const canPayDeposit = computed(() => myMember.value?.status === 1)
const canPayRemaining = computed(() => pool.value?.status === POOL_STATUS.COMPLETED && isMember.value)
const canCancel = computed(() => isOwner.value && (pool.value?.status === POOL_STATUS.OPEN || pool.value?.status === POOL_STATUS.FULL))
const canLeave = computed(() => {
  if (!myMember.value) return false
  return myMember.value.status < 3 && myMember.value.userId !== pool.value?.ownerId
})
const canConfirmComplete = computed(() => pool.value?.status === POOL_STATUS.FULL && isOwner.value)
const canStartFinish = computed(() => pool.value?.status === POOL_STATUS.COMPLETED && isOwner.value)
const canChat = computed(() => isMember.value && pool.value?.status !== POOL_STATUS.FINISHED && pool.value?.status !== POOL_STATUS.CANCELLED)

async function loadDetail() {
  loading.value = true
  try {
    const res = await getPlayerPoolDetail(Number(route.params.id))
    pool.value = res.data
    loadChatHistory()
  } finally {
    loading.value = false
  }
}

async function handleJoin() {
  joining.value = true
  try {
    await joinPool(pool.value!.id)
    ElMessage.success('加入成功，请支付押金')
    loadDetail()
  } finally {
    joining.value = false
  }
}

async function handlePayDeposit() {
  paying.value = true
  try {
    const order = await createOrder({ poolId: pool.value!.id, type: 0 })
    await payOrder(order.data.orderNo)
    ElMessage.success('押金支付成功')
    loadDetail()
  } finally {
    paying.value = false
  }
}

async function handlePayRemaining() {
  paying.value = true
  try {
    const order = await createOrder({ poolId: pool.value!.id, type: 1 })
    await payOrder(order.data.orderNo)
    ElMessage.success('尾款支付成功')
    loadDetail()
  } finally {
    paying.value = false
  }
}

async function handleStartComplete() {
  confirmingComplete.value = true
  try {
    await startComplete(pool.value!.id)
    ElMessage.success('已发起成团确认，请通知成员确认')
    loadDetail()
  } finally {
    confirmingComplete.value = false
  }
}

async function handleStartFinish() {
  confirmingFinish.value = true
  try {
    await startFinish(pool.value!.id)
    ElMessage.success('已发起结束确认，请通知成员确认')
    loadDetail()
  } finally {
    confirmingFinish.value = false
  }
}

async function handleCancel() {
  try {
    await ElMessageBox.confirm('确定要取消该拼车吗？')
    await cancelPool(pool.value!.id)
    ElMessage.success('拼车已取消')
    loadDetail()
  } catch {
    // cancelled
  }
}

async function handleLeave() {
  try {
    await ElMessageBox.confirm('确定要退出该拼车吗？')
    await leavePool(pool.value!.id)
    ElMessage.success('已退出')
    loadDetail()
  } catch {
    // cancelled
  }
}

async function loadChatHistory() {
  try {
    const res = await getChatHistory({ poolId: Number(route.params.id), page: 1, size: 50 })
    chatMessages.value = res.data.records
    await nextTick()
    if (chatRef.value) {
      chatRef.value.scrollTop = chatRef.value.scrollHeight
    }
  } catch {
    // ok
  }
}

async function sendChat() {
  if (!chatContent.value.trim()) return
  try {
    await sendChatMessage({ poolId: pool.value!.id, content: chatContent.value })
    chatContent.value = ''
    loadChatHistory()
  } catch {
    // handled
  }
}

onMounted(loadDetail)
</script>

<style scoped>
.pool-detail { max-width: 800px; margin: 0 auto; }
.detail-header { display: flex; justify-content: space-between; align-items: flex-start; }
.detail-header h3 { margin: 0 0 8px; font-size: 20px; }
.detail-tags { display: flex; gap: 6px; }
.detail-actions { display: flex; gap: 8px; flex-wrap: wrap; }
.chat-box {
  max-height: 300px; overflow-y: auto; padding: 8px;
  background: #f5f7fa; border-radius: 4px; margin-bottom: 8px;
}
.chat-msg { padding: 4px 0; font-size: 14px; }
.chat-sender { font-weight: 600; color: #409eff; }
.chat-time { font-size: 12px; color: #c0c4cc; margin-left: 8px; }
.chat-input { display: flex; gap: 8px; }
</style>
