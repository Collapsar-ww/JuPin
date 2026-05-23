<template>
  <div class="my-page">
    <el-card shadow="never" class="user-card">
      <div class="user-info">
        <el-avatar :size="48">{{ user?.nickname?.charAt(0) }}</el-avatar>
        <div class="user-detail">
          <div class="user-name">{{ user?.nickname }}</div>
          <div class="user-meta">{{ user?.phone }} · 信用分：{{ user?.creditScore }}</div>
        </div>
      </div>
    </el-card>

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
          <PoolCard v-for="pool in myPools" :key="pool.id" :pool="pool" />
          <el-empty v-if="myPools.length === 0" description="暂无拼车记录" />
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
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../../stores/auth'
import { getPlayerPoolList, getMyOrders, payOrder, getPreference, savePreference } from '../../api/player'
import type { PoolListItem, OrderItem, Preference } from '../../api/player'
import { SCRIPT_TYPES, ORDER_STATUS_TEXT, ORDER_STATUS } from '../../constants'
import PoolCard from '../../components/PoolCard.vue'
import { formatPrice, formatDateTime } from '../../utils/format'

const auth = useAuthStore()

const user = computed(() => auth.user)
const activeTab = ref('preference')

const prefLoading = ref(false)
const pref = reactive<Preference>({
  city: '', scriptType: '', priceMin: 0, priceMax: 0,
  timeSlot: '', minMembers: 2, maxMembers: 6,
})

const poolLoading = ref(false)
const myPools = ref<PoolListItem[]>([])

const orderLoading = ref(false)
const orders = ref<OrderItem[]>([])
const orderPage = ref(1)
const orderSize = ref(10)

const todoLoading = ref(false)
const todos = ref<any[]>([])

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
    myPools.value = res.data.filter(
      p => p.ownerId === auth.user?.id
    )
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
        desc: `订单 ¥${formatPrice(o.amount)}`,
        actionText: '去支付',
        action: () => handlePayOrder(o.orderNo),
      })
    }
  }
  todos.value = list
}

onMounted(async () => {
  await Promise.all([loadPreference(), loadMyPools(), loadOrders()])
  buildTodos()
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
</style>
