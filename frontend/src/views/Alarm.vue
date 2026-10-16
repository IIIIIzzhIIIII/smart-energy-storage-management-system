<template>
  <div class="page-container">
    <div class="page-title">告警管理</div>
    <el-card>
      <el-tabs v-model="activeTab">
        <!-- 告警记录 -->
        <el-tab-pane label="告警记录" name="record">
          <el-radio-group v-model="recordStatus" @change="loadRecords">
            <el-radio-button :label="undefined">全部</el-radio-button>
            <el-radio-button :label="0">未处理</el-radio-button>
            <el-radio-button :label="1">已处理</el-radio-button>
          </el-radio-group>
          <el-table :data="records" stripe v-loading="loading" style="margin-top: 12px">
            <el-table-column prop="deviceName" label="设备" width="140" />
            <el-table-column prop="metric" label="指标" width="100" />
            <el-table-column prop="value" label="触发值" width="100" />
            <el-table-column prop="threshold" label="阈值" width="140" />
            <el-table-column prop="level" label="级别" width="100">
              <template #default="{ row }">
                <el-tag :type="levelType(row.level)">{{ row.level }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="message" label="告警内容" min-width="220" show-overflow-tooltip />
            <el-table-column prop="createTime" label="产生时间" width="170" />
            <el-table-column prop="status" label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'danger'">
                  {{ row.status === 1 ? '已处理' : '未处理' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{ row }">
                <el-button v-if="row.status === 0" size="small" type="primary" @click="handleRecord(row)">处理</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            style="margin-top: 12px; justify-content: flex-end"
            layout="total, prev, pager, next"
            :total="recordTotal"
            :current-page="recordPage"
            :page-size="10"
            @current-change="handleRecordPage"
          />
        </el-tab-pane>

        <!-- 告警规则 -->
        <el-tab-pane label="告警规则" name="rule">
          <div class="toolbar">
            <el-button type="success" @click="openRuleAdd">新增规则</el-button>
          </div>
          <el-table :data="rules" stripe style="margin-top: 12px">
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column prop="deviceId" label="设备ID" width="80" />
            <el-table-column prop="metric" label="指标" width="100">
              <template #default="{ row }">
                <el-tag>{{ metricMap[row.metric] || row.metric }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="minValue" label="下限" width="90" />
            <el-table-column prop="maxValue" label="上限" width="90" />
            <el-table-column prop="level" label="级别" width="100">
              <template #default="{ row }">
                <el-tag :type="levelType(row.level)">{{ row.level }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="enabled" label="启用" width="80">
              <template #default="{ row }">{{ row.enabled === 1 ? '是' : '否' }}</template>
            </el-table-column>
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <el-button size="small" @click="openRuleEdit(row)">编辑</el-button>
                <el-button size="small" type="danger" @click="deleteRule(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            style="margin-top: 12px; justify-content: flex-end"
            layout="total, prev, pager, next"
            :total="ruleTotal"
            :current-page="rulePage"
            :page-size="10"
            @current-change="handleRulePage"
          />
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 规则编辑弹窗 -->
    <el-dialog v-model="ruleDialog" :title="ruleForm.id ? '编辑规则' : '新增规则'" width="480px">
      <el-form :model="ruleForm" label-width="90px">
        <el-form-item label="设备ID" required>
          <el-input-number v-model="ruleForm.deviceId" :min="1" />
        </el-form-item>
        <el-form-item label="指标" required>
          <el-select v-model="ruleForm.metric" style="width: 100%">
            <el-option v-for="(v, k) in metricMap" :key="k" :label="v" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="下限">
          <el-input-number v-model="ruleForm.minValue" />
        </el-form-item>
        <el-form-item label="上限">
          <el-input-number v-model="ruleForm.maxValue" />
        </el-form-item>
        <el-form-item label="级别">
          <el-select v-model="ruleForm.level" style="width: 100%">
            <el-option label="INFO" value="INFO" />
            <el-option label="WARN" value="WARN" />
            <el-option label="CRITICAL" value="CRITICAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="ruleForm.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="ruleDialog = false">取消</el-button>
        <el-button type="primary" @click="saveRule">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../utils/request'

const metricMap = { POWER: '功率', VOLTAGE: '电压', CURRENT: '电流', SOC: 'SOC', TEMP: '温度', GAS: '可燃气体', HUMIDITY: '湿度', INSULATION: '绝缘电阻', SMOKE: '烟感', DI: '硬接点', BMS: 'BMS告警' }
const activeTab = ref('record')

// 记录
const records = ref([])
const recordTotal = ref(0)
const recordPage = ref(1)
const recordStatus = ref(undefined)
const loading = ref(false)

// 规则
const rules = ref([])
const ruleTotal = ref(0)
const rulePage = ref(1)
const ruleDialog = ref(false)
const ruleForm = reactive({ id: null, deviceId: 1, metric: 'POWER', minValue: null, maxValue: null, level: 'WARN', enabled: 1 })

function levelType(level) {
  return { INFO: 'info', WARN: 'warning', CRITICAL: 'danger', EMERGENCY: 'danger' }[level] || 'info'
}

async function loadRecords() {
  loading.value = true
  try {
    const params = { current: recordPage.value, size: 10 }
    if (recordStatus.value !== undefined) params.status = recordStatus.value
    const res = await request.get('/alarm/record/page', { params })
    records.value = res.data.records || []
    recordTotal.value = res.data.total || 0
  } finally {
    loading.value = false
  }
}

function handleRecordPage(p) {
  recordPage.value = p
  loadRecords()
}

async function handleRecord(row) {
  await request.put(`/alarm/record/handle/${row.id}`)
  ElMessage.success('已处理')
  loadRecords()
}

async function loadRules() {
  const res = await request.get('/alarm/rule/page', { params: { current: rulePage.value, size: 10 } })
  rules.value = res.data.records || []
  ruleTotal.value = res.data.total || 0
}

function handleRulePage(p) {
  rulePage.value = p
  loadRules()
}

function openRuleAdd() {
  Object.assign(ruleForm, { id: null, deviceId: 1, metric: 'POWER', minValue: null, maxValue: null, level: 'WARN', enabled: 1 })
  ruleDialog.value = true
}

function openRuleEdit(row) {
  Object.assign(ruleForm, row)
  ruleDialog.value = true
}

async function saveRule() {
  if (!ruleForm.deviceId) {
    ElMessage.warning('请填写设备ID')
    return
  }
  if (ruleForm.id) {
    await request.put('/alarm/rule', ruleForm)
  } else {
    await request.post('/alarm/rule', ruleForm)
  }
  ElMessage.success('保存成功')
  ruleDialog.value = false
  loadRules()
}

async function deleteRule(row) {
  await ElMessageBox.confirm('确认删除该告警规则？', '提示', { type: 'warning' })
  await request.delete(`/alarm/rule/${row.id}`)
  ElMessage.success('删除成功')
  loadRules()
}

onMounted(() => {
  loadRecords()
  loadRules()
})
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 10px;
}
</style>