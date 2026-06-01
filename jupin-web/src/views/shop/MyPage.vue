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
        <el-button size="small" text type="primary" @click="openProfileEdit">
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
import { ElMessage } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { useAuthStore } from '../../stores/auth'
import { getCurrentShop, updateShopUserProfile } from '../../api/shop'
import type { ShopInfo } from '../../api/shop'
import { formatDateTime } from '../../utils/format'

const auth = useAuthStore()

const loading = ref(false)
const shop = ref<ShopInfo | null>(null)

const user = computed(() => auth.user)

// Profile editing
const showProfileEdit = ref(false)
const profileSaving = ref(false)
const profileFormRef = ref<FormInstance>()
const profileForm = ref({ nickname: '', gender: 0 })
const profileRules = {
  nickname: [{ required: true, message: '请输入昵称', trigger: 'blur' }, { min: 2, max: 20, message: '昵称长度 2-20 位', trigger: 'blur' }],
}

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

function openProfileEdit() {
  profileForm.value = {
    nickname: user.value?.nickname || '',
    gender: user.value?.gender ?? 0,
  }
  showProfileEdit.value = true
}

async function handleSaveProfile() {
  const valid = await profileFormRef.value?.validate().catch(() => false)
  if (!valid) return
  profileSaving.value = true
  try {
    await updateShopUserProfile({
      nickname: profileForm.value.nickname,
      gender: profileForm.value.gender,
    })
    ElMessage.success('保存成功')
    showProfileEdit.value = false
    if (auth.user) {
      auth.user.nickname = profileForm.value.nickname
      auth.user.gender = profileForm.value.gender
    }
  } catch {
    // handled
  } finally {
    profileSaving.value = false
  }
}

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
