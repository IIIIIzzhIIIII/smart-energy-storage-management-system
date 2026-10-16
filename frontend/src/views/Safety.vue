<template>
  <div class="safety-page">
    <div class="page-title">
      储能电池安全检测
      <el-button size="small" style="float: right" @click="load">刷新</el-button>
    </div>

    <el-card shadow="never" class="port-card">
      <template #header>
        <span>串口连接（USB-RS485 温湿度变送器接入）</span>
        <el-button size="small" style="float: right" :loading="scanning" @click="loadPorts(true)">重新扫描端口</el-button>
      </template>
      <div class="port-row">
        <span class="port-label">串口</span>
        <el-select v-model="portName" style="width: 300px" placeholder="请选择 USB-RS485 所在串口" @change="onPortChange">
          <el-option v-for="p in ports" :key="p.portName" :value="p.portName"
                     :label="p.portName + ' · ' + p.description">
            <span>{{ p.portName }} · {{ p.description }}</span>
            <el-tag v-if="p.usb485" size="small" type="success" style="margin-left: 6px">USB-485</el-tag>
          </el-option>
        </el-select>
        <span class="port-label">波特率</span>
        <el-select v-model="identifyBaud" style="width: 128px">
          <el-option :value="0" label="自动识别" />
          <el-option v-for="b in baudOptions" :key="b" :value="b" :label="b" />
        </el-select>
        <span class="port-label">从站地址</span>
        <el-input-number v-model="identifySlave" :min="0" :max="247" style="width: 108px" />
        <el-button type="primary" :loading="identifying" @click="doIdentify">识别传感器</el-button>
        <el-button link @click="loadFrames">原始帧</el-button>
      </div>
      <el-alert v-if="identifyMsg" :title="identifyMsg" :type="identifyOk ? 'success' : 'error'"
                :closable="false" show-icon style="margin-top: 10px" />
      <div v-if="identifyOk" class="identify-result">
        <span class="identify-item">
          <span class="port-label">湿度</span><b class="val-humidity">{{ identify.humidity }} %RH</b>
          <span class="hint">寄存器 0x0000</span>
        </span>
        <span class="identify-item">
          <span class="port-label">温度</span><b class="val-temp">{{ identify.temperature }} ℃</b>
          <span class="hint">寄存器 0x0001</span>
        </span>
        <span class="identify-item hint">{{ identify.portName }} @ {{ identify.baudRate }}bps · 从站 {{ identify.slaveAddress }}</span>
        <el-button type="success" plain @click="saveIdentified('HUMIDITY')">保存为湿度设备</el-button>
        <el-button type="warning" plain @click="saveIdentified('TEMP')">保存为温度设备</el-button>
      </div>
      <el-table v-if="frames.length" :data="frames" size="small" max-height="190" style="margin-top: 10px"
                empty-text="暂无串口报文记录">
        <el-table-column prop="time" label="时间" width="150" />
        <el-table-column prop="portName" label="端口" width="66" />
        <el-table-column prop="baudRate" label="波特率" width="74" />
        <el-table-column prop="slaveAddress" label="从站" width="56" />
        <el-table-column prop="requestFrame" label="请求帧" min-width="170" />
        <el-table-column prop="responseFrame" label="应答帧" min-width="170" />
        <el-table-column label="结果" width="72">
          <template #default="{ row }">
            <el-tag size="small" :type="row.success ? 'success' : 'danger'">{{ row.success ? 'OK' : 'NG' }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-row :gutter="12">
      <el-col :span="15">
        <el-card shadow="never" class="model-card">
          <template #header>
            <span>检测模块三维视图（点击接口可查看或挂接采集设备）</span>
          </template>
          <div class="canvas-wrap">
            <div ref="canvasEl" class="three-canvas"></div>
            <div v-if="modelError" class="model-error">{{ modelError }}</div>
          </div>
          <div class="legend">
            <span><i class="dot" style="background:#6b7280"></i>空闲</span>
            <span><i class="dot" style="background:#e6a23c"></i>待匹配</span>
            <span><i class="dot" style="background:#67c23a"></i>匹配在线</span>
            <span><i class="dot" style="background:#f56c6c"></i>失败或离线</span>
          </div>
        </el-card>
      </el-col>

      <el-col :span="9">
        <el-card shadow="never">
          <template #header>
            <span>采集设备（{{ sensors.length }}/24）</span>
            <el-button type="success" size="small" style="float: right" @click="openAdd(null)">添加设备</el-button>
          </template>
          <el-alert v-if="offlineSensors.length" type="error" :closable="false" show-icon style="margin-bottom: 8px"
                    :title="'离线：' + offlineSensors.map(s => s.deviceName).join('、') + '（USB-RS485 可能已拔出；重新插回后会自动重新匹配并恢复采集）'" />
          <el-table :data="sensors" height="404" size="small" empty-text="暂无采集设备">
            <el-table-column prop="portNo" label="接口" width="54" />
            <el-table-column prop="deviceName" label="设备名称" min-width="118" show-overflow-tooltip />
            <el-table-column label="类型" width="88">
              <template #default="{ row }">
                <el-tag size="small">{{ typeLabel(row.sensorType) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="slaveAddress" label="地址" width="56" />
            <el-table-column label="最新值" width="100">
              <template #default="{ row }">
                <span v-if="row.latestValue === null || row.latestValue === undefined">-</span>
                <span v-else :class="{ 'val-alarm': isAlarm(row) }">{{ row.latestValue }} {{ row.unit }}</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="88">
              <template #default="{ row }">
                <el-tooltip placement="top" :content="row.lastCommTime ? '最后通讯 ' + row.lastCommTime : '尚未成功通讯，请点击「匹配」'">
                  <el-tag size="small" :type="statusTagType(row)">{{ statusText(row) }}</el-tag>
                </el-tooltip>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="140" fixed="right">
              <template #default="{ row }">
                <el-button size="small" type="primary" link @click="doMatch(row)">匹配</el-button>
                <el-button size="small" link @click="openEdit(row)">编辑</el-button>
                <el-button size="small" type="danger" link @click="del(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑采集设备' : '添加采集设备'" width="620px" :close-on-click-modal="false">
      <el-form :model="form" label-width="106px">
        <el-form-item label="接口编号" required>
          <el-select v-model="form.portNo" style="width: 100%">
            <el-option v-for="p in portOptions" :key="p.value" :label="p.label" :value="p.value" :disabled="p.used && p.value !== form.portNo" />
          </el-select>
        </el-form-item>
        <el-form-item label="采集类型" required>
          <el-select v-model="form.sensorType" style="width: 100%" @change="onTypeChange">
            <el-option v-for="(v, k) in typeMap" :key="k" :label="v.label" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="设备名称" required>
          <el-input v-model="form.deviceName" placeholder="如：电芯温度探头1" />
        </el-form-item>
        <el-form-item label="设备编号" required>
          <el-input v-model="form.deviceCode" placeholder="如：TEMP-001" />
        </el-form-item>
        <el-form-item label="从站地址">
          <el-input-number v-model="form.slaveAddress" :min="1" :max="247" />
          <span class="hint">Modbus 从站地址 1-247</span>
        </el-form-item>
        <el-form-item label="串口">
          <el-input v-model="form.portName" placeholder="如 COM5（留空则使用模块默认串口）">
            <template #append>
              <el-button @click="form.portName = portName">用当前选择</el-button>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="寄存器偏移">
          <el-input-number v-model="form.registerOffset" :min="0" :max="15" />
          <span class="hint">温湿度一体变送器：湿度=0（0x0000）、温度=1（0x0001）</span>
        </el-form-item>
        <el-form-item label="波特率">
          <el-select v-model="form.baudRate" style="width: 100%">
            <el-option :value="2400" label="2400" />
            <el-option :value="4800" label="4800（说明书默认）" />
            <el-option :value="9600" label="9600" />
            <el-option :value="19200" label="19200" />
            <el-option :value="38400" label="38400" />
          </el-select>
        </el-form-item>
        <el-divider content-position="left">告警阈值（{{ form.unit || '' }}）</el-divider>
        <el-form-item label="预警 下/上限">
          <el-input-number v-model="form.warnMin" :controls="false" style="width: 46%" />
          <el-input-number v-model="form.warnMax" :controls="false" style="width: 46%; margin-left: 6%" />
        </el-form-item>
        <el-form-item label="严重 下/上限">
          <el-input-number v-model="form.critMin" :controls="false" style="width: 46%" />
          <el-input-number v-model="form.critMax" :controls="false" style="width: 46%; margin-left: 6%" />
        </el-form-item>
        <el-form-item label="紧急 下/上限">
          <el-input-number v-model="form.emergMin" :controls="false" style="width: 46%" />
          <el-input-number v-model="form.emergMax" :controls="false" style="width: 46%; margin-left: 6%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">{{ form.id ? '保存' : '匹配并保存' }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>
<script setup>
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../utils/request'
import * as THREE from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js'
import { DRACOLoader } from 'three/examples/jsm/loaders/DRACOLoader.js'

const typeMap = {
  TEMP: { label: '温度探头', unit: '℃', def: { warnMax: 55, critMax: 65, emergMax: 75 } },
  GAS: { label: '可燃气体', unit: '%LEL', def: { warnMax: 10, critMax: 25, emergMax: 50 } },
  HUMIDITY: { label: '湿度', unit: '%RH', def: { warnMax: 80, critMax: 90, emergMax: 95 } },
  VOLTAGE: { label: '单体电压', unit: 'V', def: { warnMin: 2.8, warnMax: 3.55, critMin: 2.6, critMax: 3.65, emergMin: 2.3, emergMax: 3.8 } },
  CURRENT: { label: '充放电电流', unit: 'A', def: { warnMax: 165, critMax: 195, emergMax: 225 } },
  INSULATION: { label: '绝缘电阻', unit: 'MΩ', def: { warnMin: 1.0, critMin: 0.5, emergMin: 0.2 } },
  SMOKE: { label: '烟感', unit: '', def: { warnMax: 0.5 } },
  DI: { label: '硬接点', unit: '', def: { warnMax: 0.5 } },
  BMS: { label: 'BMS告警', unit: '', def: { warnMax: 0.5 } }
}

const sensors = ref([])
const moduleId = ref(null)
const dialogVisible = ref(false)
const saving = ref(false)
const modelError = ref('')
const portOptions = ref([])

// ---- 串口连接与识别 ----
const ports = ref([])
const portName = ref('')
const identifyBaud = ref(0)
const identifySlave = ref(0)
const identifying = ref(false)
const scanning = ref(false)
const identify = ref({})
const identifyOk = ref(false)
const identifyMsg = ref('')
const frames = ref([])
const baudOptions = [2400, 4800, 9600, 19200, 38400, 57600, 115200]

/** 离线设备（拔掉 USB-RS485 或连续通讯失败后置为 status=0） */
const offlineSensors = computed(() => sensors.value.filter(s => s.matchStatus === 2 && s.status === 0))

const form = reactive({
  id: null, portNo: 1, sensorType: 'TEMP', deviceName: '', deviceCode: '',
  slaveAddress: 1, commProtocol: 'MODBUS_RTU', baudRate: 4800, unit: '℃',
  portName: '', registerOffset: 0,
  warnMin: null, warnMax: null, critMin: null, critMax: null, emergMin: null, emergMax: null
})

const canvasEl = ref(null)
let scene = null
let camera = null
let renderer = null
let controls = null
let raycaster = null
let dracoLoader = null
const markerMeshes = []
let animId = null
const pointer = new THREE.Vector2()
let pollTimer = null
let downX = 0
let downY = 0

function typeLabel(t) {
  return typeMap[t] ? typeMap[t].label : t
}

function statusText(row) {
  if (row.matchStatus === 2) return row.status === 1 ? '在线' : '离线'
  if (row.matchStatus === 3) return '匹配失败'
  return '待匹配'
}

function statusTagType(row) {
  if (row.matchStatus === 2 && row.status === 1) return 'success'
  if (row.matchStatus === 3) return 'danger'
  return 'warning'
}

function isAlarm(row) {
  const v = row.latestValue
  if (v === null || v === undefined) return false
  if (row.warnMax !== null && row.warnMax !== undefined && v > row.warnMax) return true
  if (row.warnMin !== null && row.warnMin !== undefined && v < row.warnMin) return true
  return false
}

/** 扫描本机串口（USB-RS485 模块） */
async function loadPorts(manual) {
  scanning.value = true
  try {
    const res = await request.get('/safety/ports')
    ports.value = res.data || []
    if (!portName.value || !ports.value.some(p => p.portName === portName.value)) {
      const usb = ports.value.find(p => p.usb485)
      portName.value = usb ? usb.portName : (ports.value.length ? ports.value[0].portName : '')
    }
    if (manual) {
      if (!ports.value.length) ElMessage.warning('未发现任何串口，请检查 USB-RS485 是否插好、驱动是否已安装')
      else ElMessage.success('已扫描到 ' + ports.value.length + ' 个串口')
    }
  } catch (e) {
    // 忽略扫描失败
  } finally {
    scanning.value = false
  }
}

function onPortChange() {
  identifyOk.value = false
  identifyMsg.value = ''
  frames.value = []
}

/** 识别串口上的温湿度变送器：自动探测波特率与从站地址（CRC 校验通过才认定成功） */
async function doIdentify() {
  if (!portName.value) {
    ElMessage.warning('请先选择串口')
    return
  }
  identifying.value = true
  try {
    const body = { portName: portName.value }
    if (identifyBaud.value) body.baudRate = identifyBaud.value
    if (identifySlave.value) body.slaveAddress = identifySlave.value
    const res = await request.post('/safety/identify', body)
    const r = res.data || {}
    identify.value = r
    identifyOk.value = !!r.success
    identifyMsg.value = r.message || ''
    frames.value = r.frames || []
    if (r.success) {
      // 记住实测参数，并同步为检测模块使用的串口
      if (r.baudRate) identifyBaud.value = r.baudRate
      if (r.slaveAddress) identifySlave.value = r.slaveAddress
      await request.put('/safety/module/port', { portName: r.portName || portName.value })
    }
  } finally {
    identifying.value = false
  }
}

/** 用识别结果预填并打开"添加采集设备"对话框（温度 / 湿度各自成为一条独立采集设备） */
function saveIdentified(type) {
  const r = identify.value || {}
  openAdd(null)
  const n = sensors.value.filter(s => s.sensorType === type).length + 1
  form.sensorType = type
  form.slaveAddress = r.slaveAddress || 1
  form.baudRate = r.baudRate || 4800
  form.portName = r.portName || portName.value
  form.registerOffset = type === 'TEMP' ? 1 : 0
  form.deviceName = (type === 'TEMP' ? '舱内温度传感器' : '舱内湿度传感器') + n
  form.deviceCode = (type === 'TEMP' ? 'TEMP-' : 'HUM-') + String(n).padStart(3, '0')
  onTypeChange(type)
  form.registerOffset = type === 'TEMP' ? 1 : 0
}

/** 查看最近的串口原始报文（协议匹配 / 端口识别） */
async function loadFrames() {
  const res = await request.get('/safety/frames', { params: { limit: 30 } })
  frames.value = res.data || []
  if (!frames.value.length) ElMessage.info('暂无串口报文记录，请先执行一次「识别传感器」或「匹配」')
}

function buildPortOptions() {
  const used = new Set(sensors.value.map(s => s.portNo))
  const opts = []
  for (let i = 1; i <= 24; i++) {
    opts.push({ value: i, label: '接口 ' + i + '（' + (i < 13 ? '上排' : '下排') + '）', used: used.has(i) })
  }
  portOptions.value = opts
}

function firstFreePort() {
  const used = new Set(sensors.value.map(s => s.portNo))
  for (let i = 1; i <= 24; i++) {
    if (!used.has(i)) return i
  }
  return 1
}

async function load() {
  try {
    if (moduleId.value === null) {
      const m = await request.get('/safety/module')
      moduleId.value = m.data.id
    }
    const res = await request.get('/safety/sensors', { params: { moduleId: moduleId.value } })
    sensors.value = res.data || []
    buildPortOptions()
    updateMarkerColors()
  } catch (e) {
    // 忽略轮询错误
  }
}

function onTypeChange(t) {
  const cfg = typeMap[t]
  if (!cfg) return
  // 温湿度一体变送器：湿度取第 1 个寄存器(0x0000)、温度取第 2 个寄存器(0x0001)
  form.registerOffset = t === 'TEMP' ? 1 : 0
  form.unit = cfg.unit
  form.warnMin = null; form.warnMax = null
  form.critMin = null; form.critMax = null
  form.emergMin = null; form.emergMax = null
  const d = cfg.def || {}
  if (d.warnMin !== undefined) form.warnMin = d.warnMin
  if (d.warnMax !== undefined) form.warnMax = d.warnMax
  if (d.critMin !== undefined) form.critMin = d.critMin
  if (d.critMax !== undefined) form.critMax = d.critMax
  if (d.emergMin !== undefined) form.emergMin = d.emergMin
  if (d.emergMax !== undefined) form.emergMax = d.emergMax
}

function openAdd(portNo) {
  const port = portNo || firstFreePort()
  Object.assign(form, {
    id: null, portNo: port, sensorType: 'TEMP', deviceName: '', deviceCode: '',
    slaveAddress: sensors.value.length + 1 > 247 ? 1 : sensors.value.length + 1,
    commProtocol: 'MODBUS_RTU', baudRate: 4800, unit: '℃',
    portName: portName.value, registerOffset: 0,
    warnMin: null, warnMax: null, critMin: null, critMax: null, emergMin: null, emergMax: null
  })
  onTypeChange('TEMP')
  dialogVisible.value = true
}

function openEdit(row) {
  Object.assign(form, row)
  dialogVisible.value = true
}

async function save() {
  if (!form.deviceName || !form.deviceCode) {
    ElMessage.warning('请填写设备名称和设备编号')
    return
  }
  if (form.id) {
    await request.put('/safety/sensor', form)
    ElMessage.success('修改成功')
    dialogVisible.value = false
    load()
    return
  }
  // 新增：先做协议匹配（探测设备是否已插上并通电），匹配成功才保存
  saving.value = true
  try {
    const res = await request.post('/safety/sensor', form)
    const r = res.data
    if (r && r.success) {
      ElMessage.success(r.message)
      dialogVisible.value = false
      load()
    } else {
      ElMessageBox.alert((r ? r.message : '协议匹配失败') + '，设备未保存。请检查设备是否已插上并通电、从站地址与波特率是否正确。', '协议匹配失败', { type: 'error' })
    }
  } finally {
    saving.value = false
  }
}

async function del(row) {
  await ElMessageBox.confirm('确认删除采集设备「' + row.deviceName + '」？', '提示', { type: 'warning' })
  await request.delete('/safety/sensor/' + row.id)
  ElMessage.success('删除成功')
  load()
}

async function doMatch(row) {
  const res = await request.post('/safety/sensor/' + row.id + '/match')
  const r = res.data
  if (r && r.success) {
    ElMessage.success(r.message)
  } else {
    ElMessage.error(r ? r.message : '匹配失败')
  }
  load()
}
function initThree() {
  const el = canvasEl.value
  if (!el) return
  const w = el.clientWidth || 640
  const h = el.clientHeight || 420

  scene = new THREE.Scene()
  scene.background = new THREE.Color(0x0b1122)
  camera = new THREE.PerspectiveCamera(45, w / h, 0.001, 1000)
  renderer = new THREE.WebGLRenderer({ antialias: true })
  renderer.setSize(w, h)
  renderer.setPixelRatio(window.devicePixelRatio)
  el.appendChild(renderer.domElement)

  scene.add(new THREE.AmbientLight(0xffffff, 1.3))
  const d1 = new THREE.DirectionalLight(0xffffff, 1.8)
  d1.position.set(1, -1, 2)
  scene.add(d1)
  const d2 = new THREE.DirectionalLight(0x88ccff, 1.1)
  d2.position.set(-1, 1, 1)
  scene.add(d2)

  raycaster = new THREE.Raycaster()

  dracoLoader = new DRACOLoader()
  dracoLoader.setDecoderPath('/draco/')
  const loader = new GLTFLoader()
  loader.setDRACOLoader(dracoLoader)
  loader.load('/models/储能电池检测模型.glb', (gltf) => {
    const mdl = gltf.scene
    const box0 = new THREE.Box3().setFromObject(mdl)
    const center = box0.getCenter(new THREE.Vector3())
    mdl.position.sub(center)
    scene.add(mdl)

    const box = new THREE.Box3().setFromObject(mdl)
    const size = box.getSize(new THREE.Vector3())
    const maxDim = Math.max(size.x, size.y, size.z)

    camera.position.set(0, -maxDim * 1.7, maxDim * 1.6)
    camera.lookAt(0, 0, 0)

    controls = new OrbitControls(camera, renderer.domElement)
    controls.enableDamping = true
    controls.dampingFactor = 0.1
    controls.target.set(0, 0, 0)

    createMarkers(box, size)
    updateMarkerColors()
    animate()
  }, undefined, (err) => {
    console.error('GLTF 模型加载失败', err)
    modelError.value = '3D 模型加载失败：' + (err && err.message ? err.message : '请检查模型文件与 Draco 解码器')
  })

  renderer.domElement.addEventListener('mousedown', onCanvasMouseDown)
  renderer.domElement.addEventListener('click', onCanvasClick)
  window.addEventListener('resize', onResize)
}

function createMarkers(box, size) {
  const r = Math.max(size.x, size.y) * 0.016
  const geo = new THREE.SphereGeometry(r, 18, 18)
  const step = size.x / 12
  for (let i = 0; i < 24; i++) {
    const port = i + 1
    const isTop = i < 12
    const idx = i % 12
    const x = box.min.x + step * (idx + 0.5)
    const y = isTop ? box.max.y : box.min.y
    const z = box.max.z + r * 0.5
    const mat = new THREE.MeshStandardMaterial({ color: 0x6b7280, emissive: 0x222222, emissiveIntensity: 0.6 })
    const mesh = new THREE.Mesh(geo, mat)
    mesh.position.set(x, y, z)
    mesh.userData.port = port
    scene.add(mesh)
    markerMeshes.push(mesh)
  }
}

function updateMarkerColors() {
  if (!markerMeshes.length) return
  const map = {}
  sensors.value.forEach(s => { map[s.portNo] = s })
  markerMeshes.forEach(m => {
    const s = map[m.userData.port]
    let color = 0x6b7280
    if (s) {
      if (s.matchStatus === 2 && s.status === 1) color = 0x67c23a
      else if (s.matchStatus === 3) color = 0xf56c6c
      else if (s.matchStatus === 2 && s.status === 0) color = 0xf56c6c
      else color = 0xe6a23c
    }
    m.material.color.setHex(color)
    m.material.emissive.setHex(color)
  })
}

function onCanvasMouseDown(event) {
  downX = event.clientX
  downY = event.clientY
}

function onCanvasClick(event) {
  if (!renderer || !camera) return
  if (Math.abs(event.clientX - downX) > 5 || Math.abs(event.clientY - downY) > 5) return
  const rect = renderer.domElement.getBoundingClientRect()
  pointer.x = ((event.clientX - rect.left) / rect.width) * 2 - 1
  pointer.y = -((event.clientY - rect.top) / rect.height) * 2 + 1
  raycaster.setFromCamera(pointer, camera)
  const hits = raycaster.intersectObjects(markerMeshes)
  if (hits.length === 0) return
  const port = hits[0].object.userData.port
  const s = sensors.value.find(x => x.portNo === port)
  if (s) openEdit(s)
  else openAdd(port)
}

function animate() {
  animId = requestAnimationFrame(animate)
  if (controls) controls.update()
  if (renderer && scene && camera) renderer.render(scene, camera)
}

function onResize() {
  const el = canvasEl.value
  if (!el || !renderer || !camera) return
  const w = el.clientWidth
  const h = el.clientHeight
  if (w === 0 || h === 0) return
  camera.aspect = w / h
  camera.updateProjectionMatrix()
  renderer.setSize(w, h)
}

function disposeThree() {
  if (animId) cancelAnimationFrame(animId)
  window.removeEventListener('resize', onResize)
  if (renderer) {
    renderer.domElement.removeEventListener('mousedown', onCanvasMouseDown)
    renderer.domElement.removeEventListener('click', onCanvasClick)
    if (renderer.domElement.parentNode) renderer.domElement.parentNode.removeChild(renderer.domElement)
    renderer.dispose()
  }
  if (dracoLoader) dracoLoader.dispose()
}

onMounted(async () => {
  await load()
  initThree()
  await loadPorts(false)
  pollTimer = setInterval(load, 3000)
})

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
  disposeThree()
})
</script>

<style scoped>
.page-title {
  font-size: 18px;
  font-weight: 600;
  color: #d8dee9;
  letter-spacing: 1px;
  margin-bottom: 12px;
}
.port-card {
  margin-bottom: 12px;
}
.port-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.port-label {
  color: #aab4c8;
  font-size: 13px;
}
.identify-result {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 16px;
  margin-top: 10px;
}
.identify-item {
  display: flex;
  align-items: center;
  gap: 6px;
}
.val-humidity {
  color: #409eff;
  font-size: 16px;
}
.val-temp {
  color: #e6a23c;
  font-size: 16px;
}
.model-card {
  margin-bottom: 12px;
}
.canvas-wrap {
  position: relative;
  height: 430px;
}
.three-canvas {
  width: 100%;
  height: 100%;
}
.model-error {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  color: #f56c6c;
  text-align: center;
}
.legend {
  display: flex;
  gap: 16px;
  margin-top: 10px;
  color: #aab4c8;
  font-size: 12px;
}
.legend .dot {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  margin-right: 4px;
}
.hint {
  margin-left: 10px;
  color: #909399;
  font-size: 12px;
}
.val-alarm {
  color: #f56c6c;
  font-weight: 600;
}
</style>