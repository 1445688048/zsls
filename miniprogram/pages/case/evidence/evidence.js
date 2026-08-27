// pages/case/evidence/evidence.js
// 证据采集闭环：选择媒体 → 上传后端落盘 → evidenceRefs 回写；收集/删除同步 PUT/DELETE
const { request, BASE_URL } = require("../../../utils/request");
const { getToken } = require("../../../utils/storage");

Page({
  data: {
    caseId: null,
    evidenceList: [],
    necessary: [],
    enhancing: [],
    loading: true,
    uploading: false,
  },

  onLoad(options) {
    const caseId = options.caseId;
    if (caseId) {
      this.setData({ caseId });
      this.loadEvidence(caseId);
    }
  },

  async loadEvidence(caseId) {
    await getApp().ensureLogin();
    this.setData({ loading: true });
    try {
      const res = await request({ url: "/cases/" + caseId, method: "GET" });
      const refs = (res && res.evidenceRefs) || [];

      // 已收集的 refId 集合（canned 清单项与上传项共用同一 id 空间）
      const collectedIds = {};
      refs.forEach((r) => { if (r.collected) collectedIds[r.refId] = true; });
      // 列表只展示有 url 的真实上传文件；canned 标记产生的无 url ref 仅用于收集状态
      const uploadedRefs = refs.filter((r) => !!r.url);

      const necessary = [
        { id: "contract", name: "劳动合同", description: "证明劳动关系、薪资标准、合同期限", collected: !!collectedIds.contract },
        { id: "id_card", name: "身份证复印件", description: "申请仲裁/投诉的身份证明", collected: !!collectedIds.id_card },
        { id: "payroll_record", name: "工资流水或工资条", description: "证明工资标准、发放时间、是否欠薪", collected: !!collectedIds.payroll_record },
        { id: "attendance_record", name: "考勤记录", description: "证明出勤情况、加班时长、是否在职", collected: !!collectedIds.attendance_record },
      ];
      const enhancing = [
        { id: "hr_chat", name: "与HR/老板的聊天记录", description: "证明辞退通知、欠薪沟通、调岗降薪等", collected: !!collectedIds.hr_chat },
        { id: "social_payment_record", name: "社保/公积金缴费记录", description: "证明劳动关系存续期、公司未缴/少缴情况", collected: !!collectedIds.social_payment_record },
        { id: "termination_letter", name: "书面辞退通知", description: "证明公司单方解除的事实和理由", collected: !!collectedIds.termination_letter },
      ];

      this.setData({ necessary, enhancing, evidenceList: uploadedRefs, loading: false });
    } catch (e) {
      console.error("加载证据失败:", e);
      this.setData({ loading: false });
    }
  },

  // ---------- 上传 ----------
  onChooseMedia() {
    if (this.data.uploading) return;
    wx.chooseMedia({
      count: 9,
      mediaType: ["image", "video"],
      sourceType: ["album", "camera"],
      success: async (res) => {
        const files = res.tempFiles;
        this.setData({ uploading: true });
        wx.showLoading({ title: "上传中 0/" + files.length });
        const uploaded = [];
        let done = 0;
        for (const f of files) {
          try {
            const ref = await this.uploadOne(f.tempFilePath);
            if (ref) uploaded.push(ref);
          } catch (e) {
            console.error("上传失败:", f.tempFilePath, e);
          }
          done++;
          wx.showLoading({ title: "上传中 " + done + "/" + files.length });
        }
        wx.hideLoading();
        this.setData({ uploading: false, evidenceList: [...this.data.evidenceList, ...uploaded] });
        wx.showToast({ title: "已上传 " + uploaded.length + " 个", icon: "success" });
      },
      fail: (err) => {
        console.error("选择媒体失败:", err);
      },
    });
  },

  uploadOne(tempFilePath) {
    return new Promise((resolve, reject) => {
      wx.uploadFile({
        url: BASE_URL + "/cases/" + this.data.caseId + "/evidence",
        filePath: tempFilePath,
        name: "file",
        header: { "Authorization": "Bearer " + (getToken() || "") },
        success(res) {
          if (res.statusCode === 200) {
            try {
              resolve(JSON.parse(res.data));
            } catch (e) {
              resolve(null);
            }
          } else {
            reject(new Error("上传失败(" + res.statusCode + ")"));
          }
        },
        fail: reject,
      });
    });
  },

  // ---------- 收集状态持久化 ----------
  async onCollectEvidence(e) {
    const { id } = e.currentTarget.dataset;
    const newCollected = !this.isCollected(id);
    try {
      await request({
        url: "/cases/" + this.data.caseId + "/evidence/" + id,
        method: "PUT",
        data: { name: this.cannedName(id), collected: newCollected },
      });
      this.setData({
        necessary: this.data.necessary.map((i) => (i.id === id ? { ...i, collected: newCollected } : i)),
        enhancing: this.data.enhancing.map((i) => (i.id === id ? { ...i, collected: newCollected } : i)),
      });
      wx.showToast({ title: newCollected ? "已标记为已收集" : "已取消收集", icon: "success" });
    } catch (err) {
      console.error("更新收集状态失败:", err);
      wx.showToast({ title: "操作失败", icon: "none" });
    }
  },

  isCollected(id) {
    return (
      this.data.necessary.some((i) => i.id === id && i.collected) ||
      this.data.enhancing.some((i) => i.id === id && i.collected)
    );
  },

  cannedName(id) {
    const all = [...this.data.necessary, ...this.data.enhancing];
    const hit = all.find((i) => i.id === id);
    return hit ? hit.name : id;
  },

  // ---------- 删除 ----------
  async onDeleteEvidence(e) {
    const { index } = e.currentTarget.dataset;
    const item = this.data.evidenceList[index];
    if (!item || !item.refId) return;
    wx.showModal({
      title: "确认删除",
      content: "删除后不可恢复，确认删除？",
      success: async (r) => {
        if (!r.confirm) return;
        try {
          await request({ url: "/cases/" + this.data.caseId + "/evidence/" + item.refId, method: "DELETE" });
          const list = this.data.evidenceList.slice();
          list.splice(index, 1);
          this.setData({ evidenceList: list });
          wx.showToast({ title: "已删除" });
        } catch (e) {
          wx.showToast({ title: "删除失败", icon: "none" });
        }
      },
    });
  },

  onPreviewImage(e) {
    const { path } = e.currentTarget.dataset;
    if (!path) return;
    wx.previewImage({ current: path, urls: [path] });
  },
});
