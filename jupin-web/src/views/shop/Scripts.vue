<template>
  <div class="shop-scripts" v-loading="loading">
    <div class="page-header">
      <h3>店铺剧本库</h3>
      <el-button type="primary" @click="openAddDialog">添加剧本</el-button>
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
    <el-dialog v-model="showAddDialog" title="添加剧本" width="700px" @open="loadSystemScripts">
      <div class="add-script-filters">
        <el-select v-model="filterType" placeholder="按类型筛选" clearable size="small" style="width: 130px">
          <el-option v-for="t in SCRIPT_TYPES" :key="t" :label="t" :value="t" />
        </el-select>
        <el-input v-model="filterKeyword" placeholder="搜索剧本名称" size="small" clearable style="width: 200px" />
        <span class="filter-count">共 {{ filteredScripts.length }} 个剧本</span>
      </div>
      <el-table :data="filteredScripts" size="small" border highlight-current-row @current-change="onSelectScript">
        <el-table-column prop="name" label="剧本名" />
        <el-table-column prop="type" label="类型" width="70" />
        <el-table-column label="人数" width="60">
          <template #default="{ row }">{{ row.minPlayers }}-{{ row.maxPlayers }}人</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="shopScriptIds.has(row.id)" type="success" size="small">已添加</el-tag>
            <el-tag v-else type="info" size="small">未添加</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" :disabled="!selectedScriptId || shopScriptIds.has(selectedScriptId)" :loading="adding" @click="addScript">
          添加所选剧本
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getShopScripts, getSystemScripts, addShopScript, removeShopScript, getCurrentShop } from '../../api/shop'
import type { ScriptItem } from '../../api/player'
import { SCRIPT_TYPES } from '../../constants'

const loading = ref(false)
const scripts = ref<ScriptItem[]>([])
const shopId = ref<number>(0)

const showAddDialog = ref(false)
const systemScripts = ref<ScriptItem[]>([])
const shopScriptIds = ref<Set<number>>(new Set())
const selectedScriptId = ref<number | undefined>()
const adding = ref(false)

const filterType = ref('')
const filterKeyword = ref('')
const filteredScripts = computed(() => {
  let list = systemScripts.value
  if (filterType.value) {
    list = list.filter(s => s.type === filterType.value)
  }
  if (filterKeyword.value) {
    const kw = filterKeyword.value.toLowerCase()
    list = list.filter(s => s.name.toLowerCase().includes(kw))
  }
  return list
})

async function loadScripts() {
  loading.value = true
  try {
    const shopRes = await getCurrentShop()
    shopId.value = shopRes.data.id
    const res = await getShopScripts(shopRes.data.id, { page: 1, size: 100 })
    scripts.value = res.data
  } finally {
    loading.value = false
  }
}

async function openAddDialog() {
  showAddDialog.value = true
  filterType.value = ''
  filterKeyword.value = ''
  selectedScriptId.value = undefined
}

async function loadSystemScripts() {
  const [sysRes] = await Promise.all([
    getSystemScripts({ page: 1, size: 200 }),
  ])
  systemScripts.value = sysRes.data
  shopScriptIds.value = new Set(scripts.value.map(s => s.id))
}

function onSelectScript(row: ScriptItem | null) {
  selectedScriptId.value = row?.id
}

async function addScript() {
  if (!selectedScriptId.value) return
  adding.value = true
  try {
    await addShopScript(shopId.value, selectedScriptId.value)
    ElMessage.success('添加成功')
    await loadScripts()
    shopScriptIds.value = new Set(scripts.value.map(s => s.id))
    selectedScriptId.value = undefined
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
.add-script-filters {
  display: flex; align-items: center; gap: 12px; margin-bottom: 12px;
}
.filter-count { font-size: 13px; color: #909399; }
</style>
