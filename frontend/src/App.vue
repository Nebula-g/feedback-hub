<script setup>
/**
 * 根组件
 *
 * <p>职责只有两件：
 * <ol>
 *   <li>挂载 Naive UI 的全局配置容器，统一管理主题、语言与消息提示位置</li>
 *   <li>渲染路由出口</li>
 * </ol>
 *
 * <p>为什么不在这里做布局：
 * 登录页不应当带有侧边栏和顶栏，所以布局放在 MainLayout 里，
 * 由路由决定哪些页面套用布局，这里保持极简。
 */
import {
  NConfigProvider,
  NMessageProvider,
  NDialogProvider,
  NNotificationProvider,
  NLoadingBarProvider,
  lightTheme,
  dateZhCN,
  zhCN
} from 'naive-ui'

import { themeOverrides } from './theme'
</script>

<template>
  <!--
    NConfigProvider 是 Naive UI 的全局配置入口。
    theme=lightTheme 使用浅色底版，themeOverrides 覆盖为玻璃拟态配色。
    这样一次配置，全站所有组件外观统一。
  -->
  <n-config-provider
    :theme="lightTheme"
    :theme-overrides="themeOverrides"
    :locale="zhCN"
    :date-locale="dateZhCN"
  >
    <!--
      四个 Provider 分别对应四种全局交互：
      - 消息提示（message）：操作成功/失败的轻提示
      - 对话框（dialog）：危险操作的二次确认
      - 通知（notification）：AI 调用结果这类需要停留更久的信息
      - 加载条（loading bar）：请求进行中的顶部进度条
      集中放在这里，业务组件里用 useMessage() 即可调用。
    -->
    <n-loading-bar-provider>
      <n-dialog-provider>
        <n-notification-provider>
          <n-message-provider>
            <router-view />
          </n-message-provider>
        </n-notification-provider>
      </n-dialog-provider>
    </n-loading-bar-provider>
  </n-config-provider>
</template>
