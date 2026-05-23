<template>
  <el-card class="pool-card" shadow="hover" @click="$router.push(`/player/pools/${pool.id}`)">
    <div class="pool-card-header">
      <span class="pool-name">{{ pool.scriptName }}</span>
      <StatusTag :status="pool.status" />
    </div>
    <div class="pool-card-tags">
      <el-tag size="small" effect="plain">{{ pool.scriptType }}</el-tag>
      <el-tag size="small" effect="plain">{{ pool.city }}</el-tag>
      <el-tag v-if="pool.shopName" size="small" effect="plain" type="warning">{{ pool.shopName }}</el-tag>
    </div>
    <div class="pool-card-info">
      <span>📅 {{ formatDateTime(pool.startTime) }}</span>
      <span>👥 {{ pool.currentMembers }}/{{ pool.maxMembers }}</span>
      <span>💰 ¥{{ formatPrice(pool.price) }}</span>
    </div>
    <div class="pool-card-footer">
      <span class="pool-owner">发布人：{{ pool.ownerNickname }}</span>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import StatusTag from './StatusTag.vue'
import { formatDateTime, formatPrice } from '../utils/format'
import type { PoolListItem } from '../api/player'

defineProps<{ pool: PoolListItem }>()
</script>

<style scoped>
.pool-card { cursor: pointer; margin-bottom: 12px; }
.pool-card:hover { border-color: #409eff; }
.pool-card-header {
  display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px;
}
.pool-name { font-size: 16px; font-weight: 600; }
.pool-card-tags { display: flex; gap: 6px; margin-bottom: 8px; }
.pool-card-info {
  display: flex; gap: 16px; font-size: 13px; color: #606266; margin-bottom: 4px;
}
.pool-card-footer { font-size: 12px; color: #909399; }
</style>
