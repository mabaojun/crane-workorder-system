<template>
  <div class="status-page">
    <div class="page-head">
      <div class="head-text">
        <h2>订单状态</h2>
        <p>围绕时间查看当日工单：计划时段、实际开工与持续时长</p>
      </div>
      <status-date-bar v-model="date" @change="load" />
    </div>

    <div class="stat-row">
      <div class="stat-item">
        <span class="num">{{ board?.totalOrders ?? 0 }}</span>
        <span class="label">当日工单</span>
      </div>
      <div class="stat-item">
        <span class="num active">{{ ongoingCount }}</span>
        <span class="label">进行中 / 受阻</span>
      </div>
      <div class="stat-item">
        <span class="num">{{ completedCount }}</span>
        <span class="label">已完成</span>
      </div>
      <div class="stat-item">
        <span class="num">{{ fmtDuration(totalMinutes) }}</span>
        <span class="label">当日累计持续</span>
      </div>
    </div>

    <div v-loading="loading" class="gantt-card">
      <div class="gantt-head">
        <span class="col-info">工单 / 人员 / 车辆</span>
        <span class="col-time">时间轴（00:00 - 24:00）</span>
        <span class="col-duration">持续</span>
      </div>
      <div v-for="o in board?.orders ?? []" :key="o.id" class="gantt-row" @click="goOrder(o.id)">
        <div class="col-info">
          <div class="row-title">
            <status-tag kind="workorder" :status="o.status" />
            <span class="task">{{ o.taskName }}</span>
            <span class="order-no">{{ o.orderNo }}</span>
          </div>
          <div class="row-meta">
            <span>{{ o.customerName || '-' }}</span>
            <span>{{ o.staffName || '未派单' }}</span>
            <span>{{ o.vehiclePlateNo || '未派车' }}</span>
          </div>
          <div class="row-range">{{ fmtRange(o) }}</div>
        </div>
        <div class="col-time">
          <day-timeline :orders="[o]" :date="date" @select="onSelectOrder" />
        </div>
        <div class="col-duration">{{ fmtOrderDuration(o) }}</div>
      </div>

      <el-empty
        v-if="!loading && !(board?.orders.length)"
        :description="`${date} 暂无工单`"
        :image-size="70"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  fetchStatusBoard,
  type StatusBoard,
  type StatusBoardOrder
} from '@crane/shared/api/stat'
import StatusDateBar from '../../components/StatusDateBar.vue'
import DayTimeline from '../../components/DayTimeline.vue'
import StatusTag from '../../components/StatusTag.vue'
import { fmtDuration, fmtOrderDuration, fmtRange, todayStr } from '../../utils/status'

const router = useRouter()
const date = ref(todayStr())
const loading = ref(false)
const board = ref<StatusBoard | null>(null)

const ongoingCount = computed(
  () => (board.value?.orders ?? []).filter((o) => ['IN_PROGRESS', 'SUSPENDED'].includes(o.status)).length
)
const completedCount = computed(
  () => (board.value?.orders ?? []).filter((o) => o.status === 'COMPLETED').length
)
const totalMinutes = computed(
  () => (board.value?.orders ?? []).reduce((sum, o) => sum + (o.durationMinutes ?? 0), 0)
)

async function load() {
  loading.value = true
  try {
    board.value = await fetchStatusBoard(date.value)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '订单状态加载失败')
    board.value = null
  } finally {
    loading.value = false
  }
}

function goOrder(id: number) {
  router.push(`/workorder/detail/${id}`)
}

function onSelectOrder(order: StatusBoardOrder) {
  goOrder(order.id)
}

onMounted(load)
</script>

<style scoped>
.status-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.page-head {
  background: var(--panel);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  padding: 16px 18px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}
.head-text h2 {
  margin: 0 0 4px;
  font-size: 20px;
}
.head-text p {
  margin: 0;
  color: var(--sub);
  font-size: 13px;
}
.stat-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}
.stat-item {
  background: var(--panel);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  padding: 12px 16px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.stat-item .num {
  font-size: 24px;
  font-weight: 700;
  color: var(--txt);
  font-variant-numeric: tabular-nums;
}
.stat-item .num.active {
  color: var(--brand);
}
.stat-item .label {
  font-size: 12px;
  color: var(--sub);
}
.gantt-card {
  background: var(--panel);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  padding: 14px 16px;
  min-height: 140px;
}
.gantt-head {
  display: grid;
  grid-template-columns: minmax(280px, 1.1fr) minmax(320px, 2fr) 100px;
  gap: 16px;
  padding: 0 6px 8px;
  border-bottom: 1px solid var(--line);
  font-size: 12px;
  color: var(--sub);
}
.gantt-row {
  display: grid;
  grid-template-columns: minmax(280px, 1.1fr) minmax(320px, 2fr) 100px;
  gap: 16px;
  align-items: center;
  padding: 12px 6px;
  border-bottom: 1px solid var(--line);
  cursor: pointer;
}
.gantt-row:last-of-type {
  border-bottom: none;
}
.gantt-row:hover {
  background: rgba(64, 158, 255, 0.04);
}
.row-title {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.row-title .task {
  font-size: 14px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.row-title .order-no {
  font-size: 12px;
  color: var(--sub);
  flex: none;
}
.row-meta {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  font-size: 12px;
  color: var(--sub);
  margin-top: 4px;
}
.row-range {
  font-size: 12px;
  color: var(--sub);
  margin-top: 2px;
  font-variant-numeric: tabular-nums;
}
.col-duration {
  text-align: right;
  font-size: 13px;
  font-weight: 600;
  color: var(--txt);
  font-variant-numeric: tabular-nums;
}
@media (max-width: 1200px) {
  .stat-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .gantt-head {
    display: none;
  }
  .gantt-row {
    grid-template-columns: 1fr;
    gap: 8px;
  }
  .col-duration {
    text-align: left;
  }
}
</style>
