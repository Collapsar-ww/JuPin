<template>
  <div class="shop-my-page" v-loading="loading">
    <h3>个人中心</h3>

    <el-card shadow="never" class="user-card">
      <div class="user-info">
        <el-avatar :size="64">{{ user?.nickname?.charAt(0) }}</el-avatar>
        <div class="user-detail">
          <div class="user-name">{{ user?.nickname }}</div>
          <div class="user-meta">{{ maskedPhone }}</div>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" style="margin-top: 16px">
      <template #header><span>账号信息</span></template>
      <el-descriptions :column="1" border>
        <el-descriptions-item label="昵称">{{ user?.nickname }}</el-descriptions-item>
        <el-descriptions-item label="手机号">{{ maskedPhone }}</el-descriptions-item>
        <el-descriptions-item label="性别">{{ genderText }}</el-descriptions-item>
        <el-descriptions-item label="注册时间">{{ formatDateTime(user?.createTime) }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card shadow="never" style="margin-top: 16px">
      <template #header><span>店铺身份</span></template>
      <el-descriptions :column="1" border v-if="shop">
        <el-descriptions-item label="店铺名称">{{ shop.name }}</el-descriptions-item>
        <el-descriptions-item label="店铺角色">{{ roleText }}</el-descriptions-item>
        <el-descriptions-item label="所在城市">{{ shop.city || '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-empty v-else description="暂未关联店铺" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useAuthStore } from '../../stores/auth'
import { getCurrentShop } from '../../api/shop'
import type { ShopInfo } from '../../api/shop'
import { formatDateTime } from '../../utils/format'

const auth = useAuthStore()

const loading = ref(false)
const shop = ref<ShopInfo | null>(null)

const user = computed(() => auth.user)

const maskedPhone = computed(() => {
  const p = user.value?.phone
  if (!p || p.length < 11) return p || '-'
  return p.substring(0, 3) + '****' + p.substring(7)
})

const genderText = computed(() => {
  const g = user.value?.gender
  if (g === 1) return '男'
  if (g === 2) return '女'
  return '未知'
})

const roleText = computed(() => {
  const r = shop.value?.role
  if (r === 1) return '店长'
  if (r === 2) return '管理员'
  if (r === 3) return '普通成员'
  return '未知'
})

async function loadShop() {
  loading.value = true
  try {
    const res = await getCurrentShop()
    shop.value = res.data
  } catch {
    // not bound to a shop
  } finally {
    loading.value = false
  }
}

onMounted(loadShop)
</script>

<style scoped>
.shop-my-page { max-width: 700px; margin: 0 auto; }
.shop-my-page h3 { font-size: 18px; margin-bottom: 16px; }
.user-card { margin-bottom: 8px; }
.user-info { display: flex; align-items: center; gap: 16px; }
.user-name { font-size: 20px; font-weight: 600; }
.user-meta { font-size: 14px; color: #909399; margin-top: 4px; }
</style>
