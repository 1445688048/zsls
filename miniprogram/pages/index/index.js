// pages/index/index.js
const { request } = require("../../utils/request");
const { isPrivacyAgreed } = require("../../utils/storage");
const { goToCase } = require("../../utils/casenav");

Page({
  data: {
    domainTypes: [
      { id: "LABOR", name: "劳动纠纷", icon: "📋", enabled: true, description: "劳动合同、工资、社保、加班、辞退、工伤等" },
      { id: "CONSUMER", name: "消费维权", icon: "🛒", enabled: false, reason: "暂未开放" },
    ],
    cases: [],
    loading: false,
  },

  onLoad() {
    // 隐私门禁前置：onLoad 即拦截，避免先发请求再过门禁
    if (!isPrivacyAgreed()) {
      wx.reLaunch({ url: "/pages/disclaimer/disclaimer" });
    }
  },

  onShow() {
    // 隐私门禁：未同意隐私政策则跳免责声明页，不发起业务请求
    if (!isPrivacyAgreed()) {
      wx.reLaunch({ url: "/pages/disclaimer/disclaimer" });
      return;
    }
    this.loadCases();
  },

  onPullDownRefresh() {
    this.loadCases().finally(() => wx.stopPullDownRefresh());
  },

  async loadCases() {
    await getApp().ensureLogin();
    this.setData({ loading: true });
    try {
      const cases = await request({ url: "/cases", method: "GET" });
      this.setData({ cases: cases || [] });
    } catch (e) {
      console.error("加载案件失败:", e);
      this.setData({ cases: [] });
    } finally {
      this.setData({ loading: false });
    }
  },

  onAddCase() {
    wx.navigateTo({ url: "/pages/case/create/create" });
  },

  onDomainSelect(e) {
    const { id } = e.currentTarget.dataset;
    const domain = this.data.domainTypes.find((d) => d.id === id);
    if (!domain?.enabled) {
      wx.showToast({ title: domain?.reason || "暂未开放", icon: "none" });
      return;
    }
    wx.navigateTo({ url: "/pages/case/create/create?domainType=" + id });
  },

  onGoToCase(e) {
    const { caseId } = e.currentTarget.dataset;
    const item = this.data.cases.find((c) => String(c.case_id) === String(caseId));
    goToCase({ caseId, title: item && item.title, domainType: item && item.domainType });
  },

});
