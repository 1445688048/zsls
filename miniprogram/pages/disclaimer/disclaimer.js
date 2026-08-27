// pages/disclaimer/disclaimer.js
const { setPrivacyAgreed } = require("../../utils/storage");

Page({
  data: {
    agreed: false,
  },

  onAgree() {
    setPrivacyAgreed();
    wx.reLaunch({ url: "/pages/index/index" });
  },

  onDisagree() {
    wx.exitMiniProgram();
  },
});
