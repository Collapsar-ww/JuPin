<template>
  <div class="shop-pool-create">
    <h3>发布店家局</h3>

    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" style="max-width: 600px">
      <el-form-item label="选择剧本" prop="scriptId">
        <el-select v-model="form.scriptId" placeholder="从店铺剧本库选择" style="width: 100%">
          <el-option v-for="s in shopScripts" :key="s.id"
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

      <el-form-item label="结束时间" prop="endTime">
        <el-date-picker v-model="form.endTime" type="datetime" placeholder="选择结束时间"
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

      <!-- Price breakdown -->
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

      <el-form-item label="指定 DM" prop="dmId">
        <el-select v-model="form.dmId" placeholder="选择店铺成员作为 DM" style="width: 100%">
          <el-option v-for="m in members" :key="m.userId" :label="`${m.nickname}（${m.roleText}）`"
            :value="m.userId" />
        </el-select>
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
import { getCurrentShop, getShopScripts, getShopMembers, createShopPool } from '../../api/shop'
import type { ScriptItem, ShopMember } from '../../api/shop'
import { formatPrice } from '../../utils/format'

const router = useRouter()
const formRef = ref<FormInstance>()
const shopScripts = ref<ScriptItem[]>([])
const members = ref<ShopMember[]>([])
const shopId = ref(0)
const submitting = ref(false)

const form = reactive({
  scriptId: undefined as number | undefined,
  city: '',
  address: '',
  startTime: '',
  endTime: '',
  maxMembers: 4,
  price: 0,
  dmId: undefined as number | undefined,
})

const rules = {
  scriptId: [{ required: true, message: '请选择剧本', trigger: 'change' }],
  city: [{ required: true, message: '请输入城市', trigger: 'blur' }],
  startTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  maxMembers: [{ required: true, message: '请输入人数上限', trigger: 'blur' }],
  price: [{ required: true, message: '请输入人均费用', trigger: 'blur' }],
  dmId: [{ required: true, message: '请指定 DM', trigger: 'change' }],
}

const selectedScript = computed(() => shopScripts.value.find((s) => s.id === form.scriptId))
const selectedMinPlayers = computed(() => selectedScript.value?.minPlayers ?? 2)
const selectedMaxPlayers = computed(() => selectedScript.value?.maxPlayers ?? 10)

onMounted(async () => {
  const shopRes = await getCurrentShop()
  shopId.value = shopRes.data.id
  form.city = shopRes.data.city || ''
  form.address = shopRes.data.address || ''

  const [scriptRes, memberRes] = await Promise.all([
    getShopScripts(shopRes.data.id, { page: 1, size: 100 }),
    getShopMembers(shopRes.data.id),
  ])
  shopScripts.value = scriptRes.data.records
  members.value = memberRes.data
})

async function handleCreate() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    const res = await createShopPool({
      scriptId: form.scriptId!,
      city: form.city,
      address: form.address,
      startTime: form.startTime,
      endTime: form.endTime,
      maxMembers: form.maxMembers,
      price: form.price,
      dmId: form.dmId!,
    })
    ElMessage.success('发布成功')
    router.push(`/shop/pools/${res.data.id}`)
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.shop-pool-create { max-width: 700px; margin: 0 auto; }
.shop-pool-create h3 { font-size: 18px; margin-bottom: 20px; }
.form-tip { font-size: 12px; color: #909399; margin-left: 8px; }
.price-detail {
  display: flex; justify-content: space-between; padding: 4px 0; font-size: 14px;
}
</style>
