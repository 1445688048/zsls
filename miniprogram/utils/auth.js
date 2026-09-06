// utils/auth.js
const { setUser } = require("./storage");
const { silentLogin } = require("./request");

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

/** 登录：换取 token 并写入本地用户信息（/auth/login 免鉴权，走 silentLogin 而非 request） */
async function login() {
  try {
    const res = await silentLogin();
    setUser({ userId: res.userId, openid: res.openid });
    return res;
  } catch (e) {
    console.error("登录失败:", e);
    throw e;
  }
}

module.exports = { wxLogin, login };
