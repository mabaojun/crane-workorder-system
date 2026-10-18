<template>
  <ScreenPanel title="需求单待处理" class="demand-panel">
    <template #extra>
      <span class="demand-count" v-if="count">待处理 {{ count }}</span>
    </template>
    <div v-if="list.length" class="demand-list">
      <div v-for="d in list" :key="d.demandNo" class="demand-row">
        <div class="demand-title" :title="d.title">{{ d.title }}</div>
        <div class="demand-meta">
          <span class="demand-contact">{{ d.contactName }}</span>
          <span class="demand-tag" :class="tagClass(d.status)">{{ tagLabel(d.status) }}</span>
        </div>
      </div>
    </div>
    <div v-else class="demand-empty">暂无待处理需求单</div>
  </ScreenPanel>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { DEMAND_ORDER_STATUS_LABEL } from '@crane/shared'
import { statStore } from '../store/stat'
import ScreenPanel from './ScreenPanel.vue'

const list = computed(() => statStore.dashboard?.pendingDemands ?? [])
const count = computed(() => statStore.dashboard?.customer.demandPending ?? 0)

function tagLabel(status: string): string {
  return DEMAND_ORDER_STATUS_LABEL[status] ?? status
}

function tagClass(status: string): string {
  if (status === 'PENDING') return 'tag-amber'
  if (status === 'REVIEWING') return 'tag-cyan'
  return 'tag-sub'
}
</script>

<style scoped>
.demand-list {
  height: 100%;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.demand-row {
  padding: 8px 10px;
  background: rgba(6, 12, 26, 0.45);
  border: 1px solid var(--line);
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.demand-title {
  flex: 1;
  min-width: 0;
  font-size: 14px;
  color: var(--txt);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.demand-meta {
  flex: none;
  display: flex;
  align-items: center;
  gap: 10px;
}

.demand-contact {
  font-size: 12px;
  color: var(--sub);
}

.demand-tag {
  font-size: 12px;
  padding: 1px 8px;
  border-radius: 3px;
  line-height: 18px;
}

.tag-amber {
  color: var(--amber);
  background: rgba(255, 213, 79, 0.12);
  border: 1px solid rgba(255, 213, 79, 0.4);
}

.tag-cyan {
  color: var(--cyan);
  background: rgba(56, 189, 248, 0.12);
  border: 1px solid rgba(56, 189, 248, 0.4);
}

.tag-sub {
  color: var(--sub);
  border: 1px solid var(--line);
}

.demand-count {
  color: var(--amber);
}

.demand-empty {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--sub);
  font-size: 13px;
}
</style>
