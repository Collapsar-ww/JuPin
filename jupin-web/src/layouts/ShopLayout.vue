<template>
  <el-container class="layout-container">
    <el-header class="layout-header">
      <div class="header-left">
        <span class="brand">JuPin 聚拼</span>
        <span class="role-tag">店家端</span>
      </div>
      <div class="header-right">
        <el-button text @click="handleLogout">退出</el-button>
      </div>
    </el-header>
    <el-container class="layout-body">
      <el-aside width="180px" class="layout-aside">
        <el-menu
          :default-active="defaultActive"
          router
          style="border-right: none"
        >
          <el-menu-item index="/shop/dashboard">
            <el-icon><DataBoard /></el-icon>
            <span>工作台</span>
          </el-menu-item>
          <el-menu-item index="/shop/scripts">
            <el-icon><Reading /></el-icon>
            <span>店铺剧本</span>
          </el-menu-item>
          <el-menu-item index="/shop/pools/create">
            <el-icon><Plus /></el-icon>
            <span>发布店家局</span>
          </el-menu-item>
          <el-menu-item index="/shop/members">
            <el-icon><UserFilled /></el-icon>
            <span>成员管理</span>
          </el-menu-item>
          <el-menu-item index="/shop/info">
            <el-icon><InfoFilled /></el-icon>
            <span>店铺信息</span>
          </el-menu-item>
        </el-menu>
      </el-aside>
      <el-main class="layout-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { DataBoard, Reading, Plus, UserFilled, InfoFilled } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const defaultActive = computed(() => route.path)

function handleLogout() {
  auth.logout()
  router.push('/login')
}
</script>

<style scoped>
.layout-container { height: 100vh; }
.layout-header {
  display: flex; align-items: center; justify-content: space-between;
  background: #fff; border-bottom: 1px solid #e4e7ed; padding: 0 20px;
}
.header-left { display: flex; align-items: center; gap: 12px; }
.brand { font-size: 18px; font-weight: 700; color: #409eff; }
.role-tag { font-size: 12px; background: #fdf6ec; color: #e6a23c; padding: 2px 8px; border-radius: 4px; }
.layout-body { height: calc(100vh - 60px); }
.layout-aside { background: #fff; border-right: 1px solid #e4e7ed; }
.layout-main { background: #f5f7fa; padding: 20px; overflow-y: auto; }
</style>
