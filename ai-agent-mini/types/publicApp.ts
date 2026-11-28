/** 公开应用列表与聊天类型 */

/** 公开应用摘要 */
export interface PublicApp {
  accessPolicy: string
  appType: string
  description: string
  name: string
  publicCode: string
}

/** 公开应用聊天回调 */
export interface PublicChatCallbacks {
  /** 收到一段回答内容 */
  onMessage: (content: string) => void
  /** 回答结束 */
  onDone: () => void
  /** 连接或解析失败 */
  onError?: (message: string) => void
  /** 请求关闭 */
  onClose?: () => void
}
