// utils/request.js
// 说明：为避免与 auth.js 循环依赖，401 静默重登在本模块内直接实现
const { getToken, setToken } = require("./storage");

const BASE_URL = "http://localhost:8080/api/v1";
const TIMEOUT = 15000;

let reLoginInFlight = false;

function getWxCode() {
  return new Promise((resolve, reject) => {
    wx.login({
      success(r) { r.code ? resolve(r.code) : reject(new Error(r.errMsg)); },
      fail: reject,
    });
  });
}

/** 静默登录：用 wx.login 的 code 换 token（不走本模块的 request，避免自引用） */
async function silentLogin() {
  const code = await getWxCode();
  const res = await rawRequest({
    url: BASE_URL + "/auth/login",
    method: "POST",
    data: { code },
    header: { "Content-Type": "application/json" },
  });
  if (res.statusCode !== 200) {
    throw new Error((res.data && res.data.message) || "登录失败");
  }
  setToken(res.data.token);
  return res.data;
}

function rawRequest(options) {
  return new Promise((resolve, reject) => {
    const opts = { ...options, timeout: TIMEOUT };
    const task = wx.request({
      ...opts,
      success: resolve,
      fail: reject,
    });
    // 低版本基础库不支持 timeout 参数时兜底中断
    if (task && typeof task.abort === "function") {
      setTimeout(() => {
        try { task.abort(); } catch (e) { /* ignore */ }
      }, TIMEOUT);
    }
  });
}

async function request({ url, method = "GET", data = {}, header = {}, enableChunked = false }) {
  const token = getToken();
  if (!token) {
    return Promise.reject(new Error("未登录，请先调用 getApp().ensureLogin()"));
  }

  const options = {
    url: BASE_URL + url,
    method,
    data,
    enableChunked,
    header: {
      "Content-Type": "application/json",
      "Authorization": "Bearer " + token,
      ...header,
    },
  };

  const res = await rawRequest(options);
  if (res.statusCode === 200) {
    return res.data;
  }

  // 401：静默重登一次并重放原请求；再次 401 才清 token 回首页
  if (res.statusCode === 401 && !reLoginInFlight) {
    reLoginInFlight = true;
    try {
      const fresh = await silentLogin();
      const retry = await rawRequest({
        ...options,
        header: { ...options.header, "Authorization": "Bearer " + fresh.token },
      });
      if (retry.statusCode === 200) {
        return retry.data;
      }
      if (retry.statusCode === 401) {
        wx.removeStorageSync("token");
        wx.reLaunch({ url: "/pages/index/index" });
        throw new Error("登录已过期");
      }
      throw new Error((retry.data && retry.data.message) || "请求失败");
    } catch (e) {
      if (e && e.message === "登录已过期") throw e;
      wx.removeStorageSync("token");
      wx.reLaunch({ url: "/pages/index/index" });
      throw new Error("登录已过期");
    } finally {
      reLoginInFlight = false;
    }
  }

  if (res.statusCode === 401) {
    throw new Error("登录已过期");
  }
  if (res.data && res.data.message) {
    throw new Error(res.data.message);
  }
  throw new Error("请求失败(" + res.statusCode + ")");
}

module.exports = { request, BASE_URL };
