<template>
  <el-container class="layout-container">
    <el-header class="layout-header">
      <div class="header-left">
        <span class="brand">JuPin 聚拼</span>
        <span class="role-tag">玩家端</span>
      </div>
      <div class="header-right">
        <el-button text @click="goMy">我的</el-button>
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
          <el-menu-item index="/player/pools">
            <el-icon><Tickets /></el-icon>
            <span>玩家局列表</span>
          </el-menu-item>
          <el-menu-item index="/player/shops">
            <el-icon><Shop /></el-icon>
            <span>店铺列表</span>
          </el-menu-item>
          <el-menu-item index="/player/my">
            <el-icon><User /></el-icon>
            <span>我的</span>
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
import { Tickets, Shop, User } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const defaultActive = computed(() => route.path)

function goMy() {
  router.push('/player/my')
}

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
.role-tag {
  font-size: 12px; background: #ecf5ff; color: #409eff;
  padding: 2px 8px; border-radius: 4px;
}
.layout-body { height: calc(100vh - 60px); }
.layout-aside { background: #fff; border-right: 1px solid #e4e7ed; }
.layout-main { background: #f5f7fa; padding: 20px; overflow-y: auto; }
</style>
