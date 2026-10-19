<template>
  <ScreenPanel title="AI 今日工作简报" class="ai-panel">
    <template #extra>
      <div class="ai-head">
        <span class="ai-badge">AI</span>
        <button class="ai-gen" :disabled="generating" @click="onGenerate">
          {{ generating ? '生成中…' : '手动生成' }}
        </button>
      </div>
    </template>
    <div class="ai-body">
      <div v-if="generating" class="ai-placeholder">正在生成今日简报，请稍候…</div>
      <template v-else-if="report && report.callStatus === 'SUCCESS'">
        <div class="ai-text">{{ report.contentMarkdown }}</div>
        <div class="ai-updated">更新于 {{ fmtHM(report.generatedAt) }} · AI 输出仅供参考</div>
      </template>
      <div v-else-if="report && report.callStatus === 'FAILED'" class="ai-placeholder">
        上次生成失败：{{ report.failReason || '未知原因' }}
      </div>
      <div v-else class="ai-placeholder">暂无 AI 简报，点击右上角「手动生成」立即创建</div>
    </div>

    <!-- 全文模态框：点击简报内容弹出 -->
    <teleport to="body">
      <div v-if="dialogVisible" class="ai-modal-mask" @click.self="dialogVisible = false">
        <div class="ai-modal">
          <div class="ai-modal-head">
            <span>AI 今日工作简报{{ report ? ` · ${report.briefingNo}` : '' }}</span>
            <span class="ai-modal-close" @click="dialogVisible = false">✕</span>
          </div>
          <div v-if="report" class="ai-modal-body">{{ report.contentMarkdown }}</div>
        </div>
      </div>
    </teleport>
  </ScreenPanel>
</template>

<script setup lang="ts">
// 大屏 AI 简报：展示最近一条报告，支持手动触发生成；点击正文弹模态框看全文
import { onMounted, ref } from 'vue'
import ScreenPanel from './ScreenPanel.vue'
import { generateAiReport, getLatestAiReport, type AiReport } from '@crane/shared/api/ai'
import { fmtHM } from '../utils/format'
const report = ref<AiReport | null>(null)
const generating = ref(false)
const dialogVisible = ref(false)

onMounted(load)

async function load() {
  try {
    const res = await getLatestAiReport()
    report.value = res.latest
  } catch {
    /* 大屏静默降级：拉不到就保持占位态 */
  }
}

async function onGenerate() {
  generating.value = true
  try {
    const res = await generateAiReport()
    report.value = res.report
    dialogVisible.value = true
  } catch (e) {
    report.value = {
      id: 0,
      briefingNo: '--',
      type: 'DAILY',
      periodStart: null,
      periodEnd: null,
      contentMarkdown: '',
      callStatus: 'FAILED',
      failReason: e instanceof Error ? e.message : '生成失败',
      generatedAt: new Date().toISOString(),
      expiresAt: null
    }
  } finally {
    generating.value = false
  }
}

</script>

<style scoped>
.ai-badge {
  color: var(--purple);
  border: 1px solid rgba(179, 136, 255, 0.45);
  border-radius: 3px;
  padding: 0 6px;
  font-size: 11px;
  line-height: 18px;
}
.ai-head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.ai-gen {
  background: rgba(179, 136, 255, 0.15);
  color: var(--purple);
  border: 1px solid rgba(179, 136, 255, 0.45);
  border-radius: 3px;
  font-size: 11px;
  line-height: 18px;
  padding: 0 8px;
  cursor: pointer;
}
.ai-gen:disabled {
  opacity: 0.5;
  cursor: wait;
}
.ai-body {
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 8px;
  padding: 0 4px;
}
.ai-placeholder {
  font-size: 13px;
  color: var(--sub);
}
.ai-text {
  font-size: 12.5px;
  line-height: 1.7;
  color: var(--text, #dde3ee);
  max-height: 108px;
  overflow: hidden;
  cursor: zoom-in;
}
.ai-updated {
  font-size: 12px;
  color: var(--sub);
  opacity: 0.7;
}

/* 全文模态框 */
.ai-modal-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 999;
}
.ai-modal {
  width: min(720px, 86vw);
  max-height: 80vh;
  background: #141a2c;
  border: 1px solid rgba(179, 136, 255, 0.4);
  border-radius: 10px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.ai-modal-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 18px;
  font-size: 15px;
  font-weight: 600;
  color: var(--purple);
  border-bottom: 1px solid rgba(179, 136, 255, 0.25);
}
.ai-modal-close {
  cursor: pointer;
  color: var(--sub);
  font-size: 16px;
}
.ai-modal-body {
  padding: 16px 18px;
  overflow: auto;
  white-space: pre-wrap;
  line-height: 1.8;
  font-size: 14px;
  color: var(--text, #dde3ee);
}
</style>
