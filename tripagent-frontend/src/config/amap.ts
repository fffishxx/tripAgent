/**
 * 高德地图前端配置。
 *
 * 当前项目使用 Web服务 Key：
 * - JS API 只负责地图、Marker、Polyline 等客户端渲染；
 * - POI 搜索、路线规划通过高德 Web 服务 REST 完成。
 *
 * 正式上线建议替换为 Web端(JS API) Key + 安全密钥，
 * 或由后端代理高德 Web 服务，避免在浏览器暴露 Web 服务 Key。
 */
export const amapConfig = {
  key: (import.meta.env.VITE_AMAP_KEY || '').trim(),
  version: (import.meta.env.VITE_AMAP_VERSION || '2.0').trim(),
  securityJsCode: (import.meta.env.VITE_AMAP_SECURITY_CODE || '').trim(),
}

export const hasAmapKey = Boolean(amapConfig.key)
