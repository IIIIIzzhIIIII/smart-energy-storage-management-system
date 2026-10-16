<template>
  <div class="dashboard">
    <!-- 顶部：站点定位 + 实时气象 + 时间（科技感） -->
    <div class="weather-strip">
      <div class="ws-location">
        <div class="ws-label"><span class="ws-dot"></span>站点定位</div>
        <div class="ws-loc-name">{{ locationText }}</div>
        <div class="ws-coord">
          经度 {{ weather.location ? weather.location.lon : '--' }} · 纬度 {{ weather.location ? weather.location.lat : '--' }}
        </div>
      </div>
      <div class="ws-divider"></div>
      <div class="ws-temp">
        <div class="ws-temp-value">{{ weather.temperature }}<span class="ws-temp-unit">°C</span></div>
        <div class="ws-condition">{{ weather.condition }}</div>
      </div>
      <div class="ws-divider"></div>
      <div class="ws-detail">
        <div class="ws-detail-item"><i>体感</i>{{ weather.feelsLike }}°C</div>
        <div class="ws-detail-item"><i>湿度</i>{{ weather.humidity }}%</div>
        <div class="ws-detail-item"><i>风向</i>{{ weather.windDirection }}</div>
        <div class="ws-detail-item"><i>风速</i>{{ weather.windSpeed }} m/s</div>
        <div class="ws-detail-item"><i>气压</i>{{ weather.pressure }} hPa</div>
        <div class="ws-detail-item"><i>紫外线</i>{{ weather.uvIndex }}</div>
      </div>
      <div class="ws-divider"></div>
      <div class="ws-clock">
        <div class="ws-clock-time">{{ clock }}</div>
        <div class="ws-clock-date">{{ clockDate }}</div>
      </div>
    </div>

    <!-- 调度状态条：实时展示调度引擎决策 -->
    <div class="dispatch-strip">
      <div class="ds-item">
        <div class="ds-label">当前时段</div>
        <div class="ds-value" :class="periodClass">
          {{ dispatch.period.name }} <span class="ds-price">{{ dispatch.period.price }} 元/kWh</span>
        </div>
      </div>
      <div class="ds-divider"></div>
      <div class="ds-item">
        <div class="ds-label">调度策略</div>
        <div class="ds-value ds-strategy">{{ strategyText }}</div>
      </div>
      <div class="ds-divider"></div>
      <div class="ds-item">
        <div class="ds-label">PCS 状态</div>
        <div class="ds-value" :class="actionClass">{{ actionText }}</div>
        <div class="ds-sub">目标功率 {{ dispatch.targetPower != null ? dispatch.targetPower : '--' }} kW</div>
      </div>
      <div class="ds-divider"></div>
      <div class="ds-item">
        <div class="ds-label">EV 充电</div>
        <div class="ds-value" :class="evChargingClass">{{ evChargingText }}</div>
        <div class="ds-sub">功率 {{ dispatch.charging.targetPower != null ? dispatch.charging.targetPower : '--' }} kW · 已充 {{ dispatch.charging.sessionEnergy != null ? dispatch.charging.sessionEnergy : '--' }} kWh</div>
      </div>
      <div class="ds-divider"></div>
      <div class="ds-metrics">
        <div class="ds-metric"><span>SOC</span>{{ dispatch.soc != null ? dispatch.soc + '%' : '--' }}</div>
        <div class="ds-metric"><span>光伏</span>{{ dispatch.pvPower != null ? dispatch.pvPower + 'kW' : '--' }}</div>
        <div class="ds-metric"><span>负荷</span>{{ dispatch.loadPower != null ? dispatch.loadPower + 'kW' : '--' }}</div>
      </div>
      <div class="ds-divider"></div>
      <div class="ds-reason">
        <div class="ds-label">决策依据</div>
        <div class="ds-value ds-reason-text">{{ dispatch.reason }}</div>
      </div>
    </div>

    <!-- 顶部指标卡片 -->
    <el-row :gutter="16" class="kpi-row">
      <el-col :span="4" v-for="item in kpiCards" :key="item.label">
        <div class="kpi-card" :style="{ borderTop: '3px solid ' + item.color }">
          <div class="kpi-label">{{ item.label }}</div>
          <div class="kpi-value" :style="{ color: item.color }">{{ item.value }}</div>
          <div class="kpi-unit">{{ item.unit }}</div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <!-- 实时功率曲线 -->
      <el-col :span="14">
        <div class="panel">
          <div class="panel-title curve-title">
            <span>实时功率曲线</span>
            <span class="curve-date">当前日期：{{ selectedDate }}</span>
            <el-date-picker
              v-model="selectedDate"
              type="date"
              value-format="YYYY-MM-DD"
              :clearable="false"
              placeholder="选择日期"
              class="curve-date-picker"
              :disabled-date="date => date.getTime() > Date.now()"
              @change="onDateChange"
            />
          </div>
          <div ref="lineChartRef" class="chart"></div>
        </div>
      </el-col>
      <!-- 能量流桑基图 -->
      <el-col :span="10">
        <div class="panel">
          <div class="panel-title">能量流可视化</div>
          <div ref="sankeyChartRef" class="chart"></div>
        </div>
      </el-col>
    </el-row>

    <!-- DRO 鲁棒调度计划 -->
    <div class="panel">
      <div class="panel-title dro-title">
        <span>DRO 鲁棒调度计划（未来24小时）</span>
        <span class="dro-meta" v-if="droPlan.available">
          确定性成本 {{ droPlan.detCost }} 元 · 鲁棒最坏成本 {{ droPlan.robCost }} 元
        </span>
        <span class="dro-meta dro-warn" v-else>{{ droMsg }}</span>
      </div>
      <div ref="droChartRef" class="chart dro-chart"></div>
    </div>

    <!-- 设备实时数据表格 -->
    <div class="panel table-panel">
      <div class="panel-title">设备实时数据</div>
      <el-table :data="devices" stripe>
        <el-table-column prop="device.deviceName" label="设备名称" />
        <el-table-column prop="device.deviceType" label="类型" width="100">
          <template #default="{ row }">
            <el-tag>{{ typeLabel(row.device.deviceType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="功率(kW)" width="110">
          <template #default="{ row }">{{ row.data ? row.data.power : '-' }}</template>
        </el-table-column>
        <el-table-column label="电压(V)" width="100">
          <template #default="{ row }">{{ row.data ? row.data.voltage : '-' }}</template>
        </el-table-column>
        <el-table-column label="电流(A)" width="100">
          <template #default="{ row }">{{ row.data ? row.data.current : '-' }}</template>
        </el-table-column>
        <el-table-column label="SOC(%)" width="100">
          <template #default="{ row }">
            {{ row.data && row.data.soc != null ? row.data.soc : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="辐照度" width="100">
          <template #default="{ row }">
            {{ row.data && row.data.irradiance != null ? row.data.irradiance : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="更新时间">
          <template #default="{ row }">{{ row.data ? row.data.ts : '-' }}</template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import request from '../utils/request'

const devices = ref([])
const lineChartRef = ref()
const sankeyChartRef = ref()
let lineChart = null
let sankeyChart = null

// ===== 天气 + 时钟 =====
const weather = reactive({
  location: null,
  condition: '--',
  temperature: '--',
  feelsLike: '--',
  humidity: '--',
  windSpeed: '--',
  windDirection: '--',
  pressure: '--',
  cloudCover: '--',
  uvIndex: '--',
  available: false
})
const clock = ref('--:--:--')
const clockDate = ref('')
let clockTimer = null
let weatherTimer = null

const locationText = computed(() => {
  const l = weather.location
  if (!l) return '--'
  const parts = [l.province, l.city, l.district].filter(Boolean)
  const deduped = parts.filter((p, i) =>
    !parts.some((other, j) => j !== i && other !== p && other.includes(p))
  )
  const name = deduped.join(' ')
  if (name) return name
  // 逆地理失败时退化为显示经纬度
  if (l.lat != null && l.lon != null) return `${l.lon}, ${l.lat}`
  return '--'
})

function formatClock() {
  const d = new Date()
  const pad = n => (n < 10 ? '0' + n : '' + n)
  clock.value = `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
  const week = ['日', '一', '二', '三', '四', '五', '六'][d.getDay()]
  clockDate.value = `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} 星期${week}`
}

let gpsLocation = null // 缓存浏览器 GPS 定位，避免重复弹窗

function getBrowserLocation() {
  return new Promise((resolve) => {
    if (gpsLocation) {
      resolve(gpsLocation)
      return
    }
    if (!navigator.geolocation) {
      resolve(null)
      return
    }
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        gpsLocation = { lat: pos.coords.latitude, lon: pos.coords.longitude }
        resolve(gpsLocation)
      },
      () => resolve(null), // 用户拒绝或定位失败 → 回退 IP
      { timeout: 8000, maximumAge: 600000 }
    )
  })
}

async function loadWeather() {
  try {
    const loc = await getBrowserLocation()
    const params = loc ? { lat: loc.lat, lon: loc.lon } : {}
    const res = await request.get('/weather/current', { params })
    if (res.data) {
      Object.assign(weather, res.data)
    }
  } catch (e) {
    // 失败保持默认 '--'
  }
}

// ===== 调度状态 =====
const dispatch = reactive({
  period: { name: '--', type: 'FLAT', price: '--' },
  strategy: null,
  targetPower: null,
  soc: null,
  pvPower: null,
  loadPower: null,
  reason: '--',
  charging: { active: false, targetPower: null, sessionEnergy: null }
})
let dispatchTimer = null

const strategyText = computed(() => {
  const s = dispatch.strategy
  if (!s) return '--'
  const typeMap = { PEAK_VALLEY: '削峰填谷', SELF_USE: '自发自用', PLAN: '计划曲线', DEMAND: '需量控制' }
  return `${s.name}（${typeMap[s.type] || s.type}）`
})

const actionText = computed(() => {
  const t = dispatch.targetPower
  if (t == null) return '--'
  if (t < -0.5) return '充电中'
  if (t > 0.5) return '放电中'
  return '待机'
})

const actionClass = computed(() => {
  const t = dispatch.targetPower
  if (t == null) return ''
  if (t < -0.5) return 'ds-charging'
  if (t > 0.5) return 'ds-discharging'
  return 'ds-idle'
})

const periodClass = computed(() => {
  const p = dispatch.period.type
  return p === 'VALLEY' ? 'ds-valley' : p === 'PEAK' ? 'ds-peak' : 'ds-flat'
})

const evChargingText = computed(() => {
  const c = dispatch.charging
  return c && c.active ? '充电中' : '待机'
})

const evChargingClass = computed(() => {
  const c = dispatch.charging
  return c && c.active ? 'ds-charging' : 'ds-idle'
})

async function loadDispatch() {
  try {
    const res = await request.get('/dispatch/current')
    if (res.data) {
      Object.assign(dispatch, res.data)
    }
  } catch (e) {
    // 失败保持默认
  }
}

// ===== DRO 鲁棒调度计划 =====
const droChartRef = ref()
let droChart = null
const droPlan = reactive({
  available: false,
  hours: [],
  detP: [],
  robP: [],
  robSoc: [],
  detCost: null,
  robCost: null
})
const droMsg = ref('DRO 计划加载中...（需启动 Python 服务）')
let droTimer = null

async function loadDroPlan() {
  try {
    const res = await request.get('/dispatch/dro-plan')
    const data = res.data || {}
    const plan = data.plan
    if (plan && plan.deterministic && plan.robust) {
      droPlan.available = true
      droPlan.hours = plan.hours || []
      droPlan.detP = plan.deterministic.p || []
      droPlan.robP = plan.robust.p || []
      // SOC 轨迹比小时数多 1 个点，取前 H 个与小时对齐
      droPlan.robSoc = (plan.robust.soc || []).slice(0, -1)
      droPlan.detCost = plan.deterministic.cost
      droPlan.robCost = plan.robust.worst_cost
      renderDroChart()
    } else {
      droPlan.available = false
      droMsg.value = data.error ? 'DRO 计划获取失败：' + data.error : 'DRO 计划加载中，请稍候…（后端首次拉取约需15秒）'
    }
  } catch (e) {
    droPlan.available = false
    droMsg.value = 'DRO 计划获取失败（Python 服务未启动？）'
  }
}

function renderDroChart() {
  droChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['确定性PCS', '鲁棒PCS', '鲁棒SOC'], top: 0, itemWidth: 14, itemHeight: 10, textStyle: { color: '#fff', fontSize: 12 } },
    grid: { left: 50, right: 55, top: 42, bottom: 70, containLabel: true },
    xAxis: {
      type: 'category',
      data: droPlan.hours,
      axisLabel: { color: '#aaa' },
      axisLine: { lineStyle: { color: '#555' } }
    },
    yAxis: [
      {
        type: 'value',
        name: 'kW',
        axisLabel: { color: '#aaa' },
        splitLine: { lineStyle: { color: '#333' } }
      },
      {
        type: 'value',
        name: 'SOC %',
        min: 0,
        max: 100,
        axisLabel: { color: '#aaa' },
        splitLine: { show: false }
      }
    ],
    dataZoom: [
      { type: 'inside', start: 0, end: 100 },
      { type: 'slider', height: 18, bottom: 8, borderColor: '#2a3550', backgroundColor: 'rgba(20,26,42,0.6)', fillerColor: 'rgba(0,180,255,0.2)', handleStyle: { color: '#00b4d8' }, textStyle: { color: '#aaa' } }
    ],
    series: [
      {
        name: '确定性PCS',
        type: 'line',
        smooth: true,
        showSymbol: false,
        data: droPlan.detP,
        lineStyle: { color: '#00b4d8' },
        markLine: {
          silent: true,
          symbol: 'none',
          data: [{ yAxis: 0 }],
          lineStyle: { color: '#555', type: 'dashed' }
        }
      },
      {
        name: '鲁棒PCS',
        type: 'line',
        smooth: true,
        showSymbol: false,
        data: droPlan.robP,
        lineStyle: { color: '#00e5ff' }
      },
      {
        name: '鲁棒SOC',
        type: 'line',
        yAxisIndex: 1,
        smooth: true,
        showSymbol: false,
        data: droPlan.robSoc,
        lineStyle: { color: '#ffca28' }
      }
    ]
  })
}

const kpiCards = reactive([
  { label: '光伏发电功率', value: '--', unit: 'kW', color: '#f5a623', key: 'pvPower' },
  { label: '储能功率', value: '--', unit: 'kW', color: '#4fc3f7', key: 'pcsPower' },
  { label: '负载功率', value: '--', unit: 'kW', color: '#ff7043', key: 'loadPower' },
  { label: '购电功率', value: '--', unit: 'kW', color: '#ab47bc', key: 'gridImport' },
  { label: '上网功率', value: '--', unit: 'kW', color: '#66bb6a', key: 'gridExport' },
  { label: '储能SOC', value: '--', unit: '%', color: '#ffca28', key: 'soc' }
])

const selectedDate = ref(todayStr())

function todayStr() {
  const d = new Date()
  const pad = n => (n < 10 ? '0' + n : '' + n)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

const historyData = {
  times: [],
  pv: [],
  load: [],
  pcs: []
}

function typeLabel(type) {
  const map = { PV: '光伏', PCS: '储能', METER: '电表', LOAD: '负载', GRID: '电网', EV: '充电桩' }
  return map[type] || type
}

// 初始化图表
onMounted(async () => {
  await nextTick()
  lineChart = echarts.init(lineChartRef.value)
  sankeyChart = echarts.init(sankeyChartRef.value)
  droChart = echarts.init(droChartRef.value)
  await loadSnapshot()
  await loadEnergyFlow()
  await loadDayCurve()
  await loadWeather()
  await loadDispatch()
  await loadDroPlan()
  formatClock()
  clockTimer = setInterval(formatClock, 1000)
  weatherTimer = setInterval(loadWeather, 60000) // 每分钟刷新一次天气
  dispatchTimer = setInterval(loadDispatch, 10000) // 每10秒刷新调度状态
  droTimer = setInterval(loadDroPlan, 20000) // 每20秒刷新 DRO 计划
  startWebSocket()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  if (clockTimer) clearInterval(clockTimer)
  if (weatherTimer) clearInterval(weatherTimer)
  if (dispatchTimer) clearInterval(dispatchTimer)
  if (droTimer) clearInterval(droTimer)
  if (ws) ws.close()
  if (lineChart) lineChart.dispose()
  if (sankeyChart) sankeyChart.dispose()
  if (droChart) droChart.dispose()
})

function handleResize() {
  lineChart && lineChart.resize()
  sankeyChart && sankeyChart.resize()
  droChart && droChart.resize()
}

async function loadSnapshot() {
  const res = await request.get('/monitor/snapshot')
  devices.value = res.data || []
  // 计算 SOC 均值
  const pcsList = devices.value.filter(d => d.device.deviceType === 'PCS')
  const socValues = pcsList.filter(d => d.data && d.data.soc != null).map(d => d.data.soc)
  const avgSoc = socValues.length ? (socValues.reduce((a, b) => a + b, 0) / socValues.length).toFixed(1) : '--'
  const socCard = kpiCards.find(c => c.key === 'soc')
  socCard.value = avgSoc
}

async function loadEnergyFlow() {
  const res = await request.get('/monitor/energy-flow')
  const flow = res.data
  const pvCard = kpiCards.find(c => c.key === 'pvPower')
  const pcsCard = kpiCards.find(c => c.key === 'pcsPower')
  const loadCard = kpiCards.find(c => c.key === 'loadPower')
  const importCard = kpiCards.find(c => c.key === 'gridImport')
  const exportCard = kpiCards.find(c => c.key === 'gridExport')
  pvCard.value = flow.pvPower
  pcsCard.value = flow.pcsPower
  loadCard.value = flow.loadPower
  importCard.value = flow.gridImport
  exportCard.value = flow.gridExport
  renderSankey(flow)
}

function renderSankey(flow) {
  const nodes = [
    { name: '光伏' },
    { name: '储能' },
    { name: '负载' },
    { name: '电网' },
    { name: '充电桩' }
  ]
  const links = []
  const addLink = (source, target, value) => {
    if (value > 0.01) {
      links.push({ source, target, value: Number(value.toFixed(1)) })
    }
  }
  addLink('光伏', '负载', flow.pvPower)
  addLink('光伏', '储能', flow.chargePower)
  addLink('储能', '负载', flow.dischargePower)
  addLink('电网', '负载', flow.gridImport)
  addLink('光伏', '电网', flow.gridExport)
  addLink('光伏', '充电桩', flow.evPower)

  if (links.length === 0) {
    links.push({ source: '光伏', target: '负载', value: 0.1 })
  }

  sankeyChart.setOption({
    tooltip: { trigger: 'item', triggerOn: 'mousemove' },
    series: [{
      type: 'sankey',
      layout: 'none',
      data: nodes,
      links: links,
      emphasis: { focus: 'adjacency' },
      lineStyle: { color: 'gradient', curveness: 0.5 },
      label: { color: '#fff' },
      itemStyle: { borderWidth: 1, borderColor: '#aaa' }
    }]
  })
}

// 加载指定日期（年月日）的全天功率曲线（0:00 - 24:00）
async function loadDayCurve() {
  const res = await request.get('/monitor/day-curve', {
    params: { date: selectedDate.value }
  })
  const d = res.data || {}
  historyData.times = d.times || []
  historyData.pv = d.pv || []
  historyData.load = d.load || []
  historyData.pcs = d.pcs || []
  renderLineChart()
}

function onDateChange() {
  loadDayCurve()
}

// WebSocket 实时更新
let ws = null
function startWebSocket() {
  const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws'
  ws = new WebSocket(`${protocol}://${window.location.host}/ws/monitor`)
  ws.onmessage = (event) => {
    try {
      const data = JSON.parse(event.data)
      // 实时数据（非告警）
      if (data && data.deviceId && !data.type) {
        updateRealtime(data)
      }
    } catch (e) {
      // ignore
    }
  }
}

function updateRealtime(data) {
  // 更新设备 table 中对应行的数据
  const row = devices.value.find(d => d.device.id === data.deviceId)
  if (row) {
    row.data = data
  }
  // 若为储能(PCS)数据，实时更新 SOC 指标卡
  const dev = devices.value.find(d => d.device.id === data.deviceId)
  if (dev && dev.device.deviceType === 'PCS' && data.soc != null) {
    const socCard = kpiCards.find(c => c.key === 'soc')
    socCard.value = data.soc
  }
  // 仅在查看当天时，将实时数据叠加到 0:00-24:00 的全天曲线上
  if (selectedDate.value !== todayStr() || !dev) {
    return
  }
  const seriesKey = { PV: 'pv', LOAD: 'load', PCS: 'pcs' }[dev.device.deviceType]
  if (!seriesKey) {
    return
  }
  const now = new Date()
  const idx = now.getHours() * 60 + now.getMinutes()
  if (idx >= 0 && idx < historyData.times.length) {
    historyData[seriesKey][idx] = data.power
    renderLineChart()
  }
}

function renderLineChart() {
  lineChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['光伏', '负载', '储能'], top: 0, itemWidth: 14, itemHeight: 10, textStyle: { color: '#fff', fontSize: 12 } },
    grid: { left: 45, right: 20, top: 42, bottom: 70, containLabel: true },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: historyData.times,
      axisLine: { lineStyle: { color: '#555' } },
      axisLabel: { color: '#aaa', interval: 59 }
    },
    yAxis: {
      type: 'value',
      name: 'kW',
      axisLine: { lineStyle: { color: '#555' } },
      axisLabel: { color: '#aaa' },
      splitLine: { lineStyle: { color: '#333' } }
    },
    dataZoom: [
      { type: 'inside', start: 0, end: 100 },
      { type: 'slider', height: 18, bottom: 8, borderColor: '#2a3550', backgroundColor: 'rgba(20,26,42,0.6)', fillerColor: 'rgba(0,180,255,0.2)', handleStyle: { color: '#00b4d8' }, textStyle: { color: '#aaa' } }
    ],
    series: [
      { name: '光伏', type: 'line', smooth: true, showSymbol: false, connectNulls: true, data: historyData.pv, lineStyle: { color: '#f5a623' } },
      { name: '负载', type: 'line', smooth: true, showSymbol: false, connectNulls: true, data: historyData.load, lineStyle: { color: '#ff7043' } },
      { name: '储能', type: 'line', smooth: true, showSymbol: false, connectNulls: true, data: historyData.pcs, lineStyle: { color: '#4fc3f7' } }
    ]
  })
}
</script>

<style scoped>
.dashboard {
  min-height: 100%;
}

/* ===== 顶部：定位 + 气象 + 时钟 ===== */
.weather-strip {
  position: relative;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 20px;
  padding: 16px 22px;
  margin-bottom: 16px;
  border-radius: 10px;
  border: 1px solid rgba(0, 229, 255, 0.35);
  background: linear-gradient(90deg, rgba(0, 180, 255, 0.10), rgba(0, 229, 255, 0.03));
  box-shadow: 0 0 24px rgba(0, 229, 255, 0.15), inset 0 0 24px rgba(0, 229, 255, 0.05);
  overflow: hidden;
}
/* 扫描线动画 */
.weather-strip::after {
  content: '';
  position: absolute;
  top: 0;
  left: -60%;
  width: 50%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(0, 229, 255, 0.10), transparent);
  animation: ws-scan 5s linear infinite;
}
@keyframes ws-scan {
  to { left: 160%; }
}
.ws-location { min-width: 170px; }
.ws-label {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #7fd8f0;
  font-size: 12px;
  letter-spacing: 2px;
  margin-bottom: 6px;
}
.ws-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #00e5ff;
  box-shadow: 0 0 8px #00e5ff;
  animation: ws-pulse 1.6s ease-in-out infinite;
}
@keyframes ws-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.3; }
}
.ws-loc-name {
  color: #fff;
  font-size: 20px;
  font-weight: 700;
  letter-spacing: 1px;
}
.ws-coord {
  color: #5c6b8a;
  font-size: 12px;
  margin-top: 4px;
  font-variant-numeric: tabular-nums;
}
.ws-divider {
  width: 1px;
  height: 46px;
  background: linear-gradient(180deg, transparent, rgba(0, 229, 255, 0.4), transparent);
}
.ws-temp {
  text-align: center;
  min-width: 110px;
}
.ws-temp-value {
  font-size: 40px;
  font-weight: 700;
  color: #fff;
  line-height: 1;
  text-shadow: 0 0 16px rgba(0, 229, 255, 0.7);
  font-variant-numeric: tabular-nums;
}
.ws-temp-unit {
  font-size: 18px;
  color: #00e5ff;
  margin-left: 2px;
}
.ws-condition {
  color: #7fd8f0;
  font-size: 14px;
  margin-top: 6px;
  letter-spacing: 2px;
}
.ws-detail {
  display: grid;
  grid-template-columns: repeat(3, auto);
  gap: 8px 22px;
  flex: 1;
  min-width: 280px;
}
.ws-detail-item {
  color: #c9d6e8;
  font-size: 13px;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}
