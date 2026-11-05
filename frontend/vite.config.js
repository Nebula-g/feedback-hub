import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

/**
 * Vite 工程配置
 *
 * <p>关键点说明：
 * <ol>
 *   <li><b>dev 代理</b>：前端开发服务器跑在 5173，后端在 8081，
 *       浏览器直接请求 8081 会触发跨域（协议+域名+端口任一不同即是跨域）。
 *       这里用 Vite 的 proxy 把 /api 与 /uploads 转发给后端，
 *       开发阶段就不需要后端开 CORS 也能联调。</li>
 *   <li><b>生产构建输出 dist/</b>：由 Nginx 直接托管静态文件，
 *       再把 /api 反向代理到后端，这就是"前后端分离"的标准形态。</li>
 *   <li><b>base 设为 '/'</b>：保证 Nginx 以根路径部署时资源引用路径正确。</li>
 * </ol>
 */
export default defineConfig({
  base: '/',
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 5173,
    // 监听方式：显式绑定双栈地址。
    //
    // 为什么不写默认的 'localhost'：
    //   Node 在 Windows 上把 'localhost' 解析成 ::1 并只监听 IPv6，
    //   而浏览器访问 localhost 时 Windows 优先走 IPv4 (127.0.0.1)，
    //   结果连不上或连到别的进程，表现就是页面一片空白。
    //   绑到 '::' 会同时接受 IPv6 与 IPv4（IPv4 映射地址），
    //   无论浏览器解析到哪一个都能连上。
    //
    // 另：端口固定 + 冲突即报错，避免端口被占用时静默换到 5174，
    //   导致以为连的是 5173、实际连到了别的服务。
    host: '::',
    strictPort: true,
    // 允许局域网内通过主机名访问（如 http://DESKTOP-XXX:5173）。
    // Vite 6 默认只放行 localhost / .localhost / IP 字面量，
    // 其他 Host 一律 403 (Blocked host)，会表现为"别的电脑无法登录"。
    // 这里放行所有 Host：dev 服务器只跑在本机内网、无鉴权静态资源，
    // 生产环境由 Nginx 托管 dist，不经过此配置，因此不构成安全面。
    allowedHosts: true,
    // 是否自动打开浏览器，单人开发时关掉更省事
    open: false,
    proxy: {
      // 所有接口请求转发到后端
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: true
      },
      // 上传的图片也走后端静态映射
      '/uploads': {
        target: 'http://localhost:8081',
        changeOrigin: true
      }
    }
  },
  build: {
    outDir: 'dist',
    // 关闭 sourcemap：课程项目不需要，还能显著减小产物体积
    sourcemap: false,
    chunkSizeWarningLimit: 1500
  }
})
