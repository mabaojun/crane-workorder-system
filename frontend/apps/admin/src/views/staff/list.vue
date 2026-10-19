<template>
  <div>
    <el-card shadow="never" class="page-card">
      <div class="toolbar">
        <el-form inline class="toolbar-form" @submit.prevent="onSearch">
          <el-form-item label="姓名">
            <el-input v-model="query.name" clearable placeholder="姓名" style="width: 150px" @clear="onSearch" />
          </el-form-item>
          <el-form-item label="手机号">
            <el-input v-model="query.phone" clearable placeholder="手机号" style="width: 160px" @clear="onSearch" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="onSearch">查询</el-button>
            <el-button @click="onReset">重置</el-button>
          </el-form-item>
        </el-form>
        <el-button type="primary" @click="openForm()">新增员工</el-button>
      </div>
      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column label="工号" prop="staffNo" width="160" />
        <el-table-column label="姓名" prop="name" width="100" />
        <el-table-column label="手机号" prop="phone" width="130" />
        <el-table-column label="岗位" width="90">
          <template #default="{ row }">{{ POSITION_LABEL[row.position] || row.position }}</template>
        </el-table-column>
        <el-table-column label="角色" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.adminRole" type="danger" effect="plain" size="small">管理员</el-tag>
            <span v-else class="muted">普通</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <status-tag kind="staff" :status="row.status" />
          </template>
        </el-table-column>
        <el-table-column label="驾驶证" width="90">
          <template #default="{ row }">{{ row.licenseType || '-' }}</template>
        </el-table-column>
        <el-table-column label="特种作业证" width="140">
          <template #default="{ row }">{{ row.specialCertNo || '-' }}</template>
        </el-table-column>
        <el-table-column label="入职日期" width="110">
          <template #default="{ row }">{{ fmtDate(row.hireDate) }}</template>
        </el-table-column>
        <el-table-column label="账号绑定" width="100">
          <template #default="{ row }">{{ row.userId ? '已绑定' : '未绑定' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="openForm(row)">编辑</el-button>
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

    <!-- 新增 / 编辑弹窗 -->
    <el-dialog
      v-model="formVisible"
      :title="editing ? '编辑员工' : '新增员工'"
      width="560px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="姓名" prop="name">
          <el-input v-model="form.name" maxlength="64" placeholder="姓名" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" maxlength="20" placeholder="手机号" />
        </el-form-item>
        <el-form-item label="身份证号" prop="idCard">
          <el-input v-model="form.idCard" maxlength="64" placeholder="身份证号（可选）" />
        </el-form-item>
        <el-form-item label="岗位" prop="position">
          <el-radio-group v-model="form.position">
            <el-radio value="DRIVER">司机</el-radio>
            <el-radio value="EMPLOYEE">员工</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="管理员权限">
          <el-switch v-model="form.adminRole" />
          <span class="form-tip">开启后可登录移动办公台；司机/员工可一人多角色，登录后选择身份</span>
        </el-form-item>
        <el-form-item label="驾驶证类型">
          <el-input v-model="form.licenseType" maxlength="16" placeholder="如 A2、B2（司机建议填写）" />
        </el-form-item>
        <el-form-item label="特种作业证号">
          <el-input v-model="form.specialCertNo" maxlength="64" placeholder="吊车操作证编号（司机建议填写）" />
        </el-form-item>
        <el-form-item label="入职日期">
          <el-date-picker
            v-model="form.hireDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="入职日期"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item v-if="!editing" label="">
          <el-checkbox v-model="form.createAccount">同时创建登录账号（司机角色，待绑定状态）</el-checkbox>
        </el-form-item>
        <el-form-item
          v-if="(!editing && form.createAccount) || (form.adminRole && !editing?.userId)"
          label="初始密码"
          prop="initialPassword"
        >
          <el-input
            v-model="form.initialPassword"
            type="password"
            show-password
            maxlength="64"
            placeholder="选填，至少 6 位；留空则由系统默认生成"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="255" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  pageStaffs,
  createStaff,
  updateStaff,
  deleteStaff,
  type Staff
} from '@crane/shared/api/staff'
import { POSITION_LABEL } from '../../utils/tags'
import { fmtDate } from '../../utils/format'
import StatusTag from '../../components/StatusTag.vue'

