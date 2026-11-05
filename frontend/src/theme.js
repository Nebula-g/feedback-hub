/**
 * Naive UI 主题令牌覆盖（浅色 · 玻璃拟态）
 *
 * <p><b>为什么需要这个文件</b>：
 * Naive UI 自带的默认浅色主题是一套中性灰蓝配色，偏"办公软件"，
 * 与本项目要的「马卡龙蓝紫 + 毛玻璃 + 柔和阴影」观感差距不小。
 * 把设计决策集中到这一个文件里，改一处全站生效，
 * 避免在几十个组件里散落硬编码色值。
 *
 * <p><b>配色逻辑</b>：
 * <ul>
 *   <li>背景不设纯色：由 AuroraBackground 的动态极光负责，
 *       组件背景一律半透明白 + backdrop-filter 做毛玻璃</li>
 *   <li>主色 #6c7bf5：低饱和的periwinkle蓝紫，白底上足够醒目又不刺眼</li>
 *   <li>文本三层：#252a41（正文）→ #5d6478（次要）→ #8b92a7（弱化），
 *       全部保证在 0.72 透明度玻璃上仍有足够对比度</li>
 *   <li>语义色按中国习惯：危险/错误用红，成功用绿，警示用琥珀</li>
 * </ul>
 *
 * <p><b>与 global.css 的分工</b>：
 * 这里只放 Naive UI 能识别的令牌；毛玻璃的 backdrop-filter、
 * 多层阴影这类组件库不支持的属性，统一写在 global.css 的
 * .glass / .n-card 规则里。两处色值必须保持一致，改动时同步。
 */

/** 画布与容器背景 */
export const colors = {
  /** 最底层画布（被极光背景盖住，仅作兜底） */
  canvas: '#eef1fb',
  /** 玻璃卡片背景 */
  surface: 'rgba(255, 255, 255, 0.72)',
  /** 高实度卡片（弹窗、悬浮层，保证文字可读） */
  elevated: 'rgba(255, 255, 255, 0.94)',
  /** 输入框背景 */
  input: 'rgba(255, 255, 255, 0.86)',
  /** 卡片描边（白色高光边，玻璃感的关键） */
  border: 'rgba(255, 255, 255, 0.72)',
  /** 常规分割线 / 输入框描边 */
  borderSoft: 'rgba(122, 136, 190, 0.18)',
  /** 表格、列表的分隔线 */
  divider: 'rgba(122, 136, 190, 0.12)',

  /** 主文本 */
  textPrimary: '#252a41',
  /** 次要文本 */
  textSecondary: '#5d6478',
  /** 弱化文本（占位符、时间戳） */
  textTertiary: '#8b92a7',

  /** 品牌主色 */
  primary: '#6c7bf5',
  /** 主色悬浮态 */
  primaryHover: '#7d8bf8',
  /** 主色按下态 */
  primaryPressed: '#5a69e0',
  /** 渐变副色（强调、图形） */
  accent: '#a78bfa',

  /** 成功：绿 */
  success: '#33ad74',
  /** 警示：琥珀 */
  warning: '#e0902f',
  /** 危险/错误：红 */
  danger: '#e2556b',
  /** 信息：蓝 */
  info: '#4b8ef0'
}

/** 优先级配色映射（与后端 TaskPriority 枚举对应） */
export const priorityColors = {
  P0: colors.danger, // 阻塞主流程 —— 最高警示，用红色
  P1: colors.warning, // 影响体验 —— 次高，用琥珀
  P2: colors.textTertiary // 优化类 —— 弱化，用灰色
}

/** 图表用的柔和马卡龙色序（低饱和，白底可读） */
export const chartPalette = ['#8b9bfb', '#b0a4fb', '#7cc2f8', '#8fd9c4', '#f3b7c6', '#f6cf9a']

/**
 * Naive UI 主题覆盖对象
 *
 * <p>结构与 Naive UI 官方文档的 GlobalThemeOverrides 一致。
 * 只覆盖真正需要改的令牌，其余沿用 lightTheme 默认值。
 */
