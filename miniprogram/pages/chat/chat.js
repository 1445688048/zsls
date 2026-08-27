// pages/chat/chat.js
const { request, BASE_URL } = require("../../utils/request");
const { getCurrentCase, getCurrentSession, setCurrentSession, getToken } = require("../../utils/storage");

// SSE 事件分隔：两个换行（字节 10,10）
const EVENT_SEP = [10, 10];

Page({
  data: {
    caseId: null,
    sessionId: null,
    caseTitle: "",
    messages: [],
    inputText: "",
    isStreaming: false,
    loading: true,
    scrollInto: "",
    canSend: false,
  },

  onLoad(options) {
    const cur = getCurrentCase();
    const caseId = options.caseId || (cur && cur.caseId);
    this.setData({ caseId, caseTitle: (cur && cur.title) || "" });
    this.initSession();
  },

  async initSession() {
    try {
      await getApp().ensureLogin();
      const existing = getCurrentSession();
      if (existing && existing.sessionId && String(existing.caseId) === String(this.data.caseId)) {
        this.setData({ sessionId: existing.sessionId });
        await this.loadMessages();
        return;
      }
      // 创建新会话
      const session = await request({
        url: "/chat/sessions",
        method: "POST",
        data: { caseId: this.data.caseId },
      });
      setCurrentSession({ sessionId: session.sessionId, caseId: this.data.caseId });
      this.setData({ sessionId: session.sessionId });
      await this.loadMessages();
    } catch (e) {
      console.error("初始化会话失败:", e);
      wx.showToast({ title: "初始化失败", icon: "none" });
      this.setData({ loading: false });
    }
  },

  async loadMessages() {
    try {
      const messages = await request({
        url: "/chat/sessions/" + this.data.sessionId + "/messages",
        method: "GET",
      });
      this.setData({ messages: messages || [], loading: false });
      this.scrollToBottom();
    } catch (e) {
      console.error("加载消息失败:", e);
      this.setData({ loading: false });
    }
  },

  onInput(e) {
    const inputText = e.detail.value;
    this.setData({ inputText, canSend: !!inputText.trim() });
  },

  // ---------- 发送消息（SSE 流式） ----------
  async onSend() {
    const text = this.data.inputText.trim();
    if (!text || this.data.isStreaming) return;

    // 乐观渲染用户消息 + 空的助手消息（流式内容追加到这里）
    const userMsg = { role: "user", content: text, created_at: new Date().toISOString() };
    const assistantMsg = { role: "assistant", content: "", streaming: true };
    const messages = [...this.data.messages, userMsg, assistantMsg];
    this._assistantIdx = messages.length - 1;
    this.setData({ messages, inputText: "", isStreaming: true, canSend: false });
    this.scrollToBottom();

    this.startStream(text);
  },

  startStream(text) {
    this._byteBuffer = [];
    this._pendingIncrement = "";
    this._throttleTimer = null;

    const task = wx.request({
      url: BASE_URL + "/chat/sessions/" + this.data.sessionId + "/messages",
      method: "POST",
      data: { content: text },
      enableChunked: true,
      header: {
        "Content-Type": "application/json",
        "Authorization": "Bearer " + (getToken() || ""),
      },
      success: () => {},
      fail: (err) => {
        console.error("流式请求失败:", err);
        this.finishStream(true);
      },
      complete: () => this.finishStream(false),
    });

    if (task && typeof task.onChunkReceived === "function") {
      task.onChunkReceived((res) => this.onChunk(res.data));
    } else {
      // 低版本基础库无 onChunkReceived：退化为整包接收后解析
      wx.request({
        url: BASE_URL + "/chat/sessions/" + this.data.sessionId + "/messages",
        method: "POST",
        data: { content: text },
        header: {
          "Content-Type": "application/json",
          "Authorization": "Bearer " + (getToken() || ""),
        },
        success: (res) => {
          if (res.statusCode === 200) {
            const raw = typeof res.data === "string" ? res.data : this.decodeUtf8(new Uint8Array(res.data));
            const increment = this.parseSse(raw);
            if (increment) this.appendAssistant(increment);
          } else {
            wx.showToast({ title: "发送失败(" + res.statusCode + ")", icon: "none" });
          }
        },
        fail: (err) => {
          console.error("请求失败:", err);
          this.finishStream(true);
        },
        complete: () => this.finishStream(false),
      });
    }
  },

  onChunk(arrayBuffer) {
    const bytes = new Uint8Array(arrayBuffer);
    for (let i = 0; i < bytes.length; i++) this._byteBuffer.push(bytes[i]);

    // 找到最后一个完整 SSE 事件边界（LF LF），只解码完整段，防止多字节汉字被截断
    const buf = this._byteBuffer;
    let lastSep = -1;
    for (let i = 0; i + 1 < buf.length; i++) {
      if (buf[i] === EVENT_SEP[0] && buf[i + 1] === EVENT_SEP[1]) lastSep = i + 1;
    }
    if (lastSep < 0) return;

    const complete = buf.slice(0, lastSep + 1);
    this._byteBuffer = buf.slice(lastSep + 1);
    const increment = this.parseSse(this.decodeUtf8(complete));
    if (increment) this.appendAssistant(increment);
  },

  // 解析 SSE 文本：按空行分事件，取每个事件中 data: 开头的行并按换行拼接
  parseSse(text) {
    let out = "";
    const cleaned = String(text).replace(/\r/g, "");
    const events = cleaned.split("\n\n");
    for (const ev of events) {
      const dataLines = ev.split("\n")
        .filter((l) => l.indexOf("data:") === 0)
        .map((l) => l.slice(5).replace(/^\s/, ""));
      if (dataLines.length) out += dataLines.join("\n");
    }
    return out;
  },

  // UTF-8 解码：优先 TextDecoder，低版本基础库用内置 polyfill
  decodeUtf8(bytes) {
    if (typeof TextDecoder !== "undefined") {
      try {
        return new TextDecoder("utf-8").decode(bytes);
      } catch (e) { /* fall through */ }
    }
    let out = "";
    let i = 0;
    const len = bytes.length;
    while (i < len) {
      const b = bytes[i];
      let cp, clen;
      if (b < 0x80) { cp = b; clen = 1; }
      else if ((b & 0xe0) === 0xc0) { cp = b & 0x1f; clen = 2; }
      else if ((b & 0xf0) === 0xe0) { cp = b & 0x0f; clen = 3; }
      else if ((b & 0xf8) === 0xf0) { cp = b & 0x07; clen = 4; }
      else { cp = 0xfffd; clen = 1; }
      if (i + clen > len) { out += "\uFFFD"; break; }
      for (let k = 1; k < clen; k++) {
        const nb = bytes[i + k];
        if ((nb & 0xc0) !== 0x80) { cp = 0xfffd; break; }
        cp = (cp << 6) | (nb & 0x3f);
      }
      out += String.fromCodePoint(cp);
      i += clen;
    }
    return out;
  },

  // 增量追加到助手消息（80ms 节流，减少 setData 频率）
  appendAssistant(increment) {
    this._pendingIncrement = (this._pendingIncrement || "") + increment;
    if (this._throttleTimer) return;
    this._throttleTimer = setTimeout(() => {
      this._throttleTimer = null;
      this.flushIncrement();
    }, 80);
  },

  flushIncrement() {
    const inc = this._pendingIncrement || "";
    this._pendingIncrement = "";
    if (!inc) return;
    const messages = this.data.messages.slice();
    const idx = this._assistantIdx;
    const last = idx != null ? messages[idx] : null;
    if (!last || last.role !== "assistant") return;
    messages[idx] = { ...last, content: last.content + inc, streaming: true };
    this.setData({ messages });
    this.scrollToBottom();
  },

  finishStream(isError) {
    if (this._throttleTimer) {
      clearTimeout(this._throttleTimer);
      this._throttleTimer = null;
    }
    this.flushIncrement();
    const messages = this.data.messages.slice();
    const idx = this._assistantIdx;
    const last = idx != null ? messages[idx] : null;
    if (last && last.role === "assistant") {
      messages[idx] = { ...last, streaming: false };
    }
    this.setData({ messages, isStreaming: false });
    if (isError && last && !last.content) {
      wx.showToast({ title: "发送失败", icon: "none" });
    }
    this.scrollToBottom();
  },

  scrollToBottom() {
    const len = this.data.messages.length;
    if (len) this.setData({ scrollInto: "msg-" + (len - 1) });
  },

  // ---------- 语音输入（微信同声传译插件） ----------
  onVoiceInput() {
    let mgr = this._recorder;
    if (!mgr) {
      let plugin = null;
      try {
        plugin = requirePlugin("WechatSI");
      } catch (e) {
        console.error("同声传译插件未配置:", e);
        wx.showToast({ title: "同声传译插件未配置", icon: "none" });
        return;
      }
      if (!plugin || typeof plugin.getRecordRecognitionManager !== "function") {
        wx.showToast({ title: "同声传译插件不可用", icon: "none" });
        return;
      }
      mgr = plugin.getRecordRecognitionManager();
      mgr.onRecognize((res) => {
        if (res && res.result) this.setData({ inputText: res.result });
      });
      mgr.onStop(() => {});
      mgr.onError(() => wx.showToast({ title: "语音识别失败", icon: "none" }));
      this._recorder = mgr;
    }
    wx.authorize({
      scope: "scope.record",
      success: () => {
        try {
          mgr.start({ lang: "zh_CN", duration: 60000 });
        } catch (e) {
          console.error("启动录音失败:", e);
        }
      },
      fail: () => wx.showToast({ title: "需要录音权限", icon: "none" }),
    });
  },

  onNavigateToCase() {
    // 详情页是 tab 页：switchTab 返回（当前案件已存于 storage）
    wx.switchTab({ url: "/pages/case/detail/detail" });
  },
});
