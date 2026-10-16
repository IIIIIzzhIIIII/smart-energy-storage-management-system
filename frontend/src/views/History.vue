<template>
  <div class="page-container">
    <div class="page-title">历史数据查询</div>
    <el-card>
      <div class="toolbar">
        <el-select v-model="deviceId" placeholder="选择设备" style="width: 220px" @change="loadHistory">
          <el-option v-for="d in deviceList" :key="d.id" :label="d.deviceName" :value="d.id" />
        </el-select>
        <el-date-picker
          v-model="range"
          type="datetimerange"
          range-separator="至"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          value-format="YYYY-MM-DD HH:mm:ss"
        />
        <el-button type="primary" @click="loadHistory">查询</el-button>
      </div>

      <div ref="chartRef" class="history-chart"></div>

      <el-table :data="historyList" stripe style="margin-top: 12px" max-height="300">
        <el-table-column prop="ts" label="时间" width="180" />
        <el-table-column prop="power" label="功率(kW)" />
        <el-table-column prop="voltage" label="电压(V)" />
        <el-table-column prop="current" label="电流(A)" />
        <el-table-column prop="soc" label="SOC(%)" />
        <el-table-column prop="irradiance" label="辐照度(W/m2)" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import request from '../utils/request'

const deviceList = ref([])
const deviceId = ref(null)
const range = ref([])
const historyList = ref([])
const chartRef = ref()
let chart = null

function defaultRange() {
  const end = new Date()
  const start = new Date(end.getTime() - 3600000) // 近1小时
  const pad = n => (n < 10 ? '0' + n : n)
  const fmt = d => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
  return [fmt(start), fmt(end)]
}

async function loadDevices() {
  const res = await request.get('/device/list')
  deviceList.value = res.data || []
  if (!deviceId.value && deviceList.value.length) {
    deviceId.value = deviceList.value[0].id
  }
}

async function loadHistory() {
  if (!deviceId.value) {
    return
  }
  const [start, end] = range.value && range.value.length === 2 ? range.value : defaultRange()
  const res = await request.get('/monitor/history', {
    params: { deviceId: deviceId.value, start, end }
  })
  historyList.value = res.data || []
  renderChart()
}

function renderChart() {
  const times = historyList.value.map(d => d.ts)
  const powers = historyList.value.map(d => d.power)
  chart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 55, right: 20, top: 20, bottom: 50, containLabel: true },
    xAxis: {
      type: 'category',
      data: times,
      axisLabel: { rotate: 30, color: '#aab4c8' },
      axisLine: { lineStyle: { color: '#2a3550' } }
    },
    yAxis: {
      type: 'value',
      name: 'kW',
      nameTextStyle: { color: '#7a879e' },
      axisLabel: { color: '#aab4c8' },
      splitLine: { lineStyle: { color: '#1c2740' } }
    },
    series: [{
      name: '功率',
      type: 'line',
      smooth: true,
      data: powers,
      areaStyle: { opacity: 0.2 },
      lineStyle: { color: '#00b4d8' }
    }]
  })
}

onMounted(async () => {
  await nextTick()
  chart = echarts.init(chartRef.value)
  await loadDevices()
  await loadHistory()
})
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}
.history-chart {
  height: 320px;
  margin-top: 16px;
}
</style>