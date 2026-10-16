<template>
  <div class="page-container">
    <div class="page-title">调度策略管理</div>
    <el-card>
      <div class="toolbar">
        <el-select v-model="query.strategyType" placeholder="策略类型" clearable style="width: 180px" @change="load">
          <el-option v-for="(v, k) in typeMap" :key="k" :label="v" :value="k" />
        </el-select>
        <el-button type="primary" @click="load">查询</el-button>
        <el-button type="success" @click="openAdd">新增策略</el-button>
      </div>

      <el-collapse class="param-help">
        <el-collapse-item name="1">
          <template #title>
            <el-icon class="help-icon"><QuestionFilled /></el-icon>
            <span class="help-title">参数(JSON) 变量说明</span>
          </template>
          <div class="param-help-grid">
            <div v-for="item in paramHelp" :key="item.key" class="param-help-item">
              <code class="param-key">{{ item.key }}</code>
              <span class="param-label">{{ item.label }}</span>
              <span class="param-example">示例：{{ item.example }}</span>
            </div>
          </div>
        </el-collapse-item>
      </el-collapse>

      <el-table :data="list" stripe v-loading="loading" style="margin-top: 12px">
        <el-table-column prop="name" label="策略名称" />
        <el-table-column prop="strategyType" label="类型" width="130">
          <template #default="{ row }">
            <el-tag>{{ typeMap[row.strategyType] || row.strategyType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="params" label="参数(JSON)" min-width="260" show-overflow-tooltip />
        <el-table-column prop="priority" label="优先级" width="90" />
        <el-table-column prop="enabled" label="状态" width="100">
          <template #default="{ row }">
            <el-switch
              :model-value="row.enabled === 1"
              @change="(val) => toggle(row, val)"
            />
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

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑策略' : '新增策略'" width="520px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="策略名称" required>
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="策略类型" required>
          <el-select v-model="form.strategyType" style="width: 100%">
            <el-option v-for="(v, k) in typeMap" :key="k" :label="v" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="参数(JSON)">
          <el-input v-model="form.params" type="textarea" :rows="4" placeholder='例如 {"reserveSoc":30}' />
          <div class="param-tip">
            <div class="param-tip-title">变量说明：</div>
            <div v-for="item in paramHelp" :key="item.key" class="param-tip-item">
              <code class="param-key">{{ item.key }}</code>
              <span class="param-label">{{ item.label }}</span>
              <span class="param-example">（示例：{{ item.example }}）</span>
            </div>
          </div>
        </el-form-item>
        <el-form-item label="优先级">
          <el-input-number v-model="form.priority" :min="0" />
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

const typeMap = {
  PLAN: '计划曲线',
  PEAK_VALLEY: '削峰填谷',
  SELF_USE: '自发自用',
  DEMAND: '需量控制'
}

// 参数(JSON) 变量说明：key = JSON 里的变量名, label = 中文含义, example = 示例值
const paramHelp = [
  { key: 'peakCharge', label: '峰时电价（元/kWh），用于计算峰时放电收益', example: '0.95' },
  { key: 'valleyCharge', label: '谷时电价（元/kWh），用于计算谷时充电成本', example: '0.35' },
  { key: 'peakDischarge', label: '峰时是否允许放电（true 放电 / false 不放电）', example: 'true' },
  { key: 'timeWindow', label: '峰时放电时间窗口（HH:mm-HH:mm）', example: '"19:00-22:00"' },
  { key: 'peakPeriod', label: '峰时段（HH:mm-HH:mm）', example: '"08:00-12:00"' },
  { key: 'valleyPeriod', label: '谷时段（HH:mm-HH:mm）', example: '"00:00-06:00"' },
  { key: 'reserveSoc', label: '保留 SOC 下限（%），低于此值不再放电', example: '30' }
]
const list = ref([])
const total = ref(0)
const loading = ref(false)
const dialogVisible = ref(false)

const query = reactive({ current: 1, size: 10, strategyType: '' })
const form = reactive({ id: null, name: '', strategyType: 'PEAK_VALLEY', params: '', priority: 0 })

async function load() {
  loading.value = true
  try {
    const res = await request.get('/strategy/page', { params: query })
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
  Object.assign(form, { id: null, name: '', strategyType: 'PEAK_VALLEY', params: '', priority: 0 })
  dialogVisible.value = true
}

function openEdit(row) {
  Object.assign(form, row)
  dialogVisible.value = true
}

async function save() {
  if (!form.name) {
    ElMessage.warning('请填写策略名称')
    return
  }
  if (form.id) {
    await request.put('/strategy', form)
  } else {
    await request.post('/strategy', form)
  }
  ElMessage.success('保存成功')
  dialogVisible.value = false
  load()
}

async function toggle(row, val) {
  await request.put(`/strategy/toggle/${row.id}`, null, { params: { enabled: val ? 1 : 0 } })
  row.enabled = val ? 1 : 0
  ElMessage.success(val ? '已启用' : '已停用')
}

async function del(row) {
  await ElMessageBox.confirm(`确认删除策略「${row.name}」？`, '提示', { type: 'warning' })
  await request.delete(`/strategy/${row.id}`)
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

.param-help {
  margin-top: 12px;
  border-top: 1px solid #ebeef5;
}

.help-icon {
  margin-right: 6px;
  color: #409eff;
  vertical-align: middle;
}

.help-title {
  font-weight: 600;
  color: #303133;
}

.param-help-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 8px 20px;
}

.param-help-item {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 6px;
  padding: 6px 8px;
  border-radius: 4px;
  background: #f5f7fa;
  line-height: 1.6;
}

.param-key {
  flex-shrink: 0;
  padding: 1px 6px;
  border-radius: 3px;
  background: #e6f1fc;
  color: #1d6fbe;
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
}

.param-label {
  color: #303133;
  font-size: 13px;
}

.param-example {
  color: #909399;
  font-size: 12px;
}

.param-tip {
  margin-top: 8px;
  padding: 8px 10px;
  border-radius: 4px;
  background: #f5f7fa;
  width: 100%;
}

.param-tip-title {
  color: #909399;
  font-size: 12px;
  margin-bottom: 4px;
}

.param-tip-item {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 4px;
  color: #606266;
  font-size: 12px;
  line-height: 1.8;
}
</style>
