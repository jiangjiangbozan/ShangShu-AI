<script setup lang="ts">
/**
 * 实时同传演示页（qwen3.8-livetranslate-flash-realtime）
 *
 * 链路：浏览器 getUserMedia → AudioWorklet 重采样为 16kHz PCM16 → Base64 分片
 * （100ms/片）→ 本服务 /ws/live-translate（token 走查询参数）→ 服务端代理连百炼
 * Realtime → 字幕事件回流（原文识别 + 译文增量）。
 *
 * 百炼 API Key 仅存在于后端，浏览器不接触上游协议；会话配置（session.update）
 * 由服务端代管，本页只推 input_audio_buffer.append 与 session.finish。
 *
 * @author add-live-translate
 */
import { computed, onBeforeUnmount, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Microphone, VideoPause } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'

interface CaptionSegment {
  /** 分段 id：原文识别 item id（译文 item 通过 previous_item_id 关联到该 id） */
  id: string
  source: string
  translation: string
}

type Phase = 'idle' | 'connecting' | 'running' | 'finishing' | 'finished'

const TARGET_LANGS = [
  { code: 'en', label: '英语' },
  { code: 'ja', label: '日语' },
  { code: 'ko', label: '韩语' },
  { code: 'zh', label: '中文' },
]

const SAMPLE_RATE = 16000
const auth = useAuthStore()

const phase = ref<Phase>('idle')
const targetLang = ref('en')
const captions = reactive<CaptionSegment[]>([])
const errorMessage = ref('')
const audioSeconds = ref(0)

const running = computed(
  () => phase.value === 'running' || phase.value === 'connecting' || phase.value === 'finishing',
)

const statusText = computed(
  () =>
    ({
      idle: '选择目标语言后点击麦克风开始实时翻译',
      connecting: '正在连接同传服务…',
      running: '正在聆听，请开始说话…',
      finishing: '正在收尾，等待剩余译文…',
      finished: '本次同传已结束，可再次开始',
    })[phase.value],
)

// ==================== 资源句柄 ====================

let ws: WebSocket | null = null
let audioContext: AudioContext | null = null
let mediaStream: MediaStream | null = null
let workletNode: AudioWorkletNode | null = null
let sourceNode: MediaStreamAudioSourceNode | null = null
let muteGain: GainNode | null = null
let secondsTimer: number | null = null

/** 原文 item 草稿与译文 item 草稿（按 item_id 聚合） */
const sourceDrafts = new Map<string, string>()
const translationDrafts = new Map<string, string>()
/** 译文 item id → 原文 item id（同 VAD 段配对） */
const translationToSource = new Map<string, string>()

// ==================== 开始 / 停止 ====================

async function start() {
  if (running.value) return
  if (!auth.accessToken) {
    ElMessage.error('请先登录后再使用实时同传')
    return
  }
  errorMessage.value = ''
  captions.splice(0, captions.length)
  sourceDrafts.clear()
  translationDrafts.clear()
  translationToSource.clear()
  audioSeconds.value = 0
  phase.value = 'connecting'

  try {
    await openWebSocket()
    await openMicrophone()
    phase.value = 'running'
    secondsTimer = window.setInterval(() => {
      audioSeconds.value += 1
    }, 1000)
  } catch (e) {
    phase.value = 'idle'
    errorMessage.value = e instanceof Error ? e.message : String(e)
    cleanupAudio()
    ws?.close()
    ws = null
  }
}

function stop() {
  if (!running.value) return
  phase.value = 'finishing'
  if (secondsTimer !== null) {
    window.clearInterval(secondsTimer)
    secondsTimer = null
  }
  ws?.send(JSON.stringify({ type: 'session.finish' }))
  cleanupAudio()
  // 留 3 秒接收上游剩余译文与 session.finished
  window.setTimeout(() => {
    ws?.close()
    ws = null
    if (phase.value === 'finishing') phase.value = 'finished'
  }, 3000)
}

// ==================== WebSocket ====================

function openWebSocket(): Promise<void> {
  return new Promise((resolve, reject) => {
    const proto = location.protocol === 'https:' ? 'wss' : 'ws'
    const url = `${proto}://${location.host}/ws/live-translate?token=${encodeURIComponent(
      auth.accessToken ?? '',
    )}&lang=${targetLang.value}&mode=text`
    let settled = false
    ws = new WebSocket(url)

    ws.onopen = () => {
      settled = true
      resolve()
    }
    ws.onerror = () => {
      if (!settled) {
        settled = true
        reject(new Error('WebSocket 连接失败，请确认已登录且后端已启动'))
      }
    }
    ws.onclose = () => {
      if (phase.value === 'connecting' || phase.value === 'running') {
        phase.value = 'finished'
      }
    }
    ws.onmessage = (event) => {
      handleServerEvent(event.data as string)
    }
  })
}

