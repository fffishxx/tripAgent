/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string
  readonly VITE_PROXY_TARGET?: string
  readonly VITE_DEV_PORT?: string
  readonly VITE_AMAP_KEY?: string
  readonly VITE_AMAP_VERSION?: string
  readonly VITE_AMAP_SECURITY_CODE?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
