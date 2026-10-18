<template>
  <div class="cockpit">
    <!-- 顶部欢迎区：问候 + 大屏入口 -->
    <div class="welcome-bar">
      <div class="welcome-text">
        <h2>{{ greeting }}，{{ auth.me?.name || '管理员' }}</h2>
        <p>今天是 {{ todayText }}，业务运行平稳，请注意跟进待办事项</p>
      </div>
      <el-button type="primary" size="large" class="screen-btn" @click="openScreen">
        <el-icon class="btn-icon"><DataBoard /></el-icon>
        进入数据大屏
      </el-button>
    </div>

    <!-- 快捷入口：高频功能图标直达 -->
    <div class="quick-nav">
      <div
        v-for="nav in quickNavs"
        :key="nav.title"
        class="quick-item"
        @click="go(nav.path)"
      >
        <el-icon class="quick-icon" :style="{ color: nav.color, background: nav.bg }">
          <component :is="nav.icon" />
        </el-icon>
        <span class="quick-title">{{ nav.title }}</span>
      </div>
    </div>

    <!-- 核心处置区：需求单处置置顶 -->
    <el-card shadow="never" class="page-card core-card">
      <template #header>
        <div class="card-head">
          <span class="card-title">需求单处置</span>
          <span class="card-sub">客户需求需当日跟进，避免超时</span>
        </div>
      </template>
      <div v-loading="loading" class="stat-grid">
        <div class="stat-card" @click="goDemand('PENDING')">
          <div class="stat-top">
            <el-icon class="stat-icon" style="color: var(--brand); background: var(--brand-bg, rgba(64,158,255,.12))"><Tickets /></el-icon>
            <div class="stat-num" style="color: var(--brand)">{{ demandPending }}</div>
          </div>
          <div class="stat-title">待处理需求单</div>
        </div>
        <div class="stat-card" @click="goDemand('REVIEWING')">
          <div class="stat-top">
            <el-icon class="stat-icon" style="color: var(--warning); background: rgba(230,162,60,.12)"><View /></el-icon>
            <div class="stat-num" style="color: var(--warning)">{{ demandReviewing }}</div>
          </div>
          <div class="stat-title">处理中需求单</div>
        </div>
        <div class="stat-card" @click="goDispatch()">
          <div class="stat-top">
            <el-icon class="stat-icon" style="color: var(--danger); background: rgba(245,108,108,.12)"><RefreshLeft /></el-icon>
            <div class="stat-num" style="color: var(--danger)">{{ pendingRedispatch }}</div>
          </div>
          <div class="stat-title">待重新派单（退单/拒单）</div>
        </div>
        <div class="stat-card" @click="goException('PENDING')">
          <div class="stat-top">
            <el-icon class="stat-icon" style="color: var(--danger); background: rgba(245,108,108,.12)"><WarningFilled /></el-icon>
            <div class="stat-num" style="color: var(--danger)">{{ exceptionPending }}</div>
          </div>
          <div class="stat-title">待批复异常</div>
        </div>
      </div>
    </el-card>

    <!-- 报警信息 + 工单执行动态 -->
    <el-row :gutter="12" class="duo-row">
      <el-col :span="12" :xs="24">
        <el-card shadow="never" class="page-card half-card">
          <template #header>
            <div class="card-head">
              <span class="card-title">报警信息</span>
              <el-tag v-if="alarmCount" type="danger" size="small" effect="light">{{ alarmCount }} 条</el-tag>
            </div>
          </template>
          <div v-loading="alarmLoading" class="feed-list">
            <div v-for="a in alarms" :key="a.alarmNo" class="feed-item alarm-item" @click="a.orderId && goOrderDetail(a.orderId)">
              <span class="alarm-level" :class="a.level === 'HIGH' ? 'high' : 'mid'">
                {{ a.level === 'HIGH' ? '高' : '中' }}
              </span>
              <div class="feed-main">
                <p class="feed-title">{{ a.summary }}</p>
                <p class="feed-meta">{{ ALARM_TYPE_LABEL[a.type] || a.type }} · {{ fmtTime(a.triggeredAt) }}{{ a.durationMinutes != null ? ` · 持续 ${a.durationMinutes} 分钟` : '' }}</p>
              </div>
            </div>
            <el-empty v-if="!alarmLoading && !alarms.length" description="暂无报警，运行正常" :image-size="56" />
          </div>
        </el-card>
      </el-col>
      <el-col :span="12" :xs="24">
        <el-card shadow="never" class="page-card half-card">
          <template #header>
            <div class="card-head">
              <span class="card-title">工单执行动态</span>
              <el-link type="primary" :underline="false" @click="go('/workorder/list')">全部工单</el-link>
            </div>
          </template>
          <div v-loading="recentLoading" class="feed-list">
            <div v-for="o in recentOrders" :key="o.id" class="feed-item" @click="goOrderDetail(o.id)">
              <span class="order-status" :class="statusClass(o.status)">{{ WORK_ORDER_STATUS_LABEL[o.status] || o.status }}</span>
              <div class="feed-main">
                <p class="feed-title">{{ o.orderNo }}｜{{ o.taskName }}</p>
                <p class="feed-meta">{{ o.staffName || '未派单' }} · 计划 {{ fmtTime(o.expectedStartAt) }}</p>
              </div>
            </div>
            <el-empty v-if="!recentLoading && !recentOrders.length" description="暂无工单" :image-size="56" />
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 工单概览 -->
    <el-card shadow="never" class="page-card">
      <template #header>
        <div class="card-head">
          <span class="card-title">工单概览</span>
          <span class="card-sub">点击卡片查看对应状态列表</span>
        </div>
      </template>
      <div v-loading="loading" class="stat-grid">
        <div v-for="card in cards" :key="card.status" class="stat-card" @click="goList(card.status)">
          <div class="stat-top">
            <el-icon class="stat-icon" :style="{ color: card.color, background: card.bg }">
              <component :is="card.icon" />
            </el-icon>
            <div class="stat-num" :style="{ color: card.color }">{{ card.total }}</div>
          </div>
          <div class="stat-title">{{ card.label }}</div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Tickets, View, RefreshLeft, WarningFilled, DataBoard, List, Van, User,
  Setting, Location, Clock, CircleCheck, Loading, VideoPause, Odometer
} from '@element-plus/icons-vue'
import { pageWorkOrders, type WorkOrder } from '@crane/shared/api/workorder'
import { WORK_ORDER_STATUS_LABEL } from '@crane/shared'
import { pageDemands } from '@crane/shared/api/demand'
import { pageExceptions } from '@crane/shared/api/exception'
import { pagePendingRedispatch } from '@crane/shared/api/dispatch'
import { fetchStatAlarms, type StatAlarmItem } from '@crane/shared/api/stat'
import { useAuthStore } from '../../stores/auth'
import { fmtTime } from '../../utils/format'

