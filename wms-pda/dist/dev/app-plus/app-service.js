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
  const onLoad = /* @__PURE__ */ createHook(ON_LOAD);
  const onUnload = /* @__PURE__ */ createHook(ON_UNLOAD);
  const onPullDownRefresh = /* @__PURE__ */ createHook(ON_PULL_DOWN_REFRESH);
  const defaultConfig = {
    // H5 开发走 vite 代理；真机调试请改为电脑局域网 IP
    baseUrl: "/api/v1",
    deviceNo: "PDA-SN-DEV001"
  };
  const STORAGE_KEY = "wms_server_base_url";
  const API_SUFFIX = "/api/v1";
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
      return url.endsWith(API_SUFFIX) ? url : `${url}${API_SUFFIX}`.replace("//", "/");
    }
    if (!/^https?:\/\//i.test(url)) {
      url = `http://${url}`;
    }
    if (!url.endsWith(API_SUFFIX)) {
      url = `${url}${API_SUFFIX}`;
    }
    return url;
  }
  function getServerDisplay() {
    const base = getBaseUrl();
    if (base.startsWith("/")) return "本地开发代理";
    try {
      const root = base.replace(API_SUFFIX, "");
      const u = new URL(root);
      return u.host;
    } catch {
      return base;
    }
  }
  function getServerInputValue() {
    const base = getBaseUrl();
    if (base.startsWith("/")) return "";
    return base.replace(API_SUFFIX, "").replace(/\/+$/, "");
  }
  let refreshing = null;
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
            } else resolve(false);
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
          header: {
            "Content-Type": "application/json",
            Authorization: token ? `Bearer ${token}` : "",
            "X-Device-ID": defaultConfig.deviceNo,
            ...options.header
          },
          success(res) {
            const body = res.data;
            if (body && body.code === 200) {
              resolve(body.data);
            } else if ((body == null ? void 0 : body.code) === 401 && !retried) {
              tryRefreshToken().then((ok) => {
                if (ok) doRequest(true);
                else {
                  uni.showToast({ title: "登录已过期", icon: "none" });
                  uni.reLaunch({ url: "/pages/login/login" });
                  reject(body);
                }
              });
            } else {
              if (!options.silent) {
                uni.showToast({ title: (body == null ? void 0 : body.message) || "请求失败", icon: "none" });
              }
              reject(body);
            }
          },
          fail(err) {
            uni.showToast({ title: "网络错误", icon: "none" });
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
  function verifyPanelCode(data) {
    return request({
      url: "/mobile/panel/verify",
      method: "POST",
      data: withDevice(data)
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
  function getQcOrder(qcNo) {
    return request({ url: `/mobile/quality/${qcNo}` });
  }
  function submitQcResult(qcNo, data) {
    return request({
      url: `/mobile/quality/${qcNo}/result`,
      method: "POST",
      data: withDevice({
        result: data.overallResult || data.result,
        remark: data.remark
      })
    });
  }
  function traceBatch(data) {
    return request({
      url: "/mobile/trace",
      method: "POST",
      data
    });
  }
  function syncOfflineData(offlineData, lastSyncTime) {
    return request({
      url: "/mobile/sync",
      method: "POST",
      data: withDevice({ offlineData, lastSyncTime })
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
  const _sfc_main$B = {
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
  function _sfc_render$A(_ctx, _cache, $props, $setup, $data, $options) {
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
  const PagesLoginLogin = /* @__PURE__ */ _export_sfc(_sfc_main$B, [["render", _sfc_render$A], ["__scopeId", "data-v-cdfe2409"], ["__file", "D:/AAA/WMS/wms-pda/src/pages/login/login.vue"]]);
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
  const _sfc_main$A = {
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
      async function handleSync() {
        const queue = getOfflineQueue();
        if (!queue.length) {
          uni.showToast({ title: "无离线数据", icon: "none" });
          return;
        }
        const res = await syncOfflineData(queue, uni.getStorageSync("last_sync_time") || "");
        uni.setStorageSync("last_sync_time", (/* @__PURE__ */ new Date()).toISOString());
        uni.removeStorageSync("offline_queue");
        queueCount.value = 0;
        const ok = res.successCount ?? res.totalSynced ?? 0;
        const fail = res.failCount ?? 0;
        uni.showToast({ title: `成功${ok}条${fail ? `，失败${fail}条` : ""}`, icon: "none" });
      }
      function handleLogout() {
        uni.showModal({
          title: "确认退出",
          content: "确定要退出登录吗？",
          success(res) {
            if (res.confirm) {
              uni.removeStorageSync("wms_token");
              uni.removeStorageSync("wms_refresh_token");
              uni.removeStorageSync("wms_user");
              uni.reLaunch({ url: "/pages/login/login" });
            }
          }
        });
      }
      __expose({ loadProfile });
      const __returned__ = { user, deviceNo, queueCount, serverDisplay, avatarLetter, roleText, loadProfile, goSettings, handleSync, handleLogout, ref: vue.ref, computed: vue.computed, get defaultConfig() {
        return defaultConfig;
      }, get getServerDisplay() {
        return getServerDisplay;
      }, get getUserInfo() {
        return getUserInfo;
      }, get syncOfflineData() {
        return syncOfflineData;
      }, get getOfflineQueue() {
        return getOfflineQueue;
      } };
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  };
  function _sfc_render$z(_ctx, _cache, $props, $setup, $data, $options) {
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
          onClick: $setup.handleSync
        }, [
          vue.createElementVNode("text", { class: "action-icon" }, "🔄"),
          vue.createElementVNode("text", null, "同步离线数据")
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
  const ProfilePanel = /* @__PURE__ */ _export_sfc(_sfc_main$A, [["render", _sfc_render$z], ["__scopeId", "data-v-bc2e5816"], ["__file", "D:/AAA/WMS/wms-pda/src/components/ProfilePanel.vue"]]);
  const STORAGE_TAB_KEY = "wms_active_tab";
  const STORAGE_SCROLL_KEY = "wms_tab_scroll";
  const _sfc_main$z = {
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
        { key: "stockcheck", label: "待盘点", color: "green" },
        { key: "qc", label: "待质检", color: "purple" }
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
        stockcheck: [],
        qc: []
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
          return {
            ...item,
            _key: item.taskNo,
            _title: item.taskNo,
            _meta: `${item.warehouseCode || "-"} · ${item.status || "-"}`
          };
        }
        return {
          ...item,
          _key: item.qcNo,
          _title: item.qcNo,
          _meta: `${item.materialCode || "-"} · ${item.status || "-"}`
        };
      }
      function rebuildTaskCache() {
        ["inbound", "outbound", "stockcheck", "qc"].forEach((key) => {
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
        else goTaskList(key);
      }
      function goTaskList(type) {
        uni.navigateTo({ url: `/pages/tasklist/tasklist?type=${type}` });
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
      }, taskListCache, todayStr, totalPending, getCount, getTaskList, normalizeTask, rebuildTaskCache, restoreScrollTops, onRefresherRefresh, onScroll, switchTab, loadProfileTab, onOverviewClick, goTaskList, ensureLogin, loadHomeData, loadTabData, refreshTab, goInbound, goOutbound, goPage, ref: vue.ref, reactive: vue.reactive, computed: vue.computed, onMounted: vue.onMounted, nextTick: vue.nextTick, get onShow() {
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
  function _sfc_render$y(_ctx, _cache, $props, $setup, $data, $options) {
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
          onScroll: _cache[12] || (_cache[12] = (e) => $setup.onScroll("home", e))
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
                onClick: _cache[0] || (_cache[0] = ($event) => $setup.goTaskList("stockcheck"))
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
                onClick: _cache[1] || (_cache[1] = ($event) => $setup.goTaskList("qc"))
              }, [
                vue.createElementVNode("text", { class: "menu-icon" }, "✅"),
                vue.createElementVNode("text", null, "质检"),
                $setup.getCount("qc") > 0 ? (vue.openBlock(), vue.createElementBlock(
                  "text",
                  {
                    key: 0,
                    class: "badge"
                  },
                  vue.toDisplayString($setup.getCount("qc")),
                  1
                  /* TEXT */
                )) : vue.createCommentVNode("v-if", true)
              ]),
              vue.createElementVNode("view", {
                class: "menu-item",
                onClick: _cache[2] || (_cache[2] = ($event) => $setup.goPage("/pages/panel/panel"))
              }, [
                vue.createElementVNode("text", { class: "menu-icon" }, "🏷️"),
                vue.createElementVNode("text", null, "板码校验")
              ]),
              vue.createElementVNode("view", {
                class: "menu-item",
                onClick: _cache[3] || (_cache[3] = ($event) => $setup.goPage("/pages/inventory/inventory"))
              }, [
                vue.createElementVNode("text", { class: "menu-icon" }, "📦"),
                vue.createElementVNode("text", null, "库存查询")
              ]),
              vue.createElementVNode("view", {
                class: "menu-item",
                onClick: _cache[4] || (_cache[4] = ($event) => $setup.goPage("/pages/transfer/transfer"))
              }, [
                vue.createElementVNode("text", { class: "menu-icon" }, "🔄"),
                vue.createElementVNode("text", null, "移库")
              ]),
              vue.createElementVNode("view", {
                class: "menu-item",
                onClick: _cache[5] || (_cache[5] = ($event) => $setup.goPage("/pages/trace/trace"))
              }, [
                vue.createElementVNode("text", { class: "menu-icon" }, "🔍"),
                vue.createElementVNode("text", null, "批次追溯")
              ]),
              vue.createElementVNode("view", {
                class: "menu-item",
                onClick: _cache[6] || (_cache[6] = ($event) => $setup.goPage("/pages/picking/issue-pick"))
              }, [
                vue.createElementVNode("text", { class: "menu-icon" }, "🛒"),
                vue.createElementVNode("text", null, "扫码拣货")
              ]),
              vue.createElementVNode("view", {
                class: "menu-item",
                onClick: _cache[7] || (_cache[7] = ($event) => $setup.goPage("/pages/picking/pickup"))
              }, [
                vue.createElementVNode("text", { class: "menu-icon" }, "📤"),
                vue.createElementVNode("text", null, "领料确认")
              ]),
              vue.createElementVNode("view", {
                class: "menu-item",
                onClick: _cache[8] || (_cache[8] = ($event) => $setup.goPage("/pages/picking/workshop-return"))
              }, [
                vue.createElementVNode("text", { class: "menu-icon" }, "↩️"),
                vue.createElementVNode("text", null, "车间退库")
              ]),
              vue.createElementVNode("view", {
                class: "menu-item",
                onClick: _cache[9] || (_cache[9] = ($event) => $setup.goPage("/pages/picking/production-return"))
              }, [
                vue.createElementVNode("text", { class: "menu-icon" }, "📥"),
                vue.createElementVNode("text", null, "生产退料")
              ]),
              vue.createElementVNode("view", {
                class: "menu-item",
                onClick: _cache[10] || (_cache[10] = ($event) => $setup.goPage("/pages/picking/outsource-return"))
              }, [
                vue.createElementVNode("text", { class: "menu-icon" }, "🔁"),
                vue.createElementVNode("text", null, "委外退料")
              ]),
              vue.createElementVNode("view", {
                class: "menu-item",
                onClick: _cache[11] || (_cache[11] = ($event) => $setup.goPage("/pages/messages/messages"))
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
          onScroll: _cache[13] || (_cache[13] = (e) => $setup.onScroll("inbound", e))
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
          onScroll: _cache[14] || (_cache[14] = (e) => $setup.onScroll("outbound", e))
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
          onScroll: _cache[15] || (_cache[15] = (e) => $setup.onScroll("profile", e))
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
  const PagesIndexIndex = /* @__PURE__ */ _export_sfc(_sfc_main$z, [["render", _sfc_render$y], ["__scopeId", "data-v-83a5a03c"], ["__file", "D:/AAA/WMS/wms-pda/src/pages/index/index.vue"]]);
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
      label: "生产入库单",
      direction: "INBOUND",
      icon: "📦",
      color: "#22c55e",
      searchPlaceholder: "扫码或搜索生产入库单号"
    },
    PRODUCTION_RETURN: {
      code: "PRODUCTION_RETURN",
      label: "生产退料",
      direction: "INBOUND",
      icon: "↩️",
      color: "#0ea5e9",
      searchPlaceholder: "扫码或搜索生产领料单号/车间"
    },
    OUTSOURCE_RETURN: {
      code: "OUTSOURCE_RETURN",
      label: "委外退料",
      direction: "INBOUND",
      icon: "🔁",
      color: "#7c3aed",
      searchPlaceholder: "扫码或搜索委外领料单号/供应商"
    },
    OTHER_IN: {
      code: "OTHER_IN",
      label: "其他入库单",
      direction: "INBOUND",
      icon: "📋",
      color: "#64748b",
      searchPlaceholder: "扫码或搜索其他入库单号"
    },
    SALES_DELIVERY: {
      code: "SALES_DELIVERY",
      label: "销售发货通知单",
      direction: "OUTBOUND",
      icon: "🚚",
      color: "#ef4444",
      searchPlaceholder: "扫码或搜索发货通知单号"
    },
    PRODUCTION_ISSUE: {
      code: "PRODUCTION_ISSUE",
      label: "生产领料",
      direction: "OUTBOUND",
      icon: "🔧",
      color: "#f97316",
      searchPlaceholder: "扫码或搜索生产用料清单号/车间"
    },
    OUTSOURCE_ISSUE: {
      code: "OUTSOURCE_ISSUE",
      label: "委外领料",
      direction: "OUTBOUND",
      icon: "🏗️",
      color: "#a855f7",
      searchPlaceholder: "扫码或搜索委外用料清单号/供应商"
    },
    OTHER_OUT: {
      code: "OTHER_OUT",
      label: "其他出库单",
      direction: "OUTBOUND",
      icon: "📤",
      color: "#64748b",
      searchPlaceholder: "扫码或搜索其他出库单号"
    }
  };
  function getNoticeBillType(code) {
    return NOTICE_BILL_TYPES[code] || NOTICE_BILL_TYPES.PURCHASE_RECEIVE;
  }
  function listNoticeBillTypes(direction) {
    return Object.values(NOTICE_BILL_TYPES).filter((t) => t.direction === direction);
  }
  const _sfc_main$y = {
    __name: "notice-hub",
    setup(__props, { expose: __expose }) {
      __expose();
      const types = vue.ref(listNoticeBillTypes("INBOUND"));
      function returnDesc(code) {
        if (code === "PRODUCTION_RETURN") return "扫领料单 · 核对物料 · 同步退料单";
        if (code === "OUTSOURCE_RETURN") return "扫委外领料单 · 核对物料 · 同步退料单";
        return "扫码 · 勾选 · 分批入库";
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
  function _sfc_render$x(_ctx, _cache, $props, $setup, $data, $options) {
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
  const PagesInboundNoticeHub = /* @__PURE__ */ _export_sfc(_sfc_main$y, [["render", _sfc_render$x], ["__scopeId", "data-v-16aecafc"], ["__file", "D:/AAA/WMS/wms-pda/src/pages/inbound/notice-hub.vue"]]);
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
      const isScannerBurst = elapsed < 400 && val.length >= 3;
      clearTimer();
      scanTimer = setTimeout(() => {
        scanTimer = null;
        if (destroyed) return;
        if (innerValue.value.trim() && isScannerBurst) {
          submitValue(innerValue.value);
        }
        firstKeyTime = 0;
      }, isScannerBurst ? 80 : 600);
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
  const _sfc_main$x = {
    __name: "ScanSearchBar",
    props: {
      modelValue: { type: String, default: "" },
      placeholder: { type: String, default: "扫码或搜索单号/供应商" },
      disabled: { type: Boolean, default: false },
      autoFocus: { type: Boolean, default: true }
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
        emit("search", val);
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
  function _sfc_render$w(_ctx, _cache, $props, $setup, $data, $options) {
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
        vue.createElementVNode("text", {
          class: "search-btn",
          onClick: vue.withModifiers($setup.onConfirm, ["stop"])
        }, "搜索")
      ],
      2
      /* CLASS */
    );
  }
  const ScanSearchBar = /* @__PURE__ */ _export_sfc(_sfc_main$x, [["render", _sfc_render$w], ["__scopeId", "data-v-405cb19b"], ["__file", "D:/AAA/WMS/wms-pda/src/components/ScanSearchBar.vue"]]);
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
  function updateReceiveLineQty(billNo, lineNo, qty) {
    return request({
      url: `/mobile/receive-notice/${billNo}/lines/${lineNo}/qty`,
      method: "PUT",
      data: { qty }
    });
  }
  function submitReceiveInbound(billNo, data = {}) {
    return request({
      url: `/mobile/receive-notice/${billNo}/submit`,
      method: "POST",
      data: withDevice(data)
    });
  }
  const PAGE_SIZE = 50;
  const LIST_CACHE_TTL_MS = 2e4;
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
      if (s === "COMPLETED") return "已完成";
      if (s === "PARTIAL_SUBMITTED") return "部分入库";
      if (s === "SCANNING") return "扫码中";
      if (item.inProgress) return "进行中";
      return "待收料";
    }
    function statusClass(item) {
      const s = item.scanStatus;
      if (s === "COMPLETED") return "done";
      if (s === "PARTIAL_SUBMITTED" || s === "SCANNING") return "progress";
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
  const _sfc_main$w = {
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
  function _sfc_render$v(_ctx, _cache, $props, $setup, $data, $options) {
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
                    item.erpBillNo ? (vue.openBlock(), vue.createElementBlock(
                      "text",
                      {
                        key: 2,
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
  const PagesInboundReceiveList = /* @__PURE__ */ _export_sfc(_sfc_main$w, [["render", _sfc_render$v], ["__scopeId", "data-v-2d7ea4ce"], ["__file", "D:/AAA/WMS/wms-pda/src/pages/inbound/receive-list.vue"]]);
  const _sfc_main$v = {
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
  function _sfc_render$u(_ctx, _cache, $props, $setup, $data, $options) {
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
  const CompactScanBox = /* @__PURE__ */ _export_sfc(_sfc_main$v, [["render", _sfc_render$u], ["__scopeId", "data-v-67ede7a2"], ["__file", "D:/AAA/WMS/wms-pda/src/components/CompactScanBox.vue"]]);
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
  const _sfc_main$u = {
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
  function _sfc_render$t(_ctx, _cache, $props, $setup, $data, $options) {
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
  const WarehousePicker = /* @__PURE__ */ _export_sfc(_sfc_main$u, [["render", _sfc_render$t], ["__scopeId", "data-v-4644220b"], ["__file", "D:/AAA/WMS/wms-pda/src/components/WarehousePicker.vue"]]);
  function isNoticeBillCompleted(detail) {
    if (!detail) return false;
    if (detail.scanStatus === "COMPLETED") return true;
    const rows = detail.lines || [];
    if (!rows.length) return false;
    return rows.every((line) => {
      const submitted = Number(line.submittedQty) || 0;
      const plan = Number(line.planQty) || 0;
      return plan > 0 && submitted >= plan;
    });
  }
  function useReceiveNoticeScan(billNo) {
    const loading = vue.ref(false);
    const submitting = vue.ref(false);
    const detail = vue.ref(null);
    const lines = vue.ref([]);
    const lastHighlightLineNo = vue.ref(null);
    const checkedCount = vue.computed(() => lines.value.filter((l) => l.checked).length);
    const submitableCount = vue.computed(
      () => lines.value.filter((l) => l.checked && (Number(l.pendingSubmitQty) || 0) > 0).length
    );
    async function loadDetail() {
      if (!billNo.value) return null;
      loading.value = true;
      try {
        const data = await getReceiveNoticeDetail(billNo.value);
        detail.value = data;
        lines.value = (data.lines || []).map(normalizeLine);
        return data;
      } catch (e) {
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
        pendingSubmitQty: line.pendingSubmitQty ?? 0
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
        const qtyText = qty != null && qty !== "" ? ` ×${formatQty(qty)}` : "";
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
    async function updateQty(lineNo, qty) {
      const num = Number(qty);
      if (Number.isNaN(num) || num < 0) {
        uni.showToast({ title: "请输入有效数量", icon: "none" });
        return false;
      }
      try {
        const line = await updateReceiveLineQty(billNo.value, lineNo, num);
        mergeLine(line);
        return true;
      } catch (e) {
        uni.showToast({ title: (e == null ? void 0 : e.message) || "更新数量失败", icon: "none" });
        return false;
      }
    }
    function formatQty(val) {
      if (val == null || val === "") return "0";
      const n = Number(val);
      if (Number.isNaN(n)) return String(val);
      return Number.isInteger(n) ? String(n) : String(n);
    }
    function formatSubmitError(e) {
      var _a, _b;
      const type = ((_a = e == null ? void 0 : e.data) == null ? void 0 : _a.errorType) || (e == null ? void 0 : e.errorType);
      const msg = (e == null ? void 0 : e.message) || ((_b = e == null ? void 0 : e.data) == null ? void 0 : _b.message);
      if (type === "ERP_SYNC_FAILED" || type === "ERP_IN_STOCK_QTY_EXCEEDED") {
        return msg || "金蝶同步失败，数量未变更";
      }
      return msg || "提交失败";
    }
    async function submit(getWarehousePayload) {
      var _a, _b, _c;
      if (!submitableCount.value && !checkedCount.value) {
        uni.showToast({ title: "请先扫描勾选物料", icon: "none" });
        return false;
      }
      submitting.value = true;
      try {
        const wh = typeof getWarehousePayload === "function" ? getWarehousePayload() : {};
        const manual = (wh == null ? void 0 : wh.autoAssignWarehouse) === false;
        const result = await submitReceiveInbound(billNo.value, {
          supplierCode: (_a = detail.value) == null ? void 0 : _a.supplierCode,
          supplierName: (_b = detail.value) == null ? void 0 : _b.supplierName,
          autoAssignWarehouse: !manual,
          warehouseCode: manual ? wh == null ? void 0 : wh.warehouseCode : void 0,
          erpWarehouseCode: manual ? (wh == null ? void 0 : wh.erpWarehouseCode) || (wh == null ? void 0 : wh.warehouseCode) : (_c = detail.value) == null ? void 0 : _c.erpWarehouseCode
        });
        if ((result == null ? void 0 : result.erpSyncStatus) && result.erpSyncStatus !== "SUCCESS" && result.erpSyncStatus !== "PENDING") {
          uni.showToast({
            title: result.erpSyncMessage || "金蝶同步失败，数量未变更",
            icon: "none",
            duration: 3500
          });
          return false;
        }
        uni.showToast({
          title: (result == null ? void 0 : result.erpBillNo) ? `已同步 ${result.erpBillNo}` : (result == null ? void 0 : result.message) || `已提交 ${result.lineCount || 0} 项`,
          icon: "success"
        });
        await loadDetail();
        if (isNoticeBillCompleted(detail.value)) {
          setTimeout(() => uni.navigateBack(), 600);
        }
        return true;
      } catch (e) {
        uni.showToast({ title: formatSubmitError(e), icon: "none", duration: 3500 });
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
      formatQty,
      submit,
      rowClass
    };
  }
  const _sfc_main$t = {
    __name: "receive-scan",
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
        formatQty,
        submit,
        rowClass
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
          qtyDrafts[line.lineNo] = formatQty(line.pendingSubmitQty || 0);
        });
      }
      function getQtyDraft(line) {
        if (qtyDrafts[line.lineNo] == null) {
          qtyDrafts[line.lineNo] = formatQty(line.pendingSubmitQty || 0);
        }
        return qtyDrafts[line.lineNo];
      }
      function onQtyInput(line, e) {
        qtyDrafts[line.lineNo] = e.detail.value;
      }
      async function onQtyBlur(line) {
        const raw = qtyDrafts[line.lineNo];
        const num = raw === "" || raw == null ? 0 : Number(raw);
        if (Number.isNaN(num) || num < 0) {
          qtyDrafts[line.lineNo] = formatQty(line.pendingSubmitQty || 0);
          return;
        }
        const current = Number(line.pendingSubmitQty) || 0;
        if (num === current) return;
        updatingLineNo.value = line.lineNo;
        const ok = await updateQty(line.lineNo, num);
        updatingLineNo.value = null;
        if (ok) {
          const updated = lines.value.find((l) => l.lineNo === line.lineNo);
          if (updated) qtyDrafts[line.lineNo] = formatQty(updated.pendingSubmitQty || 0);
        } else {
          qtyDrafts[line.lineNo] = formatQty(line.pendingSubmitQty || 0);
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
        uni.setNavigationBarTitle({ title: "物料扫描" });
      });
      onShow(async () => {
        if (billNo.value) {
          await loadDetail();
          syncQtyDrafts();
        }
        refocusScanInput(scanInputRef, 400);
      });
      const __returned__ = { billNo, scanInputRef, warehousePickerRef, warehousePayload, qtyDrafts, updatingLineNo, alive, refocusScanInput, loading, submitting, detail, lines, checkedCount, submitableCount, loadDetail, handleScan, toggleCheck, updateQty, formatQty, submit, rowClass, partialCount, suggestWarehouseCode, onWarehouseChange, isDoneLine, isPartialLine, syncQtyDrafts, getQtyDraft, onQtyInput, onQtyBlur, onScan, onToggle, onRowTap, onSubmit, ref: vue.ref, computed: vue.computed, reactive: vue.reactive, get onLoad() {
        return onLoad;
      }, get onShow() {
        return onShow;
      }, CompactScanBox, WarehousePicker, get useReceiveNoticeScan() {
        return useReceiveNoticeScan;
      }, get usePageAlive() {
        return usePageAlive;
      } };
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  };
  function _sfc_render$s(_ctx, _cache, $props, $setup, $data, $options) {
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
                      vue.toDisplayString($setup.formatQty(line.planQty)),
                      1
                      /* TEXT */
                    )
                  ]),
                  vue.createElementVNode("view", { class: "qty-cell" }, [
                    vue.createElementVNode("text", { class: "qty-label" }, "已领"),
                    vue.createElementVNode(
                      "text",
                      { class: "qty-value submitted" },
                      vue.toDisplayString($setup.formatQty(line.submittedQty)),
                      1
                      /* TEXT */
                    )
                  ]),
                  vue.createElementVNode("view", { class: "qty-cell" }, [
                    vue.createElementVNode("text", { class: "qty-label" }, "可领"),
                    vue.createElementVNode(
                      "text",
                      { class: "qty-value remain" },
                      vue.toDisplayString($setup.formatQty(line.remainQty)),
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
        }, " 提交入库" + vue.toDisplayString($setup.submitableCount ? ` (${$setup.submitableCount})` : ""), 9, ["loading", "disabled"])
      ])
    ]);
  }
  const PagesInboundReceiveScan = /* @__PURE__ */ _export_sfc(_sfc_main$t, [["render", _sfc_render$s], ["__scopeId", "data-v-15e915fb"], ["__file", "D:/AAA/WMS/wms-pda/src/pages/inbound/receive-scan.vue"]]);
  const _sfc_main$s = {
    __name: "notice-hub",
    setup(__props, { expose: __expose }) {
      __expose();
      const types = vue.ref(listNoticeBillTypes("OUTBOUND"));
      function hubDesc(item) {
        if (item.code === "PRODUCTION_ISSUE") return "扫用料清单 · 核对物料 · 同步领料单";
        if (item.code === "OUTSOURCE_ISSUE") return "扫委外用料清单 · 同步委外领料单";
        return "扫码 · 勾选 · 分批出库";
      }
      function openType(item) {
        if (item.code === "PRODUCTION_ISSUE") {
          uni.navigateTo({ url: "/pages/picking/production-issue" });
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
  function _sfc_render$r(_ctx, _cache, $props, $setup, $data, $options) {
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
  const PagesOutboundNoticeHub = /* @__PURE__ */ _export_sfc(_sfc_main$s, [["render", _sfc_render$r], ["__scopeId", "data-v-1ef9fa85"], ["__file", "D:/AAA/WMS/wms-pda/src/pages/outbound/notice-hub.vue"]]);
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
  function updateNoticeLineQty(billType, billNo, lineNo, qty) {
    return request({
      url: `${baseUrl(billType)}/${billNo}/lines/${lineNo}/qty`,
      method: "PUT",
      data: { qty }
    });
  }
  function submitNoticeBill(billType, billNo, data = {}) {
    return request({
      url: `${baseUrl(billType)}/${billNo}/submit`,
      method: "POST",
      data: withDevice(data)
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
        cacheSet(cacheKey, notices.value, LIST_CACHE_TTL_MS);
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
      if (s === "COMPLETED") return inbound ? "已完成" : "已出完";
      if (s === "PARTIAL_SUBMITTED") return inbound ? "部分入库" : "部分出库";
      if (s === "SCANNING") return "扫码中";
      if (item.inProgress) return "进行中";
      if (s === "NEW" || !s) return inbound ? "未扫码" : "待出库";
      return inbound ? "待收料" : "待出库";
    }
    function statusClass(item) {
      const s = item.scanStatus;
      if (s === "COMPLETED") return "done";
      if (s === "PARTIAL_SUBMITTED" || s === "SCANNING") return "progress";
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
  const _sfc_main$r = {
    __name: "list",
    setup(__props, { expose: __expose }) {
      __expose();
      const billType = vue.ref("PURCHASE_RECEIVE");
      const direction = vue.ref("INBOUND");
      const scanInputRef = vue.ref(null);
      const { alive, refocusScanInput } = usePageAlive();
      const {
        loading,
        keyword,
        notices,
        typeConfig,
        loadList,
        loadListOnShow,
        searchByBarcode,
        statusLabel,
        statusClass
      } = useNoticeBillList(billType);
      async function onScan(barcode) {
        if (!alive.value) return;
        await searchByBarcode(barcode);
        refocusScanInput(scanInputRef, 300);
      }
      function onSearch(val) {
        keyword.value = val || keyword.value;
        loadList(keyword.value);
      }
      function openBill(item) {
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
      const __returned__ = { billType, direction, scanInputRef, alive, refocusScanInput, loading, keyword, notices, typeConfig, loadList, loadListOnShow, searchByBarcode, statusLabel, statusClass, onScan, onSearch, openBill, ref: vue.ref, get onLoad() {
        return onLoad;
      }, get onShow() {
        return onShow;
      }, ScanSearchBar, get useNoticeBillList() {
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
  function _sfc_render$q(_ctx, _cache, $props, $setup, $data, $options) {
    return vue.openBlock(), vue.createElementBlock("view", { class: "page" }, [
      vue.createElementVNode("view", { class: "search-top" }, [
        vue.createVNode($setup["ScanSearchBar"], {
          ref: "scanInputRef",
          modelValue: $setup.keyword,
          "onUpdate:modelValue": _cache[0] || (_cache[0] = ($event) => $setup.keyword = $event),
          disabled: $setup.loading,
          placeholder: $setup.typeConfig.searchPlaceholder,
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
          )
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
  const PagesNoticeList = /* @__PURE__ */ _export_sfc(_sfc_main$r, [["render", _sfc_render$q], ["__scopeId", "data-v-0d535122"], ["__file", "D:/AAA/WMS/wms-pda/src/pages/notice/list.vue"]]);
  function useNoticeBillScan(billTypeRef, billNoRef) {
    const loading = vue.ref(false);
    const submitting = vue.ref(false);
    const detail = vue.ref(null);
    const lines = vue.ref([]);
    const lastHighlightLineNo = vue.ref(null);
    const typeConfig = vue.computed(() => getNoticeBillType(billTypeRef.value));
    const isInbound = vue.computed(() => typeConfig.value.direction === "INBOUND");
    const checkedCount = vue.computed(() => lines.value.filter((l) => l.checked).length);
    const submitableCount = vue.computed(
      () => lines.value.filter((l) => l.checked && (Number(l.pendingSubmitQty) || 0) > 0).length
    );
    async function loadDetail() {
      if (!billTypeRef.value || !billNoRef.value) return null;
      loading.value = true;
      try {
        const data = await getNoticeBillDetail(billTypeRef.value, billNoRef.value);
        detail.value = data;
        lines.value = (data.lines || []).map(normalizeLine);
        return data;
      } catch (e) {
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
        pendingSubmitQty: line.pendingSubmitQty ?? 0
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
      if (!(barcode == null ? void 0 : barcode.trim()) || submitting.value) return null;
      try {
        const updated = await scanNoticeLine(billTypeRef.value, billNoRef.value, barcode.trim());
        lastHighlightLineNo.value = updated.lineNo;
        mergeLine(updated);
        uni.showToast({ title: "扫描成功", icon: "success", duration: 800 });
        return updated;
      } catch (e) {
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
    async function updateQty(lineNo, qty) {
      try {
        const updated = await updateNoticeLineQty(billTypeRef.value, billNoRef.value, lineNo, qty);
        mergeLine(updated);
        return true;
      } catch (e) {
        uni.showToast({ title: (e == null ? void 0 : e.message) || "更新数量失败", icon: "none" });
        return false;
      }
    }
    function formatSubmitError(e) {
      var _a, _b;
      const type = ((_a = e == null ? void 0 : e.data) == null ? void 0 : _a.errorType) || (e == null ? void 0 : e.errorType);
      const msg = (e == null ? void 0 : e.message) || ((_b = e == null ? void 0 : e.data) == null ? void 0 : _b.message);
      if (type === "ERP_SYNC_FAILED" || type === "ERP_IN_STOCK_QTY_EXCEEDED") {
        return msg || "金蝶同步失败，数量未变更";
      }
      return msg || "提交失败";
    }
    async function submit(getWarehousePayload) {
      var _a, _b, _c;
      if (submitting.value || !submitableCount.value) return null;
      submitting.value = true;
      try {
        const wh = typeof getWarehousePayload === "function" ? getWarehousePayload() : {};
        const manual = isInbound.value && (wh == null ? void 0 : wh.autoAssignWarehouse) === false;
        const result = await submitNoticeBill(billTypeRef.value, billNoRef.value, {
          supplierCode: (_a = detail.value) == null ? void 0 : _a.supplierCode,
          supplierName: (_b = detail.value) == null ? void 0 : _b.supplierName,
          autoAssignWarehouse: isInbound.value ? !manual : void 0,
          warehouseCode: manual ? wh == null ? void 0 : wh.warehouseCode : void 0,
          erpWarehouseCode: manual ? (wh == null ? void 0 : wh.erpWarehouseCode) || (wh == null ? void 0 : wh.warehouseCode) : (_c = detail.value) == null ? void 0 : _c.erpWarehouseCode
        });
        if ((result == null ? void 0 : result.erpSyncStatus) && result.erpSyncStatus !== "SUCCESS" && result.erpSyncStatus !== "PENDING") {
          uni.showToast({
            title: result.erpSyncMessage || "金蝶同步失败，数量未变更",
            icon: "none",
            duration: 3500
          });
          return null;
        }
        uni.showToast({
          title: (result == null ? void 0 : result.message) || (isInbound.value ? "提交入库成功" : "提交出库成功"),
          icon: "success"
        });
        await loadDetail();
        if (isNoticeBillCompleted(detail.value)) {
          setTimeout(() => uni.navigateBack(), 600);
        }
        return result;
      } catch (e) {
        uni.showToast({ title: formatSubmitError(e), icon: "none", duration: 3500 });
        return null;
      } finally {
        submitting.value = false;
      }
    }
    function formatQty(v) {
      const n = Number(v);
      if (Number.isNaN(n)) return "0";
      return Number.isInteger(n) ? String(n) : n.toFixed(2).replace(/\.?0+$/, "");
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
      formatQty,
      submit,
      rowClass,
      lastHighlightLineNo
    };
  }
  const _sfc_main$q = {
    __name: "scan",
    setup(__props, { expose: __expose }) {
      __expose();
      const billType = vue.ref("PURCHASE_RECEIVE");
      const billNo = vue.ref("");
      const scanInputRef = vue.ref(null);
      const warehousePickerRef = vue.ref(null);
      const warehousePayload = vue.ref({ autoAssignWarehouse: true });
      const qtyDrafts = vue.reactive({});
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
        formatQty,
        submit,
        rowClass
      } = useNoticeBillScan(billType, billNo);
      const submitLabel = vue.computed(() => isInbound.value ? "提交入库" : "提交出库");
      const suggestWarehouseCode = vue.computed(() => {
        var _a, _b, _c;
        if (!isInbound.value) return ((_a = detail.value) == null ? void 0 : _a.warehouseCode) || "";
        const pending = lines.value.find((l) => l.checked && (Number(l.pendingSubmitQty) || 0) > 0);
        if (pending == null ? void 0 : pending.erpStockCode) return pending.erpStockCode;
        return ((_b = detail.value) == null ? void 0 : _b.erpWarehouseCode) || ((_c = detail.value) == null ? void 0 : _c.warehouseCode) || "";
      });
      function onWarehouseChange(payload) {
        warehousePayload.value = payload || { autoAssignWarehouse: true };
      }
      function isDoneLine(line) {
        const submitted = Number(line.submittedQty) || 0;
        const plan = Number(line.planQty) || 0;
        return plan > 0 && submitted >= plan;
      }
      function syncQtyDrafts() {
        lines.value.forEach((line) => {
          qtyDrafts[line.lineNo] = formatQty(line.pendingSubmitQty || 0);
        });
      }
      function getQtyDraft(line) {
        if (qtyDrafts[line.lineNo] == null) qtyDrafts[line.lineNo] = formatQty(line.pendingSubmitQty || 0);
        return qtyDrafts[line.lineNo];
      }
      function onQtyInput(line, e) {
        qtyDrafts[line.lineNo] = e.detail.value;
      }
      async function onQtyBlur(line) {
        const num = Number(qtyDrafts[line.lineNo] || 0);
        if (Number.isNaN(num) || num < 0) return;
        const ok = await updateQty(line.lineNo, num);
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
      const __returned__ = { billType, billNo, scanInputRef, warehousePickerRef, warehousePayload, qtyDrafts, alive, refocusScanInput, loading, submitting, detail, lines, isInbound, checkedCount, submitableCount, loadDetail, handleScan, toggleCheck, updateQty, formatQty, submit, rowClass, submitLabel, suggestWarehouseCode, onWarehouseChange, isDoneLine, syncQtyDrafts, getQtyDraft, onQtyInput, onQtyBlur, onScan, onToggle, onRowTap, onSubmit, ref: vue.ref, computed: vue.computed, reactive: vue.reactive, get onLoad() {
        return onLoad;
      }, get onShow() {
        return onShow;
      }, CompactScanBox, WarehousePicker, get useNoticeBillScan() {
        return useNoticeBillScan;
      }, get usePageAlive() {
        return usePageAlive;
      }, get getNoticeBillType() {
        return getNoticeBillType;
      } };
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  };
  function _sfc_render$p(_ctx, _cache, $props, $setup, $data, $options) {
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
                      vue.toDisplayString($setup.formatQty(line.planQty)),
                      1
                      /* TEXT */
                    )
                  ]),
                  vue.createElementVNode("view", { class: "qty-cell" }, [
                    vue.createElementVNode("text", { class: "qty-label" }, "已处理"),
                    vue.createElementVNode(
                      "text",
                      { class: "qty-value submitted" },
                      vue.toDisplayString($setup.formatQty(line.submittedQty)),
                      1
                      /* TEXT */
                    )
                  ]),
                  vue.createElementVNode("view", { class: "qty-cell" }, [
                    vue.createElementVNode("text", { class: "qty-label" }, "可处理"),
                    vue.createElementVNode(
                      "text",
                      { class: "qty-value remain" },
                      vue.toDisplayString($setup.formatQty(line.remainQty)),
                      1
                      /* TEXT */
                    )
                  ]),
                  vue.createElementVNode("view", { class: "qty-cell" }, [
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
                  vue.createElementVNode("text", { class: "qty-edit-label" }, "本次数量"),
                  vue.createElementVNode("input", {
                    class: "qty-input",
                    type: "digit",
                    value: $setup.getQtyDraft(line),
                    onInput: ($event) => $setup.onQtyInput(line, $event),
                    onBlur: ($event) => $setup.onQtyBlur(line)
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
        }, vue.toDisplayString($setup.submitLabel) + vue.toDisplayString($setup.submitableCount ? ` (${$setup.submitableCount})` : ""), 9, ["loading", "disabled"])
      ])
    ]);
  }
  const PagesNoticeScan = /* @__PURE__ */ _export_sfc(_sfc_main$q, [["render", _sfc_render$p], ["__scopeId", "data-v-b33616f0"], ["__file", "D:/AAA/WMS/wms-pda/src/pages/notice/scan.vue"]]);
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
  function scanCode(title = "扫描条码") {
    return new Promise((resolve, reject) => {
      uni.scanCode({
        onlyFromCamera: false,
        success: (res) => resolve((res.result || "").trim()),
        fail: () => promptBarcode(title).then(resolve).catch(reject)
      });
    });
  }
  function promptBarcode(title) {
    return new Promise((resolve, reject) => {
      uni.showModal({
        title,
        editable: true,
        placeholderText: "请输入或粘贴条码",
        success: (res) => {
          var _a;
          if (res.confirm && ((_a = res.content) == null ? void 0 : _a.trim())) resolve(res.content.trim());
          else reject(new Error("cancel"));
        },
        fail: reject
      });
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
  async function scanAndParse(title) {
    const content = await scanCode(title);
    return resolveBarcode(content);
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
  const _sfc_main$p = {
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
  function _sfc_render$o(_ctx, _cache, $props, $setup, $data, $options) {
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
  const PagesInboundDirect = /* @__PURE__ */ _export_sfc(_sfc_main$p, [["render", _sfc_render$o], ["__scopeId", "data-v-c962e0f8"], ["__file", "D:/AAA/WMS/wms-pda/src/pages/inbound/direct.vue"]]);
  const _sfc_main$o = {
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
  function _sfc_render$n(_ctx, _cache, $props, $setup, $data, $options) {
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
  const ScanInput = /* @__PURE__ */ _export_sfc(_sfc_main$o, [["render", _sfc_render$n], ["__scopeId", "data-v-66a5eeaf"], ["__file", "D:/AAA/WMS/wms-pda/src/components/ScanInput.vue"]]);
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
  const _sfc_main$n = {
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
  function _sfc_render$m(_ctx, _cache, $props, $setup, $data, $options) {
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
  const PagesOutboundOutbound = /* @__PURE__ */ _export_sfc(_sfc_main$n, [["render", _sfc_render$m], ["__scopeId", "data-v-b3062ebb"], ["__file", "D:/AAA/WMS/wms-pda/src/pages/outbound/outbound.vue"]]);
  const _sfc_main$m = {
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
      async function search() {
        searched.value = true;
        Object.keys(summary).forEach((k) => delete summary[k]);
        if (mode.value === "barcode") {
          const res = await queryInventoryPost({
            barcode: barcode.value,
            queryType: "MATERIAL",
            warehouseCode: warehouseCode.value || void 0
          });
          Object.assign(summary, res);
          list.value = res.stocks || [];
        } else {
          const res = await queryInventoryGet({
            materialCode: materialCode.value || void 0,
            warehouseCode: warehouseCode.value || void 0
          });
          list.value = Array.isArray(res) ? res : (res == null ? void 0 : res.records) || [];
        }
      }
      async function scanSearch() {
        try {
          barcode.value = await scanCode("扫描库存条码");
          mode.value = "barcode";
          await search();
        } catch {
        }
      }
      const __returned__ = { mode, materialCode, warehouseCode, barcode, list, summary, searched, search, scanSearch, ref: vue.ref, reactive: vue.reactive, get queryInventoryGet() {
        return queryInventoryGet;
      }, get queryInventoryPost() {
        return queryInventoryPost;
      }, get scanCode() {
        return scanCode;
      } };
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  };
  function _sfc_render$l(_ctx, _cache, $props, $setup, $data, $options) {
    return vue.openBlock(), vue.createElementBlock("view", { class: "page" }, [
      vue.createElementVNode("view", { class: "tabs" }, [
        vue.createElementVNode(
          "text",
          {
            class: vue.normalizeClass(["tab", $setup.mode === "code" && "active"]),
            onClick: _cache[0] || (_cache[0] = ($event) => $setup.mode = "code")
          },
          "编码查询",
          2
          /* CLASS */
        ),
        vue.createElementVNode(
          "text",
          {
            class: vue.normalizeClass(["tab", $setup.mode === "barcode" && "active"]),
            onClick: _cache[1] || (_cache[1] = ($event) => $setup.mode = "barcode")
          },
          "扫码查询",
          2
          /* CLASS */
        )
      ]),
      vue.createElementVNode("view", { class: "search-bar" }, [
        $setup.mode === "code" ? vue.withDirectives((vue.openBlock(), vue.createElementBlock(
          "input",
          {
            key: 0,
            "onUpdate:modelValue": _cache[2] || (_cache[2] = ($event) => $setup.materialCode = $event),
            class: "input",
            placeholder: "物料编码"
          },
          null,
          512
          /* NEED_PATCH */
        )), [
          [vue.vModelText, $setup.materialCode]
        ]) : vue.createCommentVNode("v-if", true),
        vue.withDirectives(vue.createElementVNode(
          "input",
          {
            "onUpdate:modelValue": _cache[3] || (_cache[3] = ($event) => $setup.warehouseCode = $event),
            class: "input",
            placeholder: "仓库编码(可选)"
          },
          null,
          512
          /* NEED_PATCH */
        ), [
          [vue.vModelText, $setup.warehouseCode]
        ]),
        $setup.mode === "barcode" ? vue.withDirectives((vue.openBlock(), vue.createElementBlock(
          "input",
          {
            key: 1,
            "onUpdate:modelValue": _cache[4] || (_cache[4] = ($event) => $setup.barcode = $event),
            class: "input",
            placeholder: "条码内容"
          },
          null,
          512
          /* NEED_PATCH */
        )), [
          [vue.vModelText, $setup.barcode]
        ]) : vue.createCommentVNode("v-if", true),
        vue.createElementVNode("button", {
          class: "btn",
          onClick: $setup.search
        }, "查询"),
        $setup.mode === "barcode" ? (vue.openBlock(), vue.createElementBlock("button", {
          key: 2,
          class: "btn scan",
          onClick: $setup.scanSearch
        }, "扫码查询")) : vue.createCommentVNode("v-if", true)
      ]),
      $setup.summary.materialCode ? (vue.openBlock(), vue.createElementBlock("view", {
        key: 0,
        class: "summary"
      }, [
        vue.createElementVNode(
          "text",
          { class: "title" },
          vue.toDisplayString($setup.summary.materialName || $setup.summary.materialCode),
          1
          /* TEXT */
        ),
        vue.createElementVNode(
          "text",
          null,
          "总库存: " + vue.toDisplayString($setup.summary.totalStockQty) + " / 可用: " + vue.toDisplayString($setup.summary.totalAvailableQty),
          1
          /* TEXT */
        )
      ])) : vue.createCommentVNode("v-if", true),
      (vue.openBlock(true), vue.createElementBlock(
        vue.Fragment,
        null,
        vue.renderList($setup.list, (item, idx) => {
          return vue.openBlock(), vue.createElementBlock("view", {
            key: idx,
            class: "card"
          }, [
            vue.createElementVNode(
              "text",
              { class: "title" },
              vue.toDisplayString(item.materialCode || $setup.summary.materialCode),
              1
              /* TEXT */
            ),
            vue.createElementVNode(
              "text",
              null,
              "仓库: " + vue.toDisplayString(item.warehouseCode) + " · 库位: " + vue.toDisplayString(item.locationCode),
              1
              /* TEXT */
            ),
            vue.createElementVNode(
              "text",
              null,
              "批次: " + vue.toDisplayString(item.batchNo) + " · 库存: " + vue.toDisplayString(item.stockQty || item.totalStockQty),
              1
              /* TEXT */
            )
          ]);
        }),
        128
        /* KEYED_FRAGMENT */
      )),
      $setup.searched && !$setup.list.length && !$setup.summary.materialCode ? (vue.openBlock(), vue.createElementBlock("view", {
        key: 1,
        class: "empty"
      }, "无库存数据")) : vue.createCommentVNode("v-if", true)
    ]);
  }
  const PagesInventoryInventory = /* @__PURE__ */ _export_sfc(_sfc_main$m, [["render", _sfc_render$l], ["__scopeId", "data-v-ec5f5572"], ["__file", "D:/AAA/WMS/wms-pda/src/pages/inventory/inventory.vue"]]);
  const _sfc_main$l = {
    __name: "transfer",
    setup(__props, { expose: __expose }) {
      __expose();
      const form = vue.reactive({
        sourceLocation: "",
        targetLocation: "",
        materialCode: "",
        batchNo: "",
        transferQty: ""
      });
      async function scanField(field, title) {
        try {
          form[field] = await scanCode(title);
        } catch {
        }
      }
      async function scanMaterial() {
        try {
          const parsed = await scanAndParse("扫描物料条码");
          form.materialCode = parsed.materialCode || form.materialCode;
          form.batchNo = parsed.batchNo || form.batchNo;
        } catch {
        }
      }
      async function submit() {
        if (!form.sourceLocation || !form.targetLocation || !form.materialCode || !form.transferQty) {
          uni.showToast({ title: "请填写完整信息", icon: "none" });
          return;
        }
        await transferStock({
          sourceLocation: form.sourceLocation,
          targetLocation: form.targetLocation,
          materialCode: form.materialCode,
          batchNo: form.batchNo,
          transferQty: Number(form.transferQty)
        });
        uni.showToast({ title: "移库成功", icon: "success" });
      }
      const __returned__ = { form, scanField, scanMaterial, submit, reactive: vue.reactive, get transferStock() {
        return transferStock;
      }, get scanAndParse() {
        return scanAndParse;
      }, get scanCode() {
        return scanCode;
      } };
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  };
  function _sfc_render$k(_ctx, _cache, $props, $setup, $data, $options) {
    return vue.openBlock(), vue.createElementBlock("view", { class: "page" }, [
      vue.createElementVNode("view", { class: "form-card" }, [
        vue.withDirectives(vue.createElementVNode(
          "input",
          {
            "onUpdate:modelValue": _cache[0] || (_cache[0] = ($event) => $setup.form.sourceLocation = $event),
            class: "input",
            placeholder: "源库位"
          },
          null,
          512
          /* NEED_PATCH */
        ), [
          [vue.vModelText, $setup.form.sourceLocation]
        ]),
        vue.withDirectives(vue.createElementVNode(
          "input",
          {
            "onUpdate:modelValue": _cache[1] || (_cache[1] = ($event) => $setup.form.targetLocation = $event),
            class: "input",
            placeholder: "目标库位"
          },
          null,
          512
          /* NEED_PATCH */
        ), [
          [vue.vModelText, $setup.form.targetLocation]
        ]),
        vue.withDirectives(vue.createElementVNode(
          "input",
          {
            "onUpdate:modelValue": _cache[2] || (_cache[2] = ($event) => $setup.form.materialCode = $event),
            class: "input",
            placeholder: "物料编码"
          },
          null,
          512
          /* NEED_PATCH */
        ), [
          [vue.vModelText, $setup.form.materialCode]
        ]),
        vue.withDirectives(vue.createElementVNode(
          "input",
          {
            "onUpdate:modelValue": _cache[3] || (_cache[3] = ($event) => $setup.form.batchNo = $event),
            class: "input",
            placeholder: "批次号(可选)"
          },
          null,
          512
          /* NEED_PATCH */
        ), [
          [vue.vModelText, $setup.form.batchNo]
        ]),
        vue.withDirectives(vue.createElementVNode(
          "input",
          {
            "onUpdate:modelValue": _cache[4] || (_cache[4] = ($event) => $setup.form.transferQty = $event),
            class: "input",
            type: "digit",
            placeholder: "移库数量"
          },
          null,
          512
          /* NEED_PATCH */
        ), [
          [vue.vModelText, $setup.form.transferQty]
        ]),
        vue.createElementVNode("view", { class: "scan-row" }, [
          vue.createElementVNode("button", {
            size: "mini",
            onClick: _cache[5] || (_cache[5] = ($event) => $setup.scanField("sourceLocation", "源库位"))
          }, "扫源库位"),
          vue.createElementVNode("button", {
            size: "mini",
            onClick: _cache[6] || (_cache[6] = ($event) => $setup.scanField("targetLocation", "目标库位"))
          }, "扫目标库位"),
          vue.createElementVNode("button", {
            size: "mini",
            onClick: $setup.scanMaterial
          }, "扫物料码")
        ]),
        vue.createElementVNode("button", {
          class: "btn",
          type: "primary",
          onClick: $setup.submit
        }, "确认移库")
      ])
    ]);
  }
  const PagesTransferTransfer = /* @__PURE__ */ _export_sfc(_sfc_main$l, [["render", _sfc_render$k], ["__scopeId", "data-v-d303ad3d"], ["__file", "D:/AAA/WMS/wms-pda/src/pages/transfer/transfer.vue"]]);
  const _sfc_main$k = {
    __name: "stockcheck",
    setup(__props, { expose: __expose }) {
      __expose();
      const taskNo = vue.ref("");
      const task = vue.ref(null);
      const details = vue.ref([]);
      const mode = vue.ref("line");
      const scanForm = vue.reactive({
        locationCode: "",
        materialCode: "",
        batchNo: "",
        actualQty: ""
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
      onLoad((options) => {
        taskNo.value = (options == null ? void 0 : options.taskNo) || "";
        loadTask();
      });
      async function loadTask() {
        if (!taskNo.value) return;
        const data = await getStockcheckTask(taskNo.value);
        task.value = data.task;
        details.value = (data.details || []).map((d) => ({ ...d, _actual: d.actualQty ?? "" }));
      }
      async function submitLine(line) {
        if (line._actual === "" && line._actual !== 0) {
          uni.showToast({ title: "请输入实盘数量", icon: "none" });
          return;
        }
        await scanStockcheck(taskNo.value, {
          lineNo: line.lineNo,
          actualQty: Number(line._actual)
        });
        uni.showToast({ title: "已提交", icon: "success" });
        loadTask();
      }
      async function scanCount() {
        try {
          const parsed = await scanAndParse("扫描盘点条码");
          scanForm.locationCode = parsed.locationCode || scanForm.locationCode;
          scanForm.materialCode = parsed.materialCode || scanForm.materialCode;
          scanForm.batchNo = parsed.batchNo || scanForm.batchNo;
        } catch {
        }
      }
      async function submitScanForm() {
        if (!scanForm.locationCode || !scanForm.materialCode) {
          uni.showToast({ title: "请先扫码", icon: "none" });
          return;
        }
        await scanStockcheck(taskNo.value, {
          locationCode: scanForm.locationCode,
          materialCode: scanForm.materialCode,
          batchNo: scanForm.batchNo,
          actualQty: Number(scanForm.actualQty || 0)
        });
        uni.showToast({ title: "扫码盘点已提交", icon: "success" });
        loadTask();
      }
      async function submitGain() {
        if (!gainForm.locationCode || !gainForm.materialCode || !gainForm.actualQty) {
          uni.showToast({ title: "请填写盘盈信息", icon: "none" });
          return;
        }
        await gainStockcheck(taskNo.value, {
          locationCode: gainForm.locationCode,
          materialCode: gainForm.materialCode,
          batchNo: gainForm.batchNo,
          actualQty: Number(gainForm.actualQty),
          remark: gainForm.remark
        });
        uni.showToast({ title: "盘盈已录入", icon: "success" });
        loadTask();
      }
      async function submitEmpty() {
        if (!emptyForm.locationCode || !emptyForm.materialCode) {
          uni.showToast({ title: "请填写库位和物料", icon: "none" });
          return;
        }
        await confirmEmptyStockcheck(taskNo.value, {
          locationCode: emptyForm.locationCode,
          materialCode: emptyForm.materialCode,
          batchNo: emptyForm.batchNo
        });
        uni.showToast({ title: "已确认无库存", icon: "success" });
        loadTask();
      }
      async function complete() {
        await completeStockcheck(taskNo.value);
        uni.showToast({ title: "盘点完成", icon: "success" });
        setTimeout(() => uni.navigateBack(), 800);
      }
      const __returned__ = { taskNo, task, details, mode, scanForm, gainForm, emptyForm, loadTask, submitLine, scanCount, submitScanForm, submitGain, submitEmpty, complete, ref: vue.ref, reactive: vue.reactive, get onLoad() {
        return onLoad;
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
      }, get scanAndParse() {
        return scanAndParse;
      } };
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  };
  function _sfc_render$j(_ctx, _cache, $props, $setup, $data, $options) {
    return vue.openBlock(), vue.createElementBlock("view", { class: "page" }, [
      $setup.task ? (vue.openBlock(), vue.createElementBlock("view", {
        key: 0,
        class: "header"
      }, [
        vue.createElementVNode(
          "text",
          null,
          "任务: " + vue.toDisplayString($setup.task.taskNo),
          1
          /* TEXT */
        ),
        vue.createElementVNode(
          "text",
          null,
          "仓库: " + vue.toDisplayString($setup.task.warehouseCode) + " · " + vue.toDisplayString($setup.task.status),
          1
          /* TEXT */
        )
      ])) : vue.createCommentVNode("v-if", true),
      vue.createElementVNode("view", { class: "tabs" }, [
        vue.createElementVNode(
          "text",
          {
            class: vue.normalizeClass(["tab", $setup.mode === "line" && "active"]),
            onClick: _cache[0] || (_cache[0] = ($event) => $setup.mode = "line")
          },
          "行录入",
          2
          /* CLASS */
        ),
        vue.createElementVNode(
          "text",
          {
            class: vue.normalizeClass(["tab", $setup.mode === "scan" && "active"]),
            onClick: _cache[1] || (_cache[1] = ($event) => $setup.mode = "scan")
          },
          "扫码盘点",
          2
          /* CLASS */
        ),
        vue.createElementVNode(
          "text",
          {
            class: vue.normalizeClass(["tab", $setup.mode === "gain" && "active"]),
            onClick: _cache[2] || (_cache[2] = ($event) => $setup.mode = "gain")
          },
          "盘盈",
          2
          /* CLASS */
        ),
        vue.createElementVNode(
          "text",
          {
            class: vue.normalizeClass(["tab", $setup.mode === "empty" && "active"]),
            onClick: _cache[3] || (_cache[3] = ($event) => $setup.mode = "empty")
          },
          "确认空",
          2
          /* CLASS */
        )
      ]),
      $setup.mode === "line" ? (vue.openBlock(), vue.createElementBlock("view", { key: 1 }, [
        (vue.openBlock(true), vue.createElementBlock(
          vue.Fragment,
          null,
          vue.renderList($setup.details, (line) => {
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
                "库位: " + vue.toDisplayString(line.locationCode) + " · 账面: " + vue.toDisplayString(line.bookQty),
                1
                /* TEXT */
              ),
              vue.createElementVNode("view", { class: "row" }, [
                vue.withDirectives(vue.createElementVNode("input", {
                  "onUpdate:modelValue": ($event) => line._actual = $event,
                  class: "qty-input",
                  type: "digit",
                  placeholder: "实盘数量"
                }, null, 8, ["onUpdate:modelValue"]), [
                  [vue.vModelText, line._actual]
                ]),
                vue.createElementVNode("button", {
                  size: "mini",
                  type: "primary",
                  onClick: ($event) => $setup.submitLine(line)
                }, "提交", 8, ["onClick"])
              ])
            ]);
          }),
          128
          /* KEYED_FRAGMENT */
        ))
      ])) : $setup.mode === "scan" ? (vue.openBlock(), vue.createElementBlock("view", {
        key: 2,
        class: "form-card"
      }, [
        vue.createElementVNode("button", {
          type: "primary",
          onClick: $setup.scanCount
        }, "扫描库位/物料码盘点"),
        vue.withDirectives(vue.createElementVNode(
          "input",
          {
            "onUpdate:modelValue": _cache[4] || (_cache[4] = ($event) => $setup.scanForm.actualQty = $event),
            class: "input",
            type: "digit",
            placeholder: "实盘数量"
          },
          null,
          512
          /* NEED_PATCH */
        ), [
          [vue.vModelText, $setup.scanForm.actualQty]
        ]),
        vue.createElementVNode("button", { onClick: $setup.submitScanForm }, "提交扫码盘点")
      ])) : $setup.mode === "gain" ? (vue.openBlock(), vue.createElementBlock("view", {
        key: 3,
        class: "form-card"
      }, [
        vue.withDirectives(vue.createElementVNode(
          "input",
          {
            "onUpdate:modelValue": _cache[5] || (_cache[5] = ($event) => $setup.gainForm.locationCode = $event),
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
            "onUpdate:modelValue": _cache[6] || (_cache[6] = ($event) => $setup.gainForm.materialCode = $event),
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
            "onUpdate:modelValue": _cache[7] || (_cache[7] = ($event) => $setup.gainForm.batchNo = $event),
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
            "onUpdate:modelValue": _cache[8] || (_cache[8] = ($event) => $setup.gainForm.actualQty = $event),
            class: "input",
            type: "digit",
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
            "onUpdate:modelValue": _cache[9] || (_cache[9] = ($event) => $setup.gainForm.remark = $event),
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
          type: "primary",
          onClick: $setup.submitGain
        }, "提交盘盈")
      ])) : (vue.openBlock(), vue.createElementBlock("view", {
        key: 4,
        class: "form-card"
      }, [
        vue.withDirectives(vue.createElementVNode(
          "input",
          {
            "onUpdate:modelValue": _cache[10] || (_cache[10] = ($event) => $setup.emptyForm.locationCode = $event),
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
            "onUpdate:modelValue": _cache[11] || (_cache[11] = ($event) => $setup.emptyForm.materialCode = $event),
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
            "onUpdate:modelValue": _cache[12] || (_cache[12] = ($event) => $setup.emptyForm.batchNo = $event),
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
          type: "warn",
          onClick: $setup.submitEmpty
        }, "确认无库存")
      ])),
      vue.createElementVNode("button", {
        class: "complete-btn",
        type: "warn",
        onClick: $setup.complete
      }, "完成盘点")
    ]);
  }
  const PagesStockcheckStockcheck = /* @__PURE__ */ _export_sfc(_sfc_main$k, [["render", _sfc_render$j], ["__scopeId", "data-v-dc8a8113"], ["__file", "D:/AAA/WMS/wms-pda/src/pages/stockcheck/stockcheck.vue"]]);
  const _sfc_main$j = {
    __name: "tasklist",
    setup(__props, { expose: __expose }) {
      __expose();
      const moduleType = vue.ref("stockcheck");
      const tasks = vue.ref({});
      const loaded = vue.ref(false);
      const moduleConfig = vue.computed(() => {
        const map = {
          stockcheck: { label: "盘点", title: "盘点任务", icon: "📋" },
          qc: { label: "质检", title: "质检任务", icon: "✅" }
        };
        return map[moduleType.value] || map.stockcheck;
      });
      const taskList = vue.computed(() => {
        var _a;
        const raw = ((_a = tasks.value[moduleType.value]) == null ? void 0 : _a.tasks) || [];
        return raw.map((item) => {
          if (moduleType.value === "stockcheck") {
            return {
              ...item,
              _key: item.taskNo,
              _title: item.taskNo,
              _meta: `${item.warehouseCode || "-"} · ${item.status || "-"}`
            };
          }
          return {
            ...item,
            _key: item.qcNo,
            _title: item.qcNo,
            _meta: `${item.materialCode || "-"} · ${item.status || "-"}`
          };
        });
      });
      onLoad((options) => {
        moduleType.value = (options == null ? void 0 : options.type) || "stockcheck";
        uni.setNavigationBarTitle({ title: moduleConfig.value.title });
        loadData();
      });
      onShow(loadData);
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
        if (moduleType.value === "stockcheck") {
          uni.navigateTo({ url: `/pages/stockcheck/stockcheck?taskNo=${item.taskNo}` });
        } else {
          uni.navigateTo({ url: `/pages/qc/qc?qcNo=${item.qcNo}` });
        }
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
  function _sfc_render$i(_ctx, _cache, $props, $setup, $data, $options) {
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
  const PagesTasklistTasklist = /* @__PURE__ */ _export_sfc(_sfc_main$j, [["render", _sfc_render$i], ["__scopeId", "data-v-8f3ed671"], ["__file", "D:/AAA/WMS/wms-pda/src/pages/tasklist/tasklist.vue"]]);
  const _sfc_main$i = {
    __name: "panel",
    setup(__props, { expose: __expose }) {
      __expose();
      const form = vue.reactive({
        panelCode: "",
        materialCode: "",
        batchNo: "",
        warehouseCode: ""
      });
      const loading = vue.ref(false);
      const result = vue.ref(null);
      const history = vue.ref(uni.getStorageSync("panel_verify_history") || []);
      async function scanPanel() {
        try {
          form.panelCode = await scanCode("扫描板码");
        } catch {
        }
      }
      async function doVerify() {
        var _a;
        if (!((_a = form.panelCode) == null ? void 0 : _a.trim())) {
          uni.showToast({ title: "请输入板码", icon: "none" });
          return;
        }
        loading.value = true;
        try {
          const data = await verifyPanelCode({
            panelCode: form.panelCode.trim(),
            materialCode: form.materialCode || void 0,
            batchNo: form.batchNo || void 0,
            warehouseCode: form.warehouseCode || void 0
          });
          result.value = data;
          const record = {
            panelCode: data.panelCode,
            valid: data.valid,
            verifyTime: data.verifyTime
          };
          history.value = [record, ...history.value.filter((h) => h.panelCode !== record.panelCode)].slice(0, 10);
          uni.setStorageSync("panel_verify_history", history.value);
          uni.showToast({
            title: data.valid ? "校验通过" : "校验失败",
            icon: data.valid ? "success" : "none"
          });
        } finally {
          loading.value = false;
        }
      }
      const __returned__ = { form, loading, result, history, scanPanel, doVerify, ref: vue.ref, reactive: vue.reactive, get verifyPanelCode() {
        return verifyPanelCode;
      }, get scanCode() {
        return scanCode;
      } };
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  };
  function _sfc_render$h(_ctx, _cache, $props, $setup, $data, $options) {
    var _a, _b;
    return vue.openBlock(), vue.createElementBlock("view", { class: "page" }, [
      vue.createElementVNode("view", { class: "form-card" }, [
        vue.createElementVNode("text", { class: "label" }, "板码"),
        vue.withDirectives(vue.createElementVNode(
          "input",
          {
            "onUpdate:modelValue": _cache[0] || (_cache[0] = ($event) => $setup.form.panelCode = $event),
            class: "input",
            placeholder: "扫描或输入板码 (PLT/BM/P开头)"
          },
          null,
          512
          /* NEED_PATCH */
        ), [
          [vue.vModelText, $setup.form.panelCode]
        ]),
        vue.createElementVNode("button", {
          size: "mini",
          class: "scan-btn",
          onClick: $setup.scanPanel
        }, "扫码输入"),
        vue.createElementVNode("text", { class: "label" }, "物料编码（可选，交叉校验）"),
        vue.withDirectives(vue.createElementVNode(
          "input",
          {
            "onUpdate:modelValue": _cache[1] || (_cache[1] = ($event) => $setup.form.materialCode = $event),
            class: "input",
            placeholder: "物料编码"
          },
          null,
          512
          /* NEED_PATCH */
        ), [
          [vue.vModelText, $setup.form.materialCode]
        ]),
        vue.createElementVNode("text", { class: "label" }, "批次号（可选）"),
        vue.withDirectives(vue.createElementVNode(
          "input",
          {
            "onUpdate:modelValue": _cache[2] || (_cache[2] = ($event) => $setup.form.batchNo = $event),
            class: "input",
            placeholder: "批次号"
          },
          null,
          512
          /* NEED_PATCH */
        ), [
          [vue.vModelText, $setup.form.batchNo]
        ]),
        vue.createElementVNode("text", { class: "label" }, "仓库（可选）"),
        vue.withDirectives(vue.createElementVNode(
          "input",
          {
            "onUpdate:modelValue": _cache[3] || (_cache[3] = ($event) => $setup.form.warehouseCode = $event),
            class: "input",
            placeholder: "WH01"
          },
          null,
          512
          /* NEED_PATCH */
        ), [
          [vue.vModelText, $setup.form.warehouseCode]
        ]),
        vue.createElementVNode("button", {
          class: "verify-btn",
          type: "primary",
          loading: $setup.loading,
          onClick: $setup.doVerify
        }, "开始校验", 8, ["loading"])
      ]),
      $setup.result ? (vue.openBlock(), vue.createElementBlock(
        "view",
        {
          key: 0,
          class: vue.normalizeClass(["result-card", $setup.result.valid ? "pass" : "fail"])
        },
        [
          vue.createElementVNode(
            "text",
            { class: "result-icon" },
            vue.toDisplayString($setup.result.valid ? "✅" : "❌"),
            1
            /* TEXT */
          ),
          vue.createElementVNode(
            "text",
            { class: "result-title" },
            vue.toDisplayString($setup.result.valid ? "校验通过" : "校验失败"),
            1
            /* TEXT */
          ),
          vue.createElementVNode(
            "text",
            { class: "result-msg" },
            vue.toDisplayString($setup.result.message),
            1
            /* TEXT */
          ),
          vue.createElementVNode("view", { class: "result-detail" }, [
            vue.createElementVNode(
              "text",
              null,
              "板码: " + vue.toDisplayString($setup.result.panelCode),
              1
              /* TEXT */
            ),
            $setup.result.materialName ? (vue.openBlock(), vue.createElementBlock(
              "text",
              { key: 0 },
              "物料: " + vue.toDisplayString($setup.result.materialName) + " (" + vue.toDisplayString($setup.result.parsedMaterialCode) + ")",
              1
              /* TEXT */
            )) : $setup.result.parsedMaterialCode ? (vue.openBlock(), vue.createElementBlock(
              "text",
              { key: 1 },
              "解析物料: " + vue.toDisplayString($setup.result.parsedMaterialCode),
              1
              /* TEXT */
            )) : vue.createCommentVNode("v-if", true),
            $setup.result.parsedBatchNo ? (vue.openBlock(), vue.createElementBlock(
              "text",
              { key: 2 },
              "解析批次: " + vue.toDisplayString($setup.result.parsedBatchNo),
              1
              /* TEXT */
            )) : vue.createCommentVNode("v-if", true),
            $setup.result.totalStockQty != null ? (vue.openBlock(), vue.createElementBlock(
              "text",
              { key: 3 },
              "库存合计: " + vue.toDisplayString($setup.result.totalStockQty),
              1
              /* TEXT */
            )) : vue.createCommentVNode("v-if", true),
            vue.createElementVNode(
              "text",
              { class: "time" },
              "校验时间: " + vue.toDisplayString($setup.result.verifyTime),
              1
              /* TEXT */
            )
          ])
        ],
        2
        /* CLASS */
      )) : vue.createCommentVNode("v-if", true),
      ((_b = (_a = $setup.result) == null ? void 0 : _a.stocks) == null ? void 0 : _b.length) ? (vue.openBlock(), vue.createElementBlock("view", {
        key: 1,
        class: "stock-section"
      }, [
        vue.createElementVNode("text", { class: "section-title" }, "匹配库存"),
        (vue.openBlock(true), vue.createElementBlock(
          vue.Fragment,
          null,
          vue.renderList($setup.result.stocks, (s, i) => {
            return vue.openBlock(), vue.createElementBlock("view", {
              key: i,
              class: "stock-card"
            }, [
              vue.createElementVNode(
                "text",
                null,
                vue.toDisplayString(s.materialCode) + " · " + vue.toDisplayString(s.batchNo || "-"),
                1
                /* TEXT */
              ),
              vue.createElementVNode(
                "text",
                null,
                "仓库 " + vue.toDisplayString(s.warehouseCode) + " · 库位 " + vue.toDisplayString(s.locationCode),
                1
                /* TEXT */
              ),
              vue.createElementVNode(
                "text",
                null,
                "库存 " + vue.toDisplayString(s.stockQty) + " / 可用 " + vue.toDisplayString(s.availableQty),
                1
                /* TEXT */
              )
            ]);
          }),
          128
          /* KEYED_FRAGMENT */
        ))
      ])) : vue.createCommentVNode("v-if", true),
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
                vue.toDisplayString(h.panelCode),
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
  const PagesPanelPanel = /* @__PURE__ */ _export_sfc(_sfc_main$i, [["render", _sfc_render$h], ["__scopeId", "data-v-c1760e80"], ["__file", "D:/AAA/WMS/wms-pda/src/pages/panel/panel.vue"]]);
  const _sfc_main$h = {
    __name: "qc",
    setup(__props, { expose: __expose }) {
      __expose();
      const qcNo = vue.ref("");
      const order = vue.ref(null);
      const result = vue.ref("PASS");
      const remark = vue.ref("");
      onLoad((options) => {
        qcNo.value = (options == null ? void 0 : options.qcNo) || "";
        loadOrder();
      });
      async function loadOrder() {
        if (!qcNo.value) return;
        order.value = await getQcOrder(qcNo.value);
      }
      function onResultChange(e) {
        result.value = e.detail.value;
      }
      async function submit() {
        await submitQcResult(qcNo.value, { result: result.value, remark: remark.value });
        uni.showToast({ title: "质检完成", icon: "success" });
        setTimeout(() => uni.navigateBack(), 800);
      }
      const __returned__ = { qcNo, order, result, remark, loadOrder, onResultChange, submit, ref: vue.ref, get onLoad() {
        return onLoad;
      }, get getQcOrder() {
        return getQcOrder;
      }, get submitQcResult() {
        return submitQcResult;
      } };
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  };
  function _sfc_render$g(_ctx, _cache, $props, $setup, $data, $options) {
    return vue.openBlock(), vue.createElementBlock("view", { class: "page" }, [
      $setup.order ? (vue.openBlock(), vue.createElementBlock("view", {
        key: 0,
        class: "header"
      }, [
        vue.createElementVNode(
          "text",
          null,
          "质检单: " + vue.toDisplayString($setup.order.qcNo),
          1
          /* TEXT */
        ),
        vue.createElementVNode(
          "text",
          null,
          "物料: " + vue.toDisplayString($setup.order.materialCode) + " · 抽样: " + vue.toDisplayString($setup.order.sampleQty),
          1
          /* TEXT */
        )
      ])) : vue.createCommentVNode("v-if", true),
      vue.createElementVNode("view", { class: "form-card" }, [
        vue.createElementVNode("text", { class: "label" }, "质检结果"),
        vue.createElementVNode(
          "radio-group",
          { onChange: $setup.onResultChange },
          [
            vue.createElementVNode("label", { class: "radio-item" }, [
              vue.createElementVNode("radio", {
                value: "PASS",
                checked: $setup.result === "PASS"
              }, null, 8, ["checked"]),
              vue.createTextVNode("合格")
            ]),
            vue.createElementVNode("label", { class: "radio-item" }, [
              vue.createElementVNode("radio", {
                value: "FAIL",
                checked: $setup.result === "FAIL"
              }, null, 8, ["checked"]),
              vue.createTextVNode("不合格")
            ])
          ],
          32
          /* NEED_HYDRATION */
        ),
        vue.createElementVNode("text", { class: "label" }, "备注"),
        vue.withDirectives(vue.createElementVNode(
          "textarea",
          {
            "onUpdate:modelValue": _cache[0] || (_cache[0] = ($event) => $setup.remark = $event),
            class: "textarea",
            placeholder: "备注信息"
          },
          null,
          512
          /* NEED_PATCH */
        ), [
          [vue.vModelText, $setup.remark]
        ]),
        vue.createElementVNode("button", {
          class: "btn",
          type: "primary",
          onClick: $setup.submit
        }, "提交结果")
      ])
    ]);
  }
  const PagesQcQc = /* @__PURE__ */ _export_sfc(_sfc_main$h, [["render", _sfc_render$g], ["__scopeId", "data-v-19b48086"], ["__file", "D:/AAA/WMS/wms-pda/src/pages/qc/qc.vue"]]);
  const _sfc_main$g = {
    __name: "trace",
    setup(__props, { expose: __expose }) {
      __expose();
      const materialCode = vue.ref("");
      const batchNo = vue.ref("");
      const barcode = vue.ref("");
      const result = vue.reactive({ traceRecords: [] });
      async function search() {
        const data = await traceBatch({
          materialCode: materialCode.value || void 0,
          batchNo: batchNo.value || void 0,
          barcode: barcode.value || void 0
        });
        Object.assign(result, data);
        result.traceRecords = data.traceRecords || [];
      }
      async function scanTrace() {
        try {
          const parsed = await scanAndParse("扫描追溯条码");
          materialCode.value = parsed.materialCode;
          batchNo.value = parsed.batchNo;
          barcode.value = parsed.barcodeContent;
          await search();
        } catch {
        }
      }
      const __returned__ = { materialCode, batchNo, barcode, result, search, scanTrace, ref: vue.ref, reactive: vue.reactive, get traceBatch() {
        return traceBatch;
      }, get scanAndParse() {
        return scanAndParse;
      } };
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  };
  function _sfc_render$f(_ctx, _cache, $props, $setup, $data, $options) {
    return vue.openBlock(), vue.createElementBlock("view", { class: "page" }, [
      vue.createElementVNode("view", { class: "search-bar" }, [
        vue.withDirectives(vue.createElementVNode(
          "input",
          {
            "onUpdate:modelValue": _cache[0] || (_cache[0] = ($event) => $setup.materialCode = $event),
            class: "input",
            placeholder: "物料编码"
          },
          null,
          512
          /* NEED_PATCH */
        ), [
          [vue.vModelText, $setup.materialCode]
        ]),
        vue.withDirectives(vue.createElementVNode(
          "input",
          {
            "onUpdate:modelValue": _cache[1] || (_cache[1] = ($event) => $setup.batchNo = $event),
            class: "input",
            placeholder: "批次号"
          },
          null,
          512
          /* NEED_PATCH */
        ), [
          [vue.vModelText, $setup.batchNo]
        ]),
        vue.withDirectives(vue.createElementVNode(
          "input",
          {
            "onUpdate:modelValue": _cache[2] || (_cache[2] = ($event) => $setup.barcode = $event),
            class: "input",
            placeholder: "条码(可选)"
          },
          null,
          512
          /* NEED_PATCH */
        ), [
          [vue.vModelText, $setup.barcode]
        ]),
        vue.createElementVNode("button", {
          class: "btn",
          onClick: $setup.search
        }, "追溯查询"),
        vue.createElementVNode("button", {
          class: "btn scan",
          onClick: $setup.scanTrace
        }, "扫码追溯")
      ]),
      $setup.result.materialCode ? (vue.openBlock(), vue.createElementBlock("view", {
        key: 0,
        class: "summary"
      }, [
        vue.createElementVNode(
          "text",
          { class: "title" },
          vue.toDisplayString($setup.result.materialName || $setup.result.materialCode),
          1
          /* TEXT */
        ),
        vue.createElementVNode(
          "text",
          null,
          "批次: " + vue.toDisplayString($setup.result.batchNo) + " · 当前库存: " + vue.toDisplayString($setup.result.currentStock ?? "-"),
          1
          /* TEXT */
        ),
        vue.createElementVNode(
          "text",
          null,
          "当前库位: " + vue.toDisplayString($setup.result.currentLocation || "-"),
          1
          /* TEXT */
        )
      ])) : vue.createCommentVNode("v-if", true),
      (vue.openBlock(true), vue.createElementBlock(
        vue.Fragment,
        null,
        vue.renderList($setup.result.traceRecords || [], (row) => {
          return vue.openBlock(), vue.createElementBlock("view", {
            key: row.seq,
            class: "card"
          }, [
            vue.createElementVNode(
              "text",
              { class: "type" },
              vue.toDisplayString(row.transactionType) + " · " + vue.toDisplayString(row.operationTime),
              1
              /* TEXT */
            ),
            vue.createElementVNode(
              "text",
              null,
              "库位: " + vue.toDisplayString(row.locationCode) + " · 数量: " + vue.toDisplayString(row.qty),
              1
              /* TEXT */
            ),
            vue.createElementVNode(
              "text",
              null,
              "单据: " + vue.toDisplayString(row.sourceOrderNo || "-") + " · 操作人: " + vue.toDisplayString(row.operatorName || "-"),
              1
              /* TEXT */
            )
          ]);
        }),
        128
        /* KEYED_FRAGMENT */
      ))
    ]);
  }
  const PagesTraceTrace = /* @__PURE__ */ _export_sfc(_sfc_main$g, [["render", _sfc_render$f], ["__scopeId", "data-v-4a0b306e"], ["__file", "D:/AAA/WMS/wms-pda/src/pages/trace/trace.vue"]]);
  const BILL_TYPE$7 = "PRODUCTION_ISSUE";
  const _sfc_main$f = {
    __name: "production-issue",
    setup(__props, { expose: __expose }) {
      __expose();
      const scanInputRef = vue.ref(null);
      const billType = vue.ref(BILL_TYPE$7);
      const { alive, refocusScanInput } = usePageAlive();
      const {
        loading,
        keyword,
        notices,
        loadList,
        loadListOnShow,
        searchByBarcode,
        statusLabel,
        statusClass
      } = useNoticeBillList(billType);
      async function onScan(barcode) {
        var _a;
        if (!alive.value) return;
        const list = await searchByBarcode(barcode);
        if (list && list.length === 1 && ((_a = list[0]) == null ? void 0 : _a.billNo)) {
          openBill(list[0]);
          return;
        }
        refocusScanInput(scanInputRef, 300);
      }
      function onSearch(val) {
        keyword.value = val || keyword.value;
        loadList(keyword.value);
      }
      function openBill(item) {
        uni.navigateTo({
          url: `/pages/picking/production-issue-scan?billNo=${encodeURIComponent(item.billNo)}`
        });
      }
      onLoad(() => uni.setNavigationBarTitle({ title: "生产领料" }));
      onShow(() => loadListOnShow());
      vue.onMounted(() => refocusScanInput(scanInputRef, 500));
      const __returned__ = { BILL_TYPE: BILL_TYPE$7, scanInputRef, billType, alive, refocusScanInput, loading, keyword, notices, loadList, loadListOnShow, searchByBarcode, statusLabel, statusClass, onScan, onSearch, openBill, ref: vue.ref, onMounted: vue.onMounted, get onLoad() {
        return onLoad;
      }, get onShow() {
        return onShow;
      }, ScanSearchBar, get useNoticeBillList() {
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
  function _sfc_render$e(_ctx, _cache, $props, $setup, $data, $options) {
    return vue.openBlock(), vue.createElementBlock("view", { class: "page" }, [
      vue.createElementVNode("view", { class: "search-top" }, [
        vue.createVNode($setup["ScanSearchBar"], {
          ref: "scanInputRef",
          modelValue: $setup.keyword,
          "onUpdate:modelValue": _cache[0] || (_cache[0] = ($event) => $setup.keyword = $event),
          disabled: $setup.loading,
          placeholder: "扫码或搜索生产用料清单号/车间",
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
                  item.erpBillNo ? (vue.openBlock(), vue.createElementBlock(
                    "text",
                    {
                      key: 2,
                      class: "bill-erp"
                    },
                    " · 领料 " + vue.toDisplayString(item.erpBillNo),
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
          vue.createElementVNode("text", { class: "empty-text" }, "暂无已审核的生产用料清单")
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
  const PagesPickingProductionIssue = /* @__PURE__ */ _export_sfc(_sfc_main$f, [["render", _sfc_render$e], ["__scopeId", "data-v-e0ad26c4"], ["__file", "D:/AAA/WMS/wms-pda/src/pages/picking/production-issue.vue"]]);
  function parseMaterialBarcode(raw) {
    const text = String(raw || "").trim();
    if (!text) return null;
    const parts = text.split("|").map((s) => s.trim()).filter(Boolean);
    if (parts.length >= 2) {
      const qty = parts.length >= 3 ? Number(parts[2]) : null;
      return {
        materialCode: parts[0],
        batchNo: parts[1] || "",
        qty: Number.isFinite(qty) && qty > 0 ? qty : null,
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
    return function useNoticeBillScanImpl(billNo) {
      const loading = vue.ref(false);
      const submitting = vue.ref(false);
      const detail = vue.ref(null);
      const lines = vue.ref([]);
      const lastHighlightLineNo = vue.ref(null);
      let lastLoadAt = 0;
      const checkedCount = vue.computed(() => lines.value.filter((l) => l.checked).length);
      const submitableCount = vue.computed(
        () => lines.value.filter((l) => l.checked && (Number(l.pendingSubmitQty) || 0) > 0).length
      );
      function detailCacheKey() {
        return `notice-detail:${billType}:${billNo.value || ""}`;
      }
      function normalizeLine(line) {
        if (!line) return line;
        return {
          ...line,
          checked: line.checked === true || line.checked === 1,
          pendingSubmitQty: line.pendingSubmitQty ?? 0
        };
      }
      function applyDetail(data) {
        detail.value = data;
        lines.value = ((data == null ? void 0 : data.lines) || []).map(normalizeLine);
      }
      async function loadDetail(options = {}) {
        if (!billNo.value) return null;
        const force = options.force === true;
        const cacheKey = detailCacheKey();
        if (!force) {
          const cached = cacheGet(cacheKey);
          if (cached) {
            applyDetail(cached);
            return cached;
          }
        }
        loading.value = true;
        try {
          const data = await getNoticeBillDetail(billType, billNo.value, { refresh: force });
          applyDetail(data);
          cacheSet(cacheKey, data, DETAIL_CACHE_TTL_MS);
          lastLoadAt = Date.now();
          return data;
        } catch (e) {
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
          return msg || "该物料已领满";
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
      function applyLocalScan(matched) {
        const line = { ...matched.line };
        const plan = Number(line.planQty) || 0;
        const submitted = Number(line.submittedQty) || 0;
        const remain = Math.max(0, plan - submitted);
        if (remain <= 0) {
          throw Object.assign(new Error("该物料已领满"), { errorType: "LINE_ALREADY_FULL" });
        }
        let addQty = matched.parsed.qty != null ? Number(matched.parsed.qty) : 1;
        if (!Number.isFinite(addQty) || addQty <= 0) addQty = 1;
        if (addQty > remain) addQty = remain;
        const pending = Number(line.pendingSubmitQty) || 0;
        const nextPending = Math.min(remain, pending + addQty);
        line.checked = true;
        line.pendingSubmitQty = nextPending;
        line.scannedQty = submitted + nextPending;
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
            applyLocalScan(local);
            lastHighlightLineNo.value = local.line.lineNo;
          }
          const line = await scanNoticeLine(billType, billNo.value, raw);
          mergeLine(line);
          lastHighlightLineNo.value = line.lineNo;
          const qty = line.scannedBarcodeQty ?? line.pendingSubmitQty;
          const qtyText = qty != null && qty !== "" ? ` ×${formatQty(qty)}` : "";
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
      async function updateQty(lineNo, qty) {
        const num = Number(qty);
        if (Number.isNaN(num) || num < 0) {
          uni.showToast({ title: "请输入有效数量", icon: "none" });
          return false;
        }
        try {
          const line = await updateNoticeLineQty(billType, billNo.value, lineNo, num);
          mergeLine(line);
          return true;
        } catch (e) {
          uni.showToast({ title: (e == null ? void 0 : e.message) || "更新数量失败", icon: "none" });
          return false;
        }
      }
      function formatQty(val) {
        if (val == null || val === "") return "0";
        const n = Number(val);
        if (Number.isNaN(n)) return String(val);
        return Number.isInteger(n) ? String(n) : String(n);
      }
      function formatSubmitError(e) {
        var _a, _b;
        const type = ((_a = e == null ? void 0 : e.data) == null ? void 0 : _a.errorType) || (e == null ? void 0 : e.errorType);
        const msg = (e == null ? void 0 : e.message) || ((_b = e == null ? void 0 : e.data) == null ? void 0 : _b.message);
        if (type === "ERP_SYNC_FAILED" || type === "ERP_IN_STOCK_QTY_EXCEEDED") {
          return msg || "金蝶同步失败，数量未变更";
        }
        return msg || "提交失败";
      }
      async function submit() {
        var _a, _b;
        if (!submitableCount.value && !checkedCount.value) {
          uni.showToast({ title: "请先扫描勾选物料", icon: "none" });
          return false;
        }
        submitting.value = true;
        try {
          const result = await submitNoticeBill(billType, billNo.value, {
            supplierCode: (_a = detail.value) == null ? void 0 : _a.supplierCode,
            supplierName: (_b = detail.value) == null ? void 0 : _b.supplierName
          });
          const syncStatus = result == null ? void 0 : result.erpSyncStatus;
          if (syncStatus && syncStatus !== "SUCCESS" && syncStatus !== "PENDING") {
            uni.showToast({
              title: result.erpSyncMessage || "金蝶同步失败，数量未变更",
              icon: "none",
              duration: 3500
            });
            return false;
          }
          uni.showToast({
            title: (result == null ? void 0 : result.erpBillNo) ? `已同步 ${result.erpBillNo}` : (result == null ? void 0 : result.message) || `已提交 ${result.lineCount || 0} 项`,
            icon: "success"
          });
          cacheDel(detailCacheKey());
          await loadDetail({ force: true });
          if (isNoticeBillCompleted(detail.value)) {
            setTimeout(() => uni.navigateBack(), 600);
          }
          return true;
        } catch (e) {
          uni.showToast({ title: formatSubmitError(e), icon: "none", duration: 3500 });
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
        formatQty,
        submit,
        rowClass
      };
    };
  }
  const BILL_TYPE$6 = "PRODUCTION_ISSUE";
  const useProductionIssueScan = createNoticeBillScan(BILL_TYPE$6, {
    notOnBill: "该物料不在本生产用料清单中",
    linesNotReady: "用料清单明细未加载完成，请返回重新进入"
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
  const _sfc_main$e = {
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
        formatQty,
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
          qtyDrafts[line.lineNo] = formatQty(line.pendingSubmitQty || 0);
        });
      }
      function getQtyDraft(line) {
        if (qtyDrafts[line.lineNo] == null) {
          qtyDrafts[line.lineNo] = formatQty(line.pendingSubmitQty || 0);
        }
        return qtyDrafts[line.lineNo];
      }
      function onQtyInput(line, e) {
        qtyDrafts[line.lineNo] = e.detail.value;
      }
      async function onQtyBlur(line) {
        const raw = qtyDrafts[line.lineNo];
        const num = raw === "" || raw == null ? 0 : Number(raw);
        if (Number.isNaN(num) || num < 0) {
          qtyDrafts[line.lineNo] = formatQty(line.pendingSubmitQty || 0);
          return;
        }
        const current = Number(line.pendingSubmitQty) || 0;
        if (num === current) return;
        updatingLineNo.value = line.lineNo;
        const ok = await updateQty(line.lineNo, num);
        updatingLineNo.value = null;
        if (ok) {
          const updated = lines.value.find((l) => l.lineNo === line.lineNo);
          if (updated) qtyDrafts[line.lineNo] = formatQty(updated.pendingSubmitQty || 0);
        } else {
          qtyDrafts[line.lineNo] = formatQty(line.pendingSubmitQty || 0);
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
      const __returned__ = { billNo, scanInputRef, qtyDrafts, updatingLineNo, alive, refocusScanInput, loading, submitting, detail, lines, checkedCount, submitableCount, loadDetailOnShow, handleScan, toggleCheck, updateQty, formatQty, submit, rowClass, lastHighlightLineNo, windowed, onListScroll, pinLine, partialCount, isDoneLine, isPartialLine, syncQtyDrafts, getQtyDraft, onQtyInput, onQtyBlur, onScan, onToggle, onRowTap, onSubmit, ref: vue.ref, computed: vue.computed, reactive: vue.reactive, get onLoad() {
        return onLoad;
      }, get onShow() {
        return onShow;
      }, CompactScanBox, get useProductionIssueScan() {
        return useProductionIssueScan;
      }, get usePageAlive() {
        return usePageAlive;
      }, get useWindowedLines() {
        return useWindowedLines;
      } };
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  };
  function _sfc_render$d(_ctx, _cache, $props, $setup, $data, $options) {
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
                        vue.toDisplayString($setup.formatQty(line.planQty)),
                        1
                        /* TEXT */
                      )
                    ]),
                    vue.createElementVNode("view", { class: "qty-cell" }, [
                      vue.createElementVNode("text", { class: "qty-label" }, "已领"),
                      vue.createElementVNode(
                        "text",
                        { class: "qty-value submitted" },
                        vue.toDisplayString($setup.formatQty(line.submittedQty)),
                        1
                        /* TEXT */
                      )
                    ]),
                    vue.createElementVNode("view", { class: "qty-cell" }, [
                      vue.createElementVNode("text", { class: "qty-label" }, "可领"),
                      vue.createElementVNode(
                        "text",
                        { class: "qty-value remain" },
                        vue.toDisplayString($setup.formatQty(line.remainQty)),
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
  const PagesPickingProductionIssueScan = /* @__PURE__ */ _export_sfc(_sfc_main$e, [["render", _sfc_render$d], ["__scopeId", "data-v-aadb7a20"], ["__file", "D:/AAA/WMS/wms-pda/src/pages/picking/production-issue-scan.vue"]]);
  const BILL_TYPE$5 = "OUTSOURCE_ISSUE";
  const _sfc_main$d = {
    __name: "outsource-issue",
    setup(__props, { expose: __expose }) {
      __expose();
      const scanInputRef = vue.ref(null);
      const billType = vue.ref(BILL_TYPE$5);
      const { alive, refocusScanInput } = usePageAlive();
      const {
        loading,
        keyword,
        notices,
        loadList,
        loadListOnShow,
        searchByBarcode,
        statusLabel,
        statusClass
      } = useNoticeBillList(billType);
      async function onScan(barcode) {
        var _a;
        if (!alive.value) return;
        const list = await searchByBarcode(barcode);
        if (list && list.length === 1 && ((_a = list[0]) == null ? void 0 : _a.billNo)) {
          openBill(list[0]);
          return;
        }
        refocusScanInput(scanInputRef, 300);
      }
      function onSearch(val) {
        keyword.value = val || keyword.value;
        loadList(keyword.value);
      }
      function openBill(item) {
        uni.navigateTo({
          url: `/pages/picking/outsource-issue-scan?billNo=${encodeURIComponent(item.billNo)}`
        });
      }
      onLoad(() => uni.setNavigationBarTitle({ title: "委外领料" }));
      onShow(() => loadListOnShow());
      vue.onMounted(() => refocusScanInput(scanInputRef, 500));
      const __returned__ = { BILL_TYPE: BILL_TYPE$5, scanInputRef, billType, alive, refocusScanInput, loading, keyword, notices, loadList, loadListOnShow, searchByBarcode, statusLabel, statusClass, onScan, onSearch, openBill, ref: vue.ref, onMounted: vue.onMounted, get onLoad() {
        return onLoad;
      }, get onShow() {
        return onShow;
      }, ScanSearchBar, get useNoticeBillList() {
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
  function _sfc_render$c(_ctx, _cache, $props, $setup, $data, $options) {
    return vue.openBlock(), vue.createElementBlock("view", { class: "page" }, [
      vue.createElementVNode("view", { class: "search-top" }, [
        vue.createVNode($setup["ScanSearchBar"], {
          ref: "scanInputRef",
          modelValue: $setup.keyword,
          "onUpdate:modelValue": _cache[0] || (_cache[0] = ($event) => $setup.keyword = $event),
          disabled: $setup.loading,
          placeholder: "扫码或搜索委外用料清单号/供应商",
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
                  item.erpBillNo ? (vue.openBlock(), vue.createElementBlock(
                    "text",
                    {
                      key: 2,
                      class: "bill-erp"
                    },
                    " · 领料 " + vue.toDisplayString(item.erpBillNo),
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
          vue.createElementVNode("text", { class: "empty-text" }, "暂无已审核的委外用料清单")
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
  const PagesPickingOutsourceIssue = /* @__PURE__ */ _export_sfc(_sfc_main$d, [["render", _sfc_render$c], ["__scopeId", "data-v-88752e11"], ["__file", "D:/AAA/WMS/wms-pda/src/pages/picking/outsource-issue.vue"]]);
  const BILL_TYPE$4 = "OUTSOURCE_ISSUE";
  const useOutsourceIssueScan = createNoticeBillScan(BILL_TYPE$4, {
    notOnBill: "?????????????",
    linesNotReady: "?????????????????????"
  });
  const _sfc_main$c = {
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
        formatQty,
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
          qtyDrafts[line.lineNo] = formatQty(line.pendingSubmitQty || 0);
        });
      }
      function getQtyDraft(line) {
        if (qtyDrafts[line.lineNo] == null) {
          qtyDrafts[line.lineNo] = formatQty(line.pendingSubmitQty || 0);
        }
        return qtyDrafts[line.lineNo];
      }
      function onQtyInput(line, e) {
        qtyDrafts[line.lineNo] = e.detail.value;
      }
      async function onQtyBlur(line) {
        const raw = qtyDrafts[line.lineNo];
        const num = raw === "" || raw == null ? 0 : Number(raw);
        if (Number.isNaN(num) || num < 0) {
          qtyDrafts[line.lineNo] = formatQty(line.pendingSubmitQty || 0);
          return;
        }
        const current = Number(line.pendingSubmitQty) || 0;
        if (num === current) return;
        updatingLineNo.value = line.lineNo;
        const ok = await updateQty(line.lineNo, num);
        updatingLineNo.value = null;
        if (ok) {
          const updated = lines.value.find((l) => l.lineNo === line.lineNo);
          if (updated) qtyDrafts[line.lineNo] = formatQty(updated.pendingSubmitQty || 0);
        } else {
          qtyDrafts[line.lineNo] = formatQty(line.pendingSubmitQty || 0);
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
        uni.setNavigationBarTitle({ title: "??????" });
      });
      onShow(async () => {
        if (billNo.value) {
          await loadDetailOnShow();
          syncQtyDrafts();
        }
        refocusScanInput(scanInputRef, 400);
      });
      const __returned__ = { billNo, scanInputRef, qtyDrafts, updatingLineNo, alive, refocusScanInput, loading, submitting, detail, lines, checkedCount, submitableCount, loadDetailOnShow, handleScan, toggleCheck, updateQty, formatQty, submit, rowClass, lastHighlightLineNo, windowed, onListScroll, pinLine, partialCount, isDoneLine, isPartialLine, syncQtyDrafts, getQtyDraft, onQtyInput, onQtyBlur, onScan, onToggle, onRowTap, onSubmit, ref: vue.ref, computed: vue.computed, reactive: vue.reactive, get onLoad() {
        return onLoad;
      }, get onShow() {
        return onShow;
      }, CompactScanBox, get useOutsourceIssueScan() {
        return useOutsourceIssueScan;
      }, get usePageAlive() {
        return usePageAlive;
      }, get useWindowedLines() {
        return useWindowedLines;
      } };
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  };
  function _sfc_render$b(_ctx, _cache, $props, $setup, $data, $options) {
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
            "????? " + vue.toDisplayString($setup.detail.erpBillNo),
            1
            /* TEXT */
          )) : vue.createCommentVNode("v-if", true)
        ]),
        vue.createElementVNode("view", { class: "order-stat-wrap" }, [
          vue.createElementVNode(
            "text",
            { class: "order-stat" },
            vue.toDisplayString($setup.checkedCount) + "/" + vue.toDisplayString($setup.lines.length) + " ??",
            1
            /* TEXT */
          ),
          $setup.partialCount ? (vue.openBlock(), vue.createElementBlock(
            "text",
            {
              key: 0,
              class: "order-partial"
            },
            "???? " + vue.toDisplayString($setup.partialCount),
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
                        }, "?")) : vue.createCommentVNode("v-if", true)
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
                      }, "????")) : vue.createCommentVNode("v-if", true)
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
                      "?? " + vue.toDisplayString(line.specification || "-"),
                      1
                      /* TEXT */
                    ),
                    vue.createElementVNode(
                      "text",
                      { class: "mat-batch" },
                      "?? " + vue.toDisplayString(line.batchNo || "-"),
                      1
                      /* TEXT */
                    ),
                    vue.createElementVNode(
                      "text",
                      { class: "mat-wh" },
                      "?? " + vue.toDisplayString(line.erpStockCode || "-"),
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
                      vue.createElementVNode("text", { class: "qty-label" }, "??"),
                      vue.createElementVNode(
                        "text",
                        { class: "qty-value" },
                        vue.toDisplayString($setup.formatQty(line.planQty)),
                        1
                        /* TEXT */
                      )
                    ]),
                    vue.createElementVNode("view", { class: "qty-cell" }, [
                      vue.createElementVNode("text", { class: "qty-label" }, "??"),
                      vue.createElementVNode(
                        "text",
                        { class: "qty-value submitted" },
                        vue.toDisplayString($setup.formatQty(line.submittedQty)),
                        1
                        /* TEXT */
                      )
                    ]),
                    vue.createElementVNode("view", { class: "qty-cell" }, [
                      vue.createElementVNode("text", { class: "qty-label" }, "??"),
                      vue.createElementVNode(
                        "text",
                        { class: "qty-value remain" },
                        vue.toDisplayString($setup.formatQty(line.remainQty)),
                        1
                        /* TEXT */
                      )
                    ]),
                    vue.createElementVNode("view", { class: "qty-cell unit-cell" }, [
                      vue.createElementVNode("text", { class: "qty-label" }, "??"),
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
                    vue.createElementVNode("text", { class: "qty-edit-label" }, "????"),
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
                  }, "?????"))
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
            vue.createElementVNode("text", { class: "empty-icon" }, "??")
          ])) : $setup.lines.length > $setup.windowed.items.length ? (vue.openBlock(), vue.createElementBlock(
            "view",
            {
              key: 3,
              class: "loading-tip end-tip"
            },
            " ?? " + vue.toDisplayString($setup.windowed.items.length) + "/" + vue.toDisplayString($setup.lines.length) + " ? ? ?????? ",
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
        }, " ????" + vue.toDisplayString($setup.submitableCount ? ` (${$setup.submitableCount})` : ""), 9, ["loading", "disabled"])
      ])
    ]);
  }
  const PagesPickingOutsourceIssueScan = /* @__PURE__ */ _export_sfc(_sfc_main$c, [["render", _sfc_render$b], ["__scopeId", "data-v-fb3c2b3c"], ["__file", "D:/AAA/WMS/wms-pda/src/pages/picking/outsource-issue-scan.vue"]]);
  const BILL_TYPE$3 = "PRODUCTION_RETURN";
  const _sfc_main$b = {
    __name: "production-return",
    setup(__props, { expose: __expose }) {
      __expose();
      const scanInputRef = vue.ref(null);
      const billType = vue.ref(BILL_TYPE$3);
      const { alive, refocusScanInput } = usePageAlive();
      const {
        loading,
        keyword,
        notices,
        loadList,
        loadListOnShow,
        searchByBarcode,
        statusLabel,
        statusClass
      } = useNoticeBillList(billType);
      async function onScan(barcode) {
        var _a;
        if (!alive.value) return;
        const list = await searchByBarcode(barcode);
        if (list && list.length === 1 && ((_a = list[0]) == null ? void 0 : _a.billNo)) {
          openBill(list[0]);
          return;
        }
        refocusScanInput(scanInputRef, 300);
      }
      function onSearch(val) {
        keyword.value = val || keyword.value;
        loadList(keyword.value);
      }
      function openBill(item) {
        uni.navigateTo({
          url: `/pages/picking/production-return-scan?billNo=${encodeURIComponent(item.billNo)}`
        });
      }
      onLoad(() => uni.setNavigationBarTitle({ title: "生产退料" }));
      onShow(() => loadListOnShow());
      vue.onMounted(() => refocusScanInput(scanInputRef, 500));
      const __returned__ = { BILL_TYPE: BILL_TYPE$3, scanInputRef, billType, alive, refocusScanInput, loading, keyword, notices, loadList, loadListOnShow, searchByBarcode, statusLabel, statusClass, onScan, onSearch, openBill, ref: vue.ref, onMounted: vue.onMounted, get onLoad() {
        return onLoad;
      }, get onShow() {
        return onShow;
      }, ScanSearchBar, get useNoticeBillList() {
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
  function _sfc_render$a(_ctx, _cache, $props, $setup, $data, $options) {
    return vue.openBlock(), vue.createElementBlock("view", { class: "page" }, [
      vue.createElementVNode("view", { class: "search-top" }, [
        vue.createVNode($setup["ScanSearchBar"], {
          ref: "scanInputRef",
          modelValue: $setup.keyword,
          "onUpdate:modelValue": _cache[0] || (_cache[0] = ($event) => $setup.keyword = $event),
          disabled: $setup.loading,
          placeholder: "扫码或搜索生产领料单号/车间",
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
                  item.erpBillNo ? (vue.openBlock(), vue.createElementBlock(
                    "text",
                    {
                      key: 2,
                      class: "bill-erp"
                    },
                    " · 退料 " + vue.toDisplayString(item.erpBillNo),
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
          vue.createElementVNode("text", { class: "empty-text" }, "暂无已审核的生产领料单")
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
  const PagesPickingProductionReturn = /* @__PURE__ */ _export_sfc(_sfc_main$b, [["render", _sfc_render$a], ["__scopeId", "data-v-7f04f7ee"], ["__file", "D:/AAA/WMS/wms-pda/src/pages/picking/production-return.vue"]]);
  const BILL_TYPE$2 = "PRODUCTION_RETURN";
  function useProductionReturnScan(billNo) {
    const loading = vue.ref(false);
    const submitting = vue.ref(false);
    const detail = vue.ref(null);
    const lines = vue.ref([]);
    const lastHighlightLineNo = vue.ref(null);
    const checkedCount = vue.computed(() => lines.value.filter((l) => l.checked).length);
    const submitableCount = vue.computed(
      () => lines.value.filter((l) => l.checked && (Number(l.pendingSubmitQty) || 0) > 0).length
    );
    async function loadDetail() {
      if (!billNo.value) return null;
      loading.value = true;
      try {
        const data = await getNoticeBillDetail(BILL_TYPE$2, billNo.value);
        detail.value = data;
        lines.value = (data.lines || []).map(normalizeLine);
        return data;
      } catch (e) {
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
        pendingSubmitQty: line.pendingSubmitQty ?? 0
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
        return msg || "该物料不在本生产领料单中";
      }
      if (type === "BILL_LINES_NOT_READY" || type === "BILL_LINE_NOT_SYNCED") {
        return msg || "领料单明细未加载完成，请返回重新进入";
      }
      if (type === "LINE_ALREADY_FULL") {
        return msg || "该物料已退满";
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
        const line = await scanNoticeLine(BILL_TYPE$2, billNo.value, barcode.trim());
        mergeLine(line);
        lastHighlightLineNo.value = line.lineNo;
        const qty = line.scannedBarcodeQty ?? line.pendingSubmitQty;
        const qtyText = qty != null && qty !== "" ? ` ×${formatQty(qty)}` : "";
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
        const line = await toggleNoticeLine(BILL_TYPE$2, billNo.value, lineNo, checked);
        mergeLine(line);
      } catch (e) {
        uni.showToast({ title: (e == null ? void 0 : e.message) || "操作失败", icon: "none" });
      }
    }
    async function updateQty(lineNo, qty) {
      const num = Number(qty);
      if (Number.isNaN(num) || num < 0) {
        uni.showToast({ title: "请输入有效数量", icon: "none" });
        return false;
      }
      try {
        const line = await updateNoticeLineQty(BILL_TYPE$2, billNo.value, lineNo, num);
        mergeLine(line);
        return true;
      } catch (e) {
        uni.showToast({ title: (e == null ? void 0 : e.message) || "更新数量失败", icon: "none" });
        return false;
      }
    }
    function formatQty(val) {
      if (val == null || val === "") return "0";
      const n = Number(val);
      if (Number.isNaN(n)) return String(val);
      return Number.isInteger(n) ? String(n) : String(n);
    }
    function formatSubmitError(e) {
      var _a, _b;
      const type = ((_a = e == null ? void 0 : e.data) == null ? void 0 : _a.errorType) || (e == null ? void 0 : e.errorType);
      const msg = (e == null ? void 0 : e.message) || ((_b = e == null ? void 0 : e.data) == null ? void 0 : _b.message);
      if (type === "ERP_SYNC_FAILED" || type === "ERP_IN_STOCK_QTY_EXCEEDED") {
        return msg || "金蝶同步失败，数量未变更";
      }
      return msg || "提交失败";
    }
    async function submit(getWarehousePayload) {
      var _a, _b, _c;
      if (!submitableCount.value && !checkedCount.value) {
        uni.showToast({ title: "请先扫描勾选物料", icon: "none" });
        return false;
      }
      submitting.value = true;
      try {
        const wh = typeof getWarehousePayload === "function" ? getWarehousePayload() : {};
        const manual = (wh == null ? void 0 : wh.autoAssignWarehouse) === false;
        const result = await submitNoticeBill(BILL_TYPE$2, billNo.value, {
          supplierCode: (_a = detail.value) == null ? void 0 : _a.supplierCode,
          supplierName: (_b = detail.value) == null ? void 0 : _b.supplierName,
          autoAssignWarehouse: !manual,
          warehouseCode: manual ? wh == null ? void 0 : wh.warehouseCode : void 0,
          erpWarehouseCode: manual ? (wh == null ? void 0 : wh.erpWarehouseCode) || (wh == null ? void 0 : wh.warehouseCode) : (_c = detail.value) == null ? void 0 : _c.erpWarehouseCode
        });
        if ((result == null ? void 0 : result.erpSyncStatus) && result.erpSyncStatus !== "SUCCESS" && result.erpSyncStatus !== "PENDING") {
          uni.showToast({
            title: result.erpSyncMessage || "金蝶同步失败，数量未变更",
            icon: "none",
            duration: 3500
          });
          return false;
        }
        uni.showToast({
          title: (result == null ? void 0 : result.erpBillNo) ? `已同步 ${result.erpBillNo}` : (result == null ? void 0 : result.message) || `已提交 ${result.lineCount || 0} 项`,
          icon: "success"
        });
        await loadDetail();
        if (isNoticeBillCompleted(detail.value)) {
          setTimeout(() => uni.navigateBack(), 600);
        }
        return true;
      } catch (e) {
        uni.showToast({ title: formatSubmitError(e), icon: "none", duration: 3500 });
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
      formatQty,
      submit,
      rowClass
    };
  }
  const _sfc_main$a = {
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
        formatQty,
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
          qtyDrafts[line.lineNo] = formatQty(line.pendingSubmitQty || 0);
        });
      }
      function getQtyDraft(line) {
        if (qtyDrafts[line.lineNo] == null) {
          qtyDrafts[line.lineNo] = formatQty(line.pendingSubmitQty || 0);
        }
        return qtyDrafts[line.lineNo];
      }
      function onQtyInput(line, e) {
        qtyDrafts[line.lineNo] = e.detail.value;
      }
      async function onQtyBlur(line) {
        const raw = qtyDrafts[line.lineNo];
        const num = raw === "" || raw == null ? 0 : Number(raw);
        if (Number.isNaN(num) || num < 0) {
          qtyDrafts[line.lineNo] = formatQty(line.pendingSubmitQty || 0);
          return;
        }
        const current = Number(line.pendingSubmitQty) || 0;
        if (num === current) return;
        updatingLineNo.value = line.lineNo;
        const ok = await updateQty(line.lineNo, num);
        updatingLineNo.value = null;
        if (ok) {
          const updated = lines.value.find((l) => l.lineNo === line.lineNo);
          if (updated) qtyDrafts[line.lineNo] = formatQty(updated.pendingSubmitQty || 0);
        } else {
          qtyDrafts[line.lineNo] = formatQty(line.pendingSubmitQty || 0);
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
      const __returned__ = { billNo, scanInputRef, warehousePickerRef, warehousePayload, qtyDrafts, updatingLineNo, alive, refocusScanInput, loading, submitting, detail, lines, checkedCount, submitableCount, loadDetail, handleScan, toggleCheck, updateQty, formatQty, submit, rowClass, partialCount, suggestWarehouseCode, onWarehouseChange, isDoneLine, isPartialLine, syncQtyDrafts, getQtyDraft, onQtyInput, onQtyBlur, onScan, onToggle, onRowTap, onSubmit, ref: vue.ref, computed: vue.computed, reactive: vue.reactive, get onLoad() {
        return onLoad;
      }, get onShow() {
        return onShow;
      }, CompactScanBox, WarehousePicker, get useProductionReturnScan() {
        return useProductionReturnScan;
      }, get usePageAlive() {
        return usePageAlive;
      } };
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  };
  function _sfc_render$9(_ctx, _cache, $props, $setup, $data, $options) {
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
                      vue.toDisplayString($setup.formatQty(line.planQty)),
                      1
                      /* TEXT */
                    )
                  ]),
                  vue.createElementVNode("view", { class: "qty-cell" }, [
                    vue.createElementVNode("text", { class: "qty-label" }, "已退"),
                    vue.createElementVNode(
                      "text",
                      { class: "qty-value submitted" },
                      vue.toDisplayString($setup.formatQty(line.submittedQty)),
                      1
                      /* TEXT */
                    )
                  ]),
                  vue.createElementVNode("view", { class: "qty-cell" }, [
                    vue.createElementVNode("text", { class: "qty-label" }, "可退"),
                    vue.createElementVNode(
                      "text",
                      { class: "qty-value remain" },
                      vue.toDisplayString($setup.formatQty(line.remainQty)),
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
  const PagesPickingProductionReturnScan = /* @__PURE__ */ _export_sfc(_sfc_main$a, [["render", _sfc_render$9], ["__scopeId", "data-v-2e25f340"], ["__file", "D:/AAA/WMS/wms-pda/src/pages/picking/production-return-scan.vue"]]);
  const BILL_TYPE$1 = "OUTSOURCE_RETURN";
  const _sfc_main$9 = {
    __name: "outsource-return",
    setup(__props, { expose: __expose }) {
      __expose();
      const scanInputRef = vue.ref(null);
      const billType = vue.ref(BILL_TYPE$1);
      const { alive, refocusScanInput } = usePageAlive();
      const {
        loading,
        keyword,
        notices,
        loadList,
        loadListOnShow,
        searchByBarcode,
        statusLabel,
        statusClass
      } = useNoticeBillList(billType);
      async function onScan(barcode) {
        var _a;
        if (!alive.value) return;
        const list = await searchByBarcode(barcode);
        if (list && list.length === 1 && ((_a = list[0]) == null ? void 0 : _a.billNo)) {
          openBill(list[0]);
          return;
        }
        refocusScanInput(scanInputRef, 300);
      }
      function onSearch(val) {
        keyword.value = val || keyword.value;
        loadList(keyword.value);
      }
      function openBill(item) {
        uni.navigateTo({
          url: `/pages/picking/outsource-return-scan?billNo=${encodeURIComponent(item.billNo)}`
        });
      }
      onLoad(() => uni.setNavigationBarTitle({ title: "委外退料" }));
      onShow(() => loadListOnShow());
      vue.onMounted(() => refocusScanInput(scanInputRef, 500));
      const __returned__ = { BILL_TYPE: BILL_TYPE$1, scanInputRef, billType, alive, refocusScanInput, loading, keyword, notices, loadList, loadListOnShow, searchByBarcode, statusLabel, statusClass, onScan, onSearch, openBill, ref: vue.ref, onMounted: vue.onMounted, get onLoad() {
        return onLoad;
      }, get onShow() {
        return onShow;
      }, ScanSearchBar, get useNoticeBillList() {
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
  function _sfc_render$8(_ctx, _cache, $props, $setup, $data, $options) {
    return vue.openBlock(), vue.createElementBlock("view", { class: "page" }, [
      vue.createElementVNode("view", { class: "search-top" }, [
        vue.createVNode($setup["ScanSearchBar"], {
          ref: "scanInputRef",
          modelValue: $setup.keyword,
          "onUpdate:modelValue": _cache[0] || (_cache[0] = ($event) => $setup.keyword = $event),
          disabled: $setup.loading,
          placeholder: "扫码或搜索委外领料单号/供应商",
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
                  item.erpBillNo ? (vue.openBlock(), vue.createElementBlock(
                    "text",
                    {
                      key: 2,
                      class: "bill-erp"
                    },
                    " · 退料 " + vue.toDisplayString(item.erpBillNo),
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
          vue.createElementVNode("text", { class: "empty-text" }, "暂无已审核的委外领料单")
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
  const PagesPickingOutsourceReturn = /* @__PURE__ */ _export_sfc(_sfc_main$9, [["render", _sfc_render$8], ["__scopeId", "data-v-5170056c"], ["__file", "D:/AAA/WMS/wms-pda/src/pages/picking/outsource-return.vue"]]);
  const BILL_TYPE = "OUTSOURCE_RETURN";
  function useOutsourceReturnScan(billNo) {
    const loading = vue.ref(false);
    const submitting = vue.ref(false);
    const detail = vue.ref(null);
    const lines = vue.ref([]);
    const lastHighlightLineNo = vue.ref(null);
    const checkedCount = vue.computed(() => lines.value.filter((l) => l.checked).length);
    const submitableCount = vue.computed(
      () => lines.value.filter((l) => l.checked && (Number(l.pendingSubmitQty) || 0) > 0).length
    );
    async function loadDetail() {
      if (!billNo.value) return null;
      loading.value = true;
      try {
        const data = await getNoticeBillDetail(BILL_TYPE, billNo.value);
        detail.value = data;
        lines.value = (data.lines || []).map(normalizeLine);
        return data;
      } catch (e) {
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
        pendingSubmitQty: line.pendingSubmitQty ?? 0
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
        return msg || "该物料不在本委外领料单中";
      }
      if (type === "BILL_LINES_NOT_READY" || type === "BILL_LINE_NOT_SYNCED") {
        return msg || "领料单明细未加载完成，请返回重新进入";
      }
      if (type === "LINE_ALREADY_FULL") {
        return msg || "该物料已退满";
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
        const line = await scanNoticeLine(BILL_TYPE, billNo.value, barcode.trim());
        mergeLine(line);
        lastHighlightLineNo.value = line.lineNo;
        const qty = line.scannedBarcodeQty ?? line.pendingSubmitQty;
        const qtyText = qty != null && qty !== "" ? ` ×${formatQty(qty)}` : "";
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
        const line = await toggleNoticeLine(BILL_TYPE, billNo.value, lineNo, checked);
        mergeLine(line);
      } catch (e) {
        uni.showToast({ title: (e == null ? void 0 : e.message) || "操作失败", icon: "none" });
      }
    }
    async function updateQty(lineNo, qty) {
      const num = Number(qty);
      if (Number.isNaN(num) || num < 0) {
        uni.showToast({ title: "请输入有效数量", icon: "none" });
        return false;
      }
      try {
        const line = await updateNoticeLineQty(BILL_TYPE, billNo.value, lineNo, num);
        mergeLine(line);
        return true;
      } catch (e) {
        uni.showToast({ title: (e == null ? void 0 : e.message) || "更新数量失败", icon: "none" });
        return false;
      }
    }
    function formatQty(val) {
      if (val == null || val === "") return "0";
      const n = Number(val);
      if (Number.isNaN(n)) return String(val);
      return Number.isInteger(n) ? String(n) : String(n);
    }
    function formatSubmitError(e) {
      var _a, _b;
      const type = ((_a = e == null ? void 0 : e.data) == null ? void 0 : _a.errorType) || (e == null ? void 0 : e.errorType);
      const msg = (e == null ? void 0 : e.message) || ((_b = e == null ? void 0 : e.data) == null ? void 0 : _b.message);
      if (type === "ERP_SYNC_FAILED" || type === "ERP_IN_STOCK_QTY_EXCEEDED") {
        return msg || "金蝶同步失败，数量未变更";
      }
      return msg || "提交失败";
    }
    async function submit(getWarehousePayload) {
      var _a, _b, _c;
      if (!submitableCount.value && !checkedCount.value) {
        uni.showToast({ title: "请先扫描勾选物料", icon: "none" });
        return false;
      }
      submitting.value = true;
      try {
        const wh = typeof getWarehousePayload === "function" ? getWarehousePayload() : {};
        const manual = (wh == null ? void 0 : wh.autoAssignWarehouse) === false;
        const result = await submitNoticeBill(BILL_TYPE, billNo.value, {
          supplierCode: (_a = detail.value) == null ? void 0 : _a.supplierCode,
          supplierName: (_b = detail.value) == null ? void 0 : _b.supplierName,
          autoAssignWarehouse: !manual,
          warehouseCode: manual ? wh == null ? void 0 : wh.warehouseCode : void 0,
          erpWarehouseCode: manual ? (wh == null ? void 0 : wh.erpWarehouseCode) || (wh == null ? void 0 : wh.warehouseCode) : (_c = detail.value) == null ? void 0 : _c.erpWarehouseCode
        });
        if ((result == null ? void 0 : result.erpSyncStatus) && result.erpSyncStatus !== "SUCCESS" && result.erpSyncStatus !== "PENDING") {
          uni.showToast({
            title: result.erpSyncMessage || "金蝶同步失败，数量未变更",
            icon: "none",
            duration: 3500
          });
          return false;
        }
        uni.showToast({
          title: (result == null ? void 0 : result.erpBillNo) ? `已同步 ${result.erpBillNo}` : (result == null ? void 0 : result.message) || `已提交 ${result.lineCount || 0} 项`,
          icon: "success"
        });
        await loadDetail();
        if (isNoticeBillCompleted(detail.value)) {
          setTimeout(() => uni.navigateBack(), 600);
        }
        return true;
      } catch (e) {
        uni.showToast({ title: formatSubmitError(e), icon: "none", duration: 3500 });
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
      formatQty,
      submit,
      rowClass
    };
  }
  const _sfc_main$8 = {
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
        formatQty,
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
          qtyDrafts[line.lineNo] = formatQty(line.pendingSubmitQty || 0);
        });
      }
      function getQtyDraft(line) {
        if (qtyDrafts[line.lineNo] == null) {
          qtyDrafts[line.lineNo] = formatQty(line.pendingSubmitQty || 0);
        }
        return qtyDrafts[line.lineNo];
      }
      function onQtyInput(line, e) {
        qtyDrafts[line.lineNo] = e.detail.value;
      }
      async function onQtyBlur(line) {
        const raw = qtyDrafts[line.lineNo];
        const num = raw === "" || raw == null ? 0 : Number(raw);
        if (Number.isNaN(num) || num < 0) {
          qtyDrafts[line.lineNo] = formatQty(line.pendingSubmitQty || 0);
          return;
        }
        const current = Number(line.pendingSubmitQty) || 0;
        if (num === current) return;
        updatingLineNo.value = line.lineNo;
        const ok = await updateQty(line.lineNo, num);
        updatingLineNo.value = null;
        if (ok) {
          const updated = lines.value.find((l) => l.lineNo === line.lineNo);
          if (updated) qtyDrafts[line.lineNo] = formatQty(updated.pendingSubmitQty || 0);
        } else {
          qtyDrafts[line.lineNo] = formatQty(line.pendingSubmitQty || 0);
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
      const __returned__ = { billNo, scanInputRef, warehousePickerRef, warehousePayload, qtyDrafts, updatingLineNo, alive, refocusScanInput, loading, submitting, detail, lines, checkedCount, submitableCount, loadDetail, handleScan, toggleCheck, updateQty, formatQty, submit, rowClass, partialCount, suggestWarehouseCode, onWarehouseChange, isDoneLine, isPartialLine, syncQtyDrafts, getQtyDraft, onQtyInput, onQtyBlur, onScan, onToggle, onRowTap, onSubmit, ref: vue.ref, computed: vue.computed, reactive: vue.reactive, get onLoad() {
        return onLoad;
      }, get onShow() {
        return onShow;
      }, CompactScanBox, WarehousePicker, get useOutsourceReturnScan() {
        return useOutsourceReturnScan;
      }, get usePageAlive() {
        return usePageAlive;
      } };
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  };
  function _sfc_render$7(_ctx, _cache, $props, $setup, $data, $options) {
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
                      vue.toDisplayString($setup.formatQty(line.planQty)),
                      1
                      /* TEXT */
                    )
                  ]),
                  vue.createElementVNode("view", { class: "qty-cell" }, [
                    vue.createElementVNode("text", { class: "qty-label" }, "已退"),
                    vue.createElementVNode(
                      "text",
                      { class: "qty-value submitted" },
                      vue.toDisplayString($setup.formatQty(line.submittedQty)),
                      1
                      /* TEXT */
                    )
                  ]),
                  vue.createElementVNode("view", { class: "qty-cell" }, [
                    vue.createElementVNode("text", { class: "qty-label" }, "可退"),
                    vue.createElementVNode(
                      "text",
                      { class: "qty-value remain" },
                      vue.toDisplayString($setup.formatQty(line.remainQty)),
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
  const PagesPickingOutsourceReturnScan = /* @__PURE__ */ _export_sfc(_sfc_main$8, [["render", _sfc_render$7], ["__scopeId", "data-v-a47099cf"], ["__file", "D:/AAA/WMS/wms-pda/src/pages/picking/outsource-return-scan.vue"]]);
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
  const _sfc_main$7 = {
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
  function _sfc_render$6(_ctx, _cache, $props, $setup, $data, $options) {
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
  const PagesPickingIssuePick = /* @__PURE__ */ _export_sfc(_sfc_main$7, [["render", _sfc_render$6], ["__scopeId", "data-v-dd2d633c"], ["__file", "D:/AAA/WMS/wms-pda/src/pages/picking/issue-pick.vue"]]);
  const _sfc_main$6 = {
    __name: "pickup",
    setup(__props, { expose: __expose }) {
      __expose();
      const scanCode2 = vue.ref("");
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
      const __returned__ = { scanCode: scanCode2, issue, lines, lastPickup, parseIssueNo, onScan, confirmPickup, ref: vue.ref, ScanInput, get confirmMaterialPickup() {
        return confirmMaterialPickup;
      }, get getPickIssueDetail() {
        return getPickIssueDetail;
      } };
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  };
  function _sfc_render$5(_ctx, _cache, $props, $setup, $data, $options) {
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
  const PagesPickingPickup = /* @__PURE__ */ _export_sfc(_sfc_main$6, [["render", _sfc_render$5], ["__scopeId", "data-v-5ce58e2f"], ["__file", "D:/AAA/WMS/wms-pda/src/pages/picking/pickup.vue"]]);
  const _sfc_main$5 = {
    __name: "workshop-return",
    setup(__props, { expose: __expose }) {
      __expose();
      const scanCode2 = vue.ref("");
      const lastNo = vue.ref("");
      const form = vue.reactive({
        issueNo: "",
        warehouseCode: "WH001",
        materialCode: "",
        batchNo: "",
        returnReason: "余料退库"
      });
      function onScan(code) {
        const c = (code || "").trim();
        if (c.startsWith("PI:") || c.startsWith("PI")) {
          form.issueNo = c.startsWith("PI:") ? c.slice(3) : c;
          return;
        }
        const parts = c.split("|");
        form.materialCode = parts[0] || c;
        if (parts[1]) form.batchNo = parts[1];
      }
      async function submit() {
        if (!form.materialCode) {
          uni.showToast({ title: "请扫描物料", icon: "none" });
          return;
        }
        const returnNo = await submitWorkshopReturn({
          ...form,
          returnQty: 1,
          autoConfirm: true
        });
        lastNo.value = returnNo;
        uni.showToast({ title: "退库成功", icon: "success" });
        form.materialCode = "";
        form.batchNo = "";
      }
      const __returned__ = { scanCode: scanCode2, lastNo, form, onScan, submit, reactive: vue.reactive, ref: vue.ref, ScanInput, get submitWorkshopReturn() {
        return submitWorkshopReturn;
      } };
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  };
  function _sfc_render$4(_ctx, _cache, $props, $setup, $data, $options) {
    return vue.openBlock(), vue.createElementBlock("view", { class: "page" }, [
      vue.createVNode($setup["ScanInput"], {
        modelValue: $setup.scanCode,
        "onUpdate:modelValue": _cache[0] || (_cache[0] = ($event) => $setup.scanCode = $event),
        placeholder: "扫描物料条码 / 发料单号",
        onScan: $setup.onScan
      }, null, 8, ["modelValue"]),
      vue.createElementVNode("view", { class: "form card" }, [
        vue.createElementVNode("view", { class: "field" }, [
          vue.createElementVNode("text", { class: "label" }, "原发料单"),
          vue.withDirectives(vue.createElementVNode(
            "input",
            {
              "onUpdate:modelValue": _cache[1] || (_cache[1] = ($event) => $setup.form.issueNo = $event),
              class: "input",
              placeholder: "PI..."
            },
            null,
            512
            /* NEED_PATCH */
          ), [
            [vue.vModelText, $setup.form.issueNo]
          ])
        ]),
        vue.createElementVNode("view", { class: "field" }, [
          vue.createElementVNode("text", { class: "label" }, "仓库"),
          vue.withDirectives(vue.createElementVNode(
            "input",
            {
              "onUpdate:modelValue": _cache[2] || (_cache[2] = ($event) => $setup.form.warehouseCode = $event),
              class: "input"
            },
            null,
            512
            /* NEED_PATCH */
          ), [
            [vue.vModelText, $setup.form.warehouseCode]
          ])
        ]),
        vue.createElementVNode("view", { class: "field" }, [
          vue.createElementVNode("text", { class: "label" }, "物料编码"),
          vue.withDirectives(vue.createElementVNode(
            "input",
            {
              "onUpdate:modelValue": _cache[3] || (_cache[3] = ($event) => $setup.form.materialCode = $event),
              class: "input"
            },
            null,
            512
            /* NEED_PATCH */
          ), [
            [vue.vModelText, $setup.form.materialCode]
          ])
        ]),
        vue.createElementVNode("view", { class: "field" }, [
          vue.createElementVNode("text", { class: "label" }, "批次"),
          vue.withDirectives(vue.createElementVNode(
            "input",
            {
              "onUpdate:modelValue": _cache[4] || (_cache[4] = ($event) => $setup.form.batchNo = $event),
              class: "input"
            },
            null,
            512
            /* NEED_PATCH */
          ), [
            [vue.vModelText, $setup.form.batchNo]
          ])
        ]),
        vue.createElementVNode("view", { class: "field" }, [
          vue.createElementVNode("text", { class: "label" }, "退库原因"),
          vue.withDirectives(vue.createElementVNode(
            "input",
            {
              "onUpdate:modelValue": _cache[5] || (_cache[5] = ($event) => $setup.form.returnReason = $event),
              class: "input"
            },
            null,
            512
            /* NEED_PATCH */
          ), [
            [vue.vModelText, $setup.form.returnReason]
          ])
        ])
      ]),
      vue.createElementVNode("button", {
        class: "btn primary",
        onClick: $setup.submit
      }, "确认退库（1件/次）"),
      $setup.lastNo ? (vue.openBlock(), vue.createElementBlock(
        "view",
        {
          key: 0,
          class: "tip"
        },
        "退库单号：" + vue.toDisplayString($setup.lastNo),
        1
        /* TEXT */
      )) : vue.createCommentVNode("v-if", true)
    ]);
  }
  const PagesPickingWorkshopReturn = /* @__PURE__ */ _export_sfc(_sfc_main$5, [["render", _sfc_render$4], ["__scopeId", "data-v-08a9dabb"], ["__file", "D:/AAA/WMS/wms-pda/src/pages/picking/workshop-return.vue"]]);
  const _sfc_main$4 = {
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
  function _sfc_render$3(_ctx, _cache, $props, $setup, $data, $options) {
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
  const PagesMessagesMessages = /* @__PURE__ */ _export_sfc(_sfc_main$4, [["render", _sfc_render$3], ["__scopeId", "data-v-f5640984"], ["__file", "D:/AAA/WMS/wms-pda/src/pages/messages/messages.vue"]]);
  const _sfc_main$3 = {
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
      function save() {
        if (!serverInput.value.trim() && !hasCustomServer()) {
          uni.showToast({ title: "请输入服务器地址", icon: "none" });
          return;
        }
        const saved = setBaseUrl(serverInput.value || "/api/v1");
        hasCustom.value = hasCustomServer();
        testResult.value = { ok: true, message: `已保存: ${getServerDisplay()}` };
        uni.showToast({ title: "服务器已更新", icon: "success" });
        emit("saved", saved);
      }
      function reset() {
        resetBaseUrl();
        serverInput.value = getServerInputValue();
        hasCustom.value = false;
        testResult.value = { ok: true, message: "已恢复默认配置" };
        uni.showToast({ title: "已恢复默认", icon: "none" });
        emit("saved", getBaseUrl());
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
      const __returned__ = { props, emit, serverInput, testing, testResult, hasCustom, displayUrl, refresh, save, reset, testConnection, ref: vue.ref, computed: vue.computed, onMounted: vue.onMounted, get getBaseUrl() {
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
  function _sfc_render$2(_ctx, _cache, $props, $setup, $data, $options) {
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
  const ServerConfigForm = /* @__PURE__ */ _export_sfc(_sfc_main$3, [["render", _sfc_render$2], ["__scopeId", "data-v-ee002fa2"], ["__file", "D:/AAA/WMS/wms-pda/src/components/ServerConfigForm.vue"]]);
  const _sfc_main$2 = {
    __name: "settings",
    setup(__props, { expose: __expose }) {
      __expose();
      const serverFormRef = vue.ref(null);
      const fromLogin = vue.ref(false);
      const pwd = vue.reactive({ old: "", new1: "", new2: "" });
      const deviceNo = defaultConfig.deviceNo;
      const isLoggedIn = vue.computed(() => !!uni.getStorageSync("wms_token"));
      onLoad((options) => {
        fromLogin.value = (options == null ? void 0 : options.from) === "login";
      });
      onShow(() => {
        var _a, _b;
        (_b = (_a = serverFormRef.value) == null ? void 0 : _a.refresh) == null ? void 0 : _b.call(_a);
      });
      function onServerSaved() {
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
      const __returned__ = { serverFormRef, fromLogin, pwd, deviceNo, isLoggedIn, onServerSaved, handleChangePwd, goBack, ref: vue.ref, reactive: vue.reactive, computed: vue.computed, get onLoad() {
        return onLoad;
      }, get onShow() {
        return onShow;
      }, ServerConfigForm, get defaultConfig() {
        return defaultConfig;
      }, get changePassword() {
        return changePassword;
      } };
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  };
  function _sfc_render$1(_ctx, _cache, $props, $setup, $data, $options) {
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
        vue.createElementVNode("text", { class: "info-row" }, "应用版本: WMS PDA 1.0")
      ]),
      $setup.fromLogin ? (vue.openBlock(), vue.createElementBlock("button", {
        key: 1,
        class: "btn-back",
        onClick: $setup.goBack
      }, "返回登录")) : vue.createCommentVNode("v-if", true)
    ]);
  }
  const PagesSettingsSettings = /* @__PURE__ */ _export_sfc(_sfc_main$2, [["render", _sfc_render$1], ["__scopeId", "data-v-6d719173"], ["__file", "D:/AAA/WMS/wms-pda/src/pages/settings/settings.vue"]]);
  const _sfc_main$1 = {
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
  function _sfc_render(_ctx, _cache, $props, $setup, $data, $options) {
    return vue.openBlock(), vue.createBlock(
      $setup["ProfilePanel"],
      { ref: "panelRef" },
      null,
      512
      /* NEED_PATCH */
    );
  }
  const PagesProfileProfile = /* @__PURE__ */ _export_sfc(_sfc_main$1, [["render", _sfc_render], ["__file", "D:/AAA/WMS/wms-pda/src/pages/profile/profile.vue"]]);
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
  __definePage("pages/stockcheck/stockcheck", PagesStockcheckStockcheck);
  __definePage("pages/tasklist/tasklist", PagesTasklistTasklist);
  __definePage("pages/panel/panel", PagesPanelPanel);
  __definePage("pages/qc/qc", PagesQcQc);
  __definePage("pages/trace/trace", PagesTraceTrace);
  __definePage("pages/picking/production-issue", PagesPickingProductionIssue);
  __definePage("pages/picking/production-issue-scan", PagesPickingProductionIssueScan);
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
