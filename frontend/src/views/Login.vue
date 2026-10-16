<template>
  <div class="login-page">
    <div class="login-box">
      <div class="login-title">
        <div class="logo-emblem">⚡</div>
        <h2>光储智慧调度监控平台</h2>
        <p>基于 Spring Boot 的智慧能源管理系统</p>
      </div>
      <el-form :model="form" :rules="rules" ref="formRef" @keyup.enter="handleLogin">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="账号" size="large">
            <template #prefix><el-icon><User /></el-icon></template>
          </el-input>
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码" size="large" show-password>
            <template #prefix><el-icon><Lock /></el-icon></template>
          </el-input>
        </el-form-item>
        <el-button type="primary" size="large" :loading="loading" @click="handleLogin" class="login-btn">
          登 录
        </el-button>
      </el-form>
      <div class="tips">
        <span>演示账号：admin / dispatcher / user / viewer</span>
        <span>密码均为 123456</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '../utils/request'
import { useUserStore } from '../store/user'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref()
const loading = ref(false)

const form = reactive({ username: 'admin', password: '123456' })
const rules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function handleLogin() {
  await formRef.value.validate()
  loading.value = true
  try {
    const res = await request.post('/auth/login', form)
    userStore.setLogin(res.data.token, res.data.user)
    ElMessage.success('登录成功')
    router.push('/dashboard')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #070b18;
  background-image:
    linear-gradient(rgba(0, 229, 255, 0.04) 1px, transparent 1px),
    linear-gradient(90deg, rgba(0, 229, 255, 0.04) 1px, transparent 1px),
    radial-gradient(1000px 600px at 50% -10%, rgba(0, 170, 255, 0.16), transparent 60%),
    linear-gradient(180deg, #070b18, #0b1122);
  background-size: 40px 40px, 40px 40px, auto, auto;
}
.login-box {
  width: 420px;
  padding: 40px;
  background: linear-gradient(180deg, rgba(26, 31, 54, 0.92), rgba(15, 20, 40, 0.92));
  border: 1px solid rgba(0, 229, 255, 0.3);
  border-radius: 12px;
  box-shadow: 0 0 40px rgba(0, 229, 255, 0.2), inset 0 0 30px rgba(0, 229, 255, 0.04);
}
.login-title {
  text-align: center;
  margin-bottom: 30px;
}
.logo-emblem {
  font-size: 48px;
  filter: drop-shadow(0 0 12px rgba(0, 229, 255, 0.6));
}
.login-title h2 {
  margin: 8px 0 4px;
  color: #d8dee9;
  letter-spacing: 2px;
}
.login-title p {
  margin: 0;
  font-size: 13px;
  color: #7a879e;
}
.login-btn {
  width: 100%;
  margin-top: 10px;
}
.tips {
  margin-top: 16px;
  text-align: center;
  font-size: 12px;
  color: #7a879e;
  display: flex;
  flex-direction: column;
}
</style>