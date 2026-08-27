// utils/casenav.js
// 统一案件跳转入口：tabBar 页不能 navigateTo 也不能带 URL 参数，
// 因此先写入当前案件到 storage，再 switchTab 到详情页。
const { setCurrentCase } = require("./storage");

/**
 * 跳转到案件详情页（tab 页）
 * @param {{caseId: number|string, title?: string, domainType?: string}} caseInfo
 */
function goToCase(caseInfo) {
  setCurrentCase({
    caseId: caseInfo.caseId,
    title: caseInfo.title || "",
    domainType: caseInfo.domainType || "",
  });
  wx.switchTab({ url: "/pages/case/detail/detail" });
}

module.exports = { goToCase };
