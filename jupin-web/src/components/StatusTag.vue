<template>
  <el-tag :type="tagType" size="small">
    {{ text }}
  </el-tag>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { POOL_STATUS_TEXT, POOL_STATUS_TAG } from '../constants'

const props = defineProps<{ status: number }>()

const text = computed(() => POOL_STATUS_TEXT[props.status] || '未知')
const tagType = computed(() => POOL_STATUS_TAG[props.status] || 'info')
</script>
