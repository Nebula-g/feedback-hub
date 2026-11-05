<script setup>
/**
 * 登录页动态背景（紫蓝流体光带漂移 + 左侧玻璃几何呼吸）
 *
 * <p><b>为什么是着色器而不是 CSS 分层</b>：早先的版本把同一张图拷成四份，
 * 用椭圆遮罩分别露出「光带 / 几何体」再各自位移。这样做的代价是——只要有遮罩，
 * 边缘就存在一份与原图不完全对齐的第二次采样，视觉上就是重影、错位，
 * 在玻璃卡片那种高对比细节处尤其明显。
 *
 * <p>这里改成<b>只采样一次贴图</b>，由片元着色器逐像素计算位移场：
 * <ol>
 *   <li>三组不同频率的正弦叠加成平滑流场，模拟丝绸被缓慢推动的形变；</li>
 *   <li>左侧玻璃图标所在区域用圆角矩形掩码做成<b>完全静止</b>：掩码内不参与
 *       流场位移、不参与镜头推近与平移、不叠高光、不吃暗角，采样坐标严格等于
 *       静态底图的倍率，所以它是原图里的那个图标本身，一个像素都没动；</li>
 *   <li>亮部叠加一层沿光带方向移动的高光，模拟丝绸反光扫过。</li>
 * </ol>
 * 图标"冻住"、丝带在后面流动，视觉上就是一块玻璃摆件立在一幅会飘的背景前，
 * 这比让图标跟着背景一起呼吸更像真实物体。
 * 单次采样也就没有重影，并且体积只有原图 150KB（视频要 2MB 起）。
 *
 * <p><b>无缝循环是怎么保证的</b>：所有随时间变化的项都写成
 * {@code sin(k * 2π * t / T)}（k 为整数），在 t=T 时相位整整转过 k 圈，
 * 与 t=0 数学上完全同值。因此不需要"凑首尾帧"，也不存在跳帧。
 *
 * <p><b>降级策略</b>：没有 WebGL（或上下文丢失且无法恢复）时给容器加
 * {@code bd--fallback}，退回静态底图 + 极缓放大的 CSS 动画，
 * 保证登录页在任何环境下都不是白屏。
 */
import { onMounted, onBeforeUnmount, ref } from 'vue'
import bgUrl from '@/assets/login-bg.jpg'

