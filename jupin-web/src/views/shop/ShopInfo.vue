<template>
  <div class="shop-info" v-loading="loading">
    <h3>店铺信息</h3>
    <el-card v-if="shop" shadow="never" style="max-width: 600px">
      <el-form :model="shop" label-width="100px" size="small">
        <el-form-item label="店铺名称">
          <el-input v-model="shop.name" disabled />
        </el-form-item>
        <el-form-item label="城市">
          <el-input v-model="shop.city" />
        </el-form-item>
        <el-form-item label="地址">
          <el-input v-model="shop.address" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="shop.phone" />
        </el-form-item>
        <el-form-item label="营业时间">
          <el-input v-model="shop.openingHours" placeholder="例如：10:00-22:00" />
        </el-form-item>
        <el-form-item label="店铺简介">
          <el-input v-model="shop.description" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="saving" @click="handleUpdate">保存</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getCurrentShop, updateShop } from '../../api/shop'
import type { ShopInfo } from '../../api/shop'

const loading = ref(false)
const saving = ref(false)
const shop = reactive<ShopInfo>({
  id: 0, name: '', city: '', address: '', phone: '',
  logo: null, cover: null, description: null, openingHours: null, role: 0,
})

async function load() {
  loading.value = true
  try {
    const res = await getCurrentShop()
    Object.assign(shop, res.data)
  } finally {
    loading.value = false
  }
}

async function handleUpdate() {
  saving.value = true
  try {
    await updateShop({ ...shop })
    ElMessage.success('保存成功')
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.shop-info h3 { margin-bottom: 16px; }
</style>