const ALARM_TYPE_LABEL: Record<string, string> = {
  CONFLICT: '冲突',
  NOT_ARRIVED: '未按时到场',
  NOT_CHECKED_IN: '未签到',
  VEHICLE_OCCUPIED: '车辆占用'
}

const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)

const cards = ref([
  { status: 'PENDING_ACCEPT', label: '待接单', color: 'var(--sub)', bg: 'rgba(144,147,153,.12)', icon: Clock, total: 0 },
  { status: 'PENDING_ARRIVE', label: '待到场', color: 'var(--arrived)', bg: 'rgba(64,158,255,.12)', icon: Van, total: 0 },
  { status: 'IN_PROGRESS', label: '进行中', color: 'var(--brand)', bg: 'rgba(64,158,255,.12)', icon: Loading, total: 0 },
  { status: 'SUSPENDED', label: '施工受阻', color: 'var(--warning)', bg: 'rgba(230,162,60,.12)', icon: VideoPause, total: 0 },
  { status: 'COMPLETED', label: '已完成', color: 'var(--success)', bg: 'rgba(103,194,58,.12)', icon: CircleCheck, total: 0 }
])

const quickNavs = computed(() => [
  { title: '需求单处置', icon: Tickets, color: '#409eff', bg: 'rgba(64,158,255,.12)', path: '/demand/list' },
  { title: '工单管理', icon: List, color: '#67c23a', bg: 'rgba(103,194,58,.12)', path: '/workorder/list' },
  { title: '派单中心', icon: Odometer, color: '#e6a23c', bg: 'rgba(230,162,60,.12)', path: '/dispatch/pending' },
  { title: '员工档案', icon: User, color: '#909399', bg: 'rgba(144,147,153,.12)', path: '/staff/list' },
  { title: '车辆档案', icon: Van, color: '#7c6af2', bg: 'rgba(124,106,242,.12)', path: '/staff/vehicle-list' },
  { title: '客户档案', icon: User, color: '#f56c6c', bg: 'rgba(245,108,108,.12)', path: '/customer/list' },
  { title: '常用施工地点', icon: Location, color: '#13c2c2', bg: 'rgba(19,194,194,.12)', path: '/system/place-list' },
  { title: '系统配置', icon: Setting, color: '#6b7db3', bg: 'rgba(107,125,179,.12)', path: '/system/config' }
])

