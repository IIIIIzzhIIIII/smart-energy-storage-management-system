<template>
  <el-container class="layout">
    <el-aside width="220px" class="aside">
      <div class="logo">
        <span>⚡ 光储调度平台</span>
      </div>
      <el-menu
        :default-active="$route.path"
        router
        background-color="#0a1122"
        text-color="#a6adb4"
        active-text-color="#00e5ff"
      >
        <el-menu-item index="/dashboard">
          <el-icon><Monitor /></el-icon>
          <span>实时大屏</span>
        </el-menu-item>
        <el-menu-item index="/device">
          <el-icon><Cpu /></el-icon>
          <span>设备管理</span>
        </el-menu-item>
        <el-menu-item index="/safety">
          <el-icon><Warning /></el-icon>
          <span>储能安全检测</span>
        </el-menu-item>
        <el-menu-item index="/history">
          <el-icon><Search /></el-icon>
          <span>历史数据</span>
        </el-menu-item>
        <el-menu-item index="/strategy">
          <el-icon><Setting /></el-icon>
          <span>调度策略</span>
        </el-menu-item>
        <el-menu-item index="/alarm">
          <el-icon><Bell /></el-icon>
          <span>告警管理</span>
        </el-menu-item>
        <el-menu-item index="/report">
          <el-icon><Document /></el-icon>
          <span>统计报表</span>
        </el-menu-item>
        <el-menu-item v-if="userStore.isAdmin" index="/user">
          <el-icon><User /></el-icon>
          <span>用户管理</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="header-title">{{ $route.meta.title || '光储智慧调度监控平台' }}</div>
        <div class="header-user">
          <el-tag size="small" :type="tagType">{{ roleText }}</el-tag>
          <el-dropdown @command="handleCommand">
            <span class="username">
              {{ userStore.user ? userStore.user.nickname || userStore.user.username : '' }}
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../store/user'

const userStore = useUserStore()
const router = useRouter()

const roleText = computed(() => {
  const map = { ADMIN: '管理员', DISPATCHER: '调度员', USER: '普通用户', VIEWER: '只读用户' }
  return userStore.user ? map[userStore.user.role] || userStore.user.role : ''
})

const tagType = computed(() => {
  const map = { ADMIN: 'danger', DISPATCHER: 'warning', USER: 'success', VIEWER: 'info' }
  return userStore.user ? map[userStore.user.role] || 'info' : 'info'
})

function handleCommand(command) {
  if (command === 'logout') {
    userStore.logout()
    router.push('/login')
  }
}

// 全局 WebSocket 连接：接收实时数据与告警（供大屏和告警页使用）
let ws = null
onMounted(() => {
  connectWs()
})
onUnmounted(() => {
  if (ws) ws.close()
})

function connectWs() {
  const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws'
  ws = new WebSocket(`${protocol}://${window.location.host}/ws/monitor`)
  ws.onmessage = (event) => {
    try {
      const data = JSON.parse(event.data)
      // 有告警时实时提示
      if (data && data.type === 'alarm') {
        ElMessage.warning('新告警: ' + (data.data && data.data.message))
      }
    } catch (e) {
      // 普通实时数据，忽略（由大屏自行处理）
    }
  }
  ws.onclose = () => {
    // 断线重连
    setTimeout(connectWs, 3000)
  }
}
</script>

<style scoped>
.layout {
  height: 100vh;
}
.aside {
  background-color: #0a1122;
  border-right: 1px solid rgba(0, 229, 255, 0.15);
}
.logo {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 1px;
  text-shadow: 0 0 12px rgba(0, 229, 255, 0.6);
  border-bottom: 1px solid rgba(0, 229, 255, 0.15);
}
.aside :deep(.el-menu) {
  border-right: none;
  background-color: transparent;
}
.header {
  background-color: rgba(11, 17, 34, 0.85);
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid rgba(0, 229, 255, 0.2);
  backdrop-filter: blur(6px);
}
.header-title {
  font-size: 18px;
  font-weight: 600;
  color: #d8dee9;
  letter-spacing: 1px;
}
.header-user {
  display: flex;
  align-items: center;
  gap: 10px;
}
.username {
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 4px;
  color: #aab4c8;
}
.main {
  padding: 16px;
  background-color: transparent;
}
</style>