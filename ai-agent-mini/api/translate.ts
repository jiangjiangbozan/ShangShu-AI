/**
 * 跨语言翻译 API
 *
 * 对应后端 TranslateController：qwen-mt-uni 单条消息翻译，
 * targetLang 留空时由服务端按语种推断（中文→英文，其余→中文）。
 */
import { post } from '@/utils/request'

/** 翻译响应 */
export interface TranslateResp {
  messageId: number
  sourceLang: string
  targetLang: string
  translated: string
}

/** 翻译单条消息 */
export function translateMessage(messageId: number, targetLang?: string) {
  return post<TranslateResp>(`/chat/messages/${messageId}/translate`, { targetLang })
}
