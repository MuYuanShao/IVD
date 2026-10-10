<template>
  <div class="login-container">

    <div class="welcome-text">
      欢迎使用我们平台
    </div>

    <el-card class="login-card" shadow="always">
      <div class="login-header">
        <h1 class="login-title">IVD 智慧实验室管理系统</h1>
        <p class="login-subtitle">体外诊断试剂全生命周期追溯平台</p>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" label-width="0" size="large">
        <el-form-item prop="username">
          <el-input
            v-model="form.username"
            placeholder="请输入用户名"
            :prefix-icon="User"
            @keyup.enter="handleLogin"
          />
        </el-form-item>

        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            :prefix-icon="Lock"
            show-password
            @keyup.enter="handleLogin"
          />
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            style="width: 100%"
            :loading="loading"
            @click="handleLogin"
          >
            {{ loading ? '登录中...' : '登 录' }}
          </el-button>
        </el-form-item>
      </el-form>

      <div class="login-footer">
        <span>IVD Team © 2026</span>
      </div>
    </el-card>

    <div class="login-links">
      <span>关于我们</span>
      <span class="divider">|</span>
      <span>备案号</span>
    </div>

  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const formRef = ref(null)
const loading = ref(false)
const form = reactive({ username: '', password: '' })

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function handleLogin() {
  if (loading.value) return
  console.log('[LoginView] 开始登录流程, username:', form.username)

  // 确保表单已挂载
  if (!formRef.value) {
    console.error('[LoginView] formRef 为空，页面未就绪')
    ElMessage.error('页面未就绪，请刷新后重试')
    return
  }

  const valid = await formRef.value.validate().catch((err) => {
    console.error('[LoginView] 表单验证失败:', err)
    return false
  })
  if (!valid) {
    console.warn('[LoginView] 表单验证未通过')
    return
  }

  // 校验期间可能再次触发 Enter，避免两个校验完成后重复提交。
  if (loading.value) return

  loading.value = true

  try {
    await authStore.login(form.username, form.password)
    console.log('[LoginView] authStore.login 完成')
    ElMessage.success('登录成功')

    const redirect = route.query.redirect || '/dashboard'
    console.log('[LoginView] 准备跳转到:', redirect, 'route.query:', route.query)
    const navigationFailure = await router.push(redirect)
    if (navigationFailure) {
      ElMessage.warning('页面跳转未完成，请重新尝试')
    }
  } catch (err) {
    console.error('[LoginView] 登录失败:', err)
    ElMessage.error(err?.message || '登录失败，请检查用户名和密码')
  } finally {
    loading.value = false
  }
}
</script>


<style scoped>
.login-container {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background-image: url('/bc.jpg');
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
  position: relative;
  overflow: hidden;
}

.welcome-text {
  position: absolute;
  top: 16%;
  left: 100%;
  transform: translateX(0);

  white-space: nowrap;

  font-size: 32px;
  font-weight: 800;
  letter-spacing: 4px;

  color: rgba(255,255,255,.95);

  text-shadow:
    0 4px 12px rgba(0,0,0,.25),
    0 0 20px rgba(16,185,129,.35);

  animation: welcomeMove 3s ease forwards;

  z-index: 3;
}


@keyframes welcomeMove {
  0% {
    left: 100%;
  }

  70% {
    left: 50%;
    transform: translateX(-50%);
  }

  100% {
    left: 50%;
    transform: translateX(-50%);
  }
}


.login-container::before {
  content: '';
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(255,255,255,.035) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255,255,255,.035) 1px, transparent 1px);
  background-size: 45px 45px;
  mask-image: radial-gradient(circle, black 30%, transparent 75%);
}

.login-container::after {
  content: '';
  position: absolute;
  width: 520px;
  height: 520px;
  border-radius: 50%;
  top: -220px;
  left: -160px;

  background:
    radial-gradient(
      circle,
      rgba(239,68,68,.28) 0%,
      rgba(239,68,68,.12) 25%,
      transparent 70%
    );

  filter: blur(10px);
}