/** 片段着色器：片元坐标 → cover 映射 → 流场位移 → 采样 */
const FRAG_SRC = `
precision highp float;

varying vec2 vUv;

uniform sampler2D uTex;
uniform vec2  uRes;      // 画布像素尺寸
uniform vec2  uImg;      // 贴图原始尺寸
uniform float uTime;     // 已运行秒数
uniform float uPeriod;   // 循环周期（秒）

const float TAU = 6.28318530718;

// 与 background-size: cover 一致的裁切：屏幕 uv → 贴图 uv。
// 取景中心默认在正中，竖屏时向左侧插画偏移——否则窄屏按 cover 裁完只剩中间一条，
// 左边那组玻璃几何体整个被裁掉，登录页会变成纯色渐变。
vec2 coverUv(vec2 uv) {
  float ca = uRes.x / uRes.y;
  float ia = uImg.x / uImg.y;
  vec2 s = ca > ia ? vec2(1.0, ia / ca) : vec2(ca / ia, 1.0);

  float narrow = smoothstep(1.60, 0.90, ca);       // 宽屏 0 → 竖屏 1
  vec2 focus = vec2(mix(0.5, 0.30, narrow), 0.5);
  return (uv - focus) * s + focus;
}

// 圆角矩形的有符号距离：<0 在内部，>0 在外部。
// 只做掩码，不追求严格的欧氏距离，够平滑即可。
float sdRoundBox(vec2 q, vec2 b, float r) {
  vec2 d = abs(q) - b + r;
  return length(max(d, 0.0)) + min(max(d.x, d.y), 0.0) - r;
}

// 静态底图（.bd::before 的 scale(1.03)）所对应的采样坐标。
// 图标区域必须用这个倍率，才能和底图逐像素重合——放大倍数一变，图标就会
// 相对底图"挪位置"，等于还是在动。
const float BASE_ZOOM = 1.03;

// 图标掩码：中心与半轴是在等比坐标（等比于贴图宽高）里量出来的，
// 覆盖卡片叠层、心形徽章、两个小方块、底座圆盘与外圈，
// 掩码内一点点外扩余量，避免图标边缘的高光被切到。
const vec2 ICON_CENTER = vec2(-0.400, 0.015);
const vec2 ICON_HALF   = vec2( 0.320, 0.305);
const float ICON_RADIUS = 0.180;
// 掩码外的过渡带宽：太窄会在图标周围留下一圈"被拉扯"的硬边，
// 太宽又会把左侧丝带一起冻住，0.12 大约是图标半宽的 1/3
const float ICON_FEATHER = 0.120;

void main() {
  float ia = uImg.x / uImg.y;

  // 归一化相位：t 与 t + uPeriod 相差整数圈
  float t = (uTime / uPeriod) * TAU;

  vec2 im = coverUv(vUv);

  // ---------- 0. 图标静态区掩码 ----------
  // 在等比坐标里算，横向纵向的"距离"单位一致，圆角矩形才不会在宽屏上被拉扁。
  // hold = 1 完全静止，0 完全交给动效，中间是过渡带
  vec2 ip = vec2((im.x - 0.5) * ia, im.y - 0.5);
  float sd = sdRoundBox(ip - ICON_CENTER, ICON_HALF, ICON_RADIUS);
  float hold = 1.0 - smoothstep(0.0, ICON_FEATHER, sd);

  // ---------- 1. 镜头：极缓推近 + 不足 1% 的位移 ----------
  // 下限 1.03 与容器静态底图的 scale(1.03) 对齐：
  // 首帧和底图完全重合，画布接管时不会跳一下；同时留出位移余量，不漏边
  float zoom = 1.03 + 0.032 * (0.5 - 0.5 * cos(t));
  vec2 cam = (im - 0.5) / zoom + 0.5;
  // 相位从 0 起步（sin(t) 而非 sin(t + φ)），t=0 时与静态底图严格重合
  cam += vec2(0.0050 * sin(t), 0.0032 * sin(2.0 * t));
  // 图标区把镜头拉回底图倍率：推近和漂移都不作用在它身上
  cam = mix(cam, (im - 0.5) / BASE_ZOOM + 0.5, hold);

  // ---------- 2. 流体位移场 ----------
  // 在"等比坐标"里算，横竖波长一致，光带才不会被拉扁
  vec2 p = vec2((im.x - 0.5) * ia, im.y - 0.5);

  vec2 d = vec2(0.0);
  // 低频主波：光带整体的缓慢起伏
  d += vec2(sin(p.y * 4.1 + t), cos(p.x * 3.4 - t)) * 0.0108;
  // 中频：让丝绸褶皱有二次摆动
  d += vec2(sin(p.y * 7.6 + 2.0 * t + 1.7), cos(p.x * 6.2 - 2.0 * t + 0.4)) * 0.0056;
  // 高频：很小的一层碎波，避免画面显得"塑料"
  d += vec2(sin((p.x + p.y) * 11.5 + 3.0 * t + 2.4),
            cos((p.x - p.y) * 9.8 - 3.0 * t)) * 0.0024;

  // 图标区域内的形变整个归零：连"压掉 78%"都不保留，
  // 保证掩码里的像素和原图完全一致
  d *= 1.0 - hold;

  vec2 uv = clamp(cam + vec2(d.x / ia, d.y), vec2(0.002), vec2(0.998));
  vec3 col = texture2D(uTex, uv).rgb;

  // ---------- 3. 丝绸高光扫过 ----------
  // 只抬亮部（lum > 0.55 才起作用），暗部不动，
  // 否则整屏亮度一起走，会变成"呼吸灯"而不是反光；
  // 图标区不吃高光，否则静止的图标会跟着一起忽明忽暗
  float lum = dot(col, vec3(0.299, 0.587, 0.114));
  float sheen = sin(dot(p, vec2(1.6, -0.9)) * 3.2 - t) * 0.5 + 0.5;
  col += (sheen - 0.5) * 0.055 * smoothstep(0.58, 0.98, lum) * (1.0 - hold);

  // ---------- 4. 轻暗角 ----------
  // 图标区跳过压暗，保持原图亮度
  float vig = smoothstep(1.30, 0.30, length((vUv - 0.5) * vec2(uRes.x / uRes.y, 1.0)));
  col *= mix(0.972, 1.0, mix(vig, 1.0, hold));

  gl_FragColor = vec4(col, 1.0);
}
`

/** 顶点着色器：一个铺满裁剪空间的四边形，直接把位置当 uv 用 */
const VERT_SRC = `
attribute vec2 aPos;
varying vec2 vUv;

void main() {
  vUv = aPos * 0.5 + 0.5;
  gl_Position = vec4(aPos, 0.0, 1.0);
}
`

/** 一个循环周期：26s 比参考视频的 15s 更慢，静态页面上的动效宜更克制 */
const PERIOD = 26

/** 帧率上限：这类极慢的画面上限 30fps 完全够用，省电也省风扇 */
const FRAME_INTERVAL = 1000 / 30

const canvasRef = ref(null)
/** WebGL 不可用时置 true，容器退回 CSS 静态底图 */
const useFallback = ref(false)

