# MES-tablet

独立于 **Web 管理后台** 和 **PDA 仓储终端** 的现场作业客户端，面向车间工业平板（横屏）。

## 启动

```bash
cd MES-tablet
npm install
npm run dev:h5
```

浏览器访问 **http://localhost:5175**。

真机/平板 App 请用 HBuilderX 打开 `MES-tablet` 目录，发行到 Android/iOS。

## 页面

| 页面 | 说明 |
|------|------|
| 登录 | 独立登录，登录后进入现场作业 |
| 现场作业 | 工序信息 / 称重 / 物料标签打印（交互后续接入） |
| 系统设置 | 服务器地址、屏幕分辨率 |

显示默认铺满屏幕，也可在作业页或系统设置中固定分辨率。
