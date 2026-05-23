<template>
  <div class="pool-create">
    <h3>发布玩家局</h3>

    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" style="max-width: 600px">
      <el-form-item label="选择剧本" prop="scriptId">
        <el-select v-model="form.scriptId" placeholder="搜索并选择剧本" filterable remote
          :remote-method="searchScripts" :loading="scriptLoading" style="width: 100%">
          <el-option v-for="s in scripts" :key="s.id"
            :label="`${s.name}（${s.type} / ${s.minPlayers}-${s.maxPlayers}人）`"
            :value="s.id" />
        </el-select>
      </el-form-item>

      <el-form-item label="城市" prop="city">
        <el-input v-model="form.city" placeholder="例如：上海" />
      </el-form-item>

      <el-form-item label="详细地址" prop="address">
        <el-input v-model="form.address" placeholder="线下集合地址" />
      </el-form-item>

      <el-form-item label="开始时间" prop="startTime">
        <el-date-picker v-model="form.startTime" type="datetime" placeholder="选择开始时间"
          style="width: 100%" value-format="YYYY-MM-DD HH:mm:ss" />
      </el-form-item>

      <el-form-item label="人数上限" prop="maxMembers">
        <el-input-number v-model="form.maxMembers" :min="selectedMinPlayers" :max="selectedMaxPlayers" />
        <span class="form-tip">可选 {{ selectedMinPlayers }}-{{ selectedMaxPlayers }} 人</span>
      </el-form-item>

      <el-form-item label="人均费用" prop="price">
        <el-input-number v-model="form.price" :min="0" :precision="2" :step="10" />
        <span class="form-tip">元/人</span>
      </el-form-item>

      <el-card v-if="form.price > 0" shadow="never" style="margin-bottom: 16px; background: #f5f7fa">
        <div class="price-detail">
          <span>人均总费用：</span><strong>¥{{ formatPrice(form.price) }}</strong>
        </div>
        <div class="price-detail">
          <span>整场预计总价：</span><strong>¥{{ formatPrice(form.price * form.maxMembers) }}</strong>
        </div>
        <div class="price-detail">
          <span>押金（20%）：</span><strong>¥{{ formatPrice(form.price * 0.2) }}</strong>
        </div>
        <div class="price-detail">
          <span>尾款车费：</span><strong>¥{{ formatPrice(form.price * 0.8) }}</strong>
        </div>
      </el-card>

      <el-form-item label="加入方式" prop="joinType">
        <el-radio-group v-model="form.joinType">
          <el-radio :value="1">自动通过（默认）</el-radio>
          <el-radio :value="0">审核制</el-radio>
        </el-radio-group>
      </el-form-item>

      <el-form-item>
        <el-button type="primary" :loading="submitting" @click="handleCreate">发布</el-button>
        <el-button @click="$router.back()">取消</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { getScriptList, createPlayerPool } from '../../api/player'
import type { ScriptItem } from '../../api/player'
import { formatPrice } from '../../utils/format'

const router = useRouter()
const formRef = ref<FormInstance>()
const scripts = ref<ScriptItem[]>([])
const scriptLoading = ref(false)
const submitting = ref(false)

const form = reactive({
  scriptId: undefined as number | undefined,
  city: '',
  address: '',
  startTime: '',
  maxMembers: 4,
  price: 0,
  joinType: 1,
})

const rules = {
  scriptId: [{ required: true, message: '请选择剧本', trigger: 'change' }],
  city: [{ required: true, message: '请输入城市', trigger: 'blur' }],
  startTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  maxMembers: [{ required: true, message: '请输入人数上限', trigger: 'blur' }],
  price: [{ required: true, message: '请输入人均费用', trigger: 'blur' }],
}

const selectedScript = computed(() => scripts.value.find((s) => s.id === form.scriptId))
const selectedMinPlayers = computed(() => selectedScript.value?.minPlayers ?? 2)
const selectedMaxPlayers = computed(() => selectedScript.value?.maxPlayers ?? 10)

onMounted(async () => {
  const res = await getScriptList({ page: 1, size: 50 })
  scripts.value = res.data.records
})

async function searchScripts(query: string) {
  if (!query) return
  scriptLoading.value = true
  try {
    const res = await getScriptList({ name: query, page: 1, size: 20 })
    scripts.value = res.data.records
  } finally {
    scriptLoading.value = false
  }
}

async function handleCreate() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    const res = await createPlayerPool({
      scriptId: form.scriptId!,
      city: form.city,
      address: form.address,
      startTime: form.startTime,
      maxMembers: form.maxMembers,
      price: form.price,
      joinType: form.joinType,
    })
    ElMessage.success('发布成功')
    router.push(`/player/pools/${res.data.id}`)
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.pool-create { max-width: 700px; margin: 0 auto; }
.pool-create h3 { font-size: 18px; margin-bottom: 20px; }
.form-tip { font-size: 12px; color: #909399; margin-left: 8px; }
.price-detail { display: flex; justify-content: space-between; padding: 4px 0; font-size: 14px; }
</style>
