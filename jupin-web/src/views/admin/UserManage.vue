<template>
  <div class="admin-users" v-loading="loading">
    <div class="page-header">
      <h3>用户管理</h3>
    </div>

    <el-table :data="users" size="small" border>
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="phone" label="手机号" width="130" />
      <el-table-column prop="nickname" label="昵称" width="120" />
      <el-table-column label="角色" width="80">
        <template #default="{ row }">
          <el-tag v-if="row.role === 0" size="small">玩家</el-tag>
          <el-tag v-else-if="row.role === 1" size="small" type="warning">店家</el-tag>
          <el-tag v-else size="small" type="danger">管理员</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
            {{ row.status === 1 ? '正常' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="creditScore" label="信用分" width="80" />
      <el-table-column label="注册时间" width="160">
        <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="100">
        <template #default="{ row }">
          <el-button
            :type="row.status === 1 ? 'danger' : 'success'"
            size="small"
            @click="toggleStatus(row)"
          >
            {{ row.status === 1 ? '禁用' : '启用' }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-if="total > 0"
      v-model:current-page="page"
      :page-size="size"
      :total="total"
      layout="prev, pager, next"
      style="margin-top: 16px; justify-content: center"
      @current-change="loadUsers"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getUserList, setUserStatus } from '../../api/admin'
import type { AdminUser } from '../../api/admin'
import { formatDateTime } from '../../utils/format'

const loading = ref(false)
const users = ref<AdminUser[]>([])
const page = ref(1)
const size = ref(20)
const total = ref(0)

async function loadUsers(p?: number) {
  if (p) page.value = p
  loading.value = true
  try {
    const res = await getUserList({ page: page.value, size: size.value })
    users.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

async function toggleStatus(user: AdminUser) {
  const newStatus = user.status === 1 ? 0 : 1
  const action = newStatus === 1 ? '启用' : '禁用'
  try {
    await ElMessageBox.confirm(`确定${action}用户 ${user.nickname} 吗？`)
    await setUserStatus(user.id, newStatus)
    ElMessage.success(`${action}成功`)
    loadUsers()
  } catch { /* cancelled */ }
}

onMounted(() => loadUsers())
</script>

<style scoped>
.page-header { margin-bottom: 16px; }
.page-header h3 { margin: 0; }
</style>
