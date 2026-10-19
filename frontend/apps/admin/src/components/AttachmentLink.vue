<template>
  <el-link
    type="primary"
    :underline="false"
    class="attachment-link"
    :class="{ 'is-pending': pending }"
    @click="onClick"
  >
    <el-icon v-if="showIcon" class="attachment-icon"><document /></el-icon>{{ name || '附件' }}
  </el-link>
  <el-image-viewer
    v-if="previewUrl"
    :url-list="[previewUrl]"
    :z-index="4000"
    teleported
    @close="closePreview"
  />
</template>

<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Document } from '@element-plus/icons-vue'
import {
  downloadAttachment,
  isImageAttachment,
  loadImagePreviewUrl
} from '@crane/shared/api/file'

/**
 * 附件文件名超链：图片走鉴权拉取后弹窗预览，非图片保持确认下载。
 */
const props = defineProps<{
  url: string
  name?: string
  showIcon?: boolean
}>()

const pending = ref(false)
const previewUrl = ref('')

function revokePreview() {
  if (!previewUrl.value) return
  URL.revokeObjectURL(previewUrl.value)
  previewUrl.value = ''
}

async function openPreview() {
  pending.value = true
  try {
    revokePreview()
    previewUrl.value = await loadImagePreviewUrl(props.url)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '图片加载失败')
  } finally {
    pending.value = false
  }
}

function closePreview() {
  revokePreview()
}

async function download() {
  try {
    await ElMessageBox.confirm(
      `是否下载附件「${props.name || '附件'}」？下载后将保存到本地。`,
      '下载附件',
      { type: 'info', confirmButtonText: '下载', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  pending.value = true
  try {
    await downloadAttachment(props.url, props.name)
    ElMessage.success('已开始下载')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '下载失败')
  } finally {
    pending.value = false
  }
}

function onClick() {
  if (pending.value) return
  if (isImageAttachment(props.url, props.name)) {
    openPreview()
  } else {
    download()
  }
}

onBeforeUnmount(revokePreview)
</script>

<style scoped>
.attachment-link {
  justify-content: flex-start;
  font-size: 13px;
  max-width: 100%;
}
.attachment-link :deep(span) {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.attachment-link.is-pending {
  opacity: 0.6;
  pointer-events: none;
}
.attachment-icon {
  margin-right: 4px;
}
</style>
