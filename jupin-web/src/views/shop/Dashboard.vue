<template>
  <div class="shop-dashboard" v-loading="loading">
    <h3>工作台总览</h3>

    <el-row :gutter="16" style="margin-bottom: 16px">
      <el-col :span="6">
        <el-card shadow="never">
          <div class="stat-item">
            <div class="stat-value">{{ stats.openPools }}</div>
            <div class="stat-label">开放中店铺拼车</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="stat-item">
            <div class="stat-value">{{ stats.pendingReviews }}</div>
            <div class="stat-label">待审核成员</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="stat-item">
            <div class="stat-value">{{ stats.todayPools }}</div>
            <div class="stat-label">今日开局</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="stat-item">
            <div class="stat-value">{{ stats.rating || '-' }}</div>
            <div class="stat-label">评价均分</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never">
      <template #header>
        <span>最近拼车</span>
      </template>
      <el-table v-if="pools.length > 0" :data="pools" size="small" border>
        <el-table-column prop="scriptName" label="剧本" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <StatusTag :status="row.status" />
          </template>
        </el-table-column>
        <el-table-column label="人数" width="80">
          <template #default="{ row }">{{ row.currentMembers }}/{{ row.maxMembers }}</template>
        </el-table-column>
        <el-table-column label="开始时间" width="160">
          <template #default="{ row }">{{ formatDateTime(row.startTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button size="small" @click="$router.push(`/shop/pools/${row.id}`)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-else description="暂无拼车" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getCurrentShop, getShopPoolList, getShopReviews } from '../../api/shop'
import type { PoolListItem } from '../../api/player'
import StatusTag from '../../components/StatusTag.vue'
import { formatDateTime } from '../../utils/format'

const loading = ref(false)
const pools = ref<PoolListItem[]>([])
const stats = ref({
  openPools: 0,
  pendingReviews: 0,
  todayPools: 0,
  rating: '-' as string | number,
})

async function loadDashboard() {
  loading.value = true
  try {
    await getCurrentShop()
    const poolRes = await getShopPoolList({ page: 1, size: 20 })

    pools.value = poolRes.data
    stats.value.openPools = poolRes.data.filter(p => p.status === 0).length
    stats.value.todayPools = poolRes.data.filter(p => {
      const today = new Date().toISOString().substring(0, 10)
      return p.startTime?.startsWith(today)
    }).length

    try {
      const reviewRes = await getShopReviews()
      const reviews = reviewRes.data
      if (reviews && reviews.length > 0) {
        const avg = reviews.reduce((s: number, r: any) => s + (r.score || 0), 0) / reviews.length
        stats.value.rating = avg.toFixed(1)
      }
    } catch {
      // no reviews yet
    }
  } finally {
    loading.value = false
  }
}

onMounted(loadDashboard)
</script>

<style scoped>
.stat-item { text-align: center; padding: 8px 0; }
.stat-value { font-size: 28px; font-weight: 700; color: #409eff; }
.stat-label { font-size: 13px; color: #909399; margin-top: 4px; }
</style>
