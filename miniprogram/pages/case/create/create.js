// pages/case/create.js
const { request } = require("../../../utils/request");
const { goToCase } = require("../../../utils/casenav");

Page({
  data: {
    domainTypes: [
      { id: "LABOR", name: "劳动纠纷", icon: "📋", description: "劳动合同、工资、社保、加班、辞退、工伤等" },
      { id: "CONSUMER", name: "消费维权", icon: "🛒", description: "商品质量、退换货、虚假宣传等" },
    ],
    title: "",
    description: "",
    selectedDomain: "LABOR",
    submitting: false,
  },

  onLoad() {
    // 从 URL 参数获取默认领域
    const options = getCurrentPages().slice(-1)[0]?.options || {};
    if (options.domainType) {
      this.setData({ selectedDomain: options.domainType });
    }
  },

  onDomainSelect(e) {
    const { id } = e.currentTarget.dataset;
    this.setData({ selectedDomain: id });
  },

  onTitleInput(e) {
    this.setData({ title: e.detail.value });
  },

  onDescriptionInput(e) {
    this.setData({ description: e.detail.value });
  },

  async onSubmit() {
    const { selectedDomain, title, submitting } = this.data;
    if (submitting) return;
    
    if (!title.trim()) {
      wx.showToast({ title: "请输入案件标题", icon: "none" });
      return;
    }

    this.setData({ submitting: true });
    try {
      const caseInfo = await request({
        url: "/cases",
        method: "POST",
        data: { domainType: selectedDomain, title: title.trim(), description: this.data.description.trim() }
      });

      wx.showToast({ title: "案件创建成功" });
      setTimeout(() => {
        goToCase({ caseId: caseInfo.caseId, title: caseInfo.title, domainType: caseInfo.domainType });
      }, 800);
    } catch (err) {
      wx.showToast({ title: "创建失败: " + (err.message || "未知错误"), icon: "none" });
    } finally {
      this.setData({ submitting: false });
    }
  },

  onNavigateBack() {
    wx.navigateBack();
  },
});
