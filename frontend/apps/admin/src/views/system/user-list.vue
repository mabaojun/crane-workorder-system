<template>
  <div>
    <el-card shadow="never" class="page-card">
      <div class="toolbar">
        <el-form inline class="toolbar-form" @submit.prevent="onSearch">
          <el-form-item label="角色">
            <el-select v-model="query.roleCode" clearable placeholder="全部" style="width: 140px" @change="onSearch">
              <el-option label="管理员" value="ADMIN" />
              <el-option label="司机" value="DRIVER" />
              <el-option label="客户" value="CUSTOMER" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="onSearch">查询</el-button>
            <el-button @click="onReset">重置</el-button>
          </el-form-item>
        </el-form>
      </div>
      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column label="账号ID" prop="id" width="80" />
        <el-table-column label="手机号" prop="phone" width="130" />
        <el-table-column label="昵称" min-width="140">
          <template #default="{ row }">{{ row.nickName || '-' }}</template>
        </el-table-column>
        <el-table-column label="角色" width="100">
          <template #default="{ row }">{{ roleLabel(row.roleId) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <status-tag kind="user" :status="row.status" />
          </template>
        </el-table-column>
        <el-table-column label="绑定状态" width="110">
          <template #default="{ row }">{{ row.status === 'PENDING_BIND' ? '待绑定' : row.boundAt ? '已绑定' : '未绑定微信' }}</template>
        </el-table-column>
        <el-table-column label="绑定员工/客户" width="130">
          <template #default="{ row }">{{ row.staffId ? `员工${row.staffId}` : row.customerId ? `客户${row.customerId}` : '-' }}</template>
        </el-table-column>
        <el-table-column label="最近登录" width="140">
          <template #default="{ row }">{{ fmtTime(row.lastLoginAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="openResetPwd(row)">重置密码</el-button>
            <el-button
              v-if="row.status === 'ACTIVE' || row.status === 'PENDING_BIND'"
              size="small"
              text
              type="warning"
              @click="changeStatus(row, 'FROZEN')"
            >冻结</el-button>
            <el-button
              v-if="row.status === 'FROZEN'"
              size="small"
              text
              type="success"
              @click="changeStatus(row, 'ACTIVE')"
            >解冻</el-button>
            <el-button
              v-if="row.status !== 'DISABLED'"
              size="small"
              text
              type="danger"
              @click="changeStatus(row, 'DISABLED')"
            >停用</el-button>
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

    <!-- 重置密码 -->
    <el-dialog v-model="pwdVisible" title="重置密码" width="420px" :close-on-click-modal="false">
      <el-form label-width="90px">
        <el-form-item label="账号">{{ pwdTarget?.phone }}</el-form-item>
        <el-form-item label="新密码" required>
          <el-input v-model="newPwd" type="password" show-password maxlength="32" placeholder="至少 8 位" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdVisible = false">取消</el-button>
        <el-button type="primary" :loading="pwdSubmitting" @click="submitResetPwd">确认重置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pageUsers, resetUserPassword, updateUserStatus, type SysUser } from '@crane/shared/api/iam'
import { ROLE_LABEL } from '../../utils/tags'
import { fmtTime } from '../../utils/format'
import StatusTag from '../../components/StatusTag.vue'

const query = reactive({ roleCode: '', pageNum: 1, pageSize: 20 })
const list = ref<SysUser[]>([])
const total = ref(0)
const loading = ref(false)

const pwdVisible = ref(false)
const pwdSubmitting = ref(false)
const pwdTarget = ref<SysUser | null>(null)
const newPwd = ref('')

onMounted(load)

function roleLabel(roleId: number) {
  const map: Record<number, string> = { 1: ROLE_LABEL.ADMIN, 2: ROLE_LABEL.DRIVER, 3: ROLE_LABEL.CUSTOMER }
  return map[roleId] || `角色${roleId}`
}

async function load() {
  loading.value = true
  try {
    const page = await pageUsers({
      roleCode: query.roleCode || undefined,
      pageNum: query.pageNum,
      pageSize: query.pageSize
    })
    list.value = page.list
    total.value = page.total
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '账号加载失败')
  } finally {
    loading.value = false
  }
}

function onSearch() {
  query.pageNum = 1
  load()
}

function onReset() {
  query.roleCode = ''
  query.pageNum = 1
  load()
}

// ==================== 重置密码 ====================
function openResetPwd(row: SysUser) {
  pwdTarget.value = row
  newPwd.value = ''
  pwdVisible.value = true
}

async function submitResetPwd() {
  if (!pwdTarget.value) return
  if (!newPwd.value || newPwd.value.length < 8) {
    ElMessage.warning('新密码至少 8 位')
    return
  }
  pwdSubmitting.value = true
  try {
    await resetUserPassword(pwdTarget.value.id, newPwd.value)
    ElMessage.success('密码已重置')
    pwdVisible.value = false
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '重置失败')
  } finally {
    pwdSubmitting.value = false
  }
}

// ==================== 状态变更 ====================
async function changeStatus(row: SysUser, status: 'ACTIVE' | 'FROZEN' | 'DISABLED') {
  const label = { ACTIVE: '解冻', FROZEN: '冻结', DISABLED: '停用' }[status]
  try {
    await ElMessageBox.confirm(
      `确认${label}账号「${row.phone}」？${status === 'ACTIVE' ? '' : '该账号将被强制下线。'}`,
      `${label}确认`,
      { type: 'warning', confirmButtonText: '确认', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await updateUserStatus(row.id, status)
    ElMessage.success(`${label}成功`)
    load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '操作失败')
  }
}
</script>
