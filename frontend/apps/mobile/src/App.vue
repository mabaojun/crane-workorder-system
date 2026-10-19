<script setup lang="ts">
import { onLaunch } from '@dcloudio/uni-app'
import { loadBranding } from '@crane/shared/api/branding'

onLaunch(async () => {
  // 启动即拉取系统名等公开配置（失败走共享层兜底），登录页与品牌条动态展示
  await loadBranding()
})
</script>

<style>
@import './styles/admin.css';

/* 全局设计令牌（11-前端设计手册 §2/§4） */
page {
  --brand: #1a6bff;
  --brand-deep: #0b1f3a;
  --brand-weak: #e8f1ff;
  --cyan: #17b7d4;
  --bg: #f2f6fc;
  --panel: #ffffff;
  --line: #d9e4f2;
  --txt: #0e1b33;
  --sub: #5f708a;
  --danger: #dc2626;
  --success: #16a34a;
  --warning: #d97706;
  --radius: 12px;
  --radius-lg: 16px;
  --shadow: 0 2px 12px rgba(15, 31, 61, 0.06);
  background-color: var(--bg);
  color: var(--txt);
  font-family: 'PingFang SC', 'Microsoft YaHei', system-ui, sans-serif;
  font-size: 16px;
  line-height: 1.65;
}

button {
  font-family: inherit;
}
</style>