.login-card {
  width: 420px;
  padding: 8px;
  border-radius: 24px;
  border: 1px solid rgba(255,255,255,.35);

  background:
    linear-gradient(
      145deg,
      rgba(255,255,255,.96),
      rgba(255,255,255,.88)
    );

  backdrop-filter: blur(20px);

  box-shadow:
    0 25px 60px rgba(0,0,0,.35),
    0 0 40px rgba(16,185,129,.12);

  position: relative;
  z-index: 2;

  transition: all .35s ease;
}


.login-card:hover {
  transform: translateY(-6px);

  box-shadow:
    0 35px 70px rgba(0,0,0,.4),
    0 0 50px rgba(16,185,129,.2);
}


.login-header {
  text-align: center;
  margin-bottom: 34px;
  position: relative;
}


.login-header::before {
  content: '';

  display: block;

  width: 42px;
  height: 52px;

  margin: 0 auto 14px;

  background:
    linear-gradient(
      180deg,
      #ef4444,
      #b91c1c
    );

  border-radius:
    50% 50% 50% 0;

  transform: rotate(-45deg);

  box-shadow:
    0 8px 20px rgba(239,68,68,.35);

  position: relative;
}


.login-header::after {
  content:'';

  position:absolute;

  width:12px;
  height:18px;

  background:rgba(255,255,255,.45);

  border-radius:50%;

  transform:translate(8px,8px);
}


.login-title {
  font-size:24px;
  font-weight:800;
  color:#0f172a;

  margin:0 0 10px;

  letter-spacing:1.5px;

  background:
    linear-gradient(
      90deg,
      #059669,
      #0f766e
    );

  -webkit-background-clip:text;
  color:transparent;
}


.login-subtitle {
  font-size:13px;
  color:#64748b;

  margin:0;

  letter-spacing:.5px;
}


:deep(.el-input__wrapper) {
  height:44px;

  border-radius:12px;

  background:rgba(248,250,252,.8);

  box-shadow:
    0 0 0 1px #e2e8f0 inset;

  transition:.25s ease;
}


:deep(.el-input__wrapper:hover) {
  box-shadow:
    0 0 0 1px #10b981 inset;
}


:deep(.el-input__wrapper.is-focus) {
  box-shadow:
    0 0 0 2px rgba(16,185,129,.25) inset;
}


:deep(.el-input__prefix-inner) {
  color:#059669;
}


:deep(.el-button--primary) {

  height:46px;

  border-radius:12px;

  border:none;

  font-size:16px;

  font-weight:600;

  letter-spacing:4px;


  background:
    linear-gradient(
      135deg,
      #059669,
      #10b981
    );


  box-shadow:
    0 10px 20px rgba(16,185,129,.25);


  transition:.3s ease;
}


:deep(.el-button--primary:hover) {

  transform:translateY(-2px);


  background:
    linear-gradient(
      135deg,
      #047857,
      #059669
    );


  box-shadow:
    0 15px 30px rgba(16,185,129,.35);
}


.login-footer {

  text-align:center;

  font-size:12px;

  color:#94a3b8;

  margin-top:26px;

  padding-top:18px;

  border-top:
    1px solid rgba(148,163,184,.25);
}


.login-links {

  position:absolute;

  bottom:32px;

  left:50%;

  transform:translateX(-50%);

  display:flex;

  align-items:center;

  gap:14px;

  color:rgba(255,255,255,.85);

  font-size:14px;

  letter-spacing:1px;

  z-index:3;

  padding:8px 22px;

  border-radius:20px;

  background:rgba(255,255,255,.12);

  backdrop-filter:blur(10px);

  transition:.3s ease;
}


.login-links:hover {

  background:rgba(44, 23, 233, 0.22);

  color:#fff;
}
.login-links span:hover {
  color: blue;
  font-size: 16px;
}


.divider {

  opacity:.6;
}


.login-card::before,
.login-card::after {

  content:'';

  position:absolute;

  border-radius:50%;

  pointer-events:none;
}


.login-card::before {

  width:80px;

  height:80px;

  right:-40px;

  top:-40px;


  background:
    radial-gradient(
      circle,
      rgba(239,68,68,.15),
      transparent 70%
    );
}


.login-card::after {

  width:120px;

  height:120px;

  left:-60px;

  bottom:-60px;


  background:
    radial-gradient(
      circle,
      rgba(16,185,129,.15),
      transparent 70%
    );
}
</style>