// ---------- 运行时句柄（不需要响应式，避免每帧触发依赖收集） ----------
let gl = null
let program = null
let uniforms = {}
let texture = null
let rafId = 0
let lastPaint = 0
let startAt = 0
let running = false
let reduceMotion = false
let resizeObserver = null
let imgLoaded = false

/** 编译单个着色器，失败时抛出带原始日志的错误 */
function compileShader(type, source) {
  const shader = gl.createShader(type)
  gl.shaderSource(shader, source)
  gl.compileShader(shader)
  if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) {
    const log = gl.getShaderInfoLog(shader)
    gl.deleteShader(shader)
    throw new Error(`着色器编译失败: ${log}`)
  }
  return shader
}

/** 建立 program、顶点缓冲与 uniform 位置表 */
function buildPipeline() {
  const vs = compileShader(gl.VERTEX_SHADER, VERT_SRC)
  const fs = compileShader(gl.FRAGMENT_SHADER, FRAG_SRC)

  program = gl.createProgram()
  gl.attachShader(program, vs)
  gl.attachShader(program, fs)
  gl.linkProgram(program)
  if (!gl.getProgramParameter(program, gl.LINK_STATUS)) {
    throw new Error(`着色器链接失败: ${gl.getProgramInfoLog(program)}`)
  }
  gl.deleteShader(vs)
  gl.deleteShader(fs)
  gl.useProgram(program)

  // 两个三角形拼成的全屏四边形
  const buffer = gl.createBuffer()
  gl.bindBuffer(gl.ARRAY_BUFFER, buffer)
  gl.bufferData(
    gl.ARRAY_BUFFER,
    new Float32Array([-1, -1, 1, -1, -1, 1, 1, 1]),
    gl.STATIC_DRAW
  )
  const aPos = gl.getAttribLocation(program, 'aPos')
  gl.enableVertexAttribArray(aPos)
  gl.vertexAttribPointer(aPos, 2, gl.FLOAT, false, 0, 0)

  uniforms = {
    tex: gl.getUniformLocation(program, 'uTex'),
    res: gl.getUniformLocation(program, 'uRes'),
    img: gl.getUniformLocation(program, 'uImg'),
    time: gl.getUniformLocation(program, 'uTime'),
    period: gl.getUniformLocation(program, 'uPeriod')
  }
  gl.uniform1f(uniforms.period, PERIOD)
}

/** 把底图上传成纹理（非 2 的幂，只能用线性过滤 + 边缘钳制） */
function uploadTexture(image) {
  texture = gl.createTexture()
  gl.bindTexture(gl.TEXTURE_2D, texture)
  gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL, true)
  gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, image)
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE)
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE)
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR)
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR)
  gl.uniform1i(uniforms.tex, 0)
  gl.uniform2f(uniforms.img, image.naturalWidth, image.naturalHeight)
}

/** 按 CSS 尺寸 * dpr 调整画布分辨率；dpr 上限 1.5，全屏绘制不必追求像素级 */
function syncCanvasSize() {
  const canvas = canvasRef.value
  if (!canvas || !gl) return false

  const dpr = Math.min(window.devicePixelRatio || 1, 1.5)
  const width = Math.max(1, Math.round(canvas.clientWidth * dpr))
  const height = Math.max(1, Math.round(canvas.clientHeight * dpr))

  if (canvas.width === width && canvas.height === height) return false

  canvas.width = width
  canvas.height = height
  gl.viewport(0, 0, width, height)
  gl.uniform2f(uniforms.res, width, height)
  return true
}

/** 画一帧 */
function paint(now) {
  if (!gl || !program || !imgLoaded) return
  const seconds = (now - startAt) / 1000
  gl.uniform1f(uniforms.time, seconds % PERIOD)
  gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4)
}

/** 主循环：限帧 + 页面不可见时自动停 */
function loop(now) {
  if (!running) return
  rafId = requestAnimationFrame(loop)
  if (now - lastPaint < FRAME_INTERVAL) return
  lastPaint = now
  paint(now)
}

function startLoop() {
  if (running || reduceMotion) return
  running = true
  lastPaint = 0
  rafId = requestAnimationFrame(loop)
}

function stopLoop() {
  running = false
  cancelAnimationFrame(rafId)
  rafId = 0
}

/** 页面切到后台就停，切回来继续（避免挂机时白烧 GPU） */
function handleVisibility() {
  if (document.hidden) {
    stopLoop()
  } else {
    startLoop()
  }
}

/** 上下文丢失：浏览器可能因为显存紧张回收，恢复后重建整条管线 */
function handleContextLost(event) {
  event.preventDefault()
  stopLoop()
}