const demandPending = ref(0)
const demandReviewing = ref(0)
const exceptionPending = ref(0)
const pendingRedispatch = ref(0)

// 报警信息（大屏同源接口，仅管理员）
const alarms = ref<StatAlarmItem[]>([])
const alarmCount = ref(0)
const alarmLoading = ref(false)

// 工单执行动态（最近工单）
const recentOrders = ref<WorkOrder[]>([])
const recentLoading = ref(false)

const isAdmin = () => auth.me?.role === 'ADMIN'

const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '凌晨好'
  if (h < 12) return '上午好'
  if (h < 18) return '下午好'
  return '晚上好'
})

const todayText = computed(() => {
  const d = new Date()
  const week = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六'][d.getDay()]
  return `${d.getFullYear()} 年 ${d.getMonth() + 1} 月 ${d.getDate()} 日 ${week}`
})

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
      .catch(() => {}),
    // 工单执行动态：最近 5 条
    recentLoadingRun()
  ]
  if (isAdmin()) {
    extras.push(
      pagePendingRedispatch({ pageNum: 1, pageSize: 1 })
        .then((p) => { pendingRedispatch.value = p.total })
        .catch(() => {}),
      alarmLoadingRun()
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

async function alarmLoadingRun() {
  alarmLoading.value = true
  try {
    const res = await fetchStatAlarms()
    alarms.value = res.list.slice(0, 5)
    alarmCount.value = res.count
  } catch { /* 报警模块失败不影响工作台整体 */ }
  finally { alarmLoading.value = false }
}

async function recentLoadingRun() {
  recentLoading.value = true
  try {
    const page = await pageWorkOrders({ pageNum: 1, pageSize: 5 })
    recentOrders.value = page.list
  } catch { /* 动态模块失败不影响整体 */ }
  finally { recentLoading.value = false }
}

/** 大屏入口：dev 走独立端口 7776，生产为同源 /screen/ 子路径，新窗口打开不打断工作台会话 */
function openScreen() {
  const url = import.meta.env.DEV
    ? `http://${location.hostname}:7776/`
    : `${location.origin}/screen/`
  window.open(url, '_blank')
}

function statusClass(status: string) {
  const map: Record<string, string> = {
    PENDING_ACCEPT: 's-pending',
    PENDING_ARRIVE: 's-arrive',
    IN_PROGRESS: 's-progress',
    SUSPENDED: 's-suspend',
    COMPLETED: 's-done',
    CANCELLED: 's-cancel'
  }
  return map[status] || 's-pending'
}

function go(path: string) {
  router.push(path)
}
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
function goOrderDetail(id: number) {
  router.push(`/workorder/detail/${id}`)
}
</script>

<style scoped>
.cockpit { display: flex; flex-direction: column; gap: 12px; }

/* 顶部欢迎区 */
.welcome-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px;
  border-radius: 12px;
  background: linear-gradient(120deg, var(--brand, #409eff) 0%, #2f7fe0 60%, #2456a8 100%);
  color: #fff;
}
.welcome-text h2 { margin: 0 0 6px; font-size: 20px; font-weight: 600; }
.welcome-text p { margin: 0; font-size: 13px; opacity: .85; }
.screen-btn {
  border: none;
  font-weight: 600;
}
.screen-btn .btn-icon { margin-right: 6px; }

/* 快捷入口 */
.quick-nav {
  display: grid;
  grid-template-columns: repeat(8, 1fr);
  gap: 10px;
  padding: 16px 8px;
  background: #fff;
  border-radius: 12px;
  border: 1px solid var(--line, #e4e7ed);
}
.quick-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 10px 4px;
  border-radius: 10px;
  cursor: pointer;
  transition: background .2s;
  min-height: 44px;
}
.quick-item:hover { background: var(--fill-light, #f5f7fa); }
.quick-icon {
  width: 44px;
  height: 44px;
  font-size: 22px;
  border-radius: 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.quick-title { font-size: 13px; color: var(--txt, #303133); }

/* 卡片头 */
.card-head { display: flex; align-items: center; gap: 10px; }
.card-title { font-weight: 600; }
.card-sub { font-size: 12px; color: var(--sub, #909399); font-weight: 400; }
.core-card { border-top: 2px solid var(--brand, #409eff); }

/* 统计卡片 */
.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}
.stat-card {
  border: 1px solid var(--line, #e4e7ed);
  border-radius: 10px;
  padding: 14px 16px;
  cursor: pointer;
  transition: box-shadow .2s, transform .2s;
  min-height: 44px;
}
.stat-card:hover { box-shadow: 0 4px 12px rgba(0,0,0,.08); transform: translateY(-2px); }
.stat-top { display: flex; align-items: center; justify-content: space-between; }
.stat-icon { font-size: 22px; border-radius: 8px; padding: 6px; }
.stat-num { font-size: 26px; font-weight: 700; line-height: 1.2; }
.stat-title { margin-top: 6px; color: var(--sub, #909399); font-size: 13px; }

/* 报警 / 动态列表 */
.duo-row { margin: 0; }
.half-card { height: 100%; }
.feed-list { display: flex; flex-direction: column; gap: 8px; min-height: 120px; }
.feed-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 8px 10px;
  border-radius: 8px;
  cursor: pointer;
  transition: background .2s;
}
.feed-item:hover { background: var(--fill-light, #f5f7fa); }
.feed-main { flex: 1; min-width: 0; }
.feed-title { margin: 0; font-size: 13px; color: var(--txt, #303133); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.feed-meta { margin: 2px 0 0; font-size: 12px; color: var(--sub, #909399); }
.alarm-level {
  flex: none;
  width: 24px;
  height: 24px;
  border-radius: 6px;
  font-size: 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  margin-top: 2px;
}
.alarm-level.high { background: var(--danger, #f56c6c); }
.alarm-level.mid { background: var(--warning, #e6a23c); }
.order-status {
  flex: none;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  margin-top: 2px;
}
.s-pending { color: #909399; background: rgba(144,147,153,.12); }
.s-arrive { color: var(--arrived, #409eff); background: rgba(64,158,255,.12); }
.s-progress { color: var(--brand, #409eff); background: rgba(64,158,255,.15); font-weight: 600; }
.s-suspend { color: var(--warning, #e6a23c); background: rgba(230,162,60,.12); }
.s-done { color: var(--success, #67c23a); background: rgba(103,194,58,.12); }
.s-cancel { color: var(--sub, #909399); background: rgba(144,147,153,.1); }

@media (max-width: 1200px) {
  .quick-nav { grid-template-columns: repeat(4, 1fr); }
  .stat-grid { grid-template-columns: repeat(2, 1fr); }
}
</style>
