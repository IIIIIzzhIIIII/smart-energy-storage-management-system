<template>
  <div class="page-container">
    <div class="page-title">统计报表</div>

    <!-- 指标卡片 -->
    <el-row :gutter="16">
      <el-col :span="8" v-for="item in cards" :key="item.label">
        <el-card class="stat-card">
          <div class="stat-label">{{ item.label }}</div>
          <div class="stat-value" :style="{ color: item.color }">
            {{ item.value }} <span class="stat-unit">{{ item.unit }}</span>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 近7天趋势 -->
    <el-card style="margin-top: 16px">
      <div class="card-header">
        <span class="page-title" style="margin: 0">近7天能源趋势</span>
        <el-date-picker
          v-model="selectedDate"
          type="month"
          placeholder="选择月份"
          value-format="YYYY-MM"
          @change="loadMonthReport"
        />
      </div>
      <div ref="trendChartRef" class="trend-chart"></div>
    </el-card>

    <!-- 月度明细 -->
    <el-card style="margin-top: 16px">
      <div class="page-title" style="margin-top: 0">月度统计明细</div>
      <el-descriptions :column="3" border>
        <el-descriptions-item label="光伏发电量(kWh)">{{ monthReport.generation }}</el-descriptions-item>
        <el-descriptions-item label="负载用电量(kWh)">{{ monthReport.consumption }}</el-descriptions-item>
        <el-descriptions-item label="储能充电量(kWh)">{{ monthReport.charge }}</el-descriptions-item>
        <el-descriptions-item label="储能放电量(kWh)">{{ monthReport.discharge }}</el-descriptions-item>
        <el-descriptions-item label="购电量(kWh)">{{ monthReport.gridImport }}</el-descriptions-item>
        <el-descriptions-item label="上网电量(kWh)">{{ monthReport.gridExport }}</el-descriptions-item>
        <el-descriptions-item label="充电桩充电量(kWh)">{{ monthReport.evCharging }}</el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import request from '../utils/request'

const cards = reactive([
  { label: '今日发电量', value: '--', unit: 'kWh', color: '#f5a623', key: 'generation' },
  { label: '今日用电量', value: '--', unit: 'kWh', color: '#ff7043', key: 'consumption' },
  { label: '今日收益', value: '--', unit: '元', color: '#66bb6a', key: 'revenue' }
])

function formatLocal(date) {
  const pad = n => (n < 10 ? '0' + n : n)
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}
function formatLocalMonth(date) {
  const pad = n => (n < 10 ? '0' + n : n)
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}`
}

const selectedDate = ref(formatLocalMonth(new Date()))
const monthReport = reactive({
  generation: '-', consumption: '-', charge: '-',
  discharge: '-', gridImport: '-', gridExport: '-', evCharging: '-'
})
const trendChartRef = ref()
let trendChart = null

async function loadToday() {
  const today = formatLocal(new Date())
  const res = await request.get('/report/day', { params: { date: today } })
  const data = res.data
  cards[0].value = data.generation
  cards[1].value = data.consumption
  cards[2].value = data.revenue
}

async function loadTrend() {
  const res = await request.get('/report/last7days')
  const list = res.data || []
  const dates = list.map(d => d.date)
  const generation = list.map(d => d.generation)
  const consumption = list.map(d => d.consumption)
  trendChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['发电量', '用电量'], top: 0, itemWidth: 14, itemHeight: 10, textStyle: { color: '#aab4c8', fontSize: 12 } },
    grid: { left: 55, right: 20, top: 42, bottom: 45, containLabel: true },
    xAxis: {
      type: 'category',
      data: dates,
      axisLabel: { color: '#aab4c8' },
      axisLine: { lineStyle: { color: '#2a3550' } }
    },
    yAxis: {
      type: 'value',
      name: 'kWh',
      nameTextStyle: { color: '#7a879e' },
      axisLabel: { color: '#aab4c8' },
      splitLine: { lineStyle: { color: '#1c2740' } }
    },
    series: [
      { name: '发电量', type: 'bar', data: generation, itemStyle: { color: '#f5a623' } },
      { name: '用电量', type: 'bar', data: consumption, itemStyle: { color: '#00b4d8' } }
    ]
  })
}

async function loadMonthReport() {
  const res = await request.get('/report/month', { params: { month: selectedDate.value } })
  Object.assign(monthReport, res.data)
}

onMounted(async () => {
  await nextTick()
  trendChart = echarts.init(trendChartRef.value)
  await Promise.all([loadToday(), loadTrend(), loadMonthReport()])
})
</script>

<style scoped>
.stat-card {
  text-align: center;
}
.stat-label {
  color: #8492a6;
  font-size: 14px;
}
.stat-value {
  font-size: 30px;
  font-weight: 700;
  margin-top: 10px;
}
.stat-unit {
  font-size: 14px;
  font-weight: 400;
}
.trend-chart {
  height: 320px;
  margin-top: 12px;
}
</style>