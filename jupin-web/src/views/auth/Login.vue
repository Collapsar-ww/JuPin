<template>
  <div class="auth-page">
    <div class="auth-card">
      <h2 class="auth-title">JuPin 聚拼</h2>
      <p class="auth-subtitle">玩家组局、店家开局，一站式剧本杀拼车</p>
      <el-tabs v-model="activeTab" class="auth-tabs">
        <el-tab-pane label="密码登录" name="password">
          <el-form ref="formRef" :model="form" :rules="rules" label-width="0">
            <el-form-item prop="phone">
              <el-input v-model="form.phone" placeholder="手机号" size="large" />
            </el-form-item>
            <el-form-item prop="password">
              <el-input v-model="form.password" type="password" placeholder="密码" size="large" show-password />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" size="large" style="width: 100%" :loading="loading" @click="handleLogin">
                登录
              </el-button>
            </el-form-item>
          </el-form>
          <div class="auth-footer">
            还没有账号？
            <router-link to="/register">立即注册</router-link>
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { login } from '../../api/auth'
import { useAuthStore } from '../../stores/auth'

const router = useRouter()
const auth = useAuthStore()

const activeTab = ref('password')
const loading = ref(false)
const formRef = ref<FormInstance>()

const form = reactive({
  phone: '',
  password: '',
})

const rules = {
  phone: [{ required: true, message: '请输入手机号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function handleLogin() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    const res = await login(form)
    auth.setLogin(res.data)
    ElMessage.success('登录成功')
    const role = res.data.user.role
    if (role === 0) router.push('/player/pools')
    else if (role === 1) router.push('/shop/dashboard')
    else if (role === 2) router.push('/admin/users')
  } catch {
    // error handled by interceptor
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%);
}
.auth-card {
  width: 400px;
  padding: 40px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.2);
}
.auth-title {
  text-align: center;
  font-size: 28px;
  font-weight: 700;
  color: #409eff;
  margin-bottom: 4px;
}
.auth-subtitle {
  text-align: center;
  font-size: 13px;
  color: #909399;
  margin-bottom: 24px;
}
.auth-tabs { margin-bottom: 16px; }
.auth-footer { text-align: center; font-size: 14px; color: #909399; }
.auth-footer a { color: #409eff; text-decoration: none; }
</style>
