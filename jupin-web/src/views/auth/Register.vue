<template>
  <div class="auth-page">
    <div class="auth-card">
      <h2 class="auth-title">注册 JuPin 聚拼</h2>
      <p class="auth-subtitle">选择身份后注册，注册后不可切换</p>
      <el-steps :active="step" align-center class="register-steps">
        <el-step title="选择身份" />
        <el-step title="填写信息" />
      </el-steps>

      <!-- Step 1: Role Selection -->
      <div v-if="step === 1" class="role-select">
        <div
          class="role-card"
          :class="{ active: form.role === 'player' }"
          @click="form.role = 'player'"
        >
          <h3>我是玩家</h3>
          <p>参与或发起拼车，与好友一起玩剧本杀</p>
        </div>
        <div
          class="role-card"
          :class="{ active: form.role === 'shop' }"
          @click="form.role = 'shop'"
        >
          <h3>我是店家</h3>
          <p>经营店铺，发布店家局，管理剧本和成员</p>
        </div>
        <el-button
          type="primary"
          size="large"
          style="width: 100%; margin-top: 16px"
          :disabled="!form.role"
          @click="step = 2"
        >
          下一步
        </el-button>
      </div>

      <!-- Step 2: Form -->
      <div v-else>
        <el-form ref="formRef" :model="form" :rules="rules" label-width="0" style="margin-top: 16px">
          <el-form-item prop="phone">
            <el-input v-model="form.phone" placeholder="手机号" size="large" />
          </el-form-item>
          <el-form-item prop="nickname">
            <el-input v-model="form.nickname" placeholder="昵称（2-20位）" size="large" />
          </el-form-item>
          <el-form-item prop="password">
            <el-input v-model="form.password" type="password" placeholder="密码（8-32位，字母+数字）" size="large" show-password />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" size="large" style="width: 100%" :loading="loading" @click="handleRegister">
              注册
            </el-button>
          </el-form-item>
        </el-form>
        <div class="auth-footer">
          已有账号？<router-link to="/login">去登录</router-link>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { register } from '../../api/auth'

const router = useRouter()
const step = ref(1)
const loading = ref(false)
const formRef = ref<FormInstance>()

const form = reactive({
  phone: '',
  nickname: '',
  password: '',
  role: '',
})

const rules = {
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' },
  ],
  nickname: [
    { required: true, message: '请输入昵称', trigger: 'blur' },
    { min: 2, max: 20, message: '昵称长度 2-20 位', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 8, max: 32, message: '密码长度 8-32 位', trigger: 'blur' },
  ],
}

async function handleRegister() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await register(form)
    ElMessage.success('注册成功，请登录')
    router.push('/login')
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
  width: 420px;
  padding: 40px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.2);
}
.auth-title { text-align: center; font-size: 24px; font-weight: 700; color: #409eff; margin-bottom: 4px; }
.auth-subtitle { text-align: center; font-size: 13px; color: #909399; margin-bottom: 24px; }
.register-steps { margin-bottom: 24px; }
.role-select { display: flex; flex-direction: column; gap: 12px; }
.role-card {
  border: 2px solid #e4e7ed;
  border-radius: 8px;
  padding: 20px;
  cursor: pointer;
  transition: all 0.2s;
}
.role-card:hover { border-color: #409eff; }
.role-card.active { border-color: #409eff; background: #ecf5ff; }
.role-card h3 { margin: 0 0 4px; font-size: 16px; }
.role-card p { margin: 0; font-size: 13px; color: #909399; }
.auth-footer { text-align: center; font-size: 14px; color: #909399; margin-top: 12px; }
.auth-footer a { color: #409eff; text-decoration: none; }
</style>
