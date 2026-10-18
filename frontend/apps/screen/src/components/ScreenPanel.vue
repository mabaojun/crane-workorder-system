<template>
  <section class="panel">
    <div class="panel-head">
      <span class="panel-title">{{ title }}</span>
      <span class="panel-extra"><slot name="extra" /></span>
    </div>
    <div class="panel-body">
      <slot />
    </div>
  </section>
</template>

<script setup lang="ts">
defineProps<{ title: string }>()
</script>

<style scoped>
.panel {
  background: var(--panel);
  border: 1px solid var(--line);
  border-radius: 6px;
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  min-height: 0;
  min-width: 0;
  flex: none;
}

.panel-head {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.panel-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--txt);
  letter-spacing: 1px;
  padding-left: 10px;
  border-left: 3px solid var(--cyan);
  line-height: 1.2;
}

.panel-extra {
  font-size: 12px;
  color: var(--sub);
}

.panel-body {
  flex: 1;
  min-height: 0;
  position: relative;
}
</style>