function handleContextRestored() {
  try {
    buildPipeline()
    reloadTexture()
  } catch (error) {
    console.warn('[LoginBackdrop] 上下文恢复失败，退回静态底图', error)
    useFallback.value = true
  }
}

/** 重新把底图喂给当前上下文 */
function reloadTexture() {
  const image = new Image()
  image.decoding = 'async'
  image.onload = () => {
    uploadTexture(image)
    imgLoaded = true
    paint(performance.now())
    startLoop()
  }
  image.onerror = () => {
    useFallback.value = true
  }
  image.src = bgUrl
}

onMounted(() => {
  const canvas = canvasRef.value
  if (!canvas) return

  const motionQuery = window.matchMedia('(prefers-reduced-motion: reduce)')
  reduceMotion = motionQuery.matches

  gl = canvas.getContext('webgl', {
    // 用透明上下文：首帧画出来之前画布是"看不见"的，
    // 底下容器的静态底图会顶上来，不会闪一帧黑屏
    alpha: true,
    antialias: false,
    depth: false,
    stencil: false,
    powerPreference: 'low-power',
    preserveDrawingBuffer: false
  })

  if (!gl) {
    useFallback.value = true
    return
  }

  try {
    buildPipeline()
  } catch (error) {
    console.warn('[LoginBackdrop] 着色器初始化失败，退回静态底图', error)
    useFallback.value = true
    return
  }

  syncCanvasSize()
  startAt = performance.now()
  reloadTexture()

  resizeObserver = new ResizeObserver(() => {
    if (syncCanvasSize()) paint(performance.now())
  })
  resizeObserver.observe(canvas)

  document.addEventListener('visibilitychange', handleVisibility)
  canvas.addEventListener('webglcontextlost', handleContextLost)
  canvas.addEventListener('webglcontextrestored', handleContextRestored)
})

onBeforeUnmount(() => {
  stopLoop()
  resizeObserver?.disconnect()
  resizeObserver = null
  document.removeEventListener('visibilitychange', handleVisibility)

  const canvas = canvasRef.value
  canvas?.removeEventListener('webglcontextlost', handleContextLost)
  canvas?.removeEventListener('webglcontextrestored', handleContextRestored)

  if (gl) {
    if (texture) gl.deleteTexture(texture)
    if (program) gl.deleteProgram(program)
  }
  gl = null
  program = null
  texture = null
  imgLoaded = false
})
</script>

<template>
  <div
    class="bd"
    :class="{ 'bd--fallback': useFallback }"
    :style="{ '--bd-img': `url(${bgUrl})` }"
    aria-hidden="true"
  >
    <canvas ref="canvasRef" class="bd__canvas"></canvas>

    <!-- 右侧柔化：压低背景纹理，托住登录卡（层级在画布之上） -->
    <div class="bd__veil"></div>
  </div>
</template>

<style scoped>
.bd {
  position: fixed;
  inset: 0;
  z-index: 0;
  overflow: hidden;
  pointer-events: none;
  contain: strict;
  /* 贴图解码完成前的兜底色，避免首帧闪白 */
  background-color: #eef2ff;
}

/* 静态底层：倍率与着色器首帧（zoom = 1.03）严格一致。
 * 贴图解码、上下文丢失期间由它顶住，画布一接管两者几乎重合，
 * 所以既不会闪黑，也不会出现"两张图错位"的重影 */
.bd::before {
  content: '';
  position: absolute;
  inset: 0;
  background-image: var(--bd-img);
  background-position: center;
  background-repeat: no-repeat;
  background-size: cover;
  transform: scale(1.03);
}

.bd__canvas {
  position: absolute;
  inset: 0;
  display: block;
  width: 100%;
  height: 100%;
}

/* 降级：隐藏画布，让容器自己的静态底图露出来。
 * 这里刻意<b>不再</b>做缩放呼吸——那会连左侧图标一起缩放，
 * 与"图标必须等于原图、只有背景在动"的诉求相悖。
 * 没有 WebGL 时宁可整页静止，也不给一个动错地方的动效。 */
.bd--fallback .bd__canvas {
  display: none;
}

/* 右侧柔化：只做极轻的压纹理（最深处 0.06）。
 * 这一层曾经用到 0.14，结果玻璃卡背后的颜色被一起洗成灰白，
 * 卡片怎么调透明度都不"透"——毛玻璃的前提是背后的东西看得见。 */
.bd__veil {
  position: absolute;
  inset: 0;
  background: linear-gradient(
    100deg,
    rgba(246, 248, 255, 0) 45%,
    rgba(246, 248, 255, 0.02) 66%,
    rgba(246, 248, 255, 0.045) 86%,
    rgba(246, 248, 255, 0.06) 100%
  );
}
</style>
