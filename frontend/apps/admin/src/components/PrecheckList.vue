<template>
  <ul class="precheck-list">
    <li v-for="item in checks" :key="item.code" :class="item.pass ? 'ok' : 'fail'">
      <span class="dot" />
      <span class="name">{{ codeLabel(item.code) }}</span>
      <span class="msg">{{ item.message }}</span>
    </li>
  </ul>
</template>

<script setup lang="ts">
import type { CheckItem } from '@crane/shared/api/dispatch'
import { CHECK_CODE_LABEL } from '../utils/tags'

defineProps<{ checks: CheckItem[] }>()

function codeLabel(code: string) {
  return CHECK_CODE_LABEL[code] || code
}
</script>

<style scoped>
.precheck-list {
  list-style: none;
  margin: 0;
  padding: 10px 12px;
  background: var(--bg);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.precheck-list li { display: flex; align-items: baseline; gap: 8px; font-size: 13px; }
.dot { width: 8px; height: 8px; border-radius: 50%; flex: none; align-self: center; }
.ok .dot { background: var(--success); }
.fail .dot { background: var(--danger); }
.ok .msg { color: var(--sub); }
.fail .msg { color: var(--danger); }
.name { font-weight: 500; flex: none; }
</style>
