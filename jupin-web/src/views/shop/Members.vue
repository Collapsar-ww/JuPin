<template>
  <div class="shop-members" v-loading="loading">
    <div class="page-header">
      <h3>店铺成员</h3>
      <el-button v-if="isOwner" type="primary" @click="showAddDialog = true">添加成员</el-button>
    </div>

    <el-table :data="members" size="small" border>
      <el-table-column prop="nickname" label="昵称" />
      <el-table-column label="角色" width="100">
        <template #default="{ row }">
          <el-tag v-if="row.role === 1" type="danger" size="small">店长</el-tag>
          <el-tag v-else-if="row.role === 2" type="warning" size="small">管理员</el-tag>
          <el-tag v-else size="small" type="info">普通成员</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180">
        <template #default="{ row }">
          <template v-if="isOwner && row.role !== 1">
            <el-button v-if="row.role === 3" size="small" @click="setRole(row, 2)">设为管理员</el-button>
            <el-button v-if="row.role === 2" size="small" @click="setRole(row, 3)">取消管理员</el-button>
            <el-button type="danger" size="small" @click="removeMember(row)">移除</el-button>
          </template>
          <template v-else-if="isAdmin && row.role === 3">
            <el-button type="danger" size="small" @click="removeMember(row)">移除</el-button>
          </template>
          <span v-else>-</span>
        </template>
      </el-table-column>
    </el-table>

    <!-- Add Member Dialog -->
    <el-dialog v-model="showAddDialog" title="添加成员" width="400px">
      <el-form :model="addForm" label-width="80px">
        <el-form-item label="用户ID">
          <el-input-number v-model="addForm.userId" :min="1" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" :loading="adding" @click="addMember">确定添加</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getCurrentShop, getShopMembers, addShopMember, removeShopMember, setMemberRole } from '../../api/shop'
import type { ShopMember } from '../../api/shop'
import { useAuthStore } from '../../stores/auth'

const auth = useAuthStore()
const loading = ref(false)
const members = ref<ShopMember[]>([])
const shopId = ref(0)
const currentRole = ref(0)

const isOwner = computed(() => currentRole.value === 1)
const isAdmin = computed(() => currentRole.value === 2 || currentRole.value === 1)

const showAddDialog = ref(false)
const adding = ref(false)
const addForm = reactive({ userId: 1 })

async function loadMembers() {
  loading.value = true
  try {
    const shopRes = await getCurrentShop()
    shopId.value = shopRes.data.id
    currentRole.value = shopRes.data.role
    const res = await getShopMembers(shopRes.data.id)
    members.value = res.data
  } finally {
    loading.value = false
  }
}

async function addMember() {
  adding.value = true
  try {
    await addShopMember(shopId.value, { userId: addForm.userId })
    ElMessage.success('添加成功')
    showAddDialog.value = false
    loadMembers()
  } finally {
    adding.value = false
  }
}

async function setRole(member: ShopMember, role: number) {
  try {
    await setMemberRole(shopId.value, { userId: member.userId, role })
    ElMessage.success('权限已更新')
    loadMembers()
  } catch { /* handled */ }
}

async function removeMember(member: ShopMember) {
  try {
    await ElMessageBox.confirm(`确定移除成员 ${member.nickname} 吗？`)
    await removeShopMember(shopId.value, { userId: member.userId })
    ElMessage.success('已移除')
    loadMembers()
  } catch { /* cancelled */ }
}

onMounted(loadMembers)
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-header h3 { margin: 0; }
</style>
