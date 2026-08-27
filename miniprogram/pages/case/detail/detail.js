// pages/case/detail/detail.js
const { request } = require("../../../utils/request");
const { getCurrentCase, setCurrentSession, isPrivacyAgreed } = require("../../../utils/storage");

Page({
  data: {
    caseId: null,
    caseInfo: null,
    activeTab: "chat",
    tabs: [
      { key: "chat", name: "对话" },
      { key: "facts", name: "事实要素" },
      { key: "evidence", name: "证据" },
      { key: "law", name: "法律意见" },
      { key: "timeline", name: "时间轴" },
    ],
    facts: [],
    evidenceList: [],
    laws: [],
    loading: true,
  },

  onLoad(options) {
    const caseId = options.id || (getCurrentCase() && getCurrentCase().caseId);
    if (caseId) {
      this.setData({ caseId });
      this.loadCase(caseId);
    } else {
      // 无案件时显示空态引导，不报错（tab 页不能 navigateBack）
      this.setData({ loading: false });
    }
  },

  onShow() {
    // 隐私门禁（tab 页也拦截，避免绕过首页检查）
    if (!isPrivacyAgreed()) {
      wx.reLaunch({ url: "/pages/disclaimer/disclaimer" });
      return;
    }
    // tabBar 页无法携带 URL 参数，每次进入都从 storage 读取当前案件
    const cur = getCurrentCase();
    const curId = cur && cur.caseId;
    if (curId && String(curId) !== String(this.data.caseId || "")) {
      this.setData({ caseId: curId });
      this.loadCase(curId);
    }
  },

  onGoCreate() {
    wx.navigateTo({ url: "/pages/case/create/create" });
  },

  async loadCase(caseId) {
    await getApp().ensureLogin();
    this.setData({ loading: true });
    try {
      const caseInfo = await request({ url: "/cases/" + caseId, method: "GET" });
      this.setData({ caseInfo, laws: caseInfo.lawsJson || [], loading: false });
      setCurrentSession(null); // 清除旧会话
      this.loadFacts(caseInfo);
    } catch (e) {
      console.error("加载案件失败:", e);
      wx.showToast({ title: "加载失败", icon: "none" });
      this.setData({ loading: false });
    }
  },

  loadFacts(caseInfo) {
    // 解析 facts_json（跳过 _ 开头的内部字段，如 _description）
    const facts = caseInfo.factsJson || {};
    const factList = Object.entries(facts)
      .filter(([key]) => !key.startsWith("_"))
      .map(([key, value]) => ({
      key,
      label: key,
      value: value?.value || "",
      confidence: value?.confidence || "LOW",
    }));
    this.setData({ facts: factList });
  },

  onTabChange(e) {
    this.setData({ activeTab: e.detail.value });
  },

  onNavigate(e) {
    const { tab } = e.currentTarget.dataset;
    if (tab === "timeline") {
      wx.navigateTo({ url: "/pages/case/timeline/timeline?caseId=" + this.data.caseId });
    } else if (tab === "evidence") {
      wx.navigateTo({ url: "/pages/case/evidence/evidence?caseId=" + this.data.caseId });
    } else if (tab === "chat") {
      wx.navigateTo({ url: "/pages/chat/chat?caseId=" + this.data.caseId });
    } else if (tab === "export") {
      wx.navigateTo({ url: "/pages/export/export?caseId=" + this.data.caseId });
    }
  },

  onRefresh() {
    if (this.data.caseId) {
      this.loadCase(this.data.caseId);
    }
  },
});
