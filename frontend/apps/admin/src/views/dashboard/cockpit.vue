<template>
  <div class="cockpit">
    <el-card shadow="never" class="page-card">
      <template #header>工单概览</template>
      <div v-loading="loading" class="stat-grid">
        <div
          v-for="card in cards"
          :key="card.status"
          class="stat-card"
          @click="goList(card.status)"
        >
          <div class="stat-num" :style="{ color: card.color }">{{ card.total }}</div>
          <div class="stat-title">{{ card.label }}</div>
        </div>
      </div>
    </el-card>
    <el-card shadow="never" class="page-card">
      <template #header>待办提醒</template>
      <div v-loading="loading" class="stat-grid">
        <div class="stat-card" @click="goDemand('PENDING')">
          <div class="stat-num" :style="{ color: 'var(--brand)' }">{{ demandPending }}</div>
          <div class="stat-title">待处理需求单</div>
        </div>
        <div class="stat-card" @click="goDemand('REVIEWING')">
          <div class="stat-num" :style="{ color: 'var(--warning)' }">{{ demandReviewing }}</div>
          <div class="stat-title">待复核需求单</div>
        </div>
        <div class="stat-card" @click="goException('PENDING')">
          <div class="stat-num" :style="{ color: 'var(--danger)' }">{{ exceptionPending }}</div>
          <div class="stat-title">待批复异常</div>
        </div>
        <div class="stat-card" @click="goDispatch()">
          <div class="stat-num" :style="{ color: 'var(--txt)' }">{{ pendingRedispatch }}</div>
          <div class="stat-title">待重新派单</div>
        </div>
      </div>
      <p class="hint">点击卡片可跳转对应列表并自动带入状态筛选。</p>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { pageWorkOrders } from '@crane/shared/api/workorder'
import { pageDemands } from '@crane/shared/api/demand'
import { pageExceptions } from '@crane/shared/api/exception'
import { pagePendingRedispatch } from '@crane/shared/api/dispatch'
import { useAuthStore } from '../../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)

const cards = ref([
  { status: 'PENDING_ACCEPT', label: '待接单', color: 'var(--sub)', total: 0 },
  { status: 'PENDING_ARRIVE', label: '待到场', color: 'var(--arrived)', total: 0 },
  { status: 'IN_PROGRESS', label: '进行中', color: 'var(--brand)', total: 0 },
  { status: 'SUSPENDED', label: '施工受阻', color: 'var(--warning)', total: 0 },
  { status: 'COMPLETED', label: '已完成', color: 'var(--success)', total: 0 }
])

const demandPending = ref(0)
const demandReviewing = ref(0)
const exceptionPending = ref(0)
const pendingRedispatch = ref(0)

const isAdmin = () => auth.me?.role === 'ADMIN'

onMounted(async () => {
  loading.value = true
  // 分别按状态取 total（pageSize=1 只为计数）
  const tasks = cards.value.map(async (card) => {
    try {
      const page = await pageWorkOrders({ status: card.status, pageNum: 1, pageSize: 1 })
      card.total = page.total
    } catch { /* 单项失败不影响整体 */ }
  })
  const extras: Promise<void>[] = [
    pageDemands({ status: 'PENDING', pageNum: 1, pageSize: 1 })
      .then((p) => { demandPending.value = p.total })
      .catch(() => {}),
    pageDemands({ status: 'REVIEWING', pageNum: 1, pageSize: 1 })
      .then((p) => { demandReviewing.value = p.total })
      .catch(() => {}),
    pageExceptions({ status: 'PENDING', pageNum: 1, pageSize: 1 })
      .then((p) => { exceptionPending.value = p.total })
      .catch(() => {})
  ]
  if (isAdmin()) {
    extras.push(
      pagePendingRedispatch({ pageNum: 1, pageSize: 1 })
        .then((p) => { pendingRedispatch.value = p.total })
        .catch(() => {})
    )
  }
  try {
    await Promise.all([...tasks, ...extras])
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '工作台数据加载失败')
  } finally {
    loading.value = false
  }
})

function goList(status: string) {
  router.push({ path: '/workorder/list', query: { status } })
}
function goDemand(status: string) {
  router.push({ path: '/demand/list', query: { status } })
}
function goException(status: string) {
  router.push({ path: '/exception/list', query: { status } })
}
function goDispatch() {
  router.push('/dispatch/pending')
}
</script>

<style scoped>
.hint { color: var(--sub); font-size: 12px; margin: 12px 0 0; }
</style>
