import { fileURLToPath, URL } from 'node:url'
import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')

  return {
    plugins: [vue()],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    server: {
      host: '0.0.0.0',
      port: Number(env.VITE_DEV_PORT || 5173),
      proxy: {
        // 后端未配置 CORS，开发环境统一经 Vite 代理访问 Java 服务。
        '/api': {
          target: env.VITE_PROXY_TARGET || 'http://localhost:8080',
          changeOrigin: true,
        },
      },
    },
    preview: {
      port: Number(env.VITE_PREVIEW_PORT || 4173),
    },
  }
})
