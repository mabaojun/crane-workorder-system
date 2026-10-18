<template>
  <div>
    <el-card shadow="never" class="page-card">
      <div class="toolbar">
        <el-form inline class="toolbar-form" @submit.prevent="onSearch">
          <el-form-item label="地点名称">
            <el-input
              v-model="keyword"
              clearable
              placeholder="按名称模糊搜索"
              style="width: 220px"
              @clear="onSearch"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="onSearch">查询</el-button>
            <el-button @click="onReset">重置</el-button>
          </el-form-item>
        </el-form>
        <div class="add-area">
          <el-input
            v-model="newName"
            maxlength="128"
            placeholder="输入常用施工地点名称"
            style="width: 220px"
            @keyup.enter="onCreate"
          />
          <el-input-number
            v-model="newLng"
            :precision="6"
            :controls="false"
            placeholder="经度(选填)"
            style="width: 130px"
          />
          <el-input-number
            v-model="newLat"
            :precision="6"
            :controls="false"
            placeholder="纬度(选填)"
            style="width: 130px"
          />
          <el-button type="primary" :loading="creating" :disabled="!newName.trim()" @click="onCreate">
            新增地点
          </el-button>
        </div>
      </div>

      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="常用施工地点会同步展示在工单创建、需求单转工单的施工地址下拉中，供快速选择"
        style="margin-bottom: 12px"
      />

      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column label="地点名称" prop="placeName" min-width="220" show-overflow-tooltip />
        <el-table-column label="经纬度" min-width="180">
          <template #default="{ row }">
            <span v-if="row.longitude != null && row.latitude != null">{{ row.longitude }}, {{ row.latitude }}</span>
            <span v-else class="no-coord">未维护</span>
          </template>
        </el-table-column>
        <el-table-column label="排序值" prop="sortOrder" width="90" align="center" />
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="更新时间" width="170">
          <template #default="{ row }">{{ fmtTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="danger" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination
          v-model:current-page="query.pageNum"
          :page-size="query.pageSize"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="load"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listCommonPlaces,
  createCommonPlace,
  deleteCommonPlace,
  type CommonPlace
} from '@crane/shared/api/place'
import { fmtTime } from '../../utils/format'

const keyword = ref('')
const list = ref<CommonPlace[]>([])
const total = ref(0)
const loading = ref(false)
const creating = ref(false)
const newName = ref('')
const newLng = ref<number | undefined>(undefined)
const newLat = ref<number | undefined>(undefined)
const query = ref({ pageNum: 1, pageSize: 20 })

onMounted(load)

async function load() {
  loading.value = true
  try {
    // 全量拉取后前端分页：地点数量有限（常用列表），且保留关键字过滤语义
    const all = await listCommonPlaces(keyword.value.trim() || undefined)
    total.value = all.length
    const start = (query.value.pageNum - 1) * query.value.pageSize
    list.value = all.slice(start, start + query.value.pageSize)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '地点列表加载失败')
  } finally {
    loading.value = false
  }
}

function onSearch() {
  query.value.pageNum = 1
  load()
}

function onReset() {
  keyword.value = ''
  query.value.pageNum = 1
  load()
}

async function onCreate() {
  const placeName = newName.value.trim()
  if (!placeName) return
  creating.value = true
  try {
    await createCommonPlace({
      placeName,
      longitude: newLng.value ?? undefined,
      latitude: newLat.value ?? undefined
    })
    ElMessage.success('已新增常用施工地点')
    newName.value = ''
    newLng.value = undefined
    newLat.value = undefined
    query.value.pageNum = 1
    load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '新增失败')
  } finally {
    creating.value = false
  }
}

async function onDelete(row: CommonPlace) {
  try {
    await ElMessageBox.confirm(
      `确认删除常用施工地点「${row.placeName}」？删除后下单下拉将不再展示。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await deleteCommonPlace(row.id)
    ElMessage.success('已删除')
    load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '删除失败')
  }
}
</script>

<style scoped>
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 12px;
}
.add-area {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}
.no-coord { color: var(--sub, #909399); }
</style>
