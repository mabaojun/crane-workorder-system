<template>
  <div class="file-links">
    <template v-if="items.length">
      <attachment-link
        v-for="(item, index) in items"
        :key="`${item.url}-${index}`"
        :url="item.url"
        :name="item.name"
        show-icon
      />
    </template>
    <span v-else class="file-empty">{{ emptyText }}</span>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { fileNameFromUrl } from '@crane/shared/api/file'
import AttachmentLink from './AttachmentLink.vue'

/**
 * 附件文件名超链：图片点击弹窗预览，其余文件确认后下载。
 */
const props = defineProps<{
  urls?: string[] | string | null
  emptyText?: string
}>()

const items = computed(() => {
  const raw = Array.isArray(props.urls) ? props.urls : props.urls ? [props.urls] : []
  return raw.filter((url) => !!url).map((url) => ({ url, name: fileNameFromUrl(url) || '附件' }))
})
</script>

<style scoped>
.file-links {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}
.file-empty {
  color: var(--sub);
  font-size: 13px;
}
</style>
