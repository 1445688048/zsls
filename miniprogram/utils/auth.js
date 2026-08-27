// utils/auth.js
const { setToken, setUser } = require("./storage");
const { request } = require("./request");

async function wxLogin() {
  return new Promise((resolve, reject) => {
    wx.login({
      success(loginRes) {
        if (loginRes.code) {
          resolve(loginRes.code);
        } else {
          reject(new Error("wx.login 失败: " + loginRes.errMsg));
        }
      },
      fail(err) {
        reject(err);
      },
    });
  });
}

async function login() {
  try {
    const code = await wxLogin();
    const res = await request({
      url: "/auth/login",
      method: "POST",
      data: { code },
    });
    setToken(res.token);
    setUser({ userId: res.userId, openid: res.openid });
    return res;
  } catch (e) {
    console.error("登录失败:", e);
    throw e;
  }
}

module.exports = { wxLogin, login };
