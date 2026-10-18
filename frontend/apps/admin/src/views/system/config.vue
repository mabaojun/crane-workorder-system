<template>
  <div>
    <el-alert type="info" :closable="false" show-icon class="notice">
      <template #title>
        「系统名称」为本项目核心配置：修改 system.name 并保存后，全端（后台 / 客户端 / 司机端）的标题与页面展示将同步生效。
      </template>
    </el-alert>

    <el-card
      v-for="group in groups"
      :key="group.name"
      shadow="never"
      class="page-card"
    >
      <template #header>{{ group.label }}</template>
      <el-table :data="group.items" border size="small">
        <el-table-column label="配置键" prop="configKey" width="220" />
        <el-table-column label="说明" min-width="200">
          <template #default="{ row }">{{ row.description || '-' }}</template>
        </el-table-column>
        <el-table-column label="配置值" min-width="240">
          <template #default="{ row }">
            <template v-if="editable(row)">
              <el-input
                v-model="row.configValue"
                size="small"
                :disabled="editingKey !== null && editingKey !== row.configKey"
              />
            </template>
            <template v-else>{{ row.configValue }}</template>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="140">
          <template #default="{ row }">{{ fmtTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column v-if="group.name === 'system'" label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <template v-if="editingKey === row.configKey">
              <el-button size="small" text type="success" @click="saveRow(row)">保存</el-button>
              <el-button size="small" text @click="cancelEdit(row)">取消</el-button>
            </template>
            <el-button v-else size="small" text type="primary" @click="beginEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listConfigs, updateConfig, type SysConfig } from '@crane/shared/api/iam'
import { loadBranding, systemName } from '@crane/shared/api/branding'
import { fmtTime } from '../../utils/format'

interface ConfigGroup {
  name: string
  label: string
  items: SysConfig[]
}

const configs = ref<SysConfig[]>([])
const loading = ref(false)
const editingKey = ref<string | null>(null)
const backupValue = ref('')

const GROUP_LABEL: Record<string, string> = {
  system: '系统信息（可编辑）',
  demand: '需求单（只读）',
  workorder: '工单（只读）',
  dispatch: '派单（只读）'
}

const groups = computed<ConfigGroup[]>(() => {
  const map = new Map<string, SysConfig[]>()
  for (const c of configs.value) {
    const key = c.groupName || 'other'
    if (!map.has(key)) map.set(key, [])
    map.get(key)!.push(c)
  }
  // system 分组置顶
  const ordered: ConfigGroup[] = []
  for (const [name, items] of map) {
    ordered.push({ name, label: GROUP_LABEL[name] || `${name}（只读）`, items })
  }
  ordered.sort((a, b) => (a.name === 'system' ? -1 : b.name === 'system' ? 1 : a.name.localeCompare(b.name)))
  return ordered
})

onMounted(load)

async function load() {
  loading.value = true
  try {
    configs.value = await listConfigs()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '配置加载失败')
  } finally {
    loading.value = false
  }
}

function editable(row: SysConfig) {
  return row.groupName === 'system'
}

function beginEdit(row: SysConfig) {
  editingKey.value = row.configKey
  backupValue.value = row.configValue
}

function cancelEdit(row: SysConfig) {
  row.configValue = backupValue.value
  editingKey.value = null
}

async function saveRow(row: SysConfig) {
  if (!row.configValue.trim()) {
    ElMessage.warning('配置值不能为空')
    return
  }
  try {
    await updateConfig(row.configKey, row.configValue.trim())
    ElMessage.success('已保存并全端生效')
    editingKey.value = null
    await load()
    // system.name 变更后刷新进程内品牌缓存，当前页面标题同步
    await loadBranding(true)
    document.title = `系统配置 - ${systemName()}`
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  }
}
</script>

<style scoped>
.notice { margin-bottom: var(--gap); }
</style>
