// utils/storage.js
const TOKEN_KEY = "token";
const USER_KEY = "user";
const CASE_KEY = "currentCase";
const SESSION_KEY = "currentSession";
const PRIVACY_AGREED = "privacy_agreed";

function getToken() {
  return wx.getStorageSync(TOKEN_KEY);
}

function setToken(token) {
  wx.setStorageSync(TOKEN_KEY, token);
}

function setUser(user) {
  wx.setStorageSync(USER_KEY, user);
}

function getUser() {
  return wx.getStorageSync(USER_KEY);
}

function setCurrentCase(caseInfo) {
  wx.setStorageSync(CASE_KEY, caseInfo);
}

function getCurrentCase() {
  return wx.getStorageSync(CASE_KEY);
}

function setCurrentSession(sessionInfo) {
  wx.setStorageSync(SESSION_KEY, sessionInfo);
}

function getCurrentSession() {
  return wx.getStorageSync(SESSION_KEY);
}

function isPrivacyAgreed() {
  return wx.getStorageSync(PRIVACY_AGREED) === true;
}

function setPrivacyAgreed() {
  wx.setStorageSync(PRIVACY_AGREED, true);
}

module.exports = {
  getToken,
  setToken,
  setUser,
  getUser,
  setCurrentCase,
  getCurrentCase,
  setCurrentSession,
  getCurrentSession,
  isPrivacyAgreed,
  setPrivacyAgreed,
};
