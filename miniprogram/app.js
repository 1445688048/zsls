// app.js
const { login } = require("./utils/auth");
const { isPrivacyAgreed } = require("./utils/storage");

App({
  onLaunch() {
    // 自动登录（隐私门禁由首页 onShow 拦截，此处只做静默登录）
    this.ensureLogin();
  },

  _loginReady: null,

  /**
   * 幂等的登录就绪 Promise：
   * - 页面首个请求前 await getApp().ensureLogin()，避免登录竞态；
   * - 登录失败也 resolve（页面正常渲染，由 request 的 401 兜底重登）。
   */
  ensureLogin() {
    if (!this._loginReady) {
      this._loginReady = (async () => {
        if (!isPrivacyAgreed()) return null;
        try {
          return await login();
        } catch (e) {
          console.log("自动登录失败，等待用户操作", e);
          return null;
        }
      })();
    }
    return this._loginReady;
  },

  globalData: {
    userInfo: null,
  },
});
