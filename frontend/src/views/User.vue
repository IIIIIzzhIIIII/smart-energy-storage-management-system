<template>
  <div class="page-container">
    <div class="page-title">用户管理</div>
    <el-card>
      <div class="toolbar">
        <el-input v-model="keyword" placeholder="搜索用户名/昵称" clearable style="width: 220px" @keyup.enter="load" />
        <el-button type="primary" @click="load">查询</el-button>
        <el-button type="success" @click="openAdd">新增用户</el-button>
      </div>

      <el-table :data="list" stripe v-loading="loading" style="margin-top: 12px">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="username" label="账号" width="130" />
        <el-table-column prop="nickname" label="昵称" />
        <el-table-column prop="role" label="角色" width="110">
          <template #default="{ row }">
            <el-tag :type="roleType(row.role)">{{ roleMap[row.role] || row.role }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" type="warning" @click="resetPwd(row)">重置密码</el-button>
            <el-button size="small" :type="row.status === 1 ? 'danger' : 'success'" @click="toggleStatus(row)">
              {{ row.status === 1 ? '停用' : '启用' }}
            </el-button>
            <el-button size="small" type="danger" @click="del(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        style="margin-top: 12px; justify-content: flex-end"
        layout="total, prev, pager, next"
        :total="total"
        :current-page="page"
        :page-size="10"
        @current-change="handlePageChange"
      />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑用户' : '新增用户'" width="480px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="账号" required>
          <el-input v-model="form.username" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item :label="form.id ? '密码(留空不改)' : '密码'">
          <el-input v-model="form.password" type="password" placeholder="默认123456" />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="form.nickname" />
        </el-form-item>
        <el-form-item label="角色" required>
          <el-select v-model="form.role" style="width: 100%">
            <el-option v-for="(v, k) in roleMap" :key="k" :label="v" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../utils/request'

const roleMap = { ADMIN: '管理员', DISPATCHER: '调度员', USER: '普通用户', VIEWER: '只读用户' }
const list = ref([])
const total = ref(0)
const page = ref(1)
const keyword = ref('')
const loading = ref(false)
const dialogVisible = ref(false)

const form = reactive({ id: null, username: '', password: '', nickname: '', role: 'USER', status: 1 })

function roleType(role) {
  return { ADMIN: 'danger', DISPATCHER: 'warning', USER: 'success', VIEWER: 'info' }[role] || 'info'
}

async function load() {
  loading.value = true
  try {
    const res = await request.get('/user/page', { params: { current: page.value, size: 10, keyword: keyword.value } })
    list.value = res.data.records || []
    total.value = res.data.total || 0
  } finally {
    loading.value = false
  }
}

function handlePageChange(p) {
  page.value = p
  load()
}

function openAdd() {
  Object.assign(form, { id: null, username: '', password: '', nickname: '', role: 'USER', status: 1 })
  dialogVisible.value = true
}

function openEdit(row) {
  Object.assign(form, { id: row.id, username: row.username, password: '', nickname: row.nickname, role: row.role, status: row.status })
  dialogVisible.value = true
}

async function save() {
  if (!form.username) {
    ElMessage.warning('请填写账号')
    return
  }
  if (form.id) {
    await request.put('/user', form)
  } else {
    await request.post('/user', form)
  }
  ElMessage.success('保存成功')
  dialogVisible.value = false
  load()
}

async function resetPwd(row) {
  await ElMessageBox.confirm(`确认重置「${row.username}」的密码为 123456？`, '提示', { type: 'warning' })
  await request.put(`/user/reset/${row.id}`)
  ElMessage.success('已重置为123456')
}

async function toggleStatus(row) {
  const newStatus = row.status === 1 ? 0 : 1
  await request.put(`/user/status/${row.id}`, null, { params: { status: newStatus } })
  row.status = newStatus
  ElMessage.success('操作成功')
}

async function del(row) {
  await ElMessageBox.confirm(`确认删除用户「${row.username}」？`, '提示', { type: 'warning' })
  await request.delete(`/user/${row.id}`)
  ElMessage.success('删除成功')
  load()
}

onMounted(load)
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 10px;
}
</style>