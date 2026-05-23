<template>
  <div class="admin-scripts" v-loading="loading">
    <div class="page-header">
      <h3>剧本管理</h3>
      <el-button type="primary" @click="showDialog = true; isEdit = false">添加剧本</el-button>
    </div>

    <el-table :data="scripts" size="small" border>
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="name" label="剧本名" width="140" />
      <el-table-column prop="type" label="类型" width="80" />
      <el-table-column label="难度" width="60">
        <template #default="{ row }">{{ ['', '简单', '中等', '困难'][row.difficulty] || '-' }}</template>
      </el-table-column>
      <el-table-column label="人数" width="80">
        <template #default="{ row }">{{ row.minPlayers }}-{{ row.maxPlayers }}人</template>
      </el-table-column>
      <el-table-column prop="duration" label="时长(分钟)" width="90" />
      <el-table-column prop="priceRef" label="参考价" width="80">
        <template #default="{ row }">¥{{ formatPrice(row.priceRef) }}</template>
      </el-table-column>
      <el-table-column prop="description" label="简介" min-width="160" show-overflow-tooltip />
      <el-table-column label="操作" width="100">
        <template #default="{ row }">
          <el-button size="small" @click="editScript(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="handleDelete(row)">下架</el-button>
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
      @current-change="loadScripts"
    />

    <!-- Edit/Create Dialog -->
    <el-dialog v-model="showDialog" :title="isEdit ? '编辑剧本' : '添加剧本'" width="600px">
      <el-form :model="form" label-width="100px" size="small">
        <el-form-item label="剧本名">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.type">
            <el-option v-for="t in SCRIPT_TYPES" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="难度">
          <el-radio-group v-model="form.difficulty">
            <el-radio :value="1">简单</el-radio>
            <el-radio :value="2">中等</el-radio>
            <el-radio :value="3">困难</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="人数范围">
          <el-input-number v-model="form.minPlayers" :min="1" :max="form.maxPlayers" /> ~
          <el-input-number v-model="form.maxPlayers" :min="form.minPlayers" :max="20" />
        </el-form-item>
        <el-form-item label="参考时长(分钟)">
          <el-input-number v-model="form.duration" :min="0" :step="30" />
        </el-form-item>
        <el-form-item label="参考价格">
          <el-input-number v-model="form.priceRef" :min="0" :precision="2" />
        </el-form-item>
        <el-form-item label="简介">
          <el-input v-model="form.description" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveScript">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAdminScriptList, createScript, updateScript, deleteScript } from '../../api/admin'
import type { ScriptItem } from '../../api/player'
import { SCRIPT_TYPES } from '../../constants'
import { formatPrice } from '../../utils/format'

const loading = ref(false)
const scripts = ref<ScriptItem[]>([])
const page = ref(1)
const size = ref(20)
const total = ref(0)

const showDialog = ref(false)
const isEdit = ref(false)
const editId = ref<number | null>(null)
const saving = ref(false)
const form = reactive({
  name: '',
  type: '',
  difficulty: 1,
  minPlayers: 4,
  maxPlayers: 6,
  duration: 240,
  priceRef: 0,
  description: '',
})

async function loadScripts(p?: number) {
  if (p) page.value = p
  loading.value = true
  try {
    const res = await getAdminScriptList({ page: page.value, size: size.value })
    scripts.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

function editScript(script: ScriptItem) {
  isEdit.value = true
  editId.value = script.id
  Object.assign(form, {
    name: script.name,
    type: script.type,
    difficulty: script.difficulty,
    minPlayers: script.minPlayers,
    maxPlayers: script.maxPlayers,
    duration: script.duration ?? 240,
    priceRef: script.priceRef ?? 0,
    description: script.description ?? '',
  })
  showDialog.value = true
}

async function saveScript() {
  saving.value = true
  try {
    if (isEdit.value && editId.value) {
      await updateScript(editId.value, { ...form })
      ElMessage.success('更新成功')
    } else {
      await createScript({ ...form })
      ElMessage.success('添加成功')
    }
    showDialog.value = false
    loadScripts()
  } finally {
    saving.value = false
  }
}

async function handleDelete(script: ScriptItem) {
  try {
    await ElMessageBox.confirm(`确定下架剧本「${script.name}」吗？`)
    await deleteScript(script.id)
    ElMessage.success('已下架')
    loadScripts()
  } catch { /* cancelled */ }
}

onMounted(() => loadScripts())
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-header h3 { margin: 0; }
</style>
