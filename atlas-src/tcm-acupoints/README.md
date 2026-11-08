# 经络穴位图 · 立体明堂

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](./LICENSE)
[![Model: CC BY-SA 4.0](https://img.shields.io/badge/Model-CC%20BY--SA%204.0-lightgrey.svg)](./ATTRIBUTION.md)
[![Vite](https://img.shields.io/badge/Vite-8-646CFF?logo=vite&logoColor=white)](https://vitejs.dev/)
[![Three.js](https://img.shields.io/badge/Three.js-r185-000000?logo=threedotjs&logoColor=white)](https://threejs.org/)
[![React](https://img.shields.io/badge/React-19-61DAFB?logo=react&logoColor=black)](https://react.dev/)

交互式三维中医经络穴位图。技术路线参照 [thebuggeddev/anatomy](https://github.com/thebuggeddev/anatomy)：Three.js 场景、点击热点、侧栏切换与详情面板。

> 本项目仅供学习与科普演示，**不能替代专业医疗诊断或针灸操作指导**。

## 功能

- **真实人体 GLB**：Draco 压缩的 Z-Anatomy 衍生模型
- **穴位表面吸附**：方向锥过滤最近点，沿法线抬起，减少穿模
- **十四经脉浏览**：十二正经 + 任督，支持双侧穴位开关
- **穴位详解**：概述、定位、主治；支持搜索拼音 / 代码 / 穴名
- **交互视口**：旋转、缩放、自动旋转、复位视角

## 快速开始

### 环境要求

- Node.js 20+（推荐 LTS）
- npm 10+

### 安装与运行

```bash
git clone https://github.com/spacejohnlf/tcm-acupoints.git
cd tcm-acupoints
npm install
npm run dev
```

浏览器打开终端提示的本地地址（默认为 `http://127.0.0.1:5173/`）。

### 常用命令

| 命令 | 说明 |
| --- | --- |
| `npm run dev` | 启动开发服务器 |
| `npm run build` | TypeScript 检查并构建生产包 |
| `npm run preview` | 预览生产构建 |
| `npm run lint` | 运行 oxlint |

## 项目结构

```text
tcm-acupoints/
├── public/
│   ├── models/body.glb      # 人体模型（Draco）
│   └── draco/               # Draco 解码器
├── src/
│   ├── components/          # 应用壳与视口 UI
│   ├── data/point-texts.json
│   └── lib/
│       ├── acupoint-data.ts # 十四经与穴位坐标
│       └── three/
│           ├── loaders.ts   # GLB 加载与坐标归一化
│           ├── snap.ts      # 网格表面吸附
│           ├── hotspots.ts  # 穴位热点精灵
│           ├── viewer.ts    # Three.js 查看器
│           └── body.ts      # 经络线与程序化回退人偶
└── README.md
```

## 技术栈

- React 19 + TypeScript + Vite 8
- Three.js（GLTFLoader / DRACOLoader / OrbitControls）
- GSAP（切换过渡）
- Lucide React（图标）

## 数据说明

- 穴位文案整理自医学百科类公开条目（概述 / 定位 / 主治）
- 三维坐标为教学示意坐标，经表面吸附贴合当前人体网格；**非国家标准取穴坐标数据库**

## 许可

- **源代码**：见 [LICENSE](./LICENSE)（MIT）
- **人体模型资源**：CC BY-SA 4.0，详见 [ATTRIBUTION.md](./ATTRIBUTION.md)

使用或再分发本仓库中的人体模型时，请保留署名与相同许可要求。

## 贡献

欢迎 Issue 与 Pull Request。提交前请确保：

1. `npm run build` 通过
2. 不提交 `node_modules/`、`.cache/`、密钥或本地环境文件
3. 变更说明清楚（功能 / 修复 / 文档）

## 致谢

- [thebuggeddev/anatomy](https://github.com/thebuggeddev/anatomy) — 交互式三维解剖技术路线参考
- [Z-Anatomy](https://www.z-anatomy.com/) / [hpfrei/body-anatomy-3d-viewer](https://github.com/hpfrei/body-anatomy-3d-viewer) — 人体模型
- Three.js 社区
