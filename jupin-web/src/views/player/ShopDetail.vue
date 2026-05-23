<template>
  <div class="shop-detail" v-loading="loading">
    <el-button text @click="$router.back()" style="margin-bottom: 12px">← 返回</el-button>

    <el-card v-if="shop" shadow="never">
      <div class="shop-header">
        <el-image :src="shop.cover || ''" style="width: 100%; height: 160px; border-radius: 8px">
          <template #error>
            <div class="cover-placeholder">🏪 {{ shop.name }}</div>
          </template>
        </el-image>
      </div>
      <div style="margin-top: 16px">
        <h3>{{ shop.name }}</h3>
        <p class="shop-addr">{{ shop.city }} · {{ shop.address }}</p>
        <p v-if="shop.phone">📞 {{ shop.phone }}</p>
        <p v-if="shop.openingHours">🕐 {{ shop.openingHours }}</p>
        <p class="shop-desc">{{ shop.description || '暂无简介' }}</p>
      </div>
    </el-card>

    <el-card shadow="never" style="margin-top: 16px">
      <h4>店铺剧本</h4>
      <el-table v-if="scripts.length > 0" :data="scripts" size="small" border>
        <el-table-column prop="name" label="剧本名" />
        <el-table-column prop="type" label="类型" width="80" />
        <el-table-column label="难度" width="60">
          <template #default="{ row }">
            {{ ['', '简单', '中等', '困难'][row.difficulty] || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="人数" width="80">
          <template #default="{ row }">{{ row.minPlayers }}-{{ row.maxPlayers }}人</template>
        </el-table-column>
      </el-table>
      <el-empty v-else description="暂无剧本" />
    </el-card>

    <el-card shadow="never" style="margin-top: 16px">
      <h4>店家局</h4>
      <div v-for="pool in pools" :key="pool.id" class="pool-row">
        <div class="pool-row-info">
          <span class="pool-row-name">{{ pool.scriptName }}</span>
          <StatusTag :status="pool.status" />
        </div>
        <div class="pool-row-meta">
          {{ formatDateTime(pool.startTime) }} · {{ pool.currentMembers }}/{{ pool.maxMembers }} ·
          ¥{{ formatPrice(pool.price) }}
        </div>
        <el-button size="small" @click="$router.push(`/player/pools/${pool.id}`)">详情</el-button>
      </div>
      <el-empty v-if="pools.length === 0" description="暂无店家局" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getShopDetail, getShopScripts, getShopPools } from '../../api/player'
import type { ShopDetail, ScriptItem, PoolListItem } from '../../api/player'
import StatusTag from '../../components/StatusTag.vue'
import { formatDateTime, formatPrice } from '../../utils/format'

const route = useRoute()
const loading = ref(false)
const shop = ref<ShopDetail | null>(null)
const scripts = ref<ScriptItem[]>([])
const pools = ref<PoolListItem[]>([])

async function loadDetail() {
  loading.value = true
  try {
    const id = Number(route.params.id)
    const [shopRes, scriptRes, poolRes] = await Promise.all([
      getShopDetail(id),
      getShopScripts(id, { page: 1, size: 50 }),
      getShopPools(id, { page: 1, size: 20 }),
    ])
    shop.value = shopRes.data
    scripts.value = scriptRes.data.records
    pools.value = poolRes.data.records
  } finally {
    loading.value = false
  }
}

onMounted(loadDetail)
</script>

<style scoped>
.shop-detail { max-width: 800px; margin: 0 auto; }
.cover-placeholder {
  width: 100%; height: 160px;
  background: linear-gradient(135deg, #667eea, #764ba2);
  display: flex; align-items: center; justify-content: center;
  font-size: 28px; color: #fff; border-radius: 8px;
}
.shop-addr { font-size: 13px; color: #909399; }
.shop-desc { font-size: 14px; color: #606266; margin-top: 8px; }
.pool-row {
  display: flex; align-items: center; justify-content: space-between;
  padding: 8px 0; border-bottom: 1px solid #f0f0f0;
}
.pool-row:last-child { border-bottom: none; }
.pool-row-info { display: flex; align-items: center; gap: 8px; }
.pool-row-name { font-weight: 500; }
.pool-row-meta { font-size: 12px; color: #909399; }
</style>
