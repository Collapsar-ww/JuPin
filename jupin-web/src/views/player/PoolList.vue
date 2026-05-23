<template>
  <div class="player-pools">
    <div class="page-header">
      <h3>拼车列表</h3>
      <el-button type="primary" @click="$router.push('/player/pools/create')">
        发布拼车
      </el-button>
    </div>

    <el-card class="filter-card" shadow="never">
      <el-form :inline="true" :model="filters" size="small">
        <el-form-item label="城市">
          <el-input v-model="filters.city" placeholder="输入城市" style="width: 140px" clearable />
        </el-form-item>
        <el-form-item label="剧本类型">
          <el-select v-model="filters.scriptType" placeholder="全部" clearable style="width: 120px">
            <el-option v-for="t in SCRIPT_TYPES" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="价格上限">
          <el-input v-model.number="filters.priceMax" placeholder="最高价" style="width: 120px" type="number" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadPools(1)">搜索</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <div v-if="loading" style="text-align: center; padding: 40px">
      <el-icon class="is-loading" :size="32"><Loading /></el-icon>
    </div>
    <template v-else>
      <PoolCard v-for="pool in pools" :key="pool.id" :pool="pool" />
      <el-empty v-if="pools.length === 0" description="暂无拼车" />
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { Loading } from '@element-plus/icons-vue'
import { getPlayerPoolList } from '../../api/player'
import type { PoolListItem } from '../../api/player'
import { SCRIPT_TYPES } from '../../constants'
import PoolCard from '../../components/PoolCard.vue'

const pools = ref<PoolListItem[]>([])
const loading = ref(false)
const page = ref(1)
const size = ref(20)

const filters = reactive({
  city: '',
  scriptType: '',
  priceMax: undefined as number | undefined,
})

function resetFilters() {
  filters.city = ''
  filters.scriptType = ''
  filters.priceMax = undefined
  loadPools(1)
}

async function loadPools(p?: number) {
  if (p) page.value = p
  loading.value = true
  try {
    const res = await getPlayerPoolList({
      ...filters,
      page: page.value,
      size: size.value,
      type: 0,
    })
    pools.value = res.data
  } finally {
    loading.value = false
  }
}

onMounted(() => loadPools())
</script>

<style scoped>
.player-pools { max-width: 800px; margin: 0 auto; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-header h3 { margin: 0; font-size: 18px; }
.filter-card { margin-bottom: 16px; }
</style>