function handleServerEvent(raw: string) {
  let evt: Record<string, unknown>
  try {
    evt = JSON.parse(raw)
  } catch {
    return
  }
  const type = evt.type as string | undefined
  switch (type) {
    case 'session.updated':
      phase.value = 'running'
      break
    case 'conversation.item.created': {
      // 译文 assistant 消息项创建：previous_item_id 指向同段原文 item
      const item = evt.item as Record<string, unknown> | undefined
      if (!item) break
      const itemId = item.id as string | undefined
      if (item.role === 'assistant' && itemId) {
        const prevId = (evt.previous_item_id as string | undefined) ?? `src-${itemId}`
        sourceDrafts.set(prevId, sourceDrafts.get(prevId) ?? '')
        translationDrafts.set(itemId, translationDrafts.get(itemId) ?? '')
        translationToSource.set(itemId, prevId)
        upsertSegment(prevId)
      }
      break
    }
    case 'conversation.item.input_audio_transcription.delta': {
      const itemId = evt.item_id as string
      sourceDrafts.set(itemId, (sourceDrafts.get(itemId) ?? '') + (evt.delta as string))
      upsertSegment(itemId)
      break
    }
    case 'conversation.item.input_audio_transcription.completed': {
      const itemId = evt.item_id as string
      sourceDrafts.set(itemId, (evt.transcript as string) ?? sourceDrafts.get(itemId) ?? '')
      upsertSegment(itemId)
      break
    }
    case 'response.text.delta':
    case 'response.audio_transcript.delta': {
      const itemId = evt.item_id as string
      translationDrafts.set(itemId, (translationDrafts.get(itemId) ?? '') + (evt.delta as string))
      upsertSegment(translationToSource.get(itemId) ?? itemId)
      break
    }
    case 'response.text.done':
    case 'response.audio_transcript.done': {
      const itemId = evt.item_id as string
      const finalText = (evt.text as string | undefined) ?? (evt.transcript as string | undefined)
      if (finalText) translationDrafts.set(itemId, finalText)
      upsertSegment(translationToSource.get(itemId) ?? itemId)
      break
    }
    case 'session.finished':
      phase.value = 'finished'
      break
    case 'error': {
      const err = evt.error as Record<string, unknown> | undefined
      errorMessage.value = (err?.message as string) ?? '上游错误'
      break
    }
  }
}

/** 把 item id 对应的分段插入/同步到字幕列表（按到达顺序保持稳定）。 */
function upsertSegment(id: string) {
  const source = sourceDrafts.get(id) ?? ''
  const translationId = [...translationToSource.entries()].find(([, src]) => src === id)?.[0]
  const translation = translationId ? (translationDrafts.get(translationId) ?? '') : ''
  const existing = captions.find((c) => c.id === id)
  if (existing) {
    existing.source = source
    existing.translation = translation
  } else {
    captions.push({ id, source, translation })
  }
}

// ==================== 麦克风采集与重采样 ====================

/**
 * AudioWorklet 内联源码：任意采样率线性插值重采样到 16kHz 并转 Int16 PCM。
 * 通过 port 以 transferable Int16Array 上抛，主线程 Base64 后上行。
 */
const WORKLET_CODE = `
class PCM16Resampler16k extends AudioWorkletProcessor {
  constructor() {
    super();
    this.ratio = sampleRate / 16000;
    this.tail = new Float32Array(0);
  }
  process(inputs) {
    const input = inputs[0] && inputs[0][0];
    if (!input) return true;
    const combined = new Float32Array(this.tail.length + input.length);
    combined.set(this.tail);
    combined.set(input, this.tail.length);
    const outLen = Math.floor(combined.length / this.ratio);
    if (outLen > 0) {
      const out = new Int16Array(outLen);
      for (let i = 0; i < outLen; i++) {
        const idx = i * this.ratio;
        const i0 = Math.floor(idx);
        const frac = idx - i0;
        const s0 = combined[i0];
        const s1 = i0 + 1 < combined.length ? combined[i0 + 1] : s0;
        let v = s0 + (s1 - s0) * frac;
        v = v > 1 ? 1 : v < -1 ? -1 : v;
        out[i] = v < 0 ? v * 0x8000 : v * 0x7fff;
      }
      const consumed = Math.floor(outLen * this.ratio);
      this.tail = combined.slice(consumed);
      this.port.postMessage(out, [out.buffer]);
    }
    return true;
  }
}
registerProcessor('pcm16-resampler-16k', PCM16Resampler16k);
`