const query = reactive({ name: '', phone: '', pageNum: 1, pageSize: 20 })
const list = ref<Staff[]>([])
const total = ref(0)
const loading = ref(false)

const formVisible = ref(false)
const submitting = ref(false)
const editing = ref<Staff | null>(null)
const formRef = ref<FormInstance>()
const form = reactive({
  name: '',
  phone: '',
  idCard: '',
  position: 'DRIVER',
  licenseType: '',
  specialCertNo: '',
  hireDate: '',
  createAccount: true,
  adminRole: false,
  initialPassword: '',
  remark: ''
})

const rules: FormRules = {
  name: [{ required: true, message: '请填写姓名', trigger: 'blur' }],
  phone: [
    { required: true, message: '请填写手机号', trigger: 'blur' },
    { pattern: /^1\d{10}$/, message: '手机号格式不正确', trigger: 'blur' }
  ],
  position: [{ required: true, message: '请选择岗位', trigger: 'change' }],
  initialPassword: [
    {
      validator: (_r, value: string, callback) => {
        if (value && value.length < 6) callback(new Error('初始密码至少 6 位'))
        else callback()
      },
      trigger: 'blur'
    }
  ]
}

onMounted(load)

async function load() {
  loading.value = true
  try {
    const page = await pageStaffs({
      name: query.name || undefined,
      phone: query.phone || undefined,
      pageNum: query.pageNum,
      pageSize: query.pageSize
    })
    list.value = page.list
    total.value = page.total
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '员工加载失败')
  } finally {
    loading.value = false
  }
}

function onSearch() {
  query.pageNum = 1
  load()
}

function onReset() {
  query.name = ''
  query.phone = ''
  query.pageNum = 1
  load()
}

function openForm(row?: Staff) {
  editing.value = row ?? null
  form.name = row?.name ?? ''
  form.phone = row?.phone ?? ''
  form.idCard = row?.idCard ?? ''
  form.position = row?.position ?? 'DRIVER'
  form.licenseType = row?.licenseType ?? ''
  form.specialCertNo = row?.specialCertNo ?? ''
  form.hireDate = row?.hireDate ?? ''
  form.createAccount = true
  form.adminRole = row?.adminRole === true
  form.initialPassword = ''
  form.remark = row?.remark ?? ''
  formVisible.value = true
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  const body = {
    name: form.name.trim(),
    phone: form.phone.trim(),
    idCard: form.idCard.trim() || undefined,
    position: form.position,
    licenseType: form.licenseType.trim() || undefined,
    specialCertNo: form.specialCertNo.trim() || undefined,
    hireDate: form.hireDate || undefined,
    createAccount: form.createAccount,
    adminRole: form.adminRole,
    initialPassword:
      (form.createAccount || form.adminRole) && form.initialPassword
        ? form.initialPassword
        : undefined,
    remark: form.remark.trim() || undefined
  }
  try {
    if (editing.value) {
      await updateStaff(editing.value.id, body)
      ElMessage.success('保存成功')
    } else {
      await createStaff(body)
      ElMessage.success(form.createAccount ? '已创建员工并开通登录账号' : '已创建员工')
    }
    formVisible.value = false
    load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    submitting.value = false
  }
}

async function onDelete(row: Staff) {
  try {
    await ElMessageBox.confirm(
      `确认删除员工「${row.name}」？删除后不可恢复。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await deleteStaff(row.id)
    ElMessage.success('已删除')
    load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '删除失败')
  }
}
</script>

<style scoped>
.muted {
  color: #94a3b8;
  font-size: 12px;
}
.form-tip {
  margin-left: 10px;
  font-size: 12px;
  color: #94a3b8;
}
</style>