.ws-detail-item i {
  font-style: normal;
  color: #5c6b8a;
  margin-right: 6px;
}
.ws-clock { text-align: right; min-width: 150px; }
.ws-clock-time {
  font-size: 32px;
  font-weight: 700;
  color: #00e5ff;
  letter-spacing: 3px;
  line-height: 1;
  text-shadow: 0 0 14px rgba(0, 229, 255, 0.8);
  font-variant-numeric: tabular-nums;
}
.ws-clock-date {
  color: #5c6b8a;
  font-size: 12px;
  margin-top: 6px;
}

/* ===== 调度状态条 ===== */
.dispatch-strip {
  position: relative;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 18px;
  padding: 14px 22px;
  margin-bottom: 16px;
  border-radius: 10px;
  border: 1px solid rgba(0, 255, 170, 0.3);
  background: linear-gradient(90deg, rgba(0, 255, 170, 0.08), rgba(0, 229, 255, 0.03));
  box-shadow: 0 0 20px rgba(0, 255, 170, 0.12), inset 0 0 20px rgba(0, 255, 170, 0.04);
  overflow: hidden;
}
.dispatch-strip::after {
  content: '';
  position: absolute;
  top: 0;
  left: -60%;
  width: 50%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(0, 255, 170, 0.10), transparent);
  animation: ds-scan 6s linear infinite;
}
@keyframes ds-scan {
  to { left: 160%; }
}
.ds-item, .ds-reason {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.ds-label {
  color: #5c6b8a;
  font-size: 12px;
  letter-spacing: 2px;
}
.ds-value {
  font-size: 18px;
  font-weight: 700;
  color: #fff;
}
.ds-price {
  font-size: 13px;
  color: #7a879e;
  font-weight: 400;
}
.ds-sub {
  color: #7a879e;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}
.ds-divider {
  width: 1px;
  height: 40px;
  background: linear-gradient(180deg, transparent, rgba(0, 255, 170, 0.35), transparent);
}
.ds-metrics {
  display: flex;
  gap: 22px;
}
.ds-metric {
  display: flex;
  flex-direction: column;
  gap: 2px;
  color: #c9d6e8;
  font-size: 15px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}
.ds-metric span {
  color: #5c6b8a;
  font-size: 12px;
  font-weight: 400;
}
.ds-reason-text {
  font-size: 14px;
  font-weight: 600;
  color: #7fd8f0;
}
.ds-charging { color: #4fc3f7; text-shadow: 0 0 10px rgba(79, 195, 247, 0.6); }
.ds-discharging { color: #ff9f43; text-shadow: 0 0 10px rgba(255, 159, 67, 0.6); }
.ds-idle { color: #8a94a6; }
.ds-valley { color: #4fc3f7; }
.ds-peak { color: #ff7043; }
.ds-flat { color: #8a94a6; }
.ds-strategy { color: #00e5ff; text-shadow: 0 0 10px rgba(0, 229, 255, 0.5); }

/* ===== KPI 卡片 ===== */
.kpi-card {
  background: linear-gradient(180deg, rgba(26, 31, 54, 0.85), rgba(15, 20, 40, 0.85));
  border: 1px solid rgba(0, 229, 255, 0.18);
  border-radius: 8px;
  padding: 16px;
  height: 90px;
  position: relative;
  overflow: hidden;
  box-shadow: 0 0 16px rgba(0, 180, 255, 0.12);
  transition: box-shadow 0.3s;
}
.kpi-card:hover {
  box-shadow: 0 0 24px rgba(0, 229, 255, 0.3);
}
.kpi-label {
  color: #8a94a6;
  font-size: 13px;
  letter-spacing: 1px;
}
.kpi-value {
  font-size: 26px;
  font-weight: 700;
  margin: 6px 0 2px;
  font-variant-numeric: tabular-nums;
}
.kpi-unit {
  color: #8a94a6;
  font-size: 12px;
}

/* ===== 面板 ===== */
.panel {
  background: linear-gradient(180deg, rgba(26, 31, 54, 0.85), rgba(15, 20, 40, 0.85));
  border: 1px solid rgba(0, 229, 255, 0.18);
  border-radius: 8px;
  padding: 16px;
  margin-top: 16px;
  box-shadow: 0 0 16px rgba(0, 180, 255, 0.10);
}
.panel-title {
  color: #d8dee9;
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 12px;
  letter-spacing: 1px;
}
.curve-title {
  display: flex;
  align-items: center;
  gap: 12px;
}
.curve-date {
  font-size: 13px;
  font-weight: 400;
  color: #8a94a6;
}
.curve-date-picker {
  margin-left: auto;
  width: 150px;
}
.chart {
  height: 340px;
  width: 100%;
}
.dro-title {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}
.dro-meta {
  font-size: 13px;
  font-weight: 400;
  color: #7fd8f0;
}
.dro-warn {
  color: #ff9f43;
}
.dro-chart {
  height: 280px;
}
</style>