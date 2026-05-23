<template>
  <div class="shop-list">
    <h3>店铺列表</h3>

    <el-card class="filter-card" shadow="never">
      <el-form :inline="true" size="small">
        <el-form-item label="城市">
          <el-input v-model="city" placeholder="输入城市" style="width: 140px" clearable />
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="keyword" placeholder="店铺名称" style="width: 140px" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadShops(1)">搜索</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <div v-loading="loading">
      <el-row :gutter="16">
        <el-col v-for="shop in shops" :key="shop.id" :span="12" style="margin-bottom: 16px">
          <el-card shadow="hover" class="shop-card" @click="$router.push(`/player/shops/${shop.id}`)">
            <div class="shop-card-header">
              <el-image :src="shop.logo || ''" style="width: 48px; height: 48px; border-radius: 8px">
                <template #error>
                  <div class="img-placeholder">🏪</div>
                </template>
              </el-image>
              <div class="shop-card-info">
                <div class="shop-name">{{ shop.name }}</div>
                <div class="shop-meta">{{ shop.city }} · {{ shop.address }}</div>
              </div>
            </div>
            <div class="shop-desc">{{ shop.description || '暂无简介' }}</div>
            <div class="shop-footer">
              <span v-if="shop.openingHours">营业时间：{{ shop.openingHours }}</span>
              <span class="shop-rating">{{ shop.ratingText }}</span>
            </div>
          </el-card>
        </el-col>
      </el-row>
      <el-empty v-if="!loading && shops.length === 0" description="暂无店铺" />
      <el-pagination
        v-if="total > 0"
        v-model:current-page="page"
        :page-size="size"
        :total="total"
        layout="prev, pager, next"
        style="margin-top: 16px; justify-content: center"
        @current-change="loadShops"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getShopList } from '../../api/player'
import type { ShopListItem } from '../../api/player'

const shops = ref<ShopListItem[]>([])
const loading = ref(false)
const page = ref(1)
const size = ref(20)
const total = ref(0)
const city = ref('')
const keyword = ref('')

async function loadShops(p?: number) {
  if (p) page.value = p
  loading.value = true
  try {
    const res = await getShopList({
      city: city.value || undefined,
      keyword: keyword.value || undefined,
      page: page.value,
      size: size.value,
    })
    shops.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

onMounted(() => loadShops())
</script>

<style scoped>
.shop-list { max-width: 900px; margin: 0 auto; }
.shop-list h3 { font-size: 18px; margin-bottom: 16px; }
.filter-card { margin-bottom: 16px; }
.shop-card { cursor: pointer; }
.shop-card:hover { border-color: #409eff; }
.shop-card-header { display: flex; gap: 12px; margin-bottom: 8px; }
.img-placeholder {
  width: 48px; height: 48px; background: #f5f7fa;
  display: flex; align-items: center; justify-content: center; font-size: 24px; border-radius: 8px;
}
.shop-name { font-size: 15px; font-weight: 600; }
.shop-meta { font-size: 12px; color: #909399; }
.shop-desc { font-size: 13px; color: #606266; margin-bottom: 8px; }
.shop-footer { display: flex; justify-content: space-between; font-size: 12px; color: #909399; }
</style>
