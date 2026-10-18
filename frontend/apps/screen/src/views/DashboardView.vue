<template>
  <div class="viewport">
    <div class="stage" :style="{ transform: `translate(-50%, -50%) scale(${scale})` }">
      <PageHeader />
      <main class="board">
        <aside class="col col-side">
          <OrderKpi style="height: 190px" />
          <StatusRing style="height: 350px" />
          <DemandList style="flex: 1 1 0; min-height: 0" />
        </aside>
        <section class="col col-mid">
          <AlarmCenter style="height: 300px" />
          <VehicleMapChart style="flex: 1 1 0; min-height: 0" />
          <TrendRow />
        </section>
        <aside class="col col-side">
          <VehicleBoard style="height: 252px" />
          <StaffBoard style="height: 132px" />
          <AiBriefing style="height: 150px" />
          <StaffRank style="flex: 1 1 0; min-height: 0" />
        </aside>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { useStatPolling } from '../composables/useStatPolling'
import PageHeader from '../components/PageHeader.vue'
import OrderKpi from '../components/OrderKpi.vue'
import StatusRing from '../components/StatusRing.vue'
import DemandList from '../components/DemandList.vue'
import AlarmCenter from '../components/AlarmCenter.vue'
import VehicleMapChart from '../components/VehicleMapChart.vue'
import TrendRow from '../components/TrendRow.vue'
import VehicleBoard from '../components/VehicleBoard.vue'
import StaffBoard from '../components/StaffBoard.vue'
import AiBriefing from '../components/AiBriefing.vue'
import StaffRank from '../components/StaffRank.vue'

useStatPolling()

// 1920×1080 舞台等比缩放，非 16:9 视口留深色 letterbox（06 §5.2）
const scale = ref(1)

function updateScale() {
  scale.value = Math.min(window.innerWidth / 1920, window.innerHeight / 1080)
}

onMounted(() => {
  updateScale()
  window.addEventListener('resize', updateScale)
})

onUnmounted(() => {
  window.removeEventListener('resize', updateScale)
})
</script>

<style scoped>
.viewport {
  position: fixed;
  inset: 0;
  background: var(--bg0);
  overflow: hidden;
}

.stage {
  position: absolute;
  left: 50%;
  top: 50%;
  width: 1920px;
  height: 1080px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  background: linear-gradient(180deg, var(--bg1) 0%, var(--bg0) 100%);
}

.board {
  flex: 1;
  min-height: 0;
  margin-top: 16px;
  display: grid;
  grid-template-columns: 430px 1fr 430px;
  gap: 16px;
}

.col {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 0;
  min-width: 0;
}
</style>
