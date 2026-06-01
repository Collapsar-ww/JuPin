<template>
  <div class="my-page">
    <el-card shadow="never" class="user-card">
      <div class="user-info">
        <el-avatar :size="48">{{ user?.nickname?.charAt(0) }}</el-avatar>
        <div class="user-detail">
          <div class="user-name">{{ user?.nickname }}</div>
          <div class="user-meta">{{ user?.phone }} · 信用分：{{ user?.creditScore }}</div>
        </div>
        <el-button size="small" text type="primary" @click="showProfileEdit = true">
          编辑资料
        </el-button>
      </div>
    </el-card>

    <!-- Profile Edit Dialog -->
    <el-dialog v-model="showProfileEdit" title="编辑个人资料" width="400px">
      <el-form ref="profileFormRef" :model="profileForm" :rules="profileRules" label-width="80px">
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="profileForm.nickname" placeholder="2-20位" maxlength="20" />
        </el-form-item>
        <el-form-item label="性别" prop="gender">
          <el-radio-group v-model="profileForm.gender">
            <el-radio :value="0">未知</el-radio>
            <el-radio :value="1">男</el-radio>
            <el-radio :value="2">女</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showProfileEdit = false">取消</el-button>
        <el-button type="primary" :loading="profileSaving" @click="handleSaveProfile">保存</el-button>
      </template>
    </el-dialog>

    <el-tabs v-model="activeTab" class="my-tabs" style="margin-top: 16px">
      <el-tab-pane label="我的偏好" name="preference">
        <el-card shadow="never" v-loading="prefLoading">
          <el-form :model="pref" label-width="120px" size="small">
            <el-form-item label="常驻城市">
              <el-input v-model="pref.city" placeholder="例如：上海" />
            </el-form-item>
            <el-form-item label="偏好剧本类型">
              <el-select v-model="pref.scriptType" placeholder="选择类型" clearable>
                <el-option v-for="t in SCRIPT_TYPES" :key="t" :label="t" :value="t" />
              </el-select>
            </el-form-item>
            <el-form-item label="最低价格">
              <el-input-number v-model="pref.priceMin" :min="0" :precision="2" />
            </el-form-item>
            <el-form-item label="最高价格">
              <el-input-number v-model="pref.priceMax" :min="0" :precision="2" />
            </el-form-item>
            <el-form-item label="常玩时间段">
              <el-select v-model="pref.timeSlot" placeholder="选择时间段" clearable>
                <el-option label="工作日晚上" value="WEEKDAY_NIGHT" />
                <el-option label="周末下午" value="WEEKEND_AFTERNOON" />
                <el-option label="周末晚上" value="WEEKEND_NIGHT" />
              </el-select>
            </el-form-item>
            <el-form-item label="可接受人数">
              <el-input-number v-model="pref.minMembers" :min="2" /> ~
              <el-input-number v-model="pref.maxMembers" :max="20" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="savePref">保存偏好</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="我的拼车" name="pools">
        <div v-loading="poolLoading">
          <template v-if="ownedPools.length > 0">
            <h4 style="margin: 0 0 8px">我发布的</h4>
            <PoolCard v-for="pool in ownedPools" :key="'own-' + pool.id" :pool="pool" />
          </template>
          <template v-if="joinedPools.length > 0">
            <h4 style="margin: 16px 0 8px">我参与的</h4>
            <el-card v-for="pool in joinedPools" :key="'join-' + pool.poolId" class="joined-pool-card" shadow="hover" @click="router.push('/player/pools/' + pool.poolId)">
              <div class="joined-pool-info">
                <span class="pool-name">{{ pool.scriptName }}</span>
                <el-tag v-if="pool.memberStatus === MEMBER_STATUS.LEFT" size="small" type="danger">已退出</el-tag>
                <el-tag v-else-if="pool.memberStatus === MEMBER_STATUS.JOINED" size="small" type="success">已加入</el-tag>
                <el-tag v-else-if="pool.memberStatus === MEMBER_STATUS.PENDING_PAYMENT" size="small" type="warning">待支付</el-tag>
                <el-tag v-else-if="pool.memberStatus === MEMBER_STATUS.PENDING_REVIEW" size="small">待审核</el-tag>
                <StatusTag v-if="pool.memberStatus === MEMBER_STATUS.JOINED || pool.memberStatus === MEMBER_STATUS.LEFT" :status="pool.poolStatus" />
              </div>
            </el-card>
          </template>
          <el-empty v-if="ownedPools.length === 0 && joinedPools.length === 0" description="暂无拼车记录" />
        </div>
      </el-tab-pane>

      <el-tab-pane label="我的订单" name="orders">
        <div v-loading="orderLoading">
          <el-table v-if="orders.length > 0" :data="orders" size="small" border>
            <el-table-column prop="orderNo" label="订单号" width="180" />
            <el-table-column label="类型" width="60">
              <template #default="{ row }">{{ row.type === 0 ? '押金' : '车费' }}</template>
            </el-table-column>
            <el-table-column label="金额" width="80">
              <template #default="{ row }">¥{{ formatPrice(row.amount) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="80">
              <template #default="{ row }">
                <el-tag :type="orderStatusTag(row.status)" size="small">
                  {{ ORDER_STATUS_TEXT[row.status] || '未知' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="创建时间" width="160">
              <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="120">
              <template #default="{ row }">
                <el-button v-if="row.status === 0" type="warning" size="small" @click="handlePayOrder(row.orderNo)">
                  支付
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="orders.length === 0" description="暂无订单" />
        </div>
      </el-tab-pane>

      <el-tab-pane label="待办事项" name="todos">
        <div v-loading="todoLoading">
          <div v-for="todo in todos" :key="todo.key" class="todo-item">
            <div class="todo-info">
              <div class="todo-title">{{ todo.title }}</div>
              <div class="todo-desc">{{ todo.desc }}</div>
            </div>
            <el-button type="primary" size="small" @click="todo.action">
              {{ todo.actionText }}
            </el-button>
          </div>
          <el-empty v-if="todos.length === 0" description="暂无待办事项" />
        </div>
      </el-tab-pane>

      <el-tab-pane label="我的评价" name="reviews">
        <div v-loading="reviewLoading">
          <template v-if="dmReviews.length > 0">
            <h4 style="margin: 0 0 12px">我作为 DM 收到的评价</h4>
            <el-table :data="dmReviews" size="small" border>
              <el-table-column label="评分" width="80">
                <template #default="{ row }">
                  <el-rate :model-value="row.score" disabled show-score score-template="{value}" />
                </template>
              </el-table-column>
              <el-table-column prop="content" label="评价内容" min-width="200" />
              <el-table-column label="时间" width="160">
                <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
              </el-table-column>
            </el-table>
          </template>
          <el-empty v-else description="暂无评价" />
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { useAuthStore } from '../../stores/auth'
import { updateUserProfile } from '../../api/player'
import { getPlayerPoolList, getMyOrders, createOrder, payOrder, getPreference, savePreference, getMyMemberships, getMyDmReviews } from '../../api/player'
import type { PoolListItem, OrderItem, Preference, MemberPoolInfo } from '../../api/player'
import { SCRIPT_TYPES, ORDER_STATUS_TEXT, ORDER_STATUS, MEMBER_STATUS, POOL_STATUS } from '../../constants'
import PoolCard from '../../components/PoolCard.vue'
import StatusTag from '../../components/StatusTag.vue'
import { formatPrice, formatDateTime } from '../../utils/format'

const auth = useAuthStore()
const router = useRouter()

const user = computed(() => auth.user)
const activeTab = ref('preference')

// Profile editing (#17)
const showProfileEdit = ref(false)
const profileSaving = ref(false)
const profileFormRef = ref<FormInstance>()
const profileForm = reactive({ nickname: '', gender: 0 })
const profileRules = {
  nickname: [{ required: true, message: '请输入昵称', trigger: 'blur' }, { min: 2, max: 20, message: '昵称长度 2-20 位', trigger: 'blur' }],
}

const prefLoading = ref(false)
const pref = reactive<Preference>({
  city: '', scriptType: '', priceMin: 0, priceMax: 0,
  timeSlot: '', minMembers: 2, maxMembers: 6,
})

const poolLoading = ref(false)
const allPools = ref<PoolListItem[]>([])

const orderLoading = ref(false)
const orders = ref<OrderItem[]>([])
const orderPage = ref(1)
const orderSize = ref(10)

const todoLoading = ref(false)
const todos = ref<any[]>([])
const memberships = ref<MemberPoolInfo[]>([])
const reviewLoading = ref(false)
const dmReviews = ref<any[]>([])

const ownedPools = computed(() => allPools.value.filter(p => p.ownerId === auth.user?.id))
const joinedPools = computed(() => memberships.value)
const poolNameMap = computed(() => {
  const map: Record<number, string> = {}
  for (const m of memberships.value) {
    map[m.poolId] = m.scriptName
  }
  return map
})

watch([orders, memberships], () => { buildTodos() })

function orderStatusTag(status: number) {
  const map: Record<number, string> = {
    [ORDER_STATUS.PENDING]: 'warning',
    [ORDER_STATUS.PAID]: 'success',
    [ORDER_STATUS.REFUNDED]: 'info',
    [ORDER_STATUS.OVERDUE]: 'danger',
  }
  return map[status] || 'info'
}

async function loadPreference() {
  prefLoading.value = true
  try {
    const res = await getPreference()
    Object.assign(pref, res.data)
  } catch {
    // default values
  } finally {
    prefLoading.value = false
  }
}

async function savePref() {
  try {
    await savePreference({ ...pref })
    ElMessage.success('保存成功')
  } catch {
    // handled
  }
}

async function loadMyPools() {
  poolLoading.value = true
  try {
    const res = await getPlayerPoolList({ page: 1, size: 50 })
    allPools.value = res.data
  } catch {
    // handled
  } finally {
    poolLoading.value = false
  }
}

async function loadOrders(p?: number) {
  if (p) orderPage.value = p
  orderLoading.value = true
  try {
    const res = await getMyOrders({ page: orderPage.value, size: orderSize.value })
    orders.value = res.data
  } catch {
    // handled
  } finally {
    orderLoading.value = false
  }
}

async function handlePayOrder(orderNo: string) {
  try {
    await payOrder(orderNo)
    ElMessage.success('支付成功')
    loadOrders()
  } catch {
    // handled
  }
}

function buildTodos() {
  const list: any[] = []

  for (const o of orders.value) {
    if (o.status === ORDER_STATUS.PENDING) {
      list.push({
        key: `pay-${o.orderNo}`,
        type: 'payment',
        title: `待支付${o.type === 0 ? '押金' : '车费'}`,
        desc: `${poolNameMap.value[o.poolId] ? `《${poolNameMap.value[o.poolId]}》` : ''}订单 ¥${formatPrice(o.amount)}`,
        actionText: '去支付',
        action: () => handlePayOrder(o.orderNo),
      })
    }
  }

  for (const m of memberships.value) {
    if (m.memberStatus === MEMBER_STATUS.PENDING_PAYMENT) {
      const hasPendingOrder = orders.value.some(o => o.poolId === m.poolId && o.type === 0 && o.status === ORDER_STATUS.PENDING)
      if (!hasPendingOrder) {
        list.push({
          key: `deposit-${m.poolId}`,
          type: 'payment',
          title: '待支付押金',
          desc: `《${m.scriptName}》押金 ¥${formatPrice(m.deposit)}`,
          actionText: '去支付',
          action: () => handleTodoPay(m),
        })
      }
    }

    if (m.memberStatus === MEMBER_STATUS.JOINED) {
      if (m.poolStatus === POOL_STATUS.FULL && m.completedConfirmed === 0) {
        list.push({
          key: `confirm-${m.poolId}`,
          type: 'confirm',
          title: '待确认成团',
          desc: `《${m.scriptName}》请确认拼车成功`,
          actionText: '去确认',
          action: () => router.push(`/player/pools/${m.poolId}`),
        })
      }
      if (m.poolStatus === POOL_STATUS.COMPLETED && m.finishedConfirmed === 0) {
        list.push({
          key: `finish-${m.poolId}`,
          type: 'confirm',
          title: '待确认结束',
          desc: `《${m.scriptName}》请确认剧本杀已完成`,
          actionText: '去确认',
          action: () => router.push(`/player/pools/${m.poolId}`),
        })
      }
      if (m.poolStatus === POOL_STATUS.FINISHED) {
        list.push({
          key: `review-${m.poolId}`,
          type: 'review',
          title: '待评价',
          desc: `《${m.scriptName}》拼车已完成，去评价`,
          actionText: '去评价',
          action: () => router.push(`/player/pools/${m.poolId}`),
        })
      }
    }
  }
  todos.value = list
}

async function loadMemberships() {
  todoLoading.value = true
  try {
    const res = await getMyMemberships()
    if (!Array.isArray(res.data)) {
      throw new Error('我的拼车接口返回格式异常')
    }
    memberships.value = res.data
  } catch {
    memberships.value = []
    ElMessage.error('我的拼车加载失败，请稍后重试')
  } finally {
    todoLoading.value = false
  }
  // Always rebuild todos after memberships load, regardless of other API results
  buildTodos()
}

async function loadDmReviews() {
  reviewLoading.value = true
  try {
    const res = await getMyDmReviews()
    dmReviews.value = res.data
  } catch {
    // handled
  } finally {
    reviewLoading.value = false
  }
}

// Profile editing: populate form when dialog opens
watch(showProfileEdit, (v) => {
  if (v && user.value) {
    profileForm.nickname = user.value.nickname
    profileForm.gender = user.value.gender
  }
})

async function handleSaveProfile() {
  const valid = await profileFormRef.value?.validate().catch(() => false)
  if (!valid) return
  profileSaving.value = true
  try {
    await updateUserProfile({ nickname: profileForm.nickname, gender: profileForm.gender })
    ElMessage.success('保存成功')
    showProfileEdit.value = false
    if (auth.user) {
      auth.user.nickname = profileForm.nickname
      auth.user.gender = profileForm.gender
    }
  } catch {
    // handled
  } finally {
    profileSaving.value = false
  }
}

// Enhance #5: try to create order and pay directly from todo action
async function handleTodoPay(m: MemberPoolInfo) {
  todoLoading.value = true
  try {
    const order = await createOrder({ poolId: m.poolId, type: 0 })
    await payOrder(order.data.orderNo)
    ElMessage.success('押金支付成功')
    await Promise.all([loadOrders(), loadMemberships()])
  } catch {
    // fallback: navigate to pool detail
    router.push(`/player/pools/${m.poolId}`)
  } finally {
    todoLoading.value = false
  }
}

onMounted(async () => {
  await Promise.allSettled([loadPreference(), loadMyPools(), loadOrders(), loadMemberships(), loadDmReviews()])
})
</script>

<style scoped>
.my-page { max-width: 800px; margin: 0 auto; }
.user-card { margin-bottom: 8px; }
.user-info { display: flex; align-items: center; gap: 12px; }
.user-name { font-size: 16px; font-weight: 600; }
.user-meta { font-size: 12px; color: #909399; }
.my-tabs { background: #fff; padding: 16px; border-radius: 4px; }
.todo-item {
  display: flex; align-items: center; justify-content: space-between;
  padding: 12px; border-bottom: 1px solid #f0f0f0;
}
.todo-item:last-child { border-bottom: none; }
.todo-title { font-size: 14px; font-weight: 500; }
.todo-desc { font-size: 12px; color: #909399; }
.joined-pool-card { margin-bottom: 8px; cursor: pointer; }
.joined-pool-info { display: flex; align-items: center; gap: 8px; }
.pool-name { font-size: 14px; }
</style>
