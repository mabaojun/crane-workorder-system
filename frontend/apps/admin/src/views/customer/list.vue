<template>
  <div>
    <el-card shadow="never" class="page-card">
      <div class="toolbar">
        <el-form inline class="toolbar-form" @submit.prevent="onSearch">
          <el-form-item label="客户名称">
            <el-input v-model="query.name" clearable placeholder="客户名称" style="width: 180px" @clear="onSearch" />
          </el-form-item>
          <el-form-item label="电话">
            <el-input v-model="query.phone" clearable placeholder="电话" style="width: 160px" @clear="onSearch" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="onSearch">查询</el-button>
            <el-button @click="onReset">重置</el-button>
          </el-form-item>
        </el-form>
        <el-button type="primary" @click="openForm()">新增客户</el-button>
      </div>
      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column label="客户编号" prop="customerNo" width="160" />
        <el-table-column label="客户名称" prop="name" min-width="180" show-overflow-tooltip />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">{{ row.type === 'COMPANY' ? '公司' : '个人' }}</template>
        </el-table-column>
        <el-table-column label="电话" prop="phone" width="130" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">{{ row.status === 'ACTIVE' ? '正常' : '停用' }}</template>
        </el-table-column>
        <el-table-column label="账号绑定" width="100">
          <template #default="{ row }">{{ row.userId ? '已绑定' : '未绑定' }}</template>
        </el-table-column>
        <el-table-column label="创建时间" width="140">
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="openContacts(row)">联系人</el-button>
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

    <!-- 新增 / 编辑客户 -->
    <el-dialog
      v-model="formVisible"
      :title="editing ? '编辑客户' : '新增客户'"
      width="560px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-form-item label="客户类型" prop="type">
          <el-radio-group v-model="form.type" :disabled="!!editing">
            <el-radio value="COMPANY">公司</el-radio>
            <el-radio value="PERSON">个人</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="客户名称" prop="name">
          <el-input v-model="form.name" maxlength="128" placeholder="公司名称或个人姓名" />
        </el-form-item>
        <el-form-item v-if="form.type === 'COMPANY'" label="统一社会信用代码" prop="creditCode">
          <el-input v-model="form.creditCode" maxlength="64" placeholder="公司客户必填" />
        </el-form-item>
        <el-form-item label="客户电话" prop="phone">
          <el-input v-model="form.phone" maxlength="20" placeholder="客户总机 / 主号码" />
        </el-form-item>
        <el-form-item label="所属行业">
          <el-input v-model="form.industry" maxlength="32" placeholder="吊装、供暖配套、其他" />
        </el-form-item>
        <el-form-item label="联系地址">
          <el-input v-model="form.address" maxlength="255" />
        </el-form-item>
        <el-form-item v-if="!editing" label="">
          <el-checkbox v-model="form.createAccount">同时创建客户登录账号（待绑定状态）</el-checkbox>
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

    <!-- 联系人抽屉 -->
    <el-drawer v-model="contactsVisible" :title="`联系人管理 - ${contactCustomer?.name || ''}`" size="560px">
      <div class="contacts-toolbar">
        <el-button type="primary" size="small" @click="openContactForm()">新增联系人</el-button>
      </div>
      <el-table v-loading="contactsLoading" :data="contacts" border size="small">
        <el-table-column label="姓名" width="100">
          <template #default="{ row }">
            <span>{{ row.name }}</span>
            <el-icon v-if="row.isDefault === 1" title="默认联系人"><star-filled /></el-icon>
          </template>
        </el-table-column>
        <el-table-column label="手机号" prop="phone" width="130" />
        <el-table-column label="职务" width="90">
          <template #default="{ row }">{{ row.position || '-' }}</template>
        </el-table-column>
        <el-table-column label="邮箱" min-width="140">
          <template #default="{ row }">{{ row.email || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="openContactForm(row)">编辑</el-button>
            <el-button
              v-if="row.isDefault !== 1"
              size="small"
              text
              type="warning"
              @click="setDefault(row)"
            >设默认</el-button>
            <el-button size="small" text type="danger" @click="deleteContactRow(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 联系人新增 / 编辑 -->
      <el-dialog
        v-model="contactFormVisible"
        :title="contactEditing ? '编辑联系人' : '新增联系人'"
        width="440px"
        :close-on-click-modal="false"
        append-to-body
      >
        <el-form ref="contactFormRef" :model="contactForm" :rules="contactRules" label-width="90px">
          <el-form-item label="姓名" prop="name">
            <el-input v-model="contactForm.name" maxlength="64" />
          </el-form-item>
          <el-form-item label="手机号" prop="phone">
            <el-input v-model="contactForm.phone" maxlength="20" />
          </el-form-item>
          <el-form-item label="职务">
            <el-input v-model="contactForm.position" maxlength="64" />
          </el-form-item>
          <el-form-item label="邮箱">
            <el-input v-model="contactForm.email" maxlength="128" />
          </el-form-item>
          <el-form-item label="设为默认">
            <el-switch v-model="contactForm.isDefault" :active-value="1" :inactive-value="0" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="contactFormVisible = false">取消</el-button>
          <el-button type="primary" :loading="contactSubmitting" @click="submitContact">保存</el-button>
        </template>
      </el-dialog>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { StarFilled } from '@element-plus/icons-vue'
import {
  pageCustomers,
  createCustomer,
  updateCustomer,
  deleteCustomer,
  listContacts,
  createContact,
  updateContact,
  deleteContact,
  type Customer,
  type CustomerContact
} from '@crane/shared/api/customer'
import { fmtTime } from '../../utils/format'

const query = reactive({ name: '', phone: '', pageNum: 1, pageSize: 20 })
const list = ref<Customer[]>([])
const total = ref(0)
const loading = ref(false)

