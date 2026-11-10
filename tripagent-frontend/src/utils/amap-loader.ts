import { amapConfig, hasAmapKey } from '@/config/amap'
import type { AmapNamespace } from '@/types/amap'

declare global {
  interface Window {
    AMap?: AmapNamespace
    _AMapSecurityConfig?: {
      securityJsCode?: string
      serviceHost?: string
    }
  }
}

export class AmapLoadError extends Error {
  readonly code: 'MISSING_KEY' | 'LOAD_FAILED'

  constructor(message: string, code: 'MISSING_KEY' | 'LOAD_FAILED') {
    super(message)
    this.name = 'AmapLoadError'
    this.code = code
  }
}

let amapPromise: Promise<AmapNamespace> | null = null

export function loadAmap(): Promise<AmapNamespace> {
  if (typeof window === 'undefined') {
    return Promise.reject(new AmapLoadError('当前环境不支持高德地图', 'LOAD_FAILED'))
  }

  if (window.AMap) {
    return Promise.resolve(window.AMap)
  }

  if (!hasAmapKey) {
    return Promise.reject(
      new AmapLoadError(
        '未配置高德地图 Key，请在 .env.development 中设置 VITE_AMAP_KEY',
        'MISSING_KEY',
      ),
    )
  }

  if (amapPromise) {
    return amapPromise
  }

  if (amapConfig.securityJsCode) {
    window._AMapSecurityConfig = {
      ...(window._AMapSecurityConfig || {}),
      securityJsCode: amapConfig.securityJsCode,
    }
  }

  amapPromise = new Promise<AmapNamespace>((resolve, reject) => {
    const callbackName = `__tripagentAmapReady_${Date.now()}`
    const windowRecord = window as unknown as Record<string, unknown>
    let finished = false
    let scriptElement: HTMLScriptElement | null = null

    const timeout = window.setTimeout(() => {
      finish(
        new AmapLoadError(
          '高德地图加载超时，请检查网络或 Key 配置',
          'LOAD_FAILED',
        ),
      )
    }, 20_000)

    function cleanup(removeScript = false): void {
      window.clearTimeout(timeout)
      delete windowRecord[callbackName]
      if (removeScript && scriptElement) {
        scriptElement.remove()
        scriptElement = null
      }
    }

    function finish(error?: AmapLoadError): void {
      if (finished) {
        return
      }
      finished = true
      cleanup(Boolean(error))

      if (error) {
        amapPromise = null
        reject(error)
        return
      }

      if (window.AMap) {
        resolve(window.AMap)
      } else {
        amapPromise = null
        reject(
          new AmapLoadError(
            '高德地图加载完成，但 AMap 对象不可用',
            'LOAD_FAILED',
          ),
        )
      }
    }

    windowRecord[callbackName] = () => finish()

    const script = document.createElement('script')
    scriptElement = script
    script.src = `https://webapi.amap.com/maps?v=${encodeURIComponent(
      amapConfig.version,
    )}&key=${encodeURIComponent(amapConfig.key)}&callback=${callbackName}`
    script.async = true
    script.onerror = () => {
      finish(
        new AmapLoadError(
          '高德地图脚本加载失败，请检查网络与 Key 白名单',
          'LOAD_FAILED',
        ),
      )
    }

    document.head.appendChild(script)
  })

  return amapPromise
}
