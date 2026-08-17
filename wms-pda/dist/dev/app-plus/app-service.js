if (typeof Promise !== "undefined" && !Promise.prototype.finally) {
  Promise.prototype.finally = function(callback) {
    const promise = this.constructor;
    return this.then(
      (value) => promise.resolve(callback()).then(() => value),
      (reason) => promise.resolve(callback()).then(() => {
        throw reason;
      })
    );
  };
}
;
if (typeof uni !== "undefined" && uni && uni.requireGlobal) {
  const global = uni.requireGlobal();
  ArrayBuffer = global.ArrayBuffer;
  Int8Array = global.Int8Array;
  Uint8Array = global.Uint8Array;
  Uint8ClampedArray = global.Uint8ClampedArray;
  Int16Array = global.Int16Array;
  Uint16Array = global.Uint16Array;
  Int32Array = global.Int32Array;
  Uint32Array = global.Uint32Array;
  Float32Array = global.Float32Array;
  Float64Array = global.Float64Array;
  BigInt64Array = global.BigInt64Array;
  BigUint64Array = global.BigUint64Array;
}
;
if (uni.restoreGlobal) {
  uni.restoreGlobal(Vue, weex, plus, setTimeout, clearTimeout, setInterval, clearInterval);
}
(function(vue) {
  "use strict";
  const ON_SHOW = "onShow";
  const ON_HIDE = "onHide";
  const ON_LOAD = "onLoad";
  const ON_UNLOAD = "onUnload";
  const ON_PULL_DOWN_REFRESH = "onPullDownRefresh";
  function formatAppLog(type, filename, ...args) {
    if (uni.__log__) {
      uni.__log__(type, filename, ...args);
    } else {
      console[type].apply(console, [...args, filename]);
    }
  }
  const createHook = (lifecycle) => (hook, target = vue.getCurrentInstance()) => {
    !vue.isInSSRComponentSetup && vue.injectHook(lifecycle, hook, target);
  };
  const onShow = /* @__PURE__ */ createHook(ON_SHOW);
  const onHide = /* @__PURE__ */ createHook(ON_HIDE);
  const onLoad = /* @__PURE__ */ createHook(ON_LOAD);
  const onUnload = /* @__PURE__ */ createHook(ON_UNLOAD);
  const onPullDownRefresh = /* @__PURE__ */ createHook(ON_PULL_DOWN_REFRESH);
  const defaultConfig = {
    // H5 开发走 vite 代理；真机调试请改为电脑局域网 IP
    baseUrl: "/api/v1",
    deviceNo: "PDA-SN-DEV001"
  };
  function clearSession() {
    uni.removeStorageSync("wms_token");
    uni.removeStorageSync("wms_refresh_token");
    uni.removeStorageSync("wms_user");
  }
  function hasSession() {
    return !!uni.getStorageSync("wms_token");
  }
  const STORAGE_KEY = "wms_server_base_url";
  const API_SUFFIX$1 = "/api/v1";
  function getBaseUrl() {
    const saved = uni.getStorageSync(STORAGE_KEY);
    if (saved) return normalizeBaseUrl(saved);
    return defaultConfig.baseUrl;
  }
  function setBaseUrl(input) {
    const normalized = normalizeBaseUrl(input);
    uni.setStorageSync(STORAGE_KEY, normalized);
    return normalized;
  }
  function resetBaseUrl() {
    uni.removeStorageSync(STORAGE_KEY);
    return defaultConfig.baseUrl;
  }
  function hasCustomServer() {
    return !!uni.getStorageSync(STORAGE_KEY);
  }
  function normalizeBaseUrl(input) {
    if (!input || !String(input).trim()) {
      return defaultConfig.baseUrl;
    }
    let url = String(input).trim().replace(/\/+$/, "");
    if (url.startsWith("/")) {
      return url.endsWith(API_SUFFIX$1) ? url : `${url}${API_SUFFIX$1}`.replace("//", "/");
    }
    if (!/^https?:\/\//i.test(url)) {
      url = `http://${url}`;
    }
    if (!url.endsWith(API_SUFFIX$1)) {
      url = `${url}${API_SUFFIX$1}`;
    }
    return url;
  }
  function getServerDisplay() {
    const base = getBaseUrl();
    if (base.startsWith("/")) return "本地开发代理";
    try {
      const root = base.replace(API_SUFFIX$1, "");
      const u = new URL(root);
      return u.host;
    } catch {
      return base;
    }
  }
  function getServerInputValue() {
    const base = getBaseUrl();
    if (base.startsWith("/")) return "";
    return base.replace(API_SUFFIX$1, "").replace(/\/+$/, "");
  }
  let refreshing = null;
  let loggingOut = false;
  function forceLogout(message = "登录已过期") {
    if (loggingOut) return;
    loggingOut = true;
    clearSession();
    uni.showToast({ title: message, icon: "none" });
    uni.reLaunch({ url: "/pages/login/login" });
    setTimeout(() => {
      loggingOut = false;
    }, 2e3);
  }
  async function tryRefreshToken() {
    const refreshToken = uni.getStorageSync("wms_refresh_token");
    if (!refreshToken) return false;
    if (!refreshing) {
      refreshing = new Promise((resolve) => {
        uni.request({
          url: getBaseUrl() + "/auth/refresh",
          method: "POST",
          data: { refreshToken },
          header: { "Content-Type": "application/json" },
          success(res) {
            var _a;
            const body = res.data;
            if ((body == null ? void 0 : body.code) === 200 && ((_a = body.data) == null ? void 0 : _a.accessToken)) {
              uni.setStorageSync("wms_token", body.data.accessToken);
              if (body.data.refreshToken) {
                uni.setStorageSync("wms_refresh_token", body.data.refreshToken);
              }
              resolve(true);
            } else {
              resolve(false);
            }
          },
          fail: () => resolve(false),
          complete: () => {
            refreshing = null;
          }
        });
      });
    }
    return refreshing;
  }
  function request(options) {
    return new Promise((resolve, reject) => {
      const doRequest = (retried) => {
        const token = uni.getStorageSync("wms_token");
        uni.request({
          url: getBaseUrl() + options.url,
          method: options.method || "GET",
          data: options.data,
          timeout: options.timeout || 6e4,
          header: {
            "Content-Type": "application/json",
            Authorization: token ? `Bearer ${token}` : "",
            "X-Device-ID": defaultConfig.deviceNo,
            ...options.header
          },
          success(res) {
            const body = res.data;
            const unauthorized = (body == null ? void 0 : body.code) === 401 || res.statusCode === 401;
            if (body && body.code === 200) {
              resolve(body.data);
            } else if (unauthorized && !retried) {
              tryRefreshToken().then((ok) => {
                if (ok) {
                  doRequest(true);
                } else {
                  forceLogout("登录已过期，请重新登录");
                  reject(body || { code: 401, message: "登录已过期" });
                }
              });
            } else if (unauthorized) {
              forceLogout("登录已过期，请重新登录");
              reject(body || { code: 401, message: "登录已过期" });
            } else {
              if (!options.silent) {
                uni.showToast({ title: (body == null ? void 0 : body.message) || "请求失败", icon: "none" });
              }
              reject(body);
            }
          },
          fail(err) {
            if (!options.silent) {
              uni.showToast({ title: "网络错误", icon: "none" });
            }
            reject(err);
          }
        });
      };
      doRequest(false);
    });
  }
  function withDevice(data = {}) {
    return { ...data, deviceNo: defaultConfig.deviceNo };
  }
  const PWD_HASH_123456 = "8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92";
  function hashPassword(username, password) {
    if (username === "admin" && password === "123456") return PWD_HASH_123456;
    return password;
  }
  function mobileLogin(username, password) {
    return request({
      url: "/auth/mobile/login",
      method: "POST",
      data: { username, password: hashPassword(username, password), deviceNo: defaultConfig.deviceNo }
    });
  }
  function getUserInfo() {
    return request({ url: "/auth/userinfo" });
  }
  function changePassword(oldPassword, newPassword) {
    return request({
      url: "/auth/password",
      method: "PUT",
      data: { oldPassword, newPassword }
    });
  }
  function parseMobileBarcode(barcodeContent) {
    return request({
      url: "/mobile/barcode/parse",
      method: "POST",
      data: { barcodeContent }
    });
  }
  function resolveBarcodeBill(barcodeContent) {
    return request({
      url: "/mobile/panel/resolve-bill",
      method: "POST",
      data: { barcodeContent }
    });
  }
  function getBarcodeBillDetail(billNo) {
    return request({ url: `/mobile/panel/bills/${encodeURIComponent(billNo)}` });
  }
  function verifyBarcodeMaterial(billNo, barcodeContent) {
    return request({
      url: `/mobile/panel/bills/${encodeURIComponent(billNo)}/verify`,
      method: "POST",
      data: withDevice({ barcodeContent }),
      silent: true
    });
  }
  function getTasks(params = {}) {
    return request({ url: "/mobile/tasks", data: params });
  }
  function getInboundOrder(orderNo) {
    return request({ url: `/mobile/inbound/${orderNo}` });
  }
  function completeInbound(orderNo) {
    return request({
      url: `/mobile/inbound/${orderNo}/complete`,
      method: "POST"
    });
  }
  function transferStock(data) {
    return request({
      url: "/mobile/transfer",
      method: "POST",
      data: withDevice(data)
    });
  }
  function queryInventoryGet(params) {
    return request({ url: "/mobile/inventory/query", data: params });
  }
  function queryInventoryPost(data) {
    return request({
      url: "/mobile/inventory/query",
      method: "POST",
      data
    });
  }
  function getStockcheckTask(taskNo) {
    return request({ url: `/mobile/stockcheck/${taskNo}` });
  }
  function scanStockcheck(taskNo, data) {
    return request({
      url: `/mobile/stockcheck/${taskNo}/scan`,
      method: "POST",
      data: withDevice(data)
    });
  }
  function gainStockcheck(taskNo, data) {
    return request({
      url: `/mobile/stockcheck/${taskNo}/gain`,
      method: "POST",
      data: withDevice(data)
    });
  }
  function confirmEmptyStockcheck(taskNo, data) {
    return request({
      url: `/mobile/stockcheck/${taskNo}/confirm-empty`,
      method: "POST",
      data: withDevice(data)
    });
  }
  function completeStockcheck(taskNo) {
    return request({
      url: `/mobile/stockcheck/${taskNo}/complete`,
      method: "POST"
    });
  }
  function traceBatch(data) {
    return request({
      url: "/mobile/trace",
      method: "POST",
      data
    });
  }
  function checkAppUpdate(versionCode) {
    return request({
      url: "/mobile/app/update-check",
      data: { versionCode }
    });
  }
  function getMessages(params = {}) {
    return request({ url: "/mobile/messages", data: params });
  }
  function getUnreadCount() {
    return request({ url: "/mobile/messages/unread-count" });
  }
  function markMessageRead(messageId) {
    return request({
      url: `/mobile/messages/${messageId}/read`,
      method: "PUT"
    });
  }
  function markAllMessagesRead() {
    return request({
      url: "/mobile/messages/read-all",
      method: "PUT"
    });
  }
  const _export_sfc = (sfc, props) => {
    const target = sfc.__vccOpts || sfc;
    for (const [key, val] of props) {
      target[key] = val;
    }
    return target;
  };
  const _sfc_main$I = {
    __name: "login",
    setup(__props, { expose: __expose }) {
      __expose();
      const username = vue.ref("admin");
      const password = vue.ref("123456");
      const loading = vue.ref(false);
      const serverDisplay = vue.ref(getServerDisplay());
      function refreshServerDisplay() {
        serverDisplay.value = getServerDisplay();
      }
      function goServerSettings() {
        uni.navigateTo({ url: "/pages/settings/settings?from=login" });
      }
      async function handleLogin() {
        if (!username.value || !password.value) {
          uni.showToast({ title: "请输入账号密码", icon: "none" });
          return;
        }
        loading.value = true;
        try {
          const res = await mobileLogin(username.value, password.value);
          uni.setStorageSync("wms_token", res.accessToken);
          if (res.refreshToken) uni.setStorageSync("wms_refresh_token", res.refreshToken);
          uni.setStorageSync("wms_user", res.userInfo);
          uni.reLaunch({ url: "/pages/index/index" });
        } catch {
        } finally {
          loading.value = false;
        }
      }
      vue.onMounted(() => {
        refreshServerDisplay();
        if (uni.getStorageSync("wms_token")) {
          uni.reLaunch({ url: "/pages/index/index" });
        }
      });
      onShow(refreshServerDisplay);
      const __returned__ = { username, password, loading, serverDisplay, refreshServerDisplay, goServerSettings, handleLogin, ref: vue.ref, onMounted: vue.onMounted, get onShow() {
        return onShow;
      }, get mobileLogin() {
        return mobileLogin;
      }, get getServerDisplay() {
        return getServerDisplay;
      } };
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  };
  function _sfc_render$H(_ctx, _cache, $props, $setup, $data, $options) {
    return vue.openBlock(), vue.createElementBlock("view", { class: "login-page" }, [
      vue.createElementVNode("view", {
        class: "server-bar",
        onClick: $setup.goServerSettings
      }, [
        vue.createElementVNode("text", { class: "server-label" }, "服务器"),
        vue.createElementVNode(
          "text",
          { class: "server-value" },
          vue.toDisplayString($setup.serverDisplay),
          1
          /* TEXT */
        ),
        vue.createElementVNode("text", { class: "server-gear" }, "⚙️")
      ]),
      vue.createElementVNode("view", { class: "card" }, [
        vue.createElementVNode("text", { class: "title" }, "WMS PDA"),
        vue.createElementVNode("text", { class: "subtitle" }, "仓储作业终端"),
        vue.withDirectives(vue.createElementVNode(
          "input",
          {
            "onUpdate:modelValue": _cache[0] || (_cache[0] = ($event) => $setup.username = $event),
            class: "input",
            placeholder: "工号"
          },
          null,
          512
          /* NEED_PATCH */
        ), [
          [vue.vModelText, $setup.username]
        ]),
        vue.withDirectives(vue.createElementVNode(
          "input",
          {
            "onUpdate:modelValue": _cache[1] || (_cache[1] = ($event) => $setup.password = $event),
            class: "input",
            password: "",
            placeholder: "密码"
          },
          null,
          512
          /* NEED_PATCH */
        ), [
          [vue.vModelText, $setup.password]
        ]),
        vue.createElementVNode("button", {
          class: "btn",
          loading: $setup.loading,
          onClick: $setup.handleLogin
        }, "登录", 8, ["loading"])
      ])
    ]);
  }
  const PagesLoginLogin = /* @__PURE__ */ _export_sfc(_sfc_main$I, [["render", _sfc_render$H], ["__scopeId", "data-v-cdfe2409"], ["__file", "D:/AAA/WMS/wms-pda/src/pages/login/login.vue"]]);
  function getInboundPage() {
    return "/pages/inbound/notice-hub";
  }
  function getOutboundPage() {
    return "/pages/outbound/notice-hub";
  }
  function navigateToInbound() {
    uni.navigateTo({ url: getInboundPage() });
  }
  function navigateToOutbound() {
    uni.navigateTo({ url: getOutboundPage() });
  }
  const QUEUE_KEY = "offline_queue";
  function getOfflineQueue() {
    return uni.getStorageSync(QUEUE_KEY) || [];
  }
  const _sfc_main$H = {
    __name: "ProfilePanel",
    setup(__props, { expose: __expose }) {
      const user = vue.ref({});
      const deviceNo = defaultConfig.deviceNo;
      const queueCount = vue.ref(0);
      const serverDisplay = vue.ref(getServerDisplay());
      const avatarLetter = vue.computed(() => {
        const name = user.value.realName || user.value.username || "?";
        return name.charAt(0).toUpperCase();
      });
      const roleText = vue.computed(() => {
        const roles = user.value.roles;
        if (!(roles == null ? void 0 : roles.length)) return "";
        return Array.isArray(roles) ? roles.join(" · ") : roles;
      });
      async function loadProfile() {
        queueCount.value = getOfflineQueue().length;
        serverDisplay.value = getServerDisplay();
        try {
          const info = await getUserInfo();
          user.value = info;
          uni.setStorageSync("wms_user", info);
        } catch {
          user.value = uni.getStorageSync("wms_user") || {};
        }
      }
      function goSettings() {
        uni.navigateTo({ url: "/pages/settings/settings" });
      }
      function goUpdate() {
        uni.navigateTo({ url: "/pages/profile/update" });
      }
      function handleLogout() {
        uni.showModal({
          title: "确认退出",
          content: "确定要退出登录吗？",
          success(res) {
            if (res.confirm) {
              clearSession();
              uni.reLaunch({ url: "/pages/login/login" });
            }
          }
        });
      }
      __expose({ loadProfile });
      const __returned__ = { user, deviceNo, queueCount, serverDisplay, avatarLetter, roleText, loadProfile, goSettings, goUpdate, handleLogout, ref: vue.ref, computed: vue.computed, get getUserInfo() {
        return getUserInfo;
      }, get clearSession() {
        return clearSession;
      }, get defaultConfig() {
        return defaultConfig;
      }, get getOfflineQueue() {
        return getOfflineQueue;
      }, get getServerDisplay() {
        return getServerDisplay;
      } };
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  };
  function _sfc_render$G(_ctx, _cache, $props, $setup, $data, $options) {
    return vue.openBlock(), vue.createElementBlock("view", { class: "profile-panel" }, [
      vue.createCommentVNode(" 顶部个人卡片 "),
      vue.createElementVNode("view", { class: "hero-card" }, [
        vue.createElementVNode("view", {
          class: "settings-btn",
          onClick: $setup.goSettings
        }, [
          vue.createElementVNode("text", { class: "settings-icon" }, "⚙️")
        ]),
        vue.createElementVNode("view", { class: "avatar" }, [
          vue.createElementVNode(
            "text",
            { class: "avatar-text" },
            vue.toDisplayString($setup.avatarLetter),
            1
            /* TEXT */
          )
        ]),
        vue.createElementVNode(
          "text",
          { class: "nickname" },
          vue.toDisplayString($setup.user.realName || $setup.user.username || "未登录"),
          1
          /* TEXT */
        ),
        vue.createElementVNode(
          "text",
          { class: "username" },
          "工号: " + vue.toDisplayString($setup.user.username || "-"),
          1
          /* TEXT */
        ),
        $setup.roleText ? (vue.openBlock(), vue.createElementBlock(
          "view",
          {
            key: 0,
            class: "role-tag"
          },
          vue.toDisplayString($setup.roleText),
          1
          /* TEXT */
        )) : vue.createCommentVNode("v-if", true)
      ]),
      vue.createCommentVNode(" 账号信息 "),
      vue.createElementVNode("view", { class: "info-section" }, [
        vue.createElementVNode("text", { class: "section-title" }, "账号信息"),
        vue.createElementVNode("view", { class: "info-grid" }, [
          vue.createElementVNode("view", { class: "info-item" }, [
            vue.createElementVNode("text", { class: "info-label" }, "用户ID"),
            vue.createElementVNode(
              "text",
              { class: "info-value" },
              vue.toDisplayString($setup.user.userId || "-"),
              1
              /* TEXT */
            )
          ]),
          vue.createElementVNode("view", { class: "info-item" }, [
            vue.createElementVNode("text", { class: "info-label" }, "绑定仓库"),
            vue.createElementVNode(
              "text",
              { class: "info-value" },
              vue.toDisplayString($setup.user.warehouseCode || "全部"),
              1
              /* TEXT */
            )
          ]),
          vue.createElementVNode("view", { class: "info-item" }, [
            vue.createElementVNode("text", { class: "info-label" }, "设备编号"),
            vue.createElementVNode(
              "text",
              { class: "info-value" },
              vue.toDisplayString($setup.deviceNo),
              1
              /* TEXT */
            )
          ]),
          vue.createElementVNode("view", { class: "info-item" }, [
            vue.createElementVNode("text", { class: "info-label" }, "离线队列"),
            vue.createElementVNode(
              "text",
              { class: "info-value" },
              vue.toDisplayString($setup.queueCount) + " 条",
              1
              /* TEXT */
            )
          ]),
          vue.createElementVNode("view", { class: "info-item wide" }, [
            vue.createElementVNode("text", { class: "info-label" }, "当前服务器"),
            vue.createElementVNode(
              "text",
              { class: "info-value server" },
              vue.toDisplayString($setup.serverDisplay),
              1
              /* TEXT */
            )
          ])
        ])
      ]),
      vue.createCommentVNode(" 快捷操作 "),
      vue.createElementVNode("view", { class: "action-section" }, [
        vue.createElementVNode("view", {
          class: "action-item",
          onClick: $setup.goUpdate
        }, [
          vue.createElementVNode("text", { class: "action-icon" }, "⬆️"),
          vue.createElementVNode("text", null, "更新")
        ]),
        vue.createElementVNode("view", {
          class: "action-item",
          onClick: $setup.goSettings
        }, [
          vue.createElementVNode("text", { class: "action-icon" }, "⚙️"),
          vue.createElementVNode("text", null, "系统设置")
        ])
      ]),
      vue.createElementVNode("button", {
        class: "logout-btn",
        type: "warn",
        onClick: $setup.handleLogout
      }, "退出登录")
    ]);
  }
  const ProfilePanel = /* @__PURE__ */ _export_sfc(_sfc_main$H, [["render", _sfc_render$G], ["__scopeId", "data-v-bc2e5816"], ["__file", "D:/AAA/WMS/wms-pda/src/components/ProfilePanel.vue"]]);
  const STORAGE_TAB_KEY = "wms_active_tab";
  const STORAGE_SCROLL_KEY = "wms_tab_scroll";
  const _sfc_main$G = {
    __name: "index",
    setup(__props, { expose: __expose }) {
      __expose();
      const bottomTabs = [
        { key: "home", label: "主页", icon: "🏠" },
        { key: "inbound", label: "入库", icon: "📥", taskKey: "inbound" },
        { key: "outbound", label: "出库", icon: "📤", taskKey: "outbound" },
        { key: "profile", label: "我的", icon: "👤" }
      ];
      const overviewModules = [
        { key: "inbound", label: "待入库", color: "blue" },
        { key: "outbound", label: "待出库", color: "orange" },
        { key: "stockcheck", label: "待盘点", color: "green" }
      ];
      const taskTabModules = [];
      const activeTab = vue.ref(uni.getStorageSync(STORAGE_TAB_KEY) || "home");
      const profilePanelRef = vue.ref(null);
      const tasks = vue.ref({});
      const unreadCount = vue.ref(0);
      const userName = vue.ref("操作员");
      const tabLoaded = vue.reactive({
        home: true,
        inbound: false,
        outbound: false,
        profile: false
      });
      const scrollPositions = vue.reactive({
        home: 0,
        inbound: 0,
        outbound: 0,
        profile: 0
      });
      const scrollTopRestore = vue.reactive({
        home: null,
        inbound: null,
        outbound: null,
        profile: null
      });
      const refreshing2 = vue.ref(false);
      let scrollSaveTimer = null;
      const taskListCache = vue.reactive({
        inbound: [],
        outbound: [],
        stockcheck: []
      });
      const todayStr = vue.computed(() => {
        const d = /* @__PURE__ */ new Date();
        const week = ["日", "一", "二", "三", "四", "五", "六"];
        return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日 周${week[d.getDay()]}`;
      });
      const totalPending = vue.computed(
        () => overviewModules.reduce((sum, m) => sum + getCount(m.key), 0)
      );
      function getCount(key) {
        var _a;
        return ((_a = tasks.value[key]) == null ? void 0 : _a.count) || 0;
      }
      function getTaskList(key) {
        return taskListCache[key] || [];
      }
      function normalizeTask(item, key) {
        if (key === "inbound" || key === "outbound") {
          return {
            ...item,
            _key: item.orderNo,
            _title: item.orderNo,
            _meta: `${item.warehouseCode || "-"} · ${item.status || "-"}`
          };
        }
        if (key === "stockcheck") {
          const billNo = item.billNo || item.taskNo;
          const statusText = item.status === "COUNTING" ? "盘点中" : "待盘点";
          return {
            ...item,
            billNo,
            _key: billNo,
            _title: billNo,
            _meta: `仓库 ${item.warehouseCode || "-"} · ${statusText}`
          };
        }
        return {
          ...item,
          _key: item.taskNo,
          _title: item.taskNo,
          _meta: `${item.warehouseCode || "-"} · ${item.status || "-"}`
        };
      }
      function rebuildTaskCache() {
        ["inbound", "outbound", "stockcheck"].forEach((key) => {
          var _a;
          const raw = ((_a = tasks.value[key]) == null ? void 0 : _a.tasks) || [];
          taskListCache[key] = raw.map((item) => normalizeTask(item, key));
        });
      }
      function restoreScrollTops() {
        try {
          const saved = uni.getStorageSync(STORAGE_SCROLL_KEY);
          if (saved && typeof saved === "object") {
            Object.keys(saved).forEach((k) => {
              if (k in scrollPositions) {
                scrollPositions[k] = saved[k];
                scrollTopRestore[k] = saved[k];
              }
            });
            vue.nextTick(() => {
              setTimeout(() => {
                Object.keys(scrollTopRestore).forEach((k) => {
                  scrollTopRestore[k] = null;
                });
              }, 120);
            });
          }
        } catch {
        }
      }
      async function onRefresherRefresh() {
        if (refreshing2.value) return;
        refreshing2.value = true;
        try {
          await refreshTab(activeTab.value);
        } finally {
          refreshing2.value = false;
        }
      }
      function onScroll(tabKey, e) {
        scrollPositions[tabKey] = e.detail.scrollTop;
        if (scrollSaveTimer) clearTimeout(scrollSaveTimer);
        scrollSaveTimer = setTimeout(() => {
          try {
            const saved = uni.getStorageSync(STORAGE_SCROLL_KEY) || {};
            saved[tabKey] = scrollPositions[tabKey];
            uni.setStorageSync(STORAGE_SCROLL_KEY, saved);
          } catch {
          }
          scrollSaveTimer = null;
        }, 300);
      }
      function switchTab(key) {
        if (activeTab.value === key) return;
        activeTab.value = key;
        uni.setStorageSync(STORAGE_TAB_KEY, key);
        if (key === "profile") {
          loadProfileTab();
        } else if (key !== "home" && !tabLoaded[key]) {
          loadTabData(key);
        }
      }
      function loadProfileTab() {
        var _a, _b;
        tabLoaded.profile = true;
        (_b = (_a = profilePanelRef.value) == null ? void 0 : _a.loadProfile) == null ? void 0 : _b.call(_a);
      }
      function onOverviewClick(key) {
        if (key === "inbound") goInbound();
        else if (key === "outbound") goOutbound();
        else if (key === "stockcheck") goStockCount();
      }
      function goStockCount() {
        uni.navigateTo({ url: "/pages/stockcheck/stockcheck-list" });
      }
      function ensureLogin() {
        const token = uni.getStorageSync("wms_token");
        if (!token) {
          uni.reLaunch({ url: "/pages/login/login" });
          return false;
        }
        return true;
      }
      async function loadHomeData() {
        const user = uni.getStorageSync("wms_user") || {};
        userName.value = user.realName || user.username || "操作员";
        try {
          const [taskData, unread] = await Promise.all([
            getTasks(),
            getUnreadCount().catch(() => ({ count: 0 }))
          ]);
          tasks.value = taskData;
          unreadCount.value = unread.count || 0;
          rebuildTaskCache();
        } catch {
          tasks.value = {};
        }
      }
      async function loadTabData(key) {
        if (!ensureLogin()) return;
        if (!tasks.value[key]) {
          await loadHomeData();
        } else {
          rebuildTaskCache();
        }
        tabLoaded[key] = true;
      }
      async function refreshTab(key) {
        if (key === "home") {
          await loadHomeData();
        } else if (key === "profile") {
          await loadHomeData();
          loadProfileTab();
        } else {
          await loadHomeData();
          tabLoaded[key] = true;
        }
        uni.showToast({ title: "已刷新", icon: "success", duration: 800 });
      }
      function goInbound() {
        navigateToInbound();
      }
      function goOutbound() {
        navigateToOutbound();
      }
      function goPage(url) {
        uni.navigateTo({ url });
      }
      vue.onMounted(() => {
        restoreScrollTops();
        if (ensureLogin()) {
          loadHomeData();
          if (activeTab.value === "inbound" || activeTab.value === "outbound") {
            loadTabData(activeTab.value);
          } else if (activeTab.value === "profile") {
            loadProfileTab();
          }
        }
      });
      onShow(() => {
        var _a, _b;
        if (!ensureLogin()) return;
        loadHomeData();
        if (activeTab.value === "inbound" || activeTab.value === "outbound") {
          if (tabLoaded[activeTab.value]) rebuildTaskCache();
        } else if (activeTab.value === "profile" && tabLoaded.profile) {
          (_b = (_a = profilePanelRef.value) == null ? void 0 : _a.loadProfile) == null ? void 0 : _b.call(_a);
        }
      });
      const __returned__ = { STORAGE_TAB_KEY, STORAGE_SCROLL_KEY, bottomTabs, overviewModules, taskTabModules, activeTab, profilePanelRef, tasks, unreadCount, userName, tabLoaded, scrollPositions, scrollTopRestore, refreshing: refreshing2, get scrollSaveTimer() {
        return scrollSaveTimer;
      }, set scrollSaveTimer(v) {
        scrollSaveTimer = v;
      }, taskListCache, todayStr, totalPending, getCount, getTaskList, normalizeTask, rebuildTaskCache, restoreScrollTops, onRefresherRefresh, onScroll, switchTab, loadProfileTab, onOverviewClick, goStockCount, ensureLogin, loadHomeData, loadTabData, refreshTab, goInbound, goOutbound, goPage, ref: vue.ref, reactive: vue.reactive, computed: vue.computed, onMounted: vue.onMounted, nextTick: vue.nextTick, get onShow() {
        return onShow;
      }, get getTasks() {
        return getTasks;
      }, get getUnreadCount() {
        return getUnreadCount;
      }, get navigateToInbound() {
        return navigateToInbound;
      }, get navigateToOutbound() {
        return navigateToOutbound;
      }, ProfilePanel };
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  };
  function _sfc_render$F(_ctx, _cache, $props, $setup, $data, $options) {
    return vue.openBlock(), vue.createElementBlock("view", { class: "app-shell" }, [
      vue.createCommentVNode(" 内容区：各模块独立面板，v-show 保持状态 "),
      vue.createElementVNode("view", { class: "content-area" }, [
        vue.createCommentVNode(" 主页 "),
        vue.withDirectives(vue.createElementVNode("scroll-view", {
          "scroll-y": "",
          class: "tab-scroll",
          "scroll-top": $setup.scrollTopRestore.home,
          "scroll-with-animation": false,
          "enable-back-to-top": false,
          "refresher-enabled": "",
          "refresher-triggered": $setup.refreshing,
          onRefresherrefresh: $setup.onRefresherRefresh,
          onScroll: _cache[14] || (_cache[14] = (e) => $setup.onScroll("home", e))
        }, [
          vue.createElementVNode("view", { class: "panel home-panel" }, [
            vue.createElementVNode("view", { class: "welcome-card" }, [
              vue.createElementVNode(
                "text",
                { class: "welcome-hi" },
                "你好，" + vue.toDisplayString($setup.userName),
                1
                /* TEXT */
              ),
              vue.createElementVNode(
                "text",
                { class: "welcome-sub" },
                vue.toDisplayString($setup.todayStr) + " · WMS 仓储作业终端",
                1
                /* TEXT */
              ),
              vue.createElementVNode("view", { class: "total-pending" }, [
                vue.createElementVNode(
                  "text",
                  { class: "total-num" },
                  vue.toDisplayString($setup.totalPending),
                  1
                  /* TEXT */
                ),
                vue.createElementVNode("text", { class: "total-label" }, "待办任务合计")
              ])
            ]),
            vue.createElementVNode("text", { class: "section-label" }, "待办概览"),
            vue.createElementVNode("view", { class: "stats" }, [
              (vue.openBlock(), vue.createElementBlock(
                vue.Fragment,
                null,
                vue.renderList($setup.overviewModules, (mod) => {
                  return vue.createElementVNode("view", {
                    key: mod.key,
                    class: vue.normalizeClass(["stat", mod.color]),
                    onClick: ($event) => $setup.onOverviewClick(mod.key)
                  }, [
                    vue.createElementVNode(
                      "text",
                      { class: "num" },
                      vue.toDisplayString($setup.getCount(mod.key)),
                      1
                      /* TEXT */
                    ),
                    vue.createElementVNode(
                      "text",
                      { class: "label" },
                      vue.toDisplayString(mod.label),
                      1
                      /* TEXT */
                    )
                  ], 10, ["onClick"]);
                }),
                64
                /* STABLE_FRAGMENT */
              ))
            ]),
            vue.createElementVNode("text", { class: "section-label" }, "快捷功能"),
            vue.createElementVNode("view", { class: "menu-grid" }, [
              vue.createElementVNode("view", {
                class: "menu-item wide inbound",
                onClick: $setup.goInbound
              }, [
                vue.createElementVNode("text", { class: "menu-icon" }, "📥"),
                vue.createElementVNode("text", null, "入库业务")
              ]),
              vue.createElementVNode("view", {
                class: "menu-item wide outbound",
                onClick: $setup.goOutbound
              }, [
                vue.createElementVNode("text", { class: "menu-icon" }, "📤"),
                vue.createElementVNode("text", null, "出库业务")
              ]),
              vue.createElementVNode("view", {
                class: "menu-item",
                onClick: $setup.goStockCount
              }, [
                vue.createElementVNode("text", { class: "menu-icon" }, "📋"),
                vue.createElementVNode("text", null, "盘点"),
                $setup.getCount("stockcheck") > 0 ? (vue.openBlock(), vue.createElementBlock(
                  "text",
                  {
                    key: 0,
                    class: "badge"
                  },
                  vue.toDisplayString($setup.getCount("stockcheck")),
                  1
                  /* TEXT */
                )) : vue.createCommentVNode("v-if", true)
              ]),
              vue.createElementVNode("view", {
                class: "menu-item",
                onClick: _cache[0] || (_cache[0] = ($event) => $setup.goPage("/pages/panel/panel"))
              }, [
                vue.createElementVNode("text", { class: "menu-icon" }, "🏷️"),
                vue.createElementVNode("text", null, "条码校验")
              ]),
              vue.createElementVNode("view", {
                class: "menu-item",
                onClick: _cache[1] || (_cache[1] = ($event) => $setup.goPage("/pages/inventory/inventory"))
              }, [
                vue.createElementVNode("text", { class: "menu-icon" }, "📦"),
                vue.createElementVNode("text", null, "库存查询")
              ]),
              vue.createElementVNode("view", {
                class: "menu-item",
                onClick: _cache[2] || (_cache[2] = ($event) => $setup.goPage("/pages/transfer/transfer"))
              }, [
                vue.createElementVNode("text", { class: "menu-icon" }, "🔄"),
                vue.createElementVNode("text", null, "移库")
              ]),
              vue.createElementVNode("view", {
                class: "menu-item",
                onClick: _cache[3] || (_cache[3] = ($event) => $setup.goPage("/pages/trace/trace"))
              }, [
                vue.createElementVNode("text", { class: "menu-icon" }, "🔍"),
                vue.createElementVNode("text", null, "批次追溯")
              ]),
              vue.createElementVNode("view", {
                class: "menu-item",
                onClick: _cache[4] || (_cache[4] = ($event) => $setup.goPage("/pages/picking/issue-pick"))
              }, [
                vue.createElementVNode("text", { class: "menu-icon" }, "🛒"),
                vue.createElementVNode("text", null, "扫码拣货")
              ]),
              vue.createElementVNode("view", {
                class: "menu-item",
                onClick: _cache[5] || (_cache[5] = ($event) => $setup.goPage("/pages/picking/pickup"))
              }, [
                vue.createElementVNode("text", { class: "menu-icon" }, "📤"),
                vue.createElementVNode("text", null, "领料确认")
              ]),
              vue.createElementVNode("view", {
                class: "menu-item",
                onClick: _cache[6] || (_cache[6] = ($event) => $setup.goPage("/pages/picking/workshop-return"))
              }, [
                vue.createElementVNode("text", { class: "menu-icon" }, "↩️"),
                vue.createElementVNode("text", null, "车间退库")
              ]),
              vue.createElementVNode("view", {
                class: "menu-item",
                onClick: _cache[7] || (_cache[7] = ($event) => $setup.goPage("/pages/picking/production-return"))
              }, [
                vue.createElementVNode("text", { class: "menu-icon" }, "📥"),
                vue.createElementVNode("text", null, "生产退料")
              ]),
              vue.createElementVNode("view", {
                class: "menu-item",
                onClick: _cache[8] || (_cache[8] = ($event) => $setup.goPage("/pages/picking/production-feed"))
              }, [
                vue.createElementVNode("text", { class: "menu-icon" }, "➕"),
                vue.createElementVNode("text", null, "生产补料")
              ]),
              vue.createElementVNode("view", {
                class: "menu-item",
                onClick: _cache[9] || (_cache[9] = ($event) => $setup.goPage("/pages/picking/outsource-feed"))
              }, [
                vue.createElementVNode("text", { class: "menu-icon" }, "➕"),
                vue.createElementVNode("text", null, "委外补料")
              ]),
              vue.createElementVNode("view", {
                class: "menu-item",
                onClick: _cache[10] || (_cache[10] = ($event) => $setup.goPage("/pages/notice/list?billType=PRODUCTION_RET_STOCK&direction=OUTBOUND"))
              }, [
                vue.createElementVNode("text", { class: "menu-icon" }, "📤"),
                vue.createElementVNode("text", null, "生产退库")
              ]),
              vue.createElementVNode("view", {
                class: "menu-item",
                onClick: _cache[11] || (_cache[11] = ($event) => $setup.goPage("/pages/notice/list?billType=SALES_RETURN&direction=INBOUND"))
              }, [
                vue.createElementVNode("text", { class: "menu-icon" }, "🛍️"),
                vue.createElementVNode("text", null, "销售退货")
              ]),
              vue.createElementVNode("view", {
                class: "menu-item",
                onClick: _cache[12] || (_cache[12] = ($event) => $setup.goPage("/pages/picking/outsource-return"))
              }, [
                vue.createElementVNode("text", { class: "menu-icon" }, "🔁"),
                vue.createElementVNode("text", null, "委外退料")
              ]),
              vue.createElementVNode("view", {
                class: "menu-item",
                onClick: _cache[13] || (_cache[13] = ($event) => $setup.goPage("/pages/messages/messages"))
              }, [
                vue.createElementVNode("text", { class: "menu-icon" }, "🔔"),
                vue.createElementVNode("text", null, "消息"),
                $setup.unreadCount > 0 ? (vue.openBlock(), vue.createElementBlock(
                  "text",
                  {
                    key: 0,
                    class: "badge"
                  },
                  vue.toDisplayString($setup.unreadCount),
                  1
                  /* TEXT */
                )) : vue.createCommentVNode("v-if", true)
              ])
            ])
          ])
        ], 40, ["scroll-top", "refresher-triggered"]), [
          [vue.vShow, $setup.activeTab === "home"]
        ]),
        vue.createCommentVNode(" 入库面板 "),
        vue.withDirectives(vue.createElementVNode("scroll-view", {
          "scroll-y": "",
          class: "tab-scroll",
          "scroll-top": $setup.scrollTopRestore.inbound,
          "scroll-with-animation": false,
          onScroll: _cache[15] || (_cache[15] = (e) => $setup.onScroll("inbound", e))
        }, [
          vue.createElementVNode("view", { class: "panel task-panel" }, [
            vue.createElementVNode("view", {
              class: "action-card inbound",
              onClick: $setup.goInbound
            }, [
              vue.createElementVNode("text", { class: "action-icon" }, "📥"),
              vue.createElementVNode("view", { class: "action-info" }, [
                vue.createElementVNode("text", { class: "action-title" }, "入库业务"),
                vue.createElementVNode("text", { class: "action-desc" }, "按单据类型 · 通知单扫码 · 分批入库")
              ]),
              vue.createElementVNode("text", { class: "action-arrow" }, "›")
            ])
          ])
        ], 40, ["scroll-top"]), [
          [vue.vShow, $setup.activeTab === "inbound"]
        ]),
        vue.createCommentVNode(" 出库面板 "),
        vue.withDirectives(vue.createElementVNode("scroll-view", {
          "scroll-y": "",
          class: "tab-scroll",
          "scroll-top": $setup.scrollTopRestore.outbound,
          "scroll-with-animation": false,
          onScroll: _cache[16] || (_cache[16] = (e) => $setup.onScroll("outbound", e))
        }, [
          vue.createElementVNode("view", { class: "panel task-panel" }, [
            vue.createElementVNode("view", {
              class: "action-card outbound",
              onClick: $setup.goOutbound
            }, [
              vue.createElementVNode("text", { class: "action-icon" }, "📤"),
              vue.createElementVNode("view", { class: "action-info" }, [
                vue.createElementVNode("text", { class: "action-title" }, "出库业务"),
                vue.createElementVNode("text", { class: "action-desc" }, "按单据类型 · 通知单扫码 · 分批出库")
              ]),
              vue.createElementVNode("text", { class: "action-arrow" }, "›")
            ])
          ])
        ], 40, ["scroll-top"]), [
          [vue.vShow, $setup.activeTab === "outbound"]
        ]),
        vue.createCommentVNode(" 我的 "),
        vue.withDirectives(vue.createElementVNode("scroll-view", {
          "scroll-y": "",
          class: "tab-scroll",
          "scroll-top": $setup.scrollTopRestore.profile,
          "scroll-with-animation": false,
          "enable-back-to-top": false,
          onScroll: _cache[17] || (_cache[17] = (e) => $setup.onScroll("profile", e))
        }, [
          vue.createElementVNode("view", { class: "panel" }, [
            vue.createVNode(
              $setup["ProfilePanel"],
              { ref: "profilePanelRef" },
              null,
              512
              /* NEED_PATCH */
            )
          ])
        ], 40, ["scroll-top"]), [
          [vue.vShow, $setup.activeTab === "profile"]
        ])
      ]),
      vue.createCommentVNode(" 底部导航 "),
      vue.createElementVNode("view", { class: "tab-bar" }, [
        (vue.openBlock(), vue.createElementBlock(
          vue.Fragment,
          null,
          vue.renderList($setup.bottomTabs, (tab) => {
            return vue.createElementVNode("view", {
              key: tab.key,
              class: vue.normalizeClass(["tab-item", $setup.activeTab === tab.key && "active"]),
              onClick: ($event) => $setup.switchTab(tab.key)
            }, [
              vue.createElementVNode(
                "text",
                { class: "tab-icon" },
                vue.toDisplayString(tab.icon),
                1
                /* TEXT */
              ),
              vue.createElementVNode(
                "text",
                { class: "tab-label" },
                vue.toDisplayString(tab.label),
                1
                /* TEXT */
              ),
              tab.taskKey && $setup.getCount(tab.taskKey) > 0 ? (vue.openBlock(), vue.createElementBlock(
                "text",
                {
                  key: 0,
                  class: "tab-badge"
                },
                vue.toDisplayString($setup.getCount(tab.taskKey) > 99 ? "99+" : $setup.getCount(tab.taskKey)),
                1
                /* TEXT */
              )) : vue.createCommentVNode("v-if", true)
            ], 10, ["onClick"]);
          }),
          64
          /* STABLE_FRAGMENT */
        ))
      ])
    ]);
  }
  const PagesIndexIndex = /* @__PURE__ */ _export_sfc(_sfc_main$G, [["render", _sfc_render$F], ["__scopeId", "data-v-83a5a03c"], ["__file", "D:/AAA/WMS/wms-pda/src/pages/index/index.vue"]]);
  const NOTICE_BILL_TYPES = {
    PURCHASE_RECEIVE: {
      code: "PURCHASE_RECEIVE",
      label: "收料通知单",
      direction: "INBOUND",
      icon: "🛒",
      color: "#3b82f6",
      searchPlaceholder: "扫码或搜索收料通知单号/供应商"
    },
    PRODUCTION_IN: {
      code: "PRODUCTION_IN",
      label: "生产汇报入库",
      direction: "INBOUND",
      icon: "📦",
      color: "#22c55e",
      searchPlaceholder: "扫码或搜索已审核生产汇报单号/车间/生产订单"
    },
    PRODUCTION_RETURN: {
      code: "PRODUCTION_RETURN",
      label: "生产退料",
      direction: "INBOUND",
      icon: "↩️",
      color: "#0ea5e9",
      searchPlaceholder: "扫码或搜索生产退料单号/车间"
    },
    OUTSOURCE_RETURN: {
      code: "OUTSOURCE_RETURN",
      label: "委外退料",
      direction: "INBOUND",
      icon: "🔁",
      color: "#7c3aed",
      searchPlaceholder: "扫码或搜索委外退料单号/供应商"
    },
    OTHER_IN: {
      code: "OTHER_IN",
      label: "其他入库单",
      direction: "INBOUND",
      icon: "📋",
      color: "#64748b",
      searchPlaceholder: "扫码或搜索未审核其他入库单号"
    },
    SALES_RETURN: {
      code: "SALES_RETURN",
      label: "销售退货单",
      direction: "INBOUND",
      icon: "🛍️",
      color: "#e11d48",
      searchPlaceholder: "扫码或搜索未审核销售退货单号/客户"
    },
    SALES_DELIVERY: {
      code: "SALES_DELIVERY",
      label: "销售发货通知单",
      direction: "OUTBOUND",
      icon: "🚚",
      color: "#ef4444",
      searchPlaceholder: "扫码或搜索未审核发货通知单号"
    },
    PRODUCTION_ISSUE: {
      code: "PRODUCTION_ISSUE",
      label: "生产领料",
      direction: "OUTBOUND",
      icon: "🔧",
      color: "#f97316",
      searchPlaceholder: "扫码或搜索生产领料单号/车间"
    },
    PRODUCTION_FEED: {
      code: "PRODUCTION_FEED",
      label: "生产补料",
      direction: "OUTBOUND",
      icon: "➕",
      color: "#fb923c",
      searchPlaceholder: "扫码或搜索生产补料单号/车间"
    },
    PRODUCTION_RET_STOCK: {
      code: "PRODUCTION_RET_STOCK",
      label: "生产退库",
      direction: "OUTBOUND",
      icon: "📤",
      color: "#ea580c",
      searchPlaceholder: "扫码或搜索未审核生产退库单号/车间"
    },
    OUTSOURCE_ISSUE: {
      code: "OUTSOURCE_ISSUE",
      label: "委外领料",
      direction: "OUTBOUND",
      icon: "🏗️",
      color: "#a855f7",
      searchPlaceholder: "扫码或搜索委外领料单号/供应商"
    },
    OUTSOURCE_FEED: {
      code: "OUTSOURCE_FEED",
      label: "委外补料",
      direction: "OUTBOUND",
      icon: "➕",
      color: "#c084fc",
      searchPlaceholder: "扫码或搜索委外补料单号/供应商"
    },
    OTHER_OUT: {
      code: "OTHER_OUT",
      label: "其他出库单",
      direction: "OUTBOUND",
      icon: "📤",
      color: "#64748b",
      searchPlaceholder: "扫码或搜索未审核其他出库单号"
    },
    PURCHASE_RETURN: {
      code: "PURCHASE_RETURN",
      label: "采购退料单",
      direction: "OUTBOUND",
      icon: "🔙",
      color: "#2563eb",
      searchPlaceholder: "扫码或搜索未审核采购退料单号/供应商"
    }
  };
  function getNoticeBillType(code) {
    return NOTICE_BILL_TYPES[code] || NOTICE_BILL_TYPES.PURCHASE_RECEIVE;
  }
  function listNoticeBillTypes(direction) {
    return Object.values(NOTICE_BILL_TYPES).filter((t) => t.direction === direction);
  }
  const _sfc_main$F = {
    __name: "notice-hub",
    setup(__props, { expose: __expose }) {
      __expose();
      const types = vue.ref(listNoticeBillTypes("INBOUND"));
      function returnDesc(code) {
        if (code === "PRODUCTION_IN") {
          return "扫已审核生产汇报单 · 严格控量 · 生成入库并反写审核";
        }
        if (code === "PRODUCTION_RETURN") return "扫未审核退料单 · 核对物料 · 提交审核";
        if (code === "OUTSOURCE_RETURN") return "扫未审核委外退料单 · 核对物料 · 提交审核";
        if (code === "SALES_RETURN") return "扫未审核销售退货单 · 核对物料 · 工作流审批";
        if (code === "OTHER_IN") {
          return "扫未审核单据 · 核对物料 · 提交审核";
        }
        return "扫码 · 勾选 · 提交审核";
      }
      function openType(item) {
        if (item.code === "PRODUCTION_RETURN") {
          uni.navigateTo({ url: "/pages/picking/production-return" });
          return;
        }
        if (item.code === "OUTSOURCE_RETURN") {
          uni.navigateTo({ url: "/pages/picking/outsource-return" });
          return;
        }
        uni.navigateTo({
          url: `/pages/notice/list?billType=${encodeURIComponent(item.code)}&direction=INBOUND`
        });
      }
      onLoad(() => uni.setNavigationBarTitle({ title: "入库业务" }));
      vue.onMounted(() => {
        types.value = listNoticeBillTypes("INBOUND");
      });
      const __returned__ = { types, returnDesc, openType, ref: vue.ref, onMounted: vue.onMounted, get onLoad() {
        return onLoad;
      }, get listNoticeBillTypes() {
        return listNoticeBillTypes;
      } };
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  };
  function _sfc_render$E(_ctx, _cache, $props, $setup, $data, $options) {
    return vue.openBlock(), vue.createElementBlock("view", { class: "page" }, [
      vue.createElementVNode("view", { class: "header" }, [
        vue.createElementVNode("text", { class: "title" }, "选择入库单据类型"),
        vue.createElementVNode("text", { class: "sub" }, "所有入库均采用通知单扫码模式：列表 → 扫码 → 分批提交")
      ]),
      vue.createElementVNode("view", { class: "type-grid" }, [
        (vue.openBlock(true), vue.createElementBlock(
          vue.Fragment,
          null,
          vue.renderList($setup.types, (item) => {
            return vue.openBlock(), vue.createElementBlock("view", {
              key: item.code,
              class: "type-card",
              style: vue.normalizeStyle({ borderLeftColor: item.color }),
              onClick: ($event) => $setup.openType(item)
            }, [
              vue.createElementVNode(
                "text",
                { class: "icon" },
                vue.toDisplayString(item.icon),
                1
                /* TEXT */
              ),
              vue.createElementVNode("view", { class: "info" }, [
                vue.createElementVNode(
                  "text",
                  { class: "label" },
                  vue.toDisplayString(item.label),
                  1
                  /* TEXT */
                ),
                vue.createElementVNode(
                  "text",
                  { class: "desc" },
                  vue.toDisplayString($setup.returnDesc(item.code)),
                  1
                  /* TEXT */
                )
              ]),
              vue.createElementVNode("text", { class: "arrow" }, "›")
            ], 12, ["onClick"]);
          }),
          128
          /* KEYED_FRAGMENT */
        ))
      ])
    ]);
  }
  const PagesInboundNoticeHub = /* @__PURE__ */ _export_sfc(_sfc_main$F, [["render", _sfc_render$E], ["__scopeId", "data-v-16aecafc"], ["__file", "D:/AAA/WMS/wms-pda/src/pages/inbound/notice-hub.vue"]]);
  function useScannerInput(onSubmit) {
    const innerValue = vue.ref("");
    const focused = vue.ref(false);
    let scanTimer = null;
    let firstKeyTime = 0;
    let refocusTimer = null;
    let focusOnceTimer = null;
    let destroyed = false;
    function clearTimer() {
      if (scanTimer) {
        clearTimeout(scanTimer);
        scanTimer = null;
      }
    }
    function clearAllTimers() {
      clearTimer();
      if (refocusTimer) {
        clearTimeout(refocusTimer);
        refocusTimer = null;
      }
      if (focusOnceTimer) {
        clearTimeout(focusOnceTimer);
        focusOnceTimer = null;
      }
    }
    function focusInput() {
      if (destroyed || focused.value) return;
      if (refocusTimer) clearTimeout(refocusTimer);
      refocusTimer = setTimeout(() => {
        refocusTimer = null;
        if (!destroyed) focused.value = true;
      }, 120);
    }
    function focusInputOnce() {
      if (destroyed || focused.value) return;
      focused.value = false;
      if (focusOnceTimer) clearTimeout(focusOnceTimer);
      focusOnceTimer = setTimeout(() => {
        focusOnceTimer = null;
        if (!destroyed) focused.value = true;
      }, 80);
    }
    function resetInputState() {
      innerValue.value = "";
      firstKeyTime = 0;
      clearTimer();
    }
    function submitValue(raw) {
      if (destroyed) return;
      const code = (raw || innerValue.value || "").replace(/[\r\n\t]/g, "").trim();
      if (!code) return;
      resetInputState();
      onSubmit(code);
    }
    function shouldAutoSubmit(text, elapsedMs) {
      if (!text || text.length < 3) return false;
      const t = text.trim();
      if (t.startsWith("{") && t.includes("}") || /billno|fbillno|formid/i.test(t)) {
        return true;
      }
      if (/^https?:\/\//i.test(t) || t.includes("://")) {
        return true;
      }
      const avg = text.length > 0 ? elapsedMs / text.length : elapsedMs;
      if (avg <= 100 && text.length >= 3) return true;
      if (text.length >= 8 && elapsedMs <= 3e3) return true;
      return false;
    }
    function onInput(e) {
      var _a;
      if (destroyed) return;
      const val = ((_a = e.detail) == null ? void 0 : _a.value) ?? innerValue.value;
      innerValue.value = val;
      if (/[\r\n]/.test(val)) {
        submitValue(val.replace(/[\r\n]/g, ""));
        return;
      }
      const now = Date.now();
      if (!firstKeyTime) firstKeyTime = now;
      const elapsed = now - firstKeyTime;
      clearTimer();
      scanTimer = setTimeout(() => {
        scanTimer = null;
        if (destroyed) return;
        const text = (innerValue.value || "").trim();
        const totalElapsed = firstKeyTime ? Date.now() - firstKeyTime : 0;
        if (text && shouldAutoSubmit(text, totalElapsed || elapsed)) {
          submitValue(innerValue.value);
        }
        firstKeyTime = 0;
      }, 200);
    }
    function onConfirm() {
      if (destroyed) return;
      clearTimer();
      firstKeyTime = 0;
      submitValue(innerValue.value);
    }
    function onBlur() {
      if (destroyed) return;
      clearTimer();
      focused.value = false;
    }
    function onFocus() {
      if (destroyed) return;
      focused.value = true;
    }
    vue.onUnmounted(() => {
      destroyed = true;
      clearAllTimers();
    });
    return {
      innerValue,
      focused,
      focusInput,
      focusInputOnce,
      resetInputState,
      onInput,
      onConfirm,
      onBlur,
      onFocus,
      submitValue
    };
  }
  const _sfc_main$E = {
    __name: "ScanSearchBar",
    props: {
      modelValue: { type: String, default: "" },
      placeholder: { type: String, default: "扫码或搜索单号/供应商" },
      disabled: { type: Boolean, default: false },
      autoFocus: { type: Boolean, default: true },
      actionText: { type: String, default: "搜索" }
    },
    emits: ["update:modelValue", "scan", "search"],
    setup(__props, { expose: __expose, emit: __emit }) {
      const props = __props;
      const emit = __emit;
      let mounted = false;
      const localTimers = [];
      function safeTimeout(fn, delay) {
        const id = setTimeout(() => {
          const idx = localTimers.indexOf(id);
          if (idx >= 0) localTimers.splice(idx, 1);
          fn();
        }, delay);
        localTimers.push(id);
        return id;
      }
      function clearLocalTimers() {
        localTimers.forEach((id) => clearTimeout(id));
        localTimers.length = 0;
      }
      const {
        innerValue,
        focused,
        focusInputOnce,
        resetInputState,
        onInput,
        onBlur,
        onFocus
      } = useScannerInput((code) => {
        emit("update:modelValue", code);
        emit("scan", code);
      });
      vue.watch(
        () => props.modelValue,
        (v) => {
          if (v !== innerValue.value) innerValue.value = v || "";
        },
        { immediate: true }
      );
      vue.watch(innerValue, (v) => {
        emit("update:modelValue", v);
      });
      function handleFocus() {
        onFocus();
      }
      function handleBlur() {
        onBlur();
      }
      function focusInput() {
        if (!props.disabled) focusInputOnce();
      }
      function onConfirm() {
        const val = (innerValue.value || "").trim();
        if (!val) return;
        emit("update:modelValue", val);
        emit("scan", val);
        resetInputState();
      }
      function clear() {
        resetInputState();
        emit("update:modelValue", "");
        focusInputOnce();
      }
      vue.watch(
        () => props.disabled,
        (v, oldV) => {
          if (oldV && !v && props.autoFocus) safeTimeout(focusInputOnce, 200);
        }
      );
      vue.onMounted(() => {
        if (props.autoFocus && !mounted) {
          mounted = true;
          safeTimeout(focusInputOnce, 400);
        }
      });
      vue.onActivated(() => {
        if (props.autoFocus && !props.disabled) safeTimeout(focusInputOnce, 300);
      });
      vue.onUnmounted(() => {
        clearLocalTimers();
      });
      __expose({
        focusInput: focusInputOnce,
        clear: () => {
          resetInputState();
          emit("update:modelValue", "");
        }
      });
      const __returned__ = { props, emit, get mounted() {
        return mounted;
      }, set mounted(v) {
        mounted = v;
      }, localTimers, safeTimeout, clearLocalTimers, innerValue, focused, focusInputOnce, resetInputState, onInput, onBlur, onFocus, handleFocus, handleBlur, focusInput, onConfirm, clear, watch: vue.watch, onMounted: vue.onMounted, onActivated: vue.onActivated, onUnmounted: vue.onUnmounted, get useScannerInput() {
        return useScannerInput;
      } };
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  };
  function _sfc_render$D(_ctx, _cache, $props, $setup, $data, $options) {
    return vue.openBlock(), vue.createElementBlock(
      "view",
      {
        class: vue.normalizeClass(["scan-search", { active: $setup.focused && !$props.disabled, disabled: $props.disabled }])
      },
      [
        vue.createElementVNode("view", {
          class: "icon-wrap",
          onClick: $setup.focusInput
        }, [
          vue.createElementVNode("view", { class: "scan-frame-icon" }, [
            vue.createElementVNode("view", { class: "corner tl" }),
            vue.createElementVNode("view", { class: "corner tr" }),
            vue.createElementVNode("view", { class: "corner bl" }),
            vue.createElementVNode("view", { class: "corner br" }),
            vue.createElementVNode("view", { class: "scan-line" })
          ])
        ]),
        vue.createElementVNode("input", {
          class: "search-input",
          type: "text",
          focus: $setup.focused,
          disabled: $props.disabled,
          value: $setup.innerValue,
          placeholder: $props.placeholder,
          "confirm-type": "search",
          "hold-keyboard": false,
          "adjust-position": false,
          onInput: _cache[0] || (_cache[0] = (...args) => $setup.onInput && $setup.onInput(...args)),
          onConfirm: $setup.onConfirm,
          onBlur: $setup.handleBlur,
          onFocus: $setup.handleFocus
        }, null, 40, ["focus", "disabled", "value", "placeholder"]),
        $setup.innerValue && !$props.disabled ? (vue.openBlock(), vue.createElementBlock("text", {
          key: 0,
          class: "clear-btn",
          onClick: vue.withModifiers($setup.clear, ["stop"])
        }, "×")) : vue.createCommentVNode("v-if", true),
        vue.createElementVNode(
          "text",
          {
            class: "search-btn",
            onClick: vue.withModifiers($setup.onConfirm, ["stop"])
          },
          vue.toDisplayString($props.actionText),
          1
          /* TEXT */
        )
      ],
      2
      /* CLASS */
    );
  }
  const ScanSearchBar = /* @__PURE__ */ _export_sfc(_sfc_main$E, [["render", _sfc_render$D], ["__scopeId", "data-v-405cb19b"], ["__file", "D:/AAA/WMS/wms-pda/src/components/ScanSearchBar.vue"]]);
  function listReceiveNotices(params = {}) {
    return request({ url: "/mobile/receive-notice", data: params });
  }
  function resolveReceiveBarcode(barcodeContent) {
    return request({
      url: "/mobile/receive-notice/resolve-barcode",
      method: "POST",
      data: { barcodeContent }
    });
  }
  function getReceiveNoticeDetail(billNo) {
    return request({ url: `/mobile/receive-notice/${billNo}` });
  }
  function scanReceiveLine(billNo, barcodeContent) {
    return request({
      url: `/mobile/receive-notice/${billNo}/scan`,
      method: "POST",
      data: withDevice({ barcodeContent }),
      silent: true
    });
  }
  function toggleReceiveLine(billNo, lineNo, checked) {
    return request({
      url: `/mobile/receive-notice/${billNo}/lines/${lineNo}/check?checked=${checked ? "true" : "false"}`,
      method: "PUT"
    });
  }
  function updateReceiveLineQty(billNo, lineNo, qty, auxQty) {
    const data = { qty };
    if (auxQty != null && auxQty !== "") {
      data.auxQty = auxQty;
    }
    return request({
      url: `/mobile/receive-notice/${billNo}/lines/${lineNo}/qty`,
      method: "PUT",
      data
    });
  }
  function submitReceiveInbound(billNo, data = {}) {
    return request({
      url: `/mobile/receive-notice/${billNo}/submit`,
      method: "POST",
      data: withDevice(data),
      silent: true,
      timeout: 18e4
    });
  }
  function heartbeatReceiveNoticeLock(billNo) {
    return request({
      url: `/mobile/receive-notice/${encodeURIComponent(billNo)}/lock/heartbeat`,
      method: "POST",
      silent: true
    });
  }
  function releaseReceiveNoticeLock(billNo) {
    return request({
      url: `/mobile/receive-notice/${encodeURIComponent(billNo)}/lock/release`,
      method: "POST",
      silent: true
    });
  }
  const PAGE_SIZE = 50;
  const LIST_CACHE_TTL_MS$1 = 2e4;
  const DETAIL_CACHE_TTL_MS = 25e3;
  const SHOW_THROTTLE_MS = 2500;
  function mergeNoticeRecords(existing, incoming) {
    const map = /* @__PURE__ */ new Map();
    for (const item of existing || []) {
      const no = String((item == null ? void 0 : item.billNo) || "").trim();
      if (no) map.set(no, item);
    }
    for (const item of incoming || []) {
      const no = String((item == null ? void 0 : item.billNo) || "").trim();
      if (no) map.set(no, item);
    }
    return sortNoticeRecords(Array.from(map.values()));
  }
  function sortNoticeRecords(records) {
    return [...records || []].sort((a, b) => {
      const dateA = String((a == null ? void 0 : a.billDate) || "");
      const dateB = String((b == null ? void 0 : b.billDate) || "");
      if (dateA !== dateB) return dateB.localeCompare(dateA);
      return String((b == null ? void 0 : b.billNo) || "").localeCompare(String((a == null ? void 0 : a.billNo) || ""));
    });
  }
  function parseNoticePage(page) {
    const records = Array.isArray(page == null ? void 0 : page.records) ? page.records : Array.isArray(page) ? page : [];
    const valid = sortNoticeRecords(
      records.filter((item) => item && String(item.billNo || "").trim())
    );
    const total = Number((page == null ? void 0 : page.total) ?? valid.length);
    const current = Number((page == null ? void 0 : page.current) ?? 1);
    const size = Number((page == null ? void 0 : page.size) ?? PAGE_SIZE);
    const hasMore = valid.length > 0 && current * size < total;
    return { valid, total, current, size, hasMore };
  }
  const store = /* @__PURE__ */ new Map();
  function cacheGet(key) {
    if (!key) return null;
    const hit = store.get(key);
    if (!hit) return null;
    if (hit.expireAt <= Date.now()) {
      store.delete(key);
      return null;
    }
    return hit.value;
  }
  function cacheSet(key, value, ttlMs = 3e4) {
    if (!key) return;
    store.set(key, { value, expireAt: Date.now() + ttlMs });
    if (store.size > 80) {
      const now = Date.now();
      for (const [k, v] of store.entries()) {
        if (v.expireAt <= now) store.delete(k);
      }
    }
  }
  function cacheDel(key) {
    if (key) store.delete(key);
  }
  function cacheDelByPrefix(prefix) {
    if (!prefix) return;
    for (const k of store.keys()) {
      if (k.startsWith(prefix)) store.delete(k);
    }
  }
  const RECEIVE_LIST_CACHE_TTL_MS = 12e4;
  function useReceiveNoticeList() {
    const loading = vue.ref(false);
    const loadingMore = vue.ref(false);
    const keyword = vue.ref("");
    const notices = vue.ref([]);
    const current = vue.ref(1);
    const total = vue.ref(0);
    const hasMore = vue.ref(false);
    let lastShowAt = 0;
    function cacheKey(kw) {
      return `receive-list-page1:${String(kw || "").trim().toLowerCase()}`;
    }
    function applyPage(parsed, append) {
      total.value = parsed.total;
      current.value = parsed.current;
      hasMore.value = parsed.hasMore;
      if (append) {
        notices.value = mergeNoticeRecords(notices.value, parsed.valid);
      } else {
        notices.value = parsed.valid;
      }
    }
    async function loadList(kw = keyword.value, options = {}) {
      var _a;
      const force = options.force === true;
      keyword.value = kw;
      current.value = 1;
      const key = cacheKey(kw);
      const cached = cacheGet(key);
      if (!force && ((_a = cached == null ? void 0 : cached.records) == null ? void 0 : _a.length)) {
        notices.value = cached.records;
        total.value = cached.total || cached.records.length;
        current.value = 1;
        hasMore.value = notices.value.length < total.value;
        refreshInBackground(kw, key);
        return notices.value;
      }
      loading.value = true;
      try {
        const pageData = await listReceiveNotices({
          keyword: kw || void 0,
          current: 1,
          size: PAGE_SIZE
        });
        const parsed = parseNoticePage(pageData);
        applyPage(parsed, false);
        cacheSet(key, { records: notices.value, total: total.value }, RECEIVE_LIST_CACHE_TTL_MS);
        lastShowAt = Date.now();
        return notices.value;
      } catch (e) {
        uni.showToast({ title: (e == null ? void 0 : e.message) || "加载失败", icon: "none" });
        return notices.value;
      } finally {
        loading.value = false;
      }
    }
    function refreshInBackground(kw, key) {
      if (Date.now() - lastShowAt < SHOW_THROTTLE_MS) return;
      lastShowAt = Date.now();
      listReceiveNotices({
        keyword: kw || void 0,
        current: 1,
        size: PAGE_SIZE
      }).then((pageData) => {
        const parsed = parseNoticePage(pageData);
        if (current.value <= 1) {
          applyPage(parsed, false);
        } else {
          notices.value = mergeNoticeRecords(parsed.valid, notices.value);
          total.value = parsed.total;
          hasMore.value = notices.value.length < total.value;
        }
        cacheSet(key, { records: parsed.valid, total: parsed.total }, RECEIVE_LIST_CACHE_TTL_MS);
      }).catch(() => {
      });
    }
    async function loadMore() {
      if (loading.value || loadingMore.value || !hasMore.value) return notices.value;
      loadingMore.value = true;
      try {
        const next = current.value + 1;
        const pageData = await listReceiveNotices({
          keyword: keyword.value || void 0,
          current: next,
          size: PAGE_SIZE
        });
        const parsed = parseNoticePage(pageData);
        applyPage(parsed, true);
        return notices.value;
      } catch (e) {
        uni.showToast({ title: (e == null ? void 0 : e.message) || "加载更多失败", icon: "none" });
        return notices.value;
      } finally {
        loadingMore.value = false;
      }
    }
    async function loadListOnShow() {
      var _a;
      const key = cacheKey(keyword.value);
      const cached = cacheGet(key);
      if ((_a = cached == null ? void 0 : cached.records) == null ? void 0 : _a.length) {
        if (!notices.value.length) {
          notices.value = cached.records;
          total.value = cached.total || cached.records.length;
          current.value = 1;
          hasMore.value = notices.value.length < total.value;
        }
        if (Date.now() - lastShowAt >= SHOW_THROTTLE_MS) {
          refreshInBackground(keyword.value, key);
        }
        return notices.value;
      }
      return loadList(keyword.value);
    }
    async function searchByBarcode(barcode) {
      const raw = (barcode || "").trim();
      cacheDel(cacheKey(keyword.value));
      if (!raw) return loadList("", { force: true });
      try {
        const { billNo } = await resolveReceiveBarcode(raw);
        if (billNo) {
          keyword.value = billNo;
          return loadList(billNo, { force: true });
        }
      } catch {
      }
      keyword.value = raw;
      return loadList(raw, { force: true });
    }
    function statusLabel(item) {
      const s = item.scanStatus || item.status;
      if (s === "COMPLETED" || s === "PARTIAL_SUBMITTED") return "已完成";
      if (s === "SCANNING") return "扫码中";
      if (s === "NEW" || !s) return "待收料";
      if (item.inProgress) return "进行中";
      return "待收料";
    }
    function statusClass(item) {
      const s = item.scanStatus;
      if (s === "COMPLETED" || s === "PARTIAL_SUBMITTED") return "done";
      if (s === "SCANNING") return "progress";
      return "new";
    }
    return {
      loading,
      loadingMore,
      keyword,
      notices,
      current,
      total,
      hasMore,
      loadList,
      loadMore,
      loadListOnShow,
      searchByBarcode,
      statusLabel,
      statusClass
    };
  }
  function usePageAlive() {
    const alive = vue.ref(true);
    const timers = [];
    onUnload(() => {
      alive.value = false;
      timers.forEach((id) => clearTimeout(id));
      timers.length = 0;
    });
    function schedule(fn, delay = 300) {
      const id = setTimeout(() => {
        const idx = timers.indexOf(id);
        if (idx >= 0) timers.splice(idx, 1);
        if (!alive.value) return;
        fn();
      }, delay);
      timers.push(id);
      return id;
    }
    function refocusScanInput(scanInputRef, delay = 300) {
      schedule(() => {
        var _a, _b;
        return (_b = (_a = scanInputRef.value) == null ? void 0 : _a.focusInput) == null ? void 0 : _b.call(_a);
      }, delay);
    }
    return { alive, schedule, refocusScanInput };
  }
  function formatMaterialLineCount(item) {
    const n = Number(item == null ? void 0 : item.totalLines);
    if (Number.isFinite(n) && n > 0) {
      return `${n} 项物料`;
    }
    return "";
  }
  const _sfc_main$D = {
    __name: "receive-list",
    setup(__props, { expose: __expose }) {
      __expose();
      const scanInputRef = vue.ref(null);
      const { alive, refocusScanInput } = usePageAlive();
      const {
        loading,
        loadingMore,
        keyword,
        notices,
        total,
        hasMore,
        loadList,
        loadMore,
        loadListOnShow,
        searchByBarcode,
        statusLabel,
        statusClass
      } = useReceiveNoticeList();
      async function onScan(barcode) {
        if (!alive.value) return;
        await searchByBarcode(barcode);
        refocusScanInput(scanInputRef, 300);
      }
      function onSearch(val) {
        keyword.value = val || keyword.value;
        loadList(keyword.value, { force: true });
      }
      function openBill(item) {
        uni.navigateTo({ url: `/pages/inbound/receive-scan?billNo=${encodeURIComponent(item.billNo)}` });
      }
      async function onLoadMore() {
        if (!hasMore.value || loadingMore.value) return;
        await loadMore();
      }
      function onScrollToLower() {
        onLoadMore();
      }
      onLoad(() => uni.setNavigationBarTitle({ title: "收料通知单" }));
      onShow(() => loadListOnShow());
      vue.onMounted(() => refocusScanInput(scanInputRef, 500));
      const __returned__ = { scanInputRef, alive, refocusScanInput, loading, loadingMore, keyword, notices, total, hasMore, loadList, loadMore, loadListOnShow, searchByBarcode, statusLabel, statusClass, onScan, onSearch, openBill, onLoadMore, onScrollToLower, ref: vue.ref, onMounted: vue.onMounted, get onLoad() {
        return onLoad;
      }, get onShow() {
        return onShow;
      }, ScanSearchBar, get useReceiveNoticeList() {
        return useReceiveNoticeList;
      }, get usePageAlive() {
        return usePageAlive;
      }, get formatMaterialLineCount() {
        return formatMaterialLineCount;
      } };
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  };
  function _sfc_render$C(_ctx, _cache, $props, $setup, $data, $options) {
    return vue.openBlock(), vue.createElementBlock("view", { class: "page" }, [
      vue.createElementVNode("view", { class: "search-top" }, [
        vue.createVNode($setup["ScanSearchBar"], {
          ref: "scanInputRef",
          modelValue: $setup.keyword,
          "onUpdate:modelValue": _cache[0] || (_cache[0] = ($event) => $setup.keyword = $event),
          disabled: $setup.loading,
          placeholder: "扫码或搜索收料通知单号/供应商",
          onScan: $setup.onScan,
          onSearch: $setup.onSearch
        }, null, 8, ["modelValue", "disabled"])
      ]),
      vue.createElementVNode(
        "scroll-view",
        {
          class: "list-scroll",
          "scroll-y": "",
          "show-scrollbar": false,
          onScrolltolower: $setup.onScrollToLower
        },
        [
          (vue.openBlock(true), vue.createElementBlock(
            vue.Fragment,
            null,
            vue.renderList($setup.notices, (item, index) => {
              return vue.openBlock(), vue.createElementBlock("view", {
                key: item.billNo || "row-" + index,
                class: "bill-row",
                onClick: ($event) => $setup.openBill(item)
              }, [
                vue.createElementVNode("view", { class: "row-main" }, [
                  vue.createElementVNode(
                    "text",
                    { class: "bill-no" },
                    vue.toDisplayString(item.billNo || "（单号缺失）"),
                    1
                    /* TEXT */
                  ),
                  vue.createElementVNode(
                    "text",
                    { class: "bill-supplier" },
                    vue.toDisplayString(item.supplierName || item.supplierCode || "-"),
                    1
                    /* TEXT */
                  ),
                  vue.createElementVNode("text", { class: "bill-meta" }, [
                    $setup.formatMaterialLineCount(item) ? (vue.openBlock(), vue.createElementBlock(
                      "text",
                      {
                        key: 0,
                        class: "bill-lines"
                      },
                      vue.toDisplayString($setup.formatMaterialLineCount(item)),
                      1
                      /* TEXT */
                    )) : vue.createCommentVNode("v-if", true),
                    item.inProgress ? (vue.openBlock(), vue.createElementBlock(
                      "text",
                      { key: 1 },
                      " · 已勾 " + vue.toDisplayString(item.checkedLines || 0),
                      1
                      /* TEXT */
                    )) : vue.createCommentVNode("v-if", true),
                    item.locked && item.lockUserName ? (vue.openBlock(), vue.createElementBlock(
                      "text",
                      {
                        key: 2,
                        class: "bill-lock"
                      },
                      " · " + vue.toDisplayString(item.lockUserName) + "操作中",
                      1
                      /* TEXT */
                    )) : vue.createCommentVNode("v-if", true),
                    item.erpBillNo ? (vue.openBlock(), vue.createElementBlock(
                      "text",
                      {
                        key: 3,
                        class: "bill-erp"
                      },
                      " · 入库 " + vue.toDisplayString(item.erpBillNo),
                      1
                      /* TEXT */
                    )) : vue.createCommentVNode("v-if", true)
                  ])
                ]),
                vue.createElementVNode("view", { class: "row-side" }, [
                  vue.createElementVNode(
                    "text",
                    {
                      class: vue.normalizeClass(["status-tag", $setup.statusClass(item)])
                    },
                    vue.toDisplayString($setup.statusLabel(item)),
                    3
                    /* TEXT, CLASS */
                  ),
                  vue.createElementVNode("text", { class: "arrow" }, "›")
                ])
              ], 8, ["onClick"]);
            }),
            128
            /* KEYED_FRAGMENT */
          )),
          !$setup.notices.length && !$setup.loading ? (vue.openBlock(), vue.createElementBlock("view", {
            key: 0,
            class: "empty"
          }, [
            vue.createElementVNode("text", { class: "empty-icon" }, "📋"),
            vue.createElementVNode("text", { class: "empty-text" }, "暂无已审核的收料通知单")
          ])) : vue.createCommentVNode("v-if", true),
          $setup.loading && !$setup.notices.length ? (vue.openBlock(), vue.createElementBlock("view", {
            key: 1,
            class: "loading-tip"
          }, "加载中...")) : vue.createCommentVNode("v-if", true),
          $setup.notices.length ? (vue.openBlock(), vue.createElementBlock("view", {
            key: 2,
            class: "footer-tip"
          }, [
            vue.createElementVNode(
              "text",
              null,
              "已显示 " + vue.toDisplayString($setup.notices.length) + " / " + vue.toDisplayString($setup.total) + " 条",
              1
              /* TEXT */
            ),
            $setup.hasMore ? (vue.openBlock(), vue.createElementBlock(
              "view",
              {
                key: 0,
                class: "load-more-btn",
                onClick: $setup.onLoadMore
              },
              vue.toDisplayString($setup.loadingMore ? "加载中..." : "加载更多"),
              1
              /* TEXT */
            )) : (vue.openBlock(), vue.createElementBlock("text", {
              key: 1,
              class: "end-tip"
            }, "没有更多了"))
          ])) : vue.createCommentVNode("v-if", true)
        ],
        32
        /* NEED_HYDRATION */
      )
    ]);
  }
  const PagesInboundReceiveList = /* @__PURE__ */ _export_sfc(_sfc_main$D, [["render", _sfc_render$C], ["__scopeId", "data-v-2d7ea4ce"], ["__file", "D:/AAA/WMS/wms-pda/src/pages/inbound/receive-list.vue"]]);
  const _sfc_main$C = {
    __name: "CompactScanBox",
    props: {
      disabled: { type: Boolean, default: false },
      autoFocus: { type: Boolean, default: true }
    },
    emits: ["scan"],
    setup(__props, { expose: __expose, emit: __emit }) {
      const props = __props;
      const emit = __emit;
      let mounted = false;
      const localTimers = [];
      function safeTimeout(fn, delay) {
        const id = setTimeout(() => {
          const idx = localTimers.indexOf(id);
          if (idx >= 0) localTimers.splice(idx, 1);
          fn();
        }, delay);
        localTimers.push(id);
        return id;
      }
      function clearLocalTimers() {
        localTimers.forEach((id) => clearTimeout(id));
        localTimers.length = 0;
      }
      const {
        innerValue,
        focused,
        focusInputOnce,
        resetInputState,
        onInput,
        onConfirm,
        onBlur,
        onFocus
      } = useScannerInput((code) => {
        emit("scan", code);
      });
      function handleFocus() {
        onFocus();
      }
      function handleBlur() {
        onBlur();
      }
      function onTap() {
        if (!props.disabled) focusInputOnce();
      }
      vue.watch(
        () => props.disabled,
        (v, oldV) => {
          if (oldV && !v && props.autoFocus) {
            safeTimeout(focusInputOnce, 200);
          }
        }
      );
      vue.onMounted(() => {
        if (props.autoFocus && !mounted) {
          mounted = true;
          safeTimeout(focusInputOnce, 400);
        }
      });
      vue.onActivated(() => {
        if (props.autoFocus && !props.disabled) {
          safeTimeout(focusInputOnce, 300);
        }
      });
      vue.onUnmounted(() => {
        clearLocalTimers();
      });
      __expose({
        focusInput: focusInputOnce,
        clear: resetInputState
      });
      const __returned__ = { props, emit, get mounted() {
        return mounted;
      }, set mounted(v) {
        mounted = v;
      }, localTimers, safeTimeout, clearLocalTimers, innerValue, focused, focusInputOnce, resetInputState, onInput, onConfirm, onBlur, onFocus, handleFocus, handleBlur, onTap, watch: vue.watch, onMounted: vue.onMounted, onActivated: vue.onActivated, onUnmounted: vue.onUnmounted, get useScannerInput() {
        return useScannerInput;
      } };
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  };
  function _sfc_render$B(_ctx, _cache, $props, $setup, $data, $options) {
    return vue.openBlock(), vue.createElementBlock(
      "view",
      {
        class: vue.normalizeClass(["compact-scan", { active: $setup.focused && !$props.disabled, disabled: $props.disabled }]),
        onClick: $setup.onTap
      },
      [
        vue.createElementVNode("view", { class: "scan-frame-icon" }, [
          vue.createElementVNode("view", { class: "corner tl" }),
          vue.createElementVNode("view", { class: "corner tr" }),
          vue.createElementVNode("view", { class: "corner bl" }),
          vue.createElementVNode("view", { class: "corner br" }),
          vue.createElementVNode("view", { class: "scan-line" })
        ]),
        vue.createElementVNode("input", {
          class: "scan-input",
          type: "text",
          focus: $setup.focused,
          disabled: $props.disabled,
          value: $setup.innerValue,
          placeholder: "",
          "confirm-type": "done",
          "hold-keyboard": false,
          "adjust-position": false,
          "cursor-spacing": 0,
          onInput: _cache[0] || (_cache[0] = (...args) => $setup.onInput && $setup.onInput(...args)),
          onConfirm: _cache[1] || (_cache[1] = (...args) => $setup.onConfirm && $setup.onConfirm(...args)),
          onBlur: $setup.handleBlur,
          onFocus: $setup.handleFocus
        }, null, 40, ["focus", "disabled", "value"])
      ],
      2
      /* CLASS */
    );
  }
  const CompactScanBox = /* @__PURE__ */ _export_sfc(_sfc_main$C, [["render", _sfc_render$B], ["__scopeId", "data-v-67ede7a2"], ["__file", "D:/AAA/WMS/wms-pda/src/components/CompactScanBox.vue"]]);
  function listWarehouses(params = {}) {
    return request({
      url: "/base/warehouses",
      data: {
        status: 1,
        current: 1,
        size: 200,
        ...params
      }
    });
  }
  function useWarehousePicker(props, emit) {
    const mode = vue.ref("auto");
    const loading = vue.ref(false);
    const warehouseOptions = vue.ref([]);
    const manualIndex = vue.ref(-1);
    const recommended = vue.reactive({
      warehouseCode: "",
      erpWarehouseCode: "",
      warehouseName: "",
      label: ""
    });
    function formatLabel(item) {
      if (!item) return "";
      const erp = item.erpWarehouseCode ? ` / ${item.erpWarehouseCode}` : "";
      return `${item.warehouseCode}${erp} · ${item.warehouseName || item.warehouseCode}`;
    }
    function applySuggest(code, name) {
      const value = (code || "").trim();
      recommended.warehouseCode = value;
      recommended.erpWarehouseCode = value;
      recommended.warehouseName = name || value;
      recommended.label = value ? `${value}${name ? ` · ${name}` : ""}` : "-";
      emitChange();
    }
    async function loadWarehouseList() {
      loading.value = true;
      try {
        const page = await listWarehouses();
        const records = (page == null ? void 0 : page.records) || (page == null ? void 0 : page.list) || [];
        warehouseOptions.value = records.map((item) => ({
          warehouseCode: item.warehouseCode,
          erpWarehouseCode: item.erpWarehouseCode || item.warehouseCode,
          warehouseName: item.warehouseName || item.warehouseCode,
          label: formatLabel(item)
        }));
        if (mode.value === "manual" && manualIndex.value < 0 && warehouseOptions.value.length) {
          const suggest = (props.suggestCode || "").trim();
          const idx = warehouseOptions.value.findIndex(
            (w) => w.warehouseCode === suggest || w.erpWarehouseCode === suggest
          );
          manualIndex.value = idx >= 0 ? idx : 0;
          const picked = warehouseOptions.value[manualIndex.value];
          recommended.warehouseCode = picked.warehouseCode;
          recommended.erpWarehouseCode = picked.erpWarehouseCode;
          recommended.warehouseName = picked.warehouseName;
          recommended.label = picked.label;
          emitChange();
        }
      } catch {
        warehouseOptions.value = [];
      } finally {
        loading.value = false;
      }
    }
    function setMode(next) {
      mode.value = next;
      if (next === "auto") {
        applySuggest(props.suggestCode, props.suggestName);
      } else if (!warehouseOptions.value.length) {
        loadWarehouseList();
      } else if (manualIndex.value >= 0) {
        const picked = warehouseOptions.value[manualIndex.value];
        recommended.warehouseCode = picked.warehouseCode;
        recommended.erpWarehouseCode = picked.erpWarehouseCode;
        recommended.warehouseName = picked.warehouseName;
        recommended.label = picked.label;
        emitChange();
      }
    }
    function onPickerChange(e) {
      const idx = Number(e.detail.value);
      manualIndex.value = idx;
      const picked = warehouseOptions.value[idx];
      if (!picked) return;
      recommended.warehouseCode = picked.warehouseCode;
      recommended.erpWarehouseCode = picked.erpWarehouseCode;
      recommended.warehouseName = picked.warehouseName;
      recommended.label = picked.label;
      emitChange();
    }
    function emitChange() {
      emit == null ? void 0 : emit("change", getPayload());
    }
    function getPayload() {
      if (mode.value === "auto") {
        return { autoAssignWarehouse: true };
      }
      return {
        autoAssignWarehouse: false,
        warehouseCode: recommended.warehouseCode,
        erpWarehouseCode: recommended.erpWarehouseCode
      };
    }
    vue.watch(
      () => [props.suggestCode, props.suggestName],
      () => {
        if (mode.value === "auto") {
          applySuggest(props.suggestCode, props.suggestName);
        }
      },
      { immediate: true }
    );
    vue.onMounted(() => {
      if (mode.value === "manual") {
        loadWarehouseList();
      }
    });
    return {
      mode,
      loading,
      warehouseOptions,
      manualIndex,
      recommended,
      setMode,
      loadWarehouseList,
      onPickerChange,
      getPayload
    };
  }
  const _sfc_main$B = {
    __name: "WarehousePicker",
    props: {
      suggestCode: { type: String, default: "" },
      suggestName: { type: String, default: "" },
      theme: { type: String, default: "light" }
    },
    emits: ["change"],
    setup(__props, { expose: __expose, emit: __emit }) {
      const props = __props;
      const emit = __emit;
      const {
        mode,
        loading,
        warehouseOptions,
        manualIndex,
        recommended,
        setMode,
        loadWarehouseList,
        onPickerChange,
        getPayload
      } = useWarehousePicker(props, emit);
      __expose({ getPayload, setMode });
      const __returned__ = { props, emit, mode, loading, warehouseOptions, manualIndex, recommended, setMode, loadWarehouseList, onPickerChange, getPayload, get useWarehousePicker() {
        return useWarehousePicker;
      } };
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  };
  function _sfc_render$A(_ctx, _cache, $props, $setup, $data, $options) {
    return vue.openBlock(), vue.createElementBlock(
      "view",
      {
        class: vue.normalizeClass(["warehouse-picker", $props.theme])
      },
      [
        vue.createElementVNode("view", { class: "mode-tabs" }, [
          vue.createElementVNode(
            "text",
            {
              class: vue.normalizeClass(["tab", $setup.mode === "auto" && "active"]),
              onClick: _cache[0] || (_cache[0] = ($event) => $setup.setMode("auto"))
            },
            "自动分配",
            2
            /* CLASS */
          ),
          vue.createElementVNode(
            "text",
            {
              class: vue.normalizeClass(["tab", $setup.mode === "manual" && "active"]),
              onClick: _cache[1] || (_cache[1] = ($event) => $setup.setMode("manual"))
            },
            "手动选仓",
            2
            /* CLASS */
          )
        ]),
        $setup.mode === "auto" ? (vue.openBlock(), vue.createElementBlock("view", {
          key: 0,
          class: "auto-panel"
        }, [
          vue.createElementVNode("view", { class: "wh-display" }, [
            vue.createElementVNode("text", { class: "wh-label" }, "物料仓库"),
            vue.createElementVNode(
              "text",
              { class: "wh-code" },
              vue.toDisplayString($setup.recommended.label || "-"),
              1
              /* TEXT */
            ),
            vue.createElementVNode("text", { class: "wh-hint" }, "按收料分录物料仓库自动分配")
          ])
        ])) : (vue.openBlock(), vue.createElementBlock("view", {
          key: 1,
          class: "manual-panel"
        }, [
          $setup.warehouseOptions.length ? (vue.openBlock(), vue.createElementBlock("picker", {
            key: 0,
            value: $setup.manualIndex < 0 ? 0 : $setup.manualIndex,
            range: $setup.warehouseOptions,
            "range-key": "label",
            onChange: _cache[2] || (_cache[2] = (...args) => $setup.onPickerChange && $setup.onPickerChange(...args))
          }, [
            vue.createElementVNode(
              "view",
              { class: "picker-value" },
              vue.toDisplayString($setup.recommended.label || "选择仓库") + " ▾",
              1
              /* TEXT */
            )
          ], 40, ["value", "range"])) : (vue.openBlock(), vue.createElementBlock(
            "text",
            {
              key: 1,
              class: "wh-empty"
            },
            vue.toDisplayString($setup.loading ? "加载仓库..." : "暂无可用仓库"),
            1
            /* TEXT */
          )),
          vue.createElementVNode("button", {
            size: "mini",
            class: "refresh-btn",
            loading: $setup.loading,
            onClick: _cache[3] || (_cache[3] = (...args) => $setup.loadWarehouseList && $setup.loadWarehouseList(...args))
          }, "刷新", 8, ["loading"])
        ]))
      ],
      2
      /* CLASS */
    );
  }
  const WarehousePicker = /* @__PURE__ */ _export_sfc(_sfc_main$B, [["render", _sfc_render$A], ["__scopeId", "data-v-4644220b"], ["__file", "D:/AAA/WMS/wms-pda/src/components/WarehousePicker.vue"]]);
  function qs(params) {
    return Object.entries(params).filter(([, v]) => v != null && v !== "").map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`).join("&");
  }
  function allocateLocation(data) {
    return request({
      url: "/mobile/locations/allocate",
      method: "POST",
      data: {
        warehouseCode: data.warehouseCode,
        materialCode: data.materialCode,
        batchNo: data.batchNo,
        autoAllocate: data.autoAllocate !== false,
        manualLocationCode: data.manualLocationCode || data.locationCode
      }
    });
  }
  function listLocations(warehouseCode) {
    return request({
      url: `/mobile/locations?${qs({ warehouseCode })}`,
      method: "GET"
    });
  }
  function parseBarcodeLocal(content) {
    const raw = (content || "").trim();
    const result = {
      raw,
      materialCode: "",
      batchNo: "",
      locationCode: "",
      barcodeContent: raw
    };
    if (!raw) return result;
    if (/^WH\d{2}/i.test(raw)) {
      result.locationCode = raw;
      return result;
    }
    if (raw.includes("|")) {
      const [materialCode, batchNo] = raw.split("|");
      result.materialCode = materialCode.trim();
      result.batchNo = (batchNo || "").trim();
      return result;
    }
    if (raw.length >= 11) {
      result.materialCode = raw.substring(0, 11);
      result.batchNo = raw.substring(11);
    } else {
      result.materialCode = raw;
    }
    return result;
  }
  async function resolveBarcode(content) {
    const local = parseBarcodeLocal(content);
    if (!content) return local;
    try {
      const data = await parseMobileBarcode(content);
      const segments = data.segments || {};
      return {
        raw: content,
        barcodeContent: content,
        materialCode: segments.materialCode || local.materialCode,
        batchNo: segments.batchNo || local.batchNo,
        locationCode: segments.locationCode || local.locationCode,
        segments
      };
    } catch {
      return local;
    }
  }
  const STRATEGY_LABEL = {
    CONSOLIDATE: "同物料归位",
    EMPTY: "空库位",
    DEFAULT: "默认库位",
    MANUAL: "自选库位",
    WAREHOUSE_ONLY: "仅仓库，无库位"
  };
  function useLocationPicker(props, emit) {
    const mode = vue.ref("none");
    const loading = vue.ref(false);
    const manualCode = vue.ref("");
    const manualLabel = vue.ref("");
    const locationOptions = vue.ref([]);
    const recommended = vue.reactive({
      locationCode: "",
      locationName: "",
      message: "",
      strategy: ""
    });
    async function fetchAllocate() {
      if (mode.value === "none") {
        recommended.locationCode = "";
        recommended.message = "仅仓库入库，不指定库位";
        recommended.strategy = "NONE";
        emitChange();
        return null;
      }
      if (!props.warehouseCode) {
        recommended.locationCode = "";
        recommended.message = "请先选择仓库";
        emitChange();
        return null;
      }
      loading.value = true;
      try {
        const data = await allocateLocation({
          warehouseCode: props.warehouseCode,
          materialCode: props.materialCode || void 0,
          batchNo: props.batchNo || void 0,
          autoAllocate: mode.value === "auto",
          manualLocationCode: mode.value === "manual" ? manualCode.value || void 0 : void 0
        });
        recommended.locationCode = data.locationCode || "";
        recommended.message = data.message || STRATEGY_LABEL[data.strategy] || "";
        recommended.strategy = data.strategy || "";
        if (mode.value === "manual" && !manualCode.value && recommended.locationCode) {
        }
        emitChange();
        return data;
      } catch {
        recommended.locationCode = "";
        recommended.message = mode.value === "auto" ? "未分配到库位，可仅按仓库入库" : "库位校验失败";
        emitChange();
        return null;
      } finally {
        loading.value = false;
      }
    }
    async function loadLocationList() {
      if (!props.warehouseCode) {
        locationOptions.value = [];
        return;
      }
      try {
        const list = await listLocations(props.warehouseCode);
        locationOptions.value = (list || []).map((loc) => ({
          code: loc.locationCode,
          label: `${loc.locationCode}${loc.locationName ? " · " + loc.locationName : ""}`
        }));
      } catch {
        locationOptions.value = [];
      }
    }
    function setMode(m) {
      mode.value = m;
      if (m === "none") {
        manualCode.value = "";
        manualLabel.value = "";
        recommended.locationCode = "";
        recommended.message = "仅仓库入库，不指定库位";
        recommended.strategy = "NONE";
        emitChange();
        return;
      }
      if (m === "auto") {
        fetchAllocate();
        return;
      }
      emitChange();
      loadLocationList();
    }
    function onPickerChange(e) {
      const idx = Number(e.detail.value);
      const picked = locationOptions.value[idx];
      if (picked) {
        manualCode.value = picked.code;
        manualLabel.value = picked.label;
        recommended.locationCode = picked.code;
        recommended.message = STRATEGY_LABEL.MANUAL;
        recommended.strategy = "MANUAL";
        emitChange();
      }
    }
    function clearManual() {
      manualCode.value = "";
      manualLabel.value = "";
      recommended.locationCode = "";
      recommended.message = "未选库位，仅按仓库入库";
      recommended.strategy = "MANUAL_EMPTY";
      emitChange();
    }
    function onManualInput() {
      manualLabel.value = manualCode.value;
      const code = (manualCode.value || "").trim();
      if (!code) {
        clearManual();
        return;
      }
      recommended.locationCode = code;
      recommended.message = STRATEGY_LABEL.MANUAL;
      recommended.strategy = "MANUAL";
      emitChange();
    }
    function scanLocation() {
      uni.scanCode({
        success: (res) => {
          const parsed = parseBarcodeLocal(res.result || "");
          const code = parsed.locationCode || (res.result || "").trim();
          if (code) {
            manualCode.value = code;
            manualLabel.value = code;
            mode.value = "manual";
            recommended.locationCode = code;
            recommended.message = STRATEGY_LABEL.MANUAL;
            recommended.strategy = "MANUAL";
            emitChange();
          }
        },
        fail: () => uni.showToast({ title: "扫码取消", icon: "none" })
      });
    }
    function emitChange() {
      emit("change", getPayload());
    }
    function getPayload() {
      if (mode.value === "none") {
        return {
          autoAllocateLocation: false,
          locationCode: void 0,
          targetLocation: void 0
        };
      }
      if (mode.value === "auto") {
        const code2 = (recommended.locationCode || "").trim();
        return {
          autoAllocateLocation: true,
          locationCode: code2 || void 0,
          targetLocation: code2 || void 0
        };
      }
      const code = (manualCode.value || recommended.locationCode || "").trim();
      return {
        autoAllocateLocation: false,
        locationCode: code || void 0,
        targetLocation: code || void 0
      };
    }
    vue.watch(
      () => [props.warehouseCode, props.materialCode, props.batchNo],
      () => {
        loadLocationList();
        if (mode.value === "auto") fetchAllocate();
      }
    );
    vue.onMounted(() => {
      loadLocationList();
      if (mode.value === "auto") fetchAllocate();
    });
    return {
      mode,
      loading,
      manualCode,
      manualLabel,
      locationOptions,
      recommended,
      STRATEGY_LABEL,
      setMode,
      fetchAllocate,
      onPickerChange,
      onManualInput,
      clearManual,
      scanLocation,
      getPayload
    };
  }
  const _sfc_main$A = {
    __name: "LocationPicker",
    props: {
      warehouseCode: { type: String, default: "" },
      materialCode: { type: String, default: "" },
      batchNo: { type: String, default: "" },
      theme: { type: String, default: "light" }
    },
    emits: ["change"],
    setup(__props, { expose: __expose, emit: __emit }) {
      const props = __props;
      const emit = __emit;
      const {
        mode,
        loading,
        manualCode,
        manualLabel,
        locationOptions,
        recommended,
        setMode,
        fetchAllocate,
        onPickerChange,
        onManualInput,
        clearManual,
        scanLocation,
        getPayload
      } = useLocationPicker(props, emit);
      __expose({ getPayload, refresh: fetchAllocate, setMode });
      const __returned__ = { props, emit, mode, loading, manualCode, manualLabel, locationOptions, recommended, setMode, fetchAllocate, onPickerChange, onManualInput, clearManual, scanLocation, getPayload, get useLocationPicker() {
        return useLocationPicker;
      } };
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  };
  function _sfc_render$z(_ctx, _cache, $props, $setup, $data, $options) {
    return vue.openBlock(), vue.createElementBlock(
      "view",
      {
        class: vue.normalizeClass(["location-picker", $props.theme])
      },
      [
        vue.createElementVNode("view", { class: "mode-tabs" }, [
          vue.createElementVNode(
            "text",
            {
              class: vue.normalizeClass(["tab", $setup.mode === "none" && "active"]),
              onClick: _cache[0] || (_cache[0] = ($event) => $setup.setMode("none"))
            },
            "不选库位",
            2
            /* CLASS */
          ),
          vue.createElementVNode(
            "text",
            {
              class: vue.normalizeClass(["tab", $setup.mode === "auto" && "active"]),
              onClick: _cache[1] || (_cache[1] = ($event) => $setup.setMode("auto"))
            },
            "自动分配",
            2
            /* CLASS */
          ),
          vue.createElementVNode(
            "text",
            {
              class: vue.normalizeClass(["tab", $setup.mode === "manual" && "active"]),
              onClick: _cache[2] || (_cache[2] = ($event) => $setup.setMode("manual"))
            },
            "自选库位",
            2
            /* CLASS */
          )
        ]),
        $setup.mode === "none" ? (vue.openBlock(), vue.createElementBlock("view", {
          key: 0,
          class: "none-panel"
        }, [
          vue.createElementVNode("view", { class: "loc-display" }, [
            vue.createElementVNode("text", { class: "loc-label" }, "库位"),
            vue.createElementVNode("text", { class: "loc-code" }, "不指定"),
            vue.createElementVNode("text", { class: "loc-hint" }, "仅按仓库入库，库位可留空")
          ])
        ])) : $setup.mode === "auto" ? (vue.openBlock(), vue.createElementBlock("view", {
          key: 1,
          class: "auto-panel"
        }, [
          vue.createElementVNode("view", { class: "loc-display" }, [
            vue.createElementVNode("text", { class: "loc-label" }, "分配库位"),
            vue.createElementVNode(
              "text",
              { class: "loc-code" },
              vue.toDisplayString($setup.recommended.locationCode || ($setup.loading ? "分配中..." : "无（仅仓库）")),
              1
              /* TEXT */
            ),
            $setup.recommended.message ? (vue.openBlock(), vue.createElementBlock(
              "text",
              {
                key: 0,
                class: "loc-hint"
              },
              vue.toDisplayString($setup.recommended.message),
              1
              /* TEXT */
            )) : vue.createCommentVNode("v-if", true)
          ]),
          vue.createElementVNode("button", {
            size: "mini",
            class: "refresh-btn",
            loading: $setup.loading,
            onClick: _cache[3] || (_cache[3] = (...args) => $setup.fetchAllocate && $setup.fetchAllocate(...args))
          }, "刷新", 8, ["loading"])
        ])) : (vue.openBlock(), vue.createElementBlock("view", {
          key: 2,
          class: "manual-panel"
        }, [
          $setup.locationOptions.length ? (vue.openBlock(), vue.createElementBlock("picker", {
            key: 0,
            range: $setup.locationOptions,
            "range-key": "label",
            onChange: _cache[4] || (_cache[4] = (...args) => $setup.onPickerChange && $setup.onPickerChange(...args))
          }, [
            vue.createElementVNode(
              "view",
              { class: "picker-value" },
              vue.toDisplayString($setup.manualLabel || "选择库位（可选）") + " ▾",
              1
              /* TEXT */
            )
          ], 40, ["range"])) : (vue.openBlock(), vue.createElementBlock(
            "text",
            {
              key: 1,
              class: "loc-empty"
            },
            vue.toDisplayString($props.warehouseCode ? "该仓库暂无库位，可不选" : "请先选择仓库"),
            1
            /* TEXT */
          )),
          vue.withDirectives(vue.createElementVNode(
            "input",
            {
              "onUpdate:modelValue": _cache[5] || (_cache[5] = ($event) => $setup.manualCode = $event),
              class: "manual-input",
              placeholder: "或输入/扫描库位码（可留空）",
              onBlur: _cache[6] || (_cache[6] = (...args) => $setup.onManualInput && $setup.onManualInput(...args))
            },
            null,
            544
            /* NEED_HYDRATION, NEED_PATCH */
          ), [
            [vue.vModelText, $setup.manualCode]
          ]),
          vue.createElementVNode("view", { class: "manual-actions" }, [
            vue.createElementVNode("button", {
              size: "mini",
              class: "scan-loc-btn",
              onClick: _cache[7] || (_cache[7] = (...args) => $setup.scanLocation && $setup.scanLocation(...args))
            }, "扫库位"),
            $setup.manualCode ? (vue.openBlock(), vue.createElementBlock("button", {
              key: 0,
              size: "mini",
              class: "clear-btn",
              onClick: _cache[8] || (_cache[8] = (...args) => $setup.clearManual && $setup.clearManual(...args))
            }, "清空")) : vue.createCommentVNode("v-if", true)
          ]),
          $setup.recommended.locationCode ? (vue.openBlock(), vue.createElementBlock(
            "text",
            {
              key: 2,
              class: "loc-confirm"
            },
            "已选: " + vue.toDisplayString($setup.recommended.locationCode),
            1
            /* TEXT */
          )) : (vue.openBlock(), vue.createElementBlock("text", {
            key: 3,
            class: "loc-hint"
          }, "未选库位时仅按仓库入库"))
        ]))
      ],
      2
      /* CLASS */
    );
  }
  const LocationPicker = /* @__PURE__ */ _export_sfc(_sfc_main$A, [["render", _sfc_render$z], ["__scopeId", "data-v-ee8a9dbb"], ["__file", "D:/AAA/WMS/wms-pda/src/components/LocationPicker.vue"]]);
  function isNoticeBillCompleted(detail) {
    if (!detail) return false;
    const s = detail.scanStatus;
    if (s === "COMPLETED" || s === "PARTIAL_SUBMITTED") return true;
    const rows = detail.lines || [];
    if (!rows.length) return false;
    const hasSubmitted = rows.some((line) => {
      return (Number(line.submittedQty) || 0) > 0 || (Number(line.submittedAuxQty) || 0) > 0;
    });
    if (hasSubmitted) return true;
    return rows.every((line) => {
      const remainQty = Number(line.remainQty);
      const qtyDone = Number.isFinite(remainQty) ? remainQty <= 0 : (Number(line.planQty) || 0) - (Number(line.submittedQty) || 0) <= 0;
      if (!line.multiUnit) return qtyDone;
      const remainAux = Number(line.remainAuxQty);
      const auxDone = Number.isFinite(remainAux) ? remainAux <= 0 : (Number(line.planAuxQty) || 0) - (Number(line.submittedAuxQty) || 0) <= 0;
      return qtyDone && auxDone;
    });
  }
  function isLabelScanned(line) {
    if (!line) return false;
    const barcode = line.scannedBarcode;
    if (barcode != null && String(barcode).trim() !== "") return true;
    if (line.labelScanned === true) return true;
    return false;
  }
  function isBillLockedError(e) {
    var _a;
    return (((_a = e == null ? void 0 : e.data) == null ? void 0 : _a.errorType) || (e == null ? void 0 : e.errorType)) === "BILL_LOCKED";
  }
  function useBillExclusiveLock({ heartbeat, release, intervalMs = 6e4 } = {}) {
    let timer = null;
    let active = false;
    let releasing = false;
    function stopTimer() {
      if (timer != null) {
        clearInterval(timer);
        timer = null;
      }
    }
    async function ping() {
      if (!active || typeof heartbeat !== "function") return true;
      try {
        await heartbeat();
        return true;
      } catch (e) {
        if (isBillLockedError(e)) {
          handleLost(e);
        }
        return false;
      }
    }
    function handleLost(e) {
      var _a;
      stop();
      const msg = (e == null ? void 0 : e.message) || ((_a = e == null ? void 0 : e.data) == null ? void 0 : _a.message) || "单据正被其他人操作";
      uni.showToast({ title: msg, icon: "none", duration: 2500 });
      setTimeout(() => {
        uni.navigateBack({ fail: () => {
        } });
      }, 400);
    }
    function start() {
      active = true;
      stopTimer();
      timer = setInterval(() => {
        ping();
      }, intervalMs);
    }
    function stop() {
      active = false;
      stopTimer();
    }
    async function releaseLock() {
      if (releasing) return;
      releasing = true;
      stop();
      try {
        if (typeof release === "function") {
          await release();
        }
      } catch {
      } finally {
        releasing = false;
      }
    }
    onShow(() => {
      if (active) {
        ping();
      }
    });
    onHide(() => {
    });
    onUnload(() => {
      releaseLock();
    });
    return {
      start,
      stop,
      releaseLock,
      ping,
      isBillLockedError
    };
  }
  function isWeightUnit(unitCode) {
    if (unitCode == null || unitCode === "") return false;
    const raw = String(unitCode).trim();
    const u = raw.toUpperCase();
    return u === "KG" || u === "KGS" || u === "KILOGRAM" || raw === "千克" || raw === "公斤" || u === "G" || raw === "克" || u === "T" || raw === "吨" || raw === "斤";
  }
  function qtyDecimalScale(unitCode) {
    return isWeightUnit(unitCode) ? 6 : 4;
  }
  function isWholeQty(n) {
    if (!Number.isFinite(n)) return false;
    const rounded = Number(n.toFixed(6));
    return Number.isInteger(rounded);
  }
  function formatPriceUnitQty(n) {
    if (isWholeQty(n)) return String(Math.round(Number(n.toFixed(6))));
    return n.toFixed(4).replace(/\.?0+$/, "");
  }
  function formatQty(v, unitCode) {
    const n = Number(v);
    if (Number.isNaN(n)) return "0";
    if (isWeightUnit(unitCode)) {
      if (isWholeQty(n)) return formatPriceUnitQty(n);
      return n.toFixed(2);
    }
    return formatPriceUnitQty(n);
  }
  function formatQtyInput(v, unitCode) {
    const n = Number(v);
    if (Number.isNaN(n)) return "0";
    if (isWeightUnit(unitCode)) {
      if (isWholeQty(n)) return formatPriceUnitQty(n);
      return n.toFixed(6);
    }
    return formatPriceUnitQty(n);
  }
  function truncate(text, max = 480) {
    const s = String(text || "").trim();
    if (!s) return "";
    return s.length > max ? `${s.slice(0, max)}…` : s;
  }
  function showModalAsync({ title, content, confirmText = "知道了" }) {
    return new Promise((resolve) => {
      uni.showModal({
        title: title || "提示",
        content: truncate(content) || "无详细信息",
        showCancel: false,
        confirmText,
        success: () => resolve(true),
        fail: () => resolve(false)
      });
    });
  }
  function resolveErpSyncOutcome(result) {
    const status = String((result == null ? void 0 : result.erpSyncStatus) || "").toUpperCase();
    const async = (result == null ? void 0 : result.erpSyncAsync) === true || (result == null ? void 0 : result.erpSyncAsync) === "true";
    const qtyUnchanged = (result == null ? void 0 : result.qtyUnchanged) === true || (result == null ? void 0 : result.qtyUnchanged) === "true";
    let erpMsg = (result == null ? void 0 : result.erpSyncMessage) || (result == null ? void 0 : result.message) || "";
    const billNo = (result == null ? void 0 : result.erpBillNo) ? String(result.erpBillNo).trim() : "";
    if (status === "FAILED" || status === "ERROR" || status === "PARTIAL") {
      if (qtyUnchanged && erpMsg && !erpMsg.includes("已处理")) {
        erpMsg = `${erpMsg}
已处理/可处理数量未变更，可修改后重新提交。`;
      }
      return {
        ok: false,
        pending: false,
        title: "金蝶同步失败",
        message: erpMsg || "金蝶同步失败，已处理/可处理数量未变更，请核对后重试"
      };
    }
    if (async || status === "PENDING" && (result == null ? void 0 : result.batchNo) && String(erpMsg).includes("后台同步")) {
      return {
        ok: false,
        pending: true,
        title: "金蝶同步中",
        message: erpMsg || "已写入 WMS，金蝶仍在后台同步。请留在本页稍后重新进入明细查看，或到 Web 批次详情确认结果。"
      };
    }
    if (status === "SUCCESS" || !status || status === "PENDING") {
      let message = erpMsg;
      if (billNo) {
        message = message && !message.includes(billNo) ? `${message}
金蝶单号：${billNo}` : message || `金蝶同步成功，单号 ${billNo}`;
      }
      if (!message) {
        message = (result == null ? void 0 : result.lineCount) != null ? `提交成功，共 ${result.lineCount} 行` : "提交成功";
      }
      return {
        ok: true,
        pending: false,
        title: billNo ? "金蝶同步成功" : "提交成功",
        message
      };
    }
    return {
      ok: false,
      pending: false,
      title: "金蝶同步失败",
      message: erpMsg || `金蝶状态异常：${status}`
    };
  }
  function formatErpSubmitError(e) {
    var _a, _b;
    const type = ((_a = e == null ? void 0 : e.data) == null ? void 0 : _a.errorType) || (e == null ? void 0 : e.errorType);
    const msg = (e == null ? void 0 : e.message) || ((_b = e == null ? void 0 : e.data) == null ? void 0 : _b.message);
    if (type === "ERP_SYNC_FAILED" || type === "ERP_IN_STOCK_QTY_EXCEEDED") {
      return msg || "金蝶同步失败，数量未变更";
    }
    return msg || "提交失败";
  }
  async function alertErpSubmitFailed(message, title = "金蝶同步失败") {
    await showModalAsync({
      title,
      content: message || "金蝶同步失败，请核对后重试（单据未退出）"
    });
  }
  async function handleErpSubmitResult(result) {
    const outcome = resolveErpSyncOutcome(result);
    if (!outcome.ok) {
      await showModalAsync({
        title: outcome.title,
        content: outcome.message
      });
      return { ok: false, pending: outcome.pending, result };
    }
    await showModalAsync({
      title: outcome.title,
      content: outcome.message
    });
    return { ok: true, pending: false, result };
  }
  function useReceiveNoticeScan(billNo) {
    const loading = vue.ref(false);
    const submitting = vue.ref(false);
    const detail = vue.ref(null);
    const lines = vue.ref([]);
    const lastHighlightLineNo = vue.ref(null);
    const billLock = useBillExclusiveLock({
      heartbeat: () => heartbeatReceiveNoticeLock(billNo.value),
      release: () => releaseReceiveNoticeLock(billNo.value)
    });
    function hasPendingSubmit(line) {
      if (!(line == null ? void 0 : line.checked)) return false;
      if ((Number(line.pendingSubmitQty) || 0) > 0) return true;
      return (Number(line.pendingSubmitAuxQty) || 0) > 0;
    }
    const checkedCount = vue.computed(
      () => lines.value.filter((l) => l.checked).length
    );
    const submitableCount = vue.computed(
      () => lines.value.filter((l) => hasPendingSubmit(l)).length
    );
    function handleLockDenied(e) {
      billLock.stop();
      const msg = (e == null ? void 0 : e.message) || "单据正被其他人操作";
      uni.showToast({ title: msg, icon: "none", duration: 2500 });
      setTimeout(() => uni.navigateBack({ fail: () => {
      } }), 400);
    }
    async function loadDetail() {
      if (!billNo.value) return null;
      loading.value = true;
      try {
        const data = await getReceiveNoticeDetail(billNo.value);
        detail.value = data;
        lines.value = (data.lines || []).map(normalizeLine);
        billLock.start();
        return data;
      } catch (e) {
        if (isBillLockedError(e)) {
          handleLockDenied(e);
          return null;
        }
        uni.showToast({ title: (e == null ? void 0 : e.message) || "加载明细失败", icon: "none" });
        return null;
      } finally {
        loading.value = false;
      }
    }
    function normalizeLine(line) {
      if (!line) return line;
      return {
        ...line,
        checked: line.checked === true || line.checked === 1,
        pendingSubmitQty: line.pendingSubmitQty ?? 0,
        pendingSubmitAuxQty: line.pendingSubmitAuxQty ?? 0
      };
    }
    function formatScanError(e) {
      var _a, _b;
      const type = ((_a = e == null ? void 0 : e.data) == null ? void 0 : _a.errorType) || (e == null ? void 0 : e.errorType);
      const msg = (e == null ? void 0 : e.message) || ((_b = e == null ? void 0 : e.data) == null ? void 0 : _b.message);
      if (type === "BARCODE_EMPTY" || type === "BARCODE_PARSE_FAILED") {
        return msg || "条码格式错误，请扫描物料标签二维码";
      }
      if (type === "BARCODE_QTY_INVALID") {
        return msg || "二维码数量无效，请检查标签或重新打印";
      }
      if (type === "MATERIAL_NOT_ON_BILL") {
        return msg || "该物料不在本收料通知单中";
      }
      if (type === "BILL_LINES_NOT_READY" || type === "BILL_LINE_NOT_SYNCED") {
        return msg || "收料单明细未加载完成，请返回重新进入";
      }
      if (type === "LINE_ALREADY_FULL") {
        return msg || "该物料已收满";
      }
      return msg || "扫描失败";
    }
    function mergeLine(updated) {
      const normalized = normalizeLine(updated);
      const idx = lines.value.findIndex((l) => l.lineNo === normalized.lineNo);
      if (idx >= 0) {
        lines.value[idx] = { ...lines.value[idx], ...normalized };
      }
      if (detail.value) {
        detail.value.checkedLines = lines.value.filter((l) => l.checked).length;
      }
    }
    async function handleScan(barcode) {
      if (!(barcode == null ? void 0 : barcode.trim()) || submitting.value) return null;
      loading.value = true;
      lastHighlightLineNo.value = null;
      try {
        const line = await scanReceiveLine(billNo.value, barcode.trim());
        mergeLine(line);
        lastHighlightLineNo.value = line.lineNo;
        const qty = line.scannedBarcodeQty ?? line.pendingSubmitQty;
        const qtyText = qty != null && qty !== "" ? ` ×${formatQty$1(qty, line.unitCode)}` : "";
        uni.showToast({
          title: `✓ ${line.materialName || line.materialCode}${qtyText}`,
          icon: "success",
          duration: 1200
        });
        return line;
      } catch (e) {
        uni.showToast({ title: formatScanError(e), icon: "none", duration: 2500 });
        return null;
      } finally {
        loading.value = false;
      }
    }
    async function toggleCheck(lineNo, checked) {
      try {
        const line = await toggleReceiveLine(billNo.value, lineNo, checked);
        mergeLine(line);
      } catch (e) {
        uni.showToast({ title: (e == null ? void 0 : e.message) || "操作失败", icon: "none" });
      }
    }
    async function updateQty(lineNo, qty, auxQty) {
      const num = Number(qty);
      if (Number.isNaN(num) || num < 0) {
        uni.showToast({ title: "请输入有效数量", icon: "none" });
        return false;
      }
      try {
        const line = await updateReceiveLineQty(billNo.value, lineNo, num, auxQty);
        mergeLine(line);
        return true;
      } catch (e) {
        uni.showToast({ title: (e == null ? void 0 : e.message) || "更新数量失败", icon: "none" });
        return false;
      }
    }
    function formatQty$1(val, unitCode) {
      return formatQty(val, unitCode);
    }
    async function submit(getSubmitPayload) {
      var _a, _b, _c;
      if (!submitableCount.value) {
        uni.showToast({ title: "请先扫码或手动填写数量后再提交", icon: "none" });
        return false;
      }
      submitting.value = true;
      try {
        const payload = typeof getSubmitPayload === "function" ? getSubmitPayload() : {};
        const manual = (payload == null ? void 0 : payload.autoAssignWarehouse) === false;
        const result = await submitReceiveInbound(billNo.value, {
          supplierCode: (_a = detail.value) == null ? void 0 : _a.supplierCode,
          supplierName: (_b = detail.value) == null ? void 0 : _b.supplierName,
          autoAssignWarehouse: !manual,
          warehouseCode: manual ? payload == null ? void 0 : payload.warehouseCode : void 0,
          erpWarehouseCode: manual ? (payload == null ? void 0 : payload.erpWarehouseCode) || (payload == null ? void 0 : payload.warehouseCode) : (_c = detail.value) == null ? void 0 : _c.erpWarehouseCode,
          autoAllocateLocation: !!(payload == null ? void 0 : payload.autoAllocateLocation),
          locationCode: (payload == null ? void 0 : payload.locationCode) || (payload == null ? void 0 : payload.targetLocation) || void 0
        });
        const feedback = await handleErpSubmitResult(result);
        if (!feedback.ok) {
          await loadDetail();
          return false;
        }
        await loadDetail();
        if (isNoticeBillCompleted(detail.value)) {
          await billLock.releaseLock();
          setTimeout(() => uni.navigateBack(), 400);
        }
        return true;
      } catch (e) {
        if (isBillLockedError(e)) {
          handleLockDenied(e);
          return false;
        }
        await alertErpSubmitFailed(formatErpSubmitError(e));
        await loadDetail();
        return false;
      } finally {
        submitting.value = false;
      }
    }
    function rowClass(line) {
      if (line.lineNo === lastHighlightLineNo.value) return "flash";
      const submitted = Number(line.submittedQty) || 0;
      const plan = Number(line.planQty) || 0;
      if (submitted >= plan && plan > 0) return "done";
      if (submitted > 0 && submitted < plan) return "partial";
      if (line.checked) return "checked";
      return "";
    }
    return {
      loading,
      submitting,
      detail,
      lines,
      lastHighlightLineNo,
      checkedCount,
      submitableCount,
      loadDetail,
      handleScan,
      toggleCheck,
      updateQty,
      formatQty: formatQty$1,
      submit,
      rowClass,
      isLabelScanned
    };
  }
  function sanitizeDecimalInput(raw, maxScale = 4) {
    if (raw == null) return "";
    let s = String(raw).replace(/[^\d.]/g, "");
    const firstDot = s.indexOf(".");
    if (firstDot >= 0) {
      s = s.slice(0, firstDot + 1) + s.slice(firstDot + 1).replace(/\./g, "");
      if (maxScale >= 0) {
        const parts = s.split(".");
        if (parts[1] && parts[1].length > maxScale) {
          parts[1] = parts[1].slice(0, maxScale);
          s = parts.join(".");
        }
      }
    }
    return s;
  }
  const _sfc_main$z = {
    __name: "receive-scan",
    setup(__props, { expose: __expose }) {
      __expose();
      const billNo = vue.ref("");
      const scanInputRef = vue.ref(null);
      const warehousePickerRef = vue.ref(null);
      const locationPickerRef = vue.ref(null);
      const warehousePayload = vue.ref({ autoAssignWarehouse: true });
      const locationPayload = vue.ref({ autoAllocateLocation: false });
      const qtyDrafts = vue.reactive({});
      const auxQtyDrafts = vue.reactive({});
      const updatingLineNo = vue.ref(null);
      const { alive, refocusScanInput } = usePageAlive();
      const {
        loading,
        submitting,
        detail,
        lines,
        checkedCount,
        submitableCount,
        loadDetail,
        handleScan,
        toggleCheck,
        updateQty,
        formatQty: formatQty2,
        submit,
        rowClass,
        isLabelScanned: isLabelScanned2
      } = useReceiveNoticeScan(billNo);
      const partialCount = vue.computed(
        () => lines.value.filter((l) => isPartialLine(l)).length
      );
      const suggestWarehouseCode = vue.computed(() => {
        var _a, _b;
        const pending = lines.value.find((l) => l.checked && (Number(l.pendingSubmitQty) || 0) > 0);
        if (pending == null ? void 0 : pending.erpStockCode) return pending.erpStockCode;
        return ((_a = detail.value) == null ? void 0 : _a.erpWarehouseCode) || ((_b = detail.value) == null ? void 0 : _b.warehouseCode) || "";
      });
      const resolvedWarehouseCode = vue.computed(() => {
        const wh = warehousePayload.value;
        if ((wh == null ? void 0 : wh.autoAssignWarehouse) === false && (wh == null ? void 0 : wh.warehouseCode)) {
          return wh.warehouseCode;
        }
        return suggestWarehouseCode.value || "";
      });
      const suggestMaterialCode = vue.computed(() => {
        var _a;
        const pending = lines.value.find((l) => l.checked && (Number(l.pendingSubmitQty) || 0) > 0);
        return (pending == null ? void 0 : pending.materialCode) || ((_a = lines.value[0]) == null ? void 0 : _a.materialCode) || "";
      });
      const suggestBatchNo = vue.computed(() => {
        const pending = lines.value.find((l) => l.checked && (Number(l.pendingSubmitQty) || 0) > 0);
        return (pending == null ? void 0 : pending.batchNo) || "";
      });
      function onWarehouseChange(payload) {
        warehousePayload.value = payload || { autoAssignWarehouse: true };
      }
      function onLocationChange(payload) {
        locationPayload.value = payload || { autoAllocateLocation: false };
      }
      function isDoneLine(line) {
        const pending = Number(line.pendingSubmitQty) || 0;
        const pendingAux = Number(line.pendingSubmitAuxQty) || 0;
        if (pending > 0 || pendingAux > 0) return false;
        const submitted = Number(line.submittedQty) || 0;
        const plan = Number(line.planQty) || 0;
        if (plan > 0 && submitted >= plan) return true;
        if (inputMapsToAux(line)) {
          const auxPlan = Number(line.planAuxQty) || 0;
          const auxSubmitted = Number(line.submittedAuxQty) || 0;
          if (plan <= 0 && auxPlan > 0 && auxSubmitted >= auxPlan) return true;
        }
        return false;
      }
      function isPartialLine(line) {
        const submitted = Number(line.submittedQty) || 0;
        const plan = Number(line.planQty) || 0;
        if (submitted > 0 && plan > 0 && submitted < plan) return true;
        if (inputMapsToAux(line)) {
          const auxSubmitted = Number(line.submittedAuxQty) || 0;
          const auxPlan = Number(line.planAuxQty) || 0;
          return auxSubmitted > 0 && auxPlan > 0 && auxSubmitted < auxPlan;
        }
        return false;
      }
      function canEditLine(line) {
        const remain = Number(inputRemainQty(line)) || 0;
        const pending = Number(inputPendingQty(line)) || 0;
        return remain > 0 || pending > 0;
      }
      function inputMapsToAux(line) {
        return !!((line == null ? void 0 : line.multiUnit) && (line == null ? void 0 : line.inputMapsToAux));
      }
      function inputPendingQty(line) {
        return inputMapsToAux(line) ? line.pendingSubmitAuxQty || 0 : line.pendingSubmitQty || 0;
      }
      function inputPlanQty(line) {
        return inputMapsToAux(line) ? line.planAuxQty : line.planQty;
      }
      function inputSubmittedQty(line) {
        return inputMapsToAux(line) ? line.submittedAuxQty : line.submittedQty;
      }
      function inputRemainQty(line) {
        return inputMapsToAux(line) ? line.remainAuxQty : line.remainQty;
      }
      function autoPendingQty(line) {
        return inputMapsToAux(line) ? line.pendingSubmitQty || 0 : line.pendingSubmitAuxQty || 0;
      }
      function autoPlanQty(line) {
        return inputMapsToAux(line) ? line.planQty : line.planAuxQty;
      }
      function autoSubmittedQty(line) {
        return inputMapsToAux(line) ? line.submittedQty : line.submittedAuxQty;
      }
      function autoRemainQty(line) {
        return inputMapsToAux(line) ? line.remainQty : line.remainAuxQty;
      }
      function convertByPlanRate(qty, fromPlan, toPlan) {
        const q = Number(qty) || 0;
        const from = Number(fromPlan) || 0;
        const to = Number(toPlan) || 0;
        if (q <= 0) return 0;
        if (from <= 0 || to <= 0) return q;
        const n = q * to / from;
        return Number.isInteger(n) ? n : Number(n.toFixed(6));
      }
      function pcsToKg(line, pcs) {
        if (inputMapsToAux(line)) return convertByPlanRate(pcs, line.planAuxQty, line.planQty);
        return convertByPlanRate(pcs, line.planQty, line.planAuxQty);
      }
      function kgToPcs(line, kg) {
        if (inputMapsToAux(line)) return convertByPlanRate(kg, line.planQty, line.planAuxQty);
        return convertByPlanRate(kg, line.planAuxQty, line.planQty);
      }
      function inputUnitOf(line) {
        return line.inputUnitCode || line.unitCode;
      }
      function autoUnitOf(line) {
        return line.autoUnitCode || line.auxUnitCode;
      }
      function syncQtyDrafts() {
        lines.value.forEach((line) => {
          qtyDrafts[line.lineNo] = formatQtyInput(inputPendingQty(line), inputUnitOf(line));
          if (line.multiUnit) auxQtyDrafts[line.lineNo] = formatQtyInput(autoPendingQty(line), autoUnitOf(line));
        });
      }
      function getQtyDraft(line) {
        const key = line.lineNo;
        if (qtyDrafts[key] === void 0 || qtyDrafts[key] === null) {
          return formatQtyInput(inputPendingQty(line), inputUnitOf(line));
        }
        return qtyDrafts[key];
      }
      function getAuxQtyDraft(line) {
        const key = line.lineNo;
        if (auxQtyDrafts[key] === void 0 || auxQtyDrafts[key] === null) {
          return formatQtyInput(autoPendingQty(line), autoUnitOf(line));
        }
        return auxQtyDrafts[key];
      }
      function onAuxQtyInput(line, e) {
        auxQtyDrafts[line.lineNo] = sanitizeDecimalInput(e.detail.value, qtyDecimalScale(autoUnitOf(line)));
      }
      async function onAuxQtyBlur(line) {
        const kg = Number(auxQtyDrafts[line.lineNo] || 0);
        if (Number.isNaN(kg) || kg < 0) {
          auxQtyDrafts[line.lineNo] = formatQtyInput(autoPendingQty(line), autoUnitOf(line));
          return;
        }
        const pcs = kgToPcs(line, kg);
        qtyDrafts[line.lineNo] = formatQtyInput(pcs, inputUnitOf(line));
        let stockQty = pcs;
        let auxQty = kg;
        if (inputMapsToAux(line)) {
          stockQty = kg;
          auxQty = pcs;
        }
        updatingLineNo.value = line.lineNo;
        const ok = await updateQty(line.lineNo, stockQty, auxQty);
        updatingLineNo.value = null;
        if (ok) syncQtyDrafts();
        else {
          qtyDrafts[line.lineNo] = formatQtyInput(inputPendingQty(line), inputUnitOf(line));
          auxQtyDrafts[line.lineNo] = formatQtyInput(autoPendingQty(line), autoUnitOf(line));
        }
      }
      function onQtyInput(line, e) {
        const raw = sanitizeDecimalInput(e.detail.value, qtyDecimalScale(inputUnitOf(line)));
        qtyDrafts[line.lineNo] = raw;
        if (line.multiUnit) {
          const pcs = Number(raw || 0);
          auxQtyDrafts[line.lineNo] = formatQtyInput(
            Number.isNaN(pcs) || pcs < 0 ? 0 : pcsToKg(line, pcs),
            autoUnitOf(line)
          );
        }
      }
      async function onQtyBlur(line) {
        const raw = qtyDrafts[line.lineNo];
        const pcs = raw === "" || raw == null ? 0 : Number(raw);
        if (Number.isNaN(pcs) || pcs < 0) {
          qtyDrafts[line.lineNo] = formatQtyInput(inputPendingQty(line), inputUnitOf(line));
          return;
        }
        let stockQty = pcs;
        let auxQty;
        if (line.multiUnit) {
          const kg = pcsToKg(line, pcs);
          auxQtyDrafts[line.lineNo] = formatQtyInput(kg, autoUnitOf(line));
          if (inputMapsToAux(line)) {
            stockQty = kg;
            auxQty = pcs;
          } else {
            stockQty = pcs;
            auxQty = kg;
          }
        }
        updatingLineNo.value = line.lineNo;
        const ok = await updateQty(line.lineNo, stockQty, auxQty);
        updatingLineNo.value = null;
        if (ok) {
          const updated = lines.value.find((l) => l.lineNo === line.lineNo);
          if (updated) {
            qtyDrafts[line.lineNo] = formatQtyInput(inputPendingQty(updated), inputUnitOf(updated));
            if (updated.multiUnit) auxQtyDrafts[line.lineNo] = formatQtyInput(autoPendingQty(updated), autoUnitOf(updated));
          }
        } else {
          qtyDrafts[line.lineNo] = formatQtyInput(inputPendingQty(line), inputUnitOf(line));
          if (line.multiUnit) auxQtyDrafts[line.lineNo] = formatQtyInput(autoPendingQty(line), autoUnitOf(line));
        }
      }
      async function onScan(barcode) {
        if (!alive.value) return;
        await handleScan(barcode);
        syncQtyDrafts();
        refocusScanInput(scanInputRef, 300);
      }
      function onToggle(line) {
        toggleCheck(line.lineNo, !line.checked);
      }
      function onRowTap(line) {
      }
      async function onSubmit() {
        const ok = await submit(() => {
          var _a, _b, _c, _d;
          return {
            ...((_b = (_a = warehousePickerRef.value) == null ? void 0 : _a.getPayload) == null ? void 0 : _b.call(_a)) || warehousePayload.value,
            ...((_d = (_c = locationPickerRef.value) == null ? void 0 : _c.getPayload) == null ? void 0 : _d.call(_c)) || locationPayload.value
          };
        });
        if (ok) syncQtyDrafts();
      }
      onLoad((options) => {
        billNo.value = decodeURIComponent((options == null ? void 0 : options.billNo) || "");
        uni.setNavigationBarTitle({ title: "物料扫描" });
      });
      onShow(async () => {
        if (billNo.value) {
          await loadDetail();
          syncQtyDrafts();
        }
        refocusScanInput(scanInputRef, 400);
      });
      const __returned__ = { billNo, scanInputRef, warehousePickerRef, locationPickerRef, warehousePayload, locationPayload, qtyDrafts, auxQtyDrafts, updatingLineNo, alive, refocusScanInput, loading, submitting, detail, lines, checkedCount, submitableCount, loadDetail, handleScan, toggleCheck, updateQty, formatQty: formatQty2, submit, rowClass, isLabelScanned: isLabelScanned2, partialCount, suggestWarehouseCode, resolvedWarehouseCode, suggestMaterialCode, suggestBatchNo, onWarehouseChange, onLocationChange, isDoneLine, isPartialLine, canEditLine, inputMapsToAux, inputPendingQty, inputPlanQty, inputSubmittedQty, inputRemainQty, autoPendingQty, autoPlanQty, autoSubmittedQty, autoRemainQty, convertByPlanRate, pcsToKg, kgToPcs, inputUnitOf, autoUnitOf, syncQtyDrafts, getQtyDraft, getAuxQtyDraft, onAuxQtyInput, onAuxQtyBlur, onQtyInput, onQtyBlur, onScan, onToggle, onRowTap, onSubmit, ref: vue.ref, computed: vue.computed, reactive: vue.reactive, get onLoad() {
        return onLoad;
      }, get onShow() {
        return onShow;
      }, CompactScanBox, WarehousePicker, LocationPicker, get useReceiveNoticeScan() {
        return useReceiveNoticeScan;
      }, get usePageAlive() {
        return usePageAlive;
      }, get sanitizeDecimalInput() {
        return sanitizeDecimalInput;
      }, get qtyDecimalScale() {
        return qtyDecimalScale;
      }, get formatQtyInput() {
        return formatQtyInput;
      } };
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  };
  function _sfc_render$y(_ctx, _cache, $props, $setup, $data, $options) {
    return vue.openBlock(), vue.createElementBlock("view", { class: "page" }, [
      vue.createElementVNode("view", { class: "scan-top" }, [
        vue.createVNode($setup["CompactScanBox"], {
          ref: "scanInputRef",
          disabled: $setup.loading || $setup.submitting,
          onScan: $setup.onScan
        }, null, 8, ["disabled"])
      ]),
      $setup.detail ? (vue.openBlock(), vue.createElementBlock("view", {
        key: 0,
        class: "order-bar"
      }, [
        vue.createElementVNode("view", { class: "order-info" }, [
          vue.createElementVNode(
            "text",
            { class: "order-no" },
            vue.toDisplayString($setup.detail.billNo),
            1
            /* TEXT */
          ),
          vue.createElementVNode(
            "text",
            { class: "order-sub" },
            vue.toDisplayString($setup.detail.supplierName || "-"),
            1
            /* TEXT */
          ),
          $setup.detail.erpBillNo ? (vue.openBlock(), vue.createElementBlock(
            "text",
            {
              key: 0,
              class: "order-erp"
            },
            "采购入库 " + vue.toDisplayString($setup.detail.erpBillNo),
            1
            /* TEXT */
          )) : vue.createCommentVNode("v-if", true)
        ]),
        vue.createElementVNode("view", { class: "order-stat-wrap" }, [
          vue.createElementVNode(
            "text",
            { class: "order-stat" },
            vue.toDisplayString($setup.checkedCount) + "/" + vue.toDisplayString($setup.lines.length) + " 已勾",
            1
            /* TEXT */
          ),
          $setup.partialCount ? (vue.openBlock(), vue.createElementBlock(
            "text",
            {
              key: 0,
              class: "order-partial"
            },
            "部分已领 " + vue.toDisplayString($setup.partialCount),
            1
            /* TEXT */
          )) : vue.createCommentVNode("v-if", true)
        ])
      ])) : vue.createCommentVNode("v-if", true),
      vue.createElementVNode("scroll-view", {
        class: "list-scroll",
        "scroll-y": "",
        "show-scrollbar": false
      }, [
        (vue.openBlock(true), vue.createElementBlock(
          vue.Fragment,
          null,
          vue.renderList($setup.lines, (line) => {
            return vue.openBlock(), vue.createElementBlock("view", {
              key: line.lineNo,
              class: vue.normalizeClass(["mat-row", $setup.rowClass(line)]),
              onClick: ($event) => $setup.onRowTap(line)
            }, [
              vue.createElementVNode("view", { class: "row-header" }, [
                vue.createElementVNode("view", {
                  class: "check-box",
                  onClick: vue.withModifiers(($event) => $setup.onToggle(line), ["stop"])
                }, [
                  vue.createElementVNode(
                    "view",
                    {
                      class: vue.normalizeClass(["check-inner", line.checked && "on"])
                    },
                    [
                      line.checked ? (vue.openBlock(), vue.createElementBlock("text", {
                        key: 0,
                        class: "check-mark"
                      }, "✓")) : vue.createCommentVNode("v-if", true)
                    ],
                    2
                    /* CLASS */
                  )
                ], 8, ["onClick"]),
                vue.createElementVNode("view", { class: "row-main" }, [
                  vue.createElementVNode("view", { class: "name-row" }, [
                    vue.createElementVNode(
                      "text",
                      { class: "mat-code" },
                      vue.toDisplayString(line.materialCode),
                      1
                      /* TEXT */
                    ),
                    $setup.isPartialLine(line) ? (vue.openBlock(), vue.createElementBlock("text", {
                      key: 0,
                      class: "partial-tag"
                    }, "部分已领")) : vue.createCommentVNode("v-if", true)
                  ]),
                  vue.createElementVNode(
                    "text",
                    { class: "mat-name" },
                    vue.toDisplayString(line.materialName || "-"),
                    1
                    /* TEXT */
                  ),
                  vue.createElementVNode(
                    "text",
                    { class: "mat-spec" },
                    "规格 " + vue.toDisplayString(line.specification || "-"),
                    1
                    /* TEXT */
                  ),
                  vue.createElementVNode(
                    "text",
                    { class: "mat-batch" },
                    "批次 " + vue.toDisplayString(line.batchNo || "-"),
                    1
                    /* TEXT */
                  ),
                  vue.createElementVNode(
                    "text",
                    { class: "mat-wh" },
                    "仓库 " + vue.toDisplayString(line.erpStockCode || "-"),
                    1
                    /* TEXT */
                  )
                ])
              ]),
              vue.createElementVNode("view", {
                class: "qty-panel",
                onClick: _cache[0] || (_cache[0] = vue.withModifiers(() => {
                }, ["stop"]))
              }, [
                vue.createElementVNode("view", { class: "qty-grid" }, [
                  vue.createElementVNode("view", { class: "qty-cell" }, [
                    vue.createElementVNode("text", { class: "qty-label" }, "计划"),
                    vue.createElementVNode(
                      "text",
                      { class: "qty-value" },
                      vue.toDisplayString($setup.formatQty($setup.inputPlanQty(line), line.inputUnitCode || line.unitCode)),
                      1
                      /* TEXT */
                    )
                  ]),
                  vue.createElementVNode("view", { class: "qty-cell" }, [
                    vue.createElementVNode("text", { class: "qty-label" }, "已领"),
                    vue.createElementVNode(
                      "text",
                      { class: "qty-value submitted" },
                      vue.toDisplayString($setup.formatQty($setup.inputSubmittedQty(line), line.inputUnitCode || line.unitCode)),
                      1
                      /* TEXT */
                    )
                  ]),
                  vue.createElementVNode("view", { class: "qty-cell" }, [
                    vue.createElementVNode("text", { class: "qty-label" }, "可领"),
                    vue.createElementVNode(
                      "text",
                      { class: "qty-value remain" },
                      vue.toDisplayString($setup.formatQty($setup.inputRemainQty(line), line.inputUnitCode || line.unitCode)),
                      1
                      /* TEXT */
                    )
                  ]),
                  vue.createElementVNode("view", { class: "qty-cell unit-cell" }, [
                    vue.createElementVNode("text", { class: "qty-label" }, "单位"),
                    vue.createElementVNode(
                      "text",
                      { class: "qty-value unit" },
                      vue.toDisplayString(line.inputUnitCode || line.unitCode || "PCS"),
                      1
                      /* TEXT */
                    )
                  ])
                ]),
                line.multiUnit ? (vue.openBlock(), vue.createElementBlock("view", {
                  key: 0,
                  class: "qty-grid aux-grid"
                }, [
                  vue.createElementVNode("view", { class: "qty-cell" }, [
                    vue.createElementVNode(
                      "text",
                      { class: "qty-label" },
                      "计划(" + vue.toDisplayString(line.autoUnitCode || line.auxUnitCode) + ")",
                      1
                      /* TEXT */
                    ),
                    vue.createElementVNode(
                      "text",
                      { class: "qty-value" },
                      vue.toDisplayString($setup.formatQty($setup.autoPlanQty(line), line.autoUnitCode || line.auxUnitCode)),
                      1
                      /* TEXT */
                    )
                  ]),
                  vue.createElementVNode("view", { class: "qty-cell" }, [
                    vue.createElementVNode("text", { class: "qty-label" }, "已领"),
                    vue.createElementVNode(
                      "text",
                      { class: "qty-value submitted" },
                      vue.toDisplayString($setup.formatQty($setup.autoSubmittedQty(line), line.autoUnitCode || line.auxUnitCode)),
                      1
                      /* TEXT */
                    )
                  ]),
                  vue.createElementVNode("view", { class: "qty-cell" }, [
                    vue.createElementVNode("text", { class: "qty-label" }, "可领"),
                    vue.createElementVNode(
                      "text",
                      { class: "qty-value remain" },
                      vue.toDisplayString($setup.formatQty($setup.autoRemainQty(line), line.autoUnitCode || line.auxUnitCode)),
                      1
                      /* TEXT */
                    )
                  ]),
                  vue.createElementVNode("view", { class: "qty-cell" }, [
                    vue.createElementVNode("text", { class: "qty-label" }, "单位"),
                    vue.createElementVNode(
                      "text",
                      { class: "qty-value unit" },
                      vue.toDisplayString(line.autoUnitCode || line.auxUnitCode),
                      1
                      /* TEXT */
                    )
                  ])
                ])) : vue.createCommentVNode("v-if", true),
                $setup.canEditLine(line) ? (vue.openBlock(), vue.createElementBlock("view", {
                  key: 1,
                  class: "qty-edit"
                }, [
                  vue.createElementVNode(
                    "text",
                    { class: "qty-edit-label" },
                    "本次(" + vue.toDisplayString(line.inputUnitCode || line.unitCode || "PCS") + ")",
                    1
                    /* TEXT */
                  ),
                  vue.createElementVNode("input", {
                    class: "qty-input",
                    type: "text",
                    inputmode: "decimal",
                    value: $setup.getQtyDraft(line),
                    disabled: $setup.updatingLineNo === line.lineNo,
                    placeholder: "0",
                    onInput: ($event) => $setup.onQtyInput(line, $event),
                    onBlur: ($event) => $setup.onQtyBlur(line),
                    onConfirm: ($event) => $setup.onQtyBlur(line)
                  }, null, 40, ["value", "disabled", "onInput", "onBlur", "onConfirm"]),
                  vue.createElementVNode(
                    "text",
                    { class: "qty-edit-unit" },
                    vue.toDisplayString(line.inputUnitCode || line.unitCode || "PCS"),
                    1
                    /* TEXT */
                  )
                ])) : vue.createCommentVNode("v-if", true),
                $setup.canEditLine(line) && line.multiUnit ? (vue.openBlock(), vue.createElementBlock("view", {
                  key: 2,
                  class: "qty-edit"
                }, [
                  vue.createElementVNode(
                    "text",
                    { class: "qty-edit-label" },
                    "换算(" + vue.toDisplayString(line.autoUnitCode || line.auxUnitCode) + ")",
                    1
                    /* TEXT */
                  ),
                  vue.createElementVNode("input", {
                    class: "qty-input",
                    type: "text",
                    inputmode: "decimal",
                    value: $setup.getAuxQtyDraft(line),
                    disabled: $setup.updatingLineNo === line.lineNo,
                    placeholder: "0",
                    onInput: ($event) => $setup.onAuxQtyInput(line, $event),
                    onBlur: ($event) => $setup.onAuxQtyBlur(line),
                    onConfirm: ($event) => $setup.onAuxQtyBlur(line)
                  }, null, 40, ["value", "disabled", "onInput", "onBlur", "onConfirm"]),
                  vue.createElementVNode(
                    "text",
                    { class: "qty-edit-unit" },
                    vue.toDisplayString(line.autoUnitCode || line.auxUnitCode),
                    1
                    /* TEXT */
                  )
                ])) : vue.createCommentVNode("v-if", true),
                $setup.isDoneLine(line) ? (vue.openBlock(), vue.createElementBlock("view", {
                  key: 3,
                  class: "qty-done-tip"
                }, "已全部领取")) : vue.createCommentVNode("v-if", true)
              ])
            ], 10, ["onClick"]);
          }),
          128
          /* KEYED_FRAGMENT */
        )),
        !$setup.lines.length && !$setup.loading ? (vue.openBlock(), vue.createElementBlock("view", {
          key: 0,
          class: "empty"
        }, [
          vue.createElementVNode("text", { class: "empty-icon" }, "📦")
        ])) : vue.createCommentVNode("v-if", true),
        $setup.detail ? (vue.openBlock(), vue.createElementBlock("view", {
          key: 1,
          class: "wh-section"
        }, [
          vue.createVNode($setup["WarehousePicker"], {
            ref: "warehousePickerRef",
            "suggest-code": $setup.suggestWarehouseCode,
            onChange: $setup.onWarehouseChange
          }, null, 8, ["suggest-code"]),
          vue.createVNode($setup["LocationPicker"], {
            ref: "locationPickerRef",
            "warehouse-code": $setup.resolvedWarehouseCode,
            "material-code": $setup.suggestMaterialCode,
            "batch-no": $setup.suggestBatchNo,
            theme: "light",
            onChange: $setup.onLocationChange
          }, null, 8, ["warehouse-code", "material-code", "batch-no"])
        ])) : vue.createCommentVNode("v-if", true),
        vue.createElementVNode("view", { class: "scroll-bottom-pad" })
      ]),
      vue.createElementVNode("view", { class: "footer" }, [
        vue.createElementVNode("button", {
          class: "submit-btn",
          type: "primary",
          loading: $setup.submitting,
          disabled: !$setup.submitableCount,
          onClick: $setup.onSubmit
        }, " 提交入库" + vue.toDisplayString($setup.submitableCount ? ` (${$setup.submitableCount})` : ""), 9, ["loading", "disabled"])
      ])
    ]);
  }
  const PagesInboundReceiveScan = /* @__PURE__ */ _export_sfc(_sfc_main$z, [["render", _sfc_render$y], ["__scopeId", "data-v-15e915fb"], ["__file", "D:/AAA/WMS/wms-pda/src/pages/inbound/receive-scan.vue"]]);
  const _sfc_main$y = {
    __name: "notice-hub",
    setup(__props, { expose: __expose }) {
      __expose();
      const types = vue.ref(listNoticeBillTypes("OUTBOUND"));
      function hubDesc(item) {
        if (item.code === "PRODUCTION_ISSUE") return "扫未审核领料单 · 核对物料 · 提交审核";
        if (item.code === "PRODUCTION_FEED") return "扫未审核补料单 · 核对物料 · 工作流审批";
        if (item.code === "OUTSOURCE_FEED") return "扫未审核委外补料单 · 核对物料 · 工作流审批";
        if (item.code === "PRODUCTION_RET_STOCK") return "扫未审核退库单 · 核对物料 · 提交审核";
        if (item.code === "OUTSOURCE_ISSUE") return "扫未审核委外领料单 · 核对物料 · 提交审核";
        if (item.code === "OTHER_OUT" || item.code === "SALES_DELIVERY") {
          return item.code === "SALES_DELIVERY" ? "扫未审核发货通知 · 确认后下推销售出库并审核" : "扫未审核其他出库单 · 核对物料 · 提交审核";
        }
        if (item.code === "PURCHASE_RETURN") {
          return "扫未审核采购退料单 · 核对物料 · 提交审核";
        }
        return "扫码 · 勾选 · 提交审核";
      }
      function openType(item) {
        if (item.code === "PRODUCTION_ISSUE") {
          uni.navigateTo({ url: "/pages/picking/production-issue" });
          return;
        }
        if (item.code === "PRODUCTION_FEED") {
          uni.navigateTo({ url: "/pages/picking/production-feed" });
          return;
        }
        if (item.code === "OUTSOURCE_FEED") {
          uni.navigateTo({ url: "/pages/picking/outsource-feed" });
          return;
        }
        if (item.code === "OUTSOURCE_ISSUE") {
          uni.navigateTo({ url: "/pages/picking/outsource-issue" });
          return;
        }
        uni.navigateTo({
          url: `/pages/notice/list?billType=${encodeURIComponent(item.code)}&direction=OUTBOUND`
        });
      }
      onLoad(() => uni.setNavigationBarTitle({ title: "出库业务" }));
      vue.onMounted(() => {
        types.value = listNoticeBillTypes("OUTBOUND");
      });
      const __returned__ = { types, hubDesc, openType, ref: vue.ref, onMounted: vue.onMounted, get onLoad() {
        return onLoad;
      }, get listNoticeBillTypes() {
        return listNoticeBillTypes;
      } };
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  };
  function _sfc_render$x(_ctx, _cache, $props, $setup, $data, $options) {
    return vue.openBlock(), vue.createElementBlock("view", { class: "page" }, [
      vue.createElementVNode("view", { class: "header" }, [
        vue.createElementVNode("text", { class: "title" }, "选择出库单据类型"),
        vue.createElementVNode("text", { class: "sub" }, "所有出库均采用通知单扫码模式：列表 → 扫码 → 分批提交")
      ]),
      vue.createElementVNode("view", { class: "type-grid" }, [
        (vue.openBlock(true), vue.createElementBlock(
          vue.Fragment,
          null,
          vue.renderList($setup.types, (item) => {
            return vue.openBlock(), vue.createElementBlock("view", {
              key: item.code,
              class: "type-card",
              style: vue.normalizeStyle({ borderLeftColor: item.color }),
              onClick: ($event) => $setup.openType(item)
            }, [
              vue.createElementVNode(
                "text",
                { class: "icon" },
                vue.toDisplayString(item.icon),
                1
                /* TEXT */
              ),
              vue.createElementVNode("view", { class: "info" }, [
                vue.createElementVNode(
                  "text",
                  { class: "label" },
                  vue.toDisplayString(item.label),
                  1
                  /* TEXT */
                ),
                vue.createElementVNode(
                  "text",
                  { class: "desc" },
                  vue.toDisplayString($setup.hubDesc(item)),
                  1
                  /* TEXT */
                )
              ]),
              vue.createElementVNode("text", { class: "arrow" }, "›")
            ], 12, ["onClick"]);
          }),
          128
          /* KEYED_FRAGMENT */
        ))
      ])
    ]);
  }
  const PagesOutboundNoticeHub = /* @__PURE__ */ _export_sfc(_sfc_main$y, [["render", _sfc_render$x], ["__scopeId", "data-v-1ef9fa85"], ["__file", "D:/AAA/WMS/wms-pda/src/pages/outbound/notice-hub.vue"]]);
  function baseUrl(billType) {
    return `/mobile/notice-bill/${billType}`;
  }
  function listNoticeBills(billType, params = {}) {
    return request({ url: baseUrl(billType), data: params });
  }
  function resolveNoticeBarcode(billType, barcodeContent) {
    return request({
      url: `${baseUrl(billType)}/resolve-barcode`,
      method: "POST",
      data: { barcodeContent }
    });
  }
  function getNoticeBillDetail(billType, billNo, options = {}) {
    const data = {};
    if (options.refresh) data.refresh = true;
    return request({ url: `${baseUrl(billType)}/${billNo}`, data });
  }
  function scanNoticeLine(billType, billNo, barcodeContent) {
    return request({
      url: `${baseUrl(billType)}/${billNo}/scan`,
      method: "POST",
      data: withDevice({ barcodeContent }),
      silent: true
    });
  }
  function toggleNoticeLine(billType, billNo, lineNo, checked) {
    return request({
      url: `${baseUrl(billType)}/${billNo}/lines/${lineNo}/check?checked=${checked ? "true" : "false"}`,
      method: "PUT"
    });
  }
  function updateNoticeLineQty(billType, billNo, lineNo, qty, auxQty) {
    const data = { qty };
    if (auxQty != null && auxQty !== "") {
      data.auxQty = auxQty;
    }
    return request({
      url: `${baseUrl(billType)}/${billNo}/lines/${lineNo}/qty`,
      method: "PUT",
      data
    });
  }
  function submitNoticeBill(billType, billNo, data = {}) {
    return request({
      url: `${baseUrl(billType)}/${billNo}/submit`,
      method: "POST",
      data: withDevice(data),
      // 由页面 Modal 展示完整金蝶成败信息，避免 http 层 Toast 截断/重复
      silent: true,
      timeout: 18e4
    });
  }
  function heartbeatNoticeBillLock(billType, billNo) {
    return request({
      url: `${baseUrl(billType)}/${encodeURIComponent(billNo)}/lock/heartbeat`,
      method: "POST",
      silent: true
    });
  }
  function releaseNoticeBillLock(billType, billNo) {
    return request({
      url: `${baseUrl(billType)}/${encodeURIComponent(billNo)}/lock/release`,
      method: "POST",
      silent: true
    });
  }
  function useNoticeBillList(billTypeRef) {
    const loading = vue.ref(false);
    const keyword = vue.ref("");
    const notices = vue.ref([]);
    let lastShowAt = 0;
    const typeConfig = vue.computed(() => getNoticeBillType(billTypeRef.value));
    function listCacheKey(kw) {
      return `notice-list:${billTypeRef.value}:${String(kw || "").trim().toLowerCase()}`;
    }
    async function loadList(kw = keyword.value, options = {}) {
      if (!billTypeRef.value) return [];
      const force = options.force === true;
      keyword.value = kw;
      const cacheKey = listCacheKey(kw);
      if (!force) {
        const cached = cacheGet(cacheKey);
        if (cached) {
          notices.value = cached;
          return notices.value;
        }
      }
      loading.value = true;
      try {
        const pageData = await listNoticeBills(billTypeRef.value, {
          keyword: kw || void 0,
          current: 1,
          size: PAGE_SIZE
        });
        const parsed = parseNoticePage(pageData);
        notices.value = parsed.valid;
        cacheSet(cacheKey, notices.value, LIST_CACHE_TTL_MS$1);
        lastShowAt = Date.now();
        return notices.value;
      } catch (e) {
        uni.showToast({ title: (e == null ? void 0 : e.message) || "加载失败", icon: "none" });
        return notices.value;
      } finally {
        loading.value = false;
      }
    }
    async function loadListOnShow() {
      const now = Date.now();
      if (now - lastShowAt < SHOW_THROTTLE_MS && notices.value.length) {
        return notices.value;
      }
      return loadList(keyword.value);
    }
    function invalidateListCache() {
      cacheDelByPrefix(`notice-list:${billTypeRef.value}:`);
    }
    async function searchByBarcode(barcode) {
      const raw = (barcode || "").trim();
      if (!raw) return loadList("", { force: true });
      try {
        const { billNo } = await resolveNoticeBarcode(billTypeRef.value, raw);
        if (billNo) {
          keyword.value = billNo;
          return loadList(billNo, { force: true });
        }
      } catch {
      }
      keyword.value = raw;
      return loadList(raw, { force: true });
    }
    function statusLabel(item) {
      const s = item.scanStatus || item.status;
      const inbound = typeConfig.value.direction === "INBOUND";
      if (s === "COMPLETED" || s === "PARTIAL_SUBMITTED") return inbound ? "已完成" : "已出完";
      if (s === "SCANNING") return "扫码中";
      if (s === "NEW" || !s) return inbound ? "待收料" : "待出库";
      if (item.inProgress) return "进行中";
      return inbound ? "待收料" : "待出库";
    }
    function statusClass(item) {
      const s = item.scanStatus;
      if (s === "COMPLETED" || s === "PARTIAL_SUBMITTED") return "done";
      if (s === "SCANNING") return "progress";
      return "new";
    }
    return {
      loading,
      keyword,
      notices,
      typeConfig,
      loadList,
      loadListOnShow,
      invalidateListCache,
      searchByBarcode,
      statusLabel,
      statusClass
    };
  }
  const _sfc_main$x = {
    __name: "list",
    setup(__props, { expose: __expose }) {
      __expose();
      const billType = vue.ref("PURCHASE_RECEIVE");
      const direction = vue.ref("INBOUND");
      const scanInputRef = vue.ref(null);
      const busy = vue.ref(false);
      const { alive, refocusScanInput } = usePageAlive();
      const {
        loading,
        keyword,
        notices,
        typeConfig,
        loadListOnShow,
        statusLabel,
        statusClass
      } = useNoticeBillList(billType);
      async function openByBarcode(barcode) {
        if (!alive.value) return;
        if (busy.value) {
          uni.showToast({ title: "正在打开，请稍候", icon: "none" });
          return;
        }
        const raw = (barcode || "").trim();
        if (!raw) {
          uni.showToast({ title: "请扫描单据二维码", icon: "none" });
          return;
        }
        busy.value = true;
        uni.showLoading({ title: "打开中...", mask: true });
        try {
          let billNo = raw;
          try {
            const res = await resolveNoticeBarcode(billType.value, raw);
            if (res == null ? void 0 : res.billNo) billNo = String(res.billNo).trim();
          } catch (e) {
            formatAppLog("warn", "at pages/notice/list.vue:95", "resolveNoticeBarcode failed", e);
          }
          billNo = String(billNo || "").trim();
          if (!billNo) {
            uni.showToast({ title: "无法识别单据号", icon: "none" });
            return;
          }
          if (billNo.startsWith("{") || billNo.includes("://") || billNo.length > 64) {
            uni.showToast({ title: "无法识别单据号，请重扫", icon: "none", duration: 2500 });
            return;
          }
          keyword.value = billNo;
          openBill({ billNo });
        } finally {
          uni.hideLoading();
          busy.value = false;
          refocusScanInput(scanInputRef, 300);
        }
      }
      function onScan(barcode) {
        openByBarcode(barcode);
      }
      function onSearch(val) {
        const raw = (val || keyword.value || "").trim();
        if (!raw) return;
        openByBarcode(raw);
      }
      function openBill(item) {
        if (!(item == null ? void 0 : item.billNo)) return;
        uni.navigateTo({
          url: `/pages/notice/scan?billType=${encodeURIComponent(billType.value)}&billNo=${encodeURIComponent(item.billNo)}&direction=${direction.value}`
        });
      }
      onLoad((options) => {
        billType.value = (options == null ? void 0 : options.billType) || "PURCHASE_RECEIVE";
        direction.value = (options == null ? void 0 : options.direction) || getNoticeBillType(billType.value).direction;
        uni.setNavigationBarTitle({ title: getNoticeBillType(billType.value).label });
      });
      onShow(() => loadListOnShow());
      vue.onMounted(() => refocusScanInput(scanInputRef, 500));
      const __returned__ = { billType, direction, scanInputRef, busy, alive, refocusScanInput, loading, keyword, notices, typeConfig, loadListOnShow, statusLabel, statusClass, openByBarcode, onScan, onSearch, openBill, ref: vue.ref, onMounted: vue.onMounted, get onLoad() {
        return onLoad;
      }, get onShow() {
        return onShow;
      }, ScanSearchBar, get resolveNoticeBarcode() {
        return resolveNoticeBarcode;
      }, get useNoticeBillList() {
        return useNoticeBillList;
      }, get usePageAlive() {
        return usePageAlive;
      }, get getNoticeBillType() {
        return getNoticeBillType;
      }, get formatMaterialLineCount() {
        return formatMaterialLineCount;
      } };
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  };
  function _sfc_render$w(_ctx, _cache, $props, $setup, $data, $options) {
    return vue.openBlock(), vue.createElementBlock("view", { class: "page" }, [
      vue.createElementVNode("view", { class: "search-top" }, [
        vue.createVNode($setup["ScanSearchBar"], {
          ref: "scanInputRef",
          modelValue: $setup.keyword,
          "onUpdate:modelValue": _cache[0] || (_cache[0] = ($event) => $setup.keyword = $event),
          disabled: $setup.busy,
          placeholder: $setup.typeConfig.searchPlaceholder,
          "action-text": "打开",
          onScan: $setup.onScan,
          onSearch: $setup.onSearch
        }, null, 8, ["modelValue", "disabled", "placeholder"])
      ]),
      vue.createElementVNode("scroll-view", {
        class: "list-scroll",
        "scroll-y": "",
        "show-scrollbar": false
      }, [
        (vue.openBlock(true), vue.createElementBlock(
          vue.Fragment,
          null,
          vue.renderList($setup.notices, (item, index) => {
            return vue.openBlock(), vue.createElementBlock("view", {
              key: item.billNo || "row-" + index,
              class: "bill-row",
              onClick: ($event) => $setup.openBill(item)
            }, [
              vue.createElementVNode("view", { class: "row-main" }, [
                vue.createElementVNode(
                  "text",
                  { class: "bill-no" },
                  vue.toDisplayString(item.billNo || "（单号缺失）"),
                  1
                  /* TEXT */
                ),
                vue.createElementVNode(
                  "text",
                  { class: "bill-supplier" },
                  vue.toDisplayString(item.supplierName || item.supplierCode || "-"),
                  1
                  /* TEXT */
                ),
                vue.createElementVNode("text", { class: "bill-meta" }, [
                  $setup.formatMaterialLineCount(item) ? (vue.openBlock(), vue.createElementBlock(
                    "text",
                    {
                      key: 0,
                      class: "bill-lines"
                    },
                    vue.toDisplayString($setup.formatMaterialLineCount(item)),
                    1
                    /* TEXT */
                  )) : vue.createCommentVNode("v-if", true),
                  item.inProgress ? (vue.openBlock(), vue.createElementBlock(
                    "text",
                    { key: 1 },
                    " · 已勾 " + vue.toDisplayString(item.checkedLines || 0),
                    1
                    /* TEXT */
                  )) : vue.createCommentVNode("v-if", true),
                  item.locked && item.lockUserName ? (vue.openBlock(), vue.createElementBlock(
                    "text",
                    {
                      key: 2,
                      class: "bill-lock"
                    },
                    " · " + vue.toDisplayString(item.lockUserName) + "操作中",
                    1
                    /* TEXT */
                  )) : vue.createCommentVNode("v-if", true)
                ])
              ]),
              vue.createElementVNode("view", { class: "row-side" }, [
                vue.createElementVNode(
                  "text",
                  {
                    class: vue.normalizeClass(["status-tag", $setup.statusClass(item)])
                  },
                  vue.toDisplayString($setup.statusLabel(item)),
                  3
                  /* TEXT, CLASS */
                ),
                vue.createElementVNode("text", { class: "arrow" }, "›")
              ])
            ], 8, ["onClick"]);
          }),
          128
          /* KEYED_FRAGMENT */
        )),
        !$setup.notices.length && !$setup.loading ? (vue.openBlock(), vue.createElementBlock("view", {
          key: 0,
          class: "empty"
        }, [
          vue.createElementVNode("text", { class: "empty-icon" }, "📋"),
          vue.createElementVNode(
            "text",
            { class: "empty-text" },
            "暂无" + vue.toDisplayString($setup.typeConfig.label),
            1
            /* TEXT */
          ),
          vue.createElementVNode("text", { class: "empty-hint" }, "可直接扫描单据二维码进入明细")
        ])) : vue.createCommentVNode("v-if", true),
        $setup.loading && !$setup.notices.length ? (vue.openBlock(), vue.createElementBlock("view", {
          key: 1,
          class: "loading-tip"
        }, "加载中...")) : $setup.notices.length ? (vue.openBlock(), vue.createElementBlock(
          "view",
          {
            key: 2,
            class: "loading-tip end-tip"
          },
          "共 " + vue.toDisplayString($setup.notices.length) + " 条",
          1
          /* TEXT */
        )) : vue.createCommentVNode("v-if", true)
      ])
    ]);
  }
  const PagesNoticeList = /* @__PURE__ */ _export_sfc(_sfc_main$x, [["render", _sfc_render$w], ["__scopeId", "data-v-0d535122"], ["__file", "D:/AAA/WMS/wms-pda/src/pages/notice/list.vue"]]);
  function useNoticeBillScan(billTypeRef, billNoRef) {
    const loading = vue.ref(false);
    const submitting = vue.ref(false);
    const detail = vue.ref(null);
    const lines = vue.ref([]);
    const lastHighlightLineNo = vue.ref(null);
    const billLock = useBillExclusiveLock({
      heartbeat: () => heartbeatNoticeBillLock(billTypeRef.value, billNoRef.value),
      release: () => releaseNoticeBillLock(billTypeRef.value, billNoRef.value)
    });
    const typeConfig = vue.computed(() => getNoticeBillType(billTypeRef.value));
    const isInbound = vue.computed(() => typeConfig.value.direction === "INBOUND");
    function hasPendingSubmit(line) {
      if (!(line == null ? void 0 : line.checked)) return false;
      if ((Number(line.pendingSubmitQty) || 0) > 0) return true;
      return (Number(line.pendingSubmitAuxQty) || 0) > 0;
    }
    const checkedCount = vue.computed(
      () => lines.value.filter((l) => l.checked).length
    );
    const submitableCount = vue.computed(
      () => lines.value.filter((l) => hasPendingSubmit(l)).length
    );
    function handleLockDenied(e) {
      billLock.stop();
      const msg = (e == null ? void 0 : e.message) || "单据正被其他人操作";
      uni.showToast({ title: msg, icon: "none", duration: 2500 });
      setTimeout(() => uni.navigateBack({ fail: () => {
      } }), 400);
    }
    async function loadDetail() {
      if (!billTypeRef.value || !billNoRef.value) return null;
      loading.value = true;
      try {
        const data = await getNoticeBillDetail(billTypeRef.value, billNoRef.value);
        detail.value = data;
        lines.value = (data.lines || []).map(normalizeLine);
        billLock.start();
        return data;
      } catch (e) {
        if (isBillLockedError(e)) {
          handleLockDenied(e);
          return null;
        }
        uni.showToast({ title: (e == null ? void 0 : e.message) || "加载明细失败", icon: "none" });
        return null;
      } finally {
        loading.value = false;
      }
    }
    function normalizeLine(line) {
      if (!line) return line;
      return {
        ...line,
        checked: line.checked === true || line.checked === 1,
        pendingSubmitQty: line.pendingSubmitQty ?? 0,
        pendingSubmitAuxQty: line.pendingSubmitAuxQty ?? 0
      };
    }
    function formatScanError(e) {
      var _a, _b;
      const type = ((_a = e == null ? void 0 : e.data) == null ? void 0 : _a.errorType) || (e == null ? void 0 : e.errorType);
      const msg = (e == null ? void 0 : e.message) || ((_b = e == null ? void 0 : e.data) == null ? void 0 : _b.message);
      if (type === "BARCODE_EMPTY" || type === "BARCODE_PARSE_FAILED") {
        return msg || "条码格式错误，请扫描物料标签二维码";
      }
      if (type === "MATERIAL_NOT_ON_BILL") {
        return msg || "该物料不在本单据中";
      }
      if (type === "LINE_ALREADY_FULL") {
        return msg || "该物料已收满";
      }
      if (type === "BILL_LINE_NOT_SYNCED" || type === "BILL_LINES_NOT_READY") {
        return msg || "单据明细未就绪，请返回后重新进入";
      }
      if (type === "NO_STOCK") {
        return msg || "未找到可出库库存";
      }
      return msg || "扫描失败";
    }
    function mergeLine(updated) {
      const normalized = normalizeLine(updated);
      const idx = lines.value.findIndex((l) => l.lineNo === normalized.lineNo);
      if (idx >= 0) {
        lines.value[idx] = { ...lines.value[idx], ...normalized };
      }
      if (detail.value) {
        detail.value.checkedLines = lines.value.filter((l) => l.checked).length;
      }
    }
    async function handleScan(barcode) {
      if (!(barcode == null ? void 0 : barcode.trim())) return null;
      if (submitting.value) {
        uni.showToast({ title: "正在提交，请稍候", icon: "none" });
        return null;
      }
      if (loading.value) {
        uni.showToast({ title: "单据加载中，请稍后再扫", icon: "none" });
        return null;
      }
      try {
        const updated = await scanNoticeLine(billTypeRef.value, billNoRef.value, barcode.trim());
        lastHighlightLineNo.value = updated.lineNo;
        mergeLine(updated);
        if (detail.value) {
          detail.value.scanStatus = detail.value.scanStatus === "COMPLETED" ? detail.value.scanStatus : "SCANNING";
        }
        uni.showToast({ title: "扫描成功", icon: "success", duration: 800 });
        return updated;
      } catch (e) {
        if (isBillLockedError(e)) {
          handleLockDenied(e);
          return null;
        }
        uni.showToast({ title: formatScanError(e), icon: "none", duration: 2500 });
        return null;
      }
    }
    async function toggleCheck(lineNo, checked) {
      try {
        const updated = await toggleNoticeLine(billTypeRef.value, billNoRef.value, lineNo, checked);
        mergeLine(updated);
      } catch (e) {
        uni.showToast({ title: (e == null ? void 0 : e.message) || "操作失败", icon: "none" });
      }
    }
    async function updateQty(lineNo, qty, auxQty) {
      try {
        const updated = await updateNoticeLineQty(billTypeRef.value, billNoRef.value, lineNo, qty, auxQty);
        mergeLine(updated);
        return true;
      } catch (e) {
        uni.showToast({ title: (e == null ? void 0 : e.message) || "更新数量失败", icon: "none" });
        return false;
      }
    }
    async function submit(getWarehousePayload) {
      var _a, _b, _c;
      if (submitting.value) return null;
      if (!submitableCount.value) {
        uni.showToast({ title: "请先扫码或手动填写数量后再提交", icon: "none" });
        return null;
      }
      submitting.value = true;
      try {
        const wh = typeof getWarehousePayload === "function" ? getWarehousePayload() : {};
        const manual = isInbound.value && (wh == null ? void 0 : wh.autoAssignWarehouse) === false;
        const result = await submitNoticeBill(billTypeRef.value, billNoRef.value, {
          supplierCode: (_a = detail.value) == null ? void 0 : _a.supplierCode,
          supplierName: (_b = detail.value) == null ? void 0 : _b.supplierName,
          autoAssignWarehouse: isInbound.value ? !manual : void 0,
          warehouseCode: manual ? wh == null ? void 0 : wh.warehouseCode : void 0,
          erpWarehouseCode: manual ? (wh == null ? void 0 : wh.erpWarehouseCode) || (wh == null ? void 0 : wh.warehouseCode) : (_c = detail.value) == null ? void 0 : _c.erpWarehouseCode,
          autoAllocateLocation: isInbound.value ? !!(wh == null ? void 0 : wh.autoAllocateLocation) : void 0,
          locationCode: isInbound.value ? (wh == null ? void 0 : wh.locationCode) || (wh == null ? void 0 : wh.targetLocation) || void 0 : void 0
        });
        const feedback = await handleErpSubmitResult(result);
        if (!feedback.ok) {
          await loadDetail();
          return null;
        }
        const auditExistingTypes = /* @__PURE__ */ new Set([
          "PRODUCTION_RET_STOCK",
          "OTHER_IN",
          "OTHER_OUT",
          "SALES_DELIVERY",
          "PURCHASE_RETURN"
        ]);
        if (auditExistingTypes.has(billTypeRef.value)) {
          await billLock.releaseLock();
          setTimeout(() => uni.navigateBack(), 400);
          return result;
        }
        await loadDetail();
        if (isNoticeBillCompleted(detail.value)) {
          await billLock.releaseLock();
          setTimeout(() => uni.navigateBack(), 400);
        }
        return result;
      } catch (e) {
        if (isBillLockedError(e)) {
          handleLockDenied(e);
          return null;
        }
        await alertErpSubmitFailed(formatErpSubmitError(e));
        await loadDetail();
        return null;
      } finally {
        submitting.value = false;
      }
    }
    function formatQty$1(v, unitCode) {
      return formatQty(v, unitCode);
    }
    function rowClass(line) {
      const submitted = Number(line.submittedQty) || 0;
      const plan = Number(line.planQty) || 0;
      const classes = [];
      if (line.checked) classes.push("checked");
      if (line.lineNo === lastHighlightLineNo.value) classes.push("flash");
      if (submitted > 0 && submitted < plan) classes.push("partial");
      if (plan > 0 && submitted >= plan) classes.push("done");
      return classes.join(" ");
    }
    return {
      loading,
      submitting,
      detail,
      lines,
      typeConfig,
      isInbound,
      checkedCount,
      submitableCount,
      loadDetail,
      handleScan,
      toggleCheck,
      updateQty,
      formatQty: formatQty$1,
      submit,
      rowClass,
      lastHighlightLineNo,
      isLabelScanned
    };
  }
  const _sfc_main$w = {
    __name: "scan",
    setup(__props, { expose: __expose }) {
      __expose();
      const billType = vue.ref("PURCHASE_RECEIVE");
      const billNo = vue.ref("");
      const scanInputRef = vue.ref(null);
      const warehousePickerRef = vue.ref(null);
      const locationPickerRef = vue.ref(null);
      const warehousePayload = vue.ref({ autoAssignWarehouse: true });
      const locationPayload = vue.ref({ autoAllocateLocation: false });
      const qtyDrafts = vue.reactive({});
      const auxQtyDrafts = vue.reactive({});
      const { alive, refocusScanInput } = usePageAlive();
      const {
        loading,
        submitting,
        detail,
        lines,
        isInbound,
        checkedCount,
        submitableCount,
        loadDetail,
        handleScan,
        toggleCheck,
        updateQty,
        formatQty: formatQty2,
        submit,
        rowClass,
        isLabelScanned: isLabelScanned2
      } = useNoticeBillScan(billType, billNo);
      const submitLabel = vue.computed(() => isInbound.value ? "提交入库" : "提交出库");
      const suggestWarehouseCode = vue.computed(() => {
        var _a, _b, _c;
        if (!isInbound.value) return ((_a = detail.value) == null ? void 0 : _a.warehouseCode) || "";
        const pending = lines.value.find((l) => l.checked && (Number(l.pendingSubmitQty) || 0) > 0);
        if (pending == null ? void 0 : pending.erpStockCode) return pending.erpStockCode;
        return ((_b = detail.value) == null ? void 0 : _b.erpWarehouseCode) || ((_c = detail.value) == null ? void 0 : _c.warehouseCode) || "";
      });
      const resolvedWarehouseCode = vue.computed(() => {
        const wh = warehousePayload.value;
        if ((wh == null ? void 0 : wh.autoAssignWarehouse) === false && (wh == null ? void 0 : wh.warehouseCode)) {
          return wh.warehouseCode;
        }
        return suggestWarehouseCode.value || "";
      });
      const suggestMaterialCode = vue.computed(() => {
        var _a;
        const pending = lines.value.find((l) => l.checked && (Number(l.pendingSubmitQty) || 0) > 0);
        return (pending == null ? void 0 : pending.materialCode) || ((_a = lines.value[0]) == null ? void 0 : _a.materialCode) || "";
      });
      const suggestBatchNo = vue.computed(() => {
        const pending = lines.value.find((l) => l.checked && (Number(l.pendingSubmitQty) || 0) > 0);
        return (pending == null ? void 0 : pending.batchNo) || "";
      });
      function onWarehouseChange(payload) {
        warehousePayload.value = payload || { autoAssignWarehouse: true };
      }
      function onLocationChange(payload) {
        locationPayload.value = payload || { autoAllocateLocation: false };
      }
      function isDoneLine(line) {
        const pending = Number(line.pendingSubmitQty) || 0;
        const pendingAux = Number(line.pendingSubmitAuxQty) || 0;
        if (pending > 0 || pendingAux > 0) return false;
        const submitted = Number(line.submittedQty) || 0;
        const plan = Number(line.planQty) || 0;
        if (plan > 0 && submitted >= plan) return true;
        if (inputMapsToAux(line)) {
          const auxPlan = Number(line.planAuxQty) || 0;
          const auxSubmitted = Number(line.submittedAuxQty) || 0;
          if (plan <= 0 && auxPlan > 0 && auxSubmitted >= auxPlan) return true;
        }
        return false;
      }
      function canEditLine(line) {
        const remain = Number(inputRemainQty(line)) || 0;
        const pending = Number(inputPendingQty(line)) || 0;
        return remain > 0 || pending > 0;
      }
      function inputMapsToAux(line) {
        return !!((line == null ? void 0 : line.multiUnit) && (line == null ? void 0 : line.inputMapsToAux));
      }
      function inputPendingQty(line) {
        return inputMapsToAux(line) ? line.pendingSubmitAuxQty || 0 : line.pendingSubmitQty || 0;
      }
      function inputPlanQty(line) {
        return inputMapsToAux(line) ? line.planAuxQty : line.planQty;
      }
      function inputSubmittedQty(line) {
        return inputMapsToAux(line) ? line.submittedAuxQty : line.submittedQty;
      }
      function inputRemainQty(line) {
        return inputMapsToAux(line) ? line.remainAuxQty : line.remainQty;
      }
      function autoPendingQty(line) {
        return inputMapsToAux(line) ? line.pendingSubmitQty || 0 : line.pendingSubmitAuxQty || 0;
      }
      function autoPlanQty(line) {
        return inputMapsToAux(line) ? line.planQty : line.planAuxQty;
      }
      function autoSubmittedQty(line) {
        return inputMapsToAux(line) ? line.submittedQty : line.submittedAuxQty;
      }
      function autoRemainQty(line) {
        return inputMapsToAux(line) ? line.remainQty : line.remainAuxQty;
      }
      function convertByPlanRate(qty, fromPlan, toPlan) {
        const q = Number(qty) || 0;
        const from = Number(fromPlan) || 0;
        const to = Number(toPlan) || 0;
        if (q <= 0) return 0;
        if (from <= 0 || to <= 0) return q;
        const n = q * to / from;
        return Number.isInteger(n) ? n : Number(n.toFixed(6));
      }
      function pcsToKg(line, pcs) {
        if (inputMapsToAux(line)) {
          return convertByPlanRate(pcs, line.planAuxQty, line.planQty);
        }
        return convertByPlanRate(pcs, line.planQty, line.planAuxQty);
      }
      function kgToPcs(line, kg) {
        if (inputMapsToAux(line)) {
          return convertByPlanRate(kg, line.planQty, line.planAuxQty);
        }
        return convertByPlanRate(kg, line.planAuxQty, line.planQty);
      }
      function inputUnitOf(line) {
        return line.inputUnitCode || line.unitCode;
      }
      function autoUnitOf(line) {
        return line.autoUnitCode || line.auxUnitCode;
      }
      function syncQtyDrafts() {
        lines.value.forEach((line) => {
          qtyDrafts[line.lineNo] = formatQtyInput(inputPendingQty(line), inputUnitOf(line));
          if (line.multiUnit) {
            auxQtyDrafts[line.lineNo] = formatQtyInput(autoPendingQty(line), autoUnitOf(line));
          }
        });
      }
      function getQtyDraft(line) {
        const key = line.lineNo;
        if (qtyDrafts[key] === void 0 || qtyDrafts[key] === null) {
          return formatQtyInput(inputPendingQty(line), inputUnitOf(line));
        }
        return qtyDrafts[key];
      }
      function getAuxQtyDraft(line) {
        const key = line.lineNo;
        if (auxQtyDrafts[key] === void 0 || auxQtyDrafts[key] === null) {
          return formatQtyInput(autoPendingQty(line), autoUnitOf(line));
        }
        return auxQtyDrafts[key];
      }
      function onQtyInput(line, e) {
        const raw = sanitizeDecimalInput(e.detail.value, qtyDecimalScale(inputUnitOf(line)));
        qtyDrafts[line.lineNo] = raw;
        if (line.multiUnit) {
          const pcs = Number(raw || 0);
          auxQtyDrafts[line.lineNo] = formatQtyInput(
            Number.isNaN(pcs) || pcs < 0 ? 0 : pcsToKg(line, pcs),
            autoUnitOf(line)
          );
        }
      }
      function onAuxQtyInput(line, e) {
        auxQtyDrafts[line.lineNo] = sanitizeDecimalInput(e.detail.value, qtyDecimalScale(autoUnitOf(line)));
      }
      async function onQtyBlur(line) {
        const pcs = Number(qtyDrafts[line.lineNo] || 0);
        if (Number.isNaN(pcs) || pcs < 0) return;
        let stockQty = pcs;
        let auxQty;
        if (line.multiUnit) {
          const kg = pcsToKg(line, pcs);
          auxQtyDrafts[line.lineNo] = formatQtyInput(kg, autoUnitOf(line));
          if (inputMapsToAux(line)) {
            stockQty = kg;
            auxQty = pcs;
          } else {
            stockQty = pcs;
            auxQty = kg;
          }
        }
        const ok = await updateQty(line.lineNo, stockQty, auxQty);
        if (ok) syncQtyDrafts();
      }
      async function onAuxQtyBlur(line) {
        const kg = Number(auxQtyDrafts[line.lineNo] || 0);
        if (Number.isNaN(kg) || kg < 0) return;
        const pcs = kgToPcs(line, kg);
        qtyDrafts[line.lineNo] = formatQtyInput(pcs, inputUnitOf(line));
        let stockQty = pcs;
        let auxQty = kg;
        if (inputMapsToAux(line)) {
          stockQty = kg;
          auxQty = pcs;
        } else {
          stockQty = pcs;
          auxQty = kg;
        }
        const ok = await updateQty(line.lineNo, stockQty, auxQty);
        if (ok) syncQtyDrafts();
      }
      async function onScan(barcode) {
        if (!alive.value) return;
        await handleScan(barcode);
        syncQtyDrafts();
        refocusScanInput(scanInputRef, 300);
      }
      function onToggle(line) {
        toggleCheck(line.lineNo, !line.checked);
      }
      function onRowTap() {
      }
      async function onSubmit() {
        const ok = await submit(() => {
          var _a, _b, _c, _d;
          return {
            ...((_b = (_a = warehousePickerRef.value) == null ? void 0 : _a.getPayload) == null ? void 0 : _b.call(_a)) || warehousePayload.value,
            ...((_d = (_c = locationPickerRef.value) == null ? void 0 : _c.getPayload) == null ? void 0 : _d.call(_c)) || locationPayload.value
          };
        });
        if (ok) syncQtyDrafts();
      }
      onLoad((options) => {
        billType.value = (options == null ? void 0 : options.billType) || "PURCHASE_RECEIVE";
        billNo.value = decodeURIComponent((options == null ? void 0 : options.billNo) || "");
        uni.setNavigationBarTitle({ title: getNoticeBillType(billType.value).label + " · 扫码" });
      });
      onShow(async () => {
        if (billNo.value) {
          await loadDetail();
          syncQtyDrafts();
        }
        refocusScanInput(scanInputRef, 400);
      });
      const __returned__ = { billType, billNo, scanInputRef, warehousePickerRef, locationPickerRef, warehousePayload, locationPayload, qtyDrafts, auxQtyDrafts, alive, refocusScanInput, loading, submitting, detail, lines, isInbound, checkedCount, submitableCount, loadDetail, handleScan, toggleCheck, updateQty, formatQty: formatQty2, submit, rowClass, isLabelScanned: isLabelScanned2, submitLabel, suggestWarehouseCode, resolvedWarehouseCode, suggestMaterialCode, suggestBatchNo, onWarehouseChange, onLocationChange, isDoneLine, canEditLine, inputMapsToAux, inputPendingQty, inputPlanQty, inputSubmittedQty, inputRemainQty, autoPendingQty, autoPlanQty, autoSubmittedQty, autoRemainQty, convertByPlanRate, pcsToKg, kgToPcs, inputUnitOf, autoUnitOf, syncQtyDrafts, getQtyDraft, getAuxQtyDraft, onQtyInput, onAuxQtyInput, onQtyBlur, onAuxQtyBlur, onScan, onToggle, onRowTap, onSubmit, ref: vue.ref, computed: vue.computed, reactive: vue.reactive, get onLoad() {
        return onLoad;
      }, get onShow() {
        return onShow;
      }, CompactScanBox, WarehousePicker, LocationPicker, get useNoticeBillScan() {
        return useNoticeBillScan;
      }, get usePageAlive() {
        return usePageAlive;
      }, get getNoticeBillType() {
        return getNoticeBillType;
      }, get sanitizeDecimalInput() {
        return sanitizeDecimalInput;
      }, get qtyDecimalScale() {
        return qtyDecimalScale;
      }, get formatQtyInput() {
        return formatQtyInput;
      } };
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  };
  function _sfc_render$v(_ctx, _cache, $props, $setup, $data, $options) {
    return vue.openBlock(), vue.createElementBlock("view", { class: "page" }, [
      vue.createElementVNode("view", { class: "scan-top" }, [
        vue.createVNode($setup["CompactScanBox"], {
          ref: "scanInputRef",
          disabled: $setup.loading || $setup.submitting,
          onScan: $setup.onScan
        }, null, 8, ["disabled"])
      ]),
      $setup.detail ? (vue.openBlock(), vue.createElementBlock("view", {
        key: 0,
        class: "order-bar"
      }, [
        vue.createElementVNode("view", { class: "order-info" }, [
          vue.createElementVNode(
            "text",
            { class: "order-no" },
            vue.toDisplayString($setup.detail.billNo),
            1
            /* TEXT */
          ),
          vue.createElementVNode(
            "text",
            { class: "order-sub" },
            vue.toDisplayString($setup.detail.supplierName || $setup.detail.billTypeLabel || "-"),
            1
            /* TEXT */
          )
        ]),
        vue.createElementVNode("view", { class: "order-stat-wrap" }, [
          vue.createElementVNode(
            "text",
            { class: "order-stat" },
            vue.toDisplayString($setup.checkedCount) + "/" + vue.toDisplayString($setup.lines.length) + " 已勾",
            1
            /* TEXT */
          )
        ])
      ])) : vue.createCommentVNode("v-if", true),
      vue.createElementVNode("scroll-view", {
        class: "list-scroll",
        "scroll-y": "",
        "show-scrollbar": false
      }, [
        (vue.openBlock(true), vue.createElementBlock(
          vue.Fragment,
          null,
          vue.renderList($setup.lines, (line) => {
            var _a;
            return vue.openBlock(), vue.createElementBlock("view", {
              key: line.lineNo,
              class: vue.normalizeClass(["mat-row", $setup.rowClass(line)]),
              onClick: ($event) => $setup.onRowTap(line)
            }, [
              vue.createElementVNode("view", { class: "row-header" }, [
                vue.createElementVNode("view", {
                  class: "check-box",
                  onClick: vue.withModifiers(($event) => $setup.onToggle(line), ["stop"])
                }, [
                  vue.createElementVNode(
                    "view",
                    {
                      class: vue.normalizeClass(["check-inner", line.checked && "on"])
                    },
                    [
                      line.checked ? (vue.openBlock(), vue.createElementBlock("text", {
                        key: 0,
                        class: "check-mark"
                      }, "✓")) : vue.createCommentVNode("v-if", true)
                    ],
                    2
                    /* CLASS */
                  )
                ], 8, ["onClick"]),
                vue.createElementVNode("view", { class: "row-main" }, [
                  vue.createElementVNode(
                    "text",
                    { class: "mat-code" },
                    vue.toDisplayString(line.materialCode),
                    1
                    /* TEXT */
                  ),
                  vue.createElementVNode(
                    "text",
                    { class: "mat-name" },
                    vue.toDisplayString(line.materialName || "-"),
                    1
                    /* TEXT */
                  ),
                  vue.createElementVNode(
                    "text",
                    { class: "mat-spec" },
                    "规格 " + vue.toDisplayString(line.specification || "-"),
                    1
                    /* TEXT */
                  ),
                  $setup.isInbound ? (vue.openBlock(), vue.createElementBlock(
                    "text",
                    {
                      key: 0,
                      class: "mat-wh"
                    },
                    "仓库 " + vue.toDisplayString(line.erpStockCode || ((_a = $setup.detail) == null ? void 0 : _a.warehouseCode) || "-"),
                    1
                    /* TEXT */
                  )) : vue.createCommentVNode("v-if", true)
                ])
              ]),
              vue.createElementVNode("view", {
                class: "qty-panel",
                onClick: _cache[0] || (_cache[0] = vue.withModifiers(() => {
                }, ["stop"]))
              }, [
                vue.createElementVNode("view", { class: "qty-grid" }, [
                  vue.createElementVNode("view", { class: "qty-cell" }, [
                    vue.createElementVNode("text", { class: "qty-label" }, "计划"),
                    vue.createElementVNode(
                      "text",
                      { class: "qty-value" },
                      vue.toDisplayString($setup.formatQty($setup.inputPlanQty(line), line.inputUnitCode || line.unitCode)),
                      1
                      /* TEXT */
                    )
                  ]),
                  vue.createElementVNode("view", { class: "qty-cell" }, [
                    vue.createElementVNode("text", { class: "qty-label" }, "已处理"),
                    vue.createElementVNode(
                      "text",
                      { class: "qty-value submitted" },
                      vue.toDisplayString($setup.formatQty($setup.inputSubmittedQty(line), line.inputUnitCode || line.unitCode)),
                      1
                      /* TEXT */
                    )
                  ]),
                  vue.createElementVNode("view", { class: "qty-cell" }, [
                    vue.createElementVNode("text", { class: "qty-label" }, "可处理"),
                    vue.createElementVNode(
                      "text",
                      { class: "qty-value remain" },
                      vue.toDisplayString($setup.formatQty($setup.inputRemainQty(line), line.inputUnitCode || line.unitCode)),
                      1
                      /* TEXT */
                    )
                  ]),
                  vue.createElementVNode("view", { class: "qty-cell" }, [
                    vue.createElementVNode("text", { class: "qty-label" }, "单位"),
                    vue.createElementVNode(
                      "text",
                      { class: "qty-value unit" },
                      vue.toDisplayString(line.inputUnitCode || line.unitCode || "PCS"),
                      1
                      /* TEXT */
                    )
                  ])
                ]),
                line.multiUnit ? (vue.openBlock(), vue.createElementBlock("view", {
                  key: 0,
                  class: "qty-grid aux-grid"
                }, [
                  vue.createElementVNode("view", { class: "qty-cell" }, [
                    vue.createElementVNode(
                      "text",
                      { class: "qty-label" },
                      "计划(" + vue.toDisplayString(line.autoUnitCode || line.auxUnitCode) + ")",
                      1
                      /* TEXT */
                    ),
                    vue.createElementVNode(
                      "text",
                      { class: "qty-value" },
                      vue.toDisplayString($setup.formatQty($setup.autoPlanQty(line), line.autoUnitCode || line.auxUnitCode)),
                      1
                      /* TEXT */
                    )
                  ]),
                  vue.createElementVNode("view", { class: "qty-cell" }, [
                    vue.createElementVNode("text", { class: "qty-label" }, "已处理"),
                    vue.createElementVNode(
                      "text",
                      { class: "qty-value submitted" },
                      vue.toDisplayString($setup.formatQty($setup.autoSubmittedQty(line), line.autoUnitCode || line.auxUnitCode)),
                      1
                      /* TEXT */
                    )
                  ]),
                  vue.createElementVNode("view", { class: "qty-cell" }, [
                    vue.createElementVNode("text", { class: "qty-label" }, "可处理"),
                    vue.createElementVNode(
                      "text",
                      { class: "qty-value remain" },
                      vue.toDisplayString($setup.formatQty($setup.autoRemainQty(line), line.autoUnitCode || line.auxUnitCode)),
                      1
                      /* TEXT */
                    )
                  ]),
                  vue.createElementVNode("view", { class: "qty-cell" }, [
                    vue.createElementVNode("text", { class: "qty-label" }, "单位"),
                    vue.createElementVNode(
                      "text",
                      { class: "qty-value unit" },
                      vue.toDisplayString(line.autoUnitCode || line.auxUnitCode),
                      1
                      /* TEXT */
                    )
                  ])
                ])) : vue.createCommentVNode("v-if", true),
                $setup.canEditLine(line) ? (vue.openBlock(), vue.createElementBlock("view", {
                  key: 1,
                  class: "qty-edit"
                }, [
                  vue.createElementVNode(
                    "text",
                    { class: "qty-edit-label" },
                    "本次(" + vue.toDisplayString(line.inputUnitCode || line.unitCode || "PCS") + ")",
                    1
                    /* TEXT */
                  ),
                  vue.createElementVNode("input", {
                    class: "qty-input",
                    type: "text",
                    inputmode: "decimal",
                    value: $setup.getQtyDraft(line),
                    onInput: ($event) => $setup.onQtyInput(line, $event),
                    onBlur: ($event) => $setup.onQtyBlur(line)
                  }, null, 40, ["value", "onInput", "onBlur"])
                ])) : vue.createCommentVNode("v-if", true),
                $setup.canEditLine(line) && line.multiUnit ? (vue.openBlock(), vue.createElementBlock("view", {
                  key: 2,
                  class: "qty-edit"
                }, [
                  vue.createElementVNode(
                    "text",
                    { class: "qty-edit-label" },
                    "换算KG(" + vue.toDisplayString(line.autoUnitCode || line.auxUnitCode) + ")",
                    1
                    /* TEXT */
                  ),
                  vue.createElementVNode("input", {
                    class: "qty-input",
                    type: "text",
                    inputmode: "decimal",
                    value: $setup.getAuxQtyDraft(line),
                    onInput: ($event) => $setup.onAuxQtyInput(line, $event),
                    onBlur: ($event) => $setup.onAuxQtyBlur(line)
                  }, null, 40, ["value", "onInput", "onBlur"])
                ])) : vue.createCommentVNode("v-if", true)
              ])
            ], 10, ["onClick"]);
          }),
          128
          /* KEYED_FRAGMENT */
        )),
        $setup.isInbound && $setup.detail ? (vue.openBlock(), vue.createElementBlock("view", {
          key: 0,
          class: "wh-section"
        }, [
          vue.createVNode($setup["WarehousePicker"], {
            ref: "warehousePickerRef",
            "suggest-code": $setup.suggestWarehouseCode,
            onChange: $setup.onWarehouseChange
          }, null, 8, ["suggest-code"]),
          vue.createVNode($setup["LocationPicker"], {
            ref: "locationPickerRef",
            "warehouse-code": $setup.resolvedWarehouseCode,
            "material-code": $setup.suggestMaterialCode,
            "batch-no": $setup.suggestBatchNo,
            theme: "light",
            onChange: $setup.onLocationChange
          }, null, 8, ["warehouse-code", "material-code", "batch-no"])
        ])) : vue.createCommentVNode("v-if", true),
        vue.createElementVNode("view", { class: "scroll-bottom-pad" })
      ]),
      vue.createElementVNode("view", { class: "footer" }, [
        vue.createElementVNode("button", {
          class: "submit-btn",
          type: "primary",
          loading: $setup.submitting,
          disabled: !$setup.submitableCount,
          onClick: $setup.onSubmit
        }, vue.toDisplayString($setup.submitLabel) + vue.toDisplayString($setup.submitableCount ? ` (${$setup.submitableCount})` : ""), 9, ["loading", "disabled"])
      ])
    ]);
  }
  const PagesNoticeScan = /* @__PURE__ */ _export_sfc(_sfc_main$w, [["render", _sfc_render$v], ["__scopeId", "data-v-b33616f0"], ["__file", "D:/AAA/WMS/wms-pda/src/pages/notice/scan.vue"]]);
  function recognizeBarcode(barcodeContent, warehouseCode) {
    return request({
      url: "/mobile/scan/recognize",
      method: "POST",
      data: { barcodeContent, warehouseCode }
    });
  }
  function scanInboundByBarcode(orderNo, data) {
    return request({
      url: `/mobile/scan/inbound/${orderNo}`,
      method: "POST",
      data: withDevice(data)
    });
  }
  function scanOutboundDirect(data) {
    return request({
      url: "/mobile/scan/outbound-direct",
      method: "POST",
      data: withDevice(data)
    });
  }
  function getMaterial(materialCode) {
    return request({ url: `/base/materials/${materialCode}` });
  }
  function parseInboundOrderNo(barcode) {
    const raw = (barcode || "").trim();
    if (!raw) return "";
    if (/^IN[:：]/i.test(raw)) return raw.replace(/^IN[:：]/i, "").trim().toUpperCase();
    const match = raw.match(/IN\d{6,}/i);
    if (match) return match[0].toUpperCase();
    return raw.toUpperCase();
  }
  function isInboundOrderBarcode(barcode) {
    const raw = (barcode || "").trim();
    if (!raw) return false;
    try {
      const json = JSON.parse(raw);
      if (json.orderNo || json.inboundOrderNo) return true;
      if (Array.isArray(json.lines) || Array.isArray(json.details)) return true;
    } catch {
    }
    if (/^IN[:：]/i.test(raw)) return true;
    return /^IN\d{6,}/i.test(parseInboundOrderNo(raw));
  }
  function parseInboundOrderQr(barcode) {
    const raw = (barcode || "").trim();
    if (!raw) return { type: "unknown", orderNo: "", lines: null, raw: "" };
    try {
      const json = JSON.parse(raw);
      const orderNo = (json.orderNo || json.inboundOrderNo || "").toString().trim();
      const lines = json.lines || json.details || json.materials || null;
      if (Array.isArray(lines) && lines.length) {
        return { type: "embedded", orderNo, lines, raw };
      }
      if (orderNo) {
        return { type: "orderNo", orderNo: parseInboundOrderNo(orderNo), lines: null, raw };
      }
    } catch {
    }
    if (isInboundOrderBarcode(raw)) {
      return { type: "orderNo", orderNo: parseInboundOrderNo(raw), lines: null, raw };
    }
    return { type: "unknown", orderNo: "", lines: null, raw };
  }
  const SCANNABLE_STATUS = ["INBOUND", "PENDING", "APPROVED", "OPEN", "PARTIAL"];
  function normalizeQty(val) {
    const n = Number(val);
    return Number.isFinite(n) ? n : 0;
  }
  function mapEmbeddedLine(raw, index) {
    return {
      lineNo: raw.lineNo ?? index + 1,
      materialCode: raw.materialCode || raw.code || "",
      materialName: raw.materialName || raw.name || "",
      specification: raw.specification || raw.spec || "",
      unitCode: raw.unitCode || raw.unit || "",
      orderQty: normalizeQty(raw.orderQty ?? raw.qty ?? raw.quantity),
      receivedQty: normalizeQty(raw.receivedQty ?? raw.received),
      batchNo: raw.batchNo || "",
      lineStatus: raw.lineStatus || "PENDING"
    };
  }
  function useInboundOrderScan() {
    const phase = vue.ref("scan_order");
    const processing = vue.ref(false);
    const inboundOrder = vue.ref(null);
    const lines = vue.ref([]);
    const lastHighlightLineNo = vue.ref(null);
    const totalCount = vue.computed(() => lines.value.length);
    const completedCount = vue.computed(
      () => lines.value.filter((l) => l.receivedQty >= l.orderQty && l.orderQty > 0).length
    );
    const allCompleted = vue.computed(
      () => totalCount.value > 0 && completedCount.value === totalCount.value
    );
    async function enrichLine(detail, index) {
      const orderQty = normalizeQty(detail.orderQty);
      const receivedQty = normalizeQty(detail.receivedQty);
      let specification = detail.specification || "";
      if (!specification && detail.materialCode) {
        try {
          const mat = await getMaterial(detail.materialCode);
          specification = mat.specification || "";
        } catch {
        }
      }
      return {
        lineNo: detail.lineNo ?? index + 1,
        materialCode: detail.materialCode || "",
        materialName: detail.materialName || "",
        specification,
        unitCode: detail.unitCode || "",
        orderQty,
        receivedQty,
        pendingQty: Math.max(0, orderQty - receivedQty),
        batchNo: detail.batchNo || "",
        lineStatus: detail.lineStatus || "PENDING"
      };
    }
    async function applyOrderData(order, rawLines) {
      if (!(order == null ? void 0 : order.orderNo) && !(rawLines == null ? void 0 : rawLines.length)) {
        throw new Error("入库单数据无效");
      }
      const status = order == null ? void 0 : order.status;
      if (status && !SCANNABLE_STATUS.includes(status)) {
        throw new Error(`入库单状态不可收货（${status}）`);
      }
      const enriched = [];
      for (let i = 0; i < rawLines.length; i++) {
        enriched.push(await enrichLine(rawLines[i], i));
      }
      if (!enriched.length) {
        throw new Error("该入库单无物料明细");
      }
      inboundOrder.value = order || { orderNo: "", warehouseCode: "WH01" };
      lines.value = enriched;
      phase.value = "receive_items";
      lastHighlightLineNo.value = null;
      return inboundOrder.value;
    }
    async function loadInboundOrder(orderNo) {
      const data = await getInboundOrder(orderNo);
      const order = {
        orderNo: data.orderNo,
        orderType: data.orderType,
        warehouseCode: data.warehouseCode,
        supplierCode: data.supplierCode,
        status: data.status
      };
      return applyOrderData(order, data.details || []);
    }
    async function loadFromQr(barcode) {
      const parsed = parseInboundOrderQr(barcode);
      if (parsed.type === "embedded") {
        const order = {
          orderNo: parsed.orderNo || "内嵌明细",
          warehouseCode: "WH01",
          status: "INBOUND"
        };
        const rawLines = parsed.lines.map(mapEmbeddedLine);
        await applyOrderData(order, rawLines);
        if (parsed.orderNo && /^IN\d/i.test(parsed.orderNo)) {
          try {
            await loadInboundOrder(parsed.orderNo);
          } catch {
          }
        }
        return inboundOrder.value;
      }
      if (parsed.type === "orderNo" && parsed.orderNo) {
        return loadInboundOrder(parsed.orderNo);
      }
      throw new Error("无法识别入库单二维码");
    }
    async function handleOrderScan(barcode) {
      processing.value = true;
      try {
        await loadFromQr(barcode);
        uni.showToast({ title: "明细已加载", icon: "success" });
        return true;
      } catch (e) {
        uni.showToast({ title: (e == null ? void 0 : e.message) || "加载入库单失败", icon: "none" });
        return false;
      } finally {
        processing.value = false;
      }
    }
    async function parseMaterialBarcode2(barcode) {
      var _a;
      const local = parseBarcodeLocal(barcode);
      const wh = (_a = inboundOrder.value) == null ? void 0 : _a.warehouseCode;
      try {
        const data = await recognizeBarcode(barcode, wh);
        return {
          materialCode: data.materialCode || local.materialCode,
          batchNo: data.batchNo || local.batchNo,
          materialName: data.materialName || "",
          specification: data.specification || "",
          parseFailed: !data.materialCode && !local.materialCode
        };
      } catch {
        return {
          materialCode: local.materialCode,
          batchNo: local.batchNo,
          materialName: "",
          specification: "",
          parseFailed: !local.materialCode
        };
      }
    }
    async function handleMaterialScan(barcode) {
      var _a;
      if (phase.value !== "receive_items" || !((_a = inboundOrder.value) == null ? void 0 : _a.orderNo)) {
        uni.showToast({ title: "请先扫描入库单二维码", icon: "none" });
        return false;
      }
      processing.value = true;
      lastHighlightLineNo.value = null;
      try {
        const parsed = await parseMaterialBarcode2(barcode);
        if (parsed.parseFailed || !parsed.materialCode) {
          uni.showToast({ title: "条码解析失败", icon: "none" });
          return false;
        }
        const target = lines.value.find(
          (l) => l.materialCode === parsed.materialCode && l.pendingQty > 0
        );
        if (!target) {
          const done = lines.value.find((l) => l.materialCode === parsed.materialCode);
          uni.showToast({
            title: done ? "该物料已收满" : "物料不在本单明细中",
            icon: "none"
          });
          return false;
        }
        const qty = Math.min(1, target.pendingQty);
        const result = await scanInboundByBarcode(inboundOrder.value.orderNo, {
          barcodeContent: barcode,
          quantity: qty,
          lineNo: target.lineNo
        });
        target.receivedQty = normalizeQty(result.receivedQty ?? target.receivedQty + qty);
        target.pendingQty = Math.max(0, target.orderQty - target.receivedQty);
        if (parsed.batchNo) target.batchNo = parsed.batchNo;
        if (parsed.materialName && !target.materialName) target.materialName = parsed.materialName;
        if (parsed.specification && !target.specification) target.specification = parsed.specification;
        lastHighlightLineNo.value = target.lineNo;
        uni.showToast({
          title: `✓ ${target.materialName || target.materialCode}`,
          icon: "success",
          duration: 800
        });
        return true;
      } catch (e) {
        uni.showToast({ title: (e == null ? void 0 : e.message) || "收货失败", icon: "none" });
        return false;
      } finally {
        processing.value = false;
      }
    }
    async function handleScan(barcode) {
      if (!(barcode == null ? void 0 : barcode.trim()) || processing.value) return;
      const raw = barcode.trim();
      if (phase.value === "scan_order" || isInboundOrderBarcode(raw)) {
        return handleOrderScan(raw);
      }
      return handleMaterialScan(raw);
    }
    async function finishInbound() {
      var _a;
      if (!((_a = inboundOrder.value) == null ? void 0 : _a.orderNo)) return false;
      if (!allCompleted.value) {
        uni.showModal({
          title: "尚未收满",
          content: `还有 ${totalCount.value - completedCount.value} 项未完成，是否仍要完结？`,
          success: async (res) => {
            if (res.confirm) await doComplete();
          }
        });
        return false;
      }
      return doComplete();
    }
    async function doComplete() {
      processing.value = true;
      try {
        await completeInbound(inboundOrder.value.orderNo);
        uni.showModal({
          title: "入库完成",
          content: `入库单 ${inboundOrder.value.orderNo} 已完结`,
          showCancel: false,
          success: () => reset()
        });
        return true;
      } catch (e) {
        uni.showToast({ title: (e == null ? void 0 : e.message) || "完结失败", icon: "none" });
        return false;
      } finally {
        processing.value = false;
      }
    }
    function reset() {
      phase.value = "scan_order";
      inboundOrder.value = null;
      lines.value = [];
      lastHighlightLineNo.value = null;
    }
    return {
      phase,
      processing,
      inboundOrder,
      lines,
      lastHighlightLineNo,
      totalCount,
      completedCount,
      allCompleted,
      handleScan,
      finishInbound,
      reset
    };
  }
  const _sfc_main$v = {
    __name: "direct",
    setup(__props, { expose: __expose }) {
      __expose();
      const scanInputRef = vue.ref(null);
      const warehouseCode = vue.ref("WH01");
      const { alive, refocusScanInput } = usePageAlive();
      const {
        phase,
        processing,
        inboundOrder,
        lines,
        lastHighlightLineNo,
        totalCount,
        completedCount,
        handleScan,
        finishInbound,
        reset
      } = useInboundOrderScan();
      const canFinish = vue.computed(() => {
        var _a;
        return /^IN\d/i.test(((_a = inboundOrder.value) == null ? void 0 : _a.orderNo) || "");
      });
      function rowClass(line) {
        if (line.lineNo === lastHighlightLineNo.value) return "flash";
        if (line.pendingQty <= 0) return "done";
        return "pending";
      }
      async function onScan(barcode) {
        if (!alive.value) return;
        await handleScan(barcode);
        if (!alive.value) return;
        refocusScanInput(scanInputRef, 300);
      }
      function onResetOrder() {
        uni.showModal({
          title: "重新扫单",
          content: "清空当前入库明细？",
          success: (res) => {
            if (res.confirm) reset();
          }
        });
      }
      async function onFinish() {
        await finishInbound();
      }
      onLoad(() => uni.setNavigationBarTitle({ title: "快速入库" }));
      let shownOnce = false;
      onShow(() => {
        if (!shownOnce) {
          shownOnce = true;
          refocusScanInput(scanInputRef, 500);
        }
      });
      const __returned__ = { scanInputRef, warehouseCode, alive, refocusScanInput, phase, processing, inboundOrder, lines, lastHighlightLineNo, totalCount, completedCount, handleScan, finishInbound, reset, canFinish, rowClass, onScan, onResetOrder, onFinish, get shownOnce() {
        return shownOnce;
      }, set shownOnce(v) {
        shownOnce = v;
      }, ref: vue.ref, computed: vue.computed, get onLoad() {
        return onLoad;
      }, get onShow() {
        return onShow;
      }, CompactScanBox, get useInboundOrderScan() {
        return useInboundOrderScan;
      }, get usePageAlive() {
        return usePageAlive;
      } };
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  };
  function _sfc_render$u(_ctx, _cache, $props, $setup, $data, $options) {
    return vue.openBlock(), vue.createElementBlock("view", { class: "page" }, [
      vue.createCommentVNode(" 顶部紧凑扫码区 "),
      vue.createElementVNode("view", { class: "scan-top" }, [
        vue.createVNode($setup["CompactScanBox"], {
          ref: "scanInputRef",
          disabled: $setup.processing,
          onScan: $setup.onScan
        }, null, 8, ["disabled"])
      ]),
      vue.createCommentVNode(" 下方可滚动物料列表 "),
      vue.createElementVNode("scroll-view", {
        class: "list-scroll",
        "scroll-y": "",
        "show-scrollbar": false
      }, [
        $setup.inboundOrder ? (vue.openBlock(), vue.createElementBlock("view", {
          key: 0,
          class: "order-bar"
        }, [
          vue.createElementVNode(
            "text",
            { class: "order-no" },
            vue.toDisplayString($setup.inboundOrder.orderNo),
            1
            /* TEXT */
          ),
          vue.createElementVNode("text", {
            class: "order-reset",
            onClick: $setup.onResetOrder
          }, "换单")
        ])) : vue.createCommentVNode("v-if", true),
        $setup.lines.length ? (vue.openBlock(), vue.createElementBlock("view", {
          key: 1,
          class: "list-head"
        }, [
          vue.createElementVNode(
            "text",
            { class: "list-count" },
            "共 " + vue.toDisplayString($setup.totalCount) + " 项",
            1
            /* TEXT */
          ),
          vue.createElementVNode(
            "text",
            { class: "list-progress" },
            vue.toDisplayString($setup.completedCount) + "/" + vue.toDisplayString($setup.totalCount) + " 已收",
            1
            /* TEXT */
          )
        ])) : vue.createCommentVNode("v-if", true),
        (vue.openBlock(true), vue.createElementBlock(
          vue.Fragment,
          null,
          vue.renderList($setup.lines, (line) => {
            return vue.openBlock(), vue.createElementBlock(
              "view",
              {
                key: line.lineNo,
                class: vue.normalizeClass(["mat-row", $setup.rowClass(line)])
              },
              [
                vue.createElementVNode("view", { class: "row-main" }, [
                  vue.createElementVNode(
                    "text",
                    { class: "mat-code" },
                    vue.toDisplayString(line.materialCode),
                    1
                    /* TEXT */
                  ),
                  vue.createElementVNode(
                    "text",
                    { class: "mat-name" },
                    vue.toDisplayString(line.materialName || "-"),
                    1
                    /* TEXT */
                  ),
                  vue.createElementVNode(
                    "text",
                    { class: "mat-spec" },
                    "规格 " + vue.toDisplayString(line.specification || "-"),
                    1
                    /* TEXT */
                  )
                ]),
                vue.createElementVNode("view", { class: "row-qty" }, [
                  vue.createElementVNode(
                    "text",
                    { class: "qty-num" },
                    vue.toDisplayString(line.receivedQty) + "/" + vue.toDisplayString(line.orderQty),
                    1
                    /* TEXT */
                  ),
                  vue.createElementVNode(
                    "text",
                    { class: "qty-unit" },
                    vue.toDisplayString(line.unitCode || ""),
                    1
                    /* TEXT */
                  )
                ])
              ],
              2
              /* CLASS */
            );
          }),
          128
          /* KEYED_FRAGMENT */
        )),
        !$setup.lines.length && !$setup.processing ? (vue.openBlock(), vue.createElementBlock("view", {
          key: 2,
          class: "list-empty"
        }, [
          vue.createElementVNode("text", { class: "empty-icon" }, "📋")
        ])) : vue.createCommentVNode("v-if", true),
        $setup.inboundOrder ? (vue.openBlock(), vue.createElementBlock("view", {
          key: 3,
          class: "order-wh"
        }, [
          vue.createElementVNode(
            "text",
            null,
            "入库仓库 " + vue.toDisplayString($setup.inboundOrder.warehouseCode || $setup.warehouseCode),
            1
            /* TEXT */
          )
        ])) : vue.createCommentVNode("v-if", true),
        vue.createElementVNode("view", { class: "scroll-bottom-pad" })
      ]),
      $setup.inboundOrder && $setup.phase === "receive_items" && $setup.canFinish ? (vue.openBlock(), vue.createElementBlock("view", {
        key: 0,
        class: "footer"
      }, [
        vue.createElementVNode("button", {
          class: "finish-btn",
          type: "primary",
          loading: $setup.processing,
          onClick: $setup.onFinish
        }, " 完成入库 ", 8, ["loading"])
      ])) : vue.createCommentVNode("v-if", true)
    ]);
  }
  const PagesInboundDirect = /* @__PURE__ */ _export_sfc(_sfc_main$v, [["render", _sfc_render$u], ["__scopeId", "data-v-c962e0f8"], ["__file", "D:/AAA/WMS/wms-pda/src/pages/inbound/direct.vue"]]);
  const _sfc_main$u = {
    __name: "ScanInput",
    props: {
      placeholder: { type: String, default: "扫描条码自动录入" },
      disabled: { type: Boolean, default: false },
      autoFocus: { type: Boolean, default: true }
    },
    emits: ["scan"],
    setup(__props, { expose: __expose, emit: __emit }) {
      const props = __props;
      const emit = __emit;
      const ready = vue.ref(false);
      let mounted = false;
      const localTimers = [];
      function safeTimeout(fn, delay) {
        const id = setTimeout(() => {
          const idx = localTimers.indexOf(id);
          if (idx >= 0) localTimers.splice(idx, 1);
          fn();
        }, delay);
        localTimers.push(id);
        return id;
      }
      function clearLocalTimers() {
        localTimers.forEach((id) => clearTimeout(id));
        localTimers.length = 0;
      }
      const {
        innerValue,
        focused,
        focusInput,
        focusInputOnce,
        resetInputState,
        onInput,
        onConfirm,
        onBlur,
        onFocus
      } = useScannerInput((code) => {
        emit("scan", code);
      });
      const statusText = vue.computed(() => {
        if (props.disabled) return "处理中，请稍候...";
        if (ready.value) return "扫码枪已就绪，扫描后自动录入";
        return "请对准条码扫描";
      });
      function handleFocus() {
        onFocus();
        ready.value = true;
      }
      function handleBlur() {
        onBlur();
        ready.value = false;
      }
      function clearInput() {
        resetInputState();
        focusInputOnce();
      }
      vue.watch(
        () => props.disabled,
        (v, oldV) => {
          if (oldV && !v && props.autoFocus) {
            safeTimeout(focusInputOnce, 200);
          }
        }
      );
      vue.onMounted(() => {
        if (props.autoFocus && !mounted) {
          mounted = true;
          safeTimeout(focusInputOnce, 400);
        }
      });
      vue.onActivated(() => {
        if (props.autoFocus && !props.disabled) {
          safeTimeout(focusInputOnce, 300);
        }
      });
      vue.onUnmounted(() => {
        clearLocalTimers();
      });
      __expose({
        focusInput: focusInputOnce,
        clear: resetInputState
      });
      const __returned__ = { props, emit, ready, get mounted() {
        return mounted;
      }, set mounted(v) {
        mounted = v;
      }, localTimers, safeTimeout, clearLocalTimers, innerValue, focused, focusInput, focusInputOnce, resetInputState, onInput, onConfirm, onBlur, onFocus, statusText, handleFocus, handleBlur, clearInput, ref: vue.ref, computed: vue.computed, watch: vue.watch, onMounted: vue.onMounted, onActivated: vue.onActivated, onUnmounted: vue.onUnmounted, get useScannerInput() {
        return useScannerInput;
      } };
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  };
  function _sfc_render$t(_ctx, _cache, $props, $setup, $data, $options) {
    return vue.openBlock(), vue.createElementBlock(
      "view",
      {
        class: vue.normalizeClass(["scan-bar", { disabled: $props.disabled }])
      },
      [
        vue.createElementVNode(
          "view",
          {
            class: vue.normalizeClass(["scan-frame", { active: $setup.focused && !$props.disabled, disabled: $props.disabled }])
          },
          [
            vue.createElementVNode("view", { class: "scan-icon-wrap" }, [
              vue.createElementVNode("text", { class: "scan-icon" }, "⌁")
            ]),
            vue.withDirectives(vue.createElementVNode("input", {
              ref: "inputRef",
              "onUpdate:modelValue": _cache[0] || (_cache[0] = ($event) => $setup.innerValue = $event),
              class: "scan-input",
              type: "text",
              focus: $setup.focused,
              disabled: $props.disabled,
              placeholder: $props.placeholder,
              "confirm-type": "done",
              "hold-keyboard": false,
              "adjust-position": false,
              "cursor-spacing": 0,
              onInput: _cache[1] || (_cache[1] = (...args) => $setup.onInput && $setup.onInput(...args)),
              onConfirm: _cache[2] || (_cache[2] = (...args) => $setup.onConfirm && $setup.onConfirm(...args)),
              onBlur: $setup.handleBlur,
              onFocus: $setup.handleFocus
            }, null, 40, ["focus", "disabled", "placeholder"]), [
              [vue.vModelText, $setup.innerValue]
            ]),
            $setup.innerValue && !$props.disabled ? (vue.openBlock(), vue.createElementBlock("view", {
              key: 0,
              class: "clear-btn",
              onClick: vue.withModifiers($setup.clearInput, ["stop"])
            }, "×")) : vue.createCommentVNode("v-if", true)
          ],
          2
          /* CLASS */
        ),
        vue.createElementVNode(
          "text",
          { class: "status-hint" },
          vue.toDisplayString($setup.statusText),
          1
          /* TEXT */
        )
      ],
      2
      /* CLASS */
    );
  }
  const ScanInput = /* @__PURE__ */ _export_sfc(_sfc_main$u, [["render", _sfc_render$t], ["__scopeId", "data-v-66a5eeaf"], ["__file", "D:/AAA/WMS/wms-pda/src/components/ScanInput.vue"]]);
  const OUTBOUND_SCAN_QTY = 1;
  function useScanOutbound() {
    const processing = vue.ref(false);
    const pendingConfirm = vue.ref(null);
    const scanLog = vue.ref([]);
    const scanCount = vue.ref(0);
    function addLog(entry) {
      scanLog.value.unshift({ ...entry, time: (/* @__PURE__ */ new Date()).toLocaleTimeString() });
      if (scanLog.value.length > 30) scanLog.value.pop();
    }
    function showError(message) {
      uni.showToast({ title: message, icon: "none", duration: 2500 });
      addLog({ ok: false, msg: message });
    }
    async function handleScan(barcode, warehouseCode) {
      if (!(barcode == null ? void 0 : barcode.trim()) || processing.value) return;
      processing.value = true;
      try {
        const preview = await scanOutboundDirect({
          barcodeContent: barcode.trim(),
          warehouseCode,
          quantity: OUTBOUND_SCAN_QTY,
          confirm: false
        });
        if (!preview.matched) {
          showError(preview.message || "未找到可用库存");
          return null;
        }
        pendingConfirm.value = { barcode: barcode.trim(), preview, warehouseCode };
        return preview;
      } catch (e) {
        showError((e == null ? void 0 : e.message) || "条码解析失败，请重新扫描");
        return null;
      } finally {
        processing.value = false;
      }
    }
    async function confirmOutbound() {
      if (!pendingConfirm.value || processing.value) return null;
      processing.value = true;
      try {
        const { barcode, preview, warehouseCode } = pendingConfirm.value;
        const result = await scanOutboundDirect({
          barcodeContent: barcode,
          warehouseCode,
          quantity: OUTBOUND_SCAN_QTY,
          sourceLocation: preview.sourceLocation || preview.recommendedLocation,
          confirm: true
        });
        scanCount.value += 1;
        const label = result.materialName || result.materialCode;
        addLog({
          ok: true,
          barcode,
          msg: `${label} -${result.quantity} @${result.sourceLocation}`
        });
        pendingConfirm.value = null;
        uni.showToast({ title: "出库成功", icon: "success", duration: 800 });
        return result;
      } catch (e) {
        showError((e == null ? void 0 : e.message) || "出库失败");
        return null;
      } finally {
        processing.value = false;
      }
    }
    function cancelConfirm() {
      pendingConfirm.value = null;
    }
    function clearLog() {
      scanLog.value = [];
    }
    return {
      processing,
      pendingConfirm,
      scanLog,
      scanCount,
      handleScan,
      confirmOutbound,
      cancelConfirm,
      clearLog
    };
  }
  const _sfc_main$t = {
    __name: "outbound",
    setup(__props, { expose: __expose }) {
      __expose();
      const scanInputRef = vue.ref(null);
      const warehouseCode = vue.ref("WH01");
      const { alive, refocusScanInput } = usePageAlive();
      const {
        processing,
        pendingConfirm,
        scanLog,
        scanCount,
        handleScan,
        confirmOutbound,
        cancelConfirm,
        clearLog
      } = useScanOutbound();
      async function onScan(barcode) {
        if (!alive.value) return;
        await handleScan(barcode, warehouseCode.value);
        if (!alive.value) return;
        if (!pendingConfirm.value) {
          refocusScanInput(scanInputRef, 300);
        }
      }
      async function onConfirm() {
        if (!alive.value) return;
        await confirmOutbound();
        if (!alive.value) return;
        refocusScanInput(scanInputRef, 300);
      }
      function onCancel() {
        cancelConfirm();
        refocusScanInput(scanInputRef, 300);
      }
      onLoad(() => uni.setNavigationBarTitle({ title: "扫码出库" }));
      let shownOnce = false;
      onShow(() => {
        if (!shownOnce) {
          shownOnce = true;
          refocusScanInput(scanInputRef, 500);
        }
      });
      const __returned__ = { scanInputRef, warehouseCode, alive, refocusScanInput, processing, pendingConfirm, scanLog, scanCount, handleScan, confirmOutbound, cancelConfirm, clearLog, onScan, onConfirm, onCancel, get shownOnce() {
        return shownOnce;
      }, set shownOnce(v) {
        shownOnce = v;
      }, ref: vue.ref, get onLoad() {
        return onLoad;
      }, get onShow() {
        return onShow;
      }, ScanInput, get useScanOutbound() {
        return useScanOutbound;
      }, get OUTBOUND_SCAN_QTY() {
        return OUTBOUND_SCAN_QTY;
      }, get usePageAlive() {
        return usePageAlive;
      } };
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  };
  function _sfc_render$s(_ctx, _cache, $props, $setup, $data, $options) {
    return vue.openBlock(), vue.createElementBlock("view", { class: "page" }, [
      vue.createElementVNode("view", { class: "scan-bar-fixed" }, [
        vue.createElementVNode("view", { class: "scan-bar-inner" }, [
          vue.createElementVNode("text", { class: "scan-title" }, "扫码出库 · 扫即出"),
          vue.createVNode($setup["ScanInput"], {
            ref: "scanInputRef",
            disabled: $setup.processing,
            placeholder: "扫描物料条码",
            onScan: $setup.onScan
          }, null, 8, ["disabled"]),
          vue.createElementVNode("view", { class: "info-row" }, [
            vue.createElementVNode("text", null, "数量"),
            vue.createElementVNode(
              "text",
              { class: "info-val" },
              vue.toDisplayString($setup.OUTBOUND_SCAN_QTY) + "/次",
              1
              /* TEXT */
            ),
            vue.createElementVNode("text", { class: "info-divider" }, "|"),
            vue.createElementVNode("text", null, "已出"),
            vue.createElementVNode(
              "text",
              { class: "info-val" },
              vue.toDisplayString($setup.scanCount),
              1
              /* TEXT */
            )
          ])
        ])
      ]),
      vue.createElementVNode("view", { class: "scan-bar-spacer" }),
      vue.createCommentVNode(" 出库确认面板 "),
      $setup.pendingConfirm ? (vue.openBlock(), vue.createElementBlock("view", {
        key: 0,
        class: "confirm-panel"
      }, [
        vue.createElementVNode("text", { class: "confirm-title" }, "确认出库"),
        vue.createElementVNode(
          "text",
          { class: "confirm-name" },
          vue.toDisplayString($setup.pendingConfirm.preview.materialName),
          1
          /* TEXT */
        ),
        vue.createElementVNode(
          "text",
          { class: "confirm-row" },
          "物料 " + vue.toDisplayString($setup.pendingConfirm.preview.materialCode),
          1
          /* TEXT */
        ),
        vue.createElementVNode(
          "text",
          { class: "confirm-row" },
          "规格 " + vue.toDisplayString($setup.pendingConfirm.preview.specification || "-"),
          1
          /* TEXT */
        ),
        vue.createElementVNode(
          "text",
          { class: "confirm-row" },
          "库位 " + vue.toDisplayString($setup.pendingConfirm.preview.recommendedLocation),
          1
          /* TEXT */
        ),
        vue.createElementVNode(
          "text",
          { class: "confirm-row" },
          "批次 " + vue.toDisplayString($setup.pendingConfirm.preview.recommendedBatchNo || "-"),
          1
          /* TEXT */
        ),
        vue.createElementVNode(
          "text",
          { class: "confirm-stock" },
          " 可用 " + vue.toDisplayString($setup.pendingConfirm.preview.recommendedAvailableQty) + " / 总 " + vue.toDisplayString($setup.pendingConfirm.preview.totalAvailableQty),
          1
          /* TEXT */
        ),
        vue.createElementVNode("view", { class: "confirm-btns" }, [
          vue.createElementVNode("button", {
            class: "btn-confirm",
            type: "warn",
            loading: $setup.processing,
            onClick: $setup.onConfirm
          }, "确认出库", 8, ["loading"]),
          vue.createElementVNode("button", {
            class: "btn-cancel",
            onClick: $setup.onCancel
          }, "重新扫描")
        ])
      ])) : (vue.openBlock(), vue.createElementBlock("view", {
        key: 1,
        class: "hint-card"
      }, [
        vue.createElementVNode("text", { class: "hint-icon" }, "📤"),
        vue.createElementVNode("text", { class: "hint-title" }, "扫描物料条码"),
        vue.createElementVNode("text", { class: "hint-desc" }, "系统自动匹配库存，点击确认即可完成出库")
      ])),
      $setup.scanLog.length ? (vue.openBlock(), vue.createElementBlock("view", {
        key: 2,
        class: "log-section"
      }, [
        vue.createElementVNode("view", { class: "log-header" }, [
          vue.createElementVNode("text", { class: "log-title" }, "出库记录"),
          vue.createElementVNode("text", {
            class: "log-clear",
            onClick: _cache[0] || (_cache[0] = (...args) => $setup.clearLog && $setup.clearLog(...args))
          }, "清空")
        ]),
        (vue.openBlock(true), vue.createElementBlock(
          vue.Fragment,
          null,
          vue.renderList($setup.scanLog, (item, i) => {
            return vue.openBlock(), vue.createElementBlock(
              "view",
              {
                key: i,
                class: vue.normalizeClass(["log-item", item.ok ? "ok" : "fail"])
              },
              [
                vue.createElementVNode(
                  "text",
                  null,
                  vue.toDisplayString(item.time) + " " + vue.toDisplayString(item.barcode || ""),
                  1
                  /* TEXT */
                ),
                vue.createElementVNode(
                  "text",
                  null,
                  vue.toDisplayString(item.msg),
                  1
                  /* TEXT */
                )
              ],
              2
              /* CLASS */
            );
          }),
          128
          /* KEYED_FRAGMENT */
        ))
      ])) : vue.createCommentVNode("v-if", true)
    ]);
  }
  const PagesOutboundOutbound = /* @__PURE__ */ _export_sfc(_sfc_main$t, [["render", _sfc_render$s], ["__scopeId", "data-v-b3062ebb"], ["__file", "D:/AAA/WMS/wms-pda/src/pages/outbound/outbound.vue"]]);
  const _sfc_main$s = {
    __name: "inventory",
    setup(__props, { expose: __expose }) {
      __expose();
      const mode = vue.ref("code");
      const materialCode = vue.ref("");
      const warehouseCode = vue.ref("");
      const barcode = vue.ref("");
      const list = vue.ref([]);
      const summary = vue.reactive({});
      const searched = vue.ref(false);
      const loading = vue.ref(false);
      const scanInputRef = vue.ref(null);
      const { alive, refocusScanInput } = usePageAlive();
      const hasSummary = vue.computed(
        () => !!(summary.materialCode || summary.materialName || Number(summary.totalStockQty) > 0 || Number(summary.totalAvailableQty) > 0)
      );
      function formatQty2(v) {
        if (v == null || v === "") return "0";
        const n = Number(v);
        if (Number.isNaN(n)) return String(v);
        return Number.isInteger(n) ? String(n) : String(Math.round(n * 1e3) / 1e3);
      }
      function rowKey(item, idx) {
        return [item.materialCode, item.warehouseCode, item.locationCode, item.batchNo, idx].join("|");
      }
      function clearResult() {
        searched.value = false;
        list.value = [];
        Object.keys(summary).forEach((k) => delete summary[k]);
      }
      function switchMode(next) {
        if (mode.value === next) return;
        mode.value = next;
        clearResult();
        if (next === "barcode") {
          barcode.value = "";
          refocusScanInput(scanInputRef, 200);
        }
      }
      function toast(title) {
        uni.showToast({ title, icon: "none" });
      }
      function applyCodeResult(res) {
        const rows = Array.isArray(res) ? res : (res == null ? void 0 : res.records) || (res == null ? void 0 : res.stocks) || [];
        list.value = rows;
        if (rows.length) {
          const first = rows[0];
          summary.materialCode = materialCode.value.trim() || first.materialCode;
          summary.materialName = first.materialName;
          let totalStock = 0;
          let totalAvail = 0;
          rows.forEach((r) => {
            totalStock += Number(r.stockQty ?? r.totalStockQty ?? 0) || 0;
            totalAvail += Number(r.availableQty ?? r.totalAvailableQty ?? r.stockQty ?? 0) || 0;
          });
          summary.totalStockQty = totalStock;
          summary.totalAvailableQty = totalAvail;
        }
      }
      function applyBarcodeResult(res) {
        Object.assign(summary, res || {});
        list.value = (res == null ? void 0 : res.stocks) || [];
      }
      async function onCodeSearch() {
        if (!alive.value || loading.value) return;
        const code = (materialCode.value || "").trim();
        if (!code) {
          toast("请输入物料编码");
          return;
        }
        loading.value = true;
        searched.value = true;
        Object.keys(summary).forEach((k) => delete summary[k]);
        list.value = [];
        try {
          const res = await queryInventoryGet({
            materialCode: code,
            warehouseCode: (warehouseCode.value || "").trim() || void 0
          });
          applyCodeResult(res);
          if (!list.value.length) toast("未查到库存");
        } catch (e) {
          toast((e == null ? void 0 : e.message) || "查询失败");
        } finally {
          loading.value = false;
        }
      }
      async function onBarcodeScan(raw) {
        if (!alive.value || loading.value) return;
        const code = (raw || "").trim();
        if (!code) {
          toast("请先扫码");
          return;
        }
        barcode.value = code;
        loading.value = true;
        searched.value = true;
        Object.keys(summary).forEach((k) => delete summary[k]);
        list.value = [];
        try {
          const res = await queryInventoryPost({
            barcode: code,
            queryType: "MATERIAL"
          });
          applyBarcodeResult(res);
          if (!list.value.length) toast((res == null ? void 0 : res.message) || "未查到库存");
        } catch (e) {
          toast((e == null ? void 0 : e.message) || "查询失败");
        } finally {
          loading.value = false;
          refocusScanInput(scanInputRef, 300);
        }
      }
      onLoad(() => uni.setNavigationBarTitle({ title: "库存查询" }));
      onShow(() => {
        if (mode.value === "barcode") refocusScanInput(scanInputRef, 300);
      });
      vue.onMounted(() => {
        if (mode.value === "barcode") refocusScanInput(scanInputRef, 400);
      });
      const __returned__ = { mode, materialCode, warehouseCode, barcode, list, summary, searched, loading, scanInputRef, alive, refocusScanInput, hasSummary, formatQty: formatQty2, rowKey, clearResult, switchMode, toast, applyCodeResult, applyBarcodeResult, onCodeSearch, onBarcodeScan, ref: vue.ref, reactive: vue.reactive, computed: vue.computed, onMounted: vue.onMounted, get onLoad() {
        return onLoad;
      }, get onShow() {
        return onShow;
      }, ScanSearchBar, get queryInventoryGet() {
        return queryInventoryGet;
      }, get queryInventoryPost() {
        return queryInventoryPost;
      }, get usePageAlive() {
        return usePageAlive;
      } };
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  };
  function _sfc_render$r(_ctx, _cache, $props, $setup, $data, $options) {
    return vue.openBlock(), vue.createElementBlock("view", { class: "page" }, [
      vue.createCommentVNode(" 顶部标签 "),
      vue.createElementVNode("view", { class: "tab-bar" }, [
        vue.createElementVNode(
          "view",
          {
            class: vue.normalizeClass(["tab-item", $setup.mode === "code" && "active"]),
            onClick: _cache[0] || (_cache[0] = ($event) => $setup.switchMode("code"))
          },
          [
            vue.createElementVNode("text", { class: "tab-text" }, "编码查询")
          ],
          2
          /* CLASS */
        ),
        vue.createElementVNode(
          "view",
          {
            class: vue.normalizeClass(["tab-item", $setup.mode === "barcode" && "active"]),
            onClick: _cache[1] || (_cache[1] = ($event) => $setup.switchMode("barcode"))
          },
          [
            vue.createElementVNode("text", { class: "tab-text" }, "扫码查询")
          ],
          2
          /* CLASS */
        ),
        vue.createElementVNode(
          "view",
          {
            class: vue.normalizeClass(["tab-indicator", $setup.mode === "barcode" ? "right" : "left"])
          },
          null,
          2
          /* CLASS */
        )
      ]),
      vue.createCommentVNode(" 编码查询 "),
      $setup.mode === "code" ? (vue.openBlock(), vue.createElementBlock("view", {
        key: 0,
        class: "panel"
      }, [
        vue.createElementVNode("view", { class: "query-row" }, [
          vue.withDirectives(vue.createElementVNode("input", {
            "onUpdate:modelValue": _cache[2] || (_cache[2] = ($event) => $setup.materialCode = $event),
            class: "query-input",
            type: "text",
            "confirm-type": "search",
            placeholder: "请输入物料编码",
            disabled: $setup.loading,
            onConfirm: $setup.onCodeSearch
          }, null, 40, ["disabled"]), [
            [vue.vModelText, $setup.materialCode]
          ]),
          vue.createElementVNode("button", {
            class: "query-btn",
            type: "primary",
            loading: $setup.loading,
            disabled: $setup.loading,
            onClick: $setup.onCodeSearch
          }, " 查询 ", 8, ["loading", "disabled"])
        ]),
        vue.createElementVNode("view", { class: "query-row secondary" }, [
          vue.withDirectives(vue.createElementVNode("input", {
            "onUpdate:modelValue": _cache[3] || (_cache[3] = ($event) => $setup.warehouseCode = $event),
            class: "query-input alone",
            type: "text",
            placeholder: "仓库编码（可选）",
            disabled: $setup.loading,
            onConfirm: $setup.onCodeSearch
          }, null, 40, ["disabled"]), [
            [vue.vModelText, $setup.warehouseCode]
          ])
        ]),
        vue.createElementVNode("text", { class: "hint" }, "须填写物料编码后查询，仓库为可选筛选条件")
      ])) : (vue.openBlock(), vue.createElementBlock(
        vue.Fragment,
        { key: 1 },
        [
          vue.createCommentVNode(" 扫码查询：侧键扫码写入输入框，无摄像头 "),
          vue.createElementVNode("view", { class: "panel scan-panel" }, [
            vue.createVNode($setup["ScanSearchBar"], {
              ref: "scanInputRef",
              modelValue: $setup.barcode,
              "onUpdate:modelValue": _cache[4] || (_cache[4] = ($event) => $setup.barcode = $event),
              placeholder: "侧键扫码或输入条码",
              "action-text": "查询",
              disabled: $setup.loading,
              onScan: $setup.onBarcodeScan,
              onSearch: $setup.onBarcodeScan
            }, null, 8, ["modelValue", "disabled"]),
            vue.createElementVNode("text", { class: "hint" }, "保持输入框聚焦，按设备侧键扫码后自动查询")
          ])
        ],
        2112
        /* STABLE_FRAGMENT, DEV_ROOT_FRAGMENT */
      )),
      vue.createCommentVNode(" 汇总 "),
      $setup.hasSummary ? (vue.openBlock(), vue.createElementBlock("view", {
        key: 2,
        class: "summary"
      }, [
        vue.createElementVNode("view", { class: "summary-head" }, [
          vue.createElementVNode(
            "text",
            { class: "summary-name" },
            vue.toDisplayString($setup.summary.materialName || $setup.summary.materialCode || "库存汇总"),
            1
            /* TEXT */
          ),
          $setup.summary.materialCode ? (vue.openBlock(), vue.createElementBlock(
            "text",
            {
              key: 0,
              class: "summary-code"
            },
            vue.toDisplayString($setup.summary.materialCode),
            1
            /* TEXT */
          )) : vue.createCommentVNode("v-if", true)
        ]),
        vue.createElementVNode("view", { class: "summary-stats" }, [
          vue.createElementVNode("view", { class: "stat" }, [
            vue.createElementVNode("text", { class: "stat-label" }, "总库存"),
            vue.createElementVNode(
              "text",
              { class: "stat-value" },
              vue.toDisplayString($setup.formatQty($setup.summary.totalStockQty)),
              1
              /* TEXT */
            )
          ]),
          vue.createElementVNode("view", { class: "stat-divider" }),
          vue.createElementVNode("view", { class: "stat" }, [
            vue.createElementVNode("text", { class: "stat-label" }, "可用"),
            vue.createElementVNode(
              "text",
              { class: "stat-value accent" },
              vue.toDisplayString($setup.formatQty($setup.summary.totalAvailableQty)),
              1
              /* TEXT */
            )
          ]),
          $setup.list.length ? (vue.openBlock(), vue.createElementBlock("view", {
            key: 0,
            class: "stat-divider"
          })) : vue.createCommentVNode("v-if", true),
          $setup.list.length ? (vue.openBlock(), vue.createElementBlock("view", {
            key: 1,
            class: "stat"
          }, [
            vue.createElementVNode("text", { class: "stat-label" }, "明细行"),
            vue.createElementVNode(
              "text",
              { class: "stat-value" },
              vue.toDisplayString($setup.list.length),
              1
              /* TEXT */
            )
          ])) : vue.createCommentVNode("v-if", true)
        ])
      ])) : vue.createCommentVNode("v-if", true),
      vue.createCommentVNode(" 明细列表 "),
      $setup.list.length ? (vue.openBlock(), vue.createElementBlock("scroll-view", {
        key: 3,
        class: "list-scroll",
        "scroll-y": "",
        "show-scrollbar": false
      }, [
        (vue.openBlock(true), vue.createElementBlock(
          vue.Fragment,
          null,
          vue.renderList($setup.list, (item, idx) => {
            return vue.openBlock(), vue.createElementBlock("view", {
              key: $setup.rowKey(item, idx),
              class: "stock-card"
            }, [
              vue.createElementVNode("view", { class: "stock-top" }, [
                vue.createElementVNode(
                  "text",
                  { class: "mat-code" },
                  vue.toDisplayString(item.materialCode || $setup.summary.materialCode || "-"),
                  1
                  /* TEXT */
                ),
                vue.createElementVNode(
                  "text",
                  { class: "qty-badge" },
                  vue.toDisplayString($setup.formatQty(item.stockQty ?? item.totalStockQty ?? item.availableQty)),
                  1
                  /* TEXT */
                )
              ]),
              vue.createElementVNode(
                "text",
                { class: "mat-name" },
                vue.toDisplayString(item.materialName || $setup.summary.materialName || "-"),
                1
                /* TEXT */
              ),
              vue.createElementVNode("view", { class: "meta-grid" }, [
                vue.createElementVNode(
                  "text",
                  { class: "meta" },
                  "仓 " + vue.toDisplayString(item.warehouseCode || "-"),
                  1
                  /* TEXT */
                ),
                vue.createElementVNode(
                  "text",
                  { class: "meta" },
                  "位 " + vue.toDisplayString(item.locationCode || "-"),
                  1
                  /* TEXT */
                ),
                vue.createElementVNode(
                  "text",
                  { class: "meta" },
                  "批 " + vue.toDisplayString(item.batchNo || "-"),
                  1
                  /* TEXT */
                ),
                item.availableQty != null ? (vue.openBlock(), vue.createElementBlock(
                  "text",
                  {
                    key: 0,
                    class: "meta avail"
                  },
                  " 可用 " + vue.toDisplayString($setup.formatQty(item.availableQty)),
                  1
                  /* TEXT */
                )) : vue.createCommentVNode("v-if", true)
              ])
            ]);
          }),
          128
          /* KEYED_FRAGMENT */
        )),
        vue.createElementVNode(
          "view",
          { class: "list-end" },
          "共 " + vue.toDisplayString($setup.list.length) + " 条",
          1
          /* TEXT */
        )
      ])) : $setup.searched && !$setup.loading ? (vue.openBlock(), vue.createElementBlock("view", {
        key: 4,
        class: "empty"
      }, [
        vue.createElementVNode("text", { class: "empty-icon" }, "📭"),
        vue.createElementVNode("text", { class: "empty-text" }, "未查到库存"),
        vue.createElementVNode("text", { class: "empty-hint" }, "请确认编码/条码是否正确")
      ])) : !$setup.searched && !$setup.loading ? (vue.openBlock(), vue.createElementBlock("view", {
        key: 5,
        class: "empty idle"
      }, [
        vue.createElementVNode("text", { class: "empty-icon" }, "📦"),
        vue.createElementVNode("text", { class: "empty-text" }, "请输入条件后查询"),
        vue.createElementVNode("text", { class: "empty-hint" }, "不支持无条件全量查询")
      ])) : vue.createCommentVNode("v-if", true)
    ]);
  }
  const PagesInventoryInventory = /* @__PURE__ */ _export_sfc(_sfc_main$s, [["render", _sfc_render$r], ["__scopeId", "data-v-ec5f5572"], ["__file", "D:/AAA/WMS/wms-pda/src/pages/inventory/inventory.vue"]]);
  const DRAFT_KEY = "pda-transfer-draft";
  const DRAFT_TTL_MS = 2 * 60 * 60 * 1e3;
  const _sfc_main$r = {
    __name: "transfer",
    setup(__props, { expose: __expose }) {
      __expose();
      const busy = vue.ref(false);
      const stocks = vue.ref([]);
      const selectedStock = vue.ref(null);
      const qtyConfirmed = vue.ref(false);
      const sourceScanRef = vue.ref(null);
      const qtyScanRef = vue.ref(null);
      const targetScanRef = vue.ref(null);
      const sourceInput = vue.ref("");
      const qtyScanInput = vue.ref("");
      const targetInput = vue.ref("");
      const { alive, refocusScanInput } = usePageAlive();
      const form = vue.reactive({
        sourceLocation: "",
        sourceWarehouse: "",
        targetLocation: "",
        materialCode: "",
        materialName: "",
        batchNo: "",
        unitCode: "",
        availableQty: 0,
        transferQty: ""
      });
      const step = vue.computed(() => {
        if (!form.sourceLocation) return 1;
        if (!qtyConfirmed.value) return 2;
        if (!form.targetLocation) return 3;
        return 4;
      });
      vue.watch(step, (val) => {
        vue.nextTick(() => focusByStep(val));
      });
      function formatQty2(val) {
        if (val == null || val === "") return "0";
        const n = Number(val);
        return Number.isNaN(n) ? String(val) : String(n);
      }
      function toast(title, icon = "none") {
        uni.showToast({ title, icon, duration: 2200 });
      }
      function focusByStep(val) {
        if (!alive.value) return;
        if (val === 1) refocusScanInput(sourceScanRef, 200);
        else if (val === 2 && !qtyConfirmed.value) refocusScanInput(qtyScanRef, 200);
        else if (val === 3 && !form.targetLocation) refocusScanInput(targetScanRef, 200);
      }
      function persistDraft() {
        const payload = {
          savedAt: Date.now(),
          form: { ...form },
          stocks: stocks.value,
          selectedStock: selectedStock.value,
          qtyConfirmed: qtyConfirmed.value
        };
        try {
          uni.setStorageSync(DRAFT_KEY, JSON.stringify(payload));
        } catch {
        }
      }
      function restoreDraft() {
        try {
          const raw = uni.getStorageSync(DRAFT_KEY);
          if (!raw) return false;
          const data = typeof raw === "string" ? JSON.parse(raw) : raw;
          if (!(data == null ? void 0 : data.savedAt) || Date.now() - data.savedAt > DRAFT_TTL_MS) {
            clearDraft();
            return false;
          }
          Object.assign(form, data.form || {});
          stocks.value = data.stocks || [];
          selectedStock.value = data.selectedStock || null;
          qtyConfirmed.value = !!data.qtyConfirmed;
          return true;
        } catch {
          return false;
        }
      }
      function clearDraft() {
        try {
          uni.removeStorageSync(DRAFT_KEY);
        } catch {
        }
      }
      function resetAll() {
        Object.assign(form, {
          sourceLocation: "",
          sourceWarehouse: "",
          targetLocation: "",
          materialCode: "",
          materialName: "",
          batchNo: "",
          unitCode: "",
          availableQty: 0,
          transferQty: ""
        });
        stocks.value = [];
        selectedStock.value = null;
        qtyConfirmed.value = false;
        sourceInput.value = "";
        qtyScanInput.value = "";
        targetInput.value = "";
        clearDraft();
      }
      function resetSource() {
        resetAll();
        persistDraft();
        vue.nextTick(() => focusByStep(1));
      }
      function resetTarget() {
        form.targetLocation = "";
        persistDraft();
        vue.nextTick(() => focusByStep(3));
      }
      function reopenQty() {
        qtyConfirmed.value = false;
        form.targetLocation = "";
        persistDraft();
        vue.nextTick(() => focusByStep(2));
      }
      function parseLocationCode(raw, parsed) {
        const code = ((parsed == null ? void 0 : parsed.locationCode) || raw || "").trim();
        if (!code) return "";
        return code;
      }
      async function loadSourceStocks(locationCode) {
        var _a;
        busy.value = true;
        try {
          const res = await queryInventoryPost({
            barcode: locationCode,
            locationCode,
            queryType: "LOCATION"
          });
          const list = (res == null ? void 0 : res.stocks) || [];
          form.sourceLocation = locationCode;
          form.sourceWarehouse = ((_a = list[0]) == null ? void 0 : _a.warehouseCode) || form.sourceWarehouse || "";
          stocks.value = list;
          selectedStock.value = null;
          qtyConfirmed.value = false;
          form.targetLocation = "";
          form.materialCode = "";
          form.materialName = "";
          form.batchNo = "";
          form.unitCode = "";
          form.availableQty = 0;
          form.transferQty = "";
          if (!list.length) {
            toast((res == null ? void 0 : res.message) || "该库位暂无可用库存");
            persistDraft();
            return;
          }
          if (list.length === 1) {
            selectStock(list[0]);
            toast(`已加载 ${list[0].materialCode}`);
          } else {
            toast(`已加载 ${list.length} 条库存，请选择物料`);
          }
          persistDraft();
        } catch (e) {
          toast((e == null ? void 0 : e.message) || "源库位查询失败");
        } finally {
          busy.value = false;
          vue.nextTick(() => focusByStep(step.value));
        }
      }
      async function onSourceScan(barcode) {
        if (!alive.value || busy.value) return;
        const raw = String(barcode || "").trim();
        if (!raw) return;
        sourceInput.value = "";
        try {
          const parsed = await resolveBarcode(raw);
          const locationCode = parseLocationCode(raw, parsed);
          if (!locationCode) {
            toast("未识别到库位编码");
            return;
          }
          await loadSourceStocks(locationCode);
        } catch (e) {
          toast((e == null ? void 0 : e.message) || "扫码失败");
          refocusScanInput(sourceScanRef, 300);
        }
      }
      function selectStock(item) {
        if (!item) return;
        selectedStock.value = item;
        form.materialCode = item.materialCode || "";
        form.materialName = item.materialName || "";
        form.batchNo = item.batchNo || "";
        form.unitCode = item.unitCode || "";
        form.availableQty = Number(item.availableQty ?? item.stockQty ?? 0);
        form.sourceWarehouse = item.warehouseCode || form.sourceWarehouse;
        if (!form.transferQty) {
          form.transferQty = "";
        }
        qtyConfirmed.value = false;
        form.targetLocation = "";
        persistDraft();
        vue.nextTick(() => focusByStep(2));
      }
      function qtyFromParsed(parsed) {
        const segments = (parsed == null ? void 0 : parsed.segments) || {};
        const raw = segments.qty ?? segments.quantity ?? segments.actualQty ?? segments.transferQty;
        if (raw == null || raw === "") return "";
        const n = Number(raw);
        return Number.isNaN(n) ? "" : String(n);
      }
      async function onQtyScan(barcode) {
        if (!alive.value || busy.value || !selectedStock.value) return;
        const raw = String(barcode || "").trim();
        if (!raw) return;
        qtyScanInput.value = "";
        try {
          const parsed = await resolveBarcode(raw);
          const mat = (parsed.materialCode || "").trim();
          if (mat && form.materialCode && mat !== form.materialCode) {
            toast(`标签物料 ${mat} 与当前选择不一致`);
            return;
          }
          if (parsed.batchNo && form.batchNo && parsed.batchNo !== form.batchNo) {
            toast("标签批次与当前选择不一致");
            return;
          }
          const qty = qtyFromParsed(parsed);
          if (!qty) {
            toast("标签未解析到数量，请手输");
            return;
          }
          form.transferQty = qty;
          toast(`已填入数量 ${qty}`);
          persistDraft();
        } catch (e) {
          toast((e == null ? void 0 : e.message) || "数量扫码失败");
        } finally {
          refocusScanInput(qtyScanRef, 300);
        }
      }
      function confirmQty() {
        const qty = Number(form.transferQty);
        if (!form.materialCode) {
          toast("请先选择物料");
          return;
        }
        if (!form.transferQty || Number.isNaN(qty) || qty <= 0) {
          toast("移库数量不合法");
          return;
        }
        if (qty > Number(form.availableQty || 0)) {
          toast(`数量超过可用库存（可用 ${formatQty2(form.availableQty)}）`);
          return;
        }
        qtyConfirmed.value = true;
        persistDraft();
        toast("数量已确认，请扫目标库位", "success");
        vue.nextTick(() => focusByStep(3));
      }
      async function onTargetScan(barcode) {
        if (!alive.value || busy.value || !qtyConfirmed.value) {
          toast("请先确认移库数量");
          return;
        }
        const raw = String(barcode || "").trim();
        if (!raw) return;
        targetInput.value = "";
        try {
          const parsed = await resolveBarcode(raw);
          const locationCode = parseLocationCode(raw, parsed);
          if (!locationCode) {
            toast("未识别到目标库位");
            return;
          }
          if (locationCode === form.sourceLocation) {
            toast("目标库位不能与源库位相同");
            return;
          }
          form.targetLocation = locationCode;
          persistDraft();
          toast("目标库位已确认", "success");
        } catch (e) {
          toast((e == null ? void 0 : e.message) || "目标库位扫码失败");
        } finally {
          vue.nextTick(() => focusByStep(step.value));
        }
      }
      async function submitTransfer() {
        if (!form.sourceLocation) {
          toast("请先扫描源库位");
          return;
        }
        if (!form.materialCode) {
          toast("请选择移库物料");
          return;
        }
        if (!form.transferQty || Number(form.transferQty) <= 0) {
          toast("移库数量不合法");
          return;
        }
        if (!form.targetLocation) {
          toast("请先扫描目标库位");
          return;
        }
        busy.value = true;
        try {
          const res = await transferStock({
            sourceLocation: form.sourceLocation,
            targetLocation: form.targetLocation,
            materialCode: form.materialCode,
            batchNo: form.batchNo || void 0,
            transferQty: Number(form.transferQty)
          });
          toast((res == null ? void 0 : res.transferNo) ? `移库成功 ${res.transferNo}` : "移库成功", "success");
          resetAll();
          vue.nextTick(() => focusByStep(1));
        } catch (e) {
          toast((e == null ? void 0 : e.message) || "移库失败");
        } finally {
          busy.value = false;
        }
      }
      onLoad(() => {
        uni.setNavigationBarTitle({ title: "移库作业" });
        restoreDraft();
      });
      onShow(() => {
        restoreDraft();
        vue.nextTick(() => focusByStep(step.value));
      });
      onHide(() => {
        persistDraft();
      });
      const __returned__ = { DRAFT_KEY, DRAFT_TTL_MS, busy, stocks, selectedStock, qtyConfirmed, sourceScanRef, qtyScanRef, targetScanRef, sourceInput, qtyScanInput, targetInput, alive, refocusScanInput, form, step, formatQty: formatQty2, toast, focusByStep, persistDraft, restoreDraft, clearDraft, resetAll, resetSource, resetTarget, reopenQty, parseLocationCode, loadSourceStocks, onSourceScan, selectStock, qtyFromParsed, onQtyScan, confirmQty, onTargetScan, submitTransfer, ref: vue.ref, reactive: vue.reactive, computed: vue.computed, watch: vue.watch, nextTick: vue.nextTick, get onLoad() {
        return onLoad;
      }, get onShow() {
        return onShow;
      }, get onHide() {
        return onHide;
      }, ScanSearchBar, get usePageAlive() {
        return usePageAlive;
      }, get queryInventoryPost() {
        return queryInventoryPost;
      }, get transferStock() {
        return transferStock;
      }, get resolveBarcode() {
        return resolveBarcode;
      } };
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  };
  function _sfc_render$q(_ctx, _cache, $props, $setup, $data, $options) {
    return vue.openBlock(), vue.createElementBlock("view", { class: "page" }, [
      vue.createCommentVNode(" 步骤1：扫源库 "),
      vue.createElementVNode("view", { class: "card" }, [
        vue.createElementVNode("view", { class: "card-head" }, [
          vue.createElementVNode("text", { class: "card-title" }, "源库位"),
          $setup.form.sourceLocation ? (vue.openBlock(), vue.createElementBlock("text", {
            key: 0,
            class: "link",
            onClick: $setup.resetSource
          }, "重扫源库")) : vue.createCommentVNode("v-if", true)
        ]),
        $setup.step === 1 ? (vue.openBlock(), vue.createBlock($setup["ScanSearchBar"], {
          key: 0,
          ref: "sourceScanRef",
          modelValue: $setup.sourceInput,
          "onUpdate:modelValue": _cache[0] || (_cache[0] = ($event) => $setup.sourceInput = $event),
          placeholder: "扫码或输入源库位",
          "action-text": "确定",
          disabled: $setup.busy,
          onScan: $setup.onSourceScan,
          onSearch: $setup.onSourceScan
        }, null, 8, ["modelValue", "disabled"])) : vue.createCommentVNode("v-if", true),
        $setup.step === 1 ? (vue.openBlock(), vue.createElementBlock("text", {
          key: 1,
          class: "hint"
        }, "请扫描或输入源库位后点确定")) : (vue.openBlock(), vue.createElementBlock("view", {
          key: 2,
          class: "value-box"
        }, [
          vue.createElementVNode(
            "text",
            { class: "value-main" },
            vue.toDisplayString($setup.form.sourceLocation),
            1
            /* TEXT */
          ),
          vue.createElementVNode(
            "text",
            { class: "value-sub" },
            "仓库 " + vue.toDisplayString($setup.form.sourceWarehouse || "-") + " · 库存行 " + vue.toDisplayString($setup.stocks.length),
            1
            /* TEXT */
          )
        ]))
      ]),
      vue.createCommentVNode(" 物料列表 / 已选物料 "),
      $setup.step >= 2 ? (vue.openBlock(), vue.createElementBlock("view", {
        key: 0,
        class: "card"
      }, [
        vue.createElementVNode("view", { class: "card-head" }, [
          vue.createElementVNode("text", { class: "card-title" }, "移库物料"),
          $setup.step >= 3 && !$setup.qtyConfirmed ? (vue.openBlock(), vue.createElementBlock("text", {
            key: 0,
            class: "link",
            onClick: $setup.reopenQty
          }, "修改数量")) : vue.createCommentVNode("v-if", true)
        ]),
        !$setup.selectedStock ? (vue.openBlock(), vue.createElementBlock("view", {
          key: 0,
          class: "stock-list"
        }, [
          (vue.openBlock(true), vue.createElementBlock(
            vue.Fragment,
            null,
            vue.renderList($setup.stocks, (item, idx) => {
              return vue.openBlock(), vue.createElementBlock("view", {
                key: idx,
                class: "stock-row",
                onClick: ($event) => $setup.selectStock(item)
              }, [
                vue.createElementVNode("view", { class: "stock-main" }, [
                  vue.createElementVNode(
                    "text",
                    { class: "mat-code" },
                    vue.toDisplayString(item.materialCode),
                    1
                    /* TEXT */
                  ),
                  vue.createElementVNode(
                    "text",
                    { class: "mat-name" },
                    vue.toDisplayString(item.materialName || "-"),
                    1
                    /* TEXT */
                  ),
                  vue.createElementVNode(
                    "text",
                    { class: "mat-meta" },
                    "批次 " + vue.toDisplayString(item.batchNo || "-") + " · 可用 " + vue.toDisplayString($setup.formatQty(item.availableQty)),
                    1
                    /* TEXT */
                  )
                ]),
                vue.createElementVNode("text", { class: "arrow" }, "›")
              ], 8, ["onClick"]);
            }),
            128
            /* KEYED_FRAGMENT */
          )),
          !$setup.stocks.length ? (vue.openBlock(), vue.createElementBlock("view", {
            key: 0,
            class: "empty"
          }, "该库位暂无可用库存")) : vue.createCommentVNode("v-if", true)
        ])) : (vue.openBlock(), vue.createElementBlock("view", {
          key: 1,
          class: "selected-box"
        }, [
          vue.createElementVNode(
            "text",
            { class: "mat-code" },
            vue.toDisplayString($setup.form.materialCode),
            1
            /* TEXT */
          ),
          vue.createElementVNode(
            "text",
            { class: "mat-name" },
            vue.toDisplayString($setup.form.materialName || "-"),
            1
            /* TEXT */
          ),
          vue.createElementVNode(
            "text",
            { class: "mat-meta" },
            "批次 " + vue.toDisplayString($setup.form.batchNo || "-") + " · 可用 " + vue.toDisplayString($setup.formatQty($setup.form.availableQty)),
            1
            /* TEXT */
          )
        ]))
      ])) : vue.createCommentVNode("v-if", true),
      vue.createCommentVNode(" 步骤2：数量输入（确认前） "),
      $setup.step === 2 && $setup.selectedStock && !$setup.qtyConfirmed ? (vue.openBlock(), vue.createElementBlock("view", {
        key: 1,
        class: "card qty-card"
      }, [
        vue.createElementVNode("text", { class: "card-title" }, "移库数量"),
        vue.createElementVNode("text", { class: "hint" }, "可手输数量，或扫描/输入物料标签后点确定自动填入"),
        vue.createVNode($setup["ScanSearchBar"], {
          ref: "qtyScanRef",
          modelValue: $setup.qtyScanInput,
          "onUpdate:modelValue": _cache[1] || (_cache[1] = ($event) => $setup.qtyScanInput = $event),
          placeholder: "扫码或输入物料标签获取数量",
          "action-text": "填入",
          disabled: $setup.busy,
          onScan: $setup.onQtyScan,
          onSearch: $setup.onQtyScan
        }, null, 8, ["modelValue", "disabled"]),
        vue.createElementVNode("view", { class: "qty-row" }, [
          vue.withDirectives(vue.createElementVNode("input", {
            "onUpdate:modelValue": _cache[2] || (_cache[2] = ($event) => $setup.form.transferQty = $event),
            class: "qty-input",
            type: "text",
            inputmode: "decimal",
            placeholder: "输入移库数量",
            disabled: $setup.busy
          }, null, 8, ["disabled"]), [
            [vue.vModelText, $setup.form.transferQty]
          ]),
          vue.createElementVNode(
            "text",
            { class: "unit" },
            vue.toDisplayString($setup.form.unitCode || ""),
            1
            /* TEXT */
          )
        ]),
        vue.createElementVNode("button", {
          class: "primary-btn",
          type: "primary",
          loading: $setup.busy,
          onClick: $setup.confirmQty
        }, "确认数量", 8, ["loading"])
      ])) : vue.createCommentVNode("v-if", true),
      $setup.qtyConfirmed ? (vue.openBlock(), vue.createElementBlock("view", {
        key: 2,
        class: "card"
      }, [
        vue.createElementVNode("text", { class: "card-title" }, "已确认数量"),
        vue.createElementVNode(
          "text",
          { class: "qty-confirmed" },
          vue.toDisplayString($setup.formatQty($setup.form.transferQty)) + " " + vue.toDisplayString($setup.form.unitCode || ""),
          1
          /* TEXT */
        )
      ])) : vue.createCommentVNode("v-if", true),
      vue.createCommentVNode(" 步骤3：目标库 "),
      $setup.step >= 3 ? (vue.openBlock(), vue.createElementBlock("view", {
        key: 3,
        class: "card"
      }, [
        vue.createElementVNode("view", { class: "card-head" }, [
          vue.createElementVNode("text", { class: "card-title" }, "目标库位"),
          $setup.form.targetLocation ? (vue.openBlock(), vue.createElementBlock("text", {
            key: 0,
            class: "link",
            onClick: $setup.resetTarget
          }, "重扫目标库")) : vue.createCommentVNode("v-if", true)
        ]),
        !$setup.form.targetLocation ? (vue.openBlock(), vue.createBlock($setup["ScanSearchBar"], {
          key: 0,
          ref: "targetScanRef",
          modelValue: $setup.targetInput,
          "onUpdate:modelValue": _cache[3] || (_cache[3] = ($event) => $setup.targetInput = $event),
          placeholder: "扫码或输入目标库位",
          "action-text": "确定",
          disabled: $setup.busy || !$setup.qtyConfirmed,
          onScan: $setup.onTargetScan,
          onSearch: $setup.onTargetScan
        }, null, 8, ["modelValue", "disabled"])) : vue.createCommentVNode("v-if", true),
        !$setup.form.targetLocation ? (vue.openBlock(), vue.createElementBlock("text", {
          key: 1,
          class: "hint"
        }, "确认数量后请扫描或输入目标库位")) : (vue.openBlock(), vue.createElementBlock("view", {
          key: 2,
          class: "value-box"
        }, [
          vue.createElementVNode(
            "text",
            { class: "value-main" },
            vue.toDisplayString($setup.form.targetLocation),
            1
            /* TEXT */
          )
        ]))
      ])) : vue.createCommentVNode("v-if", true),
      vue.createCommentVNode(" 步骤4：提交 "),
      $setup.step >= 4 ? (vue.openBlock(), vue.createElementBlock("view", {
        key: 4,
        class: "footer"
      }, [
        vue.createElementVNode("view", { class: "summary" }, [
          vue.createElementVNode(
            "text",
            null,
            vue.toDisplayString($setup.form.sourceLocation) + " → " + vue.toDisplayString($setup.form.targetLocation),
            1
            /* TEXT */
          ),
          vue.createElementVNode(
            "text",
            null,
            vue.toDisplayString($setup.form.materialCode) + " × " + vue.toDisplayString($setup.formatQty($setup.form.transferQty)),
            1
            /* TEXT */
          )
        ]),
        vue.createElementVNode("button", {
          class: "submit-btn",
          type: "primary",
          loading: $setup.busy,
          onClick: $setup.submitTransfer
        }, " 确认移库 ", 8, ["loading"])
      ])) : vue.createCommentVNode("v-if", true)
    ]);
  }
  const PagesTransferTransfer = /* @__PURE__ */ _export_sfc(_sfc_main$r, [["render", _sfc_render$q], ["__scopeId", "data-v-d303ad3d"], ["__file", "D:/AAA/WMS/wms-pda/src/pages/transfer/transfer.vue"]]);
  function listStockCountBills(params = {}) {
    return request({ url: "/mobile/stock-count", data: params });
  }
  function resolveStockCountBarcode(barcodeContent) {
    return request({
      url: "/mobile/stock-count/resolve-barcode",
      method: "POST",
      data: { barcodeContent }
    });
  }
  function getStockCountDetail(billNo, forceRefresh = false) {
    return request({
      url: `/mobile/stock-count/${encodeURIComponent(billNo)}`,
      data: forceRefresh ? { forceRefresh: true } : void 0
    });
  }
  function matchStockCountLine(billNo, data) {
    return request({
      url: `/mobile/stock-count/${encodeURIComponent(billNo)}/match`,
      method: "POST",
      data: withDevice(data || {}),
      silent: true
    });
  }
  function scanStockCountLine(billNo, data) {
    return request({
      url: `/mobile/stock-count/${encodeURIComponent(billNo)}/scan`,
      method: "POST",
      data: withDevice(data || {})
    });
  }
  function updateStockCountLineQty(billNo, lineNo, actualQty) {
    return request({
      url: `/mobile/stock-count/${encodeURIComponent(billNo)}/lines/${lineNo}/qty`,
      method: "PUT",
      data: { actualQty }
    });
  }
  function completeStockCount(billNo) {
    return request({
      url: `/mobile/stock-count/${encodeURIComponent(billNo)}/complete`,
      method: "POST"
    });
  }
  function heartbeatStockCountLock(billNo) {
    return request({
      url: `/mobile/stock-count/${encodeURIComponent(billNo)}/lock/heartbeat`,
      method: "POST",
      silent: true
    });
  }
  function releaseStockCountLock(billNo) {
    return request({
      url: `/mobile/stock-count/${encodeURIComponent(billNo)}/lock/release`,
      method: "POST",
      silent: true
    });
  }
  const LIST_CACHE_TTL_MS = 12e4;
  function useStockCountList() {
    const loading = vue.ref(false);
    const loadingMore = vue.ref(false);
    const keyword = vue.ref("");
    const bills = vue.ref([]);
    const current = vue.ref(1);
    const total = vue.ref(0);
    const hasMore = vue.ref(false);
    let lastShowAt = 0;
    function cacheKey(kw) {
      return `stockcount-list-page1:${String(kw || "").trim().toLowerCase()}`;
    }
    function applyPage(parsed, append) {
      total.value = parsed.total;
      current.value = parsed.current;
      hasMore.value = parsed.hasMore;
      if (append) {
        bills.value = mergeNoticeRecords(bills.value, parsed.valid);
      } else {
        bills.value = parsed.valid;
      }
    }
    async function loadList(kw = keyword.value, options = {}) {
      var _a;
      const force = options.force === true;
      keyword.value = kw;
      current.value = 1;
      const key = cacheKey(kw);
      const cached = cacheGet(key);
      if (!force && ((_a = cached == null ? void 0 : cached.records) == null ? void 0 : _a.length)) {
        bills.value = cached.records;
        total.value = cached.total || cached.records.length;
        current.value = 1;
        hasMore.value = bills.value.length < total.value;
        refreshInBackground(kw, key);
        return bills.value;
      }
      loading.value = true;
      try {
        const pageData = await listStockCountBills({
          keyword: kw || void 0,
          current: 1,
          size: PAGE_SIZE
        });
        const parsed = parseNoticePage(pageData);
        applyPage(parsed, false);
        cacheSet(key, { records: bills.value, total: total.value }, LIST_CACHE_TTL_MS);
        lastShowAt = Date.now();
        return bills.value;
      } catch (e) {
        uni.showToast({ title: (e == null ? void 0 : e.message) || "加载失败，请检查网络", icon: "none" });
        return bills.value;
      } finally {
        loading.value = false;
      }
    }
    function refreshInBackground(kw, key) {
      if (Date.now() - lastShowAt < SHOW_THROTTLE_MS) return;
      lastShowAt = Date.now();
      listStockCountBills({
        keyword: kw || void 0,
        current: 1,
        size: PAGE_SIZE
      }).then((pageData) => {
        const parsed = parseNoticePage(pageData);
        if (current.value <= 1) {
          applyPage(parsed, false);
        } else {
          bills.value = mergeNoticeRecords(parsed.valid, bills.value);
          total.value = parsed.total;
          hasMore.value = bills.value.length < total.value;
        }
        cacheSet(key, { records: parsed.valid, total: parsed.total }, LIST_CACHE_TTL_MS);
      }).catch(() => {
      });
    }
    async function loadMore() {
      if (loading.value || loadingMore.value || !hasMore.value) return bills.value;
      loadingMore.value = true;
      try {
        const next = current.value + 1;
        const pageData = await listStockCountBills({
          keyword: keyword.value || void 0,
          current: next,
          size: PAGE_SIZE
        });
        const parsed = parseNoticePage(pageData);
        applyPage(parsed, true);
        return bills.value;
      } catch (e) {
        uni.showToast({ title: (e == null ? void 0 : e.message) || "加载更多失败", icon: "none" });
        return bills.value;
      } finally {
        loadingMore.value = false;
      }
    }
    async function loadListOnShow() {
      var _a;
      const key = cacheKey(keyword.value);
      const cached = cacheGet(key);
      if ((_a = cached == null ? void 0 : cached.records) == null ? void 0 : _a.length) {
        if (!bills.value.length) {
          bills.value = cached.records;
          total.value = cached.total || cached.records.length;
          current.value = 1;
          hasMore.value = bills.value.length < total.value;
        }
        if (Date.now() - lastShowAt >= SHOW_THROTTLE_MS) {
          refreshInBackground(keyword.value, key);
        }
        return bills.value;
      }
      return loadList(keyword.value);
    }
    async function searchByBarcode(barcode) {
      const raw = (barcode || "").trim();
      cacheDel(cacheKey(keyword.value));
      if (!raw) return { action: "list", bills: await loadList("", { force: true }) };
      try {
        const res = await resolveStockCountBarcode(raw);
        const billNo = ((res == null ? void 0 : res.billNo) || "").trim();
        if (billNo) {
          return { action: "open", billNo };
        }
      } catch {
      }
      keyword.value = raw;
      return { action: "list", bills: await loadList(raw, { force: true }) };
    }
    function statusLabel(item) {
      const s = item.scanStatus;
      if (s === "COMPLETED") return "已完成";
      if (s === "COUNTING" || item.inProgress) return "盘点中";
      return "待盘点";
    }
    function statusClass(item) {
      const s = item.scanStatus;
      if (s === "COMPLETED") return "done";
      if (s === "COUNTING" || item.inProgress) return "progress";
      return "new";
    }
    return {
      loading,
      loadingMore,
      keyword,
      bills,
      current,
      total,
      hasMore,
      loadList,
      loadMore,
      loadListOnShow,
      searchByBarcode,
      statusLabel,
      statusClass
    };
  }
  const _sfc_main$q = {
    __name: "stockcheck-list",
    setup(__props, { expose: __expose }) {
      __expose();
      const scanInputRef = vue.ref(null);
      const { alive, refocusScanInput } = usePageAlive();
      const {
        loading,
        loadingMore,
        keyword,
        bills,
        total,
        hasMore,
        loadList,
        loadMore,
        loadListOnShow,
        searchByBarcode,
        statusLabel,
        statusClass
      } = useStockCountList();
      async function onScan(barcode) {
        if (!alive.value) return;
        const result = await searchByBarcode(barcode);
        if ((result == null ? void 0 : result.action) === "open" && result.billNo) {
          openBill({ billNo: result.billNo });
        }
        refocusScanInput(scanInputRef, 300);
      }
      async function onSearch(val) {
        if (!alive.value) return;
        const raw = (val || keyword.value || "").trim();
        keyword.value = raw;
        if (raw) {
          const result = await searchByBarcode(raw);
          if ((result == null ? void 0 : result.action) === "open" && result.billNo) {
            openBill({ billNo: result.billNo });
            refocusScanInput(scanInputRef, 300);
            return;
          }
        }
        await loadList(keyword.value, { force: true });
        refocusScanInput(scanInputRef, 300);
      }
      function openBill(item) {
        if (!(item == null ? void 0 : item.billNo)) {
          uni.showToast({ title: "单号无效", icon: "none" });
          return;
        }
        uni.navigateTo({
          url: `/pages/stockcheck/stockcheck-scan?billNo=${encodeURIComponent(item.billNo)}`
        });
      }
      async function onLoadMore() {
        if (!hasMore.value || loadingMore.value) return;
        await loadMore();
      }
      function onScrollToLower() {
        onLoadMore();
      }
      onLoad(() => uni.setNavigationBarTitle({ title: "盘点作业" }));
      onShow(() => loadListOnShow());
      vue.onMounted(() => refocusScanInput(scanInputRef, 500));
      onPullDownRefresh(async () => {
        try {
          await loadList(keyword.value, { force: true });
        } finally {
          uni.stopPullDownRefresh();
        }
      });
      const __returned__ = { scanInputRef, alive, refocusScanInput, loading, loadingMore, keyword, bills, total, hasMore, loadList, loadMore, loadListOnShow, searchByBarcode, statusLabel, statusClass, onScan, onSearch, openBill, onLoadMore, onScrollToLower, ref: vue.ref, onMounted: vue.onMounted, get onLoad() {
        return onLoad;
      }, get onShow() {
        return onShow;
      }, get onPullDownRefresh() {
        return onPullDownRefresh;
      }, ScanSearchBar, get useStockCountList() {
        return useStockCountList;
      }, get usePageAlive() {
        return usePageAlive;
      } };
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  };
  function _sfc_render$p(_ctx, _cache, $props, $setup, $data, $options) {
    return vue.openBlock(), vue.createElementBlock("view", { class: "page" }, [
      vue.createElementVNode("view", { class: "search-top" }, [
        vue.createVNode($setup["ScanSearchBar"], {
          ref: "scanInputRef",
          modelValue: $setup.keyword,
          "onUpdate:modelValue": _cache[0] || (_cache[0] = ($event) => $setup.keyword = $event),
          disabled: $setup.loading,
          placeholder: "扫盘点二维码或搜索单号",
          "action-text": "搜索",
          onScan: $setup.onScan,
          onSearch: $setup.onSearch
        }, null, 8, ["modelValue", "disabled"])
      ]),
      vue.createElementVNode(
        "scroll-view",
        {
          class: "list-scroll",
          "scroll-y": "",
          "show-scrollbar": false,
          onScrolltolower: $setup.onScrollToLower
        },
        [
          (vue.openBlock(true), vue.createElementBlock(
            vue.Fragment,
            null,
            vue.renderList($setup.bills, (item, index) => {
              return vue.openBlock(), vue.createElementBlock("view", {
                key: item.billNo || "row-" + index,
                class: "bill-row",
                onClick: ($event) => $setup.openBill(item)
              }, [
                vue.createElementVNode("view", { class: "row-main" }, [
                  vue.createElementVNode(
                    "text",
                    { class: "bill-no" },
                    vue.toDisplayString(item.billNo || "（单号缺失）"),
                    1
                    /* TEXT */
                  ),
                  vue.createElementVNode("text", { class: "bill-meta" }, [
                    vue.createTextVNode(
                      " 仓库 " + vue.toDisplayString(item.warehouseCode || "-") + " ",
                      1
                      /* TEXT */
                    ),
                    item.remark ? (vue.openBlock(), vue.createElementBlock(
                      "text",
                      { key: 0 },
                      " · " + vue.toDisplayString(item.remark),
                      1
                      /* TEXT */
                    )) : vue.createCommentVNode("v-if", true)
                  ]),
                  vue.createElementVNode("text", { class: "bill-sub" }, [
                    vue.createTextVNode(
                      " 明细 " + vue.toDisplayString(item.totalLines || 0) + " 行 ",
                      1
                      /* TEXT */
                    ),
                    item.countedLines ? (vue.openBlock(), vue.createElementBlock(
                      "text",
                      { key: 0 },
                      " · 已盘 " + vue.toDisplayString(item.countedLines),
                      1
                      /* TEXT */
                    )) : vue.createCommentVNode("v-if", true),
                    item.billDate ? (vue.openBlock(), vue.createElementBlock(
                      "text",
                      { key: 1 },
                      " · " + vue.toDisplayString(item.billDate),
                      1
                      /* TEXT */
                    )) : vue.createCommentVNode("v-if", true),
                    item.locked && item.lockUserName ? (vue.openBlock(), vue.createElementBlock(
                      "text",
                      {
                        key: 2,
                        class: "bill-lock"
                      },
                      " · " + vue.toDisplayString(item.lockUserName) + "操作中",
                      1
                      /* TEXT */
                    )) : vue.createCommentVNode("v-if", true)
                  ])
                ]),
                vue.createElementVNode("view", { class: "row-side" }, [
                  vue.createElementVNode(
                    "text",
                    {
                      class: vue.normalizeClass(["status-tag", $setup.statusClass(item)])
                    },
                    vue.toDisplayString($setup.statusLabel(item)),
                    3
                    /* TEXT, CLASS */
                  ),
                  vue.createElementVNode("text", { class: "arrow" }, "›")
                ])
              ], 8, ["onClick"]);
            }),
            128
            /* KEYED_FRAGMENT */
          )),
          !$setup.bills.length && !$setup.loading ? (vue.openBlock(), vue.createElementBlock("view", {
            key: 0,
            class: "empty"
          }, [
            vue.createElementVNode("text", { class: "empty-text" }, "暂无未审核的盘点作业单"),
            vue.createElementVNode("text", { class: "empty-hint" }, "请确认金蝶盘点方案已生成作业且未审核，或下拉刷新")
          ])) : vue.createCommentVNode("v-if", true),
          $setup.loading && !$setup.bills.length ? (vue.openBlock(), vue.createElementBlock("view", {
            key: 1,
            class: "loading-tip"
          }, "加载中...")) : vue.createCommentVNode("v-if", true),
          $setup.bills.length ? (vue.openBlock(), vue.createElementBlock("view", {
            key: 2,
            class: "footer-tip"
          }, [
            vue.createElementVNode(
              "text",
              null,
              "已显示 " + vue.toDisplayString($setup.bills.length) + " / " + vue.toDisplayString($setup.total) + " 条",
              1
              /* TEXT */
            ),
            $setup.hasMore ? (vue.openBlock(), vue.createElementBlock(
              "view",
              {
                key: 0,
                class: "load-more-btn",
                onClick: $setup.onLoadMore
              },
              vue.toDisplayString($setup.loadingMore ? "加载中..." : "加载更多"),
              1
              /* TEXT */
            )) : (vue.openBlock(), vue.createElementBlock("text", {
              key: 1,
              class: "end-tip"
            }, "没有更多了"))
          ])) : vue.createCommentVNode("v-if", true)
        ],
        32
        /* NEED_HYDRATION */
      )
    ]);
  }
  const PagesStockcheckStockcheckList = /* @__PURE__ */ _export_sfc(_sfc_main$q, [["render", _sfc_render$p], ["__scopeId", "data-v-a16038eb"], ["__file", "D:/AAA/WMS/wms-pda/src/pages/stockcheck/stockcheck-list.vue"]]);
  const _sfc_main$p = {
    __name: "stockcheck-scan",
    setup(__props, { expose: __expose }) {
      __expose();
      const billNo = vue.ref("");
      const detail = vue.ref(null);
      const lines = vue.ref([]);
      const loading = vue.ref(false);
      const busy = vue.ref(false);
      const scanInput = vue.ref("");
      const scanInputRef = vue.ref(null);
      const matchedLine = vue.ref(null);
      const matchFailTip = vue.ref("");
      const actualQtyInput = vue.ref("");
      const highlightLineNo = vue.ref(null);
      const scannedLineNos = vue.ref(/* @__PURE__ */ new Set());
      const { alive, refocusScanInput } = usePageAlive();
      const billLock = useBillExclusiveLock({
        heartbeat: () => heartbeatStockCountLock(billNo.value),
        release: () => releaseStockCountLock(billNo.value)
      });
      const countedCount = vue.computed(() => lines.value.filter((l) => l.counted).length);
      function handleLockDenied(e) {
        billLock.stop();
        toast((e == null ? void 0 : e.message) || "单据正被其他人操作");
        setTimeout(() => uni.navigateBack({ fail: () => {
        } }), 400);
      }
      function isLineLabelScanned(line) {
        if (!line) return false;
        return scannedLineNos.value.has(line.lineNo) || line.labelScanned === true;
      }
      function markLineScanned(lineNo) {
        if (lineNo == null) return;
        const next = new Set(scannedLineNos.value);
        next.add(lineNo);
        scannedLineNos.value = next;
      }
      function formatQty2(val) {
        if (val == null || val === "") return "0";
        const n = Number(val);
        return Number.isNaN(n) ? String(val) : String(n);
      }
      function diffClass(diff) {
        const n = Number(diff);
        if (Number.isNaN(n) || n === 0) return "diff-zero";
        return n > 0 ? "diff-up" : "diff-down";
      }
      function toast(title, icon = "none") {
        uni.showToast({ title, icon, duration: 2200 });
      }
      function applyDetail(data) {
        detail.value = data;
        const list = ((data == null ? void 0 : data.lines) || []).map((l) => ({
          ...l,
          _actual: l.actualQty != null ? String(l.actualQty) : ""
        }));
        lines.value = list;
      }
      async function reload(force = false) {
        if (!billNo.value) return;
        loading.value = true;
        try {
          const data = await getStockCountDetail(billNo.value, force);
          applyDetail(data);
          billLock.start();
        } catch (e) {
          if (isBillLockedError(e)) {
            handleLockDenied(e);
            return;
          }
          toast((e == null ? void 0 : e.message) || "加载失败，请检查网络后重试");
        } finally {
          loading.value = false;
          vue.nextTick(() => refocusScanInput(scanInputRef, 200));
        }
      }
      function selectLine(line) {
        matchedLine.value = line;
        matchFailTip.value = "";
        actualQtyInput.value = line._actual || (line.actualQty != null ? String(line.actualQty) : "");
        highlightLineNo.value = line.lineNo;
      }
      async function onScan(barcode) {
        if (!alive.value || busy.value || loading.value) return;
        const raw = String(barcode || "").trim();
        if (!raw) return;
        scanInput.value = "";
        busy.value = true;
        matchFailTip.value = "";
        try {
          const matched = await matchStockCountLine(billNo.value, { barcodeContent: raw });
          markLineScanned(matched.lineNo);
          matched.labelScanned = true;
          matchedLine.value = matched;
          actualQtyInput.value = matched.actualQty != null && matched.actualQty !== "" ? String(matched.actualQty) : matched.bookQty != null ? String(matched.bookQty) : "";
          highlightLineNo.value = matched.lineNo;
          toast(`已匹配 ${matched.materialCode}`, "success");
        } catch (e) {
          matchedLine.value = null;
          matchFailTip.value = (e == null ? void 0 : e.message) || "未匹配到明细";
          toast(matchFailTip.value);
        } finally {
          busy.value = false;
          refocusScanInput(scanInputRef, 300);
        }
      }
      async function submitMatched() {
        if (!matchedLine.value) {
          toast("请先扫码或点选明细");
          return;
        }
        const qty = Number(actualQtyInput.value);
        if (actualQtyInput.value === "" || Number.isNaN(qty) || qty < 0) {
          toast("请输入合法实盘数量");
          return;
        }
        busy.value = true;
        try {
          const updated = await scanStockCountLine(billNo.value, {
            lineNo: matchedLine.value.lineNo,
            actualQty: qty
          });
          markLineScanned(updated.lineNo);
          patchLine(updated);
          matchedLine.value = updated;
          actualQtyInput.value = updated.actualQty != null ? String(updated.actualQty) : String(qty);
          toast("实盘已保存", "success");
        } catch (e) {
          toast((e == null ? void 0 : e.message) || "提交失败");
        } finally {
          busy.value = false;
          refocusScanInput(scanInputRef, 300);
        }
      }
      async function submitLine(line) {
        const qty = Number(line._actual);
        if (line._actual === "" || Number.isNaN(qty) || qty < 0) {
          toast("请输入合法实盘数量");
          return;
        }
        busy.value = true;
        try {
          const updated = await updateStockCountLineQty(billNo.value, line.lineNo, qty);
          markLineScanned(line.lineNo);
          patchLine(updated);
          toast("数量已修正", "success");
        } catch (e) {
          toast((e == null ? void 0 : e.message) || "修正失败");
        } finally {
          busy.value = false;
        }
      }
      function patchLine(updated) {
        if (!updated) return;
        const idx = lines.value.findIndex((l) => l.lineNo === updated.lineNo);
        if (idx >= 0) {
          lines.value[idx] = {
            ...lines.value[idx],
            ...updated,
            _actual: updated.actualQty != null ? String(updated.actualQty) : ""
          };
        }
        if (detail.value) {
          detail.value.countedLines = lines.value.filter((l) => l.counted).length;
        }
        highlightLineNo.value = updated.lineNo;
      }
      function onComplete() {
        const pending = lines.value.filter((l) => !l.counted).length;
        if (pending > 0) {
          toast(`还有 ${pending} 行未盘点，请先完成`);
          return;
        }
        uni.showModal({
          title: "提交审核",
          content: "确认回写实盘数量并对该盘点单提交审核？",
          success: async (res) => {
            if (!res.confirm) return;
            busy.value = true;
            try {
              await completeStockCount(billNo.value);
              await billLock.releaseLock();
              toast("已提交审核", "success");
              setTimeout(() => uni.navigateBack(), 600);
            } catch (e) {
              if (isBillLockedError(e)) {
                handleLockDenied(e);
                return;
              }
              toast((e == null ? void 0 : e.message) || "提交失败");
            } finally {
              busy.value = false;
            }
          }
        });
      }
      onLoad((query) => {
        billNo.value = decodeURIComponent((query == null ? void 0 : query.billNo) || "").trim();
        uni.setNavigationBarTitle({ title: "盘点作业" });
        if (!billNo.value) {
          toast("缺少盘点单号");
          return;
        }
        reload(false);
      });
      onShow(() => {
        if (billNo.value && detail.value) {
          refocusScanInput(scanInputRef, 300);
        }
      });
      const __returned__ = { billNo, detail, lines, loading, busy, scanInput, scanInputRef, matchedLine, matchFailTip, actualQtyInput, highlightLineNo, scannedLineNos, alive, refocusScanInput, billLock, countedCount, handleLockDenied, isLineLabelScanned, markLineScanned, formatQty: formatQty2, diffClass, toast, applyDetail, reload, selectLine, onScan, submitMatched, submitLine, patchLine, onComplete, ref: vue.ref, computed: vue.computed, nextTick: vue.nextTick, get onLoad() {
        return onLoad;
      }, get onShow() {
        return onShow;
      }, ScanSearchBar, get usePageAlive() {
        return usePageAlive;
      }, get useBillExclusiveLock() {
        return useBillExclusiveLock;
      }, get isBillLockedError() {
        return isBillLockedError;
      }, get getStockCountDetail() {
        return getStockCountDetail;
      }, get matchStockCountLine() {
        return matchStockCountLine;
      }, get scanStockCountLine() {
        return scanStockCountLine;
      }, get updateStockCountLineQty() {
        return updateStockCountLineQty;
      }, get completeStockCount() {
        return completeStockCount;
      }, get heartbeatStockCountLock() {
        return heartbeatStockCountLock;
      }, get releaseStockCountLock() {
        return releaseStockCountLock;
      } };
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  };
  function _sfc_render$o(_ctx, _cache, $props, $setup, $data, $options) {
    return vue.openBlock(), vue.createElementBlock("view", { class: "page" }, [
      vue.createElementVNode("view", { class: "scan-top" }, [
        vue.createVNode($setup["ScanSearchBar"], {
          ref: "scanInputRef",
          modelValue: $setup.scanInput,
          "onUpdate:modelValue": _cache[0] || (_cache[0] = ($event) => $setup.scanInput = $event),
          disabled: $setup.busy || $setup.loading,
          placeholder: "扫物料条码或输入物料编码",
          "action-text": "匹配",
          onScan: $setup.onScan,
          onSearch: $setup.onScan
        }, null, 8, ["modelValue", "disabled"])
      ]),
      $setup.detail ? (vue.openBlock(), vue.createElementBlock("view", {
        key: 0,
        class: "order-bar"
      }, [
        vue.createElementVNode("view", { class: "order-info" }, [
          vue.createElementVNode(
            "text",
            { class: "order-no" },
            vue.toDisplayString($setup.detail.billNo),
            1
            /* TEXT */
          ),
          vue.createElementVNode(
            "text",
            { class: "order-sub" },
            "仓库 " + vue.toDisplayString($setup.detail.warehouseCode || "-") + " · " + vue.toDisplayString($setup.detail.remark || "未审核盘点"),
            1
            /* TEXT */
          )
        ]),
        vue.createElementVNode(
          "text",
          { class: "order-stat" },
          "已盘 " + vue.toDisplayString($setup.countedCount) + "/" + vue.toDisplayString($setup.lines.length),
          1
          /* TEXT */
        )
      ])) : vue.createCommentVNode("v-if", true),
      $setup.matchedLine ? (vue.openBlock(), vue.createElementBlock("view", {
        key: 1,
        class: "match-card"
      }, [
        vue.createElementVNode(
          "text",
          { class: "match-title" },
          "已匹配明细 #" + vue.toDisplayString($setup.matchedLine.lineNo),
          1
          /* TEXT */
        ),
        vue.createElementVNode(
          "text",
          { class: "match-row" },
          "物料 " + vue.toDisplayString($setup.matchedLine.materialCode),
          1
          /* TEXT */
        ),
        vue.createElementVNode(
          "text",
          { class: "match-row" },
          "名称 " + vue.toDisplayString($setup.matchedLine.materialName || "-"),
          1
          /* TEXT */
        ),
        vue.createElementVNode(
          "text",
          { class: "match-row" },
          "规格 " + vue.toDisplayString($setup.matchedLine.specification || "-"),
          1
          /* TEXT */
        ),
        vue.createElementVNode(
          "text",
          { class: "match-row" },
          "应有 " + vue.toDisplayString($setup.formatQty($setup.matchedLine.bookQty)) + " " + vue.toDisplayString($setup.matchedLine.unitCode || ""),
          1
          /* TEXT */
        ),
        vue.createElementVNode("view", { class: "qty-edit" }, [
          vue.createElementVNode("text", { class: "qty-label" }, "实盘数量"),
          vue.withDirectives(vue.createElementVNode("input", {
            "onUpdate:modelValue": _cache[1] || (_cache[1] = ($event) => $setup.actualQtyInput = $event),
            class: "qty-input",
            type: "text",
            inputmode: "decimal",
            placeholder: "输入实盘数",
            disabled: $setup.busy,
            onConfirm: $setup.submitMatched
          }, null, 40, ["disabled"]), [
            [vue.vModelText, $setup.actualQtyInput]
          ]),
          vue.createElementVNode("button", {
            class: "mini-btn",
            type: "primary",
            size: "mini",
            loading: $setup.busy,
            onClick: $setup.submitMatched
          }, " 提交 ", 8, ["loading"])
        ])
      ])) : $setup.matchFailTip ? (vue.openBlock(), vue.createElementBlock("view", {
        key: 2,
        class: "match-card warn"
      }, [
        vue.createElementVNode("text", { class: "match-title" }, "未匹配到盘点明细"),
        vue.createElementVNode(
          "text",
          { class: "match-row" },
          vue.toDisplayString($setup.matchFailTip),
          1
          /* TEXT */
        ),
        vue.createElementVNode("text", { class: "match-row" }, "请核对条码后重新扫描物料标签")
      ])) : (vue.openBlock(), vue.createElementBlock("view", {
        key: 3,
        class: "match-card idle"
      }, [
        vue.createElementVNode("text", { class: "match-title" }, "扫码或点选明细"),
        vue.createElementVNode("text", { class: "match-row" }, "可扫码匹配，也可点选下方明细手动录入实盘数量")
      ])),
      vue.createElementVNode("scroll-view", {
        class: "list-scroll",
        "scroll-y": "",
        "show-scrollbar": false
      }, [
        $setup.loading && !$setup.lines.length ? (vue.openBlock(), vue.createElementBlock("view", {
          key: 0,
          class: "loading-tip"
        }, "加载盘点明细...")) : vue.createCommentVNode("v-if", true),
        (vue.openBlock(true), vue.createElementBlock(
          vue.Fragment,
          null,
          vue.renderList($setup.lines, (line) => {
            return vue.openBlock(), vue.createElementBlock("view", {
              key: line.lineNo,
              class: vue.normalizeClass(["mat-row", line.counted && "done", $setup.highlightLineNo === line.lineNo && "flash"]),
              onClick: ($event) => $setup.selectLine(line)
            }, [
              vue.createElementVNode("view", { class: "row-main" }, [
                vue.createElementVNode("view", { class: "name-row" }, [
                  vue.createElementVNode(
                    "text",
                    { class: "mat-code" },
                    vue.toDisplayString(line.materialCode),
                    1
                    /* TEXT */
                  ),
                  vue.createElementVNode(
                    "text",
                    {
                      class: vue.normalizeClass(["tag", line.counted ? "ok" : "pending"])
                    },
                    vue.toDisplayString(line.counted ? "已盘" : "未盘"),
                    3
                    /* TEXT, CLASS */
                  )
                ]),
                vue.createElementVNode(
                  "text",
                  { class: "mat-name" },
                  vue.toDisplayString(line.materialName || "-"),
                  1
                  /* TEXT */
                ),
                vue.createElementVNode(
                  "text",
                  { class: "mat-spec" },
                  "规格 " + vue.toDisplayString(line.specification || "-"),
                  1
                  /* TEXT */
                ),
                vue.createElementVNode("text", { class: "mat-meta" }, [
                  vue.createTextVNode(
                    " 批次 " + vue.toDisplayString(line.batchNo || "-") + " ",
                    1
                    /* TEXT */
                  ),
                  line.locationCode ? (vue.openBlock(), vue.createElementBlock(
                    "text",
                    { key: 0 },
                    " · 库位 " + vue.toDisplayString(line.locationCode),
                    1
                    /* TEXT */
                  )) : vue.createCommentVNode("v-if", true)
                ]),
                vue.createElementVNode("text", { class: "mat-qty" }, [
                  vue.createTextVNode(
                    " 应有 " + vue.toDisplayString($setup.formatQty(line.bookQty)) + " ",
                    1
                    /* TEXT */
                  ),
                  line.counted ? (vue.openBlock(), vue.createElementBlock(
                    "text",
                    { key: 0 },
                    " · 实盘 " + vue.toDisplayString($setup.formatQty(line.actualQty)),
                    1
                    /* TEXT */
                  )) : vue.createCommentVNode("v-if", true),
                  line.counted && line.diffQty != null ? (vue.openBlock(), vue.createElementBlock(
                    "text",
                    {
                      key: 1,
                      class: vue.normalizeClass($setup.diffClass(line.diffQty))
                    },
                    " · 差 " + vue.toDisplayString($setup.formatQty(line.diffQty)),
                    3
                    /* TEXT, CLASS */
                  )) : vue.createCommentVNode("v-if", true)
                ])
              ]),
              vue.createElementVNode("view", {
                class: "row-side",
                onClick: _cache[2] || (_cache[2] = vue.withModifiers(() => {
                }, ["stop"]))
              }, [
                vue.withDirectives(vue.createElementVNode("input", {
                  "onUpdate:modelValue": ($event) => line._actual = $event,
                  class: "line-qty",
                  type: "text",
                  inputmode: "decimal",
                  placeholder: "实盘",
                  disabled: $setup.busy
                }, null, 8, ["onUpdate:modelValue", "disabled"]), [
                  [vue.vModelText, line._actual]
                ]),
                vue.createElementVNode("button", {
                  size: "mini",
                  type: "primary",
                  disabled: $setup.busy,
                  onClick: ($event) => $setup.submitLine(line)
                }, "改", 8, ["disabled", "onClick"])
              ])
            ], 10, ["onClick"]);
          }),
          128
          /* KEYED_FRAGMENT */
        )),
        !$setup.loading && !$setup.lines.length ? (vue.openBlock(), vue.createElementBlock("view", {
          key: 1,
          class: "empty"
        }, "暂无盘点明细")) : vue.createCommentVNode("v-if", true),
        vue.createElementVNode("view", { class: "scroll-pad" })
      ]),
      vue.createElementVNode("view", { class: "footer" }, [
        vue.createElementVNode("button", {
          class: "refresh-btn",
          disabled: $setup.busy || $setup.loading,
          onClick: _cache[3] || (_cache[3] = ($event) => $setup.reload(true))
        }, "刷新", 8, ["disabled"]),
        vue.createElementVNode("button", {
          class: "complete-btn",
          type: "warn",
          loading: $setup.busy,
          onClick: $setup.onComplete
        }, "提交审核", 8, ["loading"])
      ])
    ]);
  }
  const PagesStockcheckStockcheckScan = /* @__PURE__ */ _export_sfc(_sfc_main$p, [["render", _sfc_render$o], ["__scopeId", "data-v-a5941f36"], ["__file", "D:/AAA/WMS/wms-pda/src/pages/stockcheck/stockcheck-scan.vue"]]);
  const _sfc_main$o = {
    __name: "stockcheck",
    setup(__props, { expose: __expose }) {
      __expose();
      const taskNo = vue.ref("");
      const task = vue.ref(null);
      const details = vue.ref([]);
      const mode = vue.ref("scan");
      const busy = vue.ref(false);
      const highlightLineNo = vue.ref(null);
      const scanInputRef = vue.ref(null);
      const { alive, refocusScanInput } = usePageAlive();
      const scanForm = vue.reactive({
        locationCode: "",
        materialCode: "",
        batchNo: "",
        actualQty: "",
        barcodeContent: ""
      });
      const gainForm = vue.reactive({
        locationCode: "",
        materialCode: "",
        batchNo: "",
        actualQty: "",
        remark: ""
      });
      const emptyForm = vue.reactive({
        locationCode: "",
        materialCode: "",
        batchNo: ""
      });
      const countedCount = vue.computed(
        () => details.value.filter((d) => d.lineStatus === "COUNTED").length
      );
      const matchedLine = vue.computed(() => findMatchLine(scanForm));
      function statusLabel(status) {
        if (status === "COUNTING") return "盘点中";
        if (status === "COMPLETED") return "已完成";
        if (status === "PENDING") return "待盘点";
        return status || "-";
      }
      function lineStatusLabel(status) {
        if (status === "COUNTED") return "已盘";
        if (status === "PENDING") return "未盘";
        return status || "未盘";
      }
      function formatQty2(val) {
        if (val == null || val === "") return "0";
        const n = Number(val);
        return Number.isNaN(n) ? String(val) : String(n);
      }
      function findMatchLine(form) {
        const material = String(form.materialCode || "").trim();
        const location2 = String(form.locationCode || "").trim();
        const batch = String(form.batchNo || "").trim();
        if (!material && !location2) return null;
        const candidates = details.value.filter((d) => {
          if (material && String(d.materialCode || "").trim() !== material) return false;
          if (location2 && String(d.locationCode || "").trim() !== location2) return false;
          if (batch && String(d.batchNo || "").trim() && String(d.batchNo || "").trim() !== batch) return false;
          return true;
        });
        if (!candidates.length) return null;
        return candidates.find((d) => d.lineStatus !== "COUNTED") || candidates[0];
      }
      function switchMode(next) {
        mode.value = next;
        refocusScanInput(scanInputRef, 200);
      }
      function applyParsedToForm(parsed, form) {
        if (parsed.locationCode) form.locationCode = parsed.locationCode;
        if (parsed.materialCode) form.materialCode = parsed.materialCode;
        if (parsed.batchNo) form.batchNo = parsed.batchNo;
      }
      function qtyFromParsed(parsed) {
        const segments = (parsed == null ? void 0 : parsed.segments) || {};
        const raw = segments.qty ?? segments.quantity ?? segments.actualQty;
        if (raw == null || raw === "") return "";
        const n = Number(raw);
        return Number.isNaN(n) ? "" : String(n);
      }
      async function onScan(barcode) {
        if (!alive.value || busy.value) return;
        const raw = String(barcode || "").trim();
        if (!raw) return;
        try {
          const parsed = await resolveBarcode(raw);
          if (mode.value === "gain") {
            applyParsedToForm(parsed, gainForm);
            const qty = qtyFromParsed(parsed);
            if (qty) gainForm.actualQty = qty;
            uni.showToast({ title: "已填充盘盈信息", icon: "success" });
          } else if (mode.value === "empty") {
            applyParsedToForm(parsed, emptyForm);
            uni.showToast({ title: "已填充确认空信息", icon: "success" });
          } else {
            mode.value = "scan";
            applyParsedToForm(parsed, scanForm);
            scanForm.barcodeContent = parsed.barcodeContent || raw;
            const qty = qtyFromParsed(parsed);
            if (qty) {
              scanForm.actualQty = qty;
            } else if (matchedLine.value && (matchedLine.value._actual || matchedLine.value.bookQty) != null) {
              if (!scanForm.actualQty) {
                scanForm.actualQty = formatQty2(matchedLine.value.actualQty ?? matchedLine.value.bookQty);
              }
            }
            const match = findMatchLine(scanForm);
            highlightLineNo.value = (match == null ? void 0 : match.lineNo) || null;
            uni.showToast({
              title: match ? `匹配行 #${match.lineNo}` : "请确认库位物料后提交",
              icon: match ? "success" : "none"
            });
          }
        } catch (e) {
          uni.showToast({ title: (e == null ? void 0 : e.message) || "条码解析失败", icon: "none" });
        } finally {
          refocusScanInput(scanInputRef, 300);
        }
      }
      async function loadTask() {
        if (!taskNo.value) return;
        busy.value = true;
        try {
          const data = await getStockcheckTask(taskNo.value);
          task.value = data.task;
          details.value = (data.details || []).map((d) => ({
            ...d,
            _actual: d.actualQty != null ? formatQty2(d.actualQty) : ""
          }));
        } catch (e) {
          uni.showToast({ title: (e == null ? void 0 : e.message) || "加载盘点任务失败", icon: "none" });
        } finally {
          busy.value = false;
        }
      }
      async function submitLine(line) {
        if (line._actual === "" && line._actual !== 0) {
          uni.showToast({ title: "请输入实盘数量", icon: "none" });
          return;
        }
        busy.value = true;
        try {
          await scanStockcheck(taskNo.value, {
            lineNo: line.lineNo,
            actualQty: Number(line._actual)
          });
          uni.showToast({ title: "已提交", icon: "success" });
          await loadTask();
        } catch (e) {
          uni.showToast({ title: (e == null ? void 0 : e.message) || "提交失败", icon: "none" });
        } finally {
          busy.value = false;
          refocusScanInput(scanInputRef, 300);
        }
      }
      async function submitScanForm() {
        if (!scanForm.materialCode && !scanForm.locationCode) {
          uni.showToast({ title: "请先扫码", icon: "none" });
          return;
        }
        if (scanForm.actualQty === "" || scanForm.actualQty == null) {
          uni.showToast({ title: "请输入实盘数量", icon: "none" });
          return;
        }
        busy.value = true;
        try {
          const payload = {
            locationCode: scanForm.locationCode || void 0,
            materialCode: scanForm.materialCode || void 0,
            batchNo: scanForm.batchNo || void 0,
            actualQty: Number(scanForm.actualQty),
            barcodeContent: scanForm.barcodeContent || void 0
          };
          const match = findMatchLine(scanForm);
          if (match == null ? void 0 : match.lineNo) {
            payload.lineNo = match.lineNo;
          }
          await scanStockcheck(taskNo.value, payload);
          uni.showToast({ title: "扫码盘点已提交", icon: "success" });
          scanForm.actualQty = "";
          scanForm.barcodeContent = "";
          await loadTask();
        } catch (e) {
          uni.showToast({ title: (e == null ? void 0 : e.message) || "提交失败", icon: "none" });
        } finally {
          busy.value = false;
          refocusScanInput(scanInputRef, 300);
        }
      }
      async function submitGain() {
        if (!gainForm.locationCode || !gainForm.materialCode || !gainForm.actualQty) {
          uni.showToast({ title: "请填写盘盈信息", icon: "none" });
          return;
        }
        busy.value = true;
        try {
          await gainStockcheck(taskNo.value, {
            locationCode: gainForm.locationCode,
            materialCode: gainForm.materialCode,
            batchNo: gainForm.batchNo,
            actualQty: Number(gainForm.actualQty),
            remark: gainForm.remark
          });
          uni.showToast({ title: "盘盈已录入", icon: "success" });
          Object.assign(gainForm, { locationCode: "", materialCode: "", batchNo: "", actualQty: "", remark: "" });
          await loadTask();
        } catch (e) {
          uni.showToast({ title: (e == null ? void 0 : e.message) || "提交失败", icon: "none" });
        } finally {
          busy.value = false;
          refocusScanInput(scanInputRef, 300);
        }
      }
      async function submitEmpty() {
        if (!emptyForm.locationCode || !emptyForm.materialCode) {
          uni.showToast({ title: "请填写库位和物料", icon: "none" });
          return;
        }
        busy.value = true;
        try {
          await confirmEmptyStockcheck(taskNo.value, {
            locationCode: emptyForm.locationCode,
            materialCode: emptyForm.materialCode,
            batchNo: emptyForm.batchNo
          });
          uni.showToast({ title: "已确认无库存", icon: "success" });
          Object.assign(emptyForm, { locationCode: "", materialCode: "", batchNo: "" });
          await loadTask();
        } catch (e) {
          uni.showToast({ title: (e == null ? void 0 : e.message) || "提交失败", icon: "none" });
        } finally {
          busy.value = false;
          refocusScanInput(scanInputRef, 300);
        }
      }
      async function complete() {
        busy.value = true;
        try {
          await completeStockcheck(taskNo.value);
          uni.showToast({ title: "盘点完成", icon: "success" });
          setTimeout(() => uni.navigateBack(), 800);
        } catch (e) {
          uni.showToast({ title: (e == null ? void 0 : e.message) || "完成失败", icon: "none" });
          busy.value = false;
        }
      }
      onLoad((options) => {
        taskNo.value = (options == null ? void 0 : options.taskNo) || "";
        uni.setNavigationBarTitle({ title: "扫码盘点" });
        loadTask();
      });
      onShow(() => {
        refocusScanInput(scanInputRef, 400);
      });
      const __returned__ = { taskNo, task, details, mode, busy, highlightLineNo, scanInputRef, alive, refocusScanInput, scanForm, gainForm, emptyForm, countedCount, matchedLine, statusLabel, lineStatusLabel, formatQty: formatQty2, findMatchLine, switchMode, applyParsedToForm, qtyFromParsed, onScan, loadTask, submitLine, submitScanForm, submitGain, submitEmpty, complete, ref: vue.ref, reactive: vue.reactive, computed: vue.computed, get onLoad() {
        return onLoad;
      }, get onShow() {
        return onShow;
      }, CompactScanBox, get usePageAlive() {
        return usePageAlive;
      }, get completeStockcheck() {
        return completeStockcheck;
      }, get confirmEmptyStockcheck() {
        return confirmEmptyStockcheck;
      }, get gainStockcheck() {
        return gainStockcheck;
      }, get getStockcheckTask() {
        return getStockcheckTask;
      }, get scanStockcheck() {
        return scanStockcheck;
      }, get resolveBarcode() {
        return resolveBarcode;
      } };
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  };
  function _sfc_render$n(_ctx, _cache, $props, $setup, $data, $options) {
    return vue.openBlock(), vue.createElementBlock("view", { class: "page" }, [
      vue.createElementVNode("view", { class: "scan-top" }, [
        vue.createVNode($setup["CompactScanBox"], {
          ref: "scanInputRef",
          disabled: $setup.busy,
          onScan: $setup.onScan
        }, null, 8, ["disabled"]),
        vue.createElementVNode("text", { class: "scan-hint" }, "对准物料/库位标签连续扫码盘点")
      ]),
      $setup.task ? (vue.openBlock(), vue.createElementBlock("view", {
        key: 0,
        class: "header"
      }, [
        vue.createElementVNode(
          "text",
          { class: "task-no" },
          vue.toDisplayString($setup.task.taskNo),
          1
          /* TEXT */
        ),
        vue.createElementVNode(
          "text",
          { class: "task-meta" },
          "仓库 " + vue.toDisplayString($setup.task.warehouseCode) + " · " + vue.toDisplayString($setup.statusLabel($setup.task.status)),
          1
          /* TEXT */
        ),
        vue.createElementVNode(
          "text",
          { class: "task-progress" },
          "已盘 " + vue.toDisplayString($setup.countedCount) + "/" + vue.toDisplayString($setup.details.length),
          1
          /* TEXT */
        )
      ])) : vue.createCommentVNode("v-if", true),
      vue.createElementVNode("view", { class: "tabs" }, [
        vue.createElementVNode(
          "text",
          {
            class: vue.normalizeClass(["tab", $setup.mode === "scan" && "active"]),
            onClick: _cache[0] || (_cache[0] = ($event) => $setup.switchMode("scan"))
          },
          "扫码盘点",
          2
          /* CLASS */
        ),
        vue.createElementVNode(
          "text",
          {
            class: vue.normalizeClass(["tab", $setup.mode === "line" && "active"]),
            onClick: _cache[1] || (_cache[1] = ($event) => $setup.switchMode("line"))
          },
          "明细",
          2
          /* CLASS */
        ),
        vue.createElementVNode(
          "text",
          {
            class: vue.normalizeClass(["tab", $setup.mode === "gain" && "active"]),
            onClick: _cache[2] || (_cache[2] = ($event) => $setup.switchMode("gain"))
          },
          "盘盈",
          2
          /* CLASS */
        ),
        vue.createElementVNode(
          "text",
          {
            class: vue.normalizeClass(["tab", $setup.mode === "empty" && "active"]),
            onClick: _cache[3] || (_cache[3] = ($event) => $setup.switchMode("empty"))
          },
          "确认空",
          2
          /* CLASS */
        )
      ]),
      vue.createElementVNode("scroll-view", {
        class: "body-scroll",
        "scroll-y": "",
        "show-scrollbar": false
      }, [
        $setup.mode === "scan" ? (vue.openBlock(), vue.createElementBlock("view", {
          key: 0,
          class: "form-card"
        }, [
          $setup.matchedLine ? (vue.openBlock(), vue.createElementBlock("view", {
            key: 0,
            class: "match-box"
          }, [
            vue.createElementVNode(
              "text",
              { class: "match-title" },
              "已匹配明细 #" + vue.toDisplayString($setup.matchedLine.lineNo),
              1
              /* TEXT */
            ),
            vue.createElementVNode(
              "text",
              { class: "match-row" },
              "物料 " + vue.toDisplayString($setup.matchedLine.materialCode),
              1
              /* TEXT */
            ),
            vue.createElementVNode(
              "text",
              { class: "match-row" },
              "库位 " + vue.toDisplayString($setup.matchedLine.locationCode || "-"),
              1
              /* TEXT */
            ),
            vue.createElementVNode(
              "text",
              { class: "match-row" },
              "批次 " + vue.toDisplayString($setup.matchedLine.batchNo || "-"),
              1
              /* TEXT */
            ),
            vue.createElementVNode(
              "text",
              { class: "match-row" },
              "账面 " + vue.toDisplayString($setup.formatQty($setup.matchedLine.bookQty)) + " · 状态 " + vue.toDisplayString($setup.lineStatusLabel($setup.matchedLine.lineStatus)),
              1
              /* TEXT */
            )
          ])) : $setup.scanForm.materialCode || $setup.scanForm.locationCode ? (vue.openBlock(), vue.createElementBlock("view", {
            key: 1,
            class: "match-box warn"
          }, [
            vue.createElementVNode("text", { class: "match-title" }, "未匹配到盘点明细"),
            vue.createElementVNode("text", { class: "match-row" }, "可改数量后提交，或切到「盘盈」录入")
          ])) : (vue.openBlock(), vue.createElementBlock("view", {
            key: 2,
            class: "empty-scan"
          }, [
            vue.createElementVNode("text", null, "请扫描物料标签开始盘点")
          ])),
          vue.createElementVNode("view", { class: "field" }, [
            vue.createElementVNode("text", { class: "label" }, "库位"),
            vue.withDirectives(vue.createElementVNode(
              "input",
              {
                "onUpdate:modelValue": _cache[4] || (_cache[4] = ($event) => $setup.scanForm.locationCode = $event),
                class: "input",
                placeholder: "可扫库位码或手输"
              },
              null,
              512
              /* NEED_PATCH */
            ), [
              [vue.vModelText, $setup.scanForm.locationCode]
            ])
          ]),
          vue.createElementVNode("view", { class: "field" }, [
            vue.createElementVNode("text", { class: "label" }, "物料"),
            vue.withDirectives(vue.createElementVNode(
              "input",
              {
                "onUpdate:modelValue": _cache[5] || (_cache[5] = ($event) => $setup.scanForm.materialCode = $event),
                class: "input",
                placeholder: "扫码自动带出"
              },
              null,
              512
              /* NEED_PATCH */
            ), [
              [vue.vModelText, $setup.scanForm.materialCode]
            ])
          ]),
          vue.createElementVNode("view", { class: "field" }, [
            vue.createElementVNode("text", { class: "label" }, "批次"),
            vue.withDirectives(vue.createElementVNode(
              "input",
              {
                "onUpdate:modelValue": _cache[6] || (_cache[6] = ($event) => $setup.scanForm.batchNo = $event),
                class: "input",
                placeholder: "可选"
              },
              null,
              512
              /* NEED_PATCH */
            ), [
              [vue.vModelText, $setup.scanForm.batchNo]
            ])
          ]),
          vue.createElementVNode("view", { class: "field qty-field" }, [
            vue.createElementVNode("text", { class: "label" }, "实盘数量"),
            vue.withDirectives(vue.createElementVNode(
              "input",
              {
                "onUpdate:modelValue": _cache[7] || (_cache[7] = ($event) => $setup.scanForm.actualQty = $event),
                class: "input qty-input",
                type: "text",
                inputmode: "decimal",
                placeholder: "输入实盘数",
                onConfirm: $setup.submitScanForm
              },
              null,
              544
              /* NEED_HYDRATION, NEED_PATCH */
            ), [
              [vue.vModelText, $setup.scanForm.actualQty]
            ])
          ]),
          vue.createElementVNode("button", {
            class: "primary-btn",
            type: "primary",
            loading: $setup.busy,
            onClick: $setup.submitScanForm
          }, " 提交本行盘点 ", 8, ["loading"])
        ])) : $setup.mode === "line" ? (vue.openBlock(), vue.createElementBlock("view", { key: 1 }, [
          (vue.openBlock(true), vue.createElementBlock(
            vue.Fragment,
            null,
            vue.renderList($setup.details, (line) => {
              return vue.openBlock(), vue.createElementBlock(
                "view",
                {
                  key: line.lineNo,
                  class: vue.normalizeClass(["line-card", line.lineStatus === "COUNTED" && "done", $setup.highlightLineNo === line.lineNo && "flash"])
                },
                [
                  vue.createElementVNode(
                    "text",
                    { class: "name" },
                    vue.toDisplayString(line.materialCode),
                    1
                    /* TEXT */
                  ),
                  vue.createElementVNode(
                    "text",
                    { class: "meta" },
                    "库位 " + vue.toDisplayString(line.locationCode) + " · 批次 " + vue.toDisplayString(line.batchNo || "-"),
                    1
                    /* TEXT */
                  ),
                  vue.createElementVNode(
                    "text",
                    { class: "meta" },
                    "账面 " + vue.toDisplayString($setup.formatQty(line.bookQty)) + " · " + vue.toDisplayString($setup.lineStatusLabel(line.lineStatus)),
                    1
                    /* TEXT */
                  ),
                  vue.createElementVNode("view", { class: "row" }, [
                    vue.withDirectives(vue.createElementVNode("input", {
                      "onUpdate:modelValue": ($event) => line._actual = $event,
                      class: "qty-input",
                      type: "text",
                      inputmode: "decimal",
                      placeholder: "实盘数量"
                    }, null, 8, ["onUpdate:modelValue"]), [
                      [vue.vModelText, line._actual]
                    ]),
                    vue.createElementVNode("button", {
                      size: "mini",
                      type: "primary",
                      disabled: $setup.busy,
                      onClick: ($event) => $setup.submitLine(line)
                    }, "提交", 8, ["disabled", "onClick"])
                  ])
                ],
                2
                /* CLASS */
              );
            }),
            128
            /* KEYED_FRAGMENT */
          )),
          !$setup.details.length ? (vue.openBlock(), vue.createElementBlock("view", {
            key: 0,
            class: "empty-scan"
          }, "暂无盘点明细")) : vue.createCommentVNode("v-if", true)
        ])) : $setup.mode === "gain" ? (vue.openBlock(), vue.createElementBlock("view", {
          key: 2,
          class: "form-card"
        }, [
          vue.createElementVNode("text", { class: "section-tip" }, "扫码可自动填充库位/物料/批次"),
          vue.withDirectives(vue.createElementVNode(
            "input",
            {
              "onUpdate:modelValue": _cache[8] || (_cache[8] = ($event) => $setup.gainForm.locationCode = $event),
              class: "input",
              placeholder: "库位"
            },
            null,
            512
            /* NEED_PATCH */
          ), [
            [vue.vModelText, $setup.gainForm.locationCode]
          ]),
          vue.withDirectives(vue.createElementVNode(
            "input",
            {
              "onUpdate:modelValue": _cache[9] || (_cache[9] = ($event) => $setup.gainForm.materialCode = $event),
              class: "input",
              placeholder: "物料编码"
            },
            null,
            512
            /* NEED_PATCH */
          ), [
            [vue.vModelText, $setup.gainForm.materialCode]
          ]),
          vue.withDirectives(vue.createElementVNode(
            "input",
            {
              "onUpdate:modelValue": _cache[10] || (_cache[10] = ($event) => $setup.gainForm.batchNo = $event),
              class: "input",
              placeholder: "批次号"
            },
            null,
            512
            /* NEED_PATCH */
          ), [
            [vue.vModelText, $setup.gainForm.batchNo]
          ]),
          vue.withDirectives(vue.createElementVNode(
            "input",
            {
              "onUpdate:modelValue": _cache[11] || (_cache[11] = ($event) => $setup.gainForm.actualQty = $event),
              class: "input",
              type: "text",
              inputmode: "decimal",
              placeholder: "盘盈数量"
            },
            null,
            512
            /* NEED_PATCH */
          ), [
            [vue.vModelText, $setup.gainForm.actualQty]
          ]),
          vue.withDirectives(vue.createElementVNode(
            "input",
            {
              "onUpdate:modelValue": _cache[12] || (_cache[12] = ($event) => $setup.gainForm.remark = $event),
              class: "input",
              placeholder: "备注"
            },
            null,
            512
            /* NEED_PATCH */
          ), [
            [vue.vModelText, $setup.gainForm.remark]
          ]),
          vue.createElementVNode("button", {
            class: "primary-btn",
            type: "primary",
            loading: $setup.busy,
            onClick: $setup.submitGain
          }, "提交盘盈", 8, ["loading"])
        ])) : (vue.openBlock(), vue.createElementBlock("view", {
          key: 3,
          class: "form-card"
        }, [
          vue.createElementVNode("text", { class: "section-tip" }, "扫码可自动填充后确认无库存（实盘 0）"),
          vue.withDirectives(vue.createElementVNode(
            "input",
            {
              "onUpdate:modelValue": _cache[13] || (_cache[13] = ($event) => $setup.emptyForm.locationCode = $event),
              class: "input",
              placeholder: "库位"
            },
            null,
            512
            /* NEED_PATCH */
          ), [
            [vue.vModelText, $setup.emptyForm.locationCode]
          ]),
          vue.withDirectives(vue.createElementVNode(
            "input",
            {
              "onUpdate:modelValue": _cache[14] || (_cache[14] = ($event) => $setup.emptyForm.materialCode = $event),
              class: "input",
              placeholder: "物料编码"
            },
            null,
            512
            /* NEED_PATCH */
          ), [
            [vue.vModelText, $setup.emptyForm.materialCode]
          ]),
          vue.withDirectives(vue.createElementVNode(
            "input",
            {
              "onUpdate:modelValue": _cache[15] || (_cache[15] = ($event) => $setup.emptyForm.batchNo = $event),
              class: "input",
              placeholder: "批次号(可选)"
            },
            null,
            512
            /* NEED_PATCH */
          ), [
            [vue.vModelText, $setup.emptyForm.batchNo]
          ]),
          vue.createElementVNode("button", {
            class: "warn-btn",
            type: "warn",
            loading: $setup.busy,
            onClick: $setup.submitEmpty
          }, "确认无库存", 8, ["loading"])
        ])),
        vue.createElementVNode("view", { class: "scroll-pad" })
      ]),
      vue.createElementVNode("view", { class: "footer" }, [
        vue.createElementVNode("button", {
          class: "complete-btn",
          type: "warn",
          loading: $setup.busy,
          onClick: $setup.complete
        }, "完成盘点", 8, ["loading"])
      ])
    ]);
  }
  const PagesStockcheckStockcheck = /* @__PURE__ */ _export_sfc(_sfc_main$o, [["render", _sfc_render$n], ["__scopeId", "data-v-dc8a8113"], ["__file", "D:/AAA/WMS/wms-pda/src/pages/stockcheck/stockcheck.vue"]]);
  const _sfc_main$n = {
    __name: "tasklist",
    setup(__props, { expose: __expose }) {
      __expose();
      const moduleType = vue.ref("stockcheck");
      const tasks = vue.ref({});
      const loaded = vue.ref(false);
      const moduleConfig = vue.computed(() => {
        const map = {
          stockcheck: { label: "盘点", title: "盘点作业", icon: "📋" }
        };
        return map[moduleType.value] || map.stockcheck;
      });
      const taskList = vue.computed(() => {
        var _a;
        const raw = ((_a = tasks.value[moduleType.value]) == null ? void 0 : _a.tasks) || [];
        return raw.map((item) => {
          const billNo = item.billNo || item.taskNo;
          const counted = item.countedLines != null ? ` · 已盘 ${item.countedLines}` : "";
          const statusText = item.status === "COUNTING" ? "盘点中" : "待盘点";
          return {
            ...item,
            billNo,
            _key: billNo,
            _title: billNo,
            _meta: `仓库 ${item.warehouseCode || "-"} · ${statusText}${counted}`
          };
        });
      });
      onLoad((options) => {
        moduleType.value = (options == null ? void 0 : options.type) || "stockcheck";
        if (moduleType.value === "stockcheck") {
          uni.redirectTo({ url: "/pages/stockcheck/stockcheck-list" });
          return;
        }
        uni.setNavigationBarTitle({ title: moduleConfig.value.title });
        loadData();
      });
      onShow(() => {
        if (moduleType.value !== "stockcheck") {
          loadData();
        }
      });
      onPullDownRefresh(async () => {
        await loadData();
        uni.stopPullDownRefresh();
      });
      async function loadData() {
        try {
          tasks.value = await getTasks();
        } catch {
          tasks.value = {};
        } finally {
          loaded.value = true;
        }
      }
      function handleClick(item) {
        const billNo = item.billNo || item.taskNo;
        if (!billNo) return;
        uni.navigateTo({
          url: `/pages/stockcheck/stockcheck-scan?billNo=${encodeURIComponent(billNo)}`
        });
      }
      const __returned__ = { moduleType, tasks, loaded, moduleConfig, taskList, loadData, handleClick, ref: vue.ref, computed: vue.computed, get onLoad() {
        return onLoad;
      }, get onPullDownRefresh() {
        return onPullDownRefresh;
      }, get onShow() {
        return onShow;
      }, get getTasks() {
        return getTasks;
      } };
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  };
  function _sfc_render$m(_ctx, _cache, $props, $setup, $data, $options) {
    return vue.openBlock(), vue.createElementBlock("view", { class: "page" }, [
      vue.createElementVNode("view", { class: "header" }, [
        vue.createElementVNode(
          "text",
          { class: "title" },
          vue.toDisplayString($setup.moduleConfig.title),
          1
          /* TEXT */
        ),
        vue.createElementVNode(
          "text",
          { class: "desc" },
          "共 " + vue.toDisplayString($setup.taskList.length) + " 条待办",
          1
          /* TEXT */
        )
      ]),
      (vue.openBlock(true), vue.createElementBlock(
        vue.Fragment,
        null,
        vue.renderList($setup.taskList, (item) => {
          return vue.openBlock(), vue.createElementBlock("view", {
            key: item._key,
            class: "task-card",
            onClick: ($event) => $setup.handleClick(item)
          }, [
            vue.createElementVNode(
              "text",
              { class: "order-no" },
              vue.toDisplayString(item._title),
              1
              /* TEXT */
            ),
            vue.createElementVNode(
              "text",
              { class: "meta" },
              vue.toDisplayString(item._meta),
              1
              /* TEXT */
            )
          ], 8, ["onClick"]);
        }),
        128
        /* KEYED_FRAGMENT */
      )),
      $setup.loaded && !$setup.taskList.length ? (vue.openBlock(), vue.createElementBlock("view", {
        key: 0,
        class: "empty"
      }, [
        vue.createElementVNode(
          "text",
          { class: "empty-icon" },
          vue.toDisplayString($setup.moduleConfig.icon),
          1
          /* TEXT */
        ),
        vue.createElementVNode(
          "text",
          null,
          "暂无" + vue.toDisplayString($setup.moduleConfig.label) + "任务",
          1
          /* TEXT */
        )
      ])) : vue.createCommentVNode("v-if", true)
    ]);
  }
  const PagesTasklistTasklist = /* @__PURE__ */ _export_sfc(_sfc_main$n, [["render", _sfc_render$m], ["__scopeId", "data-v-8f3ed671"], ["__file", "D:/AAA/WMS/wms-pda/src/pages/tasklist/tasklist.vue"]]);
  const _sfc_main$m = {
    __name: "panel",
    setup(__props, { expose: __expose }) {
      __expose();
      const billNo = vue.ref("");
      const billInput = vue.ref("");
      const matInput = vue.ref("");
      const lines = vue.ref([]);
      const loading = vue.ref(false);
      const busy = vue.ref(false);
      const lastResult = vue.ref(null);
      const flashJobId = vue.ref("");
      const billScanRef = vue.ref(null);
      const matScanRef = vue.ref(null);
      const history = vue.ref(uni.getStorageSync("barcode_verify_history") || []);
      const { alive, refocusScanInput } = usePageAlive();
      const verifiedCount = vue.computed(() => lines.value.filter((l) => l.verified).length);
      function formatQty2(val) {
        if (val == null || val === "") return "-";
        const n = Number(val);
        return Number.isNaN(n) ? String(val) : String(n);
      }
      function toast(title, icon = "none") {
        uni.showToast({ title, icon, duration: 2200 });
      }
      async function loadBill(no) {
        const trimmed = String(no || "").trim();
        if (!trimmed) return;
        loading.value = true;
        try {
          const data = await getBarcodeBillDetail(trimmed);
          billNo.value = data.billNo || trimmed;
          lines.value = (data.lines || []).map((l) => ({ ...l, verified: false }));
          lastResult.value = null;
          billInput.value = "";
          vue.nextTick(() => refocusScanInput(matScanRef, 300));
        } catch (e) {
          toast((e == null ? void 0 : e.message) || "加载单据失败");
        } finally {
          loading.value = false;
        }
      }
      async function onBillScan(barcode) {
        if (!alive.value || loading.value) return;
        const raw = String(barcode || billInput.value || "").trim();
        if (!raw) return;
        billInput.value = "";
        try {
          const res = await resolveBarcodeBill(raw);
          const no = ((res == null ? void 0 : res.billNo) || raw).trim();
          if (!no) {
            toast("未识别到单据号");
            return;
          }
          await loadBill(no);
        } catch {
          await loadBill(raw);
        } finally {
          refocusScanInput(billScanRef, 300);
        }
      }
      async function onMaterialScan(barcode) {
        if (!alive.value || busy.value || !billNo.value) return;
        const raw = String(barcode || matInput.value || "").trim();
        if (!raw) return;
        matInput.value = "";
        busy.value = true;
        lastResult.value = null;
        try {
          const data = await verifyBarcodeMaterial(billNo.value, raw);
          lastResult.value = data;
          if (data.valid && data.matchedLine) {
            const jobId = data.matchedLine.jobId;
            const idx = lines.value.findIndex((l) => l.jobId === jobId);
            if (idx >= 0) {
              lines.value[idx] = { ...lines.value[idx], verified: true };
            } else {
              lines.value.push({ ...data.matchedLine, verified: true });
            }
            flashJobId.value = jobId;
            pushHistory(data);
            toast("校验通过", "success");
          } else {
            toast(data.message || "校验失败");
          }
        } catch (e) {
          toast((e == null ? void 0 : e.message) || "校验失败");
        } finally {
          busy.value = false;
          refocusScanInput(matScanRef, 300);
        }
      }
      function pushHistory(data) {
        const line = data.matchedLine || {};
        const record = {
          billNo: billNo.value,
          materialCode: line.materialCode,
          valid: !!data.valid,
          verifyTime: data.verifyTime
        };
        history.value = [record, ...history.value.filter(
          (h) => !(h.billNo === record.billNo && h.materialCode === record.materialCode)
        )].slice(0, 10);
        uni.setStorageSync("barcode_verify_history", history.value);
      }
      function resetBill() {
        billNo.value = "";
        lines.value = [];
        lastResult.value = null;
        matInput.value = "";
        billInput.value = "";
        vue.nextTick(() => refocusScanInput(billScanRef, 300));
      }
      onLoad(() => uni.setNavigationBarTitle({ title: "条码校验" }));
      onShow(() => {
        if (!billNo.value) refocusScanInput(billScanRef, 300);
        else refocusScanInput(matScanRef, 300);
      });
      const __returned__ = { billNo, billInput, matInput, lines, loading, busy, lastResult, flashJobId, billScanRef, matScanRef, history, alive, refocusScanInput, verifiedCount, formatQty: formatQty2, toast, loadBill, onBillScan, onMaterialScan, pushHistory, resetBill, ref: vue.ref, computed: vue.computed, nextTick: vue.nextTick, get onLoad() {
        return onLoad;
      }, get onShow() {
        return onShow;
      }, ScanSearchBar, get usePageAlive() {
        return usePageAlive;
      }, get resolveBarcodeBill() {
        return resolveBarcodeBill;
      }, get getBarcodeBillDetail() {
        return getBarcodeBillDetail;
      }, get verifyBarcodeMaterial() {
        return verifyBarcodeMaterial;
      } };
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  };
  function _sfc_render$l(_ctx, _cache, $props, $setup, $data, $options) {
    return vue.openBlock(), vue.createElementBlock("view", { class: "page" }, [
      vue.createCommentVNode(" 步骤1：扫单据 "),
      !$setup.billNo ? (vue.openBlock(), vue.createElementBlock("view", {
        key: 0,
        class: "step-card"
      }, [
        vue.createElementVNode("text", { class: "step-title" }, "第 1 步：扫描单据条码"),
        vue.createElementVNode("text", { class: "step-hint" }, "扫描收料通知单/标签源单二维码，加载待校验物料明细"),
        vue.createVNode($setup["ScanSearchBar"], {
          ref: "billScanRef",
          modelValue: $setup.billInput,
          "onUpdate:modelValue": _cache[0] || (_cache[0] = ($event) => $setup.billInput = $event),
          placeholder: "扫码或输入单据号",
          "action-text": "加载",
          disabled: $setup.loading,
          onScan: $setup.onBillScan,
          onSearch: $setup.onBillScan
        }, null, 8, ["modelValue", "disabled"]),
        $setup.loading ? (vue.openBlock(), vue.createElementBlock("view", {
          key: 0,
          class: "loading-tip"
        }, "加载单据明细...")) : vue.createCommentVNode("v-if", true)
      ])) : (vue.openBlock(), vue.createElementBlock(
        vue.Fragment,
        { key: 1 },
        [
          vue.createCommentVNode(" 步骤2：扫物料 "),
          vue.createElementVNode("view", { class: "bill-bar" }, [
            vue.createElementVNode("view", { class: "bill-info" }, [
              vue.createElementVNode(
                "text",
                { class: "bill-no" },
                vue.toDisplayString($setup.billNo),
                1
                /* TEXT */
              ),
              vue.createElementVNode(
                "text",
                { class: "bill-sub" },
                "待校验 " + vue.toDisplayString($setup.lines.length) + " 条 · 已通过 " + vue.toDisplayString($setup.verifiedCount),
                1
                /* TEXT */
              )
            ]),
            vue.createElementVNode("text", {
              class: "link",
              onClick: $setup.resetBill
            }, "换单")
          ]),
          vue.createElementVNode("view", { class: "scan-top" }, [
            vue.createVNode($setup["ScanSearchBar"], {
              ref: "matScanRef",
              modelValue: $setup.matInput,
              "onUpdate:modelValue": _cache[1] || (_cache[1] = ($event) => $setup.matInput = $event),
              placeholder: "扫描物料标签二维码",
              "action-text": "校验",
              disabled: $setup.busy,
              onScan: $setup.onMaterialScan,
              onSearch: $setup.onMaterialScan
            }, null, 8, ["modelValue", "disabled"])
          ]),
          $setup.lastResult ? (vue.openBlock(), vue.createElementBlock(
            "view",
            {
              key: 0,
              class: vue.normalizeClass(["result-card", $setup.lastResult.valid ? "pass" : "fail"])
            },
            [
              vue.createElementVNode(
                "text",
                { class: "result-title" },
                vue.toDisplayString($setup.lastResult.valid ? "校验通过" : "校验失败"),
                1
                /* TEXT */
              ),
              vue.createElementVNode(
                "text",
                { class: "result-msg" },
                vue.toDisplayString($setup.lastResult.message),
                1
                /* TEXT */
              ),
              $setup.lastResult.matchedLine ? (vue.openBlock(), vue.createElementBlock(
                "text",
                {
                  key: 0,
                  class: "result-detail"
                },
                vue.toDisplayString($setup.lastResult.matchedLine.materialCode) + " · " + vue.toDisplayString($setup.lastResult.matchedLine.materialName || "-"),
                1
                /* TEXT */
              )) : vue.createCommentVNode("v-if", true)
            ],
            2
            /* CLASS */
          )) : vue.createCommentVNode("v-if", true),
          vue.createElementVNode("scroll-view", {
            class: "list-scroll",
            "scroll-y": "",
            "show-scrollbar": false
          }, [
            (vue.openBlock(true), vue.createElementBlock(
              vue.Fragment,
              null,
              vue.renderList($setup.lines, (line) => {
                return vue.openBlock(), vue.createElementBlock(
                  "view",
                  {
                    key: line.jobId,
                    class: vue.normalizeClass(["line-row", line.verified && "done", $setup.flashJobId === line.jobId && "flash"])
                  },
                  [
                    vue.createElementVNode("view", { class: "line-main" }, [
                      vue.createElementVNode("view", { class: "name-row" }, [
                        vue.createElementVNode(
                          "text",
                          { class: "mat-code" },
                          vue.toDisplayString(line.materialCode),
                          1
                          /* TEXT */
                        ),
                        vue.createElementVNode(
                          "text",
                          {
                            class: vue.normalizeClass(["tag", line.verified ? "ok" : "pending"])
                          },
                          vue.toDisplayString(line.verified ? "已通过" : "待校验"),
                          3
                          /* TEXT, CLASS */
                        )
                      ]),
                      vue.createElementVNode(
                        "text",
                        { class: "mat-name" },
                        vue.toDisplayString(line.materialName || "-"),
                        1
                        /* TEXT */
                      ),
                      vue.createElementVNode(
                        "text",
                        { class: "mat-spec" },
                        "规格 " + vue.toDisplayString(line.specification || "-"),
                        1
                        /* TEXT */
                      ),
                      vue.createElementVNode(
                        "text",
                        { class: "mat-meta" },
                        " 批次 " + vue.toDisplayString(line.batchNo || "-") + " · 数量 " + vue.toDisplayString($setup.formatQty(line.quantity)) + " " + vue.toDisplayString(line.unitCode || ""),
                        1
                        /* TEXT */
                      )
                    ])
                  ],
                  2
                  /* CLASS */
                );
              }),
              128
              /* KEYED_FRAGMENT */
            )),
            vue.createElementVNode("view", { class: "scroll-pad" })
          ])
        ],
        64
        /* STABLE_FRAGMENT */
      )),
      $setup.history.length ? (vue.openBlock(), vue.createElementBlock("view", {
        key: 2,
        class: "history-section"
      }, [
        vue.createElementVNode("text", { class: "section-title" }, "最近校验"),
        (vue.openBlock(true), vue.createElementBlock(
          vue.Fragment,
          null,
          vue.renderList($setup.history, (h, i) => {
            return vue.openBlock(), vue.createElementBlock("view", {
              key: i,
              class: "history-item"
            }, [
              vue.createElementVNode(
                "text",
                {
                  class: vue.normalizeClass(h.valid ? "ok" : "ng")
                },
                vue.toDisplayString(h.valid ? "PASS" : "FAIL"),
                3
                /* TEXT, CLASS */
              ),
              vue.createElementVNode(
                "text",
                null,
                vue.toDisplayString(h.billNo) + " · " + vue.toDisplayString(h.materialCode || "-"),
                1
                /* TEXT */
              ),
              vue.createElementVNode(
                "text",
                { class: "time" },
                vue.toDisplayString(h.verifyTime),
                1
                /* TEXT */
              )
            ]);
          }),
          128
          /* KEYED_FRAGMENT */
        ))
      ])) : vue.createCommentVNode("v-if", true)
    ]);
  }
  const PagesPanelPanel = /* @__PURE__ */ _export_sfc(_sfc_main$m, [["render", _sfc_render$l], ["__scopeId", "data-v-c1760e80"], ["__file", "D:/AAA/WMS/wms-pda/src/pages/panel/panel.vue"]]);
  const _sfc_main$l = {
    __name: "trace",
    setup(__props, { expose: __expose }) {
      __expose();
      const TXN_LABELS = {
        PURCHASE_IN: "采购入库",
        PRODUCTION_IN: "生产汇报入库",
        OTHER_IN: "其他入库",
        SALES_OUT: "销售出库",
        PRODUCTION_OUT: "生产领料",
        PRODUCTION_FEED: "生产补料",
        OUTSOURCE_FEED: "委外补料",
        OTHER_OUT: "其他出库",
        TRANSFER_OUT: "移库出",
        TRANSFER_IN: "移库入",
        STOCKTAKE_GAIN: "盘盈",
        STOCKTAKE_LOSS: "盘亏",
        WORKSHOP_RETURN: "车间退库",
        PRODUCTION_RETURN: "生产退料",
        PRODUCTION_RET_STOCK: "生产退库"
      };
      const mode = vue.ref("code");
      const materialCode = vue.ref("");
      const batchNo = vue.ref("");
      const barcode = vue.ref("");
      const loading = vue.ref(false);
      const searched = vue.ref(false);
      const result = vue.reactive({});
      const records = vue.ref([]);
      const scanInputRef = vue.ref(null);
      const { alive, refocusScanInput } = usePageAlive();
      const hasResult = vue.computed(
        () => !!(result.materialCode || result.batchNo || result.materialName || records.value.length)
      );
      function formatQty2(v) {
        if (v == null || v === "") return "-";
        const n = Number(v);
        if (Number.isNaN(n)) return String(v);
        return Number.isInteger(n) ? String(n) : String(Math.round(n * 1e3) / 1e3);
      }
      function formatTime(v) {
        if (!v) return "-";
        const s = String(v).replace("T", " ");
        return s.length > 19 ? s.slice(0, 19) : s;
      }
      function txnLabel(type) {
        if (!type) return "未知";
        return TXN_LABELS[type] || type;
      }
      function txnTone(type) {
        const t = String(type || "");
        if (t.includes("IN") || t.includes("GAIN") || t.includes("RETURN")) return "in";
        if (t.includes("OUT") || t.includes("LOSS") || t.includes("FEED")) return "out";
        return "neutral";
      }
      function toast(title) {
        uni.showToast({ title, icon: "none" });
      }
      function clearResult() {
        searched.value = false;
        records.value = [];
        Object.keys(result).forEach((k) => delete result[k]);
      }
      function switchMode(next) {
        if (mode.value === next) return;
        mode.value = next;
        clearResult();
        if (next === "barcode") {
          barcode.value = "";
          refocusScanInput(scanInputRef, 200);
        }
      }
      function applyResult(data) {
        Object.keys(result).forEach((k) => delete result[k]);
        Object.assign(result, data || {});
        records.value = (data == null ? void 0 : data.traceRecords) || [];
      }
      async function doTrace(payload) {
        if (!alive.value || loading.value) return;
        loading.value = true;
        searched.value = true;
        records.value = [];
        Object.keys(result).forEach((k) => delete result[k]);
        try {
          const data = await traceBatch(payload);
          applyResult(data);
          if (!records.value.length) toast("暂无追溯记录");
        } catch (e) {
          toast((e == null ? void 0 : e.message) || "追溯失败");
        } finally {
          loading.value = false;
        }
      }
      async function onCodeSearch() {
        const mat = (materialCode.value || "").trim();
        const batch = (batchNo.value || "").trim();
        if (!mat && !batch) {
          toast("请输入物料编码或批次号");
          return;
        }
        await doTrace({
          materialCode: mat || void 0,
          batchNo: batch || void 0
        });
      }
      async function onBarcodeScan(raw) {
        const code = (raw || barcode.value || "").trim();
        if (!code) {
          toast("请先扫码");
          return;
        }
        barcode.value = code;
        await doTrace({ barcode: code });
        refocusScanInput(scanInputRef, 300);
      }
      onLoad(() => uni.setNavigationBarTitle({ title: "批次追溯" }));
      onShow(() => {
        if (mode.value === "barcode") refocusScanInput(scanInputRef, 300);
      });
      vue.onMounted(() => {
        if (mode.value === "barcode") refocusScanInput(scanInputRef, 400);
      });
      const __returned__ = { TXN_LABELS, mode, materialCode, batchNo, barcode, loading, searched, result, records, scanInputRef, alive, refocusScanInput, hasResult, formatQty: formatQty2, formatTime, txnLabel, txnTone, toast, clearResult, switchMode, applyResult, doTrace, onCodeSearch, onBarcodeScan, ref: vue.ref, reactive: vue.reactive, computed: vue.computed, onMounted: vue.onMounted, get onLoad() {
        return onLoad;
      }, get onShow() {
        return onShow;
      }, ScanSearchBar, get traceBatch() {
        return traceBatch;
      }, get usePageAlive() {
        return usePageAlive;
      } };
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  };
  function _sfc_render$k(_ctx, _cache, $props, $setup, $data, $options) {
    return vue.openBlock(), vue.createElementBlock("view", { class: "page" }, [
      vue.createElementVNode("view", { class: "tab-bar" }, [
        vue.createElementVNode(
          "view",
          {
            class: vue.normalizeClass(["tab-item", $setup.mode === "code" && "active"]),
            onClick: _cache[0] || (_cache[0] = ($event) => $setup.switchMode("code"))
          },
          [
            vue.createElementVNode("text", { class: "tab-text" }, "条件查询")
          ],
          2
          /* CLASS */
        ),
        vue.createElementVNode(
          "view",
          {
            class: vue.normalizeClass(["tab-item", $setup.mode === "barcode" && "active"]),
            onClick: _cache[1] || (_cache[1] = ($event) => $setup.switchMode("barcode"))
          },
          [
            vue.createElementVNode("text", { class: "tab-text" }, "扫码追溯")
          ],
          2
          /* CLASS */
        ),
        vue.createElementVNode(
          "view",
          {
            class: vue.normalizeClass(["tab-indicator", $setup.mode === "barcode" ? "right" : "left"])
          },
          null,
          2
          /* CLASS */
        )
      ]),
      vue.createCommentVNode(" 条件查询 "),
      $setup.mode === "code" ? (vue.openBlock(), vue.createElementBlock("view", {
        key: 0,
        class: "panel"
      }, [
        vue.createElementVNode("view", { class: "query-row" }, [
          vue.withDirectives(vue.createElementVNode("input", {
            "onUpdate:modelValue": _cache[2] || (_cache[2] = ($event) => $setup.materialCode = $event),
            class: "query-input",
            type: "text",
            "confirm-type": "search",
            placeholder: "物料编码",
            disabled: $setup.loading,
            onConfirm: $setup.onCodeSearch
          }, null, 40, ["disabled"]), [
            [vue.vModelText, $setup.materialCode]
          ]),
          vue.createElementVNode("button", {
            class: "query-btn",
            type: "primary",
            loading: $setup.loading,
            disabled: $setup.loading,
            onClick: $setup.onCodeSearch
          }, " 查询 ", 8, ["loading", "disabled"])
        ]),
        vue.createElementVNode("view", { class: "query-row secondary" }, [
          vue.withDirectives(vue.createElementVNode("input", {
            "onUpdate:modelValue": _cache[3] || (_cache[3] = ($event) => $setup.batchNo = $event),
            class: "query-input alone",
            type: "text",
            "confirm-type": "search",
            placeholder: "批次号（建议填写，结果更准）",
            disabled: $setup.loading,
            onConfirm: $setup.onCodeSearch
          }, null, 40, ["disabled"]), [
            [vue.vModelText, $setup.batchNo]
          ])
        ]),
        vue.createElementVNode("text", { class: "hint" }, "须填写物料编码或批次号至少一项")
      ])) : (vue.openBlock(), vue.createElementBlock(
        vue.Fragment,
        { key: 1 },
        [
          vue.createCommentVNode(" 扫码追溯：侧键扫码，无摄像头 "),
          vue.createElementVNode("view", { class: "panel" }, [
            vue.createVNode($setup["ScanSearchBar"], {
              ref: "scanInputRef",
              modelValue: $setup.barcode,
              "onUpdate:modelValue": _cache[4] || (_cache[4] = ($event) => $setup.barcode = $event),
              placeholder: "侧键扫码或输入条码",
              "action-text": "追溯",
              disabled: $setup.loading,
              onScan: $setup.onBarcodeScan,
              onSearch: $setup.onBarcodeScan
            }, null, 8, ["modelValue", "disabled"]),
            vue.createElementVNode("text", { class: "hint" }, "保持输入框聚焦，按设备侧键扫码后自动追溯")
          ])
        ],
        2112
        /* STABLE_FRAGMENT, DEV_ROOT_FRAGMENT */
      )),
      vue.createCommentVNode(" 汇总 "),
      $setup.hasResult ? (vue.openBlock(), vue.createElementBlock("view", {
        key: 2,
        class: "summary"
      }, [
        vue.createElementVNode("view", { class: "summary-head" }, [
          vue.createElementVNode(
            "text",
            { class: "summary-name" },
            vue.toDisplayString($setup.result.materialName || $setup.result.materialCode || "批次追溯"),
            1
            /* TEXT */
          ),
          $setup.result.materialCode ? (vue.openBlock(), vue.createElementBlock(
            "text",
            {
              key: 0,
              class: "summary-code"
            },
            vue.toDisplayString($setup.result.materialCode),
            1
            /* TEXT */
          )) : vue.createCommentVNode("v-if", true)
        ]),
        vue.createElementVNode("view", { class: "summary-grid" }, [
          vue.createElementVNode("view", { class: "sg-item" }, [
            vue.createElementVNode("text", { class: "sg-label" }, "批次"),
            vue.createElementVNode(
              "text",
              { class: "sg-value" },
              vue.toDisplayString($setup.result.batchNo || "-"),
              1
              /* TEXT */
            )
          ]),
          vue.createElementVNode("view", { class: "sg-item" }, [
            vue.createElementVNode("text", { class: "sg-label" }, "当前库存"),
            vue.createElementVNode(
              "text",
              { class: "sg-value accent" },
              vue.toDisplayString($setup.formatQty($setup.result.currentStock)),
              1
              /* TEXT */
            )
          ]),
          vue.createElementVNode("view", { class: "sg-item wide" }, [
            vue.createElementVNode("text", { class: "sg-label" }, "当前库位"),
            vue.createElementVNode(
              "text",
              { class: "sg-value" },
              vue.toDisplayString($setup.result.currentLocation || "-"),
              1
              /* TEXT */
            )
          ])
        ])
      ])) : vue.createCommentVNode("v-if", true),
      vue.createCommentVNode(" 流水时间线 "),
      $setup.records.length ? (vue.openBlock(), vue.createElementBlock("scroll-view", {
        key: 3,
        class: "list-scroll",
        "scroll-y": "",
        "show-scrollbar": false
      }, [
        vue.createElementVNode("view", { class: "timeline-title" }, [
          vue.createElementVNode("text", null, "流转记录"),
          vue.createElementVNode(
            "text",
            { class: "count" },
            vue.toDisplayString($setup.records.length) + " 条",
            1
            /* TEXT */
          )
        ]),
        (vue.openBlock(true), vue.createElementBlock(
          vue.Fragment,
          null,
          vue.renderList($setup.records, (row, idx) => {
            return vue.openBlock(), vue.createElementBlock("view", {
              key: row.seq || idx,
              class: "tl-item"
            }, [
              vue.createElementVNode("view", { class: "tl-rail" }, [
                vue.createElementVNode(
                  "view",
                  {
                    class: vue.normalizeClass(["tl-dot", $setup.txnTone(row.transactionType)])
                  },
                  null,
                  2
                  /* CLASS */
                ),
                idx < $setup.records.length - 1 ? (vue.openBlock(), vue.createElementBlock("view", {
                  key: 0,
                  class: "tl-line"
                })) : vue.createCommentVNode("v-if", true)
              ]),
              vue.createElementVNode("view", { class: "tl-card" }, [
                vue.createElementVNode("view", { class: "tl-top" }, [
                  vue.createElementVNode(
                    "text",
                    {
                      class: vue.normalizeClass(["type-tag", $setup.txnTone(row.transactionType)])
                    },
                    vue.toDisplayString($setup.txnLabel(row.transactionType)),
                    3
                    /* TEXT, CLASS */
                  ),
                  vue.createElementVNode(
                    "text",
                    { class: "qty" },
                    vue.toDisplayString($setup.formatQty(row.qty)),
                    1
                    /* TEXT */
                  )
                ]),
                vue.createElementVNode(
                  "text",
                  { class: "tl-time" },
                  vue.toDisplayString($setup.formatTime(row.operationTime)),
                  1
                  /* TEXT */
                ),
                vue.createElementVNode("view", { class: "tl-meta" }, [
                  vue.createElementVNode(
                    "text",
                    null,
                    "仓 " + vue.toDisplayString(row.warehouseCode || "-"),
                    1
                    /* TEXT */
                  ),
                  vue.createElementVNode(
                    "text",
                    null,
                    "位 " + vue.toDisplayString(row.locationCode || "-"),
                    1
                    /* TEXT */
                  )
                ]),
                vue.createElementVNode("view", { class: "tl-meta" }, [
                  vue.createElementVNode(
                    "text",
                    null,
                    "单 " + vue.toDisplayString(row.sourceOrderNo || "-"),
                    1
                    /* TEXT */
                  ),
                  vue.createElementVNode(
                    "text",
                    null,
                    "人 " + vue.toDisplayString(row.operatorName || "-"),
                    1
                    /* TEXT */
                  )
                ])
              ])
            ]);
          }),
          128
          /* KEYED_FRAGMENT */
        )),
        vue.createElementVNode("view", { class: "list-end" }, "最多显示近期 50 条")
      ])) : $setup.searched && !$setup.loading ? (vue.openBlock(), vue.createElementBlock("view", {
        key: 4,
        class: "empty"
      }, [
        vue.createElementVNode("text", { class: "empty-icon" }, "🔎"),
        vue.createElementVNode("text", { class: "empty-text" }, "暂无追溯记录"),
        vue.createElementVNode("text", { class: "empty-hint" }, "请确认物料/批次是否正确")
      ])) : !$setup.searched && !$setup.loading ? (vue.openBlock(), vue.createElementBlock("view", {
        key: 5,
        class: "empty idle"
      }, [
        vue.createElementVNode("text", { class: "empty-icon" }, "🔍"),
        vue.createElementVNode("text", { class: "empty-text" }, "请输入条件或扫码追溯"),
        vue.createElementVNode("text", { class: "empty-hint" }, "不支持无条件全量查询")
      ])) : vue.createCommentVNode("v-if", true)
    ]);
  }
  const PagesTraceTrace = /* @__PURE__ */ _export_sfc(_sfc_main$l, [["render", _sfc_render$k], ["__scopeId", "data-v-4a0b306e"], ["__file", "D:/AAA/WMS/wms-pda/src/pages/trace/trace.vue"]]);
  const BILL_TYPE$b = "PRODUCTION_ISSUE";
  const _sfc_main$k = {
    __name: "production-issue",
    setup(__props, { expose: __expose }) {
      __expose();
      const scanInputRef = vue.ref(null);
      const billType = vue.ref(BILL_TYPE$b);
      const busy = vue.ref(false);
      const { alive, refocusScanInput } = usePageAlive();
      const {
        loading,
        keyword,
        notices,
        loadList,
        loadListOnShow,
        statusLabel,
        statusClass
      } = useNoticeBillList(billType);
      async function openByBarcode(barcode) {
        if (!alive.value || busy.value) return;
        const raw = (barcode || "").trim();
        if (!raw) return;
        busy.value = true;
        uni.showLoading({ title: "打开中...", mask: true });
        try {
          let billNo = raw;
          try {
            const res = await resolveNoticeBarcode(BILL_TYPE$b, raw);
            if (res == null ? void 0 : res.billNo) billNo = String(res.billNo).trim();
          } catch {
          }
          if (!billNo) {
            uni.showToast({ title: "无法识别领料单号", icon: "none" });
            return;
          }
          keyword.value = billNo;
          openBill({ billNo });
        } finally {
          uni.hideLoading();
          busy.value = false;
          refocusScanInput(scanInputRef, 300);
        }
      }
      function onScan(barcode) {
        openByBarcode(barcode);
      }
      function onSearch(val) {
        openByBarcode(val || keyword.value);
      }
      function openBill(item) {
        if (!(item == null ? void 0 : item.billNo)) return;
        uni.navigateTo({
          url: `/pages/picking/production-issue-scan?billNo=${encodeURIComponent(item.billNo)}`
        });
      }
      onLoad(() => uni.setNavigationBarTitle({ title: "生产领料" }));
      onShow(() => loadListOnShow());
      vue.onMounted(() => refocusScanInput(scanInputRef, 500));
      const __returned__ = { BILL_TYPE: BILL_TYPE$b, scanInputRef, billType, busy, alive, refocusScanInput, loading, keyword, notices, loadList, loadListOnShow, statusLabel, statusClass, openByBarcode, onScan, onSearch, openBill, ref: vue.ref, onMounted: vue.onMounted, get onLoad() {
        return onLoad;
      }, get onShow() {
        return onShow;
      }, ScanSearchBar, get resolveNoticeBarcode() {
        return resolveNoticeBarcode;
      }, get useNoticeBillList() {
        return useNoticeBillList;
      }, get usePageAlive() {
        return usePageAlive;
      }, get formatMaterialLineCount() {
        return formatMaterialLineCount;
      } };
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  };
  function _sfc_render$j(_ctx, _cache, $props, $setup, $data, $options) {
    return vue.openBlock(), vue.createElementBlock("view", { class: "page" }, [
      vue.createElementVNode("view", { class: "search-top" }, [
        vue.createVNode($setup["ScanSearchBar"], {
          ref: "scanInputRef",
          modelValue: $setup.keyword,
          "onUpdate:modelValue": _cache[0] || (_cache[0] = ($event) => $setup.keyword = $event),
          disabled: $setup.busy,
          placeholder: "扫码或搜索生产领料单号/车间",
          "action-text": "打开",
          onScan: $setup.onScan,
          onSearch: $setup.onSearch
        }, null, 8, ["modelValue", "disabled"])
      ]),
      vue.createElementVNode("scroll-view", {
        class: "list-scroll",
        "scroll-y": "",
        "show-scrollbar": false
      }, [
        (vue.openBlock(true), vue.createElementBlock(
          vue.Fragment,
          null,
          vue.renderList($setup.notices, (item, index) => {
            return vue.openBlock(), vue.createElementBlock("view", {
              key: item.billNo || "row-" + index,
              class: "bill-row",
              onClick: ($event) => $setup.openBill(item)
            }, [
              vue.createElementVNode("view", { class: "row-main" }, [
                vue.createElementVNode(
                  "text",
                  { class: "bill-no" },
                  vue.toDisplayString(item.billNo || "（单号缺失）"),
                  1
                  /* TEXT */
                ),
                vue.createElementVNode(
                  "text",
                  { class: "bill-supplier" },
                  vue.toDisplayString(item.supplierName || item.supplierCode || "-"),
                  1
                  /* TEXT */
                ),
                vue.createElementVNode("text", { class: "bill-meta" }, [
                  $setup.formatMaterialLineCount(item) ? (vue.openBlock(), vue.createElementBlock(
                    "text",
                    {
                      key: 0,
                      class: "bill-lines"
                    },
                    vue.toDisplayString($setup.formatMaterialLineCount(item)),
                    1
                    /* TEXT */
                  )) : vue.createCommentVNode("v-if", true),
                  item.inProgress ? (vue.openBlock(), vue.createElementBlock(
                    "text",
                    { key: 1 },
                    " · 已勾 " + vue.toDisplayString(item.checkedLines || 0),
                    1
                    /* TEXT */
                  )) : vue.createCommentVNode("v-if", true),
                  item.locked && item.lockUserName ? (vue.openBlock(), vue.createElementBlock(
                    "text",
                    {
                      key: 2,
                      class: "bill-lock"
                    },
                    " · " + vue.toDisplayString(item.lockUserName) + "操作中",
                    1
                    /* TEXT */
                  )) : vue.createCommentVNode("v-if", true)
                ])
              ]),
              vue.createElementVNode("view", { class: "row-side" }, [
                vue.createElementVNode(
                  "text",
                  {
                    class: vue.normalizeClass(["status-tag", $setup.statusClass(item)])
                  },
                  vue.toDisplayString($setup.statusLabel(item)),
                  3
                  /* TEXT, CLASS */
                ),
                vue.createElementVNode("text", { class: "arrow" }, "›")
              ])
            ], 8, ["onClick"]);
          }),
          128
          /* KEYED_FRAGMENT */
        )),
        !$setup.notices.length && !$setup.loading ? (vue.openBlock(), vue.createElementBlock("view", {
          key: 0,
          class: "empty"
        }, [
          vue.createElementVNode("text", { class: "empty-icon" }, "📋"),
          vue.createElementVNode("text", { class: "empty-text" }, "暂无未审核的生产领料单"),
          vue.createElementVNode("text", { class: "empty-hint" }, "可直接扫描领料单二维码进入明细")
        ])) : vue.createCommentVNode("v-if", true),
        $setup.loading && !$setup.notices.length ? (vue.openBlock(), vue.createElementBlock("view", {
          key: 1,
          class: "loading-tip"
        }, "加载中...")) : $setup.notices.length ? (vue.openBlock(), vue.createElementBlock(
          "view",
          {
            key: 2,
            class: "loading-tip end-tip"
          },
          "共 " + vue.toDisplayString($setup.notices.length) + " 条",
          1
          /* TEXT */
        )) : vue.createCommentVNode("v-if", true)
      ])
    ]);
  }
  const PagesPickingProductionIssue = /* @__PURE__ */ _export_sfc(_sfc_main$k, [["render", _sfc_render$j], ["__scopeId", "data-v-e0ad26c4"], ["__file", "D:/AAA/WMS/wms-pda/src/pages/picking/production-issue.vue"]]);
  function parseQtyToken(token) {
    const text = String(token || "").trim();
    if (!text) return null;
    const m = text.match(/^(\d+(?:\.\d+)?)\s*(?:kg|g|t|吨|千克|公斤|pcs|pc|ea)?$/i);
    if (!m) return null;
    const n = Number(m[1]);
    return Number.isFinite(n) && n > 0 ? n : null;
  }
  function parseMaterialBarcode(raw) {
    const text = String(raw || "").trim();
    if (!text) return null;
    const parts = text.split("|").map((s) => s.trim()).filter(Boolean);
    if (parts.length >= 2) {
      const lastQty = parseQtyToken(parts[parts.length - 1]);
      let batchNo = "";
      let qty = null;
      if (lastQty != null) {
        qty = lastQty;
        batchNo = parts.length >= 3 ? parts[1] || "" : "";
      } else {
        batchNo = parts[1] || "";
        if (parts.length >= 3) {
          qty = parseQtyToken(parts[2]);
        }
      }
      return {
        materialCode: parts[0],
        batchNo,
        qty,
        mode: "pipe"
      };
    }
    return { materialCode: text, batchNo: "", qty: null, mode: "code" };
  }
  function matchLocalBillLine(lines, barcode) {
    const parsed = parseMaterialBarcode(barcode);
    if (!parsed || !Array.isArray(lines) || !lines.length) return null;
    const code = String(parsed.materialCode || "").toLowerCase();
    if (!code) return null;
    let candidates = lines.filter((l) => String(l.materialCode || "").toLowerCase() === code);
    if (!candidates.length) {
      candidates = lines.filter((l) => String(l.materialCode || "").toLowerCase().startsWith(code));
    }
    if (!candidates.length) return null;
    if (parsed.batchNo) {
      const batch = String(parsed.batchNo).toLowerCase();
      const byBatch = candidates.filter((l) => String(l.batchNo || "").toLowerCase() === batch);
      if (byBatch.length) candidates = byBatch;
    }
    const open = candidates.find((l) => {
      const plan = Number(l.planQty) || 0;
      const submitted = Number(l.submittedQty) || 0;
      return plan <= 0 || submitted < plan;
    });
    const line = open || candidates[0];
    return { line, parsed };
  }
  function createNoticeBillScan(billType, messages = {}) {
    const notOnBillMsg = messages.notOnBill || "该物料不在本单据中";
    const linesNotReadyMsg = messages.linesNotReady || "单据明细未加载完成，请返回重新进入";
    const alreadyFullMsg = messages.alreadyFull || "该物料已领满";
    const backOnSubmitSuccess = messages.backOnSubmitSuccess === true;
    return function useNoticeBillScanImpl(billNo) {
      const loading = vue.ref(false);
      const submitting = vue.ref(false);
      const detail = vue.ref(null);
      const lines = vue.ref([]);
      const lastHighlightLineNo = vue.ref(null);
      let lastLoadAt = 0;
      const billLock = useBillExclusiveLock({
        heartbeat: () => heartbeatNoticeBillLock(billType, billNo.value),
        release: () => releaseNoticeBillLock(billType, billNo.value)
      });
      function hasPendingSubmit(line) {
        if (!(line == null ? void 0 : line.checked)) return false;
        if ((Number(line.pendingSubmitQty) || 0) > 0) return true;
        return (Number(line.pendingSubmitAuxQty) || 0) > 0;
      }
      const checkedCount = vue.computed(
        () => lines.value.filter((l) => l.checked).length
      );
      const submitableCount = vue.computed(
        () => lines.value.filter((l) => hasPendingSubmit(l)).length
      );
      function detailCacheKey() {
        return `notice-detail:${billType}:${billNo.value || ""}`;
      }
      function normalizeLine(line) {
        if (!line) return line;
        return {
          ...line,
          checked: line.checked === true || line.checked === 1,
          pendingSubmitQty: line.pendingSubmitQty ?? 0,
          pendingSubmitAuxQty: line.pendingSubmitAuxQty ?? 0
        };
      }
      function applyDetail(data) {
        detail.value = data;
        lines.value = ((data == null ? void 0 : data.lines) || []).map(normalizeLine);
      }
      function handleLockDenied(e) {
        cacheDel(detailCacheKey());
        billLock.stop();
        const msg = (e == null ? void 0 : e.message) || "单据正被其他人操作";
        uni.showToast({ title: msg, icon: "none", duration: 2500 });
        setTimeout(() => uni.navigateBack({ fail: () => {
        } }), 400);
      }
      async function loadDetail(options = {}) {
        if (!billNo.value) return null;
        const force = options.force === true;
        const cacheKey = detailCacheKey();
        if (!force) {
          const cached = cacheGet(cacheKey);
          if (cached) {
            applyDetail(cached);
            try {
              await heartbeatNoticeBillLock(billType, billNo.value);
              billLock.start();
            } catch (e) {
              if (isBillLockedError(e)) {
                handleLockDenied(e);
                return null;
              }
            }
            return cached;
          }
        }
        loading.value = true;
        try {
          const data = await getNoticeBillDetail(billType, billNo.value, { refresh: force });
          applyDetail(data);
          cacheSet(cacheKey, data, DETAIL_CACHE_TTL_MS);
          lastLoadAt = Date.now();
          billLock.start();
          return data;
        } catch (e) {
          if (isBillLockedError(e)) {
            handleLockDenied(e);
            return null;
          }
          uni.showToast({ title: (e == null ? void 0 : e.message) || "加载明细失败", icon: "none" });
          return null;
        } finally {
          loading.value = false;
        }
      }
      async function loadDetailOnShow() {
        if (Date.now() - lastLoadAt < SHOW_THROTTLE_MS && lines.value.length) {
          return detail.value;
        }
        return loadDetail();
      }
      function formatScanError(e) {
        var _a, _b;
        const type = ((_a = e == null ? void 0 : e.data) == null ? void 0 : _a.errorType) || (e == null ? void 0 : e.errorType);
        const msg = (e == null ? void 0 : e.message) || ((_b = e == null ? void 0 : e.data) == null ? void 0 : _b.message);
        if (type === "BARCODE_EMPTY" || type === "BARCODE_PARSE_FAILED") {
          return msg || "条码格式错误，请扫描物料标签二维码";
        }
        if (type === "BARCODE_QTY_INVALID") {
          return msg || "二维码数量无效，请检查标签或重新打印";
        }
        if (type === "MATERIAL_NOT_ON_BILL") {
          return msg || notOnBillMsg;
        }
        if (type === "BILL_LINES_NOT_READY" || type === "BILL_LINE_NOT_SYNCED") {
          return msg || linesNotReadyMsg;
        }
        if (type === "LINE_ALREADY_FULL") {
          return msg || alreadyFullMsg;
        }
        if (type === "NO_STOCK") {
          return msg || "未找到可出库库存";
        }
        return msg || "扫描失败";
      }
      function mergeLine(updated) {
        const normalized = normalizeLine(updated);
        const idx = lines.value.findIndex((l) => l.lineNo === normalized.lineNo);
        if (idx >= 0) {
          lines.value[idx] = { ...lines.value[idx], ...normalized };
        }
        if (detail.value) {
          detail.value.checkedLines = lines.value.filter((l) => l.checked).length;
          cacheSet(detailCacheKey(), { ...detail.value, lines: lines.value }, DETAIL_CACHE_TTL_MS);
        }
      }
      function roundQty(n, unitCode) {
        if (!Number.isFinite(n)) return 0;
        const scale = isWeightUnit(unitCode) ? 1e6 : 1e4;
        return Math.round(n * scale) / scale;
      }
      function applyLocalScan(matched, barcodeRaw) {
        var _a;
        const line = { ...matched.line };
        const unit = line.unitCode;
        const plan = Number(line.planQty) || 0;
        const submitted = Number(line.submittedQty) || 0;
        const remain = roundQty(Math.max(0, plan - submitted), unit);
        if (remain <= 0) {
          throw Object.assign(new Error(alreadyFullMsg), { errorType: "LINE_ALREADY_FULL" });
        }
        let addQty = matched.parsed.qty != null ? Number(matched.parsed.qty) : 1;
        if (!Number.isFinite(addQty) || addQty <= 0) addQty = 1;
        addQty = roundQty(addQty, unit);
        if (addQty > remain) addQty = remain;
        const pending = Number(line.pendingSubmitQty) || 0;
        const nextPending = roundQty(Math.min(remain, pending + addQty), unit);
        line.checked = true;
        line.labelScanned = true;
        line.scannedBarcode = barcodeRaw || ((_a = matched.parsed) == null ? void 0 : _a.barcodeContent) || line.scannedBarcode || "";
        line.pendingSubmitQty = nextPending;
        line.scannedQty = roundQty(submitted + nextPending, unit);
        line.scannedBarcodeQty = addQty;
        if (matched.parsed.batchNo) line.batchNo = matched.parsed.batchNo;
        mergeLine(line);
        return line;
      }
      async function handleScan(barcode) {
        if (!(barcode == null ? void 0 : barcode.trim()) || submitting.value) return null;
        loading.value = true;
        lastHighlightLineNo.value = null;
        const raw = barcode.trim();
        try {
          const local = matchLocalBillLine(lines.value, raw);
          if (local) {
            applyLocalScan(local, raw);
            lastHighlightLineNo.value = local.line.lineNo;
          }
          const line = await scanNoticeLine(billType, billNo.value, raw);
          mergeLine(line);
          lastHighlightLineNo.value = line.lineNo;
          const qty = line.scannedBarcodeQty ?? line.pendingSubmitQty;
          const qtyText = qty != null && qty !== "" ? ` ×${formatQty$1(qty, line.unitCode)}` : "";
          uni.showToast({
            title: `✓ ${line.materialName || line.materialCode}${qtyText}`,
            icon: "success",
            duration: 1200
          });
          return line;
        } catch (e) {
          uni.showToast({ title: formatScanError(e), icon: "none", duration: 2500 });
          return null;
        } finally {
          loading.value = false;
        }
      }
      async function toggleCheck(lineNo, checked) {
        try {
          const line = await toggleNoticeLine(billType, billNo.value, lineNo, checked);
          mergeLine(line);
        } catch (e) {
          uni.showToast({ title: (e == null ? void 0 : e.message) || "操作失败", icon: "none" });
        }
      }
      async function updateQty(lineNo, qty, auxQty) {
        const num = Number(qty);
        if (Number.isNaN(num) || num < 0) {
          uni.showToast({ title: "请输入有效数量", icon: "none" });
          return false;
        }
        try {
          const line = await updateNoticeLineQty(billType, billNo.value, lineNo, num, auxQty);
          mergeLine(line);
          return true;
        } catch (e) {
          uni.showToast({ title: (e == null ? void 0 : e.message) || "更新数量失败", icon: "none" });
          return false;
        }
      }
      function formatQty$1(val, unitCode) {
        return formatQty(val, unitCode);
      }
      async function submit() {
        var _a, _b;
        if (!submitableCount.value) {
          uni.showToast({ title: "请先扫码或手动填写数量后再提交", icon: "none" });
          return false;
        }
        submitting.value = true;
        try {
          const result = await submitNoticeBill(billType, billNo.value, {
            supplierCode: (_a = detail.value) == null ? void 0 : _a.supplierCode,
            supplierName: (_b = detail.value) == null ? void 0 : _b.supplierName
          });
          const feedback = await handleErpSubmitResult(result);
          if (!feedback.ok) {
            cacheDel(detailCacheKey());
            await loadDetail({ force: true });
            return false;
          }
          cacheDel(detailCacheKey());
          if (backOnSubmitSuccess) {
            await billLock.releaseLock();
            setTimeout(() => uni.navigateBack(), 400);
            return true;
          }
          await loadDetail({ force: true });
          if (isNoticeBillCompleted(detail.value)) {
            await billLock.releaseLock();
            setTimeout(() => uni.navigateBack(), 400);
          }
          return true;
        } catch (e) {
          if (isBillLockedError(e)) {
            handleLockDenied(e);
            return false;
          }
          await alertErpSubmitFailed(formatErpSubmitError(e));
          cacheDel(detailCacheKey());
          await loadDetail({ force: true });
          return false;
        } finally {
          submitting.value = false;
        }
      }
      function rowClass(line) {
        if (line.lineNo === lastHighlightLineNo.value) return "flash";
        const submitted = Number(line.submittedQty) || 0;
        const plan = Number(line.planQty) || 0;
        if (submitted >= plan && plan > 0) return "done";
        if (submitted > 0 && submitted < plan) return "partial";
        if (line.checked) return "checked";
        return "";
      }
      return {
        loading,
        submitting,
        detail,
        lines,
        lastHighlightLineNo,
        checkedCount,
        submitableCount,
        loadDetail,
        loadDetailOnShow,
        handleScan,
        toggleCheck,
        updateQty,
        formatQty: formatQty$1,
        submit,
        rowClass,
        isLabelScanned
      };
    };
  }
  const BILL_TYPE$a = "PRODUCTION_ISSUE";
  const useProductionIssueScan = createNoticeBillScan(BILL_TYPE$a, {
    notOnBill: "该物料不在本生产领料单中",
    linesNotReady: "领料单明细未加载完成，请返回重新进入",
    backOnSubmitSuccess: true
  });
  function useWindowedLines(linesRef, options = {}) {
    const windowSize = options.windowSize || 36;
    const rowHeightPx = options.rowHeightPx || 140;
    const scrollTop = vue.ref(0);
    const pinnedLineNo = vue.ref(null);
    const windowed = vue.computed(() => {
      const all = linesRef.value || [];
      if (all.length <= windowSize + 8) {
        return { items: all, offset: 0, total: all.length, padTop: 0, padBottom: 0 };
      }
      let start = Math.floor(scrollTop.value / rowHeightPx) - 4;
      if (start < 0) start = 0;
      let end = start + windowSize;
      if (end > all.length) {
        end = all.length;
        start = Math.max(0, end - windowSize);
      }
      if (pinnedLineNo.value != null) {
        const idx = all.findIndex((l) => l.lineNo === pinnedLineNo.value);
        if (idx >= 0 && (idx < start || idx >= end)) {
          start = Math.max(0, idx - Math.floor(windowSize / 2));
          end = Math.min(all.length, start + windowSize);
          start = Math.max(0, end - windowSize);
        }
      }
      const items = all.slice(start, end);
      return {
        items,
        offset: start,
        total: all.length,
        padTop: start * rowHeightPx,
        padBottom: Math.max(0, (all.length - end) * rowHeightPx)
      };
    });
    function onScroll(e) {
      var _a;
      const top = (_a = e == null ? void 0 : e.detail) == null ? void 0 : _a.scrollTop;
      if (typeof top === "number") scrollTop.value = top;
    }
    function pinLine(lineNo) {
      pinnedLineNo.value = lineNo;
    }
    vue.watch(linesRef, () => {
      if ((linesRef.value || []).length <= windowSize) {
        scrollTop.value = 0;
      }
    });
    return { windowed, onScroll, pinLine, scrollTop };
  }
  const _sfc_main$j = {
    __name: "production-issue-scan",
    setup(__props, { expose: __expose }) {
      __expose();
      const billNo = vue.ref("");
      const scanInputRef = vue.ref(null);
      const qtyDrafts = vue.reactive({});
      const updatingLineNo = vue.ref(null);
      const { alive, refocusScanInput } = usePageAlive();
      const {
        loading,
        submitting,
        detail,
        lines,
        checkedCount,
        submitableCount,
        loadDetailOnShow,
        handleScan,
        toggleCheck,
        updateQty,
        formatQty: formatQty2,
        submit,
        rowClass,
        lastHighlightLineNo
      } = useProductionIssueScan(billNo);
      const { windowed, onScroll: onListScroll, pinLine } = useWindowedLines(lines);
      const partialCount = vue.computed(
        () => lines.value.filter((l) => isPartialLine(l)).length
      );
      function isDoneLine(line) {
        const submitted = Number(line.submittedQty) || 0;
        const plan = Number(line.planQty) || 0;
        return plan > 0 && submitted >= plan;
      }
      function isPartialLine(line) {
        const submitted = Number(line.submittedQty) || 0;
        const plan = Number(line.planQty) || 0;
        return submitted > 0 && submitted < plan;
      }
      function syncQtyDrafts() {
        lines.value.forEach((line) => {
          qtyDrafts[line.lineNo] = formatQtyInput(line.pendingSubmitQty || 0, line.unitCode);
        });
      }
      function getQtyDraft(line) {
        if (qtyDrafts[line.lineNo] == null) {
          qtyDrafts[line.lineNo] = formatQtyInput(line.pendingSubmitQty || 0, line.unitCode);
        }
        return qtyDrafts[line.lineNo];
      }
      function onQtyInput(line, e) {
        qtyDrafts[line.lineNo] = sanitizeDecimalInput(e.detail.value, qtyDecimalScale(line.unitCode));
      }
      async function onQtyBlur(line) {
        const raw = qtyDrafts[line.lineNo];
        const num = raw === "" || raw == null ? 0 : Number(raw);
        if (Number.isNaN(num) || num < 0) {
          qtyDrafts[line.lineNo] = formatQtyInput(line.pendingSubmitQty || 0, line.unitCode);
          return;
        }
        const current = Number(line.pendingSubmitQty) || 0;
        if (num === current) return;
        updatingLineNo.value = line.lineNo;
        const ok = await updateQty(line.lineNo, num);
        updatingLineNo.value = null;
        if (ok) {
          const updated = lines.value.find((l) => l.lineNo === line.lineNo);
          if (updated) qtyDrafts[line.lineNo] = formatQtyInput(updated.pendingSubmitQty || 0, updated.unitCode);
        } else {
          qtyDrafts[line.lineNo] = formatQtyInput(line.pendingSubmitQty || 0, line.unitCode);
        }
      }
      async function onScan(barcode) {
        if (!alive.value) return;
        const line = await handleScan(barcode);
        if ((line == null ? void 0 : line.lineNo) != null) pinLine(line.lineNo);
        else if (lastHighlightLineNo.value != null) pinLine(lastHighlightLineNo.value);
        syncQtyDrafts();
        refocusScanInput(scanInputRef, 300);
      }
      function onToggle(line) {
        toggleCheck(line.lineNo, !line.checked);
      }
      function onRowTap(line) {
        if (!line.checked && !isDoneLine(line)) toggleCheck(line.lineNo, true);
      }
      async function onSubmit() {
        const ok = await submit();
        if (ok) syncQtyDrafts();
      }
      onLoad((options) => {
        billNo.value = decodeURIComponent((options == null ? void 0 : options.billNo) || "");
        uni.setNavigationBarTitle({ title: "领料确认" });
      });
      onShow(async () => {
        if (billNo.value) {
          await loadDetailOnShow();
          syncQtyDrafts();
        }
        refocusScanInput(scanInputRef, 400);
      });
      const __returned__ = { billNo, scanInputRef, qtyDrafts, updatingLineNo, alive, refocusScanInput, loading, submitting, detail, lines, checkedCount, submitableCount, loadDetailOnShow, handleScan, toggleCheck, updateQty, formatQty: formatQty2, submit, rowClass, lastHighlightLineNo, windowed, onListScroll, pinLine, partialCount, isDoneLine, isPartialLine, syncQtyDrafts, getQtyDraft, onQtyInput, onQtyBlur, onScan, onToggle, onRowTap, onSubmit, ref: vue.ref, computed: vue.computed, reactive: vue.reactive, get onLoad() {
        return onLoad;
      }, get onShow() {
        return onShow;
      }, CompactScanBox, get useProductionIssueScan() {
        return useProductionIssueScan;
      }, get usePageAlive() {
        return usePageAlive;
      }, get sanitizeDecimalInput() {
        return sanitizeDecimalInput;
      }, get qtyDecimalScale() {
        return qtyDecimalScale;
      }, get formatQtyInput() {
        return formatQtyInput;
      }, get useWindowedLines() {
        return useWindowedLines;
      } };
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  };
  function _sfc_render$i(_ctx, _cache, $props, $setup, $data, $options) {
    return vue.openBlock(), vue.createElementBlock("view", { class: "page" }, [
      vue.createElementVNode("view", { class: "scan-top" }, [
        vue.createVNode($setup["CompactScanBox"], {
          ref: "scanInputRef",
          disabled: $setup.loading || $setup.submitting,
          onScan: $setup.onScan
        }, null, 8, ["disabled"])
      ]),
      $setup.detail ? (vue.openBlock(), vue.createElementBlock("view", {
        key: 0,
        class: "order-bar"
      }, [
        vue.createElementVNode("view", { class: "order-info" }, [
          vue.createElementVNode(
            "text",
            { class: "order-no" },
            vue.toDisplayString($setup.detail.billNo),
            1
            /* TEXT */
          ),
          vue.createElementVNode(
            "text",
            { class: "order-sub" },
            vue.toDisplayString($setup.detail.supplierName || $setup.detail.supplierCode || "-"),
            1
            /* TEXT */
          ),
          $setup.detail.erpBillNo ? (vue.openBlock(), vue.createElementBlock(
            "text",
            {
              key: 0,
              class: "order-erp"
            },
            "生产领料单 " + vue.toDisplayString($setup.detail.erpBillNo),
            1
            /* TEXT */
          )) : vue.createCommentVNode("v-if", true)
        ]),
        vue.createElementVNode("view", { class: "order-stat-wrap" }, [
          vue.createElementVNode(
            "text",
            { class: "order-stat" },
            vue.toDisplayString($setup.checkedCount) + "/" + vue.toDisplayString($setup.lines.length) + " 已勾",
            1
            /* TEXT */
          ),
          $setup.partialCount ? (vue.openBlock(), vue.createElementBlock(
            "text",
            {
              key: 0,
              class: "order-partial"
            },
            "部分已领 " + vue.toDisplayString($setup.partialCount),
            1
            /* TEXT */
          )) : vue.createCommentVNode("v-if", true)
        ])
      ])) : vue.createCommentVNode("v-if", true),
      vue.createElementVNode(
        "scroll-view",
        {
          class: "list-scroll",
          "scroll-y": "",
          "show-scrollbar": false,
          onScroll: _cache[1] || (_cache[1] = (...args) => $setup.onListScroll && $setup.onListScroll(...args))
        },
        [
          $setup.windowed.padTop ? (vue.openBlock(), vue.createElementBlock(
            "view",
            {
              key: 0,
              style: vue.normalizeStyle({ height: $setup.windowed.padTop + "px" })
            },
            null,
            4
            /* STYLE */
          )) : vue.createCommentVNode("v-if", true),
          (vue.openBlock(true), vue.createElementBlock(
            vue.Fragment,
            null,
            vue.renderList($setup.windowed.items, (line) => {
              return vue.openBlock(), vue.createElementBlock("view", {
                key: line.lineNo,
                class: vue.normalizeClass(["mat-row", $setup.rowClass(line)]),
                onClick: ($event) => $setup.onRowTap(line)
              }, [
                vue.createElementVNode("view", { class: "row-header" }, [
                  vue.createElementVNode("view", {
                    class: "check-box",
                    onClick: vue.withModifiers(($event) => $setup.onToggle(line), ["stop"])
                  }, [
                    vue.createElementVNode(
                      "view",
                      {
                        class: vue.normalizeClass(["check-inner", line.checked && "on"])
                      },
                      [
                        line.checked ? (vue.openBlock(), vue.createElementBlock("text", {
                          key: 0,
                          class: "check-mark"
                        }, "✓")) : vue.createCommentVNode("v-if", true)
                      ],
                      2
                      /* CLASS */
                    )
                  ], 8, ["onClick"]),
                  vue.createElementVNode("view", { class: "row-main" }, [
                    vue.createElementVNode("view", { class: "name-row" }, [
                      vue.createElementVNode(
                        "text",
                        { class: "mat-code" },
                        vue.toDisplayString(line.materialCode),
                        1
                        /* TEXT */
                      ),
                      $setup.isPartialLine(line) ? (vue.openBlock(), vue.createElementBlock("text", {
                        key: 0,
                        class: "partial-tag"
                      }, "部分已领")) : vue.createCommentVNode("v-if", true)
                    ]),
                    vue.createElementVNode(
                      "text",
                      { class: "mat-name" },
                      vue.toDisplayString(line.materialName || "-"),
                      1
                      /* TEXT */
                    ),
                    vue.createElementVNode(
                      "text",
                      { class: "mat-spec" },
                      "规格 " + vue.toDisplayString(line.specification || "-"),
                      1
                      /* TEXT */
                    ),
                    vue.createElementVNode(
                      "text",
                      { class: "mat-batch" },
                      "批次 " + vue.toDisplayString(line.batchNo || "-"),
                      1
                      /* TEXT */
                    ),
                    vue.createElementVNode(
                      "text",
                      { class: "mat-wh" },
                      "仓库 " + vue.toDisplayString(line.erpStockCode || "-"),
                      1
                      /* TEXT */
                    )
                  ])
                ]),
                vue.createElementVNode("view", {
                  class: "qty-panel",
                  onClick: _cache[0] || (_cache[0] = vue.withModifiers(() => {
                  }, ["stop"]))
                }, [
                  vue.createElementVNode("view", { class: "qty-grid" }, [
                    vue.createElementVNode("view", { class: "qty-cell" }, [
                      vue.createElementVNode("text", { class: "qty-label" }, "计划"),
                      vue.createElementVNode(
                        "text",
                        { class: "qty-value" },
                        vue.toDisplayString($setup.formatQty(line.planQty, line.unitCode)),
                        1
                        /* TEXT */
                      )
                    ]),
                    vue.createElementVNode("view", { class: "qty-cell" }, [
                      vue.createElementVNode("text", { class: "qty-label" }, "已领"),
                      vue.createElementVNode(
                        "text",
                        { class: "qty-value submitted" },
                        vue.toDisplayString($setup.formatQty(line.submittedQty, line.unitCode)),
                        1
                        /* TEXT */
                      )
                    ]),
                    vue.createElementVNode("view", { class: "qty-cell" }, [
                      vue.createElementVNode("text", { class: "qty-label" }, "可领"),
                      vue.createElementVNode(
                        "text",
                        { class: "qty-value remain" },
                        vue.toDisplayString($setup.formatQty(line.remainQty, line.unitCode)),
                        1
                        /* TEXT */
                      )
                    ]),
                    vue.createElementVNode("view", { class: "qty-cell unit-cell" }, [
                      vue.createElementVNode("text", { class: "qty-label" }, "单位"),
                      vue.createElementVNode(
                        "text",
                        { class: "qty-value unit" },
                        vue.toDisplayString(line.unitCode || "PCS"),
                        1
                        /* TEXT */
                      )
                    ])
                  ]),
                  !$setup.isDoneLine(line) ? (vue.openBlock(), vue.createElementBlock("view", {
                    key: 0,
                    class: "qty-edit"
                  }, [
                    vue.createElementVNode("text", { class: "qty-edit-label" }, "本次领取"),
                    vue.createElementVNode("input", {
                      class: "qty-input",
                      type: "digit",
                      value: $setup.getQtyDraft(line),
                      disabled: $setup.updatingLineNo === line.lineNo,
                      placeholder: "0",
                      onInput: ($event) => $setup.onQtyInput(line, $event),
                      onBlur: ($event) => $setup.onQtyBlur(line),
                      onConfirm: ($event) => $setup.onQtyBlur(line)
                    }, null, 40, ["value", "disabled", "onInput", "onBlur", "onConfirm"]),
                    vue.createElementVNode(
                      "text",
                      { class: "qty-edit-unit" },
                      vue.toDisplayString(line.unitCode || "PCS"),
                      1
                      /* TEXT */
                    )
                  ])) : (vue.openBlock(), vue.createElementBlock("view", {
                    key: 1,
                    class: "qty-done-tip"
                  }, "已全部领取"))
                ])
              ], 10, ["onClick"]);
            }),
            128
            /* KEYED_FRAGMENT */
          )),
          $setup.windowed.padBottom ? (vue.openBlock(), vue.createElementBlock(
            "view",
            {
              key: 1,
              style: vue.normalizeStyle({ height: $setup.windowed.padBottom + "px" })
            },
            null,
            4
            /* STYLE */
          )) : vue.createCommentVNode("v-if", true),
          !$setup.lines.length && !$setup.loading ? (vue.openBlock(), vue.createElementBlock("view", {
            key: 2,
            class: "empty"
          }, [
            vue.createElementVNode("text", { class: "empty-icon" }, "📦")
          ])) : $setup.lines.length > $setup.windowed.items.length ? (vue.openBlock(), vue.createElementBlock(
            "view",
            {
              key: 3,
              class: "loading-tip end-tip"
            },
            " 显示 " + vue.toDisplayString($setup.windowed.items.length) + "/" + vue.toDisplayString($setup.lines.length) + " 行 · 滚动查看更多 ",
            1
            /* TEXT */
          )) : vue.createCommentVNode("v-if", true),
          vue.createElementVNode("view", { class: "scroll-bottom-pad" })
        ],
        32
        /* NEED_HYDRATION */
      ),
      vue.createElementVNode("view", { class: "footer" }, [
        vue.createElementVNode("button", {
          class: "submit-btn",
          type: "primary",
          loading: $setup.submitting,
          disabled: !$setup.submitableCount,
          onClick: $setup.onSubmit
        }, " 确认领料" + vue.toDisplayString($setup.submitableCount ? ` (${$setup.submitableCount})` : ""), 9, ["loading", "disabled"])
      ])
    ]);
  }
  const PagesPickingProductionIssueScan = /* @__PURE__ */ _export_sfc(_sfc_main$j, [["render", _sfc_render$i], ["__scopeId", "data-v-aadb7a20"], ["__file", "D:/AAA/WMS/wms-pda/src/pages/picking/production-issue-scan.vue"]]);
  const BILL_TYPE$9 = "PRODUCTION_FEED";
  const _sfc_main$i = {
    __name: "production-feed",
    setup(__props, { expose: __expose }) {
      __expose();
      const scanInputRef = vue.ref(null);
      const billType = vue.ref(BILL_TYPE$9);
      const busy = vue.ref(false);
      const { alive, refocusScanInput } = usePageAlive();
      const {
        loading,
        keyword,
        notices,
        loadList,
        loadListOnShow,
        statusLabel,
        statusClass
      } = useNoticeBillList(billType);
      async function openByBarcode(barcode) {
        if (!alive.value || busy.value) return;
        const raw = (barcode || "").trim();
        if (!raw) return;
        busy.value = true;
        uni.showLoading({ title: "打开中...", mask: true });
        try {
          let billNo = raw;
          try {
            const res = await resolveNoticeBarcode(BILL_TYPE$9, raw);
            if (res == null ? void 0 : res.billNo) billNo = String(res.billNo).trim();
          } catch {
          }
          if (!billNo) {
            uni.showToast({ title: "无法识别补料单号", icon: "none" });
            return;
          }
          keyword.value = billNo;
          openBill({ billNo });
        } finally {
          uni.hideLoading();
          busy.value = false;
          refocusScanInput(scanInputRef, 300);
        }
      }
      function onScan(barcode) {
        openByBarcode(barcode);
      }
      function onSearch(val) {
        openByBarcode(val || keyword.value);
      }
      function openBill(item) {
        if (!(item == null ? void 0 : item.billNo)) return;
        uni.navigateTo({
          url: `/pages/picking/production-feed-scan?billNo=${encodeURIComponent(item.billNo)}`
        });
      }
      onLoad(() => uni.setNavigationBarTitle({ title: "生产补料" }));
      onShow(() => loadListOnShow());
      vue.onMounted(() => refocusScanInput(scanInputRef, 500));
      const __returned__ = { BILL_TYPE: BILL_TYPE$9, scanInputRef, billType, busy, alive, refocusScanInput, loading, keyword, notices, loadList, loadListOnShow, statusLabel, statusClass, openByBarcode, onScan, onSearch, openBill, ref: vue.ref, onMounted: vue.onMounted, get onLoad() {
        return onLoad;
      }, get onShow() {
        return onShow;
      }, ScanSearchBar, get resolveNoticeBarcode() {
        return resolveNoticeBarcode;
      }, get useNoticeBillList() {
        return useNoticeBillList;
      }, get usePageAlive() {
        return usePageAlive;
      }, get formatMaterialLineCount() {
        return formatMaterialLineCount;
      } };
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  };
  function _sfc_render$h(_ctx, _cache, $props, $setup, $data, $options) {
    return vue.openBlock(), vue.createElementBlock("view", { class: "page" }, [
      vue.createElementVNode("view", { class: "search-top" }, [
        vue.createVNode($setup["ScanSearchBar"], {
          ref: "scanInputRef",
          modelValue: $setup.keyword,
          "onUpdate:modelValue": _cache[0] || (_cache[0] = ($event) => $setup.keyword = $event),
          disabled: $setup.busy,
          placeholder: "扫码或搜索生产补料单号/车间",
          "action-text": "打开",
          onScan: $setup.onScan,
          onSearch: $setup.onSearch
        }, null, 8, ["modelValue", "disabled"])
      ]),
      vue.createElementVNode("scroll-view", {
        class: "list-scroll",
        "scroll-y": "",
        "show-scrollbar": false
      }, [
        (vue.openBlock(true), vue.createElementBlock(
          vue.Fragment,
          null,
          vue.renderList($setup.notices, (item, index) => {
            return vue.openBlock(), vue.createElementBlock("view", {
              key: item.billNo || "row-" + index,
              class: "bill-row",
              onClick: ($event) => $setup.openBill(item)
            }, [
              vue.createElementVNode("view", { class: "row-main" }, [
                vue.createElementVNode(
                  "text",
                  { class: "bill-no" },
                  vue.toDisplayString(item.billNo || "（单号缺失）"),
                  1
                  /* TEXT */
                ),
                vue.createElementVNode(
                  "text",
                  { class: "bill-supplier" },
                  vue.toDisplayString(item.supplierName || item.supplierCode || "-"),
                  1
                  /* TEXT */
                ),
                vue.createElementVNode("text", { class: "bill-meta" }, [
                  $setup.formatMaterialLineCount(item) ? (vue.openBlock(), vue.createElementBlock(
                    "text",
                    {
                      key: 0,
                      class: "bill-lines"
                    },
                    vue.toDisplayString($setup.formatMaterialLineCount(item)),
                    1
                    /* TEXT */
                  )) : vue.createCommentVNode("v-if", true),
                  item.inProgress ? (vue.openBlock(), vue.createElementBlock(
                    "text",
                    { key: 1 },
                    " · 已勾 " + vue.toDisplayString(item.checkedLines || 0),
                    1
                    /* TEXT */
                  )) : vue.createCommentVNode("v-if", true),
                  item.locked && item.lockUserName ? (vue.openBlock(), vue.createElementBlock(
                    "text",
                    {
                      key: 2,
                      class: "bill-lock"
                    },
                    " · " + vue.toDisplayString(item.lockUserName) + "操作中",
                    1
                    /* TEXT */
                  )) : vue.createCommentVNode("v-if", true)
                ])
              ]),
              vue.createElementVNode("view", { class: "row-side" }, [
                vue.createElementVNode(
                  "text",
                  {
                    class: vue.normalizeClass(["status-tag", $setup.statusClass(item)])
                  },
                  vue.toDisplayString($setup.statusLabel(item)),
                  3
                  /* TEXT, CLASS */
                ),
                vue.createElementVNode("text", { class: "arrow" }, "›")
              ])
            ], 8, ["onClick"]);
          }),
          128
          /* KEYED_FRAGMENT */
        )),
        !$setup.notices.length && !$setup.loading ? (vue.openBlock(), vue.createElementBlock("view", {
          key: 0,
          class: "empty"
        }, [
          vue.createElementVNode("text", { class: "empty-icon" }, "📋"),
          vue.createElementVNode("text", { class: "empty-text" }, "暂无未审核的生产补料单"),
          vue.createElementVNode("text", { class: "empty-hint" }, "可直接扫描补料单二维码进入明细")
        ])) : vue.createCommentVNode("v-if", true),
        $setup.loading && !$setup.notices.length ? (vue.openBlock(), vue.createElementBlock("view", {
          key: 1,
          class: "loading-tip"
        }, "加载中...")) : $setup.notices.length ? (vue.openBlock(), vue.createElementBlock(
          "view",
          {
            key: 2,
            class: "loading-tip end-tip"
          },
          "共 " + vue.toDisplayString($setup.notices.length) + " 条",
          1
          /* TEXT */
        )) : vue.createCommentVNode("v-if", true)
      ])
    ]);
  }
  const PagesPickingProductionFeed = /* @__PURE__ */ _export_sfc(_sfc_main$i, [["render", _sfc_render$h], ["__scopeId", "data-v-f25b30d2"], ["__file", "D:/AAA/WMS/wms-pda/src/pages/picking/production-feed.vue"]]);
  const BILL_TYPE$8 = "PRODUCTION_FEED";
  const useProductionFeedScan = createNoticeBillScan(BILL_TYPE$8, {
    notOnBill: "该物料不在本生产补料单中",
    linesNotReady: "补料单明细未加载完成，请返回重新进入",
    backOnSubmitSuccess: true
  });
  const _sfc_main$h = {
    __name: "production-feed-scan",
    setup(__props, { expose: __expose }) {
      __expose();
      const billNo = vue.ref("");
      const scanInputRef = vue.ref(null);
      const qtyDrafts = vue.reactive({});
      const updatingLineNo = vue.ref(null);
      const { alive, refocusScanInput } = usePageAlive();
      const {
        loading,
        submitting,
        detail,
        lines,
        checkedCount,
        submitableCount,
        loadDetailOnShow,
        handleScan,
        toggleCheck,
        updateQty,
        formatQty: formatQty2,
        submit,
        rowClass,
        lastHighlightLineNo,
        isLabelScanned: isLabelScanned2
      } = useProductionFeedScan(billNo);
      const { windowed, onScroll: onListScroll, pinLine } = useWindowedLines(lines);
      const partialCount = vue.computed(
        () => lines.value.filter((l) => isPartialLine(l)).length
      );
      function isDoneLine(line) {
        const submitted = Number(line.submittedQty) || 0;
        const plan = Number(line.planQty) || 0;
        return plan > 0 && submitted >= plan;
      }
      function isPartialLine(line) {
        const submitted = Number(line.submittedQty) || 0;
        const plan = Number(line.planQty) || 0;
        return submitted > 0 && submitted < plan;
      }
      function syncQtyDrafts() {
        lines.value.forEach((line) => {
          qtyDrafts[line.lineNo] = formatQtyInput(line.pendingSubmitQty || 0, line.unitCode);
        });
      }
      function getQtyDraft(line) {
        if (qtyDrafts[line.lineNo] == null) {
          qtyDrafts[line.lineNo] = formatQtyInput(line.pendingSubmitQty || 0, line.unitCode);
        }
        return qtyDrafts[line.lineNo];
      }
      function onQtyInput(line, e) {
        qtyDrafts[line.lineNo] = sanitizeDecimalInput(e.detail.value, qtyDecimalScale(line.unitCode));
      }
      async function onQtyBlur(line) {
        const raw = qtyDrafts[line.lineNo];
        const num = raw === "" || raw == null ? 0 : Number(raw);
        if (Number.isNaN(num) || num < 0) {
          qtyDrafts[line.lineNo] = formatQtyInput(line.pendingSubmitQty || 0, line.unitCode);
          return;
        }
        const current = Number(line.pendingSubmitQty) || 0;
        if (num === current) return;
        updatingLineNo.value = line.lineNo;
        const ok = await updateQty(line.lineNo, num);
        updatingLineNo.value = null;
        if (ok) {
          const updated = lines.value.find((l) => l.lineNo === line.lineNo);
          if (updated) qtyDrafts[line.lineNo] = formatQtyInput(updated.pendingSubmitQty || 0, updated.unitCode);
        } else {
          qtyDrafts[line.lineNo] = formatQtyInput(line.pendingSubmitQty || 0, line.unitCode);
        }
      }
      async function onScan(barcode) {
        if (!alive.value) return;
        const line = await handleScan(barcode);
        if ((line == null ? void 0 : line.lineNo) != null) pinLine(line.lineNo);
        else if (lastHighlightLineNo.value != null) pinLine(lastHighlightLineNo.value);
        syncQtyDrafts();
        refocusScanInput(scanInputRef, 300);
      }
      function onToggle(line) {
        toggleCheck(line.lineNo, !line.checked);
      }
      function onRowTap(line) {
      }
      async function onSubmit() {
        const ok = await submit();
        if (ok) syncQtyDrafts();
      }
      onLoad((options) => {
        billNo.value = decodeURIComponent((options == null ? void 0 : options.billNo) || "");
        uni.setNavigationBarTitle({ title: "补料确认" });
      });
      onShow(async () => {
        if (billNo.value) {
          await loadDetailOnShow();
          syncQtyDrafts();
        }
        refocusScanInput(scanInputRef, 400);
      });
      const __returned__ = { billNo, scanInputRef, qtyDrafts, updatingLineNo, alive, refocusScanInput, loading, submitting, detail, lines, checkedCount, submitableCount, loadDetailOnShow, handleScan, toggleCheck, updateQty, formatQty: formatQty2, submit, rowClass, lastHighlightLineNo, isLabelScanned: isLabelScanned2, windowed, onListScroll, pinLine, partialCount, isDoneLine, isPartialLine, syncQtyDrafts, getQtyDraft, onQtyInput, onQtyBlur, onScan, onToggle, onRowTap, onSubmit, ref: vue.ref, computed: vue.computed, reactive: vue.reactive, get onLoad() {
        return onLoad;
      }, get onShow() {
        return onShow;
      }, CompactScanBox, get useProductionFeedScan() {
        return useProductionFeedScan;
      }, get usePageAlive() {
        return usePageAlive;
      }, get useWindowedLines() {
        return useWindowedLines;
      }, get sanitizeDecimalInput() {
        return sanitizeDecimalInput;
      }, get qtyDecimalScale() {
        return qtyDecimalScale;
      }, get formatQtyInput() {
        return formatQtyInput;
      } };
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  };
  function _sfc_render$g(_ctx, _cache, $props, $setup, $data, $options) {
    return vue.openBlock(), vue.createElementBlock("view", { class: "page" }, [
      vue.createElementVNode("view", { class: "scan-top" }, [
        vue.createVNode($setup["CompactScanBox"], {
          ref: "scanInputRef",
          disabled: $setup.loading || $setup.submitting,
          onScan: $setup.onScan
        }, null, 8, ["disabled"])
      ]),
      $setup.detail ? (vue.openBlock(), vue.createElementBlock("view", {
        key: 0,
        class: "order-bar"
      }, [
        vue.createElementVNode("view", { class: "order-info" }, [
          vue.createElementVNode(
            "text",
            { class: "order-no" },
            vue.toDisplayString($setup.detail.billNo),
            1
            /* TEXT */
          ),
          vue.createElementVNode(
            "text",
            { class: "order-sub" },
            vue.toDisplayString($setup.detail.supplierName || $setup.detail.supplierCode || "-"),
            1
            /* TEXT */
          ),
          $setup.detail.erpBillNo ? (vue.openBlock(), vue.createElementBlock(
            "text",
            {
              key: 0,
              class: "order-erp"
            },
            "已审核 " + vue.toDisplayString($setup.detail.erpBillNo),
            1
            /* TEXT */
          )) : vue.createCommentVNode("v-if", true)
        ]),
        vue.createElementVNode("view", { class: "order-stat-wrap" }, [
          vue.createElementVNode(
            "text",
            { class: "order-stat" },
            vue.toDisplayString($setup.checkedCount) + "/" + vue.toDisplayString($setup.lines.length) + " 已勾",
            1
            /* TEXT */
          ),
          $setup.partialCount ? (vue.openBlock(), vue.createElementBlock(
            "text",
            {
              key: 0,
              class: "order-partial"
            },
            "部分已补 " + vue.toDisplayString($setup.partialCount),
            1
            /* TEXT */
          )) : vue.createCommentVNode("v-if", true)
        ])
      ])) : vue.createCommentVNode("v-if", true),
      vue.createElementVNode(
        "scroll-view",
        {
          class: "list-scroll",
          "scroll-y": "",
          "show-scrollbar": false,
          onScroll: _cache[1] || (_cache[1] = (...args) => $setup.onListScroll && $setup.onListScroll(...args))
        },
        [
          $setup.windowed.padTop ? (vue.openBlock(), vue.createElementBlock(
            "view",
            {
              key: 0,
              style: vue.normalizeStyle({ height: $setup.windowed.padTop + "px" })
            },
            null,
            4
            /* STYLE */
          )) : vue.createCommentVNode("v-if", true),
          (vue.openBlock(true), vue.createElementBlock(
            vue.Fragment,
            null,
            vue.renderList($setup.windowed.items, (line) => {
              return vue.openBlock(), vue.createElementBlock("view", {
                key: line.lineNo,
                class: vue.normalizeClass(["mat-row", $setup.rowClass(line)]),
                onClick: ($event) => $setup.onRowTap(line)
              }, [
                vue.createElementVNode("view", { class: "row-header" }, [
                  vue.createElementVNode("view", {
                    class: "check-box",
                    onClick: vue.withModifiers(($event) => $setup.onToggle(line), ["stop"])
                  }, [
                    vue.createElementVNode(
                      "view",
                      {
                        class: vue.normalizeClass(["check-inner", line.checked && "on"])
                      },
                      [
                        line.checked ? (vue.openBlock(), vue.createElementBlock("text", {
                          key: 0,
                          class: "check-mark"
                        }, "✓")) : vue.createCommentVNode("v-if", true)
                      ],
                      2
                      /* CLASS */
                    )
                  ], 8, ["onClick"]),
                  vue.createElementVNode("view", { class: "row-main" }, [
                    vue.createElementVNode("view", { class: "name-row" }, [
                      vue.createElementVNode(
                        "text",
                        { class: "mat-code" },
                        vue.toDisplayString(line.materialCode),
                        1
                        /* TEXT */
                      ),
                      $setup.isPartialLine(line) ? (vue.openBlock(), vue.createElementBlock("text", {
                        key: 0,
                        class: "partial-tag"
                      }, "部分已补")) : vue.createCommentVNode("v-if", true)
                    ]),
                    vue.createElementVNode(
                      "text",
                      { class: "mat-name" },
                      vue.toDisplayString(line.materialName || "-"),
                      1
                      /* TEXT */
                    ),
                    vue.createElementVNode(
                      "text",
                      { class: "mat-spec" },
                      "规格 " + vue.toDisplayString(line.specification || "-"),
                      1
                      /* TEXT */
                    ),
                    vue.createElementVNode(
                      "text",
                      { class: "mat-batch" },
                      "批次 " + vue.toDisplayString(line.batchNo || "-"),
                      1
                      /* TEXT */
                    ),
                    vue.createElementVNode(
                      "text",
                      { class: "mat-wh" },
                      "仓库 " + vue.toDisplayString(line.erpStockCode || "-"),
                      1
                      /* TEXT */
                    )
                  ])
                ]),
                vue.createElementVNode("view", {
                  class: "qty-panel",
                  onClick: _cache[0] || (_cache[0] = vue.withModifiers(() => {
                  }, ["stop"]))
                }, [
                  vue.createElementVNode("view", { class: "qty-grid" }, [
                    vue.createElementVNode("view", { class: "qty-cell" }, [
                      vue.createElementVNode("text", { class: "qty-label" }, "计划"),
                      vue.createElementVNode(
                        "text",
                        { class: "qty-value" },
                        vue.toDisplayString($setup.formatQty(line.planQty, line.unitCode)),
                        1
                        /* TEXT */
                      )
                    ]),
                    vue.createElementVNode("view", { class: "qty-cell" }, [
                      vue.createElementVNode("text", { class: "qty-label" }, "已补"),
                      vue.createElementVNode(
                        "text",
                        { class: "qty-value submitted" },
                        vue.toDisplayString($setup.formatQty(line.submittedQty, line.unitCode)),
                        1
                        /* TEXT */
                      )
                    ]),
                    vue.createElementVNode("view", { class: "qty-cell" }, [
                      vue.createElementVNode("text", { class: "qty-label" }, "可补"),
                      vue.createElementVNode(
                        "text",
                        { class: "qty-value remain" },
                        vue.toDisplayString($setup.formatQty(line.remainQty, line.unitCode)),
                        1
                        /* TEXT */
                      )
                    ]),
                    vue.createElementVNode("view", { class: "qty-cell unit-cell" }, [
                      vue.createElementVNode("text", { class: "qty-label" }, "单位"),
                      vue.createElementVNode(
                        "text",
                        { class: "qty-value unit" },
                        vue.toDisplayString(line.unitCode || "PCS"),
                        1
                        /* TEXT */
                      )
                    ])
                  ]),
                  !$setup.isDoneLine(line) ? (vue.openBlock(), vue.createElementBlock("view", {
                    key: 0,
                    class: "qty-edit"
                  }, [
                    vue.createElementVNode("text", { class: "qty-edit-label" }, "本次补料"),
                    vue.createElementVNode("input", {
                      class: "qty-input",
                      type: "text",
                      inputmode: "decimal",
                      value: $setup.getQtyDraft(line),
                      disabled: $setup.updatingLineNo === line.lineNo,
                      placeholder: "0",
                      onInput: ($event) => $setup.onQtyInput(line, $event),
                      onBlur: ($event) => $setup.onQtyBlur(line),
                      onConfirm: ($event) => $setup.onQtyBlur(line)
                    }, null, 40, ["value", "disabled", "onInput", "onBlur", "onConfirm"]),
                    vue.createElementVNode(
                      "text",
                      { class: "qty-edit-unit" },
                      vue.toDisplayString(line.unitCode || "PCS"),
                      1
                      /* TEXT */
                    )
                  ])) : (vue.openBlock(), vue.createElementBlock("view", {
                    key: 1,
                    class: "qty-done-tip"
                  }, "已全部补完"))
                ])
              ], 10, ["onClick"]);
            }),
            128
            /* KEYED_FRAGMENT */
          )),
          $setup.windowed.padBottom ? (vue.openBlock(), vue.createElementBlock(
            "view",
            {
              key: 1,
              style: vue.normalizeStyle({ height: $setup.windowed.padBottom + "px" })
            },
            null,
            4
            /* STYLE */
          )) : vue.createCommentVNode("v-if", true),
          !$setup.lines.length && !$setup.loading ? (vue.openBlock(), vue.createElementBlock("view", {
            key: 2,
            class: "empty"
          }, [
            vue.createElementVNode("text", { class: "empty-icon" }, "📦")
          ])) : $setup.lines.length > $setup.windowed.items.length ? (vue.openBlock(), vue.createElementBlock(
            "view",
            {
              key: 3,
              class: "loading-tip end-tip"
            },
            " 显示 " + vue.toDisplayString($setup.windowed.items.length) + "/" + vue.toDisplayString($setup.lines.length) + " 行 · 滚动查看更多 ",
            1
            /* TEXT */
          )) : vue.createCommentVNode("v-if", true),
          vue.createElementVNode("view", { class: "scroll-bottom-pad" })
        ],
        32
        /* NEED_HYDRATION */
      ),
      vue.createElementVNode("view", { class: "footer" }, [
        vue.createElementVNode("button", {
          class: "submit-btn",
          type: "primary",
          loading: $setup.submitting,
          disabled: !$setup.submitableCount,
          onClick: $setup.onSubmit
        }, " 确认补料" + vue.toDisplayString($setup.submitableCount ? ` (${$setup.submitableCount})` : ""), 9, ["loading", "disabled"])
      ])
    ]);
  }
  const PagesPickingProductionFeedScan = /* @__PURE__ */ _export_sfc(_sfc_main$h, [["render", _sfc_render$g], ["__scopeId", "data-v-902548ca"], ["__file", "D:/AAA/WMS/wms-pda/src/pages/picking/production-feed-scan.vue"]]);
  const BILL_TYPE$7 = "OUTSOURCE_FEED";
  const _sfc_main$g = {
    __name: "outsource-feed",
    setup(__props, { expose: __expose }) {
      __expose();
      const scanInputRef = vue.ref(null);
      const billType = vue.ref(BILL_TYPE$7);
      const busy = vue.ref(false);
      const { alive, refocusScanInput } = usePageAlive();
      const {
        loading,
        keyword,
        notices,
        loadListOnShow,
        statusLabel,
        statusClass
      } = useNoticeBillList(billType);
      async function openByBarcode(barcode) {
        if (!alive.value || busy.value) return;
        const raw = (barcode || "").trim();
        if (!raw) return;
        busy.value = true;
        uni.showLoading({ title: "打开中...", mask: true });
        try {
          let billNo = raw;
          try {
            const res = await resolveNoticeBarcode(BILL_TYPE$7, raw);
            if (res == null ? void 0 : res.billNo) billNo = String(res.billNo).trim();
          } catch {
          }
          if (!billNo) {
            uni.showToast({ title: "无法识别补料单号", icon: "none" });
            return;
          }
          keyword.value = billNo;
          openBill({ billNo });
        } finally {
          uni.hideLoading();
          busy.value = false;
          refocusScanInput(scanInputRef, 300);
        }
      }
      function onScan(barcode) {
        openByBarcode(barcode);
      }
      function onSearch(val) {
        openByBarcode(val || keyword.value);
      }
      function openBill(item) {
        if (!(item == null ? void 0 : item.billNo)) return;
        uni.navigateTo({
          url: `/pages/picking/outsource-feed-scan?billNo=${encodeURIComponent(item.billNo)}`
        });
      }
      onLoad(() => uni.setNavigationBarTitle({ title: "委外补料" }));
      onShow(() => loadListOnShow());
      vue.onMounted(() => refocusScanInput(scanInputRef, 500));
      const __returned__ = { BILL_TYPE: BILL_TYPE$7, scanInputRef, billType, busy, alive, refocusScanInput, loading, keyword, notices, loadListOnShow, statusLabel, statusClass, openByBarcode, onScan, onSearch, openBill, ref: vue.ref, onMounted: vue.onMounted, get onLoad() {
        return onLoad;
      }, get onShow() {
        return onShow;
      }, ScanSearchBar, get resolveNoticeBarcode() {
        return resolveNoticeBarcode;
      }, get useNoticeBillList() {
        return useNoticeBillList;
      }, get usePageAlive() {
        return usePageAlive;
      }, get formatMaterialLineCount() {
        return formatMaterialLineCount;
      } };
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  };
  function _sfc_render$f(_ctx, _cache, $props, $setup, $data, $options) {
    return vue.openBlock(), vue.createElementBlock("view", { class: "page" }, [
      vue.createElementVNode("view", { class: "search-top" }, [
        vue.createVNode($setup["ScanSearchBar"], {
          ref: "scanInputRef",
          modelValue: $setup.keyword,
          "onUpdate:modelValue": _cache[0] || (_cache[0] = ($event) => $setup.keyword = $event),
          disabled: $setup.busy,
          placeholder: "扫码或搜索委外补料单号/供应商",
          "action-text": "打开",
          onScan: $setup.onScan,
          onSearch: $setup.onSearch
        }, null, 8, ["modelValue", "disabled"])
      ]),
      vue.createElementVNode("scroll-view", {
        class: "list-scroll",
        "scroll-y": "",
        "show-scrollbar": false
      }, [
        (vue.openBlock(true), vue.createElementBlock(
          vue.Fragment,
          null,
          vue.renderList($setup.notices, (item, index) => {
            return vue.openBlock(), vue.createElementBlock("view", {
              key: item.billNo || "row-" + index,
              class: "bill-row",
              onClick: ($event) => $setup.openBill(item)
            }, [
              vue.createElementVNode("view", { class: "row-main" }, [
                vue.createElementVNode(
                  "text",
                  { class: "bill-no" },
                  vue.toDisplayString(item.billNo || "（单号缺失）"),
                  1
                  /* TEXT */
                ),
                vue.createElementVNode(
                  "text",
                  { class: "bill-supplier" },
                  vue.toDisplayString(item.supplierName || item.supplierCode || "-"),
                  1
                  /* TEXT */
                ),
                vue.createElementVNode("text", { class: "bill-meta" }, [
                  $setup.formatMaterialLineCount(item) ? (vue.openBlock(), vue.createElementBlock(
                    "text",
                    {
                      key: 0,
                      class: "bill-lines"
                    },
                    vue.toDisplayString($setup.formatMaterialLineCount(item)),
                    1
                    /* TEXT */
                  )) : vue.createCommentVNode("v-if", true),
                  item.inProgress ? (vue.openBlock(), vue.createElementBlock(
                    "text",
                    { key: 1 },
                    " · 已勾 " + vue.toDisplayString(item.checkedLines || 0),
                    1
                    /* TEXT */
                  )) : vue.createCommentVNode("v-if", true),
                  item.locked && item.lockUserName ? (vue.openBlock(), vue.createElementBlock(
                    "text",
                    {
                      key: 2,
                      class: "bill-lock"
                    },
                    " · " + vue.toDisplayString(item.lockUserName) + "操作中",
                    1
                    /* TEXT */
                  )) : vue.createCommentVNode("v-if", true)
                ])
              ]),
              vue.createElementVNode("view", { class: "row-side" }, [
                vue.createElementVNode(
                  "text",
                  {
                    class: vue.normalizeClass(["status-tag", $setup.statusClass(item)])
                  },
                  vue.toDisplayString($setup.statusLabel(item)),
                  3
                  /* TEXT, CLASS */
                ),
                vue.createElementVNode("text", { class: "arrow" }, "›")
              ])
            ], 8, ["onClick"]);
          }),
          128
          /* KEYED_FRAGMENT */
        )),
        !$setup.notices.length && !$setup.loading ? (vue.openBlock(), vue.createElementBlock("view", {
          key: 0,
          class: "empty"
        }, [
          vue.createElementVNode("text", { class: "empty-icon" }, "📋"),
          vue.createElementVNode("text", { class: "empty-text" }, "暂无未审核的委外补料单"),
          vue.createElementVNode("text", { class: "empty-hint" }, "可直接扫描补料单二维码进入明细")
        ])) : vue.createCommentVNode("v-if", true),
        $setup.loading && !$setup.notices.length ? (vue.openBlock(), vue.createElementBlock("view", {
          key: 1,
          class: "loading-tip"
        }, "加载中...")) : $setup.notices.length ? (vue.openBlock(), vue.createElementBlock(
          "view",
          {
            key: 2,
            class: "loading-tip end-tip"
          },
          "共 " + vue.toDisplayString($setup.notices.length) + " 条",
          1
          /* TEXT */
        )) : vue.createCommentVNode("v-if", true)
      ])
    ]);
  }
  const PagesPickingOutsourceFeed = /* @__PURE__ */ _export_sfc(_sfc_main$g, [["render", _sfc_render$f], ["__scopeId", "data-v-821095fa"], ["__file", "D:/AAA/WMS/wms-pda/src/pages/picking/outsource-feed.vue"]]);
  const BILL_TYPE$6 = "OUTSOURCE_FEED";
  const useOutsourceFeedScan = createNoticeBillScan(BILL_TYPE$6, {
    notOnBill: "该物料不在本委外补料单中",
    linesNotReady: "补料单明细未加载完成，请返回重新进入",
    backOnSubmitSuccess: true
  });
  const _sfc_main$f = {
    __name: "outsource-feed-scan",
    setup(__props, { expose: __expose }) {
      __expose();
      const billNo = vue.ref("");
      const scanInputRef = vue.ref(null);
      const qtyDrafts = vue.reactive({});
      const updatingLineNo = vue.ref(null);
      const { alive, refocusScanInput } = usePageAlive();
      const {
        loading,
        submitting,
        detail,
        lines,
        checkedCount,
        submitableCount,
        loadDetailOnShow,
        handleScan,
        toggleCheck,
        updateQty,
        formatQty: formatQty2,
        submit,
        rowClass,
        lastHighlightLineNo,
        isLabelScanned: isLabelScanned2
      } = useOutsourceFeedScan(billNo);
      const { windowed, onScroll: onListScroll, pinLine } = useWindowedLines(lines);
      const partialCount = vue.computed(
        () => lines.value.filter((l) => isPartialLine(l)).length
      );
      function isDoneLine(line) {
        const submitted = Number(line.submittedQty) || 0;
        const plan = Number(line.planQty) || 0;
        return plan > 0 && submitted >= plan;
      }
      function isPartialLine(line) {
        const submitted = Number(line.submittedQty) || 0;
        const plan = Number(line.planQty) || 0;
        return submitted > 0 && submitted < plan;
      }
      function syncQtyDrafts() {
        lines.value.forEach((line) => {
          qtyDrafts[line.lineNo] = formatQtyInput(line.pendingSubmitQty || 0, line.unitCode);
        });
      }
      function getQtyDraft(line) {
        if (qtyDrafts[line.lineNo] == null) {
          qtyDrafts[line.lineNo] = formatQtyInput(line.pendingSubmitQty || 0, line.unitCode);
        }
        return qtyDrafts[line.lineNo];
      }
      function onQtyInput(line, e) {
        qtyDrafts[line.lineNo] = sanitizeDecimalInput(e.detail.value, qtyDecimalScale(line.unitCode));
      }
      async function onQtyBlur(line) {
        const raw = qtyDrafts[line.lineNo];
        const num = raw === "" || raw == null ? 0 : Number(raw);
        if (Number.isNaN(num) || num < 0) {
          qtyDrafts[line.lineNo] = formatQtyInput(line.pendingSubmitQty || 0, line.unitCode);
          return;
        }
        const current = Number(line.pendingSubmitQty) || 0;
        if (num === current) return;
        updatingLineNo.value = line.lineNo;
        const ok = await updateQty(line.lineNo, num);
        updatingLineNo.value = null;
        if (ok) {
          const updated = lines.value.find((l) => l.lineNo === line.lineNo);
          if (updated) qtyDrafts[line.lineNo] = formatQtyInput(updated.pendingSubmitQty || 0, updated.unitCode);
        } else {
          qtyDrafts[line.lineNo] = formatQtyInput(line.pendingSubmitQty || 0, line.unitCode);
        }
      }
      async function onScan(barcode) {
        if (!alive.value) return;
        const line = await handleScan(barcode);
        if ((line == null ? void 0 : line.lineNo) != null) pinLine(line.lineNo);
        else if (lastHighlightLineNo.value != null) pinLine(lastHighlightLineNo.value);
        syncQtyDrafts();
        refocusScanInput(scanInputRef, 300);
      }
      function onToggle(line) {
        toggleCheck(line.lineNo, !line.checked);
      }
      function onRowTap(line) {
      }
      async function onSubmit() {
        const ok = await submit();
        if (ok) syncQtyDrafts();
      }
      onLoad((options) => {
        billNo.value = decodeURIComponent((options == null ? void 0 : options.billNo) || "");
        uni.setNavigationBarTitle({ title: "委外补料确认" });
      });
      onShow(async () => {
        if (billNo.value) {
          await loadDetailOnShow();
          syncQtyDrafts();
        }
        refocusScanInput(scanInputRef, 400);
      });
      const __returned__ = { billNo, scanInputRef, qtyDrafts, updatingLineNo, alive, refocusScanInput, loading, submitting, detail, lines, checkedCount, submitableCount, loadDetailOnShow, handleScan, toggleCheck, updateQty, formatQty: formatQty2, submit, rowClass, lastHighlightLineNo, isLabelScanned: isLabelScanned2, windowed, onListScroll, pinLine, partialCount, isDoneLine, isPartialLine, syncQtyDrafts, getQtyDraft, onQtyInput, onQtyBlur, onScan, onToggle, onRowTap, onSubmit, ref: vue.ref, computed: vue.computed, reactive: vue.reactive, get onLoad() {
        return onLoad;
      }, get onShow() {
        return onShow;
      }, CompactScanBox, get useOutsourceFeedScan() {
        return useOutsourceFeedScan;
      }, get usePageAlive() {
        return usePageAlive;
      }, get useWindowedLines() {
        return useWindowedLines;
      }, get sanitizeDecimalInput() {
        return sanitizeDecimalInput;
      }, get qtyDecimalScale() {
        return qtyDecimalScale;
      }, get formatQtyInput() {
        return formatQtyInput;
      } };
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  };
  function _sfc_render$e(_ctx, _cache, $props, $setup, $data, $options) {
    return vue.openBlock(), vue.createElementBlock("view", { class: "page" }, [
      vue.createElementVNode("view", { class: "scan-top" }, [
        vue.createVNode($setup["CompactScanBox"], {
          ref: "scanInputRef",
          disabled: $setup.loading || $setup.submitting,
          onScan: $setup.onScan
        }, null, 8, ["disabled"])
      ]),
      $setup.detail ? (vue.openBlock(), vue.createElementBlock("view", {
        key: 0,
        class: "order-bar"
      }, [
        vue.createElementVNode("view", { class: "order-info" }, [
          vue.createElementVNode(
            "text",
            { class: "order-no" },
            vue.toDisplayString($setup.detail.billNo),
            1
            /* TEXT */
          ),
          vue.createElementVNode(
            "text",
            { class: "order-sub" },
            vue.toDisplayString($setup.detail.supplierName || $setup.detail.supplierCode || "-"),
            1
            /* TEXT */
          ),
          $setup.detail.erpBillNo ? (vue.openBlock(), vue.createElementBlock(
            "text",
            {
              key: 0,
              class: "order-erp"
            },
            "已审核 " + vue.toDisplayString($setup.detail.erpBillNo),
            1
            /* TEXT */
          )) : vue.createCommentVNode("v-if", true)
        ]),
        vue.createElementVNode("view", { class: "order-stat-wrap" }, [
          vue.createElementVNode(
            "text",
            { class: "order-stat" },
            vue.toDisplayString($setup.checkedCount) + "/" + vue.toDisplayString($setup.lines.length) + " 已勾",
            1
            /* TEXT */
          ),
          $setup.partialCount ? (vue.openBlock(), vue.createElementBlock(
            "text",
            {
              key: 0,
              class: "order-partial"
            },
            "部分已补 " + vue.toDisplayString($setup.partialCount),
            1
            /* TEXT */
          )) : vue.createCommentVNode("v-if", true)
        ])
      ])) : vue.createCommentVNode("v-if", true),
      vue.createElementVNode(
        "scroll-view",
        {
          class: "list-scroll",
          "scroll-y": "",
          "show-scrollbar": false,
          onScroll: _cache[1] || (_cache[1] = (...args) => $setup.onListScroll && $setup.onListScroll(...args))
        },
        [
          $setup.windowed.padTop ? (vue.openBlock(), vue.createElementBlock(
            "view",
            {
              key: 0,
              style: vue.normalizeStyle({ height: $setup.windowed.padTop + "px" })
            },
            null,
            4
            /* STYLE */
          )) : vue.createCommentVNode("v-if", true),
          (vue.openBlock(true), vue.createElementBlock(
            vue.Fragment,
            null,
            vue.renderList($setup.windowed.items, (line) => {
              return vue.openBlock(), vue.createElementBlock("view", {
                key: line.lineNo,
                class: vue.normalizeClass(["mat-row", $setup.rowClass(line)]),
                onClick: ($event) => $setup.onRowTap(line)
              }, [
                vue.createElementVNode("view", { class: "row-header" }, [
                  vue.createElementVNode("view", {
                    class: "check-box",
                    onClick: vue.withModifiers(($event) => $setup.onToggle(line), ["stop"])
                  }, [
                    vue.createElementVNode(
                      "view",
                      {
                        class: vue.normalizeClass(["check-inner", line.checked && "on"])
                      },
                      [
                        line.checked ? (vue.openBlock(), vue.createElementBlock("text", {
                          key: 0,
                          class: "check-mark"
                        }, "✓")) : vue.createCommentVNode("v-if", true)
                      ],
                      2
                      /* CLASS */
                    )
                  ], 8, ["onClick"]),
                  vue.createElementVNode("view", { class: "row-main" }, [
                    vue.createElementVNode("view", { class: "name-row" }, [
                      vue.createElementVNode(
                        "text",
                        { class: "mat-code" },
                        vue.toDisplayString(line.materialCode),
                        1
                        /* TEXT */
                      ),
                      $setup.isPartialLine(line) ? (vue.openBlock(), vue.createElementBlock("text", {
                        key: 0,
                        class: "partial-tag"
                      }, "部分已补")) : vue.createCommentVNode("v-if", true)
                    ]),
                    vue.createElementVNode(
                      "text",
                      { class: "mat-name" },
                      vue.toDisplayString(line.materialName || "-"),
                      1
                      /* TEXT */
                    ),
                    vue.createElementVNode(
                      "text",
                      { class: "mat-spec" },
                      "规格 " + vue.toDisplayString(line.specification || "-"),
                      1
                      /* TEXT */
                    ),
                    vue.createElementVNode(
                      "text",
                      { class: "mat-batch" },
                      "批次 " + vue.toDisplayString(line.batchNo || "-"),
                      1
                      /* TEXT */
                    ),
                    vue.createElementVNode(
                      "text",
                      { class: "mat-wh" },
                      "仓库 " + vue.toDisplayString(line.erpStockCode || "-"),
                      1
                      /* TEXT */
                    )
                  ])
                ]),
                vue.createElementVNode("view", {
                  class: "qty-panel",
                  onClick: _cache[0] || (_cache[0] = vue.withModifiers(() => {
                  }, ["stop"]))
                }, [
                  vue.createElementVNode("view", { class: "qty-grid" }, [
                    vue.createElementVNode("view", { class: "qty-cell" }, [
                      vue.createElementVNode("text", { class: "qty-label" }, "计划"),
                      vue.createElementVNode(
                        "text",
                        { class: "qty-value" },
                        vue.toDisplayString($setup.formatQty(line.planQty, line.unitCode)),
                        1
                        /* TEXT */
                      )
                    ]),
                    vue.createElementVNode("view", { class: "qty-cell" }, [
                      vue.createElementVNode("text", { class: "qty-label" }, "已补"),
                      vue.createElementVNode(
                        "text",
                        { class: "qty-value submitted" },
                        vue.toDisplayString($setup.formatQty(line.submittedQty, line.unitCode)),
                        1
                        /* TEXT */
                      )
                    ]),
                    vue.createElementVNode("view", { class: "qty-cell" }, [
                      vue.createElementVNode("text", { class: "qty-label" }, "可补"),
                      vue.createElementVNode(
                        "text",
                        { class: "qty-value remain" },
                        vue.toDisplayString($setup.formatQty(line.remainQty, line.unitCode)),
                        1
                        /* TEXT */
                      )
                    ]),
                    vue.createElementVNode("view", { class: "qty-cell unit-cell" }, [
                      vue.createElementVNode("text", { class: "qty-label" }, "单位"),
                      vue.createElementVNode(
                        "text",
                        { class: "qty-value unit" },
                        vue.toDisplayString(line.unitCode || "PCS"),
                        1
                        /* TEXT */
                      )
                    ])
                  ]),
                  !$setup.isDoneLine(line) ? (vue.openBlock(), vue.createElementBlock("view", {
                    key: 0,
                    class: "qty-edit"
                  }, [
                    vue.createElementVNode("text", { class: "qty-edit-label" }, "本次补料"),
                    vue.createElementVNode("input", {
                      class: "qty-input",
                      type: "text",
                      inputmode: "decimal",
                      value: $setup.getQtyDraft(line),
                      disabled: $setup.updatingLineNo === line.lineNo,
                      placeholder: "0",
                      onInput: ($event) => $setup.onQtyInput(line, $event),
                      onBlur: ($event) => $setup.onQtyBlur(line),
                      onConfirm: ($event) => $setup.onQtyBlur(line)
                    }, null, 40, ["value", "disabled", "onInput", "onBlur", "onConfirm"]),
                    vue.createElementVNode(
                      "text",
                      { class: "qty-edit-unit" },
                      vue.toDisplayString(line.unitCode || "PCS"),
                      1
                      /* TEXT */
                    )
                  ])) : (vue.openBlock(), vue.createElementBlock("view", {
                    key: 1,
                    class: "qty-done-tip"
                  }, "已全部补完"))
                ])
              ], 10, ["onClick"]);
            }),
            128
            /* KEYED_FRAGMENT */
          )),
          $setup.windowed.padBottom ? (vue.openBlock(), vue.createElementBlock(
            "view",
            {
              key: 1,
              style: vue.normalizeStyle({ height: $setup.windowed.padBottom + "px" })
            },
            null,
            4
            /* STYLE */
          )) : vue.createCommentVNode("v-if", true),
          !$setup.lines.length && !$setup.loading ? (vue.openBlock(), vue.createElementBlock("view", {
            key: 2,
            class: "empty"
          }, [
            vue.createElementVNode("text", { class: "empty-icon" }, "📦")
          ])) : $setup.lines.length > $setup.windowed.items.length ? (vue.openBlock(), vue.createElementBlock(
            "view",
            {
              key: 3,
              class: "loading-tip end-tip"
            },
            " 显示 " + vue.toDisplayString($setup.windowed.items.length) + "/" + vue.toDisplayString($setup.lines.length) + " 行 · 滚动查看更多 ",
            1
            /* TEXT */
          )) : vue.createCommentVNode("v-if", true),
          vue.createElementVNode("view", { class: "scroll-bottom-pad" })
        ],
        32
        /* NEED_HYDRATION */
      ),
      vue.createElementVNode("view", { class: "footer" }, [
        vue.createElementVNode("button", {
          class: "submit-btn",
          type: "primary",
          loading: $setup.submitting,
          disabled: !$setup.submitableCount,
          onClick: $setup.onSubmit
        }, " 确认补料" + vue.toDisplayString($setup.submitableCount ? ` (${$setup.submitableCount})` : ""), 9, ["loading", "disabled"])
      ])
    ]);
  }
  const PagesPickingOutsourceFeedScan = /* @__PURE__ */ _export_sfc(_sfc_main$f, [["render", _sfc_render$e], ["__scopeId", "data-v-fd20c7a1"], ["__file", "D:/AAA/WMS/wms-pda/src/pages/picking/outsource-feed-scan.vue"]]);
  const BILL_TYPE$5 = "OUTSOURCE_ISSUE";
  const _sfc_main$e = {
    __name: "outsource-issue",
    setup(__props, { expose: __expose }) {
      __expose();
      const scanInputRef = vue.ref(null);
      const billType = vue.ref(BILL_TYPE$5);
      const busy = vue.ref(false);
      const { alive, refocusScanInput } = usePageAlive();
      const {
        loading,
        keyword,
        notices,
        loadListOnShow,
        statusLabel,
        statusClass
      } = useNoticeBillList(billType);
      async function openByBarcode(barcode) {
        if (!alive.value || busy.value) return;
        const raw = (barcode || "").trim();
        if (!raw) return;
        busy.value = true;
        uni.showLoading({ title: "打开中...", mask: true });
        try {
          let billNo = raw;
          try {
            const res = await resolveNoticeBarcode(BILL_TYPE$5, raw);
            if (res == null ? void 0 : res.billNo) billNo = String(res.billNo).trim();
          } catch {
          }
          if (!billNo) {
            uni.showToast({ title: "无法识别领料单号", icon: "none" });
            return;
          }
          keyword.value = billNo;
          openBill({ billNo });
        } finally {
          uni.hideLoading();
          busy.value = false;
          refocusScanInput(scanInputRef, 300);
        }
      }
      function onScan(barcode) {
        openByBarcode(barcode);
      }
      function onSearch(val) {
        openByBarcode(val || keyword.value);
      }
      function openBill(item) {
        if (!(item == null ? void 0 : item.billNo)) return;
        uni.navigateTo({
          url: `/pages/picking/outsource-issue-scan?billNo=${encodeURIComponent(item.billNo)}`
        });
      }
      onLoad(() => uni.setNavigationBarTitle({ title: "委外领料" }));
      onShow(() => loadListOnShow());
      vue.onMounted(() => refocusScanInput(scanInputRef, 500));
      const __returned__ = { BILL_TYPE: BILL_TYPE$5, scanInputRef, billType, busy, alive, refocusScanInput, loading, keyword, notices, loadListOnShow, statusLabel, statusClass, openByBarcode, onScan, onSearch, openBill, ref: vue.ref, onMounted: vue.onMounted, get onLoad() {
        return onLoad;
      }, get onShow() {
        return onShow;
      }, ScanSearchBar, get resolveNoticeBarcode() {
        return resolveNoticeBarcode;
      }, get useNoticeBillList() {
        return useNoticeBillList;
      }, get usePageAlive() {
        return usePageAlive;
      }, get formatMaterialLineCount() {
        return formatMaterialLineCount;
      } };
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  };
  function _sfc_render$d(_ctx, _cache, $props, $setup, $data, $options) {
    return vue.openBlock(), vue.createElementBlock("view", { class: "page" }, [
      vue.createElementVNode("view", { class: "search-top" }, [
        vue.createVNode($setup["ScanSearchBar"], {
          ref: "scanInputRef",
          modelValue: $setup.keyword,
          "onUpdate:modelValue": _cache[0] || (_cache[0] = ($event) => $setup.keyword = $event),
          disabled: $setup.busy,
          placeholder: "扫码或搜索委外领料单号/供应商",
          "action-text": "打开",
          onScan: $setup.onScan,
          onSearch: $setup.onSearch
        }, null, 8, ["modelValue", "disabled"])
      ]),
      vue.createElementVNode("scroll-view", {
        class: "list-scroll",
        "scroll-y": "",
        "show-scrollbar": false
      }, [
        (vue.openBlock(true), vue.createElementBlock(
          vue.Fragment,
          null,
          vue.renderList($setup.notices, (item, index) => {
            return vue.openBlock(), vue.createElementBlock("view", {
              key: item.billNo || "row-" + index,
              class: "bill-row",
              onClick: ($event) => $setup.openBill(item)
            }, [
              vue.createElementVNode("view", { class: "row-main" }, [
                vue.createElementVNode(
                  "text",
                  { class: "bill-no" },
                  vue.toDisplayString(item.billNo || "（单号缺失）"),
                  1
                  /* TEXT */
                ),
                vue.createElementVNode(
                  "text",
                  { class: "bill-supplier" },
                  vue.toDisplayString(item.supplierName || item.supplierCode || "-"),
                  1
                  /* TEXT */
                ),
                vue.createElementVNode("text", { class: "bill-meta" }, [
                  $setup.formatMaterialLineCount(item) ? (vue.openBlock(), vue.createElementBlock(
                    "text",
                    {
                      key: 0,
                      class: "bill-lines"
                    },
                    vue.toDisplayString($setup.formatMaterialLineCount(item)),
                    1
                    /* TEXT */
                  )) : vue.createCommentVNode("v-if", true),
                  item.inProgress ? (vue.openBlock(), vue.createElementBlock(
                    "text",
                    { key: 1 },
                    " · 已勾 " + vue.toDisplayString(item.checkedLines || 0),
                    1
                    /* TEXT */
                  )) : vue.createCommentVNode("v-if", true),
                  item.locked && item.lockUserName ? (vue.openBlock(), vue.createElementBlock(
                    "text",
                    {
                      key: 2,
                      class: "bill-lock"
                    },
                    " · " + vue.toDisplayString(item.lockUserName) + "操作中",
                    1
                    /* TEXT */
                  )) : vue.createCommentVNode("v-if", true)
                ])
              ]),
              vue.createElementVNode("view", { class: "row-side" }, [
                vue.createElementVNode(
                  "text",
                  {
                    class: vue.normalizeClass(["status-tag", $setup.statusClass(item)])
                  },
                  vue.toDisplayString($setup.statusLabel(item)),
                  3
                  /* TEXT, CLASS */
                ),
                vue.createElementVNode("text", { class: "arrow" }, "›")
              ])
            ], 8, ["onClick"]);
          }),
          128
          /* KEYED_FRAGMENT */
        )),
        !$setup.notices.length && !$setup.loading ? (vue.openBlock(), vue.createElementBlock("view", {
          key: 0,
          class: "empty"
        }, [
          vue.createElementVNode("text", { class: "empty-icon" }, "📋"),
          vue.createElementVNode("text", { class: "empty-text" }, "暂无未审核的委外领料单"),
          vue.createElementVNode("text", { class: "empty-hint" }, "可直接扫描领料单二维码进入明细")
        ])) : vue.createCommentVNode("v-if", true),
        $setup.loading && !$setup.notices.length ? (vue.openBlock(), vue.createElementBlock("view", {
          key: 1,
          class: "loading-tip"
        }, "加载中...")) : $setup.notices.length ? (vue.openBlock(), vue.createElementBlock(
          "view",
          {
            key: 2,
            class: "loading-tip end-tip"
          },
          "共 " + vue.toDisplayString($setup.notices.length) + " 条",
          1
          /* TEXT */
        )) : vue.createCommentVNode("v-if", true)
      ])
    ]);
  }
  const PagesPickingOutsourceIssue = /* @__PURE__ */ _export_sfc(_sfc_main$e, [["render", _sfc_render$d], ["__scopeId", "data-v-88752e11"], ["__file", "D:/AAA/WMS/wms-pda/src/pages/picking/outsource-issue.vue"]]);
  const BILL_TYPE$4 = "OUTSOURCE_ISSUE";
  const useOutsourceIssueScan = createNoticeBillScan(BILL_TYPE$4, {
    notOnBill: "该物料不在本委外领料单中",
    linesNotReady: "领料单明细未加载完成，请返回重新进入",
    backOnSubmitSuccess: true
  });
  const _sfc_main$d = {
    __name: "outsource-issue-scan",
    setup(__props, { expose: __expose }) {
      __expose();
      const billNo = vue.ref("");
      const scanInputRef = vue.ref(null);
      const qtyDrafts = vue.reactive({});
      const updatingLineNo = vue.ref(null);
      const { alive, refocusScanInput } = usePageAlive();
      const {
        loading,
        submitting,
        detail,
        lines,
        checkedCount,
        submitableCount,
        loadDetailOnShow,
        handleScan,
        toggleCheck,
        updateQty,
        formatQty: formatQty2,
        submit,
        rowClass,
        lastHighlightLineNo
      } = useOutsourceIssueScan(billNo);
      const { windowed, onScroll: onListScroll, pinLine } = useWindowedLines(lines);
      const partialCount = vue.computed(
        () => lines.value.filter((l) => isPartialLine(l)).length
      );
      function isDoneLine(line) {
        const submitted = Number(line.submittedQty) || 0;
        const plan = Number(line.planQty) || 0;
        return plan > 0 && submitted >= plan;
      }
      function isPartialLine(line) {
        const submitted = Number(line.submittedQty) || 0;
        const plan = Number(line.planQty) || 0;
        return submitted > 0 && submitted < plan;
      }
      function syncQtyDrafts() {
        lines.value.forEach((line) => {
          qtyDrafts[line.lineNo] = formatQtyInput(line.pendingSubmitQty || 0, line.unitCode);
        });
      }
      function getQtyDraft(line) {
        if (qtyDrafts[line.lineNo] == null) {
          qtyDrafts[line.lineNo] = formatQtyInput(line.pendingSubmitQty || 0, line.unitCode);
        }
        return qtyDrafts[line.lineNo];
      }
      function onQtyInput(line, e) {
        qtyDrafts[line.lineNo] = sanitizeDecimalInput(e.detail.value, qtyDecimalScale(line.unitCode));
      }
      async function onQtyBlur(line) {
        const raw = qtyDrafts[line.lineNo];
        const num = raw === "" || raw == null ? 0 : Number(raw);
        if (Number.isNaN(num) || num < 0) {
          qtyDrafts[line.lineNo] = formatQtyInput(line.pendingSubmitQty || 0, line.unitCode);
          return;
        }
        const current = Number(line.pendingSubmitQty) || 0;
        if (num === current) return;
        updatingLineNo.value = line.lineNo;
        const ok = await updateQty(line.lineNo, num);
        updatingLineNo.value = null;
        if (ok) {
          const updated = lines.value.find((l) => l.lineNo === line.lineNo);
          if (updated) qtyDrafts[line.lineNo] = formatQtyInput(updated.pendingSubmitQty || 0, updated.unitCode);
        } else {
          qtyDrafts[line.lineNo] = formatQtyInput(line.pendingSubmitQty || 0, line.unitCode);
        }
      }
      async function onScan(barcode) {
        if (!alive.value) return;
        const line = await handleScan(barcode);
        if ((line == null ? void 0 : line.lineNo) != null) pinLine(line.lineNo);
        else if (lastHighlightLineNo.value != null) pinLine(lastHighlightLineNo.value);
        syncQtyDrafts();
        refocusScanInput(scanInputRef, 300);
      }
      function onToggle(line) {
        toggleCheck(line.lineNo, !line.checked);
      }
      function onRowTap(line) {
        if (!line.checked && !isDoneLine(line)) toggleCheck(line.lineNo, true);
      }
      async function onSubmit() {
        const ok = await submit();
        if (ok) syncQtyDrafts();
      }
      onLoad((options) => {
        billNo.value = decodeURIComponent((options == null ? void 0 : options.billNo) || "");
        uni.setNavigationBarTitle({ title: "委外领料确认" });
      });
      onShow(async () => {
        if (billNo.value) {
          await loadDetailOnShow();
          syncQtyDrafts();
        }
        refocusScanInput(scanInputRef, 400);
      });
      const __returned__ = { billNo, scanInputRef, qtyDrafts, updatingLineNo, alive, refocusScanInput, loading, submitting, detail, lines, checkedCount, submitableCount, loadDetailOnShow, handleScan, toggleCheck, updateQty, formatQty: formatQty2, submit, rowClass, lastHighlightLineNo, windowed, onListScroll, pinLine, partialCount, isDoneLine, isPartialLine, syncQtyDrafts, getQtyDraft, onQtyInput, onQtyBlur, onScan, onToggle, onRowTap, onSubmit, ref: vue.ref, computed: vue.computed, reactive: vue.reactive, get onLoad() {
        return onLoad;
      }, get onShow() {
        return onShow;
      }, CompactScanBox, get useOutsourceIssueScan() {
        return useOutsourceIssueScan;
      }, get usePageAlive() {
        return usePageAlive;
      }, get sanitizeDecimalInput() {
        return sanitizeDecimalInput;
      }, get qtyDecimalScale() {
        return qtyDecimalScale;
      }, get formatQtyInput() {
        return formatQtyInput;
      }, get useWindowedLines() {
        return useWindowedLines;
      } };
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  };
  function _sfc_render$c(_ctx, _cache, $props, $setup, $data, $options) {
    return vue.openBlock(), vue.createElementBlock("view", { class: "page" }, [
      vue.createElementVNode("view", { class: "scan-top" }, [
        vue.createVNode($setup["CompactScanBox"], {
          ref: "scanInputRef",
          disabled: $setup.loading || $setup.submitting,
          onScan: $setup.onScan
        }, null, 8, ["disabled"])
      ]),
      $setup.detail ? (vue.openBlock(), vue.createElementBlock("view", {
        key: 0,
        class: "order-bar"
      }, [
        vue.createElementVNode("view", { class: "order-info" }, [
          vue.createElementVNode(
            "text",
            { class: "order-no" },
            vue.toDisplayString($setup.detail.billNo),
            1
            /* TEXT */
          ),
          vue.createElementVNode(
            "text",
            { class: "order-sub" },
            vue.toDisplayString($setup.detail.supplierName || $setup.detail.supplierCode || "-"),
            1
            /* TEXT */
          ),
          $setup.detail.erpBillNo ? (vue.openBlock(), vue.createElementBlock(
            "text",
            {
              key: 0,
              class: "order-erp"
            },
            "委外领料单 " + vue.toDisplayString($setup.detail.erpBillNo),
            1
            /* TEXT */
          )) : vue.createCommentVNode("v-if", true)
        ]),
        vue.createElementVNode("view", { class: "order-stat-wrap" }, [
          vue.createElementVNode(
            "text",
            { class: "order-stat" },
            vue.toDisplayString($setup.checkedCount) + "/" + vue.toDisplayString($setup.lines.length) + " 已勾",
            1
            /* TEXT */
          ),
          $setup.partialCount ? (vue.openBlock(), vue.createElementBlock(
            "text",
            {
              key: 0,
              class: "order-partial"
            },
            "部分已领 " + vue.toDisplayString($setup.partialCount),
            1
            /* TEXT */
          )) : vue.createCommentVNode("v-if", true)
        ])
      ])) : vue.createCommentVNode("v-if", true),
      vue.createElementVNode(
        "scroll-view",
        {
          class: "list-scroll",
          "scroll-y": "",
          "show-scrollbar": false,
          onScroll: _cache[1] || (_cache[1] = (...args) => $setup.onListScroll && $setup.onListScroll(...args))
        },
        [
          $setup.windowed.padTop ? (vue.openBlock(), vue.createElementBlock(
            "view",
            {
              key: 0,
              style: vue.normalizeStyle({ height: $setup.windowed.padTop + "px" })
            },
            null,
            4
            /* STYLE */
          )) : vue.createCommentVNode("v-if", true),
          (vue.openBlock(true), vue.createElementBlock(
            vue.Fragment,
            null,
            vue.renderList($setup.windowed.items, (line) => {
              return vue.openBlock(), vue.createElementBlock("view", {
                key: line.lineNo,
                class: vue.normalizeClass(["mat-row", $setup.rowClass(line)]),
                onClick: ($event) => $setup.onRowTap(line)
              }, [
                vue.createElementVNode("view", { class: "row-header" }, [
                  vue.createElementVNode("view", {
                    class: "check-box",
                    onClick: vue.withModifiers(($event) => $setup.onToggle(line), ["stop"])
                  }, [
                    vue.createElementVNode(
                      "view",
                      {
                        class: vue.normalizeClass(["check-inner", line.checked && "on"])
                      },
                      [
                        line.checked ? (vue.openBlock(), vue.createElementBlock("text", {
                          key: 0,
                          class: "check-mark"
                        }, "✓")) : vue.createCommentVNode("v-if", true)
                      ],
                      2
                      /* CLASS */
                    )
                  ], 8, ["onClick"]),
                  vue.createElementVNode("view", { class: "row-main" }, [
                    vue.createElementVNode("view", { class: "name-row" }, [
                      vue.createElementVNode(
                        "text",
                        { class: "mat-code" },
                        vue.toDisplayString(line.materialCode),
                        1
                        /* TEXT */
                      ),
                      $setup.isPartialLine(line) ? (vue.openBlock(), vue.createElementBlock("text", {
                        key: 0,
                        class: "partial-tag"
                      }, "部分已领")) : vue.createCommentVNode("v-if", true)
                    ]),
                    vue.createElementVNode(
                      "text",
                      { class: "mat-name" },
                      vue.toDisplayString(line.materialName || "-"),
                      1
                      /* TEXT */
                    ),
                    vue.createElementVNode(
                      "text",
                      { class: "mat-spec" },
                      "规格 " + vue.toDisplayString(line.specification || "-"),
                      1
                      /* TEXT */
                    ),
                    vue.createElementVNode(
                      "text",
                      { class: "mat-batch" },
                      "批次 " + vue.toDisplayString(line.batchNo || "-"),
                      1
                      /* TEXT */
                    ),
                    vue.createElementVNode(
                      "text",
                      { class: "mat-wh" },
                      "仓库 " + vue.toDisplayString(line.erpStockCode || "-"),
                      1
                      /* TEXT */
                    )
                  ])
                ]),
                vue.createElementVNode("view", {
                  class: "qty-panel",
                  onClick: _cache[0] || (_cache[0] = vue.withModifiers(() => {
                  }, ["stop"]))
                }, [
                  vue.createElementVNode("view", { class: "qty-grid" }, [
                    vue.createElementVNode("view", { class: "qty-cell" }, [
                      vue.createElementVNode("text", { class: "qty-label" }, "计划"),
                      vue.createElementVNode(
                        "text",
                        { class: "qty-value" },
                        vue.toDisplayString($setup.formatQty(line.planQty, line.unitCode)),
                        1
                        /* TEXT */
                      )
                    ]),
                    vue.createElementVNode("view", { class: "qty-cell" }, [
                      vue.createElementVNode("text", { class: "qty-label" }, "已领"),
                      vue.createElementVNode(
                        "text",
                        { class: "qty-value submitted" },
                        vue.toDisplayString($setup.formatQty(line.submittedQty, line.unitCode)),
                        1
                        /* TEXT */
                      )
                    ]),
                    vue.createElementVNode("view", { class: "qty-cell" }, [
                      vue.createElementVNode("text", { class: "qty-label" }, "可领"),
                      vue.createElementVNode(
                        "text",
                        { class: "qty-value remain" },
                        vue.toDisplayString($setup.formatQty(line.remainQty, line.unitCode)),
                        1
                        /* TEXT */
                      )
                    ]),
                    vue.createElementVNode("view", { class: "qty-cell unit-cell" }, [
                      vue.createElementVNode("text", { class: "qty-label" }, "单位"),
                      vue.createElementVNode(
                        "text",
                        { class: "qty-value unit" },
                        vue.toDisplayString(line.unitCode || "PCS"),
                        1
                        /* TEXT */
                      )
                    ])
                  ]),
                  !$setup.isDoneLine(line) ? (vue.openBlock(), vue.createElementBlock("view", {
                    key: 0,
                    class: "qty-edit"
                  }, [
                    vue.createElementVNode("text", { class: "qty-edit-label" }, "本次领取"),
                    vue.createElementVNode("input", {
                      class: "qty-input",
                      type: "digit",
                      value: $setup.getQtyDraft(line),
                      disabled: $setup.updatingLineNo === line.lineNo,
                      placeholder: "0",
                      onInput: ($event) => $setup.onQtyInput(line, $event),
                      onBlur: ($event) => $setup.onQtyBlur(line),
                      onConfirm: ($event) => $setup.onQtyBlur(line)
                    }, null, 40, ["value", "disabled", "onInput", "onBlur", "onConfirm"]),
                    vue.createElementVNode(
                      "text",
                      { class: "qty-edit-unit" },
                      vue.toDisplayString(line.unitCode || "PCS"),
                      1
                      /* TEXT */
                    )
                  ])) : (vue.openBlock(), vue.createElementBlock("view", {
                    key: 1,
                    class: "qty-done-tip"
                  }, "已全部领取"))
                ])
              ], 10, ["onClick"]);
            }),
            128
            /* KEYED_FRAGMENT */
          )),
          $setup.windowed.padBottom ? (vue.openBlock(), vue.createElementBlock(
            "view",
            {
              key: 1,
              style: vue.normalizeStyle({ height: $setup.windowed.padBottom + "px" })
            },
            null,
            4
            /* STYLE */
          )) : vue.createCommentVNode("v-if", true),
          !$setup.lines.length && !$setup.loading ? (vue.openBlock(), vue.createElementBlock("view", {
            key: 2,
            class: "empty"
          }, [
            vue.createElementVNode("text", { class: "empty-icon" }, "📦")
          ])) : $setup.lines.length > $setup.windowed.items.length ? (vue.openBlock(), vue.createElementBlock(
            "view",
            {
              key: 3,
              class: "loading-tip end-tip"
            },
            " 显示 " + vue.toDisplayString($setup.windowed.items.length) + "/" + vue.toDisplayString($setup.lines.length) + " 行 · 滚动查看更多 ",
            1
            /* TEXT */
          )) : vue.createCommentVNode("v-if", true),
          vue.createElementVNode("view", { class: "scroll-bottom-pad" })
        ],
        32
        /* NEED_HYDRATION */
      ),
      vue.createElementVNode("view", { class: "footer" }, [
        vue.createElementVNode("button", {
          class: "submit-btn",
          type: "primary",
          loading: $setup.submitting,
          disabled: !$setup.submitableCount,
          onClick: $setup.onSubmit
        }, " 确认领料" + vue.toDisplayString($setup.submitableCount ? ` (${$setup.submitableCount})` : ""), 9, ["loading", "disabled"])
      ])
    ]);
  }
  const PagesPickingOutsourceIssueScan = /* @__PURE__ */ _export_sfc(_sfc_main$d, [["render", _sfc_render$c], ["__scopeId", "data-v-fb3c2b3c"], ["__file", "D:/AAA/WMS/wms-pda/src/pages/picking/outsource-issue-scan.vue"]]);
  const BILL_TYPE$3 = "PRODUCTION_RETURN";
  const _sfc_main$c = {
    __name: "production-return",
    setup(__props, { expose: __expose }) {
      __expose();
      const scanInputRef = vue.ref(null);
      const billType = vue.ref(BILL_TYPE$3);
      const busy = vue.ref(false);
      const { alive, refocusScanInput } = usePageAlive();
      const {
        loading,
        keyword,
        notices,
        loadListOnShow,
        statusLabel,
        statusClass
      } = useNoticeBillList(billType);
      async function openByBarcode(barcode) {
        if (!alive.value || busy.value) return;
        const raw = (barcode || "").trim();
        if (!raw) return;
        busy.value = true;
        uni.showLoading({ title: "打开中...", mask: true });
        try {
          let billNo = raw;
          try {
            const res = await resolveNoticeBarcode(BILL_TYPE$3, raw);
            if (res == null ? void 0 : res.billNo) billNo = String(res.billNo).trim();
          } catch {
          }
          if (!billNo) {
            uni.showToast({ title: "无法识别退料单号", icon: "none" });
            return;
          }
          keyword.value = billNo;
          openBill({ billNo });
        } finally {
          uni.hideLoading();
          busy.value = false;
          refocusScanInput(scanInputRef, 300);
        }
      }
      function onScan(barcode) {
        openByBarcode(barcode);
      }
      function onSearch(val) {
        openByBarcode(val || keyword.value);
      }
      function openBill(item) {
        if (!(item == null ? void 0 : item.billNo)) return;
        uni.navigateTo({
          url: `/pages/picking/production-return-scan?billNo=${encodeURIComponent(item.billNo)}`
        });
      }
      onLoad(() => uni.setNavigationBarTitle({ title: "生产退料" }));
      onShow(() => loadListOnShow());
      vue.onMounted(() => refocusScanInput(scanInputRef, 500));
      const __returned__ = { BILL_TYPE: BILL_TYPE$3, scanInputRef, billType, busy, alive, refocusScanInput, loading, keyword, notices, loadListOnShow, statusLabel, statusClass, openByBarcode, onScan, onSearch, openBill, ref: vue.ref, onMounted: vue.onMounted, get onLoad() {
        return onLoad;
      }, get onShow() {
        return onShow;
      }, ScanSearchBar, get resolveNoticeBarcode() {
        return resolveNoticeBarcode;
      }, get useNoticeBillList() {
        return useNoticeBillList;
      }, get usePageAlive() {
        return usePageAlive;
      }, get formatMaterialLineCount() {
        return formatMaterialLineCount;
      } };
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  };
  function _sfc_render$b(_ctx, _cache, $props, $setup, $data, $options) {
    return vue.openBlock(), vue.createElementBlock("view", { class: "page" }, [
      vue.createElementVNode("view", { class: "search-top" }, [
        vue.createVNode($setup["ScanSearchBar"], {
          ref: "scanInputRef",
          modelValue: $setup.keyword,
          "onUpdate:modelValue": _cache[0] || (_cache[0] = ($event) => $setup.keyword = $event),
          disabled: $setup.busy,
          placeholder: "扫码或搜索生产退料单号/车间",
          "action-text": "打开",
          onScan: $setup.onScan,
          onSearch: $setup.onSearch
        }, null, 8, ["modelValue", "disabled"])
      ]),
      vue.createElementVNode("scroll-view", {
        class: "list-scroll",
        "scroll-y": "",
        "show-scrollbar": false
      }, [
        (vue.openBlock(true), vue.createElementBlock(
          vue.Fragment,
          null,
          vue.renderList($setup.notices, (item, index) => {
            return vue.openBlock(), vue.createElementBlock("view", {
              key: item.billNo || "row-" + index,
              class: "bill-row",
              onClick: ($event) => $setup.openBill(item)
            }, [
              vue.createElementVNode("view", { class: "row-main" }, [
                vue.createElementVNode(
                  "text",
                  { class: "bill-no" },
                  vue.toDisplayString(item.billNo || "（单号缺失）"),
                  1
                  /* TEXT */
                ),
                vue.createElementVNode(
                  "text",
                  { class: "bill-supplier" },
                  vue.toDisplayString(item.supplierName || item.supplierCode || "-"),
                  1
                  /* TEXT */
                ),
                vue.createElementVNode("text", { class: "bill-meta" }, [
                  $setup.formatMaterialLineCount(item) ? (vue.openBlock(), vue.createElementBlock(
                    "text",
                    {
                      key: 0,
                      class: "bill-lines"
                    },
                    vue.toDisplayString($setup.formatMaterialLineCount(item)),
                    1
                    /* TEXT */
                  )) : vue.createCommentVNode("v-if", true),
                  item.inProgress ? (vue.openBlock(), vue.createElementBlock(
                    "text",
                    { key: 1 },
                    " · 已勾 " + vue.toDisplayString(item.checkedLines || 0),
                    1
                    /* TEXT */
                  )) : vue.createCommentVNode("v-if", true),
                  item.locked && item.lockUserName ? (vue.openBlock(), vue.createElementBlock(
                    "text",
                    {
                      key: 2,
                      class: "bill-lock"
                    },
                    " · " + vue.toDisplayString(item.lockUserName) + "操作中",
                    1
                    /* TEXT */
                  )) : vue.createCommentVNode("v-if", true)
                ])
              ]),
              vue.createElementVNode("view", { class: "row-side" }, [
                vue.createElementVNode(
                  "text",
                  {
                    class: vue.normalizeClass(["status-tag", $setup.statusClass(item)])
                  },
                  vue.toDisplayString($setup.statusLabel(item)),
                  3
                  /* TEXT, CLASS */
                ),
                vue.createElementVNode("text", { class: "arrow" }, "›")
              ])
            ], 8, ["onClick"]);
          }),
          128
          /* KEYED_FRAGMENT */
        )),
        !$setup.notices.length && !$setup.loading ? (vue.openBlock(), vue.createElementBlock("view", {
          key: 0,
          class: "empty"
        }, [
          vue.createElementVNode("text", { class: "empty-icon" }, "↩️"),
          vue.createElementVNode("text", { class: "empty-text" }, "暂无未审核的生产退料单"),
          vue.createElementVNode("text", { class: "empty-hint" }, "可直接扫描退料单二维码进入明细")
        ])) : vue.createCommentVNode("v-if", true),
        $setup.loading && !$setup.notices.length ? (vue.openBlock(), vue.createElementBlock("view", {
          key: 1,
          class: "loading-tip"
        }, "加载中...")) : $setup.notices.length ? (vue.openBlock(), vue.createElementBlock(
          "view",
          {
            key: 2,
            class: "loading-tip end-tip"
          },
          "共 " + vue.toDisplayString($setup.notices.length) + " 条",
          1
          /* TEXT */
        )) : vue.createCommentVNode("v-if", true)
      ])
    ]);
  }
  const PagesPickingProductionReturn = /* @__PURE__ */ _export_sfc(_sfc_main$c, [["render", _sfc_render$b], ["__scopeId", "data-v-7f04f7ee"], ["__file", "D:/AAA/WMS/wms-pda/src/pages/picking/production-return.vue"]]);
  const BILL_TYPE$2 = "PRODUCTION_RETURN";
  const useProductionReturnScan = createNoticeBillScan(BILL_TYPE$2, {
    notOnBill: "该物料不在本生产退料单中",
    linesNotReady: "退料单明细未加载完成，请返回重新进入",
    alreadyFull: "该物料已退满",
    backOnSubmitSuccess: true
  });
  const _sfc_main$b = {
    __name: "production-return-scan",
    setup(__props, { expose: __expose }) {
      __expose();
      const billNo = vue.ref("");
      const scanInputRef = vue.ref(null);
      const warehousePickerRef = vue.ref(null);
      const warehousePayload = vue.ref({ autoAssignWarehouse: true });
      const qtyDrafts = vue.reactive({});
      const updatingLineNo = vue.ref(null);
      const { alive, refocusScanInput } = usePageAlive();
      const {
        loading,
        submitting,
        detail,
        lines,
        checkedCount,
        submitableCount,
        loadDetail,
        handleScan,
        toggleCheck,
        updateQty,
        formatQty: formatQty2,
        submit,
        rowClass
      } = useProductionReturnScan(billNo);
      const partialCount = vue.computed(
        () => lines.value.filter((l) => isPartialLine(l)).length
      );
      const suggestWarehouseCode = vue.computed(() => {
        var _a, _b;
        const pending = lines.value.find((l) => l.checked && (Number(l.pendingSubmitQty) || 0) > 0);
        if (pending == null ? void 0 : pending.erpStockCode) return pending.erpStockCode;
        return ((_a = detail.value) == null ? void 0 : _a.erpWarehouseCode) || ((_b = detail.value) == null ? void 0 : _b.warehouseCode) || "";
      });
      function onWarehouseChange(payload) {
        warehousePayload.value = payload || { autoAssignWarehouse: true };
      }
      function isDoneLine(line) {
        const submitted = Number(line.submittedQty) || 0;
        const plan = Number(line.planQty) || 0;
        return plan > 0 && submitted >= plan;
      }
      function isPartialLine(line) {
        const submitted = Number(line.submittedQty) || 0;
        const plan = Number(line.planQty) || 0;
        return submitted > 0 && submitted < plan;
      }
      function syncQtyDrafts() {
        lines.value.forEach((line) => {
          qtyDrafts[line.lineNo] = formatQtyInput(line.pendingSubmitQty || 0, line.unitCode);
        });
      }
      function getQtyDraft(line) {
        if (qtyDrafts[line.lineNo] == null) {
          qtyDrafts[line.lineNo] = formatQtyInput(line.pendingSubmitQty || 0, line.unitCode);
        }
        return qtyDrafts[line.lineNo];
      }
      function onQtyInput(line, e) {
        qtyDrafts[line.lineNo] = sanitizeDecimalInput(e.detail.value, qtyDecimalScale(line.unitCode));
      }
      async function onQtyBlur(line) {
        const raw = qtyDrafts[line.lineNo];
        const num = raw === "" || raw == null ? 0 : Number(raw);
        if (Number.isNaN(num) || num < 0) {
          qtyDrafts[line.lineNo] = formatQtyInput(line.pendingSubmitQty || 0, line.unitCode);
          return;
        }
        const current = Number(line.pendingSubmitQty) || 0;
        if (num === current) return;
        updatingLineNo.value = line.lineNo;
        const ok = await updateQty(line.lineNo, num);
        updatingLineNo.value = null;
        if (ok) {
          const updated = lines.value.find((l) => l.lineNo === line.lineNo);
          if (updated) qtyDrafts[line.lineNo] = formatQtyInput(updated.pendingSubmitQty || 0, updated.unitCode);
        } else {
          qtyDrafts[line.lineNo] = formatQtyInput(line.pendingSubmitQty || 0, line.unitCode);
        }
      }
      async function onScan(barcode) {
        if (!alive.value) return;
        await handleScan(barcode);
        syncQtyDrafts();
        refocusScanInput(scanInputRef, 300);
      }
      function onToggle(line) {
        toggleCheck(line.lineNo, !line.checked);
      }
      function onRowTap(line) {
        if (!line.checked && !isDoneLine(line)) toggleCheck(line.lineNo, true);
      }
      async function onSubmit() {
        const ok = await submit(() => {
          var _a, _b;
          return ((_b = (_a = warehousePickerRef.value) == null ? void 0 : _a.getPayload) == null ? void 0 : _b.call(_a)) || warehousePayload.value;
        });
        if (ok) syncQtyDrafts();
      }
      onLoad((options) => {
        billNo.value = decodeURIComponent((options == null ? void 0 : options.billNo) || "");
        uni.setNavigationBarTitle({ title: "退料确认" });
      });
      onShow(async () => {
        if (billNo.value) {
          await loadDetail();
          syncQtyDrafts();
        }
        refocusScanInput(scanInputRef, 400);
      });
      const __returned__ = { billNo, scanInputRef, warehousePickerRef, warehousePayload, qtyDrafts, updatingLineNo, alive, refocusScanInput, loading, submitting, detail, lines, checkedCount, submitableCount, loadDetail, handleScan, toggleCheck, updateQty, formatQty: formatQty2, submit, rowClass, partialCount, suggestWarehouseCode, onWarehouseChange, isDoneLine, isPartialLine, syncQtyDrafts, getQtyDraft, onQtyInput, onQtyBlur, onScan, onToggle, onRowTap, onSubmit, ref: vue.ref, computed: vue.computed, reactive: vue.reactive, get onLoad() {
        return onLoad;
      }, get onShow() {
        return onShow;
      }, CompactScanBox, WarehousePicker, get useProductionReturnScan() {
        return useProductionReturnScan;
      }, get usePageAlive() {
        return usePageAlive;
      }, get sanitizeDecimalInput() {
        return sanitizeDecimalInput;
      }, get qtyDecimalScale() {
        return qtyDecimalScale;
      }, get formatQtyInput() {
        return formatQtyInput;
      } };
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  };
  function _sfc_render$a(_ctx, _cache, $props, $setup, $data, $options) {
    return vue.openBlock(), vue.createElementBlock("view", { class: "page" }, [
      vue.createElementVNode("view", { class: "scan-top" }, [
        vue.createVNode($setup["CompactScanBox"], {
          ref: "scanInputRef",
          disabled: $setup.loading || $setup.submitting,
          onScan: $setup.onScan
        }, null, 8, ["disabled"])
      ]),
      $setup.detail ? (vue.openBlock(), vue.createElementBlock("view", {
        key: 0,
        class: "order-bar"
      }, [
        vue.createElementVNode("view", { class: "order-info" }, [
          vue.createElementVNode(
            "text",
            { class: "order-no" },
            vue.toDisplayString($setup.detail.billNo),
            1
            /* TEXT */
          ),
          vue.createElementVNode(
            "text",
            { class: "order-sub" },
            vue.toDisplayString($setup.detail.supplierName || $setup.detail.supplierCode || "-"),
            1
            /* TEXT */
          ),
          $setup.detail.erpBillNo ? (vue.openBlock(), vue.createElementBlock(
            "text",
            {
              key: 0,
              class: "order-erp"
            },
            "生产退料单 " + vue.toDisplayString($setup.detail.erpBillNo),
            1
            /* TEXT */
          )) : vue.createCommentVNode("v-if", true)
        ]),
        vue.createElementVNode("view", { class: "order-stat-wrap" }, [
          vue.createElementVNode(
            "text",
            { class: "order-stat" },
            vue.toDisplayString($setup.checkedCount) + "/" + vue.toDisplayString($setup.lines.length) + " 已勾",
            1
            /* TEXT */
          ),
          $setup.partialCount ? (vue.openBlock(), vue.createElementBlock(
            "text",
            {
              key: 0,
              class: "order-partial"
            },
            "部分已退 " + vue.toDisplayString($setup.partialCount),
            1
            /* TEXT */
          )) : vue.createCommentVNode("v-if", true)
        ])
      ])) : vue.createCommentVNode("v-if", true),
      vue.createElementVNode("scroll-view", {
        class: "list-scroll",
        "scroll-y": "",
        "show-scrollbar": false
      }, [
        (vue.openBlock(true), vue.createElementBlock(
          vue.Fragment,
          null,
          vue.renderList($setup.lines, (line) => {
            return vue.openBlock(), vue.createElementBlock("view", {
              key: line.lineNo,
              class: vue.normalizeClass(["mat-row", $setup.rowClass(line)]),
              onClick: ($event) => $setup.onRowTap(line)
            }, [
              vue.createElementVNode("view", { class: "row-header" }, [
                vue.createElementVNode("view", {
                  class: "check-box",
                  onClick: vue.withModifiers(($event) => $setup.onToggle(line), ["stop"])
                }, [
                  vue.createElementVNode(
                    "view",
                    {
                      class: vue.normalizeClass(["check-inner", line.checked && "on"])
                    },
                    [
                      line.checked ? (vue.openBlock(), vue.createElementBlock("text", {
                        key: 0,
                        class: "check-mark"
                      }, "✓")) : vue.createCommentVNode("v-if", true)
                    ],
                    2
                    /* CLASS */
                  )
                ], 8, ["onClick"]),
                vue.createElementVNode("view", { class: "row-main" }, [
                  vue.createElementVNode("view", { class: "name-row" }, [
                    vue.createElementVNode(
                      "text",
                      { class: "mat-code" },
                      vue.toDisplayString(line.materialCode),
                      1
                      /* TEXT */
                    ),
                    $setup.isPartialLine(line) ? (vue.openBlock(), vue.createElementBlock("text", {
                      key: 0,
                      class: "partial-tag"
                    }, "部分已退")) : vue.createCommentVNode("v-if", true)
                  ]),
                  vue.createElementVNode(
                    "text",
                    { class: "mat-name" },
                    vue.toDisplayString(line.materialName || "-"),
                    1
                    /* TEXT */
                  ),
                  vue.createElementVNode(
                    "text",
                    { class: "mat-spec" },
                    "规格 " + vue.toDisplayString(line.specification || "-"),
                    1
                    /* TEXT */
                  ),
                  vue.createElementVNode(
                    "text",
                    { class: "mat-batch" },
                    "批次 " + vue.toDisplayString(line.batchNo || "-"),
                    1
                    /* TEXT */
                  ),
                  vue.createElementVNode(
                    "text",
                    { class: "mat-wh" },
                    "仓库 " + vue.toDisplayString(line.erpStockCode || "-"),
                    1
                    /* TEXT */
                  )
                ])
              ]),
              vue.createElementVNode("view", {
                class: "qty-panel",
                onClick: _cache[0] || (_cache[0] = vue.withModifiers(() => {
                }, ["stop"]))
              }, [
                vue.createElementVNode("view", { class: "qty-grid" }, [
                  vue.createElementVNode("view", { class: "qty-cell" }, [
                    vue.createElementVNode("text", { class: "qty-label" }, "计划"),
                    vue.createElementVNode(
                      "text",
                      { class: "qty-value" },
                      vue.toDisplayString($setup.formatQty(line.planQty, line.unitCode)),
                      1
                      /* TEXT */
                    )
                  ]),
                  vue.createElementVNode("view", { class: "qty-cell" }, [
                    vue.createElementVNode("text", { class: "qty-label" }, "已退"),
                    vue.createElementVNode(
                      "text",
                      { class: "qty-value submitted" },
                      vue.toDisplayString($setup.formatQty(line.submittedQty, line.unitCode)),
                      1
                      /* TEXT */
                    )
                  ]),
                  vue.createElementVNode("view", { class: "qty-cell" }, [
                    vue.createElementVNode("text", { class: "qty-label" }, "可退"),
                    vue.createElementVNode(
                      "text",
                      { class: "qty-value remain" },
                      vue.toDisplayString($setup.formatQty(line.remainQty, line.unitCode)),
                      1
                      /* TEXT */
                    )
                  ]),
                  vue.createElementVNode("view", { class: "qty-cell unit-cell" }, [
                    vue.createElementVNode("text", { class: "qty-label" }, "单位"),
                    vue.createElementVNode(
                      "text",
                      { class: "qty-value unit" },
                      vue.toDisplayString(line.unitCode || "PCS"),
                      1
                      /* TEXT */
                    )
                  ])
                ]),
                !$setup.isDoneLine(line) ? (vue.openBlock(), vue.createElementBlock("view", {
                  key: 0,
                  class: "qty-edit"
                }, [
                  vue.createElementVNode("text", { class: "qty-edit-label" }, "本次退料"),
                  vue.createElementVNode("input", {
                    class: "qty-input",
                    type: "digit",
                    value: $setup.getQtyDraft(line),
                    disabled: $setup.updatingLineNo === line.lineNo,
                    placeholder: "0",
                    onInput: ($event) => $setup.onQtyInput(line, $event),
                    onBlur: ($event) => $setup.onQtyBlur(line),
                    onConfirm: ($event) => $setup.onQtyBlur(line)
                  }, null, 40, ["value", "disabled", "onInput", "onBlur", "onConfirm"]),
                  vue.createElementVNode(
                    "text",
                    { class: "qty-edit-unit" },
                    vue.toDisplayString(line.unitCode || "PCS"),
                    1
                    /* TEXT */
                  )
                ])) : (vue.openBlock(), vue.createElementBlock("view", {
                  key: 1,
                  class: "qty-done-tip"
                }, "已全部退完"))
              ])
            ], 10, ["onClick"]);
          }),
          128
          /* KEYED_FRAGMENT */
        )),
        !$setup.lines.length && !$setup.loading ? (vue.openBlock(), vue.createElementBlock("view", {
          key: 0,
          class: "empty"
        }, [
          vue.createElementVNode("text", { class: "empty-icon" }, "📦")
        ])) : vue.createCommentVNode("v-if", true),
        $setup.detail ? (vue.openBlock(), vue.createElementBlock("view", {
          key: 1,
          class: "wh-section"
        }, [
          vue.createVNode($setup["WarehousePicker"], {
            ref: "warehousePickerRef",
            "suggest-code": $setup.suggestWarehouseCode,
            onChange: $setup.onWarehouseChange
          }, null, 8, ["suggest-code"])
        ])) : vue.createCommentVNode("v-if", true),
        vue.createElementVNode("view", { class: "scroll-bottom-pad" })
      ]),
      vue.createElementVNode("view", { class: "footer" }, [
        vue.createElementVNode("button", {
          class: "submit-btn",
          type: "primary",
          loading: $setup.submitting,
          disabled: !$setup.submitableCount,
          onClick: $setup.onSubmit
        }, " 确认退料" + vue.toDisplayString($setup.submitableCount ? ` (${$setup.submitableCount})` : ""), 9, ["loading", "disabled"])
      ])
    ]);
  }
  const PagesPickingProductionReturnScan = /* @__PURE__ */ _export_sfc(_sfc_main$b, [["render", _sfc_render$a], ["__scopeId", "data-v-2e25f340"], ["__file", "D:/AAA/WMS/wms-pda/src/pages/picking/production-return-scan.vue"]]);
  const BILL_TYPE$1 = "OUTSOURCE_RETURN";
  const _sfc_main$a = {
    __name: "outsource-return",
    setup(__props, { expose: __expose }) {
      __expose();
      const scanInputRef = vue.ref(null);
      const billType = vue.ref(BILL_TYPE$1);
      const busy = vue.ref(false);
      const { alive, refocusScanInput } = usePageAlive();
      const {
        loading,
        keyword,
        notices,
        loadListOnShow,
        statusLabel,
        statusClass
      } = useNoticeBillList(billType);
      async function openByBarcode(barcode) {
        if (!alive.value || busy.value) return;
        const raw = (barcode || "").trim();
        if (!raw) return;
        busy.value = true;
        uni.showLoading({ title: "打开中...", mask: true });
        try {
          let billNo = raw;
          try {
            const res = await resolveNoticeBarcode(BILL_TYPE$1, raw);
            if (res == null ? void 0 : res.billNo) billNo = String(res.billNo).trim();
          } catch {
          }
          if (!billNo) {
            uni.showToast({ title: "无法识别退料单号", icon: "none" });
            return;
          }
          keyword.value = billNo;
          openBill({ billNo });
        } finally {
          uni.hideLoading();
          busy.value = false;
          refocusScanInput(scanInputRef, 300);
        }
      }
      function onScan(barcode) {
        openByBarcode(barcode);
      }
      function onSearch(val) {
        openByBarcode(val || keyword.value);
      }
      function openBill(item) {
        if (!(item == null ? void 0 : item.billNo)) return;
        uni.navigateTo({
          url: `/pages/picking/outsource-return-scan?billNo=${encodeURIComponent(item.billNo)}`
        });
      }
      onLoad(() => uni.setNavigationBarTitle({ title: "委外退料" }));
      onShow(() => loadListOnShow());
      vue.onMounted(() => refocusScanInput(scanInputRef, 500));
      const __returned__ = { BILL_TYPE: BILL_TYPE$1, scanInputRef, billType, busy, alive, refocusScanInput, loading, keyword, notices, loadListOnShow, statusLabel, statusClass, openByBarcode, onScan, onSearch, openBill, ref: vue.ref, onMounted: vue.onMounted, get onLoad() {
        return onLoad;
      }, get onShow() {
        return onShow;
      }, ScanSearchBar, get resolveNoticeBarcode() {
        return resolveNoticeBarcode;
      }, get useNoticeBillList() {
        return useNoticeBillList;
      }, get usePageAlive() {
        return usePageAlive;
      }, get formatMaterialLineCount() {
        return formatMaterialLineCount;
      } };
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  };
  function _sfc_render$9(_ctx, _cache, $props, $setup, $data, $options) {
    return vue.openBlock(), vue.createElementBlock("view", { class: "page" }, [
      vue.createElementVNode("view", { class: "search-top" }, [
        vue.createVNode($setup["ScanSearchBar"], {
          ref: "scanInputRef",
          modelValue: $setup.keyword,
          "onUpdate:modelValue": _cache[0] || (_cache[0] = ($event) => $setup.keyword = $event),
          disabled: $setup.busy,
          placeholder: "扫码或搜索委外退料单号/供应商",
          "action-text": "打开",
          onScan: $setup.onScan,
          onSearch: $setup.onSearch
        }, null, 8, ["modelValue", "disabled"])
      ]),
      vue.createElementVNode("scroll-view", {
        class: "list-scroll",
        "scroll-y": "",
        "show-scrollbar": false
      }, [
        (vue.openBlock(true), vue.createElementBlock(
          vue.Fragment,
          null,
          vue.renderList($setup.notices, (item, index) => {
            return vue.openBlock(), vue.createElementBlock("view", {
              key: item.billNo || "row-" + index,
              class: "bill-row",
              onClick: ($event) => $setup.openBill(item)
            }, [
              vue.createElementVNode("view", { class: "row-main" }, [
                vue.createElementVNode(
                  "text",
                  { class: "bill-no" },
                  vue.toDisplayString(item.billNo || "（单号缺失）"),
                  1
                  /* TEXT */
                ),
                vue.createElementVNode(
                  "text",
                  { class: "bill-supplier" },
                  vue.toDisplayString(item.supplierName || item.supplierCode || "-"),
                  1
                  /* TEXT */
                ),
                vue.createElementVNode("text", { class: "bill-meta" }, [
                  $setup.formatMaterialLineCount(item) ? (vue.openBlock(), vue.createElementBlock(
                    "text",
                    {
                      key: 0,
                      class: "bill-lines"
                    },
                    vue.toDisplayString($setup.formatMaterialLineCount(item)),
                    1
                    /* TEXT */
                  )) : vue.createCommentVNode("v-if", true),
                  item.inProgress ? (vue.openBlock(), vue.createElementBlock(
                    "text",
                    { key: 1 },
                    " · 已勾 " + vue.toDisplayString(item.checkedLines || 0),
                    1
                    /* TEXT */
                  )) : vue.createCommentVNode("v-if", true),
                  item.locked && item.lockUserName ? (vue.openBlock(), vue.createElementBlock(
                    "text",
                    {
                      key: 2,
                      class: "bill-lock"
                    },
                    " · " + vue.toDisplayString(item.lockUserName) + "操作中",
                    1
                    /* TEXT */
                  )) : vue.createCommentVNode("v-if", true)
                ])
              ]),
              vue.createElementVNode("view", { class: "row-side" }, [
                vue.createElementVNode(
                  "text",
                  {
                    class: vue.normalizeClass(["status-tag", $setup.statusClass(item)])
                  },
                  vue.toDisplayString($setup.statusLabel(item)),
                  3
                  /* TEXT, CLASS */
                ),
                vue.createElementVNode("text", { class: "arrow" }, "›")
              ])
            ], 8, ["onClick"]);
          }),
          128
          /* KEYED_FRAGMENT */
        )),
        !$setup.notices.length && !$setup.loading ? (vue.openBlock(), vue.createElementBlock("view", {
          key: 0,
          class: "empty"
        }, [
          vue.createElementVNode("text", { class: "empty-icon" }, "↩️"),
          vue.createElementVNode("text", { class: "empty-text" }, "暂无未审核的委外退料单"),
          vue.createElementVNode("text", { class: "empty-hint" }, "可直接扫描退料单二维码进入明细")
        ])) : vue.createCommentVNode("v-if", true),
        $setup.loading && !$setup.notices.length ? (vue.openBlock(), vue.createElementBlock("view", {
          key: 1,
          class: "loading-tip"
        }, "加载中...")) : $setup.notices.length ? (vue.openBlock(), vue.createElementBlock(
          "view",
          {
            key: 2,
            class: "loading-tip end-tip"
          },
          "共 " + vue.toDisplayString($setup.notices.length) + " 条",
          1
          /* TEXT */
        )) : vue.createCommentVNode("v-if", true)
      ])
    ]);
  }
  const PagesPickingOutsourceReturn = /* @__PURE__ */ _export_sfc(_sfc_main$a, [["render", _sfc_render$9], ["__scopeId", "data-v-5170056c"], ["__file", "D:/AAA/WMS/wms-pda/src/pages/picking/outsource-return.vue"]]);
  const BILL_TYPE = "OUTSOURCE_RETURN";
  const useOutsourceReturnScan = createNoticeBillScan(BILL_TYPE, {
    notOnBill: "该物料不在本委外退料单中",
    linesNotReady: "退料单明细未加载完成，请返回重新进入",
    alreadyFull: "该物料已退满",
    backOnSubmitSuccess: true
  });
  const _sfc_main$9 = {
    __name: "outsource-return-scan",
    setup(__props, { expose: __expose }) {
      __expose();
      const billNo = vue.ref("");
      const scanInputRef = vue.ref(null);
      const warehousePickerRef = vue.ref(null);
      const warehousePayload = vue.ref({ autoAssignWarehouse: true });
      const qtyDrafts = vue.reactive({});
      const updatingLineNo = vue.ref(null);
      const { alive, refocusScanInput } = usePageAlive();
      const {
        loading,
        submitting,
        detail,
        lines,
        checkedCount,
        submitableCount,
        loadDetail,
        handleScan,
        toggleCheck,
        updateQty,
        formatQty: formatQty2,
        submit,
        rowClass
      } = useOutsourceReturnScan(billNo);
      const partialCount = vue.computed(
        () => lines.value.filter((l) => isPartialLine(l)).length
      );
      const suggestWarehouseCode = vue.computed(() => {
        var _a, _b;
        const pending = lines.value.find((l) => l.checked && (Number(l.pendingSubmitQty) || 0) > 0);
        if (pending == null ? void 0 : pending.erpStockCode) return pending.erpStockCode;
        return ((_a = detail.value) == null ? void 0 : _a.erpWarehouseCode) || ((_b = detail.value) == null ? void 0 : _b.warehouseCode) || "";
      });
      function onWarehouseChange(payload) {
        warehousePayload.value = payload || { autoAssignWarehouse: true };
      }
      function isDoneLine(line) {
        const submitted = Number(line.submittedQty) || 0;
        const plan = Number(line.planQty) || 0;
        return plan > 0 && submitted >= plan;
      }
      function isPartialLine(line) {
        const submitted = Number(line.submittedQty) || 0;
        const plan = Number(line.planQty) || 0;
        return submitted > 0 && submitted < plan;
      }
      function syncQtyDrafts() {
        lines.value.forEach((line) => {
          qtyDrafts[line.lineNo] = formatQtyInput(line.pendingSubmitQty || 0, line.unitCode);
        });
      }
      function getQtyDraft(line) {
        if (qtyDrafts[line.lineNo] == null) {
          qtyDrafts[line.lineNo] = formatQtyInput(line.pendingSubmitQty || 0, line.unitCode);
        }
        return qtyDrafts[line.lineNo];
      }
      function onQtyInput(line, e) {
        qtyDrafts[line.lineNo] = sanitizeDecimalInput(e.detail.value, qtyDecimalScale(line.unitCode));
      }
      async function onQtyBlur(line) {
        const raw = qtyDrafts[line.lineNo];
        const num = raw === "" || raw == null ? 0 : Number(raw);
        if (Number.isNaN(num) || num < 0) {
          qtyDrafts[line.lineNo] = formatQtyInput(line.pendingSubmitQty || 0, line.unitCode);
          return;
        }
        const current = Number(line.pendingSubmitQty) || 0;
        if (num === current) return;
        updatingLineNo.value = line.lineNo;
        const ok = await updateQty(line.lineNo, num);
        updatingLineNo.value = null;
        if (ok) {
          const updated = lines.value.find((l) => l.lineNo === line.lineNo);
          if (updated) qtyDrafts[line.lineNo] = formatQtyInput(updated.pendingSubmitQty || 0, updated.unitCode);
        } else {
          qtyDrafts[line.lineNo] = formatQtyInput(line.pendingSubmitQty || 0, line.unitCode);
        }
      }
      async function onScan(barcode) {
        if (!alive.value) return;
        await handleScan(barcode);
        syncQtyDrafts();
        refocusScanInput(scanInputRef, 300);
      }
      function onToggle(line) {
        toggleCheck(line.lineNo, !line.checked);
      }
      function onRowTap(line) {
        if (!line.checked && !isDoneLine(line)) toggleCheck(line.lineNo, true);
      }
      async function onSubmit() {
        const ok = await submit(() => {
          var _a, _b;
          return ((_b = (_a = warehousePickerRef.value) == null ? void 0 : _a.getPayload) == null ? void 0 : _b.call(_a)) || warehousePayload.value;
        });
        if (ok) syncQtyDrafts();
      }
      onLoad((options) => {
        billNo.value = decodeURIComponent((options == null ? void 0 : options.billNo) || "");
        uni.setNavigationBarTitle({ title: "退料确认" });
      });
      onShow(async () => {
        if (billNo.value) {
          await loadDetail();
          syncQtyDrafts();
        }
        refocusScanInput(scanInputRef, 400);
      });
      const __returned__ = { billNo, scanInputRef, warehousePickerRef, warehousePayload, qtyDrafts, updatingLineNo, alive, refocusScanInput, loading, submitting, detail, lines, checkedCount, submitableCount, loadDetail, handleScan, toggleCheck, updateQty, formatQty: formatQty2, submit, rowClass, partialCount, suggestWarehouseCode, onWarehouseChange, isDoneLine, isPartialLine, syncQtyDrafts, getQtyDraft, onQtyInput, onQtyBlur, onScan, onToggle, onRowTap, onSubmit, ref: vue.ref, computed: vue.computed, reactive: vue.reactive, get onLoad() {
        return onLoad;
      }, get onShow() {
        return onShow;
      }, CompactScanBox, WarehousePicker, get useOutsourceReturnScan() {
        return useOutsourceReturnScan;
      }, get usePageAlive() {
        return usePageAlive;
      }, get sanitizeDecimalInput() {
        return sanitizeDecimalInput;
      }, get qtyDecimalScale() {
        return qtyDecimalScale;
      }, get formatQtyInput() {
        return formatQtyInput;
      } };
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  };
  function _sfc_render$8(_ctx, _cache, $props, $setup, $data, $options) {
    return vue.openBlock(), vue.createElementBlock("view", { class: "page" }, [
      vue.createElementVNode("view", { class: "scan-top" }, [
        vue.createVNode($setup["CompactScanBox"], {
          ref: "scanInputRef",
          disabled: $setup.loading || $setup.submitting,
          onScan: $setup.onScan
        }, null, 8, ["disabled"])
      ]),
      $setup.detail ? (vue.openBlock(), vue.createElementBlock("view", {
        key: 0,
        class: "order-bar"
      }, [
        vue.createElementVNode("view", { class: "order-info" }, [
          vue.createElementVNode(
            "text",
            { class: "order-no" },
            vue.toDisplayString($setup.detail.billNo),
            1
            /* TEXT */
          ),
          vue.createElementVNode(
            "text",
            { class: "order-sub" },
            vue.toDisplayString($setup.detail.supplierName || $setup.detail.supplierCode || "-"),
            1
            /* TEXT */
          ),
          $setup.detail.erpBillNo ? (vue.openBlock(), vue.createElementBlock(
            "text",
            {
              key: 0,
              class: "order-erp"
            },
            "委外退料单 " + vue.toDisplayString($setup.detail.erpBillNo),
            1
            /* TEXT */
          )) : vue.createCommentVNode("v-if", true)
        ]),
        vue.createElementVNode("view", { class: "order-stat-wrap" }, [
          vue.createElementVNode(
            "text",
            { class: "order-stat" },
            vue.toDisplayString($setup.checkedCount) + "/" + vue.toDisplayString($setup.lines.length) + " 已勾",
            1
            /* TEXT */
          ),
          $setup.partialCount ? (vue.openBlock(), vue.createElementBlock(
            "text",
            {
              key: 0,
              class: "order-partial"
            },
            "部分已退 " + vue.toDisplayString($setup.partialCount),
            1
            /* TEXT */
          )) : vue.createCommentVNode("v-if", true)
        ])
      ])) : vue.createCommentVNode("v-if", true),
      vue.createElementVNode("scroll-view", {
        class: "list-scroll",
        "scroll-y": "",
        "show-scrollbar": false
      }, [
        (vue.openBlock(true), vue.createElementBlock(
          vue.Fragment,
          null,
          vue.renderList($setup.lines, (line) => {
            return vue.openBlock(), vue.createElementBlock("view", {
              key: line.lineNo,
              class: vue.normalizeClass(["mat-row", $setup.rowClass(line)]),
              onClick: ($event) => $setup.onRowTap(line)
            }, [
              vue.createElementVNode("view", { class: "row-header" }, [
                vue.createElementVNode("view", {
                  class: "check-box",
                  onClick: vue.withModifiers(($event) => $setup.onToggle(line), ["stop"])
                }, [
                  vue.createElementVNode(
                    "view",
                    {
                      class: vue.normalizeClass(["check-inner", line.checked && "on"])
                    },
                    [
                      line.checked ? (vue.openBlock(), vue.createElementBlock("text", {
                        key: 0,
                        class: "check-mark"
                      }, "✓")) : vue.createCommentVNode("v-if", true)
                    ],
                    2
                    /* CLASS */
                  )
                ], 8, ["onClick"]),
                vue.createElementVNode("view", { class: "row-main" }, [
                  vue.createElementVNode("view", { class: "name-row" }, [
                    vue.createElementVNode(
                      "text",
                      { class: "mat-code" },
                      vue.toDisplayString(line.materialCode),
                      1
                      /* TEXT */
                    ),
                    $setup.isPartialLine(line) ? (vue.openBlock(), vue.createElementBlock("text", {
                      key: 0,
                      class: "partial-tag"
                    }, "部分已退")) : vue.createCommentVNode("v-if", true)
                  ]),
                  vue.createElementVNode(
                    "text",
                    { class: "mat-name" },
                    vue.toDisplayString(line.materialName || "-"),
                    1
                    /* TEXT */
                  ),
                  vue.createElementVNode(
                    "text",
                    { class: "mat-spec" },
                    "规格 " + vue.toDisplayString(line.specification || "-"),
                    1
                    /* TEXT */
                  ),
                  vue.createElementVNode(
                    "text",
                    { class: "mat-batch" },
                    "批次 " + vue.toDisplayString(line.batchNo || "-"),
                    1
                    /* TEXT */
                  ),
                  vue.createElementVNode(
                    "text",
                    { class: "mat-wh" },
                    "仓库 " + vue.toDisplayString(line.erpStockCode || "-"),
                    1
                    /* TEXT */
                  )
                ])
              ]),
              vue.createElementVNode("view", {
                class: "qty-panel",
                onClick: _cache[0] || (_cache[0] = vue.withModifiers(() => {
                }, ["stop"]))
              }, [
                vue.createElementVNode("view", { class: "qty-grid" }, [
                  vue.createElementVNode("view", { class: "qty-cell" }, [
                    vue.createElementVNode("text", { class: "qty-label" }, "计划"),
                    vue.createElementVNode(
                      "text",
                      { class: "qty-value" },
                      vue.toDisplayString($setup.formatQty(line.planQty, line.unitCode)),
                      1
                      /* TEXT */
                    )
                  ]),
                  vue.createElementVNode("view", { class: "qty-cell" }, [
                    vue.createElementVNode("text", { class: "qty-label" }, "已退"),
                    vue.createElementVNode(
                      "text",
                      { class: "qty-value submitted" },
                      vue.toDisplayString($setup.formatQty(line.submittedQty, line.unitCode)),
                      1
                      /* TEXT */
                    )
                  ]),
                  vue.createElementVNode("view", { class: "qty-cell" }, [
                    vue.createElementVNode("text", { class: "qty-label" }, "可退"),
                    vue.createElementVNode(
                      "text",
                      { class: "qty-value remain" },
                      vue.toDisplayString($setup.formatQty(line.remainQty, line.unitCode)),
                      1
                      /* TEXT */
                    )
                  ]),
                  vue.createElementVNode("view", { class: "qty-cell unit-cell" }, [
                    vue.createElementVNode("text", { class: "qty-label" }, "单位"),
                    vue.createElementVNode(
                      "text",
                      { class: "qty-value unit" },
                      vue.toDisplayString(line.unitCode || "PCS"),
                      1
                      /* TEXT */
                    )
                  ])
                ]),
                !$setup.isDoneLine(line) ? (vue.openBlock(), vue.createElementBlock("view", {
                  key: 0,
                  class: "qty-edit"
                }, [
                  vue.createElementVNode("text", { class: "qty-edit-label" }, "本次退料"),
                  vue.createElementVNode("input", {
                    class: "qty-input",
                    type: "digit",
                    value: $setup.getQtyDraft(line),
                    disabled: $setup.updatingLineNo === line.lineNo,
                    placeholder: "0",
                    onInput: ($event) => $setup.onQtyInput(line, $event),
                    onBlur: ($event) => $setup.onQtyBlur(line),
                    onConfirm: ($event) => $setup.onQtyBlur(line)
                  }, null, 40, ["value", "disabled", "onInput", "onBlur", "onConfirm"]),
                  vue.createElementVNode(
                    "text",
                    { class: "qty-edit-unit" },
                    vue.toDisplayString(line.unitCode || "PCS"),
                    1
                    /* TEXT */
                  )
                ])) : (vue.openBlock(), vue.createElementBlock("view", {
                  key: 1,
                  class: "qty-done-tip"
                }, "已全部退完"))
              ])
            ], 10, ["onClick"]);
          }),
          128
          /* KEYED_FRAGMENT */
        )),
        !$setup.lines.length && !$setup.loading ? (vue.openBlock(), vue.createElementBlock("view", {
          key: 0,
          class: "empty"
        }, [
          vue.createElementVNode("text", { class: "empty-icon" }, "📦")
        ])) : vue.createCommentVNode("v-if", true),
        $setup.detail ? (vue.openBlock(), vue.createElementBlock("view", {
          key: 1,
          class: "wh-section"
        }, [
          vue.createVNode($setup["WarehousePicker"], {
            ref: "warehousePickerRef",
            "suggest-code": $setup.suggestWarehouseCode,
            onChange: $setup.onWarehouseChange
          }, null, 8, ["suggest-code"])
        ])) : vue.createCommentVNode("v-if", true),
        vue.createElementVNode("view", { class: "scroll-bottom-pad" })
      ]),
      vue.createElementVNode("view", { class: "footer" }, [
        vue.createElementVNode("button", {
          class: "submit-btn",
          type: "primary",
          loading: $setup.submitting,
          disabled: !$setup.submitableCount,
          onClick: $setup.onSubmit
        }, " 确认退料" + vue.toDisplayString($setup.submitableCount ? ` (${$setup.submitableCount})` : ""), 9, ["loading", "disabled"])
      ])
    ]);
  }
  const PagesPickingOutsourceReturnScan = /* @__PURE__ */ _export_sfc(_sfc_main$9, [["render", _sfc_render$8], ["__scopeId", "data-v-a47099cf"], ["__file", "D:/AAA/WMS/wms-pda/src/pages/picking/outsource-return-scan.vue"]]);
  function confirmMaterialPickup(issueNo, receiverName) {
    return request({
      url: "/mobile/picking/pickup/confirm",
      method: "POST",
      data: { issueNo, receiverName }
    });
  }
  function getPickIssueDetail(issueNo) {
    return request({ url: `/mobile/picking/issues/${issueNo}` });
  }
  function scanPickIssue(issueNo, data) {
    return request({
      url: `/mobile/picking/issues/${issueNo}/scan`,
      method: "POST",
      data
    });
  }
  function submitWorkshopReturn(data) {
    return request({
      url: "/mobile/picking/workshop-return",
      method: "POST",
      data
    });
  }
  const _sfc_main$8 = {
    __name: "issue-pick",
    setup(__props, { expose: __expose }) {
      __expose();
      const issueScanCode = vue.ref("");
      const issue = vue.ref(null);
      const lines = vue.ref([]);
      const currentLocation = vue.ref("");
      const scanStep = vue.ref("location");
      const scanLog = vue.ref([]);
      const scanInputRef = vue.ref(null);
      const stepLabel = vue.computed(() => scanStep.value === "location" ? "扫描库位" : "扫描物料条码");
      const stepPlaceholder = vue.computed(
        () => scanStep.value === "location" ? "对准库位条码扫描" : "对准物料条码扫描"
      );
      const statusText = vue.computed(() => {
        var _a;
        const s = (_a = issue.value) == null ? void 0 : _a.status;
        if (s === "PICKING") return "拣货中";
        if (s === "PICKED_UP") return "已领料";
        return s || "";
      });
      function parseIssueNo(code) {
        const c = (code || "").trim();
        if (c.startsWith("PI:")) return c.slice(3);
        return c;
      }
      function isLocationCode(code) {
        return /^WH\d/i.test((code || "").trim());
      }
      function lineProgress(line) {
        const total = Number(line.pickQty) || 1;
        const done = Number(line.pickedQty) || 0;
        return Math.min(100, Math.round(done / total * 100));
      }
      function addLog(ok, msg) {
        const now = /* @__PURE__ */ new Date();
        const time = `${String(now.getHours()).padStart(2, "0")}:${String(now.getMinutes()).padStart(2, "0")}:${String(now.getSeconds()).padStart(2, "0")}`;
        scanLog.value.unshift({ ok, msg, time });
        if (scanLog.value.length > 30) scanLog.value.pop();
      }
      async function onIssueScan(code) {
        try {
          const issueNo = parseIssueNo(code);
          const data = await getPickIssueDetail(issueNo);
          issue.value = data.issue;
          lines.value = data.lines || [];
          currentLocation.value = "";
          scanStep.value = "location";
          if (issue.value.status !== "PICKING") {
            uni.showToast({ title: "该单不可拣货", icon: "none" });
          }
        } catch (e) {
          uni.showToast({ title: e.message || "加载失败", icon: "none" });
        }
      }
      async function onStepScan(code) {
        if (!issue.value) return;
        const raw = (code || "").trim();
        if (!raw) return;
        if (scanStep.value === "location") {
          if (!isLocationCode(raw)) {
            addLog(false, "请扫描库位条码（WH开头）");
            uni.showToast({ title: "请扫描库位", icon: "none" });
            return;
          }
          currentLocation.value = raw;
          scanStep.value = "material";
          addLog(true, `库位 ${raw}`);
          uni.showToast({ title: "请扫描物料", icon: "success", duration: 800 });
          return;
        }
        await doMaterialPick(raw);
      }
      async function doMaterialPick(barcode) {
        if (!currentLocation.value) {
          scanStep.value = "location";
          uni.showToast({ title: "请先扫描库位", icon: "none" });
          return;
        }
        try {
          const recognized = await recognizeBarcode(barcode, issue.value.warehouseCode);
          const materialCode = recognized.materialCode;
          if (!materialCode) {
            addLog(false, "无法识别物料");
            uni.showToast({ title: "无法识别物料", icon: "none" });
            return;
          }
          const result = await scanPickIssue(issue.value.issueNo, {
            materialCode,
            locationCode: currentLocation.value,
            batchNo: recognized.batchNo,
            quantity: 1,
            barcodeContent: barcode
          });
          addLog(true, `${result.materialName || materialCode} +1 @${currentLocation.value}`);
          uni.showToast({ title: "拣货成功", icon: "success", duration: 800 });
          const data = await getPickIssueDetail(issue.value.issueNo);
          lines.value = data.lines || [];
          if (result.allComplete) {
            uni.showModal({
              title: "拣货完成",
              content: "全部明细已拣完，可前往领料确认",
              showCancel: false
            });
          }
          scanStep.value = "material";
        } catch (e) {
          addLog(false, e.message || "拣货失败");
          uni.showToast({ title: e.message || "拣货失败", icon: "none" });
        }
      }
      onShow(() => {
        setTimeout(() => {
          var _a, _b;
          return (_b = (_a = scanInputRef.value) == null ? void 0 : _a.focusInput) == null ? void 0 : _b.call(_a);
        }, 500);
      });
      const __returned__ = { issueScanCode, issue, lines, currentLocation, scanStep, scanLog, scanInputRef, stepLabel, stepPlaceholder, statusText, parseIssueNo, isLocationCode, lineProgress, addLog, onIssueScan, onStepScan, doMaterialPick, ref: vue.ref, computed: vue.computed, get onShow() {
        return onShow;
      }, ScanInput, get getPickIssueDetail() {
        return getPickIssueDetail;
      }, get scanPickIssue() {
        return scanPickIssue;
      }, get recognizeBarcode() {
        return recognizeBarcode;
      } };
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  };
  function _sfc_render$7(_ctx, _cache, $props, $setup, $data, $options) {
    return vue.openBlock(), vue.createElementBlock("view", { class: "page" }, [
      !$setup.issue ? (vue.openBlock(), vue.createBlock($setup["ScanInput"], {
        key: 0,
        modelValue: $setup.issueScanCode,
        "onUpdate:modelValue": _cache[0] || (_cache[0] = ($event) => $setup.issueScanCode = $event),
        placeholder: "扫描拣配发料单二维码",
        onScan: $setup.onIssueScan
      }, null, 8, ["modelValue"])) : vue.createCommentVNode("v-if", true),
      $setup.issue ? (vue.openBlock(), vue.createElementBlock("view", {
        key: 1,
        class: "header card"
      }, [
        vue.createElementVNode(
          "text",
          { class: "title" },
          "发料单 " + vue.toDisplayString($setup.issue.issueNo),
          1
          /* TEXT */
        ),
        vue.createElementVNode(
          "text",
          null,
          "仓库：" + vue.toDisplayString($setup.issue.warehouseCode) + " · " + vue.toDisplayString($setup.statusText),
          1
          /* TEXT */
        ),
        vue.createElementVNode(
          "text",
          null,
          "交接区：" + vue.toDisplayString($setup.issue.handoverArea || "-"),
          1
          /* TEXT */
        )
      ])) : vue.createCommentVNode("v-if", true),
      $setup.issue ? (vue.openBlock(), vue.createElementBlock("view", {
        key: 2,
        class: "step card"
      }, [
        vue.createElementVNode(
          "text",
          { class: "step-label" },
          "当前步骤：" + vue.toDisplayString($setup.stepLabel),
          1
          /* TEXT */
        ),
        vue.createVNode($setup["ScanInput"], {
          ref: "scanInputRef",
          placeholder: $setup.stepPlaceholder,
          onScan: $setup.onStepScan
        }, null, 8, ["placeholder"]),
        $setup.currentLocation ? (vue.openBlock(), vue.createElementBlock(
          "text",
          {
            key: 0,
            class: "loc-hint"
          },
          "已选库位：" + vue.toDisplayString($setup.currentLocation),
          1
          /* TEXT */
        )) : vue.createCommentVNode("v-if", true)
      ])) : vue.createCommentVNode("v-if", true),
      $setup.lines.length ? (vue.openBlock(), vue.createElementBlock("text", {
        key: 3,
        class: "section-title"
      }, "拣货进度")) : vue.createCommentVNode("v-if", true),
      (vue.openBlock(true), vue.createElementBlock(
        vue.Fragment,
        null,
        vue.renderList($setup.lines, (line) => {
          return vue.openBlock(), vue.createElementBlock("view", {
            key: line.lineNo,
            class: "line-card"
          }, [
            vue.createElementVNode(
              "text",
              { class: "name" },
              vue.toDisplayString(line.materialCode),
              1
              /* TEXT */
            ),
            vue.createElementVNode(
              "text",
              null,
              "应拣 " + vue.toDisplayString(line.pickQty) + " / 已拣 " + vue.toDisplayString(line.pickedQty || 0),
              1
              /* TEXT */
            ),
            vue.createElementVNode(
              "text",
              { class: "loc" },
              "推荐库位 " + vue.toDisplayString(line.sourceLocation || "-"),
              1
              /* TEXT */
            ),
            vue.createElementVNode("view", { class: "progress-bar" }, [
              vue.createElementVNode(
                "view",
                {
                  class: "progress-fill",
                  style: vue.normalizeStyle({ width: $setup.lineProgress(line) + "%" })
                },
                null,
                4
                /* STYLE */
              )
            ])
          ]);
        }),
        128
        /* KEYED_FRAGMENT */
      )),
      $setup.scanLog.length ? (vue.openBlock(), vue.createElementBlock("view", {
        key: 4,
        class: "log card"
      }, [
        vue.createElementVNode("text", { class: "log-title" }, "扫码记录"),
        (vue.openBlock(true), vue.createElementBlock(
          vue.Fragment,
          null,
          vue.renderList($setup.scanLog, (item, i) => {
            return vue.openBlock(), vue.createElementBlock(
              "view",
              {
                key: i,
                class: vue.normalizeClass(["log-item", item.ok ? "ok" : "fail"])
              },
              [
                vue.createElementVNode(
                  "text",
                  null,
                  vue.toDisplayString(item.time) + " · " + vue.toDisplayString(item.msg),
                  1
                  /* TEXT */
                )
              ],
              2
              /* CLASS */
            );
          }),
          128
          /* KEYED_FRAGMENT */
        ))
      ])) : vue.createCommentVNode("v-if", true)
    ]);
  }
  const PagesPickingIssuePick = /* @__PURE__ */ _export_sfc(_sfc_main$8, [["render", _sfc_render$7], ["__scopeId", "data-v-dd2d633c"], ["__file", "D:/AAA/WMS/wms-pda/src/pages/picking/issue-pick.vue"]]);
  const _sfc_main$7 = {
    __name: "pickup",
    setup(__props, { expose: __expose }) {
      __expose();
      const scanCode = vue.ref("");
      const issue = vue.ref(null);
      const lines = vue.ref([]);
      const lastPickup = vue.ref("");
      function parseIssueNo(code) {
        const c = (code || "").trim();
        if (c.startsWith("PI:")) return c.slice(3);
        if (c.startsWith("PI")) return c;
        return c;
      }
      async function onScan(code) {
        const issueNo = parseIssueNo(code);
        const data = await getPickIssueDetail(issueNo);
        issue.value = data.issue;
        lines.value = data.lines || [];
      }
      async function confirmPickup() {
        if (!issue.value) return;
        const pickupNo = await confirmMaterialPickup(issue.value.issueNo, "PDA操作员");
        lastPickup.value = pickupNo;
        uni.showToast({ title: "领料确认成功", icon: "success" });
      }
      const __returned__ = { scanCode, issue, lines, lastPickup, parseIssueNo, onScan, confirmPickup, ref: vue.ref, ScanInput, get confirmMaterialPickup() {
        return confirmMaterialPickup;
      }, get getPickIssueDetail() {
        return getPickIssueDetail;
      } };
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  };
  function _sfc_render$6(_ctx, _cache, $props, $setup, $data, $options) {
    return vue.openBlock(), vue.createElementBlock("view", { class: "page" }, [
      vue.createVNode($setup["ScanInput"], {
        modelValue: $setup.scanCode,
        "onUpdate:modelValue": _cache[0] || (_cache[0] = ($event) => $setup.scanCode = $event),
        placeholder: "扫描拣配发料单二维码",
        onScan: $setup.onScan
      }, null, 8, ["modelValue"]),
      $setup.issue ? (vue.openBlock(), vue.createElementBlock("view", {
        key: 0,
        class: "card"
      }, [
        vue.createElementVNode(
          "text",
          { class: "title" },
          "发料单 " + vue.toDisplayString($setup.issue.issueNo),
          1
          /* TEXT */
        ),
        vue.createElementVNode(
          "text",
          null,
          "仓库：" + vue.toDisplayString($setup.issue.warehouseCode),
          1
          /* TEXT */
        ),
        vue.createElementVNode(
          "text",
          null,
          "状态：" + vue.toDisplayString($setup.issue.status),
          1
          /* TEXT */
        ),
        vue.createElementVNode(
          "text",
          null,
          "交接区：" + vue.toDisplayString($setup.issue.handoverArea || "-"),
          1
          /* TEXT */
        )
      ])) : vue.createCommentVNode("v-if", true),
      $setup.lines.length ? (vue.openBlock(), vue.createElementBlock("view", {
        key: 1,
        class: "card"
      }, [
        vue.createElementVNode("text", { class: "subtitle" }, "明细"),
        (vue.openBlock(true), vue.createElementBlock(
          vue.Fragment,
          null,
          vue.renderList($setup.lines, (line) => {
            return vue.openBlock(), vue.createElementBlock("view", {
              key: line.lineNo,
              class: "line"
            }, [
              vue.createElementVNode(
                "text",
                null,
                vue.toDisplayString(line.materialCode) + " · 应拣 " + vue.toDisplayString(line.pickQty) + " / 已拣 " + vue.toDisplayString(line.pickedQty || 0),
                1
                /* TEXT */
              ),
              vue.createElementVNode(
                "text",
                { class: "loc" },
                "库位 " + vue.toDisplayString(line.sourceLocation || "-"),
                1
                /* TEXT */
              )
            ]);
          }),
          128
          /* KEYED_FRAGMENT */
        ))
      ])) : vue.createCommentVNode("v-if", true),
      $setup.issue ? (vue.openBlock(), vue.createElementBlock("button", {
        key: 2,
        class: "btn primary",
        onClick: $setup.confirmPickup
      }, "确认领料")) : vue.createCommentVNode("v-if", true),
      $setup.lastPickup ? (vue.openBlock(), vue.createElementBlock(
        "view",
        {
          key: 3,
          class: "tip"
        },
        "已生成领料单：" + vue.toDisplayString($setup.lastPickup),
        1
        /* TEXT */
      )) : vue.createCommentVNode("v-if", true)
    ]);
  }
  const PagesPickingPickup = /* @__PURE__ */ _export_sfc(_sfc_main$7, [["render", _sfc_render$6], ["__scopeId", "data-v-5ce58e2f"], ["__file", "D:/AAA/WMS/wms-pda/src/pages/picking/pickup.vue"]]);
  const _sfc_main$6 = {
    __name: "workshop-return",
    setup(__props, { expose: __expose }) {
      __expose();
      const reasonOptions = ["余料退库", "多领退回", "换料退库", "质量退回"];
      const scanCode = vue.ref("");
      const lastNo = vue.ref("");
      const qtyText = vue.ref("1");
      const submitting = vue.ref(false);
      const scanInputRef = vue.ref(null);
      const { alive, refocusScanInput } = usePageAlive();
      const form = vue.reactive({
        issueNo: "",
        warehouseCode: "WH001",
        materialCode: "",
        batchNo: "",
        returnReason: "余料退库"
      });
      const canSubmit = vue.computed(
        () => !!(form.materialCode || "").trim() && !!(form.warehouseCode || "").trim()
      );
      function toast(title) {
        uni.showToast({ title, icon: "none" });
      }
      function normalizeQty2() {
        let n = Number(qtyText.value);
        if (Number.isNaN(n) || n <= 0) n = 1;
        qtyText.value = String(Math.round(n * 1e3) / 1e3);
      }
      function adjustQty(delta) {
        let n = Number(qtyText.value);
        if (Number.isNaN(n) || n <= 0) n = 1;
        n = Math.max(1e-3, Math.round((n + delta) * 1e3) / 1e3);
        qtyText.value = String(n);
      }
      function resetForm() {
        form.issueNo = "";
        form.materialCode = "";
        form.batchNo = "";
        form.returnReason = "余料退库";
        qtyText.value = "1";
        scanCode.value = "";
        lastNo.value = "";
        refocusScanInput(scanInputRef, 200);
      }
      function onScan(code) {
        if (!alive.value || submitting.value) return;
        const c = (code || "").trim();
        if (!c) return;
        scanCode.value = c;
        const upper = c.toUpperCase();
        if (upper.startsWith("PI:") || upper.startsWith("PI")) {
          form.issueNo = upper.startsWith("PI:") ? c.slice(3) : c;
          toast("已填入发料单");
          refocusScanInput(scanInputRef, 200);
          return;
        }
        const parts = c.split("|");
        form.materialCode = (parts[0] || c).trim();
        if (parts[1]) form.batchNo = parts[1].trim();
        if (parts[2] && !form.warehouseCode) form.warehouseCode = parts[2].trim();
        toast("已填入物料");
        refocusScanInput(scanInputRef, 200);
      }
      async function submit() {
        if (!alive.value || submitting.value) return;
        const materialCode = (form.materialCode || "").trim();
        const warehouseCode = (form.warehouseCode || "").trim();
        if (!materialCode) {
          toast("请扫描或输入物料编码");
          return;
        }
        if (!warehouseCode) {
          toast("请输入仓库编码");
          return;
        }
        normalizeQty2();
        const returnQty = Number(qtyText.value);
        if (!returnQty || returnQty <= 0) {
          toast("退库数量须大于 0");
          return;
        }
        submitting.value = true;
        try {
          const returnNo = await submitWorkshopReturn({
            issueNo: (form.issueNo || "").trim() || void 0,
            warehouseCode,
            materialCode,
            batchNo: (form.batchNo || "").trim() || void 0,
            returnReason: (form.returnReason || "").trim() || "余料退库",
            returnQty,
            autoConfirm: true
          });
          lastNo.value = returnNo;
          uni.showToast({ title: "退库成功", icon: "success" });
          form.materialCode = "";
          form.batchNo = "";
          qtyText.value = "1";
          scanCode.value = "";
        } catch (e) {
          toast((e == null ? void 0 : e.message) || "退库失败");
        } finally {
          submitting.value = false;
          refocusScanInput(scanInputRef, 300);
        }
      }
      onLoad(() => uni.setNavigationBarTitle({ title: "车间退库" }));
      onShow(() => refocusScanInput(scanInputRef, 300));
      vue.onMounted(() => refocusScanInput(scanInputRef, 400));
      const __returned__ = { reasonOptions, scanCode, lastNo, qtyText, submitting, scanInputRef, alive, refocusScanInput, form, canSubmit, toast, normalizeQty: normalizeQty2, adjustQty, resetForm, onScan, submit, reactive: vue.reactive, ref: vue.ref, computed: vue.computed, onMounted: vue.onMounted, get onLoad() {
        return onLoad;
      }, get onShow() {
        return onShow;
      }, ScanSearchBar, get submitWorkshopReturn() {
        return submitWorkshopReturn;
      }, get usePageAlive() {
        return usePageAlive;
      } };
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  };
  function _sfc_render$5(_ctx, _cache, $props, $setup, $data, $options) {
    return vue.openBlock(), vue.createElementBlock("view", { class: "page" }, [
      vue.createElementVNode("view", { class: "scan-panel" }, [
        vue.createVNode($setup["ScanSearchBar"], {
          ref: "scanInputRef",
          modelValue: $setup.scanCode,
          "onUpdate:modelValue": _cache[0] || (_cache[0] = ($event) => $setup.scanCode = $event),
          placeholder: "侧键扫物料条码 / 发料单号",
          "action-text": "解析",
          disabled: $setup.submitting,
          onScan: $setup.onScan,
          onSearch: $setup.onScan
        }, null, 8, ["modelValue", "disabled"]),
        vue.createElementVNode("text", { class: "hint" }, "侧键扫码自动填入；发料单以 PI 开头，物料支持 编码|批次")
      ]),
      vue.createElementVNode("scroll-view", {
        class: "form-scroll",
        "scroll-y": "",
        "show-scrollbar": false
      }, [
        vue.createElementVNode("view", { class: "card" }, [
          vue.createElementVNode("view", { class: "card-head" }, [
            vue.createElementVNode("text", { class: "card-title" }, "退库信息"),
            vue.createElementVNode("text", {
              class: "card-link",
              onClick: $setup.resetForm
            }, "清空")
          ]),
          vue.createElementVNode("view", { class: "field" }, [
            vue.createElementVNode("text", { class: "label" }, "原发料单"),
            vue.withDirectives(vue.createElementVNode("input", {
              "onUpdate:modelValue": _cache[1] || (_cache[1] = ($event) => $setup.form.issueNo = $event),
              class: "input",
              placeholder: "可选，如 PI2026...",
              disabled: $setup.submitting
            }, null, 8, ["disabled"]), [
              [vue.vModelText, $setup.form.issueNo]
            ])
          ]),
          vue.createElementVNode("view", { class: "field" }, [
            vue.createElementVNode("text", { class: "label" }, [
              vue.createTextVNode("仓库编码 "),
              vue.createElementVNode("text", { class: "req" }, "*")
            ]),
            vue.withDirectives(vue.createElementVNode("input", {
              "onUpdate:modelValue": _cache[2] || (_cache[2] = ($event) => $setup.form.warehouseCode = $event),
              class: "input",
              placeholder: "请输入仓库",
              disabled: $setup.submitting
            }, null, 8, ["disabled"]), [
              [vue.vModelText, $setup.form.warehouseCode]
            ])
          ]),
          vue.createElementVNode("view", { class: "field" }, [
            vue.createElementVNode("text", { class: "label" }, [
              vue.createTextVNode("物料编码 "),
              vue.createElementVNode("text", { class: "req" }, "*")
            ]),
            vue.withDirectives(vue.createElementVNode("input", {
              "onUpdate:modelValue": _cache[3] || (_cache[3] = ($event) => $setup.form.materialCode = $event),
              class: "input mono",
              placeholder: "扫码或手输",
              disabled: $setup.submitting
            }, null, 8, ["disabled"]), [
              [vue.vModelText, $setup.form.materialCode]
            ])
          ]),
          vue.createElementVNode("view", { class: "field" }, [
            vue.createElementVNode("text", { class: "label" }, "批次"),
            vue.withDirectives(vue.createElementVNode("input", {
              "onUpdate:modelValue": _cache[4] || (_cache[4] = ($event) => $setup.form.batchNo = $event),
              class: "input mono",
              placeholder: "可选",
              disabled: $setup.submitting
            }, null, 8, ["disabled"]), [
              [vue.vModelText, $setup.form.batchNo]
            ])
          ])
        ]),
        vue.createElementVNode("view", { class: "card" }, [
          vue.createElementVNode("view", { class: "card-head" }, [
            vue.createElementVNode("text", { class: "card-title" }, "退库数量")
          ]),
          vue.createElementVNode("view", { class: "qty-row" }, [
            vue.createElementVNode("button", {
              class: "qty-step",
              disabled: $setup.submitting,
              onClick: _cache[5] || (_cache[5] = ($event) => $setup.adjustQty(-1))
            }, "−", 8, ["disabled"]),
            vue.withDirectives(vue.createElementVNode("input", {
              "onUpdate:modelValue": _cache[6] || (_cache[6] = ($event) => $setup.qtyText = $event),
              class: "qty-input",
              type: "text",
              inputmode: "decimal",
              disabled: $setup.submitting,
              onBlur: $setup.normalizeQty
            }, null, 40, ["disabled"]), [
              [vue.vModelText, $setup.qtyText]
            ]),
            vue.createElementVNode("button", {
              class: "qty-step",
              disabled: $setup.submitting,
              onClick: _cache[7] || (_cache[7] = ($event) => $setup.adjustQty(1))
            }, "＋", 8, ["disabled"])
          ]),
          vue.createElementVNode("text", { class: "hint tight" }, "默认 1，可手动调整")
        ]),
        vue.createElementVNode("view", { class: "card" }, [
          vue.createElementVNode("view", { class: "card-head" }, [
            vue.createElementVNode("text", { class: "card-title" }, "退库原因")
          ]),
          vue.createElementVNode("view", { class: "reason-chips" }, [
            (vue.openBlock(), vue.createElementBlock(
              vue.Fragment,
              null,
              vue.renderList($setup.reasonOptions, (item) => {
                return vue.createElementVNode("text", {
                  key: item,
                  class: vue.normalizeClass(["chip", $setup.form.returnReason === item && "on"]),
                  onClick: ($event) => $setup.form.returnReason = item
                }, vue.toDisplayString(item), 11, ["onClick"]);
              }),
              64
              /* STABLE_FRAGMENT */
            ))
          ]),
          vue.withDirectives(vue.createElementVNode("input", {
            "onUpdate:modelValue": _cache[8] || (_cache[8] = ($event) => $setup.form.returnReason = $event),
            class: "input",
            placeholder: "可自定义原因",
            disabled: $setup.submitting
          }, null, 8, ["disabled"]), [
            [vue.vModelText, $setup.form.returnReason]
          ])
        ]),
        $setup.lastNo ? (vue.openBlock(), vue.createElementBlock("view", {
          key: 0,
          class: "success-card"
        }, [
          vue.createElementVNode("text", { class: "success-title" }, "退库成功"),
          vue.createElementVNode(
            "text",
            { class: "success-no" },
            "单号 " + vue.toDisplayString($setup.lastNo),
            1
            /* TEXT */
          ),
          vue.createElementVNode("text", { class: "success-hint" }, "可继续扫下一件物料")
        ])) : vue.createCommentVNode("v-if", true),
        vue.createElementVNode("view", { class: "scroll-pad" })
      ]),
      vue.createElementVNode("view", { class: "footer" }, [
        vue.createElementVNode("button", {
          class: "submit-btn",
          type: "primary",
          loading: $setup.submitting,
          disabled: $setup.submitting || !$setup.canSubmit,
          onClick: $setup.submit
        }, " 确认退库" + vue.toDisplayString($setup.qtyText ? ` × ${$setup.qtyText}` : ""), 9, ["loading", "disabled"])
      ])
    ]);
  }
  const PagesPickingWorkshopReturn = /* @__PURE__ */ _export_sfc(_sfc_main$6, [["render", _sfc_render$5], ["__scopeId", "data-v-08a9dabb"], ["__file", "D:/AAA/WMS/wms-pda/src/pages/picking/workshop-return.vue"]]);
  const _sfc_main$5 = {
    __name: "messages",
    setup(__props, { expose: __expose }) {
      __expose();
      const messages = vue.ref([]);
      const unreadCount = vue.ref(0);
      async function loadData() {
        const [listRes, countRes] = await Promise.all([
          getMessages({ current: 1, size: 50 }),
          getUnreadCount()
        ]);
        messages.value = listRes.records || [];
        unreadCount.value = countRes.count || 0;
      }
      async function readOne(msg) {
        if (!msg.isRead) {
          await markMessageRead(msg.messageId);
          msg.isRead = true;
          unreadCount.value = Math.max(0, unreadCount.value - 1);
        }
      }
      async function readAll() {
        await markAllMessagesRead();
        messages.value.forEach((m) => {
          m.isRead = true;
        });
        unreadCount.value = 0;
        uni.showToast({ title: "已全部标记", icon: "success" });
      }
      vue.onMounted(loadData);
      const __returned__ = { messages, unreadCount, loadData, readOne, readAll, ref: vue.ref, onMounted: vue.onMounted, get getMessages() {
        return getMessages;
      }, get getUnreadCount() {
        return getUnreadCount;
      }, get markAllMessagesRead() {
        return markAllMessagesRead;
      }, get markMessageRead() {
        return markMessageRead;
      } };
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  };
  function _sfc_render$4(_ctx, _cache, $props, $setup, $data, $options) {
    return vue.openBlock(), vue.createElementBlock("view", { class: "page" }, [
      vue.createElementVNode("view", { class: "header" }, [
        vue.createElementVNode(
          "text",
          null,
          "未读消息: " + vue.toDisplayString($setup.unreadCount),
          1
          /* TEXT */
        ),
        vue.createElementVNode("button", {
          size: "mini",
          onClick: $setup.readAll
        }, "全部已读")
      ]),
      (vue.openBlock(true), vue.createElementBlock(
        vue.Fragment,
        null,
        vue.renderList($setup.messages, (msg) => {
          return vue.openBlock(), vue.createElementBlock("view", {
            key: msg.messageId,
            class: "card",
            onClick: ($event) => $setup.readOne(msg)
          }, [
            vue.createElementVNode("view", { class: "row" }, [
              vue.createElementVNode(
                "text",
                { class: "type" },
                vue.toDisplayString(msg.messageType),
                1
                /* TEXT */
              ),
              !msg.isRead ? (vue.openBlock(), vue.createElementBlock("text", {
                key: 0,
                class: "dot"
              }, "未读")) : vue.createCommentVNode("v-if", true)
            ]),
            vue.createElementVNode(
              "text",
              { class: "title" },
              vue.toDisplayString(msg.title),
              1
              /* TEXT */
            ),
            vue.createElementVNode(
              "text",
              { class: "content" },
              vue.toDisplayString(msg.content),
              1
              /* TEXT */
            ),
            vue.createElementVNode(
              "text",
              { class: "time" },
              vue.toDisplayString(msg.createTime),
              1
              /* TEXT */
            )
          ], 8, ["onClick"]);
        }),
        128
        /* KEYED_FRAGMENT */
      )),
      !$setup.messages.length ? (vue.openBlock(), vue.createElementBlock("view", {
        key: 0,
        class: "empty"
      }, "暂无消息")) : vue.createCommentVNode("v-if", true)
    ]);
  }
  const PagesMessagesMessages = /* @__PURE__ */ _export_sfc(_sfc_main$5, [["render", _sfc_render$4], ["__scopeId", "data-v-f5640984"], ["__file", "D:/AAA/WMS/wms-pda/src/pages/messages/messages.vue"]]);
  const _sfc_main$4 = {
    __name: "ServerConfigForm",
    props: {
      showTitle: { type: Boolean, default: true }
    },
    emits: ["saved"],
    setup(__props, { expose: __expose, emit: __emit }) {
      const props = __props;
      const emit = __emit;
      const serverInput = vue.ref("");
      const testing = vue.ref(false);
      const testResult = vue.ref(null);
      const hasCustom = vue.ref(false);
      const displayUrl = vue.computed(() => getServerDisplay());
      vue.onMounted(refresh);
      function refresh() {
        serverInput.value = getServerInputValue();
        hasCustom.value = hasCustomServer();
        testResult.value = null;
      }
      function onServerChanged(prevBase, nextBase) {
        if (prevBase === nextBase) {
          return false;
        }
        const hadSession = hasSession();
        clearSession();
        return hadSession;
      }
      function save() {
        if (!serverInput.value.trim() && !hasCustomServer()) {
          uni.showToast({ title: "请输入服务器地址", icon: "none" });
          return;
        }
        const prevBase = getBaseUrl();
        const saved = setBaseUrl(serverInput.value || "/api/v1");
        const needRelogin = onServerChanged(prevBase, saved);
        hasCustom.value = hasCustomServer();
        testResult.value = {
          ok: true,
          message: needRelogin ? `已保存: ${getServerDisplay()}（已退出登录，请重新登录）` : `已保存: ${getServerDisplay()}`
        };
        uni.showToast({ title: needRelogin ? "服务器已更新，请重新登录" : "服务器已更新", icon: "success" });
        emit("saved", { baseUrl: saved, needRelogin });
      }
      function reset() {
        const prevBase = getBaseUrl();
        const next = resetBaseUrl();
        const needRelogin = onServerChanged(prevBase, next);
        serverInput.value = getServerInputValue();
        hasCustom.value = false;
        testResult.value = {
          ok: true,
          message: needRelogin ? "已恢复默认配置（已退出登录）" : "已恢复默认配置"
        };
        uni.showToast({ title: needRelogin ? "已恢复默认，请重新登录" : "已恢复默认", icon: "none" });
        emit("saved", { baseUrl: next, needRelogin });
      }
      async function testConnection() {
        const base = serverInput.value.trim() ? normalizeBaseUrl(serverInput.value) : getBaseUrl();
        testing.value = true;
        testResult.value = null;
        try {
          await new Promise((resolve, reject) => {
            uni.request({
              url: base + "/auth/captcha",
              method: "GET",
              timeout: 8e3,
              success(res) {
                var _a, _b;
                if (res.statusCode === 200 && ((_a = res.data) == null ? void 0 : _a.code) === 200) resolve();
                else reject(new Error(((_b = res.data) == null ? void 0 : _b.message) || `HTTP ${res.statusCode}`));
              },
              fail: reject
            });
          });
          testResult.value = { ok: true, message: "连接成功，服务器可用" };
        } catch (e) {
          testResult.value = { ok: false, message: `连接失败: ${e.message || "网络错误"}` };
        } finally {
          testing.value = false;
        }
      }
      __expose({ refresh });
      const __returned__ = { props, emit, serverInput, testing, testResult, hasCustom, displayUrl, refresh, onServerChanged, save, reset, testConnection, ref: vue.ref, computed: vue.computed, onMounted: vue.onMounted, get clearSession() {
        return clearSession;
      }, get hasSession() {
        return hasSession;
      }, get getBaseUrl() {
        return getBaseUrl;
      }, get getServerDisplay() {
        return getServerDisplay;
      }, get getServerInputValue() {
        return getServerInputValue;
      }, get hasCustomServer() {
        return hasCustomServer;
      }, get normalizeBaseUrl() {
        return normalizeBaseUrl;
      }, get resetBaseUrl() {
        return resetBaseUrl;
      }, get setBaseUrl() {
        return setBaseUrl;
      } };
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  };
  function _sfc_render$3(_ctx, _cache, $props, $setup, $data, $options) {
    return vue.openBlock(), vue.createElementBlock("view", { class: "server-form" }, [
      $props.showTitle ? (vue.openBlock(), vue.createElementBlock("text", {
        key: 0,
        class: "form-title"
      }, "服务器配置")) : vue.createCommentVNode("v-if", true),
      vue.createElementVNode("text", { class: "hint" }, "请输入服务器地址，如 192.168.1.100:9980"),
      vue.withDirectives(vue.createElementVNode(
        "input",
        {
          "onUpdate:modelValue": _cache[0] || (_cache[0] = ($event) => $setup.serverInput = $event),
          class: "input",
          placeholder: "http://192.168.1.100:9980"
        },
        null,
        512
        /* NEED_PATCH */
      ), [
        [vue.vModelText, $setup.serverInput]
      ]),
      vue.createElementVNode(
        "text",
        { class: "current" },
        "当前: " + vue.toDisplayString($setup.displayUrl),
        1
        /* TEXT */
      ),
      vue.createElementVNode("view", { class: "btn-row" }, [
        vue.createElementVNode("button", {
          size: "mini",
          class: "btn-test",
          loading: $setup.testing,
          onClick: $setup.testConnection
        }, "测试连接", 8, ["loading"]),
        vue.createElementVNode("button", {
          size: "mini",
          class: "btn-save",
          type: "primary",
          onClick: $setup.save
        }, "保存"),
        $setup.hasCustom ? (vue.openBlock(), vue.createElementBlock("button", {
          key: 0,
          size: "mini",
          onClick: $setup.reset
        }, "恢复默认")) : vue.createCommentVNode("v-if", true)
      ]),
      $setup.testResult ? (vue.openBlock(), vue.createElementBlock(
        "view",
        {
          key: 1,
          class: vue.normalizeClass(["test-result", $setup.testResult.ok ? "ok" : "fail"])
        },
        vue.toDisplayString($setup.testResult.message),
        3
        /* TEXT, CLASS */
      )) : vue.createCommentVNode("v-if", true)
    ]);
  }
  const ServerConfigForm = /* @__PURE__ */ _export_sfc(_sfc_main$4, [["render", _sfc_render$3], ["__scopeId", "data-v-ee002fa2"], ["__file", "D:/AAA/WMS/wms-pda/src/components/ServerConfigForm.vue"]]);
  const API_SUFFIX = "/api/v1";
  function isAppPlus() {
    return typeof plus !== "undefined" && (plus == null ? void 0 : plus.runtime);
  }
  function getLocalAppVersion() {
    return new Promise((resolve) => {
      if (isAppPlus()) {
        try {
          plus.runtime.getProperty(plus.runtime.appid, (info) => {
            resolve({
              versionName: (info == null ? void 0 : info.version) || "1.0.0",
              versionCode: Number(info == null ? void 0 : info.versionCode) || 100,
              name: (info == null ? void 0 : info.name) || "WMS PDA",
              platform: "app"
            });
          });
          return;
        } catch {
        }
      }
      resolve({
        versionName: "1.0.0",
        versionCode: 100,
        name: "WMS PDA",
        platform: "h5"
      });
    });
  }
  function getServerRootUrl() {
    const base = getBaseUrl();
    if (base.startsWith("/")) {
      if (typeof location !== "undefined" && location.origin) {
        return location.origin;
      }
      return "";
    }
    return base.replace(API_SUFFIX, "").replace(/\/+$/, "");
  }
  function resolveDownloadUrl(url) {
    const raw = (url || "").trim();
    if (!raw) return "";
    if (/^https?:\/\//i.test(raw)) return raw;
    const root = getServerRootUrl();
    if (!root) return raw;
    if (raw.startsWith("/")) return `${root}${raw}`;
    return `${root}/${raw}`;
  }
  async function detectAppUpdate() {
    const local = await getLocalAppVersion();
    const remote = await checkAppUpdate(local.versionCode);
    return {
      local,
      remote,
      hasUpdate: !!(remote == null ? void 0 : remote.hasUpdate),
      downloadUrl: resolveDownloadUrl(remote == null ? void 0 : remote.downloadUrl)
    };
  }
  function downloadAndInstallUpdate({ downloadUrl, packageType = "wgt", onProgress } = {}) {
    return new Promise((resolve) => {
      if (!isAppPlus()) {
        resolve({ ok: false, message: "当前环境不支持安装应用更新，请在 PDA App 中操作" });
        return;
      }
      if (!downloadUrl) {
        resolve({ ok: false, message: "下载地址为空" });
        return;
      }
      const type = String(packageType || "wgt").toLowerCase();
      if (type === "apk") {
        plus.runtime.openURL(downloadUrl);
        resolve({ ok: true, message: "已打开下载页，请按提示安装" });
        return;
      }
      const task = uni.downloadFile({
        url: downloadUrl,
        success(res) {
          if (res.statusCode !== 200 || !res.tempFilePath) {
            resolve({ ok: false, message: `下载失败(${res.statusCode || 0})` });
            return;
          }
          plus.runtime.install(
            res.tempFilePath,
            { force: false },
            () => {
              resolve({ ok: true, message: "安装成功，即将重启" });
              setTimeout(() => {
                try {
                  plus.runtime.restart();
                } catch {
                }
              }, 400);
            },
            (e) => {
              resolve({
                ok: false,
                message: `安装失败: ${(e == null ? void 0 : e.message) || (e == null ? void 0 : e.code) || "未知错误"}`
              });
            }
          );
        },
        fail(err) {
          resolve({ ok: false, message: (err == null ? void 0 : err.errMsg) || "下载失败，请检查网络" });
        }
      });
      if (task && typeof onProgress === "function" && task.onProgressUpdate) {
        task.onProgressUpdate((p) => {
          onProgress((p == null ? void 0 : p.progress) ?? 0);
        });
      }
    });
  }
  const _sfc_main$3 = {
    __name: "settings",
    setup(__props, { expose: __expose }) {
      __expose();
      const serverFormRef = vue.ref(null);
      const fromLogin = vue.ref(false);
      const pwd = vue.reactive({ old: "", new1: "", new2: "" });
      const deviceNo = defaultConfig.deviceNo;
      const appVersionText = vue.ref("WMS PDA");
      const isLoggedIn = vue.computed(() => !!uni.getStorageSync("wms_token"));
      onLoad((options) => {
        fromLogin.value = (options == null ? void 0 : options.from) === "login";
        getLocalAppVersion().then((v) => {
          appVersionText.value = `WMS PDA ${v.versionName}`;
        });
      });
      onShow(() => {
        var _a, _b;
        (_b = (_a = serverFormRef.value) == null ? void 0 : _a.refresh) == null ? void 0 : _b.call(_a);
      });
      function onServerSaved(payload) {
        if ((payload == null ? void 0 : payload.needRelogin) && !fromLogin.value) {
          uni.reLaunch({ url: "/pages/login/login" });
        }
      }
      async function handleChangePwd() {
        if (!pwd.old || !pwd.new1) {
          uni.showToast({ title: "请填写密码", icon: "none" });
          return;
        }
        if (pwd.new1 !== pwd.new2) {
          uni.showToast({ title: "两次密码不一致", icon: "none" });
          return;
        }
        await changePassword(pwd.old, pwd.new1);
        pwd.old = "";
        pwd.new1 = "";
        pwd.new2 = "";
        uni.showToast({ title: "密码已修改", icon: "success" });
      }
      function goBack() {
        uni.navigateBack();
      }
      const __returned__ = { serverFormRef, fromLogin, pwd, deviceNo, appVersionText, isLoggedIn, onServerSaved, handleChangePwd, goBack, ref: vue.ref, reactive: vue.reactive, computed: vue.computed, get onLoad() {
        return onLoad;
      }, get onShow() {
        return onShow;
      }, ServerConfigForm, get defaultConfig() {
        return defaultConfig;
      }, get changePassword() {
        return changePassword;
      }, get getLocalAppVersion() {
        return getLocalAppVersion;
      } };
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  };
  function _sfc_render$2(_ctx, _cache, $props, $setup, $data, $options) {
    return vue.openBlock(), vue.createElementBlock("view", { class: "page" }, [
      vue.createElementVNode("view", { class: "section-card" }, [
        vue.createVNode(
          $setup["ServerConfigForm"],
          {
            ref: "serverFormRef",
            onSaved: $setup.onServerSaved
          },
          null,
          512
          /* NEED_PATCH */
        )
      ]),
      $setup.isLoggedIn ? (vue.openBlock(), vue.createElementBlock("view", {
        key: 0,
        class: "section-card"
      }, [
        vue.createElementVNode("text", { class: "section-title" }, "账号安全"),
        vue.createElementVNode("text", { class: "label" }, "原密码"),
        vue.withDirectives(vue.createElementVNode(
          "input",
          {
            "onUpdate:modelValue": _cache[0] || (_cache[0] = ($event) => $setup.pwd.old = $event),
            class: "input",
            password: "",
            placeholder: "原密码"
          },
          null,
          512
          /* NEED_PATCH */
        ), [
          [vue.vModelText, $setup.pwd.old]
        ]),
        vue.createElementVNode("text", { class: "label" }, "新密码"),
        vue.withDirectives(vue.createElementVNode(
          "input",
          {
            "onUpdate:modelValue": _cache[1] || (_cache[1] = ($event) => $setup.pwd.new1 = $event),
            class: "input",
            password: "",
            placeholder: "新密码"
          },
          null,
          512
          /* NEED_PATCH */
        ), [
          [vue.vModelText, $setup.pwd.new1]
        ]),
        vue.createElementVNode("text", { class: "label" }, "确认新密码"),
        vue.withDirectives(vue.createElementVNode(
          "input",
          {
            "onUpdate:modelValue": _cache[2] || (_cache[2] = ($event) => $setup.pwd.new2 = $event),
            class: "input",
            password: "",
            placeholder: "确认新密码"
          },
          null,
          512
          /* NEED_PATCH */
        ), [
          [vue.vModelText, $setup.pwd.new2]
        ]),
        vue.createElementVNode("button", {
          class: "btn-primary",
          onClick: $setup.handleChangePwd
        }, "保存密码")
      ])) : vue.createCommentVNode("v-if", true),
      vue.createElementVNode("view", { class: "section-card" }, [
        vue.createElementVNode("text", { class: "section-title" }, "设备信息"),
        vue.createElementVNode(
          "text",
          { class: "info-row" },
          "设备编号: " + vue.toDisplayString($setup.deviceNo),
          1
          /* TEXT */
        ),
        vue.createElementVNode(
          "text",
          { class: "info-row" },
          "应用版本: " + vue.toDisplayString($setup.appVersionText),
          1
          /* TEXT */
        )
      ]),
      $setup.fromLogin ? (vue.openBlock(), vue.createElementBlock("button", {
        key: 1,
        class: "btn-back",
        onClick: $setup.goBack
      }, "返回登录")) : vue.createCommentVNode("v-if", true)
    ]);
  }
  const PagesSettingsSettings = /* @__PURE__ */ _export_sfc(_sfc_main$3, [["render", _sfc_render$2], ["__scopeId", "data-v-6d719173"], ["__file", "D:/AAA/WMS/wms-pda/src/pages/settings/settings.vue"]]);
  const _sfc_main$2 = {
    __name: "profile",
    setup(__props, { expose: __expose }) {
      __expose();
      const panelRef = vue.ref(null);
      vue.onMounted(() => {
        var _a, _b;
        return (_b = (_a = panelRef.value) == null ? void 0 : _a.loadProfile) == null ? void 0 : _b.call(_a);
      });
      const __returned__ = { panelRef, ref: vue.ref, onMounted: vue.onMounted, ProfilePanel };
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  };
  function _sfc_render$1(_ctx, _cache, $props, $setup, $data, $options) {
    return vue.openBlock(), vue.createBlock(
      $setup["ProfilePanel"],
      { ref: "panelRef" },
      null,
      512
      /* NEED_PATCH */
    );
  }
  const PagesProfileProfile = /* @__PURE__ */ _export_sfc(_sfc_main$2, [["render", _sfc_render$1], ["__file", "D:/AAA/WMS/wms-pda/src/pages/profile/profile.vue"]]);
  const _sfc_main$1 = {
    __name: "update",
    setup(__props, { expose: __expose }) {
      __expose();
      const checking = vue.ref(false);
      const downloading = vue.ref(false);
      const progress = vue.ref(0);
      const local = vue.ref({});
      const remote = vue.ref({});
      const hasUpdate = vue.ref(false);
      const downloadUrl = vue.ref("");
      const errorTip = vue.ref("");
      let autoChecked = false;
      const statusText = vue.computed(() => {
        if (checking.value) return "正在检测...";
        if (errorTip.value) return errorTip.value;
        if (hasUpdate.value) return remote.value.message || "发现新版本";
        return remote.value.message || "已是最新版本";
      });
      const statusClass = vue.computed(() => {
        if (errorTip.value) return "err";
        if (hasUpdate.value) return "new";
        return "ok";
      });
      async function runCheck(force = false) {
        if (checking.value || downloading.value) return;
        checking.value = true;
        errorTip.value = "";
        try {
          const result = await detectAppUpdate();
          local.value = result.local || {};
          remote.value = result.remote || {};
          hasUpdate.value = !!result.hasUpdate;
          downloadUrl.value = result.downloadUrl || "";
          if (force && !hasUpdate.value) {
            uni.showToast({ title: remote.value.message || "已是最新版本", icon: "none" });
          }
          if (hasUpdate.value && remote.value.force && force !== false) {
          }
        } catch (e) {
          errorTip.value = (e == null ? void 0 : e.message) || "检测失败，请检查网络";
          hasUpdate.value = false;
          uni.showToast({ title: errorTip.value, icon: "none" });
        } finally {
          checking.value = false;
        }
      }
      async function onInstall() {
        if (!hasUpdate.value || downloading.value) return;
        if (!downloadUrl.value) {
          uni.showToast({ title: "下载地址无效", icon: "none" });
          return;
        }
        downloading.value = true;
        progress.value = 0;
        try {
          const res = await downloadAndInstallUpdate({
            downloadUrl: downloadUrl.value,
            packageType: remote.value.packageType || "wgt",
            onProgress: (p) => {
              progress.value = Math.max(0, Math.min(100, Number(p) || 0));
            }
          });
          if (!(res == null ? void 0 : res.ok)) {
            uni.showToast({ title: (res == null ? void 0 : res.message) || "更新失败", icon: "none", duration: 3e3 });
          } else if (res.message) {
            uni.showToast({ title: res.message, icon: "none" });
          }
        } finally {
          downloading.value = false;
        }
      }
      onLoad(() => {
        uni.setNavigationBarTitle({ title: "检查更新" });
      });
      onShow(async () => {
        if (autoChecked) return;
        autoChecked = true;
        await runCheck(false);
      });
      const __returned__ = { checking, downloading, progress, local, remote, hasUpdate, downloadUrl, errorTip, get autoChecked() {
        return autoChecked;
      }, set autoChecked(v) {
        autoChecked = v;
      }, statusText, statusClass, runCheck, onInstall, ref: vue.ref, computed: vue.computed, get onLoad() {
        return onLoad;
      }, get onShow() {
        return onShow;
      }, get detectAppUpdate() {
        return detectAppUpdate;
      }, get downloadAndInstallUpdate() {
        return downloadAndInstallUpdate;
      } };
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  };
  function _sfc_render(_ctx, _cache, $props, $setup, $data, $options) {
    return vue.openBlock(), vue.createElementBlock("view", { class: "page" }, [
      vue.createElementVNode("view", { class: "card" }, [
        vue.createElementVNode("text", { class: "title" }, "应用更新"),
        vue.createElementVNode("text", { class: "sub" }, "进入页面后自动检测最新版本"),
        vue.createElementVNode("view", { class: "version-box" }, [
          vue.createElementVNode("view", { class: "row" }, [
            vue.createElementVNode("text", { class: "label" }, "当前版本"),
            vue.createElementVNode(
              "text",
              { class: "value" },
              vue.toDisplayString($setup.local.versionName || "-") + " (" + vue.toDisplayString($setup.local.versionCode || "-") + ")",
              1
              /* TEXT */
            )
          ]),
          vue.createElementVNode("view", { class: "row" }, [
            vue.createElementVNode("text", { class: "label" }, "最新版本"),
            vue.createElementVNode(
              "text",
              { class: "value" },
              vue.toDisplayString($setup.remote.latestVersionName || "-") + " (" + vue.toDisplayString($setup.remote.latestVersionCode || "-") + ")",
              1
              /* TEXT */
            )
          ]),
          vue.createElementVNode("view", { class: "row" }, [
            vue.createElementVNode("text", { class: "label" }, "检测状态"),
            vue.createElementVNode(
              "text",
              {
                class: vue.normalizeClass(["value", $setup.statusClass])
              },
              vue.toDisplayString($setup.statusText),
              3
              /* TEXT, CLASS */
            )
          ])
        ]),
        $setup.remote.changelog ? (vue.openBlock(), vue.createElementBlock("view", {
          key: 0,
          class: "changelog"
        }, [
          vue.createElementVNode("text", { class: "changelog-title" }, "更新说明"),
          vue.createElementVNode(
            "text",
            { class: "changelog-body" },
            vue.toDisplayString($setup.remote.changelog),
            1
            /* TEXT */
          )
        ])) : vue.createCommentVNode("v-if", true),
        $setup.downloading ? (vue.openBlock(), vue.createElementBlock("view", {
          key: 1,
          class: "progress-wrap"
        }, [
          vue.createElementVNode("view", { class: "progress-bar" }, [
            vue.createElementVNode(
              "view",
              {
                class: "progress-inner",
                style: vue.normalizeStyle({ width: $setup.progress + "%" })
              },
              null,
              4
              /* STYLE */
            )
          ]),
          vue.createElementVNode(
            "text",
            { class: "progress-text" },
            "下载中 " + vue.toDisplayString($setup.progress) + "%",
            1
            /* TEXT */
          )
        ])) : vue.createCommentVNode("v-if", true),
        vue.createElementVNode("button", {
          class: "btn-primary",
          type: "primary",
          loading: $setup.checking || $setup.downloading,
          disabled: $setup.checking || $setup.downloading,
          onClick: _cache[0] || (_cache[0] = ($event) => $setup.runCheck(true))
        }, vue.toDisplayString($setup.checking ? "检测中..." : "重新检测"), 9, ["loading", "disabled"]),
        $setup.hasUpdate ? (vue.openBlock(), vue.createElementBlock("button", {
          key: 2,
          class: "btn-update",
          type: "warn",
          loading: $setup.downloading,
          disabled: $setup.checking || $setup.downloading,
          onClick: $setup.onInstall
        }, vue.toDisplayString($setup.downloading ? "更新中..." : $setup.remote.force ? "立即更新（强制）" : "立即更新"), 9, ["loading", "disabled"])) : vue.createCommentVNode("v-if", true)
      ])
    ]);
  }
  const PagesProfileUpdate = /* @__PURE__ */ _export_sfc(_sfc_main$1, [["render", _sfc_render], ["__scopeId", "data-v-edf9c5b8"], ["__file", "D:/AAA/WMS/wms-pda/src/pages/profile/update.vue"]]);
  __definePage("pages/login/login", PagesLoginLogin);
  __definePage("pages/index/index", PagesIndexIndex);
  __definePage("pages/inbound/notice-hub", PagesInboundNoticeHub);
  __definePage("pages/inbound/receive-list", PagesInboundReceiveList);
  __definePage("pages/inbound/receive-scan", PagesInboundReceiveScan);
  __definePage("pages/outbound/notice-hub", PagesOutboundNoticeHub);
  __definePage("pages/notice/list", PagesNoticeList);
  __definePage("pages/notice/scan", PagesNoticeScan);
  __definePage("pages/inbound/direct", PagesInboundDirect);
  __definePage("pages/outbound/outbound", PagesOutboundOutbound);
  __definePage("pages/inventory/inventory", PagesInventoryInventory);
  __definePage("pages/transfer/transfer", PagesTransferTransfer);
  __definePage("pages/stockcheck/stockcheck-list", PagesStockcheckStockcheckList);
  __definePage("pages/stockcheck/stockcheck-scan", PagesStockcheckStockcheckScan);
  __definePage("pages/stockcheck/stockcheck", PagesStockcheckStockcheck);
  __definePage("pages/tasklist/tasklist", PagesTasklistTasklist);
  __definePage("pages/panel/panel", PagesPanelPanel);
  __definePage("pages/trace/trace", PagesTraceTrace);
  __definePage("pages/picking/production-issue", PagesPickingProductionIssue);
  __definePage("pages/picking/production-issue-scan", PagesPickingProductionIssueScan);
  __definePage("pages/picking/production-feed", PagesPickingProductionFeed);
  __definePage("pages/picking/production-feed-scan", PagesPickingProductionFeedScan);
  __definePage("pages/picking/outsource-feed", PagesPickingOutsourceFeed);
  __definePage("pages/picking/outsource-feed-scan", PagesPickingOutsourceFeedScan);
  __definePage("pages/picking/outsource-issue", PagesPickingOutsourceIssue);
  __definePage("pages/picking/outsource-issue-scan", PagesPickingOutsourceIssueScan);
  __definePage("pages/picking/production-return", PagesPickingProductionReturn);
  __definePage("pages/picking/production-return-scan", PagesPickingProductionReturnScan);
  __definePage("pages/picking/outsource-return", PagesPickingOutsourceReturn);
  __definePage("pages/picking/outsource-return-scan", PagesPickingOutsourceReturnScan);
  __definePage("pages/picking/issue-pick", PagesPickingIssuePick);
  __definePage("pages/picking/pickup", PagesPickingPickup);
  __definePage("pages/picking/workshop-return", PagesPickingWorkshopReturn);
  __definePage("pages/messages/messages", PagesMessagesMessages);
  __definePage("pages/settings/settings", PagesSettingsSettings);
  __definePage("pages/profile/profile", PagesProfileProfile);
  __definePage("pages/profile/update", PagesProfileUpdate);
  const _sfc_main = {
    onLaunch() {
      formatAppLog("log", "at App.vue:4", "WMS PDA launched");
    }
  };
  const App = /* @__PURE__ */ _export_sfc(_sfc_main, [["__file", "D:/AAA/WMS/wms-pda/src/App.vue"]]);
  function createApp() {
    const app = vue.createVueApp(App);
    return { app };
  }
  const { app: __app__, Vuex: __Vuex__, Pinia: __Pinia__ } = createApp();
  uni.Vuex = __Vuex__;
  uni.Pinia = __Pinia__;
  __app__.provide("__globalStyles", __uniConfig.styles);
  __app__._component.mpType = "app";
  __app__._component.render = () => {
  };
  __app__.mount("#app");
})(Vue);