const formVisible = ref(false)
const submitting = ref(false)
const editing = ref<Customer | null>(null)
const formRef = ref<FormInstance>()
const form = reactive({
  type: 'COMPANY',
  name: '',
  creditCode: '',
  phone: '',
  industry: '',
  address: '',
  createAccount: true,
  remark: ''
})

const rules: FormRules = {
  type: [{ required: true, message: '请选择客户类型', trigger: 'change' }],
  name: [{ required: true, message: '请填写客户名称', trigger: 'blur' }],
  phone: [
    { required: true, message: '请填写客户电话', trigger: 'blur' },
    { pattern: /^[\d\-+]{6,20}$/, message: '电话格式不正确', trigger: 'blur' }
  ],
  creditCode: [
    {
      validator: (_r, value: string, callback) => {
        if (form.type === 'COMPANY' && !value?.trim()) callback(new Error('公司客户必须填写统一社会信用代码'))
        else callback()
      },
      trigger: 'blur'
    }
  ]
}

// 联系人抽屉
const contactsVisible = ref(false)
const contactsLoading = ref(false)
const contactCustomer = ref<Customer | null>(null)
const contacts = ref<CustomerContact[]>([])
const contactFormVisible = ref(false)
const contactSubmitting = ref(false)
const contactEditing = ref<CustomerContact | null>(null)
const contactFormRef = ref<FormInstance>()
const contactForm = reactive({
  name: '',
  phone: '',
  position: '',
  email: '',
  isDefault: 0
})
const contactRules: FormRules = {
  name: [{ required: true, message: '请填写姓名', trigger: 'blur' }],
  phone: [
    { required: true, message: '请填写手机号', trigger: 'blur' },
    { pattern: /^1\d{10}$/, message: '手机号格式不正确', trigger: 'blur' }
  ]
}

onMounted(load)

async function load() {
  loading.value = true
  try {
    const page = await pageCustomers({
      name: query.name || undefined,
      phone: query.phone || undefined,
      pageNum: query.pageNum,
      pageSize: query.pageSize
    })
    list.value = page.list
    total.value = page.total
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '客户加载失败')
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

function openForm(row?: Customer) {
  editing.value = row ?? null
  form.type = row?.type ?? 'COMPANY'
  form.name = row?.name ?? ''
  form.creditCode = row?.creditCode ?? ''
  form.phone = row?.phone ?? ''
  form.industry = row?.industry ?? ''
  form.address = row?.address ?? ''
  form.createAccount = true
  form.remark = row?.remark ?? ''
  formVisible.value = true
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  const body = {
    type: form.type,
    name: form.name.trim(),
    creditCode: form.type === 'COMPANY' ? form.creditCode.trim() || undefined : undefined,
    phone: form.phone.trim(),
    industry: form.industry.trim() || undefined,
    address: form.address.trim() || undefined,
    createAccount: form.createAccount,
    remark: form.remark.trim() || undefined
  }
  try {
    if (editing.value) {
      await updateCustomer(editing.value.id, body)
      ElMessage.success('保存成功')
    } else {
      await createCustomer(body)
      ElMessage.success('已创建客户')
    }
    formVisible.value = false
    load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    submitting.value = false
  }
}

async function onDelete(row: Customer) {
  try {
    await ElMessageBox.confirm(
      `确认删除客户「${row.name}」？删除后不可恢复。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await deleteCustomer(row.id)
    ElMessage.success('已删除')
    load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '删除失败')
  }
}

// ==================== 联系人 ====================
async function openContacts(row: Customer) {
  contactCustomer.value = row
  contactsVisible.value = true
  await loadContacts()
}

async function loadContacts() {
  if (!contactCustomer.value) return
  contactsLoading.value = true
  try {
    contacts.value = await listContacts(contactCustomer.value.id)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '联系人加载失败')
  } finally {
    contactsLoading.value = false
  }
}

function openContactForm(row?: CustomerContact) {
  contactEditing.value = row ?? null
  contactForm.name = row?.name ?? ''
  contactForm.phone = row?.phone ?? ''
  contactForm.position = row?.position ?? ''
  contactForm.email = row?.email ?? ''
  contactForm.isDefault = row?.isDefault ?? 0
  contactFormVisible.value = true
}

async function submitContact() {
  const valid = await contactFormRef.value?.validate().catch(() => false)
  if (!valid || !contactCustomer.value) return
  contactSubmitting.value = true
  const body = {
    customerId: contactCustomer.value.id,
    name: contactForm.name.trim(),
    phone: contactForm.phone.trim(),
    position: contactForm.position.trim() || undefined,
    email: contactForm.email.trim() || undefined,
    isDefault: contactForm.isDefault
  }
  try {
    if (contactEditing.value) {
      await updateContact(contactEditing.value.id, body)
      ElMessage.success('保存成功')
    } else {
      await createContact(body)
      ElMessage.success('已新增联系人')
    }
    contactFormVisible.value = false
    loadContacts()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    contactSubmitting.value = false
  }
}

async function setDefault(row: CustomerContact) {
  try {
    await updateContact(row.id, {
      customerId: row.customerId,
      name: row.name,
      phone: row.phone,
      position: row.position || undefined,
      email: row.email || undefined,
      isDefault: 1
    })
    ElMessage.success('已设为默认联系人')
    loadContacts()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '设置失败')
  }
}

async function deleteContactRow(row: CustomerContact) {
  try {
    await ElMessageBox.confirm(
      `确认删除联系人「${row.name}」？`,
      '删除确认',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await deleteContact(row.id)
    ElMessage.success('已删除')
    loadContacts()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '删除失败')
  }
}
</script>

<style scoped>
.contacts-toolbar { margin-bottom: 12px; }
</style>
