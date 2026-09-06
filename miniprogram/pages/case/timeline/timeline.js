// pages/case/timeline/timeline.js
const { request } = require("../../../utils/request");

Page({
  data: {
    caseId: null,
    events: [],
    showAddModal: false,
    newEvent: { eventTime: "", description: "", type: "USER_ACTION", isDeadline: false, reminderText: "" },
    loading: true,
  },

  onLoad(options) {
    const caseId = options.caseId;
    if (caseId) {
      this.setData({ caseId });
      this.loadEvents(caseId);
    }
  },

  async loadEvents(caseId) {
    await getApp().ensureLogin();
    this.setData({ loading: true });
    try {
      const events = await request({ url: "/cases/" + caseId + "/timeline", method: "GET" });
      const sorted = (events || []).sort((a, b) => new Date(a.eventTime || 0) - new Date(b.eventTime || 0));
      this.setData({ events: sorted, loading: false });
    } catch (e) {
      console.error("加载时间轴失败:", e);
      this.setData({ loading: false, events: [] });
    }
  },

  onAddEvent() {
    this.setData({ 
      showAddModal: true, 
      newEvent: { eventTime: "", description: "", type: "USER_ACTION", isDeadline: false, reminderText: "" } 
    });
  },

  onCloseModal() {
    this.setData({ showAddModal: false });
  },

  onInput(e) {
    const { field } = e.currentTarget.dataset;
    this.setData({ ["newEvent." + field]: e.detail.value });
  },

  onDeadlineToggle(e) {
    // 独立 checkbox 的 e.detail.value 是数组（选中 [value] / 未选中 []），需转为布尔
    const checked = Array.isArray(e.detail.value) ? e.detail.value.length > 0 : !!e.detail.value;
    this.setData({ newEvent: { ...this.data.newEvent, isDeadline: checked } });
  },

  onTypeChange(e) {
    this.setData({ newEvent: { ...this.data.newEvent, type: e.detail.value } });
  },

  async onSaveEvent() {
    const { caseId, newEvent } = this.data;
    if (!newEvent.eventTime || !newEvent.description) {
      wx.showToast({ title: "请填写完整信息", icon: "none" });
      return;
    }

    try {
      await request({
        url: "/cases/" + caseId + "/timeline",
        method: "POST",
        data: newEvent,
      });
      this.setData({ showAddModal: false });
      this.loadEvents(caseId);
      wx.showToast({ title: "添加成功" });
    } catch (e) {
      wx.showToast({ title: "添加失败", icon: "none" });
    }
  },

  onDeleteEvent(e) {
    const { eventId } = e.currentTarget.dataset;
    wx.showModal({
      title: "确认删除",
      content: "删除后不可恢复，确认删除？",
      success: async (res) => {
        if (res.confirm) {
          try {
            await request({ url: "/cases/" + this.data.caseId + "/timeline/" + eventId, method: "DELETE" });
            this.loadEvents(this.data.caseId);
            wx.showToast({ title: "已删除" });
          } catch (e) {
            wx.showToast({ title: "删除失败", icon: "none" });
          }
        }
      },
    });
  },
});
