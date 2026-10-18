<template>
  <div>
    <el-alert type="info" :closable="false" show-icon class="notice">
      <template #title>
        「系统名称」为本项目核心配置：修改 system.name 并保存后，全端（后台 / 客户端 / 司机端）的标题与页面展示将同步生效。system / map / auth 三组支持在线编辑，其余组只读。
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
          <template #default="{ row }">{{ rowTip(row) }}</template>
        </el-table-column>
        <el-table-column label="配置值" min-width="240">
          <template #default="{ row }">
            <template v-if="isAuthSwitch(row)">
              <el-switch
                :model-value="row.configValue === 'true'"
                active-text="开启"
                inactive-text="关闭"
                @change="(val: boolean) => onAuthSwitchChange(row, val)"
              />
            </template>
            <template v-else-if="editable(row)">
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
        <el-table-column v-if="isEditableGroup(group.name)" label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <template v-if="editingKey === row.configKey">
              <el-button size="small" text type="success" @click="saveRow(row)">保存</el-button>
              <el-button size="small" text @click="cancelEdit(row)">取消</el-button>
            </template>
            <el-button
              v-else-if="!isAuthSwitch(row)"
              size="small"
              text
              type="primary"
              @click="beginEdit(row)"
            >编辑</el-button>
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

/** 允许行内编辑的配置组 */
const EDITABLE_GROUPS = ['system', 'map', 'auth']
/** 这些组的配置值允许清空（如清掉地图 Key） */
const EMPTY_VALUE_OK_GROUPS = ['map', 'auth']
/** 布尔开关类配置：true / false 字符串 */
const AUTH_SWITCH_KEY = 'auth.password_login_enabled'

const GROUP_LABEL: Record<string, string> = {
  system: '系统信息（可编辑）',
  map: '地图配置（可编辑）',
  auth: '登录配置（可编辑）',
  ai: 'AI 服务配置（运行参数，由后端维护）'
}

/** ai 组各项说明（AI 简报/日报调度参数，仅展示） */
const AI_TIPS: Record<string, string> = {
  'ai.daily_run_time': 'AI 日报每日执行时间（HH:mm），由后端调度任务读取',
  'ai.briefing_retention_days': 'AI 简报保留天数，超期自动清理'
}

/** map 组各项说明（与后端代理 / 前端渲染 / mock 联调方式对应） */
const MAP_TIPS: Record<string, string> = {
  'map.amap.web_key': '填高德 Web 服务 Key，供后端代理逆地理编码与 POI 关键词搜索',
  'map.amap.jsapi_key': '填高德 JSAPI Key，供前端地图选点渲染',
  'map.amap.security_jscode': '填高德 JSAPI 安全密钥，与 jsapi_key 配套使用',
  'map.mock': 'true 时后端返回模拟数据，便于无 Key 联调'
}

const groups = computed<ConfigGroup[]>(() => {
  const map = new Map<string, SysConfig[]>()
  for (const c of configs.value) {
    const key = c.groupName || 'other'
    if (!map.has(key)) map.set(key, [])
    map.get(key)!.push(c)
  }
  const ordered: ConfigGroup[] = []
  for (const [name, items] of map) {
    ordered.push({ name, label: GROUP_LABEL[name] || `${name}（只读）`, items })
  }
  // 可编辑组置顶：system → map → auth，其余按名称排序
  ordered.sort((a, b) => {
    const ia = EDITABLE_GROUPS.indexOf(a.name)
    const ib = EDITABLE_GROUPS.indexOf(b.name)
    const ra = ia === -1 ? 99 : ia
    const rb = ib === -1 ? 99 : ib
    if (ra !== rb) return ra - rb
    return a.name.localeCompare(b.name)
  })
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

function isEditableGroup(name: string) {
  return EDITABLE_GROUPS.includes(name)
}

function editable(row: SysConfig) {
  return isEditableGroup(row.groupName) && !isAuthSwitch(row)
}

function isAuthSwitch(row: SysConfig) {
  return row.groupName === 'auth' && row.configKey === AUTH_SWITCH_KEY
}

function rowTip(row: SysConfig) {
  if (row.groupName === 'map' && MAP_TIPS[row.configKey]) return MAP_TIPS[row.configKey]
  if (row.groupName === 'ai' && AI_TIPS[row.configKey]) return AI_TIPS[row.configKey]
  return row.description || '-'
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
  // map / auth 组允许清空（如清除 Key），system 组仍要求非空
  if (!EMPTY_VALUE_OK_GROUPS.includes(row.groupName) && !row.configValue.trim()) {
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

/** auth.password_login_enabled 开关：true / false 字符串直存 */
async function onAuthSwitchChange(row: SysConfig, val: boolean) {
  const next = val ? 'true' : 'false'
  try {
    await updateConfig(row.configKey, next)
    row.configValue = next
    ElMessage.success('已保存并全端生效')
    await load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  }
}
</script>

<style scoped>
.notice { margin-bottom: var(--gap); }
</style>
