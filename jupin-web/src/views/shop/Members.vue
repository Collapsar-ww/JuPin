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
        <el-form-item label="手机号">
          <el-input v-model="addForm.phone" placeholder="输入用户手机号搜索" maxlength="11" />
        </el-form-item>
        <el-form-item v-if="addForm.foundUser" label="搜索结果">
          <span>{{ addForm.foundUser.nickname }}（ID: {{ addForm.foundUser.id }}）</span>
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
import { getUserList } from '../../api/admin'
const loading = ref(false)
const members = ref<ShopMember[]>([])
const shopId = ref(0)
const currentRole = ref(0)

const isOwner = computed(() => currentRole.value === 1)
const isAdmin = computed(() => currentRole.value === 2 || currentRole.value === 1)

const showAddDialog = ref(false)
const adding = ref(false)
const addForm = reactive({
  phone: '',
  foundUser: null as { id: number; nickname: string } | null,
})

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
  if (!addForm.foundUser) {
    // Search by phone first
    adding.value = true
    try {
      const res = await getUserList({ phone: addForm.phone, page: 1, size: 1 })
      if (res.data.length === 0) {
        ElMessage.error('未找到该手机号的用户')
        return
      }
      const user = res.data[0]
      await addShopMember(shopId.value, { userId: user.id })
      ElMessage.success('添加成功')
      showAddDialog.value = false
      addForm.phone = ''
      addForm.foundUser = null
      loadMembers()
    } catch {
      ElMessage.error('搜索用户失败，请检查手机号或权限')
    } finally {
      adding.value = false
    }
    return
  }
  // Use already-found user
  adding.value = true
  try {
    await addShopMember(shopId.value, { userId: addForm.foundUser.id })
    ElMessage.success('添加成功')
    showAddDialog.value = false
    addForm.phone = ''
    addForm.foundUser = null
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
