<template>
  <div class="page-container">
    <div class="page-title">设备管理</div>
    <el-card>
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="搜索设备名称/编号" clearable style="width: 220px" @keyup.enter="load" />
        <el-select v-model="query.deviceType" placeholder="设备类型" clearable style="width: 160px" @change="load">
          <el-option v-for="(v, k) in typeMap" :key="k" :label="v" :value="k" />
        </el-select>
        <el-button type="primary" @click="load">查询</el-button>
        <el-button type="success" @click="openAdd">新增设备</el-button>
      </div>

      <el-table :data="list" stripe v-loading="loading" style="margin-top: 12px">
        <el-table-column prop="deviceCode" label="设备编号" width="130" />
        <el-table-column prop="deviceName" label="设备名称" />
        <el-table-column prop="deviceType" label="类型" width="100">
          <template #default="{ row }">
            <el-tag>{{ typeMap[row.deviceType] || row.deviceType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="model" label="型号" width="120" />
        <el-table-column prop="ratedPower" label="额定功率(kW)" width="120" />
        <el-table-column prop="capacity" label="容量(kWh)" width="110" />
        <el-table-column prop="commProtocol" label="通讯协议" width="130" />
        <el-table-column prop="commAddress" label="通讯地址" width="140" />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? '在线' : '离线' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="del(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        style="margin-top: 12px; justify-content: flex-end"
        layout="total, prev, pager, next"
        :total="total"
        :current-page="query.current"
        :page-size="query.size"
        @current-change="handlePageChange"
      />
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑设备' : '新增设备'" width="520px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="设备编号" required>
          <el-input v-model="form.deviceCode" />
        </el-form-item>
        <el-form-item label="设备名称" required>
          <el-input v-model="form.deviceName" />
        </el-form-item>
        <el-form-item label="设备类型" required>
          <el-select v-model="form.deviceType" style="width: 100%">
            <el-option v-for="(v, k) in typeMap" :key="k" :label="v" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="型号">
          <el-input v-model="form.model" />
        </el-form-item>
        <el-form-item label="额定功率">
          <el-input-number v-model="form.ratedPower" :min="0" />
        </el-form-item>
        <el-form-item label="容量">
          <el-input-number v-model="form.capacity" :min="0" />
        </el-form-item>
        <el-form-item label="通讯协议">
          <el-input v-model="form.commProtocol" placeholder="MODBUS_TCP" />
        </el-form-item>
        <el-form-item label="通讯地址">
          <el-input v-model="form.commAddress" placeholder="192.168.1.100" />
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

const typeMap = { PV: '光伏', PCS: '储能', METER: '电表', LOAD: '负载', GRID: '电网', EV: '充电桩', SAFETY: '安全检测' }
const list = ref([])
const total = ref(0)
const loading = ref(false)
const dialogVisible = ref(false)

const query = reactive({ current: 1, size: 10, keyword: '', deviceType: '' })
const form = reactive({
  id: null, deviceCode: '', deviceName: '', deviceType: 'PV', model: '',
  ratedPower: 0, capacity: 0, commProtocol: 'MODBUS_TCP', commAddress: ''
})

async function load() {
  loading.value = true
  try {
    const res = await request.get('/device/page', { params: query })
    list.value = res.data.records || []
    total.value = res.data.total || 0
  } finally {
    loading.value = false
  }
}

function handlePageChange(p) {
  query.current = p
  load()
}

function openAdd() {
  Object.assign(form, { id: null, deviceCode: '', deviceName: '', deviceType: 'PV', model: '', ratedPower: 0, capacity: 0, commProtocol: 'MODBUS_TCP', commAddress: '' })
  dialogVisible.value = true
}

function openEdit(row) {
  Object.assign(form, row)
  dialogVisible.value = true
}

async function save() {
  if (!form.deviceCode || !form.deviceName) {
    ElMessage.warning('请填写设备编号和名称')
    return
  }
  if (form.id) {
    await request.put('/device', form)
    ElMessage.success('修改成功')
  } else {
    await request.post('/device', form)
    ElMessage.success('新增成功')
  }
  dialogVisible.value = false
  load()
}

async function del(row) {
  await ElMessageBox.confirm(`确认删除设备「${row.deviceName}」？`, '提示', { type: 'warning' })
  await request.delete(`/device/${row.id}`)
  ElMessage.success('删除成功')
  load()
}

onMounted(load)
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}
</style>