async function openMicrophone(): Promise<void> {
  mediaStream = await navigator.mediaDevices.getUserMedia({
    audio: { channelCount: 1, echoCancellation: true, noiseSuppression: true },
  })
  audioContext = new AudioContext()
  const workletBlob = new Blob([WORKLET_CODE], { type: 'application/javascript' })
  const workletUrl = URL.createObjectURL(workletBlob)
  await audioContext.audioWorklet.addModule(workletUrl)
  URL.revokeObjectURL(workletUrl)

  sourceNode = audioContext.createMediaStreamSource(mediaStream)
  workletNode = new AudioWorkletNode(audioContext, 'pcm16-resampler-16k')
  workletNode.port.onmessage = (event: MessageEvent<Int16Array>) => {
    sendAudioChunk(event.data)
  }
  // 静音输出链：保证工作流被持续拉起，同时不外放造成回声
  muteGain = audioContext.createGain()
  muteGain.gain.value = 0
  sourceNode.connect(workletNode)
  workletNode.connect(muteGain)
  muteGain.connect(audioContext.destination)
}

function sendAudioChunk(pcm: Int16Array) {
  if (!ws || ws.readyState !== WebSocket.OPEN) return
  const bytes = new Uint8Array(pcm.buffer, pcm.byteOffset, pcm.byteLength)
  // Uint8Array → base64（分块避免 String.fromCharCode 栈溢出）
  let binary = ''
  const STEP = 0x8000
  for (let i = 0; i < bytes.length; i += STEP) {
    binary += String.fromCharCode(...bytes.subarray(i, i + STEP))
  }
  const audio = btoa(binary)
  ws.send(JSON.stringify({ type: 'input_audio_buffer.append', audio }))
}

function cleanupAudio() {
  workletNode?.port.close()
  workletNode?.disconnect()
  workletNode = null
  sourceNode?.disconnect()
  sourceNode = null
  muteGain?.disconnect()
  muteGain = null
  mediaStream?.getTracks().forEach((t) => t.stop())
  mediaStream = null
  void audioContext?.close()
  audioContext = null
}

onBeforeUnmount(() => {
  if (secondsTimer !== null) window.clearInterval(secondsTimer)
  cleanupAudio()
  ws?.close()
  ws = null
})

function formatDuration(total: number): string {
  const m = Math.floor(total / 60)
  const s = total % 60
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}
</script>

<template>
  <div class="live-translate">
    <el-card shadow="never" class="control-card">
      <template #header>
        <div class="card-header">
          <span>实时同传 · 双语字幕</span>
          <el-tag v-if="phase === 'running'" type="success" effect="light">进行中</el-tag>
          <el-tag v-else-if="phase === 'connecting'" type="warning" effect="light">连接中</el-tag>
          <el-tag v-else-if="phase === 'finishing'" type="info" effect="light">收尾中</el-tag>
        </div>
      </template>

      <div class="control-row">
        <span class="control-label">翻译成</span>
        <el-radio-group v-model="targetLang" :disabled="running">
          <el-radio-button v-for="lang in TARGET_LANGS" :key="lang.code" :value="lang.code">
            {{ lang.label }}
          </el-radio-button>
        </el-radio-group>
        <span class="duration">已采集 {{ formatDuration(audioSeconds) }}</span>
        <el-button
          :type="running ? 'danger' : 'primary'"
          size="large"
          :icon="running ? VideoPause : Microphone"
          round
          @click="running ? stop() : start()"
        >
          {{ running ? '停止' : '开始' }}
        </el-button>
      </div>

      <el-alert
        v-if="errorMessage"
        :title="errorMessage"
        type="error"
        show-icon
        class="error-alert"
        @close="errorMessage = ''"
      />
      <p class="status-line">{{ statusText }}</p>
    </el-card>

    <el-empty v-if="captions.length === 0" description="尚无字幕" />

    <div v-else class="caption-list">
      <el-card
        v-for="segment in captions"
        :key="segment.id"
        shadow="never"
        class="caption-card"
      >
        <p v-if="segment.source" class="caption-source">{{ segment.source }}</p>
        <p v-if="segment.translation" class="caption-translation">{{ segment.translation }}</p>
      </el-card>
    </div>
  </div>
</template>

<style scoped>
.live-translate {
  padding: 16px;
  max-width: 960px;
  margin: 0 auto;
}

.control-card {
  margin-bottom: 16px;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 12px;
  font-weight: 600;
}

.control-row {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}

.control-label {
  font-size: 14px;
  color: var(--el-text-color-secondary);
}

.duration {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin-left: auto;
}

.error-alert {
  margin-top: 12px;
}

.status-line {
  margin: 12px 0 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.caption-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.caption-source {
  margin: 0;
  font-size: 15px;
  color: var(--el-text-color-primary);
}

.caption-translation {
  margin: 4px 0 0;
  font-size: 15px;
  font-style: italic;
  color: var(--el-color-primary);
}
</style>