export const themeOverrides = {
  common: {
    // ---------- 主色系 ----------
    primaryColor: colors.primary,
    primaryColorHover: colors.primaryHover,
    primaryColorPressed: colors.primaryPressed,
    primaryColorSuppl: colors.primaryHover,

    // ---------- 语义色 ----------
    successColor: colors.success,
    successColorHover: '#45bd84',
    successColorPressed: '#2b9a66',
    successColorSuppl: '#45bd84',

    warningColor: colors.warning,
    warningColorHover: '#eda245',
    warningColorPressed: '#c87c1f',
    warningColorSuppl: '#eda245',

    errorColor: colors.danger,
    errorColorHover: '#ee6c80',
    errorColorPressed: '#cb455b',
    errorColorSuppl: '#ee6c80',

    infoColor: colors.info,
    infoColorHover: '#63a1f5',
    infoColorPressed: '#3a7ad6',
    infoColorSuppl: '#63a1f5',

    // ---------- 文本 ----------
    textColorBase: colors.textPrimary,
    textColor1: colors.textPrimary,
    textColor2: colors.textSecondary,
    textColor3: colors.textTertiary,

    // ---------- 背景与边框 ----------
    bodyColor: 'transparent',
    cardColor: colors.surface,
    modalColor: colors.elevated,
    popoverColor: colors.elevated,
    tableColor: 'transparent',
    inputColor: colors.input,
    inputColorDisabled: 'rgba(255, 255, 255, 0.5)',
    borderColor: colors.borderSoft,
    dividerColor: colors.divider,
    tableHeaderColor: 'rgba(255, 255, 255, 0.55)',
    actionColor: 'rgba(255, 255, 255, 0.5)',
    hoverColor: 'rgba(108, 123, 245, 0.06)',

    // ---------- 圆角：卡片大、控件小，层次才分得开 ----------
    borderRadius: '10px',
    borderRadiusSmall: '8px',

    // ---------- 字体：优先思源黑体，缺失时回落系统字体 ----------
    fontFamily:
      "'Source Han Sans SC', 'Noto Sans SC', 'PingFang SC', " +
      "'Microsoft YaHei', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif",
    fontFamilyMono:
      "'JetBrains Mono', 'SF Mono', Menlo, Consolas, 'Courier New', monospace",
    fontSize: '14px',

    // ---------- 高度 ----------
    heightSmall: '30px',
    heightMedium: '36px',
    heightLarge: '44px'
  },

  Card: {
    borderRadius: '20px',
    color: colors.surface,
    borderColor: colors.border,
    titleTextColor: colors.textPrimary,
    textColor: colors.textSecondary,
    paddingMedium: '20px 22px'
  },
  DataTable: {
    borderColor: colors.divider,
    thColor: 'rgba(255, 255, 255, 0.5)',
    thTextColor: colors.textSecondary,
    tdColor: 'transparent',
    tdColorHover: 'rgba(255, 255, 255, 0.62)',
    tdTextColor: colors.textSecondary,
    borderRadius: '12px',
    thFontWeight: '600'
  },
  Input: {
    color: colors.input,
    colorFocus: '#ffffff',
    borderColor: colors.borderSoft,
    borderColorHover: 'rgba(108, 123, 245, 0.45)',
    borderColorFocus: colors.primary,
    boxShadowFocus: '0 0 0 3px rgba(108, 123, 245, 0.14)',
    placeholderColor: colors.textTertiary,
    borderRadius: '12px'
  },
  Button: {
    borderRadiusMedium: '12px',
    borderRadiusLarge: '14px',
    fontWeight: '600'
  },
  Menu: {
    itemTextColor: colors.textSecondary,
    itemTextColorHover: colors.primary,
    itemTextColorActive: '#ffffff',
    itemTextColorChildActive: colors.primary,
    itemColorActive: colors.primary,
    itemColorActiveHover: colors.primary,
    itemColorHover: 'rgba(108, 123, 245, 0.08)',
    itemIconColor: colors.textTertiary,
    itemIconColorHover: colors.primary,
    itemIconColorActive: '#ffffff',
    itemBorderRadius: '12px',
    borderRadius: '12px'
  },
  Tag: {
    borderRadius: '8px',
    heightSmall: '22px'
  },
  Pagination: {
    itemBorderRadius: '8px',
    itemColorActive: colors.primary,
    itemTextColorActive: '#ffffff'
  },
  Dialog: {
    color: colors.elevated,
    borderRadius: '20px',
    titleTextColor: colors.textPrimary,
    textColor: colors.textSecondary
  },
  Popover: {
    color: colors.elevated,
    borderRadius: '14px',
    textColor: colors.textPrimary
  },
  Tooltip: {
    borderRadius: '8px'
  }
}
