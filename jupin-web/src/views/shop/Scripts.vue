<template>
  <div class="shop-scripts" v-loading="loading">
    <div class="page-header">
      <h3>店铺剧本库</h3>
      <el-button type="primary" @click="showAddDialog = true">添加剧本</el-button>
    </div>

    <el-table :data="scripts" size="small" border>
      <el-table-column prop="name" label="剧本名" />
      <el-table-column prop="type" label="类型" width="80" />
      <el-table-column label="难度" width="60">
        <template #default="{ row }">{{ ['', '简单', '中等', '困难'][row.difficulty] || '-' }}</template>
      </el-table-column>
      <el-table-column label="人数" width="80">
        <template #default="{ row }">{{ row.minPlayers }}-{{ row.maxPlayers }}人</template>
      </el-table-column>
      <el-table-column prop="duration" label="时长(分钟)" width="100" />
      <el-table-column label="操作" width="80">
        <template #default="{ row }">
          <el-button type="danger" size="small" @click="handleRemove(row.id)">移除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="scripts.length === 0 && !loading" description="剧本库为空，请添加剧本" />

    <!-- Add Script Dialog -->
    <el-dialog v-model="showAddDialog" title="添加剧本" width="500px">
      <el-select v-model="selectedScriptId" placeholder="搜索系统剧本" filterable remote
        :remote-method="searchScripts" :loading="searchLoading" style="width: 100%">
        <el-option v-for="s in systemScripts" :key="s.id" :label="`${s.name}（${s.type} / ${s.minPlayers}-${s.maxPlayers}人）`"
          :value="s.id" />
      </el-select>
      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" :loading="adding" @click="addScript">确定添加</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getShopScripts, getSystemScripts, addShopScript, removeShopScript, getCurrentShop } from '../../api/shop'
import type { ScriptItem } from '../../api/player'

const loading = ref(false)
const scripts = ref<ScriptItem[]>([])
const shopId = ref<number>(0)

const showAddDialog = ref(false)
const systemScripts = ref<ScriptItem[]>([])
const selectedScriptId = ref<number | undefined>()
const searchLoading = ref(false)
const adding = ref(false)

async function loadScripts() {
  loading.value = true
  try {
    const shopRes = await getCurrentShop()
    shopId.value = shopRes.data.id
    const res = await getShopScripts(shopRes.data.id, { page: 1, size: 100 })
    scripts.value = res.data.records
  } finally {
    loading.value = false
  }
}

async function searchScripts(query: string) {
  if (!query) return
  searchLoading.value = true
  try {
    const res = await getSystemScripts({ name: query, page: 1, size: 20 })
    systemScripts.value = res.data.records
  } finally {
    searchLoading.value = false
  }
}

async function addScript() {
  if (!selectedScriptId.value) return
  adding.value = true
  try {
    await addShopScript(shopId.value, selectedScriptId.value)
    ElMessage.success('添加成功')
    showAddDialog.value = false
    selectedScriptId.value = undefined
    loadScripts()
  } finally {
    adding.value = false
  }
}

async function handleRemove(scriptId: number) {
  try {
    await ElMessageBox.confirm('确定移除该剧本吗？')
    await removeShopScript(shopId.value, scriptId)
    ElMessage.success('已移除')
    loadScripts()
  } catch {
    // cancelled
  }
}

onMounted(loadScripts)
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-header h3 { margin: 0; }
</style>
