<script setup lang="ts">
import { onLaunch } from '@dcloudio/uni-app'
import { loadBranding } from '@crane/shared/api/branding'

onLaunch(async () => {
  // 启动即拉取系统名等公开配置（失败走共享层兜底），登录页与品牌条动态展示
  await loadBranding()
})
</script>

<style>
/* 全局设计令牌（11-前端设计手册 §2/§4） */
page {
  --brand: #1d4ed8;
  --brand-weak: #dbeafe;
  --bg: #f5f7fa;
  --panel: #ffffff;
  --line: #e5e7eb;
  --txt: #1f2937;
  --sub: #6b7280;
  --danger: #dc2626;
  --success: #16a34a;
  --warning: #d97706;
  --radius: 8px;
  --radius-lg: 12px;
  --shadow: 0 1px 2px rgba(15, 23, 42, 0.06);
  background-color: var(--bg);
  color: var(--txt);
  font-family: 'PingFang SC', 'Microsoft YaHei', system-ui, sans-serif;
  font-size: 15px;
  line-height: 1.6;
}
</style>
