# -*- coding: utf-8 -*-
"""实时同传端到端验证脚本。

链路：登录拿 accessToken → 连接本服务 /ws/live-translate（token 走查询参数）→
把 16kHz 单声道 PCM 按 100ms/片 Base64 上行 → 收集字幕事件 → session.finish 收尾。

用法：python ws_e2e_test.py [pcm路径] [lang]
"""

import asyncio
import base64
import json
import sys
import urllib.request

import websockets

HTTP_BASE = "http://localhost:8080"
WS_BASE = "ws://localhost:8080/ws/live-translate"
ACCOUNT = "rageval"
PASSWORD = "RagEval#2026"

PCM_PATH = sys.argv[1] if len(sys.argv) > 1 else "zh_sample_16k.pcm"
LANG = sys.argv[2] if len(sys.argv) > 2 else "en"

CHUNK_MS = 100  # 每片 100ms，与 Android 端一致
SAMPLE_RATE = 16000
BYTES_PER_CHUNK = SAMPLE_RATE * 2 * CHUNK_MS // 1000  # 3200 字节


def login() -> str:
    body = json.dumps({"account": ACCOUNT, "password": PASSWORD}).encode("utf-8")
    req = urllib.request.Request(
        HTTP_BASE + "/api/v1/auth/login",
        data=body,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=15) as resp:
        data = json.loads(resp.read().decode("utf-8"))
    token = data["data"]["accessToken"]
    assert token, f"登录失败: {data}"
    return token


async def run() -> None:
    token = login()
    print(f"[1] 登录成功 token_len={len(token)}")

    uri = f"{WS_BASE}?token={token}&lang={LANG}&mode=text"
    async with websockets.connect(uri, open_timeout=15, max_size=2**22) as ws:
        print("[2] WS 已连接，开始推流（实时节奏）")

        translation = {}   # response_id -> 累计译文
        source = {}        # item_id -> 累计原文
        errors = []
        session_created = False
        session_updated = False
        finished = asyncio.Event()

        async def reader():
            nonlocal session_created, session_updated
            async for raw in ws:
                evt = json.loads(raw)
                etype = evt.get("type", "")
                if etype == "session.created":
                    session_created = True
                    print("    <- session.created")
                elif etype == "session.updated":
                    session_updated = True
                    print("    <- session.updated")
                elif etype == "response.text.delta":
                    rid = evt.get("response_id", "?")
                    translation[rid] = translation.get(rid, "") + evt.get("delta", "")
                elif etype == "response.audio_transcript.delta":
                    rid = evt.get("response_id", "?")
                    translation[rid] = translation.get(rid, "") + evt.get("delta", "")
                elif etype == "conversation.item.input_audio_transcription.delta":
                    iid = evt.get("item_id", "?")
                    source[iid] = source.get(iid, "") + evt.get("delta", "")
                elif etype == "conversation.item.input_audio_transcription.completed":
                    iid = evt.get("item_id", "?")
                    source[iid] = evt.get("transcript", source.get(iid, ""))
                    print(f"    <- 原文识别完成: {source[iid][:50]}...")
                elif etype == "response.done":
                    usage = evt.get("response", {}).get("usage", {})
                    print(f"    <- response.done usage={usage.get('total_tokens')} tokens")
                elif etype == "session.finished":
                    print("    <- session.finished")
                    finished.set()
                elif etype == "error":
                    errors.append(evt.get("error", {}))
                    print(f"    <- ERROR: {evt.get('error')}")
                else:
                    pass  # 其余生命周期事件静默

        reader_task = asyncio.create_task(reader())

        with open(PCM_PATH, "rb") as f:
            pcm = f.read()

        total = len(pcm)
        offset = 0
        sent_seconds = 0.0
        while offset < total:
            chunk = pcm[offset : offset + BYTES_PER_CHUNK]
            offset += len(chunk)
            event = {
                "type": "input_audio_buffer.append",
                "audio": base64.b64encode(chunk).decode("ascii"),
            }
            await ws.send(json.dumps(event))
            sent_seconds += CHUNK_MS / 1000
            await asyncio.sleep(CHUNK_MS / 1000)  # 实时节奏推流

        print(f"[3] 推流完成 {total} 字节 ≈ {total / 32000:.1f}s")
        await ws.send(json.dumps({"type": "session.finish"}))
        print("[4] 已发送 session.finish，等待 session.finished")

        try:
            await asyncio.wait_for(finished.wait(), timeout=45)
        except asyncio.TimeoutError:
            print("!! 等待 session.finished 超时")

        reader_task.cancel()

        print("\n===== 结果 =====")
        print(f"session.created={session_created} session.updated={session_updated}")
        print(f"errors={errors}")
        for iid, text in source.items():
            print(f"原文: {text}")
        for rid, text in translation.items():
            print(f"译文: {text}")
        ok = session_created and session_updated and translation and not errors
        print(f"\nE2E_{'PASS' if ok else 'CHECK'}")


asyncio.run(run())
