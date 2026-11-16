/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_APP_TITLE: string
  readonly VITE_API_BASE_URL: string
  readonly VITE_PROXY_TARGET: string
  readonly VITE_USE_MOCK: string
  readonly VITE_DEFAULT_MERCHANT_CODE: string
  /** 仅本地演示：物流 SOAP 服务间凭证，生产由网关注入，不进仓库 */
  readonly VITE_SHIPMENT_WS_TOKEN?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<Record<string, unknown>, Record<string, unknown>, unknown>
  export default component
}
