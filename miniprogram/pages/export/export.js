// pages/export/export.js
const { request } = require("../../utils/request");
const { getCurrentCase } = require("../../utils/storage");

Page({
  data: {
    caseId: null,
    caseTitle: "",
    generating: false,
  },

  onLoad(options) {
    const caseId = options.caseId || (getCurrentCase() && getCurrentCase().caseId);
    if (caseId) {
      this.setData({ caseId });
      this.loadCase(caseId);
    }
  },

  async loadCase(caseId) {
    try {
      await getApp().ensureLogin();
      const res = await request({ url: "/cases/" + caseId, method: "GET" });
      this.setData({ caseTitle: (res && res.title) || "案件" });
    } catch (e) {
      console.error("加载案件失败:", e);
    }
  },

  async onExportPdf(e) {
    const redact = e.currentTarget.dataset.redact;
    this.setData({ generating: true });
    try {
      const res = await request({
        url: "/cases/" + this.data.caseId + "/export/pdf?redact=" + redact,
        method: "POST",
      });
      if (!res || !res.downloadUrl) {
        throw new Error("未返回下载地址");
      }
      wx.showLoading({ title: "正在下载..." });
      const dl = await new Promise((resolve, reject) => {
        wx.downloadFile({
          url: res.downloadUrl,
          header: { "Authorization": "Bearer " + require("../../utils/storage").getToken() },
          success: resolve,
          fail: reject,
        });
      });
      wx.hideLoading();
      if (dl.statusCode !== 200 || !dl.tempFilePath) {
        wx.showToast({ title: "下载失败(" + (dl.statusCode || "未知") + ")", icon: "none" });
        return;
      }
      wx.openDocument({
        filePath: dl.tempFilePath,
        fileType: "pdf",
        showMenu: true,
        success: () => {},
        fail: (err) => {
          console.error("打开 PDF 失败:", err);
          wx.showToast({ title: "打开失败", icon: "none" });
        },
      });
    } catch (err) {
      console.error("导出失败:", err);
      wx.showToast({ title: "导出失败: " + (err.message || "未知错误"), icon: "none" });
    } finally {
      // 下载失败路径也要收起 loading，否则蒙层永久停留且吞掉 toast
      wx.hideLoading();
      this.setData({ generating: false });
    }
  },

  onPreviewOriginal() {
    this.onExportPdf({ currentTarget: { dataset: { redact: false } } });
  },

  onPreviewRedacted() {
    this.onExportPdf({ currentTarget: { dataset: { redact: true } } });
  },
});
