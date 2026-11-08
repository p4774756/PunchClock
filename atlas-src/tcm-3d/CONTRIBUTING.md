# 参与贡献

欢迎通过Issue、Pull Request或[JIC Discord](https://discord.gg/jic)参与。

## 提交问题

请说明浏览器、设备、复现步骤、预期行为与实际行为。截图中请避免包含个人健康信息、账号凭据或其他隐私。

## 改进代码

1. Fork仓库，在自己的分支实现一个明确改动。
2. 运行 `npm ci` 和 `npm run check`。
3. 改动工具或接触行为时运行 `node scripts/validate-tool-drag.mjs` 与 `node scripts/validate-teaching.mjs`，并在浏览器验证。
4. 运行 `npm run build`。PR中说明问题、改动和实际验证结果。

不要提交node_modules、构建产物、环境变量、生产配置或账号信息。保留模型与上游代码署名。

## 穴位与教学内容

请附标准/论文原始出处、版本、条款、适用体位和左右侧，说明哪些信息已证实、哪些仍待核。几何贴附成功不能代替专业定位校核；未经审查不要把reviewed改为true。研究组合、单穴作用和不同工具的证据应分别说明。

## 模型资产

请提供来源、版本与明确可再分发的许可证，并说明改编。禁止把未知来源模型直接放入仓库。